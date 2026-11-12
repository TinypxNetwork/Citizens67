package net.citizensnpcs.packet.skin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;

import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.util.SkinProperty;

/** Small persistent skin cache used by the packet layer. */
public final class PacketSkinCache {
    private static final PacketSkinCache INSTANCE = new PacketSkinCache();

    public static PacketSkinCache getInstance() {
        return INSTANCE;
    }

    private final Map<String, Entry> cache = new HashMap<String, Entry>();
    private File file;
    private boolean dirty;

    private PacketSkinCache() {
    }

    public void load() {
        file = new File(CitizensAPI.getPlugin().getDataFolder(), "packet-skins.yml");
        cache.clear();
        if (!file.exists())
            return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> entries = config.getMapList("skins");
        long now = System.currentTimeMillis();
        for (Map<?, ?> raw : entries) {
            Object key = raw.get("key");
            Object value = raw.get("value");
            if (!(key instanceof String) || !(value instanceof String))
                continue;
            long expires = raw.get("expires") instanceof Number ? ((Number) raw.get("expires")).longValue() : 0L;
            if (expires > 0 && expires <= now)
                continue;
            Object name = raw.get("name");
            Object signature = raw.get("signature");
            cache.put((String) key, new Entry(new SkinProperty(name instanceof String ? (String) name : "textures",
                    (String) value, signature instanceof String ? (String) signature : ""), expires));
        }
    }

    public SkinProperty get(String key) {
        Entry entry = cache.get(key);
        if (entry == null)
            return null;
        if (entry.expires > 0 && entry.expires <= System.currentTimeMillis()) {
            cache.remove(key);
            dirty = true;
            return null;
        }
        return entry.skin;
    }

    public void put(String key, SkinProperty skin) {
        put(key, skin, Setting.PACKET_SKIN_CACHE_TTL_SECONDS.asInt());
    }

    public void put(String key, SkinProperty skin, long ttlSeconds) {
        if (key == null || skin == null)
            return;
        long expires = ttlSeconds < 0 ? System.currentTimeMillis() - 1L
                : ttlSeconds == 0 ? 0L : System.currentTimeMillis() + ttlSeconds * 1000L;
        cache.put(key, new Entry(skin, expires));
        dirty = true;
    }

    public void save() {
        if (file == null || !dirty)
            return;
        List<Map<String, Object>> entries = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Entry> entry : cache.entrySet()) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("key", entry.getKey());
            row.put("name", entry.getValue().skin.name);
            row.put("value", entry.getValue().skin.value);
            row.put("signature", entry.getValue().skin.signature);
            row.put("expires", entry.getValue().expires);
            entries.add(row);
        }
        YamlConfiguration config = new YamlConfiguration();
        config.set("skins", entries);
        try {
            config.save(file);
            dirty = false;
        } catch (IOException ignored) {
        }
    }

    public int size() {
        return cache.size();
    }

    private static final class Entry {
        private final long expires;
        private final SkinProperty skin;

        Entry(SkinProperty skin, long expires) {
            this.skin = skin;
            this.expires = expires;
        }
    }
}
