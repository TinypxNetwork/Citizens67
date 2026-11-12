package net.citizensnpcs.packet.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnLivingEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo.PlayerData;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.packet.interaction.PacketClickType;
import net.citizensnpcs.packet.interaction.PacketInteractionService;
import net.citizensnpcs.packet.core.PacketNpcState;
import net.citizensnpcs.packet.sink.PacketSink;
import net.citizensnpcs.util.SkinProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * PacketEvents 2.13 implementation. This class lives in a separate, non-relocated module because the main Citizens jar
 * relocates net.kyori; PacketEvents' public API uses the original Adventure Component types.
 */
public final class PacketEventsPacketSink implements PacketSink {
    private final ConcurrentLinkedQueue<Runnable> outbox = new ConcurrentLinkedQueue<Runnable>();
    private final ExecutorService sender = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Citizens-PacketSender");
            thread.setDaemon(true);
            return thread;
        }
    });

    public PacketEventsPacketSink() {
        if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isInitialized()) {
            throw new IllegalStateException("PacketEvents is not initialized");
        }
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListener() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY)
                    return;
                Player player = event.getPlayer();
                if (player == null)
                    return;

                WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
                PacketClickType type;
                if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    type = PacketClickType.LEFT;
                } else if (packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT
                        || packet.getAction() == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                    type = PacketClickType.RIGHT;
                } else {
                    return;
                }
                final int entityId = packet.getEntityId();
                final PacketClickType clickType = type;
                Bukkit.getScheduler().runTask(CitizensAPI.getPlugin(), new Runnable() {
                    @Override
                    public void run() {
                        PacketInteractionService.handle(player, entityId, clickType);
                    }
                });
            }
        }, PacketListenerPriority.NORMAL);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void spawn(Player viewer, PacketNpcState state) {
        Location location = state.getLocation();
        if (location == null)
            return;
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
        EntityType bukkitType = state.getType();
        com.github.retrooper.packetevents.protocol.entity.type.EntityType packetType = SpigotConversionUtil
                .fromBukkitEntityType(bukkitType);
        if (bukkitType == EntityType.PLAYER) {
            send(viewer, new WrapperPlayServerSpawnPlayer(state.getEntityId(), state.getUuid(), position,
                    state.getYaw(), state.getPitch(), Collections.<EntityData<?>>emptyList()));
        } else if (state.getBackingEntity() instanceof LivingEntity) {
            send(viewer, new WrapperPlayServerSpawnLivingEntity(state.getEntityId(), state.getUuid(), packetType,
                    position, state.getYaw(), state.getPitch(), state.getHeadYaw(), new Vector3d(0, 0, 0),
                    Collections.<EntityData<?>>emptyList()));
        } else {
            send(viewer, new WrapperPlayServerSpawnEntity(state.getEntityId(), Optional.of(state.getUuid()),
                    packetType, position, state.getPitch(), state.getYaw(), state.getHeadYaw(), 0,
                    Optional.<Vector3d>empty()));
        }
    }

    @Override
    public void destroy(Player viewer, PacketNpcState state) {
        send(viewer, new WrapperPlayServerDestroyEntities(state.getEntityId()));
    }

    @Override
    public void metadata(Player viewer, PacketNpcState state) {
        if (state.getBackingEntity() == null)
            return;
        try {
            List<EntityData<?>> data = SpigotConversionUtil.getEntityMetadata(state.getBackingEntity());
            send(viewer, new WrapperPlayServerEntityMetadata(state.getEntityId(), data));
        } catch (Throwable t) {
            Messaging.severe("Failed to read metadata for packet NPC " + state.getNpc().getId() + ": "
                    + t.getMessage());
        }
    }

    @Override
    public void equipmentSlot(Player viewer, PacketNpcState state, int slot, ItemStack item) {
        EquipmentSlot packetSlot = toPacketSlot(slot);
        if (packetSlot == null)
            return;
        com.github.retrooper.packetevents.protocol.item.ItemStack packetItem = item == null
                ? com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY
                : SpigotConversionUtil.fromBukkitItemStack(item);
        List<Equipment> equipment = new ArrayList<Equipment>(1);
        equipment.add(new Equipment(packetSlot, packetItem));
        send(viewer, new WrapperPlayServerEntityEquipment(state.getEntityId(), equipment));
    }

    @Override
    public void teleport(Player viewer, PacketNpcState state) {
        Location location = state.getLocation();
        if (location == null)
            return;
        send(viewer, new WrapperPlayServerEntityTeleport(state.getEntityId(),
                new Vector3d(location.getX(), location.getY(), location.getZ()), state.getYaw(), state.getPitch(),
                false));
    }

    @Override
    public void look(Player viewer, PacketNpcState state, float yaw, float pitch) {
        send(viewer, new WrapperPlayServerEntityRotation(state.getEntityId(), yaw, pitch, false));
    }

    @Override
    public void headLook(Player viewer, PacketNpcState state, float headYaw) {
        send(viewer, new WrapperPlayServerEntityHeadLook(state.getEntityId(), headYaw));
    }

    @Override
    public void playerInfoAdd(Player viewer, PacketNpcState state, String displayName, String profileName,
            SkinProperty skin) {
        if (!state.isPlayerType())
            return;
        UserProfile profile = new UserProfile(state.getUuid(), profileName);
        if (skin != null && skin.value != null && !skin.value.isEmpty()) {
            profile.setTextureProperties(Collections.singletonList(
                    new TextureProperty("textures", skin.value, skin.signature == null ? "" : skin.signature)));
        }
        String plain = org.bukkit.ChatColor.stripColor(displayName == null ? "" : displayName);
        Component component = Component.text(plain, NamedTextColor.DARK_GRAY);
        PlayerData data = new PlayerData(component, profile, GameMode.SURVIVAL, 1);
        send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.ADD_PLAYER, data));
    }

    @Override
    public void playerInfoRemove(Player viewer, PacketNpcState state) {
        if (!state.isPlayerType())
            return;
        UserProfile profile = new UserProfile(state.getUuid(), state.getProfileName());
        PlayerData data = new PlayerData(null, profile, GameMode.SURVIVAL, 1);
        send(viewer, new WrapperPlayServerPlayerInfo(WrapperPlayServerPlayerInfo.Action.REMOVE_PLAYER, data));
    }

    @Override
    public void hologramText(Player viewer, int entityId, UUID uuid, Location location, String text) {
        if (location == null)
            return;
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
        List<EntityData<?>> data = new ArrayList<EntityData<?>>(4);
        data.add(new EntityData<Byte>(0, EntityDataTypes.BYTE, (byte) 0x20));
        data.add(new EntityData<String>(2, EntityDataTypes.STRING, text == null ? "" : text));
        data.add(new EntityData<Boolean>(3, EntityDataTypes.BOOLEAN, true));
        data.add(new EntityData<Byte>(10, EntityDataTypes.BYTE, (byte) (0x02 | 0x10)));
        send(viewer, new WrapperPlayServerSpawnLivingEntity(entityId, uuid,
                SpigotConversionUtil.fromBukkitEntityType(EntityType.ARMOR_STAND), position, 0F, 0F, 0F,
                new Vector3d(0, 0, 0), data));
    }

    @Override
    public void hologramItem(Player viewer, int entityId, UUID uuid, Location location, ItemStack item) {
        if (location == null || item == null)
            return;
        Vector3d position = new Vector3d(location.getX(), location.getY(), location.getZ());
        send(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid), EntityTypes.ITEM, position, 0F, 0F,
                0F, 0, Optional.<Vector3d>empty()));
        List<EntityData<?>> data = new ArrayList<EntityData<?>>(1);
        data.add(new EntityData<com.github.retrooper.packetevents.protocol.item.ItemStack>(10,
                EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(item)));
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    @Override
    public void hologramTextUpdate(Player viewer, int entityId, String text) {
        List<EntityData<?>> data = new ArrayList<EntityData<?>>(1);
        data.add(new EntityData<String>(2, EntityDataTypes.STRING, text == null ? "" : text));
        send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    @Override
    public void hologramTeleport(Player viewer, int entityId, Location location) {
        if (location == null)
            return;
        send(viewer, new WrapperPlayServerEntityTeleport(entityId,
                new Vector3d(location.getX(), location.getY(), location.getZ()), 0F, 0F, false));
    }

    @Override
    public void hologramDespawn(Player viewer, int entityId) {
        send(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    @Override
    public void flush() {
        List<Runnable> batch = new ArrayList<Runnable>();
        Runnable task;
        while ((task = outbox.poll()) != null) {
            batch.add(task);
        }
        if (!batch.isEmpty()) {
            sender.execute(new Batch(batch));
        }
    }

    @Override
    public void shutdown() {
        outbox.clear();
        sender.shutdownNow();
    }

    private static EquipmentSlot toPacketSlot(int slot) {
        switch (slot) {
            case 0:
                return EquipmentSlot.MAIN_HAND;
            case 1:
                return EquipmentSlot.BOOTS;
            case 2:
                return EquipmentSlot.LEGGINGS;
            case 3:
                return EquipmentSlot.CHEST_PLATE;
            case 4:
                return EquipmentSlot.HELMET;
            default:
                return null;
        }
    }

    private void send(final Player player, final PacketWrapper<?> packet) {
        if (player == null)
            return;
        outbox.add(new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
                }
            }
        });
    }

    private static final class Batch implements Runnable {
        private final List<Runnable> packets;

        Batch(List<Runnable> packets) {
            this.packets = packets;
        }

        @Override
        public void run() {
            for (Runnable packet : packets) {
                packet.run();
            }
        }
    }
}
