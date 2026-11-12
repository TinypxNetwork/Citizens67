package net.citizensnpcs.packet.action;

/** Supported action types for packet NPC click chains. */
public enum PacketActionType {
    CONSOLE("console"),
    COMMAND("command"),
    PLAYER_COMMAND("player-command"),
    MESSAGE("message"),
    SOUND("sound"),
    SERVER("server"),
    WAIT("wait"),
    PERMISSION("permission"),
    ITEM("item"),
    RANDOM("random");

    private final String id;

    PacketActionType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static PacketActionType parse(String value) {
        if (value == null)
            return null;
        String normalized = value.toLowerCase().replace('_', '-');
        if (normalized.equals("player") || normalized.equals("playercommand"))
            return PLAYER_COMMAND;
        if (normalized.equals("cmd"))
            return COMMAND;
        for (PacketActionType type : values()) {
            if (type.id.equals(normalized) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
