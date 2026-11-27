package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ITameableHorde;
import com.nox.hzcode.faction.FactionManager;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Medic behavior (server side; clients only see the particles): find the nearest injured ally,
 * path toward it, heal when within range (plus a small friendly pulse), then cool down.
 * Invalid targets (dead, healed, hostile, out of reach) simply end the goal.
 * Replaces the old stationary aura: the medic no longer idles while an injured ally exists.
 */
public class HealAlliesGoal extends Goal {
    private static final int SCAN_INTERVAL = 10; // ticks between target scans

    private final PathfinderMob mob;
    private LivingEntity target;
    private int cooldownUntil;

    public HealAlliesGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) return false;
        if (mob.tickCount < cooldownUntil) return false;
        if (mob.tickCount % SCAN_INTERVAL != 0) return false; // throttle the ally scan
        this.target = findInjuredAlly();
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null) return false;
        double giveUp = seekRange() * 1.5;
        return target.isAlive() && isAlly(target) && target.getHealth() < target.getMaxHealth()
                && mob.distanceToSqr(target) <= giveUp * giveUp;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        if (target != null) mob.getNavigation().moveTo(target, 1.2);
    }

    @Override
    public void stop() {
        target = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (target == null || !(mob.level() instanceof ServerLevel)) return;
        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double healRange = ModConfig.COMMON.medicHealRange.get();
        if (mob.distanceToSqr(target) > healRange * healRange) {
            mob.getNavigation().moveTo(target, 1.2);
            return;
        }
        // in range: heal the target, then pulse-heal other injured allies around the medic
        heal(target);
        double radius = ModConfig.COMMON.medicHealRadius.get();
        for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(radius))) {
            if (e == mob || e == target || !e.isAlive() || !isAlly(e)) continue;
            if (e.getHealth() < e.getMaxHealth()) heal(e);
        }
        cooldownUntil = mob.tickCount + Math.max(1, ModConfig.COMMON.medicHealInterval.get());
        target = null;
        mob.getNavigation().stop();
    }

    private void heal(LivingEntity e) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;
        e.heal(ModConfig.COMMON.medicHealAmount.get().floatValue());
        if (!e.hasEffect(MobEffects.REGENERATION)) {
            e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        }
        serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.0, e.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
    }

    private LivingEntity findInjuredAlly() {
        double seek = seekRange();
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(seek), this::isValidTarget);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : candidates) {
            double d = mob.distanceToSqr(e);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity e) {
        return e != mob && e.isAlive() && e.getHealth() < e.getMaxHealth() && isAlly(e);
    }

    /** Allies: the taming owner (if tamed), plus anything the faction matrix calls allied and not hostile. */
    private boolean isAlly(LivingEntity e) {
        if (mob instanceof ITameableHorde t && t.isTamed() && t.getOwnerUUID() != null
                && t.getOwnerUUID().equals(e.getUUID())) return true;
        return FactionManager.isAlliedWith(mob, e) && !FactionManager.isHostileTo(mob, e);
    }

    private double seekRange() {
        return ModConfig.COMMON.medicSeekRange.get();
    }
}
