package net.citizensnpcs.trait;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.util.RemoveReason;
import net.citizensnpcs.npc.EntityController;
import net.citizensnpcs.util.EntityPacketTracker;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.PlayerUpdateTask;

@TraitName("packet")
public class PacketNPC extends Trait {
    private static final Map<Integer, PacketNPC> SPAWNED = new ConcurrentHashMap<>();
    private final Map<UUID, Player> viewers = new HashMap<>();
    private Entity entity;
    private EntityPacketTracker packetTracker;
    private int viewCounter;

    public PacketNPC() {
        super("packet");
    }

    public static PacketNPC getByEntityId(int id) {
        return SPAWNED.get(id);
    }

    public static void hideAll(Player player) {
        for (PacketNPC packet : SPAWNED.values()) {
            packet.hide(player);
        }
    }

    public EntityPacketTracker getPacketTracker() {
        return packetTracker;
    }

    public boolean isTracking(Player player) {
        if (packetTracker instanceof ViewerTracker)
            return ((ViewerTracker) packetTracker).isTracking(player);
        return viewers.get(player.getUniqueId()) == player;
    }

    public void hide(Player player) {
        viewers.remove(player.getUniqueId());
        if (packetTracker != null) {
            packetTracker.unlink(player);
        }
    }

    @Override
    public void onDespawn() {
        clear();
    }

    @Override
    public void onRemove(RemoveReason reason) {
        if (reason == RemoveReason.REMOVAL && !npc.hasTrait(PacketNPC.class)) {
            Location at = npc.getEntity() == null ? null : npc.getEntity().getLocation();
            EntityType type = npc.getOrAddTrait(MobType.class).getType();
            if (at != null) {
                npc.despawn(DespawnReason.PENDING_RESPAWN);
            } else {
                clear();
            }
            npc.setBukkitEntityType(type);
            if (at != null) {
                Bukkit.getScheduler().runTask(CitizensAPI.getPlugin(), () -> {
                    if (!npc.isSpawned()) {
                        npc.spawn(at, SpawnReason.RESPAWN);
                    }
                });
            }
        } else {
            clear();
        }
    }

    @Override
    public void onSpawn() {
        if (npc.getEntity().isValid()) {
            // /npc packet --enabled true can convert an existing world entity, too.
            Location at = npc.getEntity().getLocation();
            EntityType type = npc.getEntity().getType();
            npc.despawn(DespawnReason.PENDING_RESPAWN);
            npc.setBukkitEntityType(type);
            npc.spawn(at, SpawnReason.RESPAWN);
            return;
        }
        entity = npc.getEntity();
        packetTracker = NMS.createPacketTracker(entity);
        SPAWNED.put(entity.getEntityId(), this);
        viewCounter = 0;
    }

    @Override
    public void run() {
        if (packetTracker == null || !npc.isSpawned())
            return;
        if (viewCounter-- <= 0) {
            viewCounter = 4;
            Location at = entity.getLocation();
            double range = npc.data().get(NPC.Metadata.TRACKING_RANGE, 64);
            double rangeSquared = range * range;
            for (Iterator<Player> itr = viewers.values().iterator(); itr.hasNext();) {
                Player player = itr.next();
                if (!canSee(player, at, rangeSquared) || !isTracking(player)) {
                    itr.remove();
                    packetTracker.unlink(player);
                }
            }
            for (Player player : CitizensAPI.getLocationLookup().getNearbyPlayers(at, range)) {
                if (!viewers.containsKey(player.getUniqueId()) && canSee(player, at, rangeSquared)) {
                    packetTracker.link(player);
                    if (packetTracker instanceof ViewerTracker
                            && !((ViewerTracker) packetTracker).isTracking(player))
                        continue;
                    viewers.put(player.getUniqueId(), player);
                }
            }
        }
        packetTracker.run();
    }

    private boolean canSee(Player player, Location at, double rangeSquared) {
        // CraftPlayer.isOnline() can also be true for a stale object after the same UUID rejoins.
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
                && !player.hasMetadata("NPC") && player.getWorld() == at.getWorld()
                && player.getLocation().distanceSquared(at) <= rangeSquared
                && (!(entity instanceof Player) || player.canSee((Player) entity));
    }

    private void clear() {
        if (entity != null) {
            SPAWNED.remove(entity.getEntityId(), this);
            PlayerUpdateTask.deregisterPlayer(entity);
        }
        if (packetTracker != null) {
            packetTracker.unlinkAll(null);
            packetTracker = null;
        }
        viewers.clear();
        entity = null;
    }

    public EntityController wrap(EntityController controller) {
        return controller instanceof PacketController ? controller : new PacketController(controller);
    }

    /** Distinguishes an accepted viewer from a cancelled NPCSeenByPlayerEvent. */
    public interface ViewerTracker extends EntityPacketTracker {
        boolean isTracking(Player player);
    }

    private class PacketController implements EntityController {
        private final EntityController base;

        PacketController(EntityController base) {
            this.base = base;
        }

        @Override
        public void create(Location at, NPC npc) {
            base.create(at, npc);
        }

        @Override
        public void die() {
            PlayerUpdateTask.deregisterPlayer(getBukkitEntity());
            clear();
            base.die();
        }

        @Override
        public Entity getBukkitEntity() {
            return base.getBukkitEntity();
        }

        @Override
        public void remove() {
            PlayerUpdateTask.deregisterPlayer(getBukkitEntity());
            clear();
            // Release the backing object even if spawning was cancelled before onSpawn.
            base.remove();
        }

        @Override
        public boolean spawn(Location at) {
            NMS.setLocationDirectly(base.getBukkitEntity(), at);
            PlayerUpdateTask.registerPlayer(getBukkitEntity());
            return true;
        }
    }
}
