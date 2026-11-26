package com.nox.hzcode.entities;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Implemented by horde mobs that can be tamed by a player (soldiers, scavengers). */
public interface ITameableHorde {
    boolean isTamed();

    @Nullable
    UUID getOwnerUUID();

    void setTamed(@Nullable UUID owner);
}
