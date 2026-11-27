package com.nox.hzcode.horde;

import com.nox.hzcode.faction.Faction;

/** Kind of horde. The engine is generic; each type gets its own strategic policy. */
public enum HordeType {
    SOLDIER(Faction.SOLDIER),
    SCAVENGER(Faction.SCAVENGER),
    ZOMBIE(Faction.ZOMBIE),
    TERRORIST(Faction.TERRORIST),
    MEDIC(Faction.MEDIC),
    MERCENARY(Faction.MERCENARY);

    private final Faction faction;

    HordeType(Faction faction) {
        this.faction = faction;
    }

    public Faction faction() {
        return faction;
    }

    public static HordeType parse(String value, HordeType fallback) {
        if (value == null) return fallback;
        try {
            return HordeType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
