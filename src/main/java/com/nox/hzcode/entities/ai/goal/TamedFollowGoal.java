package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.entities.ITameableHorde;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Tamed horde mobs follow their owner. No-op for wild mobs. */
public class TamedFollowGoal extends Goal {
    private final PathfinderMob mob;
    private LivingEntity owner;
    private int timeToRecalcPath;

    public TamedFollowGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private LivingEntity owner() {
        if (!(mob instanceof ITameableHorde tameable) || !tameable.isTamed()) return null;
        UUID ownerId = tameable.getOwnerUUID();
        if (ownerId == null) return null;
        Player player = mob.level().getPlayerByUUID(ownerId);
        if (player != null) return player;
        if (mob.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(ownerId) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    public boolean canUse() {
        LivingEntity candidate = owner();
        if (candidate == null || candidate.isSpectator()) return false;
        if (mob.distanceToSqr(candidate) < 36.0D) return false;
        this.owner = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && !mob.getNavigation().isDone() && mob.distanceToSqr(owner) > 100.0D;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop() {
        this.owner = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (owner == null) return;
        this.mob.getLookControl().setLookAt(owner, 10.0F, this.mob.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = 10;
            this.mob.getNavigation().moveTo(owner, 1.15D);
        }
    }
}
