package com.nox.horde_soldiers.faction;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure, dependency-free faction relation matrix. Defaults follow the design table;
 * any pair can be overridden (symmetrically) at runtime from config.
 * Kept free of Minecraft classes so it can be unit-tested directly.
 */
public final class FactionRelations {
    private static final Map<Faction, Map<Faction, FactionRelation>> MATRIX = new EnumMap<>(Faction.class);

    static {
        for (Faction a : Faction.values()) {
            MATRIX.put(a, new EnumMap<>(Faction.class));
            for (Faction b : Faction.values()) {
                MATRIX.get(a).put(b, FactionRelation.NEUTRAL);
            }
        }
        // Player row/column
        set(Faction.PLAYER, Faction.PLAYER, FactionRelation.ALLIED);
        set(Faction.PLAYER, Faction.SOLDIER, FactionRelation.ALLIED);
        set(Faction.PLAYER, Faction.ZOMBIE, FactionRelation.HOSTILE);
        set(Faction.PLAYER, Faction.TERRORIST, FactionRelation.HOSTILE);
        // Soldiers
        set(Faction.SOLDIER, Faction.SOLDIER, FactionRelation.ALLIED);
        set(Faction.SOLDIER, Faction.ZOMBIE, FactionRelation.HOSTILE);
        set(Faction.SOLDIER, Faction.TERRORIST, FactionRelation.HOSTILE);
        // Scavengers
        set(Faction.SCAVENGER, Faction.SCAVENGER, FactionRelation.ALLIED);
        set(Faction.SCAVENGER, Faction.ZOMBIE, FactionRelation.HOSTILE);
        // Zombies
        set(Faction.ZOMBIE, Faction.ZOMBIE, FactionRelation.ALLIED);
        set(Faction.ZOMBIE, Faction.TERRORIST, FactionRelation.HOSTILE);
        // Terrorists
        set(Faction.TERRORIST, Faction.TERRORIST, FactionRelation.ALLIED);
        // Medics: allied with players/soldiers/scavengers, hostile to zombies/terrorists
        set(Faction.MEDIC, Faction.MEDIC, FactionRelation.ALLIED);
        set(Faction.MEDIC, Faction.PLAYER, FactionRelation.ALLIED);
        set(Faction.MEDIC, Faction.SOLDIER, FactionRelation.ALLIED);
        set(Faction.MEDIC, Faction.SCAVENGER, FactionRelation.ALLIED);
        set(Faction.MEDIC, Faction.ZOMBIE, FactionRelation.HOSTILE);
        set(Faction.MEDIC, Faction.TERRORIST, FactionRelation.HOSTILE);
        // Zombies attack everything (all other factions)
        set(Faction.ZOMBIE, Faction.PLAYER, FactionRelation.HOSTILE);
        set(Faction.ZOMBIE, Faction.SOLDIER, FactionRelation.HOSTILE);
        set(Faction.ZOMBIE, Faction.SCAVENGER, FactionRelation.HOSTILE);
        set(Faction.ZOMBIE, Faction.TERRORIST, FactionRelation.HOSTILE);
        set(Faction.ZOMBIE, Faction.MEDIC, FactionRelation.HOSTILE);
    }

    private FactionRelations() {}

    private static void set(Faction a, Faction b, FactionRelation relation) {
        MATRIX.get(a).put(b, relation);
        MATRIX.get(b).put(a, relation);
    }

    /** Configurable override; stored symmetrically. */
    public static void setOverride(Faction a, Faction b, FactionRelation relation) {
        if (a == null || b == null || relation == null) return;
        set(a, b, relation);
    }

    public static FactionRelation get(Faction a, Faction b) {
        if (a == null || b == null) return FactionRelation.NEUTRAL;
        if (a == b) return FactionRelation.ALLIED;
        return MATRIX.get(a).getOrDefault(b, FactionRelation.NEUTRAL);
    }

    public static boolean isHostile(Faction a, Faction b) {
        return get(a, b) == FactionRelation.HOSTILE;
    }

    public static boolean isAllied(Faction a, Faction b) {
        return get(a, b) == FactionRelation.ALLIED;
    }
}
