package net.citizensnpcs.packet.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Allocates packet-only entity ids in a high range so they do not collide with normal world entities, which start low.
 */
public final class PacketEntityIdAllocator {
    private static final int START = 1_000_000_000;
    private static final AtomicInteger NEXT = new AtomicInteger(START);

    private PacketEntityIdAllocator() {
    }

    public static int allocate() {
        for (;;) {
            int id = NEXT.incrementAndGet();
            if (id > 0 && id < Integer.MAX_VALUE)
                return id;
            NEXT.set(START);
        }
    }
}
