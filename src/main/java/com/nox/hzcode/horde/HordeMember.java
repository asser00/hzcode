package com.nox.hzcode.horde;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * One entity's membership record inside a horde. Tracks role, formation slot, local movement
 * state (stuck/separated detection) and throttled path-recalc state. Persists identifiers only.
 */
public class HordeMember {
    private final UUID entityId;
    private final UUID hordeId;
    private boolean leader;
    private FormationSlot formationSlot = new FormationSlot(0);
    private MemberState state = MemberState.IDLE;
    private int ticksInHorde;
    private boolean needsPathRecalculation;
    private int stuckCounter;
    private int pathCooldown;
    private double lastX, lastY, lastZ;
    private boolean hasLastPos;
    private int dormantTicks;

    public HordeMember(UUID entityId, UUID hordeId) {
        this.entityId = entityId;
        this.hordeId = hordeId;
    }

    public UUID getEntityId() { return entityId; }
    public UUID getHordeId() { return hordeId; }
    public boolean isLeader() { return leader; }
    public void setLeader(boolean leader) { this.leader = leader; }
    public FormationSlot getFormationSlot() { return formationSlot; }
    public void setFormationSlot(FormationSlot slot) { this.formationSlot = slot; }
    public MemberState getState() { return state; }
    public void setState(MemberState state) { this.state = state; }
    public int getTicksInHorde() { return ticksInHorde; }
    public boolean needsPathRecalculation() { return needsPathRecalculation; }
    public void setNeedsPathRecalculation(boolean v) { this.needsPathRecalculation = v; }
    public int getStuckCounter() { return stuckCounter; }
    public int getPathCooldown() { return pathCooldown; }
    public void setPathCooldown(int v) { this.pathCooldown = v; }
    public int getDormantTicks() { return dormantTicks; }

    public void tick() {
        ticksInHorde++;
        if (pathCooldown > 0) pathCooldown--;
    }

    /** Called by the entity each tick with its current position; updates stuck detection. */
    public void onEntityTick(LivingEntity entity, boolean shouldBeMoving) {
        double x = entity.getX(), y = entity.getY(), z = entity.getZ();
        if (hasLastPos && shouldBeMoving) {
            double moved = Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ));
            if (moved < 0.03) {
                stuckCounter++;
            } else {
                stuckCounter = 0;
            }
        } else if (!shouldBeMoving) {
            stuckCounter = 0;
        }
        lastX = x; lastY = y; lastZ = z;
        hasLastPos = true;
    }

    public void markDormant() { dormantTicks++; }
    public void markActive() { dormantTicks = 0; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("EntityId", entityId);
        tag.putUUID("HordeId", hordeId);
        tag.putBoolean("Leader", leader);
        tag.put("Slot", formationSlot.serializeNBT());
        tag.putString("State", state.name());
        tag.putInt("TicksInHorde", ticksInHorde);
        return tag;
    }

    public static HordeMember deserializeNBT(CompoundTag tag) {
        HordeMember member = new HordeMember(tag.getUUID("EntityId"), tag.getUUID("HordeId"));
        member.setLeader(tag.getBoolean("Leader"));
        if (tag.contains("Slot")) member.setFormationSlot(FormationSlot.deserializeNBT(tag.getCompound("Slot")));
        try {
            member.setState(MemberState.valueOf(tag.getString("State")));
        } catch (IllegalArgumentException ignored) {
            member.setState(MemberState.IDLE);
        }
        member.ticksInHorde = tag.getInt("TicksInHorde");
        return member;
    }
}
