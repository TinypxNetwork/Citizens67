package net.citizensnpcs.packet.action;

/** Click trigger for a packet NPC action. */
public enum PacketActionTrigger {
    LEFT,
    RIGHT,
    ANY;

    public static PacketActionTrigger parse(String value) {
        if (value == null)
            return ANY;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ANY;
        }
    }
}
