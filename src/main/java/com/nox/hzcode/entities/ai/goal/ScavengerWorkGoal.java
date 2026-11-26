package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.entities.ScavengerEntity;
import com.nox.hzcode.faction.FactionManager;
import com.nox.hzcode.horde.ScavengerWorkManager;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Tamed scavengers work for their owner: when idle and safe, they may go to work (farming /
 * chopping / mining) - they disappear for 5 minutes and return with loot (ScavengerWorkManager).
 */
public class ScavengerWorkGoal extends Goal {
    private final ScavengerEntity mob;

    public ScavengerWorkGoal(ScavengerEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!mob.isTamed()) return false;
        if (!(mob.level() instanceof ServerLevel serverLevel)) return false;
        UUID ownerId = mob.getOwnerUUID();
        if (ownerId == null) return false;
        Player owner = serverLevel.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null || !owner.isAlive()) return false;
        if (mob.distanceToSqr(owner) > 64.0 * 64.0) return false; // only when owner is near
        // no hostiles nearby
        for (LivingEntity e : serverLevel.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(24.0))) {
            if (e != mob && e.isAlive() && FactionManager.isHostileTo(mob, e)) return false;
        }
        return mob.tickCount % 100 == 0 && mob.getRandom().nextFloat() < 0.25f;
    }

    @Override
    public void start() {
        if (mob.level() instanceof ServerLevel serverLevel) {
            ScavengerWorkManager.startWork(mob, serverLevel);
        }
    }
}
