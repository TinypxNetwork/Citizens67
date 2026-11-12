package net.citizensnpcs.packet.interaction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.packet.core.PacketNpcService;
import net.citizensnpcs.packet.core.PacketNpcState;
import net.citizensnpcs.packet.core.PacketViewerSession;
import net.citizensnpcs.packet.action.PacketActionTrait;
import net.citizensnpcs.trait.ClickRedirectTrait;
import net.citizensnpcs.trait.CommandTrait;

/** Main-thread interaction dispatcher shared by the PacketEvents adapter. */
public final class PacketInteractionService {
    private static final Map<UUID, Map<Integer, Long>> LAST_CLICKS = new HashMap<UUID, Map<Integer, Long>>();

    private PacketInteractionService() {
    }

    public static void clear(Player player) {
        LAST_CLICKS.remove(player.getUniqueId());
    }

    public static boolean handle(Player player, int entityId, PacketClickType type) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service == null || !player.isOnline())
            return false;

        PacketNpcState state = service.getByEntityId(entityId);
        if (state == null)
            return false;
        PacketViewerSession session = state.getSessions().get(player.getUniqueId());
        if (session == null || !session.isLinked())
            return false;

        double reach = Setting.PACKET_INTERACTION_REACH.asDouble();
        if (state.getLocation() == null || state.getLocation().getWorld() != player.getWorld()
                || state.getLocation().distanceSquared(player.getLocation()) > reach * reach)
            return false;

        long now = System.currentTimeMillis();
        Map<Integer, Long> clicks = LAST_CLICKS.get(player.getUniqueId());
        if (clicks == null) {
            clicks = new HashMap<Integer, Long>();
            LAST_CLICKS.put(player.getUniqueId(), clicks);
        }
        Long last = clicks.get(entityId);
        if (last != null && now - last < Setting.PACKET_CLICK_DEDUPE_MILLIS.asInt())
            return false;
        clicks.put(entityId, now);

        if (type == PacketClickType.RIGHT) {
            // Reuse Citizens' normal right-click, selector, command and shop handlers.
            org.bukkit.event.player.PlayerInteractEntityEvent event = new org.bukkit.event.player.PlayerInteractEntityEvent(
                    player, state.getBackingEntity());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                runActions(state.getNpc(), player, type);
            }
            return true;
        }

        NPC npc = state.getNpc();
        ClickRedirectTrait redirect = npc.getTraitNullable(ClickRedirectTrait.class);
        if (redirect != null) {
            npc = redirect.getRedirectNPC();
        }
        if (npc == null)
            return false;
        NPCLeftClickEvent event = new NPCLeftClickEvent(npc, player);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled() && npc.hasTrait(CommandTrait.class)) {
            npc.getTraitNullable(CommandTrait.class).dispatch(player, CommandTrait.Hand.LEFT);
        }
        if (!event.isCancelled()) {
            runActions(npc, player, type);
        }
        return true;
    }

    private static void runActions(NPC npc, Player player, PacketClickType type) {
        if (npc == null)
            return;
        ClickRedirectTrait redirect = npc.getTraitNullable(ClickRedirectTrait.class);
        if (redirect != null) {
            NPC target = redirect.getRedirectNPC();
            if (target != null) {
                npc = target;
            }
        }
        PacketActionTrait actions = npc.getTraitNullable(PacketActionTrait.class);
        if (actions != null) {
            actions.execute(player, type);
        }
    }
}
