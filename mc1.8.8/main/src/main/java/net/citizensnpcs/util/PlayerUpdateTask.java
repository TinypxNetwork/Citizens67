package net.citizensnpcs.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import net.citizensnpcs.api.npc.AbstractNPC;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.npc.ai.NPCHolder;
import net.citizensnpcs.trait.PacketNPC;

public class PlayerUpdateTask extends BukkitRunnable {
    private final Map<UUID, PlayerTick> players = new LinkedHashMap<>();

    @Override
    public void cancel() {
        super.cancel();
        players.clear();
        PLAYERS_PENDING_ADD.clear();
        PLAYERS_PENDING_REMOVE.clear();
    }

    @Override
    public void run() {
        for (Entity entity : PLAYERS_PENDING_REMOVE) {
            PlayerTick current = players.get(entity.getUniqueId());
            // A despawned entity and its replacement share a UUID, but only the old instance is removed.
            if (current != null && current.entity == entity) {
                players.remove(entity.getUniqueId());
            }
        }
        PLAYERS_PENDING_REMOVE.clear();
        for (Entity entity : PLAYERS_PENDING_ADD) {
            NPC next = ((NPCHolder) entity).getNPC();
            PlayerTick previous = players.get(entity.getUniqueId());
            // Packet spawning and post-spawn initialization can register the same object on different ticks.
            if (previous != null && previous.entity == entity)
                continue;
            if (previous != null) {
                NPC old = ((NPCHolder) previous.entity).getNPC();
                if (old != next) {
                    Messaging.severe("Player registered twice with different NPC instances", entity.getUniqueId());
                }
                if (previous.entity instanceof Player) {
                    NMS.removeFromWorld(previous.entity);
                    NMS.remove(previous.entity);
                } else {
                    previous.entity.remove();
                }
            }
            if (next.hasTrait(PacketNPC.class)) {
                players.put(entity.getUniqueId(), new PlayerTick(entity, () -> ((AbstractNPC) next).update()));
            } else {
                players.put(entity.getUniqueId(), new PlayerTick(entity, NMS.playerTicker((Player) entity)));
            }
        }
        PLAYERS_PENDING_ADD.clear();

        for (PlayerTick player : players.values()) {
            player.run();
        }
    }

    private static class PlayerTick implements Runnable {
        private final Entity entity;
        private final Runnable tick;

        public PlayerTick(Entity entity, Runnable tick) {
            this.entity = entity;
            this.tick = tick;
        }

        @Override
        public void run() {
            tick.run();
        }
    }

    public static void deregisterPlayer(org.bukkit.entity.Entity entity) {
        if (entity == null)
            return;
        PLAYERS_PENDING_ADD.removeIf(pending -> pending == entity);
        PLAYERS_PENDING_REMOVE.add(entity);
    }

    public static void registerPlayer(org.bukkit.entity.Entity entity) {
        if (entity == null)
            return;
        PLAYERS_PENDING_REMOVE.remove(entity);
        PLAYERS_PENDING_ADD.removeIf(pending -> pending == entity);
        PLAYERS_PENDING_ADD.add(entity);
    }

    private static final List<Entity> PLAYERS_PENDING_ADD = new ArrayList<>();
    private static final Set<Entity> PLAYERS_PENDING_REMOVE = Collections.newSetFromMap(new IdentityHashMap<>());
}
