package net.citizensnpcs.packet.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PacketEntityIdAllocatorTest {
    @Test
    void allocatesUniqueHighIds() {
        Set<Integer> ids = new HashSet<Integer>();
        for (int i = 0; i < 1000; i++) {
            int id = PacketEntityIdAllocator.allocate();
            assertTrue(id > 0, "id must be positive");
            assertTrue(ids.add(id), "id must be unique");
        }
    }
}
