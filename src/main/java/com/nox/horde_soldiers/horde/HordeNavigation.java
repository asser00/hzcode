package com.nox.horde_soldiers.horde;

import com.nox.horde_soldiers.config.ModConfig;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Hybrid navigation: the leader owns the strategic path; followers move toward their published
 * formation slot with lightweight local correction. Full individual pathfinding is a throttled
 * recovery action, budgeted globally per tick and per member by cooldown - never every tick.
 */
public final class HordeNavigation {
    private HordeNavigation() {}

    /**
     * Lightweight follower correction: walk straight at the slot when close and the nav is idle;
     * otherwise ask the vanilla navigator for a path to the slot. Called at most every
     * followerCorrectionInterval ticks per member by the follower goal.
     */
    public static void moveFollowerToSlot(PathfinderMob mob, HordeMember member, double speed) {
        FormationSlot slot = member.getFormationSlot();
        double dx = slot.getWorldX() - mob.getX();
        double dy = slot.getWorldY() - mob.getY();
        double dz = slot.getWorldZ() - mob.getZ();
        double distSqr = dx * dx + dy * dy + dz * dz;
        PathNavigation nav = mob.getNavigation();
        if (distSqr < 1.2 * 1.2) {
            if (!nav.isDone()) nav.stop();
            return;
        }
        if (nav.isDone() || nav.getPath() == null) {
            nav.moveTo(slot.getWorldX(), slot.getWorldY(), slot.getWorldZ(), speed);
        }
    }

    /**
     * Controlled recovery: full individual path only when the follower is stuck/separated, its own
     * cooldown elapsed, and the global per-tick budget allows it.
     *
     * @return true if a full path was (re)calculated
     */
    public static boolean tryRecoveryPath(PathfinderMob mob, HordeRegistry registry, HordeMember member,
                                          double x, double y, double z, double speed) {
        if (member.getPathCooldown() > 0) return false;
        if (!registry.tryAcquirePathBudget()) return false;
        member.setPathCooldown(ModConfig.COMMON.pathRecalcCooldown.get());
        member.setNeedsPathRecalculation(false);
        mob.getNavigation().moveTo(x, y, z, speed);
        return true;
    }
}
