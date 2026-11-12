package net.citizensnpcs.packet.skin;

import org.bukkit.entity.Player;

import net.citizensnpcs.util.SkinProperty;

/** One way of obtaining the skin for a packet NPC. */
public interface SkinDescriptor {
    /** Stable cache key for this descriptor/viewer combination. */
    String cacheKey(Player viewer);

    /** Resolve immediately, or null when the caller should fall back/refresh asynchronously. */
    SkinProperty resolve(Player viewer);

    String type();

    String value();
}
