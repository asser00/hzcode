package com.nox.hzcode.entities;

import com.nox.hzcode.HordeSoldiersMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HordeSoldiersMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HordeSoldiersMod.MOD_ID);

    public static final RegistryObject<EntityType<ZombieEntity>> ZOMBIE = ENTITY_TYPES.register("zombie",
            () -> EntityType.Builder.of(ZombieEntity::new, MobCategory.MONSTER).sized(0.6f, 1.8f).build("hzcode_zombie"));
    public static final RegistryObject<EntityType<SoldierEntity>> SOLDIER = ENTITY_TYPES.register("soldier",
            () -> EntityType.Builder.of(SoldierEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("hzcode_soldier"));
    public static final RegistryObject<EntityType<ScavengerEntity>> SCAVENGER = ENTITY_TYPES.register("scavenger",
            () -> EntityType.Builder.of(ScavengerEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("hzcode_scavenger"));
    public static final RegistryObject<EntityType<TerroristEntity>> TERRORIST = ENTITY_TYPES.register("terrorist",
            () -> EntityType.Builder.of(TerroristEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("hzcode_terrorist"));
    public static final RegistryObject<EntityType<MedicEntity>> MEDIC = ENTITY_TYPES.register("medic",
            () -> EntityType.Builder.of(MedicEntity::new, MobCategory.CREATURE).sized(0.6f, 1.8f).build("hzcode_medic"));

    public static final RegistryObject<Item> ZOMBIE_SPAWN_EGG = ITEMS.register("zombie_spawn_egg",
            () -> new ForgeSpawnEggItem(ZOMBIE, 0x4A7C3A, 0x2E4A6B, new Item.Properties()));
    public static final RegistryObject<Item> SOLDIER_SPAWN_EGG = ITEMS.register("soldier_spawn_egg",
            () -> new ForgeSpawnEggItem(SOLDIER, 0x3B5B8C, 0xC8C8C8, new Item.Properties()));
    public static final RegistryObject<Item> SCAVENGER_SPAWN_EGG = ITEMS.register("scavenger_spawn_egg",
            () -> new ForgeSpawnEggItem(SCAVENGER, 0xC8A27A, 0x7A5A36, new Item.Properties()));
    public static final RegistryObject<Item> TERRORIST_SPAWN_EGG = ITEMS.register("terrorist_spawn_egg",
            () -> new ForgeSpawnEggItem(TERRORIST, 0x3A2E2A, 0xB08A68, new Item.Properties()));
    public static final RegistryObject<Item> MEDIC_SPAWN_EGG = ITEMS.register("medic_spawn_egg",
            () -> new ForgeSpawnEggItem(MEDIC, 0xD8D8D8, 0xC03030, new Item.Properties()));

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
    }

    /** Spawn eggs appear in the vanilla Spawn Eggs creative tab. */
    public static void onBuildCreativeModeTab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ZOMBIE_SPAWN_EGG.get());
            event.accept(SOLDIER_SPAWN_EGG.get());
            event.accept(SCAVENGER_SPAWN_EGG.get());
            event.accept(TERRORIST_SPAWN_EGG.get());
            event.accept(MEDIC_SPAWN_EGG.get());
        }
    }

    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ZOMBIE.get(), ZombieEntity.createAttributes().build());
        event.put(SOLDIER.get(), SoldierEntity.createAttributes().build());
        event.put(SCAVENGER.get(), ScavengerEntity.createAttributes().build());
        event.put(TERRORIST.get(), TerroristEntity.createAttributes().build());
        event.put(MEDIC.get(), MedicEntity.createAttributes().build());
    }
}
