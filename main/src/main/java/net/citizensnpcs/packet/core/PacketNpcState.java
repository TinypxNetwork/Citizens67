package net.citizensnpcs.packet.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import com.mojang.authlib.GameProfile;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.NPCNames;
import net.citizensnpcs.util.SkinProperty;

/** Mutable packet-side state for one Citizens NPC. */
public final class PacketNpcState {
    private static final int[] EQUIPMENT_SLOTS = { 0, 1, 2, 3, 4 };

    private final NPC npc;
    private final int entityId;
    private final Entity backingEntity;
    private final UUID uuid;
    private final EntityType type;
    private final Map<Integer, ItemStack> equipment = new HashMap<Integer, ItemStack>();
    private final Map<UUID, PacketViewerSession> sessions = new HashMap<UUID, PacketViewerSession>();
    private boolean dynamic;
    private String displayName = "";
    private float headYaw;
    private Location location;
    private boolean metadataDirty = true;
    private float pitch;
    private String profileName = "";
    private SkinProperty skin;
    private int updateInterval = 20;
    private float yaw;

    public PacketNpcState(NPC npc, int entityId) {
        this.npc = npc;
        this.entityId = entityId;
        this.backingEntity = npc.getEntity();
        this.uuid = backingEntity == null ? npc.getUniqueId() : backingEntity.getUniqueId();
        EntityType resolvedType = backingEntity == null ? npc.getOrAddTrait(MobType.class).getType()
                : backingEntity.getType();
        this.type = resolvedType == null ? EntityType.PLAYER : resolvedType;
        updateFromEntity();
    }

    public void clearMetadataDirty() {
        metadataDirty = false;
    }

    public Entity getBackingEntity() {
        return backingEntity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getEntityId() {
        return entityId;
    }

    public Map<Integer, ItemStack> getEquipment() {
        return equipment;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public Location getLocation() {
        return location;
    }

    public NPC getNpc() {
        return npc;
    }

    public float getPitch() {
        return pitch;
    }

    public String getProfileName() {
        return profileName;
    }

    public Map<UUID, PacketViewerSession> getSessions() {
        return sessions;
    }

    public SkinProperty getSkin() {
        return skin;
    }

    public EntityType getType() {
        return type;
    }

    public int getUpdateInterval() {
        return updateInterval;
    }

    public UUID getUuid() {
        return uuid;
    }

    public float getYaw() {
        return yaw;
    }

    public boolean isDynamic() {
        return dynamic;
    }

    public boolean isMetadataDirty() {
        return metadataDirty;
    }

    public boolean isPlayerType() {
        return type == EntityType.PLAYER;
    }

    public void markMetadataDirty() {
        metadataDirty = true;
    }

    public void setDynamic(boolean dynamic) {
        this.dynamic = dynamic;
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = Math.max(1, updateInterval);
    }

    /** Refresh cached values from the backing Citizens/NMS entity. */
    public void updateFromEntity() {
        if (backingEntity == null)
            return;

        Location current = backingEntity.getLocation();
        if (current != null) {
            location = current.clone();
            yaw = current.getYaw();
            pitch = current.getPitch();
        }
        try {
            headYaw = NMS.getHeadYaw(backingEntity);
        } catch (Throwable ignored) {
            headYaw = yaw;
        }

        if (backingEntity instanceof LivingEntity) {
            EntityEquipment eq = ((LivingEntity) backingEntity).getEquipment();
            if (eq != null) {
                for (int slot : EQUIPMENT_SLOTS) {
                    ItemStack item = getEquipmentItem(eq, slot);
                    ItemStack old = equipment.get(slot);
                    if (item == null) {
                        equipment.remove(slot);
                    } else if (old == null || !old.equals(item)) {
                        equipment.put(slot, item.clone());
                    }
                }
            }
        }

        if (backingEntity instanceof Player) {
            GameProfile profile = NMS.getProfile((Player) backingEntity);
            if (profile != null) {
                profileName = profile.getName() == null ? npc.getName() : profile.getName();
                skin = SkinProperty.fromMojangProfile(profile);
            }
            if (profileName == null || profileName.isEmpty()) {
                profileName = npc.getName();
            }
            displayName = NPCNames.tabListName(npc);
        } else {
            profileName = npc.getName();
            displayName = npc.getFullName();
        }
    }

    private static ItemStack getEquipmentItem(EntityEquipment eq, int slot) {
        switch (slot) {
            case 0:
                return eq.getItemInHand();
            case 1:
                return eq.getBoots();
            case 2:
                return eq.getLeggings();
            case 3:
                return eq.getChestplate();
            case 4:
                return eq.getHelmet();
            default:
                return null;
        }
    }
}
