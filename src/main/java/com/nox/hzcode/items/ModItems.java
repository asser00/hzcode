package com.nox.hzcode.items;

import com.nox.hzcode.HordeSoldiersMod;
import com.nox.hzcode.entities.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Mod items + the mod creative tab: spawn eggs, taming treat, war horn (horde event timing), support-call items (spawn 1-5 soldiers). */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, HordeSoldiersMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HordeSoldiersMod.MOD_ID);

    public static final RegistryObject<Item> TAMING_TREAT =
            ITEMS.register("taming_treat", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> WAR_HORN =
            ITEMS.register("war_horn", WarHornItem::new);
    public static final RegistryObject<Item> SUPPORT_CALL_1 = ITEMS.register("support_call_1", () -> new SupportCallItem(1));
    public static final RegistryObject<Item> SUPPORT_CALL_2 = ITEMS.register("support_call_2", () -> new SupportCallItem(2));
    public static final RegistryObject<Item> SUPPORT_CALL_3 = ITEMS.register("support_call_3", () -> new SupportCallItem(3));
    public static final RegistryObject<Item> SUPPORT_CALL_4 = ITEMS.register("support_call_4", () -> new SupportCallItem(4));
    public static final RegistryObject<Item> SUPPORT_CALL_5 = ITEMS.register("support_call_5", () -> new SupportCallItem(5));
    public static final RegistryObject<Item> MERCENARY_CONTRACT =
            ITEMS.register("mercenary_contract", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> SCRAP_METAL =
            ITEMS.register("scrap_metal", () -> new Item(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> WEAPON_PART =
            ITEMS.register("weapon_part", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("hzcode", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hzcode"))
            .icon(() -> new ItemStack(ModEntities.SOLDIER_SPAWN_EGG.get()))
            .displayItems((params, output) -> {
                output.accept(ModEntities.ZOMBIE_SPAWN_EGG.get());
                output.accept(ModEntities.SOLDIER_SPAWN_EGG.get());
                output.accept(ModEntities.SCAVENGER_SPAWN_EGG.get());
                output.accept(ModEntities.TERRORIST_SPAWN_EGG.get());
                output.accept(ModEntities.MEDIC_SPAWN_EGG.get());
                output.accept(TAMING_TREAT.get());
                output.accept(WAR_HORN.get());
                output.accept(SUPPORT_CALL_1.get());
                output.accept(SUPPORT_CALL_2.get());
                output.accept(SUPPORT_CALL_3.get());
                output.accept(SUPPORT_CALL_4.get());
                output.accept(SUPPORT_CALL_5.get());
                output.accept(MERCENARY_CONTRACT.get());
                output.accept(SCRAP_METAL.get());
                output.accept(WEAPON_PART.get());
            })
            .build());

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
