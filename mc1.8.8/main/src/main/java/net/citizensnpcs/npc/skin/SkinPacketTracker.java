package net.citizensnpcs.npc.skin;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.trait.PacketNPC;
import net.citizensnpcs.util.NMS;

/**
 * Handles and synchronizes add and remove packets for Player type NPC's in order to properly apply the NPC skin.
 *
 * <p>
 * Used as one instance per NPC entity.
 * </p>
 */
public class SkinPacketTracker {
    private final SkinnableEntity entity;
    private final Map<UUID, PlayerEntry> inProgress = new HashMap<>(
            Math.max(128, Math.min(1024, Bukkit.getMaxPlayers() / 2)));
    private boolean isRemoved;
    private Skin skin;

    /**
     * Constructor.
     *
     * @param entity
     *            The skinnable entity the instance belongs to.
     */
    public SkinPacketTracker(SkinnableEntity entity) {
        Objects.requireNonNull(entity);

        this.entity = entity;
        skin = Skin.get(entity);

        if (LISTENER == null) {
            LISTENER = new PlayerListener();
            Bukkit.getPluginManager().registerEvents(LISTENER, CitizensAPI.getPlugin());
        }
    }

    /**
     * Get the NPC skin.
     */
    public Skin getSkin() {
        return skin;
    }

    /**
     * Notify the tracker that a remove packet has been sent to the specified player.
     *
     * @param playerId
     *            The ID of the player.
     */
    void notifyRemovePacketCancelled(UUID playerId) {
        PlayerEntry entry = inProgress.remove(playerId);
        if (entry != null) {
            entry.cancel();
        }
    }

    /**
     * Notify the tracker that a remove packet has been sent to the specified player.
     *
     * @param playerId
     *            The ID of the player.
     */
    void notifyRemovePacketSent(UUID playerId) {
        inProgress.remove(playerId);
    }

    /**
     * Notify that the NPC skin has been changed.
     */
    public void notifySkinChange(boolean forceUpdate) {
        skin = Skin.get(entity, forceUpdate);
        skin.applyAndRespawn(entity);
    }

    /**
     * Invoke when the NPC entity is removed.
     *
     * <p>
     * Sends remove packets to all players.
     * </p>
     */
    public void onRemoveNPC() {
        isRemoved = true;
        for (PlayerEntry entry : inProgress.values()) {
            entry.cancel();
        }
        inProgress.clear();

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        for (Player player : players) {
            if (player.hasMetadata("NPC"))
                continue;

            // send packet now and later to ensure removal from player list
            NMS.sendTabListRemove(player, entity.getBukkitEntity());
            TAB_LIST_REMOVER.sendPacket(player, entity);
        }
    }

    /**
     * Invoke when the NPC entity is spawned.
     */
    public void onSpawnNPC() {
        isRemoved = false;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (isRemoved || !entity.getNPC().isSpawned()
                        || entity.getNPC().getEntity() != entity.getBukkitEntity())
                    return;

                double viewDistance = Setting.NPC_SKIN_VIEW_DISTANCE.asDouble();
                updateNearbyViewers(viewDistance);
            }
        }.runTaskLater(CitizensAPI.getPlugin(), 15);
    }

    private void scheduleRemovePacket(PlayerEntry entry) {
        if (isRemoved || !CitizensAPI.hasImplementation() || !CitizensAPI.getPlugin().isEnabled()
                || !entity.getNPC().shouldRemoveFromTabList())
            return;

        int delay = Setting.TABLIST_REMOVE_PACKET_DELAY.asTicks();
        if (entity.getNPC().hasTrait(PacketNPC.class)) {
            // 1.8 clients need time to load the texture before their player-info entry is removed.
            delay = Math.max(20, delay);
        }
        entry.removeTask = Bukkit.getScheduler().runTaskLater(CitizensAPI.getPlugin(),
                () -> TAB_LIST_REMOVER.sendPacket(entry.player, entity), delay);
    }

    /**
     * Send skin related packets to all nearby players within the specified block radius.
     *
     * @param radius
     *            The radius.
     */
    public void updateNearbyViewers(double radius) {
        Player from = entity.getBukkitEntity();

        CitizensAPI.getLocationLookup().getNearbyPlayers(from.getLocation(), radius).forEach(player -> {
            if (!player.canSee(from) || player.hasMetadata("NPC"))
                return;
            updateViewer(player);
        });
    }

    /**
     * Send skin related packets to a player.
     *
     * @param player
     *            The player.
     */
    public void updateViewer(Player player) {
        Objects.requireNonNull(player);

        if (isRemoved || entity.getNPC().getEntity() != entity.getBukkitEntity() || player.hasMetadata("NPC"))
            return;

        PlayerEntry entry = inProgress.get(player.getUniqueId());
        if (entry != null) {
            entry.cancel();
        } else {
            entry = new PlayerEntry(player);
        }
        TAB_LIST_REMOVER.cancelPackets(player, entity);

        inProgress.put(player.getUniqueId(), entry);
        skin = Skin.get(entity);
        skin.apply(entity);
        if (NMS.sendTabListAdd(player, entity.getBukkitEntity())) {
            scheduleRemovePacket(entry);
        }
    }

    private static class PlayerEntry {
        Player player;
        BukkitTask removeTask;

        PlayerEntry(Player player) {
            this.player = player;
        }

        // cancel previous packet tasks so they do not interfere with
        // new tasks
        void cancel() {
            if (removeTask != null) {
                removeTask.cancel();
            }
        }
    }

    private static class PlayerListener implements Listener {
        @EventHandler
        private void onPlayerQuit(PlayerQuitEvent event) {
            // this also causes any entries in the "inProgress" field to
            // be removed.
            TAB_LIST_REMOVER.cancelPackets(event.getPlayer());
        }
    }

    private static PlayerListener LISTENER;
    private static TabListRemover TAB_LIST_REMOVER = new TabListRemover();
}
