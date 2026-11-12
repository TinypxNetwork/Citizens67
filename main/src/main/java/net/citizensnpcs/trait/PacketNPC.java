package net.citizensnpcs.trait;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.util.RemoveReason;
import net.citizensnpcs.npc.EntityController;
import net.citizensnpcs.packet.core.PacketNpcService;
import net.citizensnpcs.util.NMS;

/**
 * Marks an NPC as packet-only. The actual viewer/session state lives in {@link PacketNpcService}; this trait only
 * adapts Citizens' spawn/despawn lifecycle and prevents the backing entity from being registered with the world.
 */
@TraitName("packet")
public class PacketNPC extends Trait {
    public PacketNPC() {
        super("packet");
    }

    @Override
    public void onDespawn() {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.unregister(npc);
        }
    }

    @Override
    public void onRemove(RemoveReason reason) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.unregister(npc);
        }
        if (reason == RemoveReason.REMOVAL && !npc.hasTrait(PacketNPC.class)) {
            Location at = npc.getEntity() == null ? null : npc.getEntity().getLocation();
            EntityType type = npc.getOrAddTrait(MobType.class).getType();
            if (at != null) {
                npc.despawn(DespawnReason.PENDING_RESPAWN);
            }
            npc.setBukkitEntityType(type);
            if (at != null) {
                Bukkit.getScheduler().runTask(CitizensAPI.getPlugin(), new Runnable() {
                    @Override
                    public void run() {
                        if (!npc.isSpawned()) {
                            npc.spawn(at, SpawnReason.RESPAWN);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onSpawn() {
        if (npc.getEntity() != null && npc.getEntity().isValid()) {
            // /npc packet --enabled true can convert an already-spawned real entity.
            Location at = npc.getEntity().getLocation();
            EntityType type = npc.getEntity().getType();
            npc.despawn(DespawnReason.PENDING_RESPAWN);
            npc.setBukkitEntityType(type);
            npc.spawn(at, SpawnReason.RESPAWN);
            return;
        }
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.register(this);
        }
    }

    public EntityController wrap(EntityController controller) {
        return controller instanceof PacketController ? controller : new PacketController(controller);
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
            PacketNpcService service = PacketNpcService.getInstance();
            if (service != null) {
                service.unregister(npc);
            }
            base.die();
        }

        @Override
        public Entity getBukkitEntity() {
            return base.getBukkitEntity();
        }

        @Override
        public void remove() {
            PacketNpcService service = PacketNpcService.getInstance();
            if (service != null) {
                service.unregister(npc);
            }
            base.remove();
        }

        @Override
        public boolean spawn(Location at) {
            NMS.setLocationDirectly(base.getBukkitEntity(), at);
            return true;
        }
    }
}
