package net.citizensnpcs.packet.skin;

import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.MirrorTrait;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.util.SkinProperty;

/** Builds a skin descriptor from the NPC's existing Citizens traits. */
public final class SkinDescriptorFactory {
    private SkinDescriptorFactory() {
    }

    public static SkinDescriptor from(NPC npc) {
        MirrorTrait mirror = npc.getTraitNullable(MirrorTrait.class);
        if (mirror != null && mirror.isEnabled()) {
            return new MirrorSkinDescriptor(mirror.mirrorName());
        }
        SkinTrait skin = npc.getTraitNullable(SkinTrait.class);
        if (skin != null) {
            String texture = skin.getTexture();
            if (texture != null && !texture.isEmpty()) {
                return new StaticSkinDescriptor(new SkinProperty("textures", texture, skin.getSignature()));
            }
            String name = skin.getSkinName();
            if (name != null && !name.isEmpty()) {
                return new NameSkinDescriptor(name);
            }
        }
        return null;
    }
}
