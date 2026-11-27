package com.nox.hzcode.horde;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.IHordeEntity;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.ScavengerEntity;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.TerroristEntity;
import com.nox.hzcode.entities.ZombieEntity;
import com.nox.hzcode.tacz.TacZAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * Creates hordes as real groups: leader + followers registered into one controller with stable
 * formation slots and faction-specific initialization. No orphaned entities on partial failure.
 */
public final class HordeSpawner {
    private HordeSpawner() {}

    public static int defaultSize(HordeType type) {
        ModConfig.Common cfg = ModConfig.COMMON;
        return switch (type) {
            case SOLDIER -> cfg.soldierHordeSize.get();
            case SCAVENGER -> cfg.scavengerHordeSize.get();
            case ZOMBIE -> cfg.zombieHordeSize.get();
            case TERRORIST -> cfg.terroristHordeSize.get();
            case MEDIC -> cfg.medicHordeSize.get();
            case MERCENARY -> cfg.mercenaryHordeSize.get();
        };
    }

    public static int clampSize(int size) {
        return Math.max(ModConfig.COMMON.minHordeSize.get(), Math.min(ModConfig.COMMON.maxHordeSize.get(), size));
    }

    public static EntityType<? extends Mob> entityTypeFor(HordeType type) {
        return switch (type) {
            case SOLDIER -> ModEntities.SOLDIER.get();
            case SCAVENGER -> ModEntities.SCAVENGER.get();
            case ZOMBIE -> ModEntities.ZOMBIE.get();
            case TERRORIST -> ModEntities.TERRORIST.get();
            case MEDIC -> ModEntities.MEDIC.get();
            case MERCENARY -> ModEntities.SOLDIER.get(); // mercenaries are soldier entities flagged at spawn
        };
    }

    /**
     * Spawns a full horde at the given center. Returns the controller, or null if nothing could
     * be spawned (in which case nothing is left behind).
     */
    public static HordeController spawnHorde(ServerLevel level, HordeType type, BlockPos center, int requestedSize) {
        int size = clampSize(requestedSize <= 0 ? defaultSize(type) : requestedSize);
        HordeRegistry registry = HordeRegistry.get(level);
        UUID hordeId = UUID.randomUUID();
        HordeController controller = registry.createController(type, hordeId);

        List<LivingEntity> spawned = new ArrayList<>();
        EntityType<? extends Mob> entityType = entityTypeFor(type);
        for (int i = 0; i < size; i++) {
            try {
                Mob mob = entityType.create(level);
                if (mob == null) continue;
                double angle = (2.0 * Math.PI * i) / Math.max(1, size);
                double x = center.getX() + 0.5 + Math.cos(angle) * 2.0;
                double z = center.getZ() + 0.5 + Math.sin(angle) * 2.0;
                double y = center.getY();
                mob.moveTo(x, y, z, level.random.nextFloat() * 360.0f, 0.0f);
                if (mob instanceof IHordeEntity hordeEntity) {
                    hordeEntity.setHordeId(hordeId); // set BEFORE adding so attach() joins this horde
                }
                if (type == HordeType.MERCENARY && mob instanceof SoldierEntity s) s.setMercenary(true);
                level.addFreshEntity(mob);
                spawned.add(mob);
            } catch (Exception e) {
                // partial failure: keep going; already-spawned members stay in the horde
            }
        }

        if (spawned.isEmpty()) {
            registry.getHordes().remove(hordeId);
            return null;
        }

        // register any that did not auto-attach (safety net), elect leader, formation, faction init
        for (LivingEntity entity : spawned) {
            controller.registerMember(entity);
        }
        controller.recalculateFormation();
        initFaction(controller, type);
        return controller;
    }

    /** Faction-specific initialization applied once at horde creation. */
    private static void initFaction(HordeController controller, HordeType type) {
        switch (type) {
            case SOLDIER, MERCENARY -> equipSoldiers(controller);
            case TERRORIST -> {
                if (ModConfig.COMMON.terroristsUseGuns.get()) equipSoldiers(controller);
            }
            case ZOMBIE -> designateBreachers(controller);
            case SCAVENGER -> { /* nothing to equip; loot handled by controller */ }
            case MEDIC -> { /* medics heal, no weapon */ }
        }
    }

    /** Real TACZ provisioning: every soldier gets a configured gun, loaded rounds, and ammo stacks. */
    private static void equipSoldiers(HordeController controller) {
        if (!TacZAdapter.isTaczAvailable()) return;
        ModConfig.Common cfg = ModConfig.COMMON;
        for (UUID memberId : controller.getData().memberIds()) {
            var entity = controller.getLevel().getEntity(memberId);
            if (entity instanceof SoldierEntity soldier) {
                soldier.provisionEquipment();
            } else if (entity instanceof TerroristEntity terrorist) {
                terrorist.provisionEquipment();
            }
        }
    }

    private static void designateBreachers(HordeController controller) {
        int designated = 0;
        for (UUID memberId : controller.getData().memberIds()) {
            var entity = controller.getLevel().getEntity(memberId);
            if (entity instanceof ZombieEntity zombie) {
                zombie.setBreacher(designated == 0); // at least one breacher per horde
                designated++;
                if (designated >= 1) break;
            }
        }
    }

    public static ItemStack gunStack() {
        return TacZAdapter.createGunStack(ModConfig.COMMON.soldierGun.get());
    }

    public static ItemStack ammoStack() {
        return TacZAdapter.createAmmoStack(ModConfig.COMMON.soldierAmmo.get(), ModConfig.COMMON.soldierAmmoStacks.get());
    }
}
