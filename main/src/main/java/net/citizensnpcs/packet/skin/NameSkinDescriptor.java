package net.citizensnpcs.packet.skin;

import org.bukkit.entity.Player;

import net.citizensnpcs.util.SkinProperty;

/** A skin identified by player name; resolution is provided by Citizens' Skin/SkinTrait pipeline. */
public final class NameSkinDescriptor implements SkinDescriptor {
    private final String name;

    public NameSkinDescriptor(String name) {
        this.name = name == null ? "" : name;
    }

    @Override
    public String cacheKey(Player viewer) {
        return "name:" + name.toLowerCase();
    }

    @Override
    public SkinProperty resolve(Player viewer) {
        return null;
    }

    @Override
    public String type() {
        return "name";
    }

    @Override
    public String value() {
        return name;
    }
}
