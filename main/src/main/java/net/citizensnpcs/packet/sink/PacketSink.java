package net.citizensnpcs.packet.sink;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.packet.core.PacketNpcState;
import net.citizensnpcs.util.SkinProperty;
/**
 * Version/library-neutral packet operations used by the packet NPC core. Implementations must not expose
 * PacketEvents or Adventure types to the main module, because the main jar relocates net.kyori.
 */
public interface PacketSink {
    boolean isAvailable();

    void spawn(Player viewer, PacketNpcState state);

    void destroy(Player viewer, PacketNpcState state);

    void metadata(Player viewer, PacketNpcState state);

    void equipmentSlot(Player viewer, PacketNpcState state, int slot, ItemStack item);

    void teleport(Player viewer, PacketNpcState state);

    void look(Player viewer, PacketNpcState state, float yaw, float pitch);

    void headLook(Player viewer, PacketNpcState state, float headYaw);

    void playerInfoAdd(Player viewer, PacketNpcState state, String displayName, String profileName, SkinProperty skin);

    void playerInfoRemove(Player viewer, PacketNpcState state);

    void hologramText(Player viewer, int entityId, java.util.UUID uuid, org.bukkit.Location location, String text);

    void hologramItem(Player viewer, int entityId, java.util.UUID uuid, org.bukkit.Location location, ItemStack item);

    void hologramTextUpdate(Player viewer, int entityId, String text);

    void hologramTeleport(Player viewer, int entityId, org.bukkit.Location location);

    void hologramDespawn(Player viewer, int entityId);

    /** Flush queued packets for the current service tick. Implementations may send synchronously. */
    void flush();

    void shutdown();
}
