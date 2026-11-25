package com.nox.horde_soldiers.tacz;

import com.nox.horde_soldiers.config.ModConfig;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.fml.ModList;

/**
 * Bridge to the real TACZ API. Every method is safe to call when TACZ is absent (it is a hard
 * dependency, but the guards keep classloading robust). {@link #equipSoldier} is the single
 * provisioning entry point, called by the horde spawner and on entity load.
 */
public final class TacZAdapter {
    private TacZAdapter() {}

    public static boolean isTaczAvailable() {
        try {
            return ModList.get().isLoaded("tacz");
        } catch (Throwable t) {
            return false;
        }
    }

    public static IGunOperator getGunOperator(LivingEntity entity) {
        if (!isTaczAvailable()) return null;
        return IGunOperator.fromLivingEntity(entity);
    }

    public static void initializeTaczData(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.initialData();
    }

    public static boolean isHoldingGun(LivingEntity entity) {
        return isTaczAvailable() && IGun.mainHandHoldGun(entity);
    }

    public static ItemStack getHeldGun(LivingEntity entity) {
        if (!isTaczAvailable()) return ItemStack.EMPTY;
        ItemStack stack = entity.getMainHandItem();
        if (!stack.isEmpty() && IGun.getIGunOrNull(stack) != null) return stack;
        return ItemStack.EMPTY;
    }

