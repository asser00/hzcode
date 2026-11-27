package com.nox.hzcode.client;

import com.nox.hzcode.HordeSoldiersMod;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.SkinVariant;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.Variant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * gender sub-pools .../skins/<type>/male/ and .../skins/<type>/female/. Mercenary soldiers use the
 * mercenary pools. Gender is deterministic from the entity UUID (Variant; medics are always
 * female), so server and client always agree and the look never changes after a reload. Female
 * entities render with the slim (Alex) player model, male with the classic one.
 *
 * Pools are resolved LAZILY on the render thread (not at class-init / RegisterRenderers time, when
 * the ResourceManager does not contain mod packs yet) and re-resolved after every resource reload
 * (invalidatePools via AddReloadListenerEvent). Render type stays entityCutoutNoCull so skins are
 * never see-through.
 */
public final class ClientModEvents {
    private static final Map<String, ResourceLocation[]> GENERAL = new HashMap<>();
    private static final Map<String, ResourceLocation[]> MALE = new HashMap<>();
    private static final Map<String, ResourceLocation[]> FEMALE = new HashMap<>();
    private static final Map<String, String> FALLBACK = Map.of(
            "zombie", "textures/entity/zombie.png",
            "soldier", "textures/entity/soldier_biker.png",
            "scavenger", "textures/entity/scavenger.png",
            "terrorist", "textures/entity/terrorist.png",
            "medic", "textures/entity/soldier_medic.png",
            "mercenary", "textures/entity/soldier_biker.png");
    private static boolean poolsResolved;

    private ClientModEvents() {}

    /** Reload listener that drops the cached skin pools after every resource reload. */
    public static class PoolInvalidator implements net.minecraft.server.packs.resources.ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resourceManager) {
            invalidatePools();
        }
    }

    /** Called on resource reload: drop cached pools so they are re-read from the ResourceManager. */
    public static void invalidatePools() {
        poolsResolved = false;
        GENERAL.clear();
        MALE.clear();
        FEMALE.clear();
    }

    private static void ensurePools() {
        if (poolsResolved) return;
        poolsResolved = true;
        for (String type : FALLBACK.keySet()) {
            GENERAL.put(type, listSkins("textures/entity/skins/" + type, type));
            MALE.put(type, listSkins("textures/entity/skins/" + type + "/male", type));
            FEMALE.put(type, listSkins("textures/entity/skins/" + type + "/female", type));
        }
    }

    private static ResourceLocation[] listSkins(String dir, String type) {
        List<ResourceLocation> list = new ArrayList<>();
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            list.addAll(rm.listResources(dir, p -> p.getPath().endsWith(".png")).keySet());
            list.sort(Comparator.comparing(ResourceLocation::getPath));
        } catch (Throwable ignored) {
            // fall through to the legacy single texture
        }
        if (list.isEmpty()) list.add(new ResourceLocation(HordeSoldiersMod.MOD_ID, FALLBACK.get(type)));
        if (type.equals("zombie")) {
            // vanilla Minecraft zombie textures are always available - mix them into the pool
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/husk.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie/drowned.png"));
            list.add(new ResourceLocation("minecraft", "textures/entity/zombie_villager/zombie_villager.png"));
        }
        return list.toArray(new ResourceLocation[0]);
    }

    /** Resolves the skin for an entity: gender sub-pool if present, else the general pool. */
    private static ResourceLocation pick(String type, Entity entity, boolean forceFemale) {
        ensurePools();
        boolean female = Variant.isFemale(entity.getUUID(), forceFemale);
        ResourceLocation[] chosen = (female ? FEMALE : MALE).get(type);
        if (chosen == null || chosen.length == 0) chosen = GENERAL.get(type);
        if (chosen == null || chosen.length == 0) {
            return new ResourceLocation(HordeSoldiersMod.MOD_ID, FALLBACK.getOrDefault(type, "textures/entity/zombie.png"));
        }
        return chosen[SkinVariant.index(entity.getUUID(), chosen.length)];
    }

    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ZOMBIE.get(), HordeZombieRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLDIER.get(), SoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SCAVENGER.get(), ctx -> new SkinHumanoidRenderer<>(ctx, "scavenger", false));
        event.registerEntityRenderer(ModEntities.TERRORIST.get(), ctx -> new SkinHumanoidRenderer<>(ctx, "terrorist", false));
        event.registerEntityRenderer(ModEntities.MEDIC.get(), ctx -> new SkinHumanoidRenderer<>(ctx, "medic", true)); // medics are female
    }

    public static class HordeZombieRenderer extends ZombieRenderer {
        public HordeZombieRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public ResourceLocation getTextureLocation(Zombie entity) {
            return pick("zombie", entity, false);
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
            return pick(entity.isMercenary() ? "mercenary" : "soldier", entity, false);
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
        private final String type;
        private final boolean forceFemale;
        private final HumanoidModel<T> slim;

        public SkinHumanoidRenderer(EntityRendererProvider.Context context, String type, boolean forceFemale) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
            this.slim = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
            this.type = type;
            this.forceFemale = forceFemale;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return pick(type, entity, forceFemale);
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
