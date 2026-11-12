package net.citizensnpcs.packet.skin;

import org.bukkit.entity.Player;

import net.citizensnpcs.util.SkinProperty;

/** A texture already resolved by SkinTrait or a command. */
public final class StaticSkinDescriptor implements SkinDescriptor {
    private final SkinProperty skin;

    public StaticSkinDescriptor(SkinProperty skin) {
        this.skin = skin;
    }

    @Override
    public String cacheKey(Player viewer) {
        return "static:" + (skin == null ? "" : skin.value);
    }

    @Override
    public SkinProperty resolve(Player viewer) {
        return skin;
    }

    @Override
    public String type() {
        return "static";
    }

    @Override
    public String value() {
        return skin == null ? "" : skin.value;
    }
}
