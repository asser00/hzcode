package com.nox.hzcode.tacz;

import com.nox.hzcode.config.ModConfig;
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

    // TACZ 1.2.0 uses ONE base gun item (tacz:modern_kinetic_gun) and ONE base ammo item (tacz:ammo);
    // the concrete gun/ammo is stored in NBT via IGun.setGunId / IAmmo.setAmmoId as tacz:<index id>.
    private static Item gunBaseItem;
    private static Item ammoBaseItem;
    private static boolean basesResolved;

    private static void resolveBases() {
        if (basesResolved) return;
        basesResolved = true;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || !"tacz".equals(id.getNamespace())) continue;
            ItemStack probe = new ItemStack(item);
            if (gunBaseItem == null && IGun.getIGunOrNull(probe) != null) gunBaseItem = item;
            if (ammoBaseItem == null && IAmmo.getIAmmoOrNull(probe) != null) ammoBaseItem = item;
        }
    }

    /** Normalizes a gun/ammo index id: accepts "ak47" or "tacz:ak47" and returns "tacz:ak47". */
    private static String normalizeIndexId(String id) {
        if (id == null || id.isEmpty()) return null;
        return id.contains(":") ? id : "tacz:" + id;
    }

    /** Creates a TACZ gun stack for a gun index id (e.g. "tacz:ak47"); EMPTY if TACZ/base item/id invalid. */
    public static ItemStack createGunStack(String id) {
        String nid = normalizeIndexId(id);
        if (nid == null || !isTaczAvailable()) return ItemStack.EMPTY;
        resolveBases();
        if (gunBaseItem == null) return ItemStack.EMPTY;
        ResourceLocation gunId;
        try { gunId = new ResourceLocation(nid); } catch (Exception e) { return ItemStack.EMPTY; }
        ItemStack stack = new ItemStack(gunBaseItem);
        IGun igun = IGun.getIGunOrNull(stack);
        if (igun == null) return ItemStack.EMPTY;
        igun.setGunId(stack, gunId);
        return stack;
    }

    /** Creates a TACZ ammo stack for an ammo index id (e.g. "tacz:762x39"); EMPTY if unknown. */
    public static ItemStack createAmmoStack(String id, int count) {
        String nid = normalizeIndexId(id);
        if (nid == null || !isTaczAvailable()) return ItemStack.EMPTY;
        resolveBases();
        if (ammoBaseItem == null) return ItemStack.EMPTY;
        ResourceLocation ammoId;
        try { ammoId = new ResourceLocation(nid); } catch (Exception e) { return ItemStack.EMPTY; }
        ItemStack stack = new ItemStack(ammoBaseItem, Math.max(1, count));
        IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
        if (ammo == null) return ItemStack.EMPTY;
        ammo.setAmmoId(stack, ammoId);
        return stack;
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
