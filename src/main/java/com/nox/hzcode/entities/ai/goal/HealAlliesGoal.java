package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ITameableHorde;
import com.nox.hzcode.faction.FactionManager;
import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/** Medic aura: periodically heals + regenerates all friendly (non-hostile) entities nearby, including the owner. */
public class HealAlliesGoal extends Goal {
    private final PathfinderMob mob;

    public HealAlliesGoal(PathfinderMob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.level() instanceof ServerLevel && mob.tickCount % Math.max(1, ModConfig.COMMON.medicHealInterval.get()) == 0;
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel serverLevel)) return;
        double radius = ModConfig.COMMON.medicHealRadius.get();
        float amount = ModConfig.COMMON.medicHealAmount.get().floatValue();
        for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(radius))) {
            if (e == mob || !e.isAlive()) continue;
            boolean owner = mob instanceof ITameableHorde t && t.isTamed()
                    && t.getOwnerUUID() != null && t.getOwnerUUID().equals(e.getUUID());
            boolean ally = owner || (FactionManager.isAlliedWith(mob, e) && !FactionManager.isHostileTo(mob, e));
            if (!ally) continue;
            if (e.getHealth() < e.getMaxHealth()) {
                e.heal(amount);
                if (!e.hasEffect(MobEffects.REGENERATION)) {
                    e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
                }
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + 1.0, e.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }
}
