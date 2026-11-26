package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.IHordeEntity;
import com.nox.hzcode.horde.HordeController;
import com.nox.hzcode.horde.HordeMember;
import com.nox.hzcode.horde.HordeRegistry;
import com.nox.hzcode.horde.MemberState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.List;

/**
 * Scavenger local survival: fight what is right next to us, retreat when health is low.
 * Strategic looting is NOT here - it is leader-driven via the controller.
 */
public class ScavengerDefenseGoal extends Goal {
    private final PathfinderMob mob;
    private int cooldown;

    public ScavengerDefenseGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) return false;
        float threshold = ModConfig.COMMON.scavengerRetreatThreshold.get() / 100.0f;
        return mob.getHealth() < mob.getMaxHealth() * threshold;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (cooldown-- > 0) return;
        cooldown = 10;
        if (!(mob.level() instanceof ServerLevel level)) return;
        AABB box = mob.getBoundingBox().inflate(12.0);
        List<LivingEntity> threats = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != mob && e.isAlive()
                        && com.nox.hzcode.faction.FactionManager.isHostileTo(mob, e));
        if (threats.isEmpty()) {
            mob.getNavigation().stop();
            return;
        }
        LivingEntity nearest = threats.get(0);
        double best = Double.MAX_VALUE;
        for (LivingEntity t : threats) {
            double d = mob.distanceToSqr(t);
            if (d < best) { best = d; nearest = t; }
        }
        Vec3 away = mob.position().subtract(nearest.position());
        if (away.lengthSqr() < 0.001) away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0, mob.getRandom().nextDouble() - 0.5);
        away = away.normalize();
        double speed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 8.0 + 0.8;
        mob.getNavigation().moveTo(mob.getX() + away.x * 10.0, mob.getY(), mob.getZ() + away.z * 10.0, speed);
        if (mob instanceof IHordeEntity) {
            HordeController c = HordeRegistry.get(level).getHordeOf(mob.getUUID());
            if (c != null) {
                HordeMember m = c.getMember(mob.getUUID());
                if (m != null) m.setState(MemberState.RETREATING);
            }
        }
    }
}
