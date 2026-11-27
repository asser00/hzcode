package com.nox.hzcode.client;

import com.nox.hzcode.HordeSoldiersMod;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.SkinVariant;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.Variant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.client.event.EntityRenderersEvent;

/**
 * Client-side renderers with male/female variants.
 *
 * Skin pools live under assets/hzcode/textures/entity/skins/<type>/ (general pool) with optional
 * gender sub-pools .../skins/<type>/male/ and .../skins/<type>/female/ (owner-provided skins).
 * Mercenary soldiers use the mercenary pools. Gender is deterministic from the entity UUID
 * (Variant; medics are always female), so server and client always agree and the look never
 * changes after a reload. Female entities render with the slim (Alex) player model, male with the
 * classic one (LivingEntityRenderer.render reads the model field directly, so the renderer swaps
 * it around the super call). Pools are resolved through the client ResourceManager (reliable for
 * mod-jar assets in production) and cached once; render type stays entityCutoutNoCull so skins
 * are never see-through.
 */
public final class ClientModEvents {
    private static final ResourceLocation[] ZOMBIE_SKINS = skins("zombie", "textures/entity/zombie.png");
    private static final ResourceLocation[] SOLDIER_SKINS = skins("soldier", "textures/entity/soldier_biker.png");
    private static final ResourceLocation[] SCAVENGER_SKINS = skins("scavenger", "textures/entity/scavenger.png");
    private static final ResourceLocation[] TERRORIST_SKINS = skins("terrorist", "textures/entity/terrorist.png");
    private static final ResourceLocation[] MEDIC_SKINS = skins("medic", "textures/entity/soldier_medic.png");
    private static final ResourceLocation[] MERCENARY_SKINS = skins("mercenary", "textures/entity/soldier_biker.png");

    private static final ResourceLocation[] SOLDIER_M = genderPool("soldier", "male", SOLDIER_SKINS);
    private static final ResourceLocation[] SOLDIER_F = genderPool("soldier", "female", SOLDIER_SKINS);
    private static final ResourceLocation[] SCAVENGER_M = genderPool("scavenger", "male", SCAVENGER_SKINS);
    private static final ResourceLocation[] SCAVENGER_F = genderPool("scavenger", "female", SCAVENGER_SKINS);
    private static final ResourceLocation[] TERRORIST_M = genderPool("terrorist", "male", TERRORIST_SKINS);
    private static final ResourceLocation[] TERRORIST_F = genderPool("terrorist", "female", TERRORIST_SKINS);
    private static final ResourceLocation[] MEDIC_M = genderPool("medic", "male", MEDIC_SKINS);
    private static final ResourceLocation[] MEDIC_F = genderPool("medic", "female", MEDIC_SKINS);
    private static final ResourceLocation[] MERCENARY_M = genderPool("mercenary", "male", MERCENARY_SKINS);
    private static final ResourceLocation[] MERCENARY_F = genderPool("mercenary", "female", MERCENARY_SKINS);

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

    /** Gender sub-pool for a type; falls back to the general pool when the sub-pool is empty. */
    private static ResourceLocation[] genderPool(String type, String gender, ResourceLocation[] general) {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            list.addAll(rm.listResources("textures/entity/skins/" + type + "/" + gender,
                    p -> p.getPath().endsWith(".png")).keySet());
            list.sort(Comparator.comparing(ResourceLocation::getPath));
        } catch (Throwable ignored) {
            // fall back to the general pool
        }
        return list.isEmpty() ? general : list.toArray(new ResourceLocation[0]);
    }

    private static ResourceLocation pick(ResourceLocation[] general, ResourceLocation[] male,
                                         ResourceLocation[] female, Entity entity, boolean forceFemale) {
        ResourceLocation[] pool = Variant.isFemale(entity.getUUID(), forceFemale) ? female : male;
        if (pool.length == 0) pool = general;
        return pool[SkinVariant.index(entity.getUUID(), pool.length)];
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ZOMBIE.get(), HordeZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDIER.get(), SoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SCAVENGER.get(),
                ctx -> new SkinHumanoidRenderer<>(ctx, SCAVENGER_SKINS, SCAVENGER_M, SCAVENGER_F, false));
        event.registerEntityRenderer(ModEntities.TERRORIST.get(),
                ctx -> new SkinHumanoidRenderer<>(ctx, TERRORIST_SKINS, TERRORIST_M, TERRORIST_F, false));
        event.registerEntityRenderer(ModEntities.MEDIC.get(),
                ctx -> new SkinHumanoidRenderer<>(ctx, MEDIC_SKINS, MEDIC_M, MEDIC_F, true)); // medics are female
    }

    public static class HordeZombieRenderer extends ZombieRenderer {
        public HordeZombieRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie entity) {
            return pick(ZOMBIE_SKINS, ZOMBIE_SKINS, ZOMBIE_SKINS, entity, false);
        }
    }

    public static class SoldierRenderer extends HumanoidMobRenderer<SoldierEntity, HumanoidModel<SoldierEntity>> {
        private final HumanoidModel<SoldierEntity> slim;

        public SoldierRenderer(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
            this.slim = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        }

        @Override
        public ResourceLocation getTextureLocation(SoldierEntity entity) {
            if (entity.isMercenary()) {
                return pick(MERCENARY_SKINS, MERCENARY_M, MERCENARY_F, entity, false);
            }
            return pick(SOLDIER_SKINS, SOLDIER_M, SOLDIER_F, entity, false);
        }

        @Override
        public void render(SoldierEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            HumanoidModel<SoldierEntity> previous = this.model;
            this.model = Variant.isFemale(entity.getUUID(), false) ? slim : previous;
            try {
                super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            } finally {
                this.model = previous;
            }
        }
    }

    public static class SkinHumanoidRenderer<T extends PathfinderMob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
        private final ResourceLocation[] general;
        private final ResourceLocation[] male;
        private final ResourceLocation[] female;
        private final boolean forceFemale;
        private final HumanoidModel<T> slim;

        public SkinHumanoidRenderer(EntityRendererProvider.Context context, ResourceLocation[] general,
                                    ResourceLocation[] male, ResourceLocation[] female, boolean forceFemale) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
            this.slim = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
            this.general = general;
            this.male = male;
            this.female = female;
            this.forceFemale = forceFemale;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return pick(general, male, female, entity, forceFemale);
        }

        @Override
        public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack,
                           MultiBufferSource buffer, int packedLight) {
            HumanoidModel<T> previous = this.model;
            this.model = Variant.isFemale(entity.getUUID(), forceFemale) ? slim : previous;
            try {
                super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            } finally {
                this.model = previous;
            }
        }
    }
}
