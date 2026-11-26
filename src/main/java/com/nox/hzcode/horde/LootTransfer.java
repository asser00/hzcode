package com.nox.hzcode.horde;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.items.IItemHandler;

/**
 * Lossless container -> inventory transfer. Simulates the extraction first; only removes what the
 * destination actually accepted; puts any remainder back into the container (or drops it at the
 * container as a last resort). An item can never be voided by a full inventory.
 */
public final class LootTransfer {
    private LootTransfer() {}

    /**
     * @return number of slot operations that moved at least one item
     */
    public static int transferSafely(IItemHandler container, IItemHandler dest, int maxOperations) {
        return transferSafely(container, dest, maxOperations, null, null);
    }

    public static int transferSafely(IItemHandler container, IItemHandler dest, int maxOperations, Level level, BlockPos dropPos) {
        int operations = 0;
        for (int slot = 0; slot < container.getSlots() && operations < maxOperations; slot++) {
            ItemStack simulated = container.extractItem(slot, 64, true); // simulate: nothing removed
            if (simulated.isEmpty()) continue;
            ItemStack remainder = insertAll(dest, simulated.copy(), true); // probe only: nothing inserted
            int amount = simulated.getCount() - remainder.getCount();
            if (amount <= 0) continue; // destination cannot accept: leave the container untouched
            ItemStack actual = container.extractItem(slot, amount, false); // now really remove
            ItemStack leftover = insertAll(dest, actual, false);
            if (!leftover.isEmpty()) {
                ItemStack putBack = insertAll(container, leftover, false);
                if (!putBack.isEmpty() && level != null && dropPos != null && !level.isClientSide) {
                    // absolute last resort: drop on the ground instead of losing the item
                    level.addFreshEntity(new ItemEntity(level, dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, putBack.copy()));
                }
            }
            operations++;
        }
        return operations;
    }

    private static ItemStack insertAll(IItemHandler dest, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;
        for (int i = 0; i < dest.getSlots() && !remainder.isEmpty(); i++) {
            remainder = dest.insertItem(i, remainder, simulate);
        }
        return remainder;
    }
}
