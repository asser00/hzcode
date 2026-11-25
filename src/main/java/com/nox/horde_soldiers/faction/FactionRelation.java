package com.nox.horde_soldiers.faction;

public enum FactionRelation {
    ALLIED,
    NEUTRAL,
    HOSTILE;

    public static FactionRelation parse(String value, FactionRelation fallback) {
        if (value == null) return fallback;
        try {
            return FactionRelation.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
