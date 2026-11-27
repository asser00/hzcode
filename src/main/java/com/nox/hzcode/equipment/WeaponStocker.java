package com.nox.hzcode.equipment;

import com.nox.hzcode.tacz.TacZAdapter;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Random weapon provisioning. Scavengers get a random melee weapon (swords/axes/tridents and any
 * modded item whose id looks melee-like and has attack damage); soldiers get a random rifle-class
 * TACZ gun; terrorists get a random TACZ gun of any class. Ammo is matched to the chosen gun via
 * TACZ's {@link IAmmo#isAmmoOfGun}. Pure helpers ({@link #isMeleePath}, {@link #isRifle}) are
 * unit-tested in LogicTests.
 */
public final class WeaponStocker {
    private WeaponStocker() {}

    /** TACZ gun ids treated as rifles (item id path under the tacz namespace). */
    /** Exact rifle-class TACZ gun ids (from tacz index/guns/* "type":"rifle"). */
    private static final Set<String> RIFLE_IDS = Set.of(
            "ak47", "ak12", "ak74m", "m4a1", "m16a1", "m16a4", "aug", "g36k", "hk416d", "hk_g3",
            "fn_fal", "hcar", "mk18", "qbz_95", "qbz_191", "qr_24", "scar_h", "scar_l",
            "type_81", "urgi", "mcx_spear_lt");

    private static final Pattern MELEE_PATH = Pattern.compile(
            ".*(sword|axe|katana|machete|knife|dagger|spear|trident|hammer|scimitar|cleaver|halberd|mace|club|staff|baton|tonfa|cutlass|rapier|sabre|saber|falchion|gladius|whip|scythe).*");

    public static boolean isRifle(String gunPath) {
        return gunPath != null && RIFLE_IDS.contains(gunPath);
    }

    /** Pure, testable: does an item id path look like a melee weapon? */
    public static boolean isMeleePath(String path) {
        return path != null && MELEE_PATH.matcher(path.toLowerCase(Locale.ROOT)).matches();
    }

    private static boolean hasAttackDamage(Item item) {
        return item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    public static boolean isMeleeWeapon(Item item, ResourceLocation id) {
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem) return true;
        return isMeleePath(id.getPath()) && hasAttackDamage(item);
    }

    /** Picks a random registered melee weapon, preferring swords/axes/tridents. EMPTY if none. */
    public static ItemStack randomMelee(RandomSource random) {
        List<Item> candidates = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (isMeleeWeapon(item, BuiltInRegistries.ITEM.getKey(item))) candidates.add(item);
        }
        if (candidates.isEmpty()) return ItemStack.EMPTY;
        for (int tries = 0; tries < 8; tries++) {
            Item pick = candidates.get(random.nextInt(candidates.size()));
            if (pick instanceof SwordItem || pick instanceof AxeItem || pick instanceof TridentItem) {
                return new ItemStack(pick);
            }
        }
        return new ItemStack(candidates.get(random.nextInt(candidates.size())));
    }

    /** Picks a random registered TACZ gun; riflesOnly restricts to rifle-class ids. EMPTY if none/TACZ absent. */
    public static ItemStack randomTaczGun(RandomSource random, boolean riflesOnly) {
        if (!TacZAdapter.isTaczAvailable()) return ItemStack.EMPTY;
        List<Item> guns = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!"tacz".equals(id.getNamespace())) continue;
            if (IGun.getIGunOrNull(new ItemStack(item)) == null) continue;
            if (riflesOnly && !isRifle(id.getPath())) continue;
            guns.add(item);
        }
        if (guns.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(guns.get(random.nextInt(guns.size())));
    }

    /** Finds the TACZ ammo item that fits the given gun and returns a stack of it. EMPTY if none. */
    public static ItemStack matchingTaczAmmo(ItemStack gun, int count) {
        if (gun.isEmpty() || !TacZAdapter.isTaczAvailable()) return ItemStack.EMPTY;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!"tacz".equals(id.getNamespace())) continue;
            ItemStack ammoStack = new ItemStack(item);
            IAmmo ammo = IAmmo.getIAmmoOrNull(ammoStack);
            if (ammo != null && ammo.isAmmoOfGun(gun, ammoStack)) {
                return new ItemStack(item, Math.max(1, count));
            }
        }
        return ItemStack.EMPTY;
    }
}
