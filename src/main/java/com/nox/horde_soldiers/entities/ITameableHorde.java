package com.nox.horde_soldiers.entities;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Implemented by horde mobs that can be tamed by a player (soldiers, scavengers). */
public interface ITameableHorde {
    boolean isTamed();

    @Nullable
    UUID getOwnerUUID();

    void setTamed(@Nullable UUID owner);
}
