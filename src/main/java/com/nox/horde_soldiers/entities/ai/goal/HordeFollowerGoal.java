package com.nox.horde_soldiers.entities.ai.goal;

import com.nox.horde_soldiers.config.ModConfig;
import com.nox.horde_soldiers.entities.IHordeEntity;
import com.nox.horde_soldiers.horde.HordeController;
import com.nox.horde_soldiers.horde.HordeMember;
import com.nox.horde_soldiers.horde.HordeNavigation;
import com.nox.horde_soldiers.horde.HordeRegistry;
import com.nox.horde_soldiers.horde.MemberState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/**
 * Follower movement: move toward the published formation slot with lightweight correction.
 * Full individual pathfinding only as throttled, budgeted recovery - never every tick.
 */
public class HordeFollowerGoal extends Goal {
    private final PathfinderMob mob;
    private HordeController controller;
    private HordeMember member;
    private int refreshCooldown;
    private int correctionCooldown;

    public HordeFollowerGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return resolve();
    }

    @Override
    public boolean canContinueToUse() {
        return resolve();
    }

    private boolean resolve() {
        if (!(mob instanceof IHordeEntity hordeEntity)) return false;
        if (hordeEntity.getHordeId() == null || hordeEntity.isHordeLeader()) return false;
        if (!(mob.level() instanceof ServerLevel serverLevel)) return false;
        if (refreshCooldown-- <= 0) {
            refreshCooldown = 20;
            controller = HordeRegistry.get(serverLevel).getHordeOf(mob.getUUID());
            member = controller == null ? null : controller.getMember(mob.getUUID());
        }
        return controller != null && member != null;
    }

    @Override
    public void start() {
        correctionCooldown = 0;
    }

    @Override
    public void stop() {
        if (member != null) member.setState(MemberState.IDLE);
    }

    @Override
    public void tick() {
        if (!resolve()) return;
        // local combat execution: if we have a (shared) target nearby, combat goals take over movement
        if (mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceToSqr(mob.getTarget()) < 24.0 * 24.0) {
            member.setState(MemberState.COMBAT);
            return;
        }
        ModConfig.Common cfg = ModConfig.COMMON;
        double speed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 8.0 + 0.6;
        if (correctionCooldown-- <= 0) {
            correctionCooldown = cfg.followerCorrectionInterval.get();
            var slot = member.getFormationSlot();
            HordeNavigation.moveFollowerToSlot(mob, member, speed);
            member.onEntityTick(mob, true);
            double dx = slot.getWorldX() - mob.getX();
            double dz = slot.getWorldZ() - mob.getZ();
            double distSlot = Math.sqrt(dx * dx + dz * dz);
            if (distSlot > cfg.recoveryDistance.get()) {
                member.setState(MemberState.SEPARATED);
                member.setNeedsPathRecalculation(true);
            } else if (member.getStuckCounter() > 12) {
                member.setState(MemberState.STUCK);
                member.setNeedsPathRecalculation(true);
            } else {
                member.setState(MemberState.ACTIVE);
            }
        }
        if (member.needsPathRecalculation()) {
            var slot = member.getFormationSlot();
            boolean recovered = HordeNavigation.tryRecoveryPath(mob,
                    HordeRegistry.get((ServerLevel) mob.level()), member,
                    slot.getWorldX(), slot.getWorldY(), slot.getWorldZ(), speed);
            if (!recovered) {
                // cheap fallback: ask the navigator for the slot without consuming the budget
                mob.getNavigation().moveTo(slot.getWorldX(), slot.getWorldY(), slot.getWorldZ(), speed);
                member.setNeedsPathRecalculation(false);
            }
        }
    }
}
