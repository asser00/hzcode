package com.nox.hzcode.entities;

import com.nox.hzcode.HordeSoldiersMod;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic per-entity skin selection: every entity gets a stable skin index derived from its
 * UUID, so a given mob always wears the same skin across rejoins. Pure functions are unit-tested.
 */
public final class SkinVariant {
    private SkinVariant() {}

    public static int index(UUID id, int count) {
        if (count <= 0) return 0;
        return (id.hashCode() & 0x7fffffff) % count;
    }

    public static ResourceLocation texture(String type, int index) {
        return new ResourceLocation(HordeSoldiersMod.MOD_ID, "textures/entity/skins/" + type + "/" + index + ".png");
    }
}
