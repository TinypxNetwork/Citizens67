package net.citizensnpcs.packet.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.packet.hologram.PacketHologramTrait;
import net.citizensnpcs.packet.sink.PacketSink;
import net.citizensnpcs.packet.skin.PacketSkinCache;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.PacketNPC;
import net.citizensnpcs.api.util.Messaging;

/** Owns packet NPC state, viewer sessions and the single tick loop. */
public final class PacketNpcService implements Runnable {
    private static PacketNpcService instance;

    public static PacketNpcService getInstance() {
        return instance;
    }

    public static PacketNpcService init(PacketSink sink) {
        if (instance == null) {
            instance = new PacketNpcService(sink);
            PacketSkinCache.getInstance().load();
        }
        return instance;
    }

    private final PacketSessionRegistry registry = new PacketSessionRegistry();
    private final PacketSink sink;
    private boolean shutdown;
    private BukkitTask task;
    private double averageTickMicros;
    private long lastTickNanos;
    private int tickCounter;

    private PacketNpcService(PacketSink sink) {
        this.sink = sink;
    }

    public void start(Plugin plugin) {
        if (task != null)
            return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this, 0L, 1L);
    }

    public void register(PacketNPC trait) {
        NPC npc = trait.getNPC();
        if (registry.getByNpc(npc.getUniqueId()) != null)
            return;
        PacketNpcState state = new PacketNpcState(npc, npc.getEntity() == null ? PacketEntityIdAllocator.allocate()
                : npc.getEntity().getEntityId());
        boolean dynamic = npc.hasTrait(LookClose.class) || npc.hasTrait(HologramTrait.class)
                || "citizens-temporary".equals(npc.getOwningRegistry().getName());
        state.setDynamic(dynamic);
        state.setUpdateInterval(dynamic ? Setting.PACKET_DYNAMIC_UPDATE_INTERVAL.asInt()
                : Setting.PACKET_STATIC_UPDATE_INTERVAL.asInt());
        registry.register(state);
    }

    public void unregister(NPC npc) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state == null)
            return;
        for (PacketViewerSession session : new ArrayList<PacketViewerSession>(state.getSessions().values())) {
            session.unlink();
            registry.removeSession(session);
        }
        registry.unregister(state);
    }

    @Override
    public void run() {
        if (shutdown)
            return;
        long started = System.nanoTime();
        tickCounter++;
        for (PacketNpcState state : new ArrayList<PacketNpcState>(registry.all())) {
            NPC npc = state.getNpc();
            if (!npc.isSpawned())
                continue;

            refreshDynamic(state);
            boolean due = state.isDynamic() || tickCounter % state.getUpdateInterval() == 0;
            if (due) {
                try {
                    if (npc instanceof CitizensNPC) {
                        ((CitizensNPC) npc).update();
                    }
                } catch (Throwable t) {
                    Messaging.severe("Exception updating packet NPC " + npc.getId() + ": " + t.getMessage());
                }
                state.updateFromEntity();
            }

            if (tickCounter % Math.max(1, Setting.PACKET_VIEWER_SCAN_INTERVAL.asInt()) == 0) {
                scanViewers(state);
            }

            for (PacketViewerSession session : new ArrayList<PacketViewerSession>(state.getSessions().values())) {
                if (session.getViewer().isOnline()) {
                    session.tick();
                }
            }
            PacketHologramTrait hologram = npc.getTraitNullable(PacketHologramTrait.class);
            if (hologram != null) {
                hologram.tick();
            }
            if (state.isMetadataDirty() && !state.getSessions().isEmpty()) {
                state.clearMetadataDirty();
            }
        }
        if (sink != null) {
            sink.flush();
        }
        lastTickNanos = System.nanoTime() - started;
        double micros = lastTickNanos / 1000.0D;
        averageTickMicros = averageTickMicros == 0 ? micros : averageTickMicros * 0.95D + micros * 0.05D;
    }

    private void refreshDynamic(PacketNpcState state) {
        NPC npc = state.getNpc();
        boolean dynamic = npc.hasTrait(LookClose.class) || npc.hasTrait(HologramTrait.class)
                || "citizens-temporary".equals(npc.getOwningRegistry().getName());
        if (dynamic != state.isDynamic()) {
            state.setDynamic(dynamic);
            state.setUpdateInterval(dynamic ? Setting.PACKET_DYNAMIC_UPDATE_INTERVAL.asInt()
                    : Setting.PACKET_STATIC_UPDATE_INTERVAL.asInt());
        }
    }

    private void scanViewers(PacketNpcState state) {        Location location = state.getLocation();
        if (location == null || location.getWorld() == null)
            return;

        double range = state.getNpc().data().get(NPC.Metadata.TRACKING_RANGE, 64);
        double rangeSquared = range * range;
        Set<UUID> nearby = new HashSet<UUID>();
        Iterable<Player> players = CitizensAPI.getLocationLookup().getNearbyPlayers(location, range);
        for (Player player : players) {
            if (!canSee(player, state, location, rangeSquared))
                continue;
            nearby.add(player.getUniqueId());
            if (state.getSessions().containsKey(player.getUniqueId()))
                continue;
            PacketViewerSession session = new PacketViewerSession(state, player, sink);
            if (session.link()) {
                registry.addSession(session);
            }
        }

        for (PacketViewerSession session : new ArrayList<PacketViewerSession>(state.getSessions().values())) {
            if (!nearby.contains(session.getViewer().getUniqueId())) {
                session.unlink();
                registry.removeSession(session);
            }
        }
    }

    private boolean canSee(Player player, PacketNpcState state, Location location, double rangeSquared) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player && !player.hasMetadata("NPC")
                && player.getWorld() == location.getWorld()
                && player.getLocation().distanceSquared(location) <= rangeSquared
                && (!state.isPlayerType() || player.canSee((Player) state.getBackingEntity()));
    }

    public void onPlayerQuit(Player player) {
        for (PacketViewerSession session : registry.removePlayer(player.getUniqueId())) {
            session.unlink();
        }
        if (sink != null) {
            sink.flush();
        }
    }

    public void onWorldChange(Player player) {
        onPlayerQuit(player);
    }

    public void hideAll(Player player) {
        onPlayerQuit(player);
    }

    public void show(NPC npc, Player player) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state == null || state.getSessions().containsKey(player.getUniqueId()))
            return;
        PacketViewerSession session = new PacketViewerSession(state, player, sink);
        if (session.link()) {
            registry.addSession(session);
        }
        if (sink != null) {
            sink.flush();
        }
    }

    public void hide(NPC npc, Player player) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state == null)
            return;
        PacketViewerSession session = state.getSessions().get(player.getUniqueId());
        if (session != null) {
            session.unlink();
            registry.removeSession(session);
        }
        if (sink != null) {
            sink.flush();
        }
    }

    public void markMetadataDirty(NPC npc) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state != null) {
            state.markMetadataDirty();
        }
    }

    public void refresh(NPC npc) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state != null) {
            state.updateFromEntity();
        }
    }

    public PacketNpcState getByEntityId(int entityId) {
        return registry.getByEntityId(entityId);
    }

    public PacketNpcState getState(NPC npc) {
        return registry.getByNpc(npc.getUniqueId());
    }

    public void scanNow(NPC npc) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        if (state != null) {
            state.updateFromEntity();
            scanViewers(state);
        }
    }

    public String getStats() {
        int sessions = 0;
        for (PacketNpcState state : registry.all()) {
            sessions += state.getSessions().size();
        }
        return "packet NPCs=" + registry.all().size() + ", viewers=" + sessions + ", sink="
                + (isAvailable() ? "ready" : "unavailable") + String.format(", tick=%.2fms/%.2fms",
                        lastTickNanos / 1_000_000.0D, averageTickMicros / 1000.0D);
    }

    public PacketViewerSession getSession(NPC npc, Player player) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        return state == null ? null : state.getSessions().get(player.getUniqueId());
    }

    public Set<Player> getViewers(NPC npc) {
        PacketNpcState state = registry.getByNpc(npc.getUniqueId());
        Set<Player> viewers = new HashSet<Player>();
        if (state != null) {
            for (PacketViewerSession session : state.getSessions().values()) {
                viewers.add(session.getViewer());
            }
        }
        return viewers;
    }

    public boolean isAvailable() {
        return sink != null && sink.isAvailable();
    }

    public PacketSink getSink() {
        return sink;
    }

    public void shutdown() {
        shutdown = true;
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (PacketNpcState state : new ArrayList<PacketNpcState>(registry.all())) {
            unregister(state.getNpc());
        }
        if (sink != null) {
            sink.shutdown();
        }
        PacketSkinCache.getInstance().save();
        instance = null;
    }
}
