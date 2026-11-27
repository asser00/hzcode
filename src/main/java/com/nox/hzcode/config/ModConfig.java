package com.nox.hzcode.config;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;

public final class ModConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new Common(builder);
        COMMON_SPEC = builder.build();
    }

    private ModConfig() {}

    public static void register(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC);
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue enableHorde;
        public final ForgeConfigSpec.IntValue waveInterval;
        public final ForgeConfigSpec.IntValue spawnCap;
        public final ForgeConfigSpec.IntValue waveSizeGrowthPerWave;
        public final ForgeConfigSpec.IntValue waveSizeGrowthCap;

        public final ForgeConfigSpec.IntValue soldierHordeSize;
        public final ForgeConfigSpec.IntValue scavengerHordeSize;
        public final ForgeConfigSpec.IntValue zombieHordeSize;
        public final ForgeConfigSpec.IntValue terroristHordeSize;
        public final ForgeConfigSpec.IntValue minHordeSize;
        public final ForgeConfigSpec.IntValue maxHordeSize;
        public final ForgeConfigSpec.IntValue strategicInterval;
        public final ForgeConfigSpec.IntValue targetRefreshInterval;
        public final ForgeConfigSpec.IntValue formationInterval;
        public final ForgeConfigSpec.IntValue followerCorrectionInterval;
        public final ForgeConfigSpec.IntValue leaderCheckInterval;
        public final ForgeConfigSpec.IntValue pathRecalcCooldown;
        public final ForgeConfigSpec.IntValue maxPathRecalcsPerTick;
        public final ForgeConfigSpec.DoubleValue recoveryDistance;
        public final ForgeConfigSpec.DoubleValue formationSpacing;
        public final ForgeConfigSpec.DoubleValue targetAcquisitionRange;
        public final ForgeConfigSpec.IntValue leaderPathInterval;
        public final ForgeConfigSpec.IntValue saveInterval;

        public final ForgeConfigSpec.BooleanValue breachEnabled;
        public final ForgeConfigSpec.IntValue maxConcurrentBreaches;
        public final ForgeConfigSpec.BooleanValue respectMobGriefing;
        public final ForgeConfigSpec.BooleanValue breachRequiresToolForStone;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> breachableBlocks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> protectedBlocks;

        public final ForgeConfigSpec.DoubleValue zombieHealth;
        public final ForgeConfigSpec.DoubleValue zombieDamage;
        public final ForgeConfigSpec.BooleanValue zombiesBurnInSunlight;
        public final ForgeConfigSpec.BooleanValue zombieNaturalSpawn;

        public final ForgeConfigSpec.DoubleValue soldierHealth;
        public final ForgeConfigSpec.DoubleValue soldierDamage;
        public final ForgeConfigSpec.BooleanValue soldiersFriendlyToPlayer;
        public final ForgeConfigSpec.ConfigValue<String> soldierGun;
        public final ForgeConfigSpec.ConfigValue<String> soldierAmmo;
        public final ForgeConfigSpec.IntValue soldierAmmoStacks;
        public final ForgeConfigSpec.IntValue soldierLoadedAmmo;
        public final ForgeConfigSpec.BooleanValue soldierNaturalSpawn;
        public final ForgeConfigSpec.BooleanValue soldiersRandomWeapons;

        public final ForgeConfigSpec.DoubleValue scavengerHealth;
        public final ForgeConfigSpec.IntValue scavengerLootRadius;
        public final ForgeConfigSpec.IntValue scavengerLootInterval;
        public final ForgeConfigSpec.IntValue scavengerRetreatThreshold;
        public final ForgeConfigSpec.BooleanValue scavengerNaturalSpawn;
        public final ForgeConfigSpec.BooleanValue scavengersRandomMelee;

        public final ForgeConfigSpec.DoubleValue terroristHealth;
        public final ForgeConfigSpec.DoubleValue terroristDamage;
        public final ForgeConfigSpec.BooleanValue terroristsHostileToSoldiers;
        public final ForgeConfigSpec.BooleanValue terroristsUseGuns;
        public final ForgeConfigSpec.BooleanValue terroristNaturalSpawn;
        public final ForgeConfigSpec.BooleanValue terroristsRandomWeapons;

        public final ForgeConfigSpec.ConfigValue<String> relationPlayerScavenger;
        public final ForgeConfigSpec.ConfigValue<String> relationPlayerTerrorist;
        public final ForgeConfigSpec.ConfigValue<String> relationSoldierScavenger;
        public final ForgeConfigSpec.ConfigValue<String> relationSoldierTerrorist;
        public final ForgeConfigSpec.ConfigValue<String> relationScavengerTerrorist;

        public final ForgeConfigSpec.BooleanValue tamingEnabled;
        public final ForgeConfigSpec.ConfigValue<String> tameItem;
        public final ForgeConfigSpec.DoubleValue tameChance;

        public final ForgeConfigSpec.BooleanValue tradingEnabled;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> tradeGiveItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> tradeReceiveItems;

        public final ForgeConfigSpec.DoubleValue medicHealth;
        public final ForgeConfigSpec.BooleanValue medicNaturalSpawn;
        public final ForgeConfigSpec.IntValue medicHordeSize;
        public final ForgeConfigSpec.IntValue medicHealInterval;
        public final ForgeConfigSpec.DoubleValue medicHealAmount;
        public final ForgeConfigSpec.DoubleValue medicHealRadius;
        public final ForgeConfigSpec.DoubleValue medicSeekRange;
        public final ForgeConfigSpec.DoubleValue medicHealRange;
        public final ForgeConfigSpec.IntValue mercenaryHordeSize;
        public final ForgeConfigSpec.BooleanValue mercenaryNaturalSpawn;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> terroristWeaponClasses;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> mercenaryWeaponClasses;

        Common(ForgeConfigSpec.Builder b) {
            b.comment("General").push("general");
            enableHorde = b.comment("Enable the timed horde event").define("enableHorde", true);
            waveInterval = b.comment("Seconds between horde waves").defineInRange("waveInterval", 300, 10, 36000);
            spawnCap = b.comment("Maximum total horde members per dimension during the event").defineInRange("spawnCap", 60, 1, 512);
            waveSizeGrowthPerWave = b.comment("Extra wave members added per two waves (waveNumber / 2 * this)").defineInRange("waveSizeGrowthPerWave", 1, 0, 16);
            waveSizeGrowthCap = b.comment("Cap on the wave-size growth bonus").defineInRange("waveSizeGrowthCap", 6, 0, 64);
            b.pop();

            b.comment("Horde engine: one strategic controller per horde, shared state, hybrid navigation").push("horde");
            soldierHordeSize = b.defineInRange("soldierHordeSize", 5, 1, 64);
            scavengerHordeSize = b.defineInRange("scavengerHordeSize", 5, 1, 64);
            zombieHordeSize = b.defineInRange("zombieHordeSize", 6, 1, 64);
            terroristHordeSize = b.defineInRange("terroristHordeSize", 5, 1, 64);
            minHordeSize = b.defineInRange("minHordeSize", 1, 1, 64);
            maxHordeSize = b.defineInRange("maxHordeSize", 12, 1, 64);
            strategicInterval = b.comment("Ticks between strategic decisions by the horde leader (one per horde)").defineInRange("strategicInterval", 15, 1, 200);
            targetRefreshInterval = b.comment("Ticks between leader target refreshes").defineInRange("targetRefreshInterval", 15, 1, 200);
            formationInterval = b.comment("Ticks between formation slot assignments").defineInRange("formationInterval", 7, 1, 100);
            followerCorrectionInterval = b.comment("Ticks between follower movement corrections").defineInRange("followerCorrectionInterval", 5, 1, 100);
            leaderCheckInterval = b.comment("Ticks between leader validity checks / elections").defineInRange("leaderCheckInterval", 20, 1, 200);
            pathRecalcCooldown = b.comment("Per-follower cooldown before a full individual path recalc is allowed").defineInRange("pathRecalcCooldown", 100, 20, 1200);
            maxPathRecalcsPerTick = b.comment("Global budget of full path recalcs per tick across all hordes").defineInRange("maxPathRecalcsPerTick", 2, 1, 20);
            recoveryDistance = b.comment("Distance from formation slot that marks a follower as separated").defineInRange("recoveryDistance", 8.0, 2.0, 64.0);
            formationSpacing = b.comment("Base spacing between formation slots").defineInRange("formationSpacing", 2.0, 0.5, 10.0);
            targetAcquisitionRange = b.comment("Range the leader scans for hostile targets").defineInRange("targetAcquisitionRange", 32.0, 4.0, 128.0);
            leaderPathInterval = b.comment("Ticks between leader strategic path recalculations").defineInRange("leaderPathInterval", 40, 5, 1200);
            saveInterval = b.comment("Ticks between batched world-save writes of horde state (dirty-flagged)").defineInRange("saveInterval", 100, 20, 6000);
            b.pop();

            b.comment("Zombie horde members").push("zombieStats");
            zombieHealth = b.defineInRange("zombieHealth", 24.0, 1.0, 1024.0);
            zombieDamage = b.defineInRange("zombieDamage", 5.0, 0.0, 1024.0);
            zombiesBurnInSunlight = b.comment("Horde zombies burn in daylight like vanilla zombies").define("zombiesBurnInSunlight", false);
            zombieNaturalSpawn = b.comment("Zombies spawn naturally in the overworld (at night, like vanilla monsters)").define("naturalSpawn", true);
            b.pop();

            b.comment("Zombie breaching").push("zombie");
            breachEnabled = b.define("breachEnabled", true);
            maxConcurrentBreaches = b.comment("Concurrent block breaches per dimension").defineInRange("maxConcurrentBreaches", 4, 1, 64);
            respectMobGriefing = b.comment("Breaching does nothing while the mobGriefing gamerule is off").define("respectMobGriefing", true);
            breachRequiresToolForStone = b.comment("Stone-tier blocks may only be breached by a zombie holding a pickaxe").define("breachRequiresToolForStone", true);
            breachableBlocks = b.comment("Whitelist of breachable block ids (in addition to wooden doors/planks/fences/gates)")
                .defineList("breachableBlocks", List.of("minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door", "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door", "minecraft:mangrove_door", "minecraft:crimson_door", "minecraft:warped_door", "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks", "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks", "minecraft:mangrove_planks", "minecraft:crimson_planks", "minecraft:warped_planks", "minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:birch_fence", "minecraft:jungle_fence", "minecraft:acacia_fence", "minecraft:dark_oak_fence", "minecraft:oak_fence_gate", "minecraft:cobblestone", "minecraft:stone"), v -> v instanceof String);
            protectedBlocks = b.comment("Blacklist: never breached even if whitelisted elsewhere")
                .defineList("protectedBlocks", List.of("minecraft:bedrock", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:barrier", "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw", "minecraft:end_portal_frame", "minecraft:nether_portal", "minecraft:end_gateway", "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault"), v -> v instanceof String);
            b.pop();

            b.comment("Soldiers").push("soldier");
            soldierHealth = b.comment("Soldier max health (legacy ally key)").defineInRange("allyHealth", 30.0, 1.0, 1024.0);
            soldierDamage = b.comment("Soldier melee damage (legacy ally key)").defineInRange("allyDamage", 6.0, 0.0, 1024.0);
            soldiersFriendlyToPlayer = b.comment("Soldiers are allied with players (legacy ally key)").define("alliesFriendlyToPlayer", true);
            soldierGun = b.define("soldierGun", "tacz:ak47");
            soldierAmmo = b.define("soldierAmmo", "tacz:762x39");
            soldierAmmoStacks = b.comment("Ammo stacks placed in each soldier inventory").defineInRange("soldierAmmoStacks", 6, 0, 64);
            soldierLoadedAmmo = b.comment("Rounds loaded into the gun magazine on equip").defineInRange("soldierLoadedAmmo", 30, 0, 1000);
            soldierNaturalSpawn = b.comment("Soldiers spawn naturally in the overworld").define("naturalSpawn", true);
            soldiersRandomWeapons = b.comment("Each soldier gets a random rifle-class TACZ gun instead of the fixed soldierGun").define("randomWeapons", true);
            b.pop();

            b.comment("Scavengers").push("scavenger");
            scavengerHealth = b.defineInRange("scavengerHealth", 20.0, 1.0, 1024.0);
            scavengerLootRadius = b.comment("Radius the horde leader scans for loot containers (leader-driven, throttled)").defineInRange("scavengerLootRadius", 16, 4, 64);
            scavengerLootInterval = b.comment("Ticks between leader-driven loot searches").defineInRange("scavengerLootInterval", 200, 20, 6000);
            scavengerRetreatThreshold = b.comment("Scavengers retreat below this % of max health").defineInRange("scavengerRetreatThreshold", 30, 0, 100);
            scavengerNaturalSpawn = b.comment("Scavengers spawn naturally in the overworld").define("naturalSpawn", true);
            scavengersRandomMelee = b.comment("Each scavenger spawns with a random melee weapon").define("randomMeleeWeapons", true);
            b.pop();

            b.comment("Terrorists").push("terrorist");
            terroristHealth = b.defineInRange("terroristHealth", 24.0, 1.0, 1024.0);
            terroristDamage = b.defineInRange("terroristDamage", 8.0, 0.0, 1024.0);
            terroristsHostileToSoldiers = b.comment("Terrorists are hostile to soldiers (legacy key)").define("terroristsHostileToAllies", true);
            terroristsUseGuns = b.define("terroristsUseGuns", true);
            terroristNaturalSpawn = b.comment("Terrorists spawn naturally in the overworld").define("naturalSpawn", true);
            terroristsRandomWeapons = b.comment("Each terrorist gets a random TACZ gun instead of the fixed soldierGun").define("randomWeapons", true);
            terroristWeaponClasses = b.comment("TACZ weapon classes terrorists draw from (rifle,dmr,sniper,smg,shotgun,pistol,mg,rpg)").defineList("weaponClasses",
                    List.of("smg", "shotgun", "pistol"), v -> v instanceof String);
            b.pop();

            b.comment("Centralized faction relations: ALLIED, NEUTRAL or HOSTILE").push("factions");
            relationPlayerScavenger = b.define("relationPlayerScavenger", "NEUTRAL");
            relationPlayerTerrorist = b.define("relationPlayerTerrorist", "HOSTILE");
            relationSoldierScavenger = b.define("relationSoldierScavenger", "NEUTRAL");
            relationSoldierTerrorist = b.define("relationSoldierTerrorist", "HOSTILE");
            relationScavengerTerrorist = b.define("relationScavengerTerrorist", "NEUTRAL");
            b.pop();

            b.comment("Taming: soldiers and scavengers can be tamed by right-clicking them with the item").push("taming");
            tamingEnabled = b.comment("Enable taming").define("enabled", true);
            tameItem = b.comment("Item used to tame soldiers/scavengers (wolf-like: hold it and right-click)").define("item", "hzcode:taming_treat");
            tameChance = b.comment("Chance per item to successfully tame").defineInRange("chance", 0.5, 0.0, 1.0);
            b.pop();

            b.comment("Medics (healers)").push("medic");
            medicHealth = b.comment("Medic max health").defineInRange("medicHealth", 20.0, 1.0, 1024.0);
            medicNaturalSpawn = b.comment("Medics spawn naturally in the overworld").define("naturalSpawn", true);
            medicHordeSize = b.comment("Medic horde size when spawned as a horde").defineInRange("hordeSize", 2, 1, 12);
            medicHealInterval = b.comment("Ticks between heal pulses").defineInRange("healInterval", 40, 5, 1200);
            medicHealAmount = b.comment("Health restored per pulse").defineInRange("healAmount", 2.0, 0.5, 100.0);
            medicHealRadius = b.comment("Friendly pulse radius around the medic when it heals (blocks)").defineInRange("healRadius", 10.0, 2.0, 64.0);
            medicSeekRange = b.comment("How far the medic searches for an injured ally (blocks)").defineInRange("seekRange", 24.0, 4.0, 64.0);
            medicHealRange = b.comment("Distance at which the medic can heal its target (blocks)").defineInRange("healRange", 3.0, 1.0, 16.0);
            b.pop();

            b.comment("Mercenaries (trade-only soldiers; cannot be tamed)").push("mercenary");
            mercenaryHordeSize = b.comment("Mercenary group size when spawned as a horde").defineInRange("hordeSize", 3, 1, 12);
            mercenaryNaturalSpawn = b.comment("Mercenaries spawn naturally in the overworld").define("naturalSpawn", false);
            mercenaryWeaponClasses = b.comment("TACZ weapon classes mercenaries draw from (professional: dmr/sniper)").defineList("weaponClasses",
                    List.of("dmr", "sniper"), v -> v instanceof String);
            b.pop();

            b.comment("Trading: give a scavenger/soldier one of these items to barter for a random reward").push("trading");
            tradingEnabled = b.comment("Enable bartering with soldiers/scavengers").define("enabled", true);
            tradeGiveItems = b.comment("Items accepted in a trade (consumed)").defineList("giveItems",
                    List.of("minecraft:bread", "minecraft:cooked_beef", "minecraft:apple", "minecraft:iron_ingot", "minecraft:gold_ingot"), v -> v instanceof String);
            tradeReceiveItems = b.comment("Possible rewards (one random item is given)").defineList("receiveItems",
                    List.of("minecraft:arrow", "minecraft:torch", "minecraft:bread", "minecraft:iron_ingot", "minecraft:string", "minecraft:leather", "minecraft:emerald", "hzcode:scrap_metal", "hzcode:weapon_part", "minecraft:golden_apple"), v -> v instanceof String);
            b.pop();
        }
    }
}
