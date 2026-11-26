package com.nox.hzcode;

import com.nox.hzcode.client.ClientModEvents;
import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.event.ModEvents;
import com.nox.hzcode.horde.HordeManager;
import com.nox.hzcode.items.ModItems;
import com.nox.hzcode.structure.ModStructures;
import com.nox.hzcode.worldgen.ModSpawnPlacements;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(HordeSoldiersMod.MOD_ID)
public class HordeSoldiersMod {
    public static final String MOD_ID = "hzcode";

    public HordeSoldiersMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModConfig.register(modBus);
        ModEntities.register(modBus);
        ModStructures.register(modBus);
        ModItems.register(modBus);
        modBus.addListener(ModSpawnPlacements::onCommonSetup);
        modBus.addListener(ModEvents::onEntityAttributeCreation);
        modBus.addListener(ModEntities::onBuildCreativeModeTab);
        modBus.addListener(ModEvents::onConfigEvent);
        modBus.addListener(ModEvents::onRegisterCapabilities);
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) { modBus.addListener(ClientModEvents::registerEntityRenderers); }
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onEntityJoinLevel);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(ModEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(HordeManager::onServerTick);
    }
}
