package com.nox.horde_soldiers.entities.ai.goal;

import com.nox.horde_soldiers.entities.IHordeEntity;
import com.nox.horde_soldiers.faction.FactionManager;
import com.nox.horde_soldiers.horde.HordeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * Faction-aware targeting. All hostility decisions go through {@link FactionManager}.
 * Followers defer to the shared horde target assigned by the controller, so they never run
 * their own strategic scans while a horde target exists.
 */
public class FactionTargetGoal<T extends PathfinderMob> extends NearestAttackableTargetGoal<LivingEntity> {
    private final T mob;

    public FactionTargetGoal(T mob, boolean mustSee) {
        super(mob, LivingEntity.class, 10, mustSee, false,
                target -> target != null && target.isAlive() && FactionManager.isHostileTo(mob, target));
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (mob instanceof IHordeEntity hordeEntity && hordeEntity.getHordeId() != null
                && !hordeEntity.isHordeLeader() && mob.level() instanceof ServerLevel serverLevel) {
            var controller = HordeRegistry.get(serverLevel).getHordeOf(mob.getUUID());
            if (controller != null && controller.getData().getTargetId() != null) {
                return false; // the leader already chose a shared target
            }
        }
        return super.canUse();
    }
}
