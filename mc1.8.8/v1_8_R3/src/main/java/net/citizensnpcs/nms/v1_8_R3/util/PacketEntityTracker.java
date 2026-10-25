package net.citizensnpcs.nms.v1_8_R3.util;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import net.citizensnpcs.api.event.NPCLinkToPlayerEvent;
import net.citizensnpcs.api.event.NPCSeenByPlayerEvent;
import net.citizensnpcs.api.event.NPCUnlinkFromPlayerEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.trait.LookClose;
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
import net.minecraft.server.v1_8_R3.PacketPlayOutEntity.PacketPlayOutEntityLook;
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
    private final Map<UUID, byte[]> rotations = new HashMap<>();
    private final EntityTrackerEntry spawnEncoder;
    private int x, y, z;

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
        sendPosition(player, false, true);
        if (handle instanceof EntityLiving) {
            // EntityHuman.getEquipment() returns only armor; indexed access includes the hand.
            for (int slot = 0; slot < 5; slot++) {
                send(player, new PacketPlayOutEntityEquipment(handle.getId(), slot,
                        ((EntityLiving) handle).getEquipment(slot)));
            }
        }
    }

    @Override
    public void run() {
        if (linked.isEmpty())
            return;
        boolean moved = x != position(handle.locX) || y != position(handle.locY) || z != position(handle.locZ);
        for (Player player : linked.values()) {
            sendPosition(player, moved, false);
        }
        savePosition();
        if (handle.getDataWatcher().a()) {
            aggregator.send(new PacketPlayOutEntityMetadata(handle.getId(), handle.getDataWatcher(), false));
        }
        if (handle instanceof EntityLiving) {
            for (int slot = 0; slot < 5; slot++) {
                ItemStack item = ((EntityLiving) handle).getEquipment(slot);
                if (!ItemStack.matches(equipment.get(slot), item)) {
                    equipment.put(slot, item == null ? null : item.cloneItemStack());
                    aggregator.send(new PacketPlayOutEntityEquipment(handle.getId(), slot, item));
                }
            }
        }
    }

    private void sendPosition(Player player, boolean moved, boolean force) {
        float bodyYaw = handle.yaw;
        float pitch = handle.pitch;
        float headYaw = NMS.getHeadYaw(handle.getBukkitEntity());
        LookClose look = npc.getTraitNullable(LookClose.class);
        if (look != null && look.isLookingAtViewer(player)) {
            Location from = handle.getBukkitEntity() instanceof LivingEntity
                    ? ((LivingEntity) handle.getBukkitEntity()).getEyeLocation() : handle.getBukkitEntity().getLocation();
            Location target = player.getEyeLocation();
            double dx = target.getX() - from.getX();
            double dy = target.getY() - from.getY();
            double dz = target.getZ() - from.getZ();
            headYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            if (!look.isHeadOnly()) {
                bodyYaw = headYaw;
            }
        }
        byte nextYaw = angle(bodyYaw), nextPitch = angle(pitch), nextHeadYaw = angle(headYaw);
        byte[] previous = rotations.get(player.getUniqueId());
        if (moved) {
            send(player, new PacketPlayOutEntityTeleport(handle.getId(), position(handle.locX), position(handle.locY),
                    position(handle.locZ), nextYaw, nextPitch, handle.onGround));
        } else if (force || previous == null || previous[0] != nextYaw || previous[1] != nextPitch) {
            send(player, new PacketPlayOutEntityLook(handle.getId(), nextYaw, nextPitch, handle.onGround));
        }
        if (force || previous == null || previous[2] != nextHeadYaw) {
            send(player, new PacketPlayOutEntityHeadRotation(handle, nextHeadYaw));
        }
        if (previous == null) {
            rotations.put(player.getUniqueId(), previous = new byte[3]);
        }
        previous[0] = nextYaw;
        previous[1] = nextPitch;
        previous[2] = nextHeadYaw;
    }

    @Override
    public void unlink(Player player) {
        if (linked.remove(player.getUniqueId()) == null)
            return;
        rotations.remove(player.getUniqueId());
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
