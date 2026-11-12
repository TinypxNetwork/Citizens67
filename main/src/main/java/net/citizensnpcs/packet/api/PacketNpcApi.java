package net.citizensnpcs.packet.api;

import java.util.Collections;
import java.util.Set;

import org.bukkit.entity.Player;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.packet.core.PacketNpcService;
import net.citizensnpcs.trait.PacketNPC;

/** Small public surface for plugins that need to inspect or control packet NPC visibility. */
public final class PacketNpcApi {
    private PacketNpcApi() {
    }

    public static boolean isPacketNpc(NPC npc) {
        return npc != null && npc.hasTrait(PacketNPC.class);
    }

    public static Set<Player> getViewers(NPC npc) {
        PacketNpcService service = PacketNpcService.getInstance();
        return service == null ? Collections.<Player>emptySet() : service.getViewers(npc);
    }

    public static void show(NPC npc, Player player) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.show(npc, player);
        }
    }

    public static void hide(NPC npc, Player player) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.hide(npc, player);
        }
    }

    public static void refresh(NPC npc) {
        PacketNpcService service = PacketNpcService.getInstance();
        if (service != null) {
            service.refresh(npc);
        }
    }

    public static String getStats() {
        PacketNpcService service = PacketNpcService.getInstance();
        return service == null ? "packet service unavailable" : service.getStats();
    }
}
