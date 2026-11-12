package net.citizensnpcs.packet.action;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.packet.interaction.PacketClickType;

/** Persisted action chain executed when a packet NPC is clicked. */
@TraitName("packet-actions")
public class PacketActionTrait extends Trait {
    private final List<PacketAction> actions = new ArrayList<PacketAction>();
    private final Map<UUID, Map<Integer, Long>> cooldowns = new HashMap<UUID, Map<Integer, Long>>();

    public PacketActionTrait() {
        super("packet-actions");
    }

    public void add(PacketAction action) {
        actions.add(action);
    }

    public void clear() {
        actions.clear();
        cooldowns.clear();
    }

    public List<PacketAction> getActions() {
        return Collections.unmodifiableList(actions);
    }

    public void remove(int index) {
        if (index >= 0 && index < actions.size()) {
            actions.remove(index);
            cooldowns.clear();
        }
    }

    public void execute(Player player, PacketClickType click) {
        if (!npc.isSpawned() || actions.isEmpty())
            return;
        executeFrom(player, click == PacketClickType.LEFT ? PacketActionTrigger.LEFT : PacketActionTrigger.RIGHT, 0);
    }

    private void executeFrom(final Player player, final PacketActionTrigger trigger, final int index) {
        if (!player.isOnline() || !npc.isSpawned() || index >= actions.size())
            return;
        final PacketAction action = actions.get(index);
        if (action.type == null || (action.trigger != PacketActionTrigger.ANY && action.trigger != trigger)) {
            executeFrom(player, trigger, index + 1);
            return;
        }
        if (action.permission != null && !action.permission.isEmpty()
                && !player.hasPermission(action.permission))
            return;
        if (!checkCooldown(player, index, action))
            return;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!player.isOnline())
                    return;
                if (runAction(player, trigger, index, action)) {
                    executeFrom(player, trigger, index + 1);
                }
            }
        };
        if (action.delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(CitizensAPI.getPlugin(), runnable, action.delayTicks);
        } else {
            runnable.run();
        }
    }

    private boolean checkCooldown(Player player, int index, PacketAction action) {
        if (action.cooldownMillis <= 0)
            return true;
        Map<Integer, Long> map = cooldowns.get(player.getUniqueId());
        if (map == null) {
            map = new HashMap<Integer, Long>();
            cooldowns.put(player.getUniqueId(), map);
        }
        long now = System.currentTimeMillis();
        Long last = map.get(index);
        if (last != null && now - last < action.cooldownMillis)
            return false;
        map.put(index, now);
        return true;
    }

    private boolean runAction(Player player, PacketActionTrigger trigger, int index, PacketAction action) {
        String value = action.value == null ? "" : action.value;
        switch (action.type) {
            case CONSOLE:
            case COMMAND:
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
                return true;
            case PLAYER_COMMAND:
                player.performCommand(value);
                return true;
            case MESSAGE:
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', replacePlaceholders(player, value)));
                return true;
            case SOUND:
                playSound(player, value);
                return true;
            case SERVER:
                connectToServer(player, value);
                return true;
            case WAIT:
                int ticks = parseInt(value, 20);
                Bukkit.getScheduler().runTaskLater(CitizensAPI.getPlugin(), new Runnable() {
                    @Override
                    public void run() {
                        executeFrom(player, trigger, index + 1);
                    }
                }, Math.max(1, ticks));
                return false;
            case PERMISSION:
                return player.hasPermission(value);
            case ITEM:
                return hasItem(player, value);
            case RANDOM:
                if (index + 1 < actions.size()) {
                    int next = index + 1 + ThreadLocalRandom.current().nextInt(actions.size() - index - 1);
                    executeFrom(player, trigger, next);
                }
                return false;
            default:
                return true;
        }
    }

    private String replacePlaceholders(Player player, String value) {
        return value.replace("{player}", player.getName()).replace("{npc}", npc.getName())
                .replace("{world}", player.getWorld().getName());
    }

    private boolean hasItem(Player player, String value) {
        String[] parts = value.split(":");
        Material material = Material.matchMaterial(parts[0]);
        if (material == null)
            return false;
        int amount = parts.length > 1 ? parseInt(parts[1], 1) : 1;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
                if (count >= amount)
                    return true;
            }
        }
        return false;
    }

    private void playSound(Player player, String value) {
        String[] parts = value.split(":");
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0F;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0F;
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void connectToServer(Player player, String server) {
        if (server == null || server.isEmpty())
            return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.sendPluginMessage(CitizensAPI.getPlugin(), "BungeeCord", bytes.toByteArray());
        } catch (IOException ex) {
            Messaging.severe("Failed to send BungeeCord connect for NPC " + npc.getId() + ": " + ex.getMessage());
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    @Override
    public void onDespawn() {
        cooldowns.clear();
    }

    @Override
    public void onRemove() {
        cooldowns.clear();
    }

    @Override
    public void load(DataKey root) {
        actions.clear();
        for (DataKey key : root.getRelative("actions").getIntegerSubKeys()) {
            PacketActionType type = PacketActionType.parse(key.getString("type", ""));
            if (type == null)
                continue;
            PacketAction action = new PacketAction();
            action.trigger = PacketActionTrigger.parse(key.getString("trigger", "any"));
            action.type = type;
            action.value = key.getString("value", "");
            action.cooldownMillis = key.getInt("cooldown", 0);
            action.delayTicks = key.getInt("delay", 0);
            action.permission = key.getString("permission", "");
            actions.add(action);
        }
    }

    @Override
    public void save(DataKey root) {
        root.removeKey("actions");
        for (int i = 0; i < actions.size(); i++) {
            PacketAction action = actions.get(i);
            DataKey key = root.getRelative("actions." + i);
            key.setString("trigger", action.trigger.name().toLowerCase());
            key.setString("type", action.type == null ? "" : action.type.getId());
            key.setString("value", action.value == null ? "" : action.value);
            key.setInt("cooldown", (int) Math.min(Integer.MAX_VALUE, action.cooldownMillis));
            key.setInt("delay", action.delayTicks);
            key.setString("permission", action.permission == null ? "" : action.permission);
        }
    }

    @Override
    public String toString() {
        return "PacketActionTrait{npc=" + (npc == null ? "?" : npc.getId()) + ", actions=" + actions.size() + "}";
    }
}
