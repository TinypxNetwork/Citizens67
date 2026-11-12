package net.citizensnpcs.util;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.util.Messaging;

/** Formats visible NPC names without changing saved names or default skin lookups. */
public final class NPCNames {
    private static final String TAB_NAME_KEY = "packet-tab-name";
    private static final String TAB_NAME_OWNER_KEY = "packet-tab-name-owner";
    private static final char[] TAB_NAME_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Pattern TAB_NAME_PATTERN = Pattern.compile("[a-z0-9]{10}");

    private NPCNames() {
    }

    public static String format(NPC npc, String name) {
        if (name.isEmpty() || "citizens-temporary".equals(npc.getOwningRegistry().getName()))
            return name;
        String parsed = Messaging.parseComponents(name);
        if (Messaging.stripColor(parsed).startsWith("[NPC] "))
            return parsed;
        return Messaging.parseComponents("&8[NPC] " + name);
    }

    /** A persistent alias used only by the player-info packet's display name field. */
    public static String tabListName(NPC npc) {
        Object stored = npc.data().get(TAB_NAME_KEY);
        String owner = npc.getUniqueId().toString();
        if (!(stored instanceof String) || !TAB_NAME_PATTERN.matcher((String) stored).matches()
                || !owner.equals(npc.data().get(TAB_NAME_OWNER_KEY))) {
            char[] characters = new char[10];
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < characters.length; i++) {
                characters[i] = TAB_NAME_CHARACTERS[random.nextInt(TAB_NAME_CHARACTERS.length)];
            }
            stored = new String(characters);
            npc.data().setPersistent(TAB_NAME_KEY, stored);
            // Cloned NPCs receive their own alias instead of copying the original's alias.
            npc.data().setPersistent(TAB_NAME_OWNER_KEY, owner);
        }
        return "\u00a78[NPC] " + stored;
    }
}
