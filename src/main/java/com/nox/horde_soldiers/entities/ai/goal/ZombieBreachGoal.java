package com.nox.horde_soldiers.entities.ai.goal;

import com.nox.horde_soldiers.config.ModConfig;
import com.nox.horde_soldiers.entities.IHordeEntity;
import com.nox.horde_soldiers.entities.ZombieEntity;
import com.nox.horde_soldiers.horde.HordeController;
import com.nox.horde_soldiers.horde.HordeRegistry;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

/**
 * Zombie breaching. Only runs for zombies the controller designated as breachers. Every check is
 * re-validated per tick: mobGriefing gamerule, whitelist/blacklist, tool requirement, and the
 * per-dimension concurrent breach limit. The breach slot is held only while the goal runs.
 */
public class ZombieBreachGoal extends Goal {
    private final ZombieEntity zombie;
    private BlockPos targetBlock;
    private int progress;
    private int maxProgress;
    private boolean holdingBreachSlot;

    public ZombieBreachGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!zombie.isBreacher()) return false;
        if (!(zombie.level() instanceof ServerLevel level)) return false;
        if (!ZombieBreachRules.griefingAllowed(level)) return false;
        if (!ModConfig.COMMON.breachEnabled.get()) return false;
        HordeRegistry registry = HordeRegistry.get(level);
        if (registry.getActiveBreaches() >= ModConfig.COMMON.maxConcurrentBreaches.get()) return false;
        BlockPos target = resolveBreachTarget(level);
        if (target == null) return false;
        if (!canBreach(level, target)) return false;
        this.targetBlock = target;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!(zombie.level() instanceof ServerLevel level)) return false;
        if (!ZombieBreachRules.griefingAllowed(level)) return false; // gamerule may flip mid-breach
        return targetBlock != null && canBreach(level, targetBlock);
    }

    @Override
    public void start() {
        progress = 0;
        if (zombie.level() instanceof ServerLevel level) {
            if (HordeRegistry.get(level).tryAcquireBreachSlot()) holdingBreachSlot = true;
        }
    }

    @Override
    public void stop() {
        if (holdingBreachSlot) {
            if (zombie.level() instanceof ServerLevel level) HordeRegistry.get(level).releaseBreachSlot();
            holdingBreachSlot = false;
        }
        progress = 0;
        targetBlock = null;
    }

    @Override
    public void tick() {
        if (!(zombie.level() instanceof ServerLevel level)) return;
        BlockPos target = resolveBreachTarget(level);
        if (target == null || !canBreach(level, target)) return;
        this.targetBlock = target;
        BlockState state = level.getBlockState(target);
        maxProgress = calculateBreachTicks(state);

        double distSqr = zombie.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSqr > 3.0 * 3.0) {
            zombie.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        zombie.getNavigation().stop();
        zombie.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        progress++;
        if (progress >= maxProgress) {
            breakBlock(level, target);
            progress = 0;
            this.targetBlock = null;
            clearSharedBreachTarget(level);
        }
    }

    private BlockPos resolveBreachTarget(ServerLevel level) {
        if (zombie.getHordeId() != null) {
            HordeController controller = HordeRegistry.get(level).getHordeOf(zombie.getUUID());
            if (controller != null && controller.getData().getBreachTarget() != null) {
                return controller.getData().getBreachTarget();
            }
        }
        // fallback: blocking block toward the current target
        if (zombie.getTarget() != null) {
            BlockPos from = zombie.blockPosition();
            BlockPos to = zombie.getTarget().blockPosition();
            for (int i = 1; i <= 6; i++) {
                BlockPos pos = from.offset((to.getX() - from.getX()) * i / 6, (to.getY() - from.getY()) * i / 6, (to.getZ() - from.getZ()) * i / 6);
                if (ZombieBreachRules.isBreachable(level, pos, level.getBlockState(pos))) return pos;
            }
        }
        return null;
    }

    private boolean canBreach(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!ZombieBreachRules.isBreachable(level, pos, state)) return false;
        if (ZombieBreachRules.requiresTool(state) && !hasRequiredTool()) {
            // designate stone-breacher: equip a pickaxe once so the role is actually usable
            if (!zombie.getMainHandItem().isEmpty() && !(zombie.getMainHandItem().getItem() instanceof PickaxeItem)) {
                return false;
            }
            if (zombie.getMainHandItem().isEmpty()) {
                zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
            }
            return zombie.getMainHandItem().getItem() instanceof PickaxeItem;
        }
        return true;
    }

    private boolean hasRequiredTool() {
        ItemStack main = zombie.getMainHandItem();
        return main.getItem() instanceof PickaxeItem;
    }

    private int calculateBreachTicks(BlockState state) {
        float destroySpeed = state.getDestroySpeed(zombie.level(), targetBlock == null ? zombie.blockPosition() : targetBlock);
        if (destroySpeed <= 0) return 200; // unbreakable-ish
        return Math.max(5, (int) (30.0f / destroySpeed));
    }

    private void breakBlock(ServerLevel level, BlockPos pos) {
        // final re-validation at the moment of breaking
        if (!ZombieBreachRules.griefingAllowed(level)) return;
        BlockState state = level.getBlockState(pos);
        if (!ZombieBreachRules.isBreachable(level, pos, state)) return;
        if (ZombieBreachRules.requiresTool(state) && !hasRequiredTool()) return;
        level.destroyBlock(pos, true, zombie);
    }

    private void clearSharedBreachTarget(ServerLevel level) {
        if (zombie.getHordeId() != null) {
            HordeController controller = HordeRegistry.get(level).getHordeOf(zombie.getUUID());
            if (controller != null) controller.getData().setBreachTarget(null);
        }
    }
}
