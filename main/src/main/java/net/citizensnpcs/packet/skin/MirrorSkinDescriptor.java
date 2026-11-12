package net.citizensnpcs.packet.skin;

import org.bukkit.entity.Player;

import com.mojang.authlib.GameProfile;

import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.SkinProperty;

/** Resolves the viewer's own skin, optionally mirroring their name too. */
public final class MirrorSkinDescriptor implements SkinDescriptor {
    private final boolean mirrorName;

    public MirrorSkinDescriptor(boolean mirrorName) {
        this.mirrorName = mirrorName;
    }

    public boolean mirrorName() {
        return mirrorName;
    }

    @Override
    public String cacheKey(Player viewer) {
        return "mirror:" + viewer.getUniqueId();
    }

    @Override
    public SkinProperty resolve(Player viewer) {
        GameProfile profile = NMS.getProfile(viewer);
        return profile == null ? null : SkinProperty.fromMojangProfile(profile);
    }

    @Override
    public String type() {
        return "mirror";
    }

    @Override
    public String value() {
        return "";
    }
}
