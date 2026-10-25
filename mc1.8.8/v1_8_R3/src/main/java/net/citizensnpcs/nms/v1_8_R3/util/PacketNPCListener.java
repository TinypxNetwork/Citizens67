package net.citizensnpcs.nms.v1_8_R3.util;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.ClickRedirectTrait;
import net.citizensnpcs.trait.CommandTrait;
import net.citizensnpcs.trait.PacketNPC;
import net.citizensnpcs.util.NMS;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.NetworkManager;
import net.minecraft.server.v1_8_R3.PacketPlayInUseEntity;
import net.minecraft.server.v1_8_R3.PacketPlayInUseEntity.EnumEntityUseAction;

/** Routes use-entity packets for IDs that do not exist in the server world's entity map. */
public final class PacketNPCListener implements Listener {
    private static final String HANDLER = "citizens_packet_npc";
    private static final MethodHandle ENTITY_ID = NMS.getGetter(PacketPlayInUseEntity.class, "a");
    private final Map<UUID, Channel> channels = new HashMap<>();

    public PacketNPCListener() {
        Bukkit.getPluginManager().registerEvents(this, CitizensAPI.getPlugin());
        for (Player player : Bukkit.getOnlinePlayers()) {
            inject(player);
        }
    }

    public void close() {
        HandlerList.unregisterAll(this);
        for (Channel channel : channels.values()) {
            remove(channel);
        }
        channels.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        inject(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PacketNPC.hideAll(event.getPlayer());
        Channel channel = channels.remove(event.getPlayer().getUniqueId());
        if (channel != null) {
            remove(channel);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        PacketNPC.hideAll(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        PacketNPC.hideAll(event.getPlayer());
    }

    private void inject(Player player) {
        NetworkManager manager = ((EntityPlayer) NMSImpl.getHandle(player)).playerConnection.networkManager;
        Channel channel = manager.channel;
        channels.put(player.getUniqueId(), channel);
        channel.eventLoop().execute(() -> {
            ChannelHandlerContext target = channel.pipeline().context(manager);
            if (!channel.isActive() || target == null || channel.pipeline().get(HANDLER) != null)
                return;
            channel.pipeline().addBefore(target.name(), HANDLER, new ChannelDuplexHandler() {
                private final AtomicBoolean pending = new AtomicBoolean();

                @Override
                public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    if (message instanceof PacketPlayInUseEntity) {
                        PacketPlayInUseEntity packet = (PacketPlayInUseEntity) message;
                        final int id;
                        try {
                            id = (int) ENTITY_ID.invoke(packet);
                        } catch (Throwable ex) {
                            throw new IllegalStateException("Cannot read NPC interaction packet", ex);
                        }
                        if (PacketNPC.getByEntityId(id) != null) {
                            // 1.8 sends INTERACT_AT and INTERACT together; handle the latter once.
                            if (packet.a() != EnumEntityUseAction.INTERACT_AT && pending.compareAndSet(false, true)) {
                                Bukkit.getScheduler().runTask(CitizensAPI.getPlugin(), () -> {
                                    try {
                                        interact(player, id, packet.a());
                                    } finally {
                                        pending.set(false);
                                    }
                                });
                            }
                            return;
                        }
                    }
                    super.channelRead(context, message);
                }
            });
        });
    }

    private static void interact(Player player, int id, EnumEntityUseAction action) {
        PacketNPC trait = PacketNPC.getByEntityId(id);
        if (trait == null || !player.isOnline() || !trait.isTracking(player))
            return;
        NPC npc = trait.getNPC();
        if (!npc.isSpawned() || npc.getEntity().getWorld() != player.getWorld()
                || npc.getEntity().getLocation().distanceSquared(player.getLocation()) > 36)
            return;
        if (action == EnumEntityUseAction.INTERACT) {
            // Reuse Citizens' normal right-click, selector, command and shop handlers.
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player, npc.getEntity()));
        } else if (action == EnumEntityUseAction.ATTACK) {
            ClickRedirectTrait redirect = npc.getTraitNullable(ClickRedirectTrait.class);
            if (redirect != null) {
                npc = redirect.getRedirectNPC();
            }
            if (npc == null)
                return;
            NPCLeftClickEvent event = new NPCLeftClickEvent(npc, player);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled() && npc.hasTrait(CommandTrait.class)) {
                npc.getTraitNullable(CommandTrait.class).dispatch(player, CommandTrait.Hand.LEFT);
            }
        }
    }

    private static void remove(Channel channel) {
        channel.eventLoop().execute(() -> {
            if (channel.pipeline().get(HANDLER) != null) {
                channel.pipeline().remove(HANDLER);
            }
        });
    }
}
