package com.nox.horde_soldiers.horde;

/** Orders a player (or the controller itself) can give to a horde. Applied to the controller, never re-interpreted per follower. */
public enum HordeOrder {
    FOLLOW,
    HOLD_POSITION,
    DEFEND_POSITION,
    PROTECT_PLAYER,
    ATTACK_TARGET,
    RETREAT,
    CEASE_FIRE,
    RECALL,
    MOVE_TO;

    public static HordeOrder parse(String value) {
        if (value == null) return null;
        try {
            return HordeOrder.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
