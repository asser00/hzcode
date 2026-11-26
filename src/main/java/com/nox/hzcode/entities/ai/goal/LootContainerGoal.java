package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.entities.IHordeEntity;
import com.nox.hzcode.horde.HordeController;
import com.nox.hzcode.horde.HordeRegistry;
import com.nox.hzcode.horde.LootTransfer;
import com.nox.hzcode.tacz.TacZAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import java.util.EnumSet;

/**
 * Executes the leader-assigned loot destination: path to the container, then run the lossless
 * transfer. Only the assignee (or nearest member) runs this; discovery is done once by the leader.
 */
public class LootContainerGoal extends Goal {
    private static final int TRANSFER_INTERVAL = 10;
    private static final int MAX_OPS_PER_TICK = 8;

    private final PathfinderMob mob;
    private int cooldown;

    public LootContainerGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!(mob instanceof IHordeEntity hordeEntity) || hordeEntity.getHordeId() == null) return false;
        HordeController controller = HordeRegistry.get(level).getHordeOf(mob.getUUID());
        if (controller == null) return false;
        BlockPos lootTarget = controller.getData().getLootTarget();
        if (lootTarget == null) return false;
        var assignee = controller.getData().getLootAssignee();
        return assignee == null || assignee.equals(mob.getUUID());
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        HordeController controller = HordeRegistry.get(level).getHordeOf(mob.getUUID());
        if (controller == null) return;
        BlockPos target = controller.getData().getLootTarget();
        if (target == null) return;

        double distSqr = mob.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (distSqr > 2.5 * 2.5) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            return;
        }
        mob.getNavigation().stop();
        if (cooldown-- > 0) return;
        cooldown = TRANSFER_INTERVAL;

        BlockEntity be = level.getBlockEntity(target);
        if (be == null) {
            controller.getData().setLootTarget(null);
            return;
        }
        var containerCap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        var invCap = mob.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!containerCap.isPresent() || !invCap.isPresent()) {
            controller.getData().setLootTarget(null);
            return;
        }
        IItemHandler container = containerCap.orElse(null);
        IItemHandler dest = invCap.orElse(null);
        if (container == null || dest == null) return;
        int moved = LootTransfer.transferSafely(container, dest, MAX_OPS_PER_TICK, level, target);
        if (moved == 0) {
            // container empty or inventory full: let the leader pick a new target
            controller.getData().setLootTarget(null);
            controller.getData().setLootAssignee(null);
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
