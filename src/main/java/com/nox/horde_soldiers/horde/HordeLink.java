package com.nox.horde_soldiers.horde;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Small state holder for an entity's horde membership; persisted in the entity's own NBT. */
public final class HordeLink {
    private UUID hordeId;
    private boolean leader;

    public UUID getHordeId() { return hordeId; }
    public void setHordeId(UUID hordeId) { this.hordeId = hordeId; }
    public boolean isLeader() { return leader; }
    public void setLeader(boolean leader) { this.leader = leader; }

    public void writeNBT(CompoundTag tag) {
        if (hordeId != null) tag.putUUID("HordeId", hordeId);
        tag.putBoolean("HordeLeader", leader);
    }

    public void readNBT(CompoundTag tag) {
        if (tag.hasUUID("HordeId")) hordeId = tag.getUUID("HordeId");
        leader = tag.getBoolean("HordeLeader");
    }
}
