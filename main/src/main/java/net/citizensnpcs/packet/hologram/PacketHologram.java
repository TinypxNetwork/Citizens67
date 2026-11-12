package net.citizensnpcs.packet.hologram;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.util.Placeholders;
import net.citizensnpcs.packet.sink.PacketSink;

/** Lightweight 1.8 packet hologram: armor stands for text, item entities for items. */
public final class PacketHologram {
    private final List<PacketHologramLine> lines = new ArrayList<PacketHologramLine>();
    private final NPC npc;
    private Location lastBase;
    private double offset = 0.3D;
    private int refreshTicks = 20;
    private double spacing = 0.25D;
    private int ticker;

    public PacketHologram(NPC npc) {
        this.npc = npc;
    }

    public void addText(String raw) {
        lines.add(new PacketHologramLine(raw));
    }

    public void addItem(ItemStack item) {
        lines.add(new PacketHologramLine(item));
    }

    public void insertText(int index, String raw) {
        lines.add(Math.max(0, Math.min(index, lines.size())), new PacketHologramLine(raw));
    }

    public void insertItem(int index, ItemStack item) {
        lines.add(Math.max(0, Math.min(index, lines.size())), new PacketHologramLine(item));
    }

    public void remove(int index) {
        if (index >= 0 && index < lines.size()) {
            lines.remove(index);
        }
    }

    public void clear() {
        lines.clear();
        lastBase = null;
    }

    public List<PacketHologramLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public double getOffset() {
        return offset;
    }

    public double getSpacing() {
        return spacing;
    }

    public void setOffset(double offset) {
        this.offset = offset;
        lastBase = null;
    }

    public void setSpacing(double spacing) {
        this.spacing = Math.max(0.01D, spacing);
        lastBase = null;
    }

    public void setRefreshTicks(int refreshTicks) {
        this.refreshTicks = Math.max(1, refreshTicks);
    }

    public void show(Player viewer, PacketSink sink) {
        Location base = baseLocation();
        if (base == null)
            return;
        for (int i = 0; i < lines.size(); i++) {
            PacketHologramLine line = lines.get(i);
            line.setLocation(locationFor(i, base));
            if (line.isItem()) {
                sink.hologramItem(viewer, line.getEntityId(), line.getUuid(), line.getLocation(), line.getItem());
            } else {
                line.setRenderedText(render(line.getRawText()));
                sink.hologramText(viewer, line.getEntityId(), line.getUuid(), line.getLocation(),
                        line.getRenderedText());
            }
        }
        lastBase = base;
    }

    public void hide(Player viewer, PacketSink sink) {
        for (PacketHologramLine line : lines) {
            sink.hologramDespawn(viewer, line.getEntityId());
        }
    }

    public void tick(PacketSink sink, Collection<Player> viewers) {
        if (lines.isEmpty() || viewers.isEmpty())
            return;
        Location base = baseLocation();
        if (base == null)
            return;
        boolean moved = lastBase == null || lastBase.getWorld() != base.getWorld()
                || lastBase.distanceSquared(base) > 0.0001D;
        if (moved) {
            for (int i = 0; i < lines.size(); i++) {
                PacketHologramLine line = lines.get(i);
                line.setLocation(locationFor(i, base));
                for (Player viewer : viewers) {
                    sink.hologramTeleport(viewer, line.getEntityId(), line.getLocation());
                }
            }
            lastBase = base;
        }
        if (++ticker >= refreshTicks) {
            ticker = 0;
            for (PacketHologramLine line : lines) {
                if (line.isItem())
                    continue;
                String rendered = render(line.getRawText());
                if (rendered.equals(line.getRenderedText()))
                    continue;
                line.setRenderedText(rendered);
                for (Player viewer : viewers) {
                    sink.hologramTextUpdate(viewer, line.getEntityId(), rendered);
                }
            }
        }
    }

    public void refresh(PacketSink sink, Collection<Player> viewers) {
        for (PacketHologramLine line : lines) {
            if (line.isItem())
                continue;
            String rendered = render(line.getRawText());
            line.setRenderedText(rendered);
            for (Player viewer : viewers) {
                sink.hologramTextUpdate(viewer, line.getEntityId(), rendered);
            }
        }
    }

    private Location baseLocation() {
        Location location = npc.getEntity() == null ? npc.getStoredLocation() : npc.getEntity().getLocation();
        if (location == null)
            return null;
        return location.clone().add(0, offset, 0);
    }

    private Location locationFor(int index, Location base) {
        return base.clone().add(0, (lines.size() - 1 - index) * spacing, 0);
    }

    private String render(String raw) {
        return raw == null ? "" : Placeholders.replace(raw, null, npc);
    }
}
