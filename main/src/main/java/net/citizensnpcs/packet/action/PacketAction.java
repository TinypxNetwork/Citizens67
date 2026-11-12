package net.citizensnpcs.packet.action;

/** Serializable definition of one action in a packet NPC click chain. */
public final class PacketAction {
    public long cooldownMillis;
    public int delayTicks;
    public String permission = "";
    public PacketActionTrigger trigger = PacketActionTrigger.ANY;
    public PacketActionType type;
    public String value = "";

    public PacketAction() {
    }

    public PacketAction(PacketActionTrigger trigger, PacketActionType type, String value) {
        this.trigger = trigger;
        this.type = type;
        this.value = value;
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append(trigger.name().toLowerCase()).append(' ').append(type == null ? "?" : type.getId())
                .append(' ').append(value);
        if (cooldownMillis > 0) {
            builder.append(" cooldown=").append(cooldownMillis);
        }
        if (delayTicks > 0) {
            builder.append(" delay=").append(delayTicks);
        }
        if (permission != null && !permission.isEmpty()) {
            builder.append(" permission=").append(permission);
        }
        return builder.toString();
    }
}
