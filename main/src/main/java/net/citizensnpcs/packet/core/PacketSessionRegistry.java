package net.citizensnpcs.packet.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;

/** Indexes packet NPC states by entity id/NPC UUID and viewer sessions by player UUID. */
public final class PacketSessionRegistry {
    private final Map<Integer, PacketNpcState> byEntityId = new HashMap<Integer, PacketNpcState>();
    private final Map<UUID, PacketNpcState> byNpc = new HashMap<UUID, PacketNpcState>();
    private final Map<UUID, Set<PacketViewerSession>> byPlayer = new HashMap<UUID, Set<PacketViewerSession>>();

    public void register(PacketNpcState state) {
        byEntityId.put(state.getEntityId(), state);
        byNpc.put(state.getNpc().getUniqueId(), state);
    }

    public void unregister(PacketNpcState state) {
        byEntityId.remove(state.getEntityId());
        byNpc.remove(state.getNpc().getUniqueId());
        for (PacketViewerSession session : new ArrayList<PacketViewerSession>(state.getSessions().values())) {
            removeSession(session);
        }
    }

    public PacketNpcState getByEntityId(int entityId) {
        return byEntityId.get(entityId);
    }

    public PacketNpcState getByNpc(UUID uuid) {
        return byNpc.get(uuid);
    }

    public Collection<PacketNpcState> all() {
        return Collections.unmodifiableCollection(byNpc.values());
    }

    public void addSession(PacketViewerSession session) {
        UUID playerId = session.getViewer().getUniqueId();
        session.getState().getSessions().put(playerId, session);
        Set<PacketViewerSession> sessions = byPlayer.get(playerId);
        if (sessions == null) {
            sessions = new HashSet<PacketViewerSession>();
            byPlayer.put(playerId, sessions);
        }
        sessions.add(session);
    }

    public void removeSession(PacketViewerSession session) {
        UUID playerId = session.getViewer().getUniqueId();
        session.getState().getSessions().remove(playerId);
        Set<PacketViewerSession> sessions = byPlayer.get(playerId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                byPlayer.remove(playerId);
            }
        }
    }

    public Set<PacketViewerSession> getSessions(Player player) {
        Set<PacketViewerSession> sessions = byPlayer.get(player.getUniqueId());
        return sessions == null ? Collections.<PacketViewerSession>emptySet() : sessions;
    }

    public List<PacketViewerSession> removePlayer(UUID playerId) {
        Set<PacketViewerSession> sessions = byPlayer.remove(playerId);
        if (sessions == null)
            return Collections.emptyList();
        List<PacketViewerSession> result = new ArrayList<PacketViewerSession>(sessions);
        for (PacketViewerSession session : result) {
            session.getState().getSessions().remove(playerId);
        }
        return result;
    }
}
