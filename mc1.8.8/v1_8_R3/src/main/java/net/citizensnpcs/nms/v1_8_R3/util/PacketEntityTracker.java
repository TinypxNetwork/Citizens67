package net.citizensnpcs.nms.v1_8_R3.util;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.citizensnpcs.api.event.NPCLinkToPlayerEvent;
import net.citizensnpcs.api.event.NPCSeenByPlayerEvent;
import net.citizensnpcs.api.event.NPCUnlinkFromPlayerEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.trait.PacketNPC.ViewerTracker;
import net.citizensnpcs.util.EntityPacketTracker.PacketAggregator;
import net.citizensnpcs.util.NMS;
import net.minecraft.server.v1_8_R3.Entity;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EntityTrackerEntry;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo.EnumPlayerInfoAction;

/** Sends changes without registering the backing entity with WorldServer or its tracker. */
public final class PacketEntityTracker implements ViewerTracker {
    private static final MethodHandle SPAWN_PACKET = NMS.getMethodHandle(EntityTrackerEntry.class, "c", true);
    private final PacketAggregator aggregator;
    private final Map<Integer, ItemStack> equipment = new HashMap<>();
    private final Entity handle;
    private final Map<UUID, Player> linked = new LinkedHashMap<>();
    private final NPC npc;
    private final EntityTrackerEntry spawnEncoder;
    private int x, y, z, yaw, pitch, headYaw;

    public PacketEntityTracker(org.bukkit.entity.Entity entity, PacketAggregator aggregator) {
        this.aggregator = aggregator;
        handle = NMSImpl.getHandle(entity);
        npc = ((NPCHolder) handle).getNPC();
        // Used only as an encoder, never registered with the world's tracker.
        spawnEncoder = new EntityTrackerEntry(handle, 64, 3, false);
        savePosition();
    }

    @Override
    public boolean isTracking(Player player) {
        return linked.get(player.getUniqueId()) == player;
    }

    @Override
    public void link(Player player) {
        if (isTracking(player) || !player.isOnline())
            return;
        NPCSeenByPlayerEvent seen = new NPCSeenByPlayerEvent(npc, player);
        Bukkit.getPluginManager().callEvent(seen);
        if (seen.isCancelled())
            return;
        linked.put(player.getUniqueId(), player);
        EntityPlayer viewer = (EntityPlayer) NMSImpl.getHandle(player);
        aggregator.add(player.getUniqueId(), packet -> viewer.playerConnection.sendPacket((Packet<?>) packet));
        // Citizens sends player-info/skin packets before the player spawn packet.
        Bukkit.getPluginManager().callEvent(new NPCLinkToPlayerEvent(npc, player));
        try {
            send(player, (Packet<?>) SPAWN_PACKET.invoke(spawnEncoder));
        } catch (Throwable ex) {
            unlink(player);
            throw new IllegalStateException("Unable to encode packet NPC " + npc.getId(), ex);
        }
        send(player, new PacketPlayOutEntityMetadata(handle.getId(), handle.getDataWatcher(), true));
        send(player, new PacketPlayOutEntityHeadRotation(handle, angle(NMS.getHeadYaw(handle.getBukkitEntity()))));
        if (handle instanceof EntityLiving) {
            ItemStack[] items = ((EntityLiving) handle).getEquipment();
            for (int slot = 0; slot < items.length; slot++) {
                send(player, new PacketPlayOutEntityEquipment(handle.getId(), slot, items[slot]));
            }
        }
    }

    @Override
    public void run() {
        if (linked.isEmpty())
            return;
        if (x != position(handle.locX) || y != position(handle.locY) || z != position(handle.locZ)
                || yaw != angle(handle.yaw) || pitch != angle(handle.pitch)) {
            aggregator.send(new PacketPlayOutEntityTeleport(handle));
        }
        int newHeadYaw = angle(NMS.getHeadYaw(handle.getBukkitEntity()));
        if (headYaw != newHeadYaw) {
            aggregator.send(new PacketPlayOutEntityHeadRotation(handle, (byte) newHeadYaw));
        }
        savePosition();
        if (handle.getDataWatcher().a()) {
            aggregator.send(new PacketPlayOutEntityMetadata(handle.getId(), handle.getDataWatcher(), false));
        }
        if (handle instanceof EntityLiving) {
            ItemStack[] items = ((EntityLiving) handle).getEquipment();
            for (int slot = 0; slot < items.length; slot++) {
                if (!ItemStack.matches(equipment.get(slot), items[slot])) {
                    equipment.put(slot, items[slot] == null ? null : items[slot].cloneItemStack());
                    aggregator.send(new PacketPlayOutEntityEquipment(handle.getId(), slot, items[slot]));
                }
            }
        }
    }

    @Override
    public void unlink(Player player) {
        if (linked.remove(player.getUniqueId()) == null)
            return;
        aggregator.removeConnection(player.getUniqueId());
        if (player.isOnline()) {
            send(player, new PacketPlayOutEntityDestroy(handle.getId()));
            if (handle instanceof EntityPlayer) {
                send(player, new PacketPlayOutPlayerInfo(EnumPlayerInfoAction.REMOVE_PLAYER, (EntityPlayer) handle));
            }
        }
        Bukkit.getPluginManager().callEvent(new NPCUnlinkFromPlayerEvent(npc, player));
    }

    @Override
    public void unlinkAll(Consumer<Player> callback) {
        for (Player player : new ArrayList<>(linked.values())) {
            unlink(player);
            if (callback != null) {
                callback.accept(player);
            }
        }
    }

    private void savePosition() {
        x = position(handle.locX);
        y = position(handle.locY);
        z = position(handle.locZ);
        yaw = angle(handle.yaw);
        pitch = angle(handle.pitch);
        headYaw = angle(NMS.getHeadYaw(handle.getBukkitEntity()));
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private static int position(double value) {
        return (int) Math.floor(value * 32.0D);
    }

    private static void send(Player player, Packet<?> packet) {
        if (packet != null) {
            ((EntityPlayer) NMSImpl.getHandle(player)).playerConnection.sendPacket(packet);
        }
    }
}
