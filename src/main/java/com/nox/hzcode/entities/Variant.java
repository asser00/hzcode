package com.nox.hzcode.entities;

import java.util.UUID;

/**
 * Deterministic per-entity character variant (male/female). Derived from the entity UUID, so the
 * variant is stable across rejoins and world reloads and needs no extra network sync: server and
 * client compute the same value from the same UUID. Pure functions are unit-tested in LogicTests.
 */
public final class Variant {
    private Variant() {}

    /**
     * @param id          entity UUID
     * @param forceFemale type-level override (medics are always female)
     * @return true for the female (slim-model) variant
     */
    public static boolean isFemale(UUID id, boolean forceFemale) {
        if (forceFemale) return true;
        return SkinVariant.index(id, 2) == 1;
    }
}
