package net.citizensnpcs.packet.skin;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.citizensnpcs.util.SkinProperty;

class PacketSkinCacheTest {
    @Test
    void storesAndExpiresEntries() {
        PacketSkinCache cache = PacketSkinCache.getInstance();
        SkinProperty skin = new SkinProperty("textures", "value", "signature");
        cache.put("test:skin", skin, 60);
        assertSame(skin, cache.get("test:skin"));

        cache.put("test:expired", skin, -1);
        assertNull(cache.get("test:expired"));
    }
}
