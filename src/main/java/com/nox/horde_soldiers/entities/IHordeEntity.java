package com.nox.horde_soldiers.entities;

import com.nox.horde_soldiers.horde.HordeType;
import java.util.UUID;

/** Implemented by every entity that can belong to a horde. */
public interface IHordeEntity {
    UUID getHordeId();
    void setHordeId(UUID hordeId);
    boolean isHordeLeader();
    void setHordeLeader(boolean leader);
    HordeType getHordeType();
}
