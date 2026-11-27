package com.nox.hzcode.worldgen;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.ZombieEntity;
import com.nox.hzcode.horde.HordeType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Registers spawn placements so all four horde types can spawn naturally in the overworld.
 * Which types actually spawn is gated per type by the config (naturalSpawn keys) at check time;
 * the biome modifiers in data/hzcode/worldgen/biome_modifier/add_*_spawns.json provide
 * the weights and group sizes. Spawn eggs and /horde commands always work.
 */
public final class ModSpawnPlacements {
    private ModSpawnPlacements() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            SpawnPlacements.register(ModEntities.ZOMBIE.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ModSpawnPlacements::zombieRule);
            SpawnPlacements.register(ModEntities.SOLDIER.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> humanoidRule(HordeType.SOLDIER, type, level, spawnType, pos, random));
            SpawnPlacements.register(ModEntities.SCAVENGER.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> humanoidRule(HordeType.SCAVENGER, type, level, spawnType, pos, random));
            SpawnPlacements.register(ModEntities.TERRORIST.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> humanoidRule(HordeType.TERRORIST, type, level, spawnType, pos, random));
            SpawnPlacements.register(ModEntities.MEDIC.get(), SpawnPlacements.Type.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (type, level, spawnType, pos, random) -> humanoidRule(HordeType.MEDIC, type, level, spawnType, pos, random));
        });
    }

    private static boolean naturalSpawnEnabled(HordeType type) {
        ModConfig.Common cfg = ModConfig.COMMON;
        return switch (type) {
            case ZOMBIE -> cfg.zombieNaturalSpawn.get();
            case SOLDIER -> cfg.soldierNaturalSpawn.get();
            case SCAVENGER -> cfg.scavengerNaturalSpawn.get();
            case TERRORIST -> cfg.terroristNaturalSpawn.get();
            case MEDIC -> cfg.medicNaturalSpawn.get();
            case MERCENARY -> cfg.mercenaryNaturalSpawn.get();
        };
    }

    private static boolean zombieRule(net.minecraft.world.entity.EntityType<ZombieEntity> type, ServerLevelAccessor level,
                                      MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND || spawnType == MobSpawnType.MOB_SUMMONED) return true;
        if (spawnType == MobSpawnType.SPAWNER) return false;
        if (!ModConfig.COMMON.zombieNaturalSpawn.get()) return false;
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<? extends Monster> monsterType =
                (net.minecraft.world.entity.EntityType<? extends Monster>) (net.minecraft.world.entity.EntityType<?>) type;
        return Monster.checkMonsterSpawnRules(monsterType, level, spawnType, pos, random);
    }

    private static boolean humanoidRule(HordeType hordeType, net.minecraft.world.entity.EntityType<? extends PathfinderMob> type,
                                        ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        if (spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND || spawnType == MobSpawnType.MOB_SUMMONED) return true;
        if (spawnType == MobSpawnType.SPAWNER) return false;
        if (!naturalSpawnEnabled(hordeType)) return false;
        if (!level.getFluidState(pos).isEmpty()) return false;
        if (!level.getBlockState(pos.below()).isSolidRender(level, pos.below())) return false;
        return Mob.checkMobSpawnRules(type, level, spawnType, pos, random);
    }
}
