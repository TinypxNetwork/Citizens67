package net.citizensnpcs.packet.hologram;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.packet.core.PacketEntityIdAllocator;

/** One packet-only hologram line (armor stand text or dropped item). */
public final class PacketHologramLine {
    private final int entityId = PacketEntityIdAllocator.allocate();
    private final UUID uuid = UUID.randomUUID();
    private ItemStack item;
    private Location location;
    private String rawText;
    private String renderedText;

    public PacketHologramLine(String rawText) {
        this.rawText = rawText;
    }

    public PacketHologramLine(ItemStack item) {
        this.item = item == null ? null : item.clone();
    }

    public int getEntityId() {
        return entityId;
    }

    public UUID getUuid() {
        return uuid;
    }

    public ItemStack getItem() {
        return item;
    }

    public Location getLocation() {
        return location;
    }

    public String getRawText() {
        return rawText;
    }

    public String getRenderedText() {
        return renderedText;
    }

    public boolean isItem() {
        return item != null;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setRenderedText(String renderedText) {
        this.renderedText = renderedText;
    }
}
