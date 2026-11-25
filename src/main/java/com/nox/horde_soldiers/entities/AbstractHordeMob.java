package com.nox.horde_soldiers.entities;

import com.nox.horde_soldiers.horde.HordeLink;
import com.nox.horde_soldiers.horde.HordeType;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for humanoid horde members (soldiers, scavengers, terrorists): horde link plus a
 * persisted 27-slot inventory exposed through the Forge item-handler capability.
 */
public abstract class AbstractHordeMob extends PathfinderMob implements IHordeEntity, ITameableHorde {
    private final HordeLink hordeLink = new HordeLink();
    @Nullable private java.util.UUID ownerUUID;
    private final ItemStackHandler inventory = new ItemStackHandler(27);
    private final LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> inventory);

    protected AbstractHordeMob(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public UUID getHordeId() { return hordeLink.getHordeId(); }

    @Override
    public void setHordeId(UUID hordeId) { hordeLink.setHordeId(hordeId); }

    @Override
    public boolean isHordeLeader() { return hordeLink.isLeader(); }

    @Override
    public void setHordeLeader(boolean leader) { hordeLink.setLeader(leader); }

    public ItemStackHandler getInventory() { return inventory; }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCapability.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hordeLink.writeNBT(tag);
        tag.put("Inventory", inventory.serializeNBT());
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hordeLink.readNBT(tag);
        if (tag.contains("Inventory")) inventory.deserializeNBT(tag.getCompound("Inventory"));
        ownerUUID = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public boolean isTamed() { return ownerUUID != null; }

    @Override
    @Nullable
    public java.util.UUID getOwnerUUID() { return ownerUUID; }

    @Override
    public void setTamed(@Nullable java.util.UUID owner) { this.ownerUUID = owner; }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return getHordeId() == null && !isTamed() && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        super.remove(reason);
        itemCapability.invalidate();
    }
}
