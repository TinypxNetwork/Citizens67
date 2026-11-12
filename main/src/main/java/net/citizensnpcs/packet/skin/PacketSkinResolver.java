package net.citizensnpcs.packet.skin;

import org.bukkit.entity.Player;

import com.mojang.authlib.GameProfile;

import net.citizensnpcs.packet.core.PacketNpcState;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.SkinProperty;

/** Resolves the per-viewer name/profile/skin for a packet NPC (including MirrorTrait). */
public final class PacketSkinResolver {
    private PacketSkinResolver() {
    }

    public static ResolvedSkin resolve(PacketNpcState state, Player viewer) {
        String displayName = state.getDisplayName();
        String profileName = state.getProfileName();
        SkinProperty skin = state.getSkin();
        PacketSkinCache cache = PacketSkinCache.getInstance();
        SkinDescriptor descriptor = SkinDescriptorFactory.from(state.getNpc());

        if (descriptor instanceof MirrorSkinDescriptor) {
            MirrorSkinDescriptor mirror = (MirrorSkinDescriptor) descriptor;
            GameProfile profile = NMS.getProfile(viewer);
            if (profile != null) {
                SkinProperty mirrored = SkinProperty.fromMojangProfile(profile);
                if (mirrored != null) {
                    skin = mirrored;
                }
                if (mirror.mirrorName() && profile.getName() != null) {
                    profileName = profile.getName();
                    displayName = profile.getName();
                }
            }
        } else if (descriptor != null) {
            String key = descriptor.cacheKey(viewer);
            SkinProperty cached = cache.get(key);
            if (cached != null) {
                skin = cached;
            } else {
                SkinProperty resolved = descriptor.resolve(viewer);
                if (resolved != null) {
                    skin = resolved;
                    cache.put(key, resolved);
                } else if (state.getSkin() != null) {
                    // Name/UUID descriptors are resolved by Citizens' existing Skin pipeline; don't cache a
                    // possibly not-yet-fetched profile under the name key.
                    skin = state.getSkin();
                }
            }
        }
        return new ResolvedSkin(displayName, profileName, skin);
    }

    public static final class ResolvedSkin {
        public final String displayName;
        public final String profileName;
        public final SkinProperty skin;

        ResolvedSkin(String displayName, String profileName, SkinProperty skin) {
            this.displayName = displayName;
            this.profileName = profileName;
            this.skin = skin;
        }
    }
}
