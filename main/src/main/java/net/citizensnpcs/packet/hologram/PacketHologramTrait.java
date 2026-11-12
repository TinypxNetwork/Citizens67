package net.citizensnpcs.packet.hologram;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.packet.core.PacketNpcService;
import net.citizensnpcs.packet.sink.PacketSink;

/** Lightweight packet hologram attached to a Citizens NPC. */
@TraitName("packet-hologram")
public class PacketHologramTrait extends Trait {
    private final List<String> rawLines = new ArrayList<String>();
    private PacketHologram hologram;

    public PacketHologramTrait() {
        super("packet-hologram");
    }

    public void addItemLine(String spec) {
        rawLines.add(normalizeItemSpec(spec));
        rebuild();
    }

    public void addTextLine(String text) {
        rawLines.add(encodeText(text));
        rebuild();
    }

    public void clear() {
        rawLines.clear();
        rebuild();
    }

    public PacketHologram getHologram() {
        return hologram;
    }

    public List<String> getRawLines() {
        return rawLines;
    }

    public void insertItemLine(int index, String spec) {
        rawLines.add(Math.max(0, Math.min(index, rawLines.size())), normalizeItemSpec(spec));
        rebuild();
    }

    public void insertTextLine(int index, String text) {
        rawLines.add(Math.max(0, Math.min(index, rawLines.size())), encodeText(text));
        rebuild();
    }

    public void onViewerLink(Player player) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (hologram != null && service != null) {
            hologram.show(player, service.getSink());
        }
    }

    public void onViewerUnlink(Player player) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (hologram != null && service != null) {
            hologram.hide(player, service.getSink());
        }
    }

    public void refresh() {
        PacketNpcService service = PacketNpcService.getInstance();
        if (hologram != null && service != null) {
            hologram.refresh(service.getSink(), service.getViewers(npc));
        }
    }

    public void removeLine(int index) {
        if (index >= 0 && index < rawLines.size()) {
            rawLines.remove(index);
            rebuild();
        }
    }

    public void setItemLine(int index, String spec) {
        if (index >= 0 && index < rawLines.size()) {
            rawLines.set(index, normalizeItemSpec(spec));
            rebuild();
        }
    }

    public void setOffset(double offset) {
        if (hologram != null) {
            hologram.setOffset(offset);
        }
        npc.data().setPersistent("packet-hologram-offset", offset);
    }

    public void setSpacing(double spacing) {
        if (hologram != null) {
            hologram.setSpacing(spacing);
        }
        npc.data().setPersistent("packet-hologram-spacing", spacing);
    }

    public void setTextLine(int index, String text) {
        if (index >= 0 && index < rawLines.size()) {
            rawLines.set(index, encodeText(text));
            rebuild();
        }
    }

    public void tick() {
        PacketNpcService service = PacketNpcService.getInstance();
        if (hologram == null || service == null)
            return;
        hologram.tick(service.getSink(), service.getViewers(npc));
    }

    @Override
    public void onDespawn() {
        PacketNpcService service = PacketNpcService.getInstance();
        if (hologram != null && service != null) {
            for (Player viewer : service.getViewers(npc)) {
                hologram.hide(viewer, service.getSink());
            }
        }
    }

    @Override
    public void onRemove() {
        onDespawn();
    }

    @Override
    public void onSpawn() {
        rebuild();
    }

    @Override
    public void load(DataKey root) {
        rawLines.clear();
        for (DataKey key : root.getRelative("lines").getIntegerSubKeys()) {
            String text = key.getString("text", "");
            String item = key.getString("item", "");
            if (!item.isEmpty()) {
                rawLines.add("item:" + item);
            } else if (!text.isEmpty()) {
                rawLines.add(encodeText(text));
            }
        }
        rebuild();
    }

    @Override
    public void save(DataKey root) {
        root.removeKey("lines");
        for (int i = 0; i < rawLines.size(); i++) {
            String raw = rawLines.get(i);
            DataKey key = root.getRelative("lines." + i);
            if (raw.startsWith("item:")) {
                key.setString("item", raw.substring(5));
            } else {
                key.setString("text", decodeText(raw));
            }
        }
    }

    private void rebuild() {
        PacketNpcService service = PacketNpcService.getInstance();
        PacketSink sink = service == null ? null : service.getSink();
        if (hologram != null && sink != null) {
            for (Player viewer : service.getViewers(npc)) {
                hologram.hide(viewer, sink);
            }
        }
        hologram = new PacketHologram(npc);
        hologram.setOffset(npc.data().get("packet-hologram-offset", 0.3D));
        hologram.setSpacing(npc.data().get("packet-hologram-spacing", 0.25D));
        for (String raw : rawLines) {
            if (raw.startsWith("item:")) {
                ItemStack item = parseItem(raw.substring(5));
                if (item != null) {
                    hologram.addItem(item);
                }
            } else {
                hologram.addText(decodeText(raw));
            }
        }
        if (sink != null && npc.isSpawned()) {
            for (Player viewer : service.getViewers(npc)) {
                hologram.show(viewer, sink);
            }
        }
    }

    private static String encodeText(String text) {
        return text == null ? "" : text;
    }

    private static String decodeText(String raw) {
        return raw == null ? "" : raw;
    }

    private static String normalizeItemSpec(String spec) {
        String value = spec == null ? "" : spec;
        return value.startsWith("item:") ? value.substring(5) : value;
    }

    private static ItemStack parseItem(String spec) {
        String[] parts = spec.split(":");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null)
            return null;
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        short data = (short) (parts.length > 2 ? parseInt(parts[2], 0) : 0);
        return new ItemStack(material, Math.max(1, amount), data);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
