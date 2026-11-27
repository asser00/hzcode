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

    /**
     * Verified TACZ 1.2.0 gun index ids by class, read from the installed
     * tacz-forge-1.20.1-1.2.0.jar (data/tacz/index/guns/<id>.json "type" field). 70 guns total.
     */
    public static final java.util.Map<String, java.util.Set<String>> GUNS_BY_CLASS = java.util.Map.of(
            "rifle", Set.of("ak12", "ak47", "ak74m", "aug", "fn_fal", "g36k", "hcar", "hk416d", "hk_g3",
                    "m16a1", "m16a4", "m4a1", "mcx_spear_lt", "mk18", "qbz_191", "qbz_95", "qr_24",
                    "scar_h", "scar_l", "type_81", "urgi"),
            "dmr", Set.of("m1866", "mk14", "scar_mk20", "sks_tactical", "spr15hb"),
            "sniper", Set.of("ai_awp", "hm50b", "kar98", "m107", "m700", "m95", "mrad", "springfield1873", "svd"),
            "smg", Set.of("hk_mp5a5", "hk_mp7a1", "p90", "ump45", "uzi", "vector45"),
            "shotgun", Set.of("aa12", "db_long", "db_short", "m1014", "m1897", "m870", "spas_12"),
            "pistol", Set.of("b93r", "cz75", "deagle", "deagle_golden", "glock_17", "hk_mk23", "lonetrail",
                    "m1911", "m9a4", "p320", "rhino357", "taurus500", "taurus943", "tec_9", "timeless50"),
            "mg", Set.of("fn_evolys", "m249", "minigun", "pkm", "rpk"),
            "rpg", Set.of("m320", "rpg7"));

    /** All verified TACZ gun index ids (paths, without namespace). */
    public static final java.util.Set<String> ALL_GUN_IDS = GUNS_BY_CLASS.values().stream()
            .flatMap(java.util.Set::stream).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** Verified TACZ 1.2.0 ammo index ids (from data/tacz/index/ammo/<id>.json). */
    public static final java.util.Set<String> AMMO_IDS = Set.of(
            "12g", "22wmr", "300blk", "308", "30_06", "338", "357mag", "40mm", "45_70", "45acp", "46x30",
            "500mag", "50ae", "50bmg", "545x39", "556x45", "57x28", "58x42", "68x51fury", "762x25",
            "762x39", "762x54", "792x57", "9mm", "rpg_rocket");

    /** Union of gun ids for the given weapon classes (unknown classes are ignored). */
    public static java.util.Set<String> gunsOfClasses(java.util.List<? extends String> classes) {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (classes != null) {
            for (String cls : classes) {
                java.util.Set<String> ids = GUNS_BY_CLASS.get(cls == null ? "" : cls.toLowerCase(java.util.Locale.ROOT));
                if (ids != null) out.addAll(ids);
            }
        }
        return out;
    }

    public static boolean isRifle(String gunPath) {
        return gunPath != null && GUNS_BY_CLASS.get("rifle").contains(gunPath);
    }

    private static final Pattern MELEE_PATH = Pattern.compile(
            ".*(sword|axe|katana|machete|knife|dagger|spear|trident|hammer|scimitar|cleaver|halberd|mace|club|staff|baton|tonfa|cutlass|rapier|sabre|saber|falchion|gladius|whip|scythe).*");

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

    /** Picks a random verified TACZ gun; riflesOnly restricts to rifle-class ids. EMPTY if none/TACZ absent. */
    public static ItemStack randomTaczGun(RandomSource random, boolean riflesOnly) {
        return randomTaczGunFrom(random, riflesOnly ? GUNS_BY_CLASS.get("rifle") : ALL_GUN_IDS);
    }

    /** Picks a random gun from the given index-id collection and materializes it via TacZAdapter. */
    public static ItemStack randomTaczGunFrom(RandomSource random, java.util.Collection<String> ids) {
        if (!TacZAdapter.isTaczAvailable() || ids == null || ids.isEmpty()) return ItemStack.EMPTY;
        java.util.List<String> list = new java.util.ArrayList<>(ids);
        for (int tries = 0; tries < 8; tries++) {
            String id = list.get(random.nextInt(list.size()));
            ItemStack gun = TacZAdapter.createGunStack("tacz:" + id);
            if (!gun.isEmpty()) return gun;
        }
        return ItemStack.EMPTY;
    }

    /** Finds the TACZ ammo (by verified ammo id) that fits the given gun and returns a stack of it. EMPTY if none. */
    public static ItemStack matchingTaczAmmo(ItemStack gun, int count) {
        if (gun.isEmpty() || !TacZAdapter.isTaczAvailable()) return ItemStack.EMPTY;
        for (String ammoId : AMMO_IDS) {
            ItemStack ammoStack = TacZAdapter.createAmmoStack("tacz:" + ammoId, Math.max(1, count));
            if (ammoStack.isEmpty()) continue;
            IAmmo ammo = IAmmo.getIAmmoOrNull(ammoStack);
            if (ammo != null && ammo.isAmmoOfGun(gun, ammoStack)) return ammoStack;
        }
        return ItemStack.EMPTY;
    }
}
