package com.nox.horde_soldiers.entities.ai.goal;

import com.nox.horde_soldiers.config.ModConfig;
import com.nox.horde_soldiers.entities.IHordeEntity;
import com.nox.horde_soldiers.horde.HordeController;
import com.nox.horde_soldiers.horde.HordeOrder;
import com.nox.horde_soldiers.horde.HordeRegistry;
import com.nox.horde_soldiers.tacz.TacZAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/**
 * TACZ combat execution for armed horde members (soldiers, optionally terrorists). Uses the real
 * TACZ API through {@link TacZAdapter}: aim, shoot with computed yaw/pitch, reload from the
 * member's own inventory. Strategic target selection stays with the horde controller.
 */
public class SoldierTaczCombatGoal extends Goal {
    private static final int MAX_AIM_TIME = 10;
    private static final int SHOOT_COOLDOWN = 8;
    private static final double MAX_ENGAGE_DISTANCE = 40.0;
    private static final double MIN_ENGAGE_DISTANCE = 6.0;

    private final PathfinderMob mob;
    private int attackCooldown;
    private int aimTime;

    public SoldierTaczCombatGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!TacZAdapter.isTaczAvailable()) return false;
        if (!TacZAdapter.isHoldingGun(mob)) return false;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (!com.nox.horde_soldiers.faction.FactionManager.isHostileTo(mob, target)) return false;
        if (mob.level() instanceof ServerLevel level && mob instanceof IHordeEntity hordeEntity && hordeEntity.getHordeId() != null) {
            HordeController controller = HordeRegistry.get(level).getHordeOf(mob.getUUID());
            if (controller != null) {
                HordeOrder order = controller.getOrder();
                if (order == HordeOrder.CEASE_FIRE || order == HordeOrder.RETREAT) return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        TacZAdapter.setAiming(mob, false);
        aimTime = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (attackCooldown > 0) attackCooldown--;

        mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double distSqr = mob.distanceToSqr(target);
        double dist = Math.sqrt(distSqr);

        manageMovement(dist, target);

        boolean inRange = dist <= MAX_ENGAGE_DISTANCE;
        if (inRange && mob.hasLineOfSight(target)) {
            TacZAdapter.setAiming(mob, true);
            aimTime++;
        } else {
            TacZAdapter.setAiming(mob, false);
            aimTime = 0;
        }

        // reload from the member's own inventory when the gun runs dry
        if (TacZAdapter.needsReload(mob) && !TacZAdapter.isReloading(mob)) {
            if (TacZAdapter.findAndLoadAmmo(mob)) {
                TacZAdapter.reload(mob);
            }
        }

        if (aimTime >= MAX_AIM_TIME && attackCooldown <= 0 && !TacZAdapter.isOnShootCooldown(mob) && !TacZAdapter.isReloading(mob)) {
            Vec3 eye = mob.getEyePosition();
            Vec3 aim = target.getEyePosition().subtract(eye);
            double len = aim.length();
            if (len > 0.001) {
                float yaw = (float) Math.toDegrees(Math.atan2(-aim.x, aim.z));
                float pitch = (float) Math.toDegrees(Math.asin(-aim.y / len));
                TacZAdapter.shoot(mob, () -> yaw, () -> pitch);
                attackCooldown = SHOOT_COOLDOWN;
                aimTime = 0;
            }
        }
    }

    private void manageMovement(double dist, LivingEntity target) {
        double speed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 8.0 + 0.6;
        if (dist > MAX_ENGAGE_DISTANCE) {
            mob.getNavigation().moveTo(target, speed);
        } else if (dist < MIN_ENGAGE_DISTANCE) {
            Vec3 away = mob.position().subtract(target.position()).normalize();
            mob.getNavigation().moveTo(mob.getX() + away.x * 4.0, mob.getY(), mob.getZ() + away.z * 4.0, speed);
        } else if (mob.getNavigation().isDone()) {
            // strafe deterministically so the squad does not stack up
            int side = (mob.getUUID().hashCode() & 1) == 0 ? 1 : -1;
            Vec3 dir = target.position().subtract(mob.position()).normalize();
            double px = -dir.z * side, pz = dir.x * side;
            mob.getNavigation().moveTo(mob.getX() + px * 3.0, mob.getY(), mob.getZ() + pz * 3.0, speed * 0.8);
        }
    }
}
