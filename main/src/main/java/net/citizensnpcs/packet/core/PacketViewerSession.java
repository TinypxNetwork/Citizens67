package net.citizensnpcs.packet.core;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLinkToPlayerEvent;
import net.citizensnpcs.api.event.NPCSeenByPlayerEvent;
import net.citizensnpcs.api.event.NPCUnlinkFromPlayerEvent;
import net.citizensnpcs.packet.hologram.PacketHologramTrait;
import net.citizensnpcs.packet.sink.PacketSink;
import net.citizensnpcs.packet.skin.PacketSkinResolver;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.util.SkinProperty;

/** One NPC × one player packet view. This is the single source of truth for viewer state. */
public final class PacketViewerSession {
    private static final double MOVE_EPSILON = 0.0001D;

    private final Map<Integer, ItemStack> sentEquipment = new HashMap<Integer, ItemStack>();
    private final PacketSink sink;
    private final PacketNpcState state;
    private final Player viewer;
    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch, lastHeadYaw;
    private boolean linked;
    private String resolvedDisplayName;
    private String resolvedProfileName;
    private SkinProperty resolvedSkin;
    private BukkitTask tabRemoveTask;

    public PacketViewerSession(PacketNpcState state, Player viewer, PacketSink sink) {
        this.state = state;
        this.viewer = viewer;
        this.sink = sink;
        rememberPosition();
    }

    public PacketNpcState getState() {
        return state;
    }

    public Player getViewer() {
        return viewer;
    }

    public boolean isLinked() {
        return linked;
    }

    /** Fire the seen event, then player-info/skin, spawn, metadata, equipment and rotation. */
    public boolean link() {
        if (linked || !viewer.isOnline())
            return false;
        NPCSeenByPlayerEvent seen = new NPCSeenByPlayerEvent(state.getNpc(), viewer);
        Bukkit.getPluginManager().callEvent(seen);
        if (seen.isCancelled())
            return false;

        linked = true;
        rememberPosition();
        Bukkit.getPluginManager().callEvent(new NPCLinkToPlayerEvent(state.getNpc(), viewer));

        resolveAppearance();
        sink.playerInfoAdd(viewer, state, resolvedDisplayName, resolvedProfileName, resolvedSkin);
        sink.spawn(viewer, state);
        sink.metadata(viewer, state);
        sendAllEquipment();
        sink.look(viewer, state, state.getYaw(), state.getPitch());
        sink.headLook(viewer, state, state.getHeadYaw());

        if (state.getNpc().shouldRemoveFromTabList() && state.isPlayerType()) {
            int delay = Math.max(20, Setting.TABLIST_REMOVE_PACKET_DELAY.asTicks());
            tabRemoveTask = Bukkit.getScheduler().runTaskLater(CitizensAPI.getPlugin(), new Runnable() {
                @Override
                public void run() {
                    tabRemoveTask = null;
                    if (linked) {
                        sink.playerInfoRemove(viewer, state);
                    }
                }
            }, delay);
        }
        PacketHologramTrait hologram = state.getNpc().getTraitNullable(PacketHologramTrait.class);
        if (hologram != null) {
            hologram.onViewerLink(viewer);
        }
        return true;
    }

    public void tick() {
        if (!linked || !viewer.isOnline())
            return;
        Location location = state.getLocation();
        if (location == null)
            return;

        double dx = location.getX() - lastX;
        double dy = location.getY() - lastY;
        double dz = location.getZ() - lastZ;
        if (Math.abs(dx) > MOVE_EPSILON || Math.abs(dy) > MOVE_EPSILON || Math.abs(dz) > MOVE_EPSILON) {
            // Keep the old 1.8 behavior: any movement is a full teleport. M2 may switch small deltas to relative move.
            sink.teleport(viewer, state);
            lastX = location.getX();
            lastY = location.getY();
            lastZ = location.getZ();
        }

        float yaw = state.getYaw();
        float pitch = state.getPitch();
        float headYaw = state.getHeadYaw();
        LookClose look = state.getNpc().getTraitNullable(LookClose.class);
        if (look != null && look.isLookingAtViewer(viewer)) {
            Location from = state.getBackingEntity() instanceof LivingEntity
                    ? ((LivingEntity) state.getBackingEntity()).getEyeLocation() : location;
            Location target = viewer.getEyeLocation();
            double lx = target.getX() - from.getX();
            double ly = target.getY() - from.getY();
            double lz = target.getZ() - from.getZ();
            headYaw = (float) Math.toDegrees(Math.atan2(lz, lx)) - 90F;
            pitch = (float) -Math.toDegrees(Math.atan2(ly, Math.sqrt(lx * lx + lz * lz)));
            if (!look.isHeadOnly()) {
                yaw = headYaw;
            }
        }

        if (yaw != lastYaw || pitch != lastPitch) {
            sink.look(viewer, state, yaw, pitch);
            lastYaw = yaw;
            lastPitch = pitch;
        }
        if (headYaw != lastHeadYaw) {
            sink.headLook(viewer, state, headYaw);
            lastHeadYaw = headYaw;
        }

        if (state.isMetadataDirty()) {
            sink.metadata(viewer, state);
        }
        sendEquipmentDiff();
    }

    public void unlink() {
        if (!linked)
            return;
        linked = false;
        if (tabRemoveTask != null) {
            tabRemoveTask.cancel();
            tabRemoveTask = null;
        }
        sink.destroy(viewer, state);
        sink.playerInfoRemove(viewer, state);
        PacketHologramTrait hologram = state.getNpc().getTraitNullable(PacketHologramTrait.class);
        if (hologram != null) {
            hologram.onViewerUnlink(viewer);
        }
        Bukkit.getPluginManager().callEvent(new NPCUnlinkFromPlayerEvent(state.getNpc(), viewer));
    }

    private void resolveAppearance() {
        PacketSkinResolver.ResolvedSkin appearance = PacketSkinResolver.resolve(state, viewer);
        resolvedDisplayName = appearance.displayName;
        resolvedProfileName = appearance.profileName;
        resolvedSkin = appearance.skin;
    }

    private void rememberPosition() {
        Location location = state.getLocation();
        if (location != null) {
            lastX = location.getX();
            lastY = location.getY();
            lastZ = location.getZ();
        }
        lastYaw = state.getYaw();
        lastPitch = state.getPitch();
        lastHeadYaw = state.getHeadYaw();
    }

    private void sendAllEquipment() {
        sentEquipment.clear();
        for (Map.Entry<Integer, ItemStack> entry : state.getEquipment().entrySet()) {
            ItemStack item = entry.getValue();
            if (item != null) {
                sink.equipmentSlot(viewer, state, entry.getKey(), item);
                sentEquipment.put(entry.getKey(), item.clone());
            }
        }
    }

    private void sendEquipmentDiff() {
        for (int slot = 0; slot < 5; slot++) {
            ItemStack current = state.getEquipment().get(slot);
            ItemStack sent = sentEquipment.get(slot);
            boolean changed = current == null ? sent != null : sent == null || !current.equals(sent);
            if (!changed)
                continue;
            if (current == null) {
                sentEquipment.remove(slot);
            } else {
                sentEquipment.put(slot, current.clone());
            }
            sink.equipmentSlot(viewer, state, slot, current);
        }
    }
}