    public static void drawGun(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null && isHoldingGun(entity)) {
            operator.draw(() -> entity.getMainHandItem());
        }
    }

    public static void setAiming(LivingEntity entity, boolean aiming) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.aim(aiming);
    }

    public static ShootResult shoot(LivingEntity entity, Supplier<Float> yaw, Supplier<Float> pitch) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) return operator.shoot(yaw, pitch);
        return ShootResult.NOT_GUN;
    }

    public static void reload(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.reload();
    }

    public static boolean isReloading(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator == null) return false;
        ReloadState state = operator.getSynReloadState();
        return state != null && state.getStateType() != ReloadState.StateType.NOT_RELOADING;
    }

    public static int getCurrentAmmo(LivingEntity entity) {
        ItemStack gun = getHeldGun(entity);
        if (gun.isEmpty()) return 0;
        IGun igun = IGun.getIGunOrNull(gun);
        return igun == null ? 0 : igun.getCurrentAmmoCount(gun);
    }

    public static int getMaxAmmo(LivingEntity entity) {
        ItemStack gun = getHeldGun(entity);
        if (gun.isEmpty()) return 0;
        IGun igun = IGun.getIGunOrNull(gun);
        return igun == null ? 0 : igun.getMaxDummyAmmoAmount(gun);
    }

    public static boolean needsReload(LivingEntity entity) {
        return getCurrentAmmo(entity) <= 0 && getMaxAmmo(entity) > 0;
    }

    /**
     * Loads one magazine from the entity's own item-handler inventory into the held gun.
     * Matches ammo to the gun through TACZ's {@link IAmmo#isAmmoOfGun} API.
     *
     * @return true if a magazine was loaded
     */
    public static boolean findAndLoadAmmo(LivingEntity entity) {
        ItemStack gun = getHeldGun(entity);
        if (gun.isEmpty()) return false;
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun == null) return false;
        var cap = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!cap.isPresent()) return false;
        IItemHandler inv = cap.orElse(null);
        if (inv == null) return false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
            if (ammo != null && ammo.isAmmoOfGun(stack, gun)) {
                int loaded = Math.max(1, ModConfig.COMMON.soldierLoadedAmmo.get());
                igun.setCurrentAmmoCount(gun, loaded);
                igun.setBulletInBarrel(gun, true);
                if (inv instanceof IItemHandlerModifiable modifiable) {
                    modifiable.setStackInSlot(slot, inv.extractItem(slot, 1, false));
                } else {
                    inv.extractItem(slot, 1, false);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Provisions a soldier with a real TACZ firearm and ammunition: the gun goes to the main hand
     * with rounds loaded, ammo stacks go into the soldier's persisted inventory (dropped at its
     * feet only if the inventory is somehow full - never silently lost).
     */
    public static void equipSoldier(LivingEntity entity, ItemStack gun, ItemStack ammo, int loadedRounds) {
        if (gun.isEmpty() || !isTaczAvailable()) return;
        entity.setItemSlot(EquipmentSlot.MAINHAND, gun);
        IGun igun = IGun.getIGunOrNull(gun);
        if (igun != null) {
            igun.setCurrentAmmoCount(gun, Math.max(0, loadedRounds));
            igun.setBulletInBarrel(gun, true);
        }
        if (!ammo.isEmpty()) {
            var cap = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
            if (cap.isPresent()) {
                IItemHandler inv = cap.orElse(null);
                if (inv != null) {
                    ItemStack remainder = ammo.copy();
                    for (int i = 0; i < inv.getSlots() && !remainder.isEmpty(); i++) {
                        remainder = inv.insertItem(i, remainder, false);
                    }
                    if (!remainder.isEmpty() && entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        serverLevel.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(serverLevel,
                                entity.getX(), entity.getY(), entity.getZ(), remainder.copy()));
                    }
                }
            }
        }
        initializeTaczData(entity);
    }

    /** True if the stack is a TACZ gun. */
    public static boolean isGunStack(net.minecraft.world.item.ItemStack stack) {
        return isTaczAvailable() && !stack.isEmpty() && IGun.getIGunOrNull(stack) != null;
    }

    /** True if the stack is TACZ ammo. */
    public static boolean isAmmoStack(net.minecraft.world.item.ItemStack stack) {
        return isTaczAvailable() && !stack.isEmpty() && IAmmo.getIAmmoOrNull(stack) != null;
    }

    /** Creates a TACZ gun stack from a config id; EMPTY if the id is not a registered gun. */
    public static ItemStack createGunStack(String id) {
        if (id == null || id.isEmpty() || !isTaczAvailable()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item);
        return IGun.getIGunOrNull(stack) != null ? stack : ItemStack.EMPTY;
    }

    /** Creates a TACZ ammo stack from a config id; EMPTY if unknown. */
    public static ItemStack createAmmoStack(String id, int count) {
        if (id == null || id.isEmpty() || !isTaczAvailable()) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, count));
        return IAmmo.getIAmmoOrNull(stack) != null ? stack : ItemStack.EMPTY;
    }

    public static FireMode getFireMode(LivingEntity entity) {
        ItemStack gun = getHeldGun(entity);
        if (gun.isEmpty()) return FireMode.SEMI;
        IGun igun = IGun.getIGunOrNull(gun);
        return igun == null ? FireMode.SEMI : igun.getFireMode(gun);
    }

    public static void setFireMode(LivingEntity entity, FireMode mode) {
        ItemStack gun = getHeldGun(entity);
        if (!gun.isEmpty()) {
            IGun igun = IGun.getIGunOrNull(gun);
            if (igun != null) igun.setFireMode(gun, mode);
        }
    }

    public static boolean isGunReady(LivingEntity entity) {
        return isHoldingGun(entity) && !isReloading(entity) && !isOnShootCooldown(entity);
    }

    public static boolean isOnShootCooldown(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        return operator != null && operator.getSynShootCoolDown() > 0;
    }

    public static float getAimingProgress(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        return operator == null ? 0.0f : operator.getSynAimingProgress();
    }

    public static boolean isBolting(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        return operator != null && operator.getSynIsBolting();
    }

    public static void bolt(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.bolt();
    }

    public static void cancelReload(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.cancelReload();
    }

    public static void unloadAmmo(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.unloadAmmo();
    }

    public static void selectFireMode(LivingEntity entity) {
        IGunOperator operator = getGunOperator(entity);
        if (operator != null) operator.fireSelect();
    }
}
