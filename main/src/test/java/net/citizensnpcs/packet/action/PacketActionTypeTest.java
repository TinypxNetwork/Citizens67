package net.citizensnpcs.packet.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PacketActionTypeTest {
    @Test
    void parsesAliases() {
        assertEquals(PacketActionType.CONSOLE, PacketActionType.parse("console"));
        assertEquals(PacketActionType.PLAYER_COMMAND, PacketActionType.parse("player"));
        assertEquals(PacketActionType.PLAYER_COMMAND, PacketActionType.parse("player-command"));
        assertEquals(PacketActionType.COMMAND, PacketActionType.parse("cmd"));
        assertNull(PacketActionType.parse("unknown"));
    }
}
