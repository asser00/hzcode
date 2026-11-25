package com.nox.horde_soldiers.entities.ai.goal;

import com.nox.horde_soldiers.entities.ITameableHorde;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

/** Tamed horde mobs defend their owner: they target whoever last hurt the owner (or whom the owner last hurt). */
public class TamedOwnerDefenseGoal extends TargetGoal {
    private final PathfinderMob mob;
    private LivingEntity owner;
    private LivingEntity attacker;

    public TamedOwnerDefenseGoal(PathfinderMob mob) {
        super(mob, false);
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!(mob instanceof ITameableHorde tameable) || !tameable.isTamed()) return false;
        UUID ownerId = tameable.getOwnerUUID();
        if (ownerId == null) return false;
        Player player = mob.level().getPlayerByUUID(ownerId);
        if (player != null) {
            this.owner = player;
        } else if (mob.level() instanceof ServerLevel serverLevel && serverLevel.getEntity(ownerId) instanceof LivingEntity living) {
            this.owner = living;
        }
        if (this.owner == null) return false;
        LivingEntity hurtBy = this.owner.getLastHurtByMob();
        if (hurtBy != null && hurtBy.isAlive() && mob.distanceToSqr(hurtBy) < 1024.0D) {
            this.attacker = hurtBy;
            return true;
        }
        LivingEntity hurt = this.owner.getLastHurtMob();
        if (hurt != null && hurt.isAlive() && mob.distanceToSqr(hurt) < 1024.0D) {
            this.attacker = hurt;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.attacker);
        super.start();
    }
}
