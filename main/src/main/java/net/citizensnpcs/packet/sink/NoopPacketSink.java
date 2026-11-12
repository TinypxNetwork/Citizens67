package net.citizensnpcs.packet.sink;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.packet.core.PacketNpcState;
import net.citizensnpcs.util.SkinProperty;

/** Fallback used when the PacketEvents plugin/adapter is not installed. */
public final class NoopPacketSink implements PacketSink {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public void spawn(Player viewer, PacketNpcState state) {
    }

    @Override
    public void destroy(Player viewer, PacketNpcState state) {
    }

    @Override
    public void metadata(Player viewer, PacketNpcState state) {
    }

    @Override
    public void equipmentSlot(Player viewer, PacketNpcState state, int slot, ItemStack item) {
    }

    @Override
    public void teleport(Player viewer, PacketNpcState state) {
    }

    @Override
    public void look(Player viewer, PacketNpcState state, float yaw, float pitch) {
    }

    @Override
    public void headLook(Player viewer, PacketNpcState state, float headYaw) {
    }

    @Override
    public void playerInfoAdd(Player viewer, PacketNpcState state, String displayName, String profileName,
            SkinProperty skin) {
    }

    @Override
    public void playerInfoRemove(Player viewer, PacketNpcState state) {
    }

    @Override
    public void hologramText(Player viewer, int entityId, java.util.UUID uuid, org.bukkit.Location location,
            String text) {
    }

    @Override
    public void hologramItem(Player viewer, int entityId, java.util.UUID uuid, org.bukkit.Location location,
            ItemStack item) {
    }

    @Override
    public void hologramTextUpdate(Player viewer, int entityId, String text) {
    }

    @Override
    public void hologramTeleport(Player viewer, int entityId, org.bukkit.Location location) {
    }

    @Override
    public void hologramDespawn(Player viewer, int entityId) {
    }

    @Override
    public void flush() {
    }

    @Override
    public void shutdown() {
    }
}
