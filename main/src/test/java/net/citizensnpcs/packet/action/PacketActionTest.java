package net.citizensnpcs.packet.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacketActionTest {
    @Test
    void triggerFallsBackToAny() {
        assertEquals(PacketActionTrigger.LEFT, PacketActionTrigger.parse("left"));
        assertEquals(PacketActionTrigger.RIGHT, PacketActionTrigger.parse("right"));
        assertEquals(PacketActionTrigger.ANY, PacketActionTrigger.parse("unknown"));
    }

    @Test
    void describeIncludesMetadata() {
        PacketAction action = new PacketAction(PacketActionTrigger.RIGHT, PacketActionType.MESSAGE, "hello");
        action.cooldownMillis = 1000;
        action.delayTicks = 20;
        action.permission = "test.node";
        String description = action.describe();
        assertTrue(description.contains("right"));
        assertTrue(description.contains("message"));
        assertTrue(description.contains("hello"));
        assertTrue(description.contains("cooldown=1000"));
        assertTrue(description.contains("delay=20"));
        assertTrue(description.contains("permission=test.node"));
    }
}
