package com.nox.hzcode.client;

import com.nox.hzcode.HordeSoldiersMod;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.ScavengerEntity;
import com.nox.hzcode.entities.SkinVariant;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.TerroristEntity;
import com.nox.hzcode.entities.ZombieEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client-side renderers. Each entity type has a skin pool under
 * assets/hzcode/textures/entity/skins/<type>/ (curated per creature: the player's own
 * downloads from E:\Games\Minecraft\instances\*.png plus matching modpack textures and a few
 * internet player skins). The skin is chosen deterministically from the entity UUID (SkinVariant),
 * so every mob keeps its look. Pools are resolved through the client ResourceManager (reliable for
 * mod-jar assets in production) and cached once; render type stays entityCutoutNoCull so skins are
 * never see-through.
 */
public final class ClientModEvents {
    private static final ResourceLocation[] ZOMBIE_SKINS = skins("zombie", "textures/entity/zombie.png");
    private static final ResourceLocation[] SOLDIER_SKINS = skins("soldier", "textures/entity/soldier_biker.png");
    private static final ResourceLocation[] SCAVENGER_SKINS = skins("scavenger", "textures/entity/scavenger.png");
    private static final ResourceLocation[] TERRORIST_SKINS = skins("terrorist", "textures/entity/terrorist.png");
    private static final ResourceLocation[] MEDIC_SKINS = skins("medic", "textures/entity/soldier_medic.png");

    private ClientModEvents() {}

    private static ResourceLocation[] skins(String type, String fallbackPath) {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            list.addAll(rm.listResources("textures/entity/skins/" + type,
                    p -> p.getPath().endsWith(".png")).keySet());
            list.sort(Comparator.comparing(ResourceLocation::getPath));
        } catch (Throwable ignored) {
            // fall through to the legacy single texture
        }
        if (list.isEmpty()) list.add(new ResourceLocation(HordeSoldiersMod.MOD_ID, fallbackPath));
        if (type.equals("zombie")) {
            // vanilla Minecraft zombie textures are always available - mix them into the pool
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/husk.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/drowned.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie_villager/zombie_villager.png"));
        }
        return list.toArray(new ResourceLocation[0]);
    }

    private static ResourceLocation pick(ResourceLocation[] pool, Entity entity) {
        return pool[SkinVariant.index(entity.getUUID(), pool.length)];
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ZOMBIE.get(), HordeZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDIER.get(), SoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SCAVENGER.get(), ctx -> new SkinHumanoidRenderer<>(ctx, SCAVENGER_SKINS));
        event.registerEntityRenderer(ModEntities.TERRORIST.get(), ctx -> new SkinHumanoidRenderer<>(ctx, TERRORIST_SKINS));
        event.registerEntityRenderer(ModEntities.MEDIC.get(), ctx -> new SkinHumanoidRenderer<>(ctx, MEDIC_SKINS));
    }

    public static class HordeZombieRenderer extends ZombieRenderer {
        public HordeZombieRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie entity) {
            return pick(ZOMBIE_SKINS, entity);
        }
    }

    public static class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, HumanoidModel<SoldierEntity>> {
        public SoldierRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(SoldierEntity entity) {
            return pick(SOLDIER_SKINS, entity);
        }
    }

    public static class SkinHumanoidRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
        private final ResourceLocation[] pool;

        public SkinHumanoidRenderer(EntityRendererProvider.Context context, ResourceLocation[] pool) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
            this.pool = pool;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return pick(pool, entity);
        }
    }
}
