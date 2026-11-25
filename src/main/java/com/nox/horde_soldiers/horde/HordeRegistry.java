package com.nox.horde_soldiers.horde;

import com.nox.horde_soldiers.config.ModConfig;
import com.nox.horde_soldiers.entities.IHordeEntity;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Per-dimension registry of active hordes. Guarantees at most one controller per horde id,
 * tracks entity -> horde membership, scopes the breach counter and path budget per dimension,
 * and bridges to {@link HordeSavedData} for persistence. No global mutable entity references.
 */
public class HordeRegistry {
    private static final Map<ResourceKey<Level>, HordeRegistry> INSTANCES = new LinkedHashMap<>();

    private final ServerLevel level;
    private final Map<UUID, HordeController> hordes = new LinkedHashMap<>();
    private final Map<UUID, UUID> entityToHorde = new LinkedHashMap<>();
    private boolean loadedFromDisk;
    private boolean dirty;
    private int dirtyTicks;
    private int activeBreaches;
    private int pathBudget;

    private HordeRegistry(ServerLevel level) {
        this.level = level;
    }

    public static HordeRegistry get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level.dimension(), key -> new HordeRegistry(level));
    }

    public static void tickAll(MinecraftServer server) {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            HordeRegistry registry = get(serverLevel);
            registry.tick();
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        for (ServerLevel serverLevel : server.getAllLevels()) {
            get(serverLevel).saveNow();
        }
        INSTANCES.clear();
    }

    public void tick() {
        if (!loadedFromDisk) {
            loadedFromDisk = true;
            HordeSavedData.loadInto(this);
        }
        pathBudget = ModConfig.COMMON.maxPathRecalcsPerTick.get();
        Iterator<Map.Entry<UUID, HordeController>> it = hordes.entrySet().iterator();
        while (it.hasNext()) {
            HordeController controller = it.next().getValue();
            controller.tick();
            if (controller.getMemberCount() == 0) {
                for (UUID memberId : controller.getData().memberIds()) {
                    entityToHorde.remove(memberId);
                }
                it.remove();
                dirty = true;
            }
        }
        if (dirty) {
            dirtyTicks++;
            if (dirtyTicks >= ModConfig.COMMON.saveInterval.get()) {
                saveNow();
                dirty = false;
                dirtyTicks = 0;
            }
        }
    }

    public void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------- hordes

    /** Creates an empty controller; at most one per horde id. */
    public HordeController createController(HordeType type, UUID hordeId) {
        HordeController existing = hordes.get(hordeId);
        if (existing != null) return existing;
        HordeController controller = new HordeController(level, new HordeData(hordeId, type));
        hordes.put(hordeId, controller);
        dirty = true;
        return controller;
    }

    public HordeController getController(UUID hordeId) {
        return hordes.get(hordeId);
    }

    public HordeController getHordeOf(UUID entityId) {
        UUID hordeId = entityToHorde.get(entityId);
        return hordeId == null ? null : hordes.get(hordeId);
    }

    public Map<UUID, HordeController> getHordes() {
        return hordes;
    }

    /**
     * Attaches a freshly loaded/spawned entity to its horde. Creates the controller from saved
     * data (or a singleton horde) when needed. Never force-loads chunks.
     */
    public void attach(LivingEntity entity) {
        if (!(entity instanceof IHordeEntity hordeEntity)) return;
        UUID hordeId = hordeEntity.getHordeId();
        HordeController controller;
        if (hordeId != null) {
            controller = hordes.get(hordeId);
            if (controller == null) {
                controller = createController(hordeEntity.getHordeType(), hordeId);
            }
        } else {
            controller = createController(hordeEntity.getHordeType(), UUID.randomUUID());
        }
        if (controller.registerMember(entity)) {
            entityToHorde.put(entity.getUUID(), controller.getHordeId());
        }
    }

    public void detach(UUID entityId) {
        HordeController controller = getHordeOf(entityId);
        if (controller != null) controller.unregisterMember(entityId);
        entityToHorde.remove(entityId);
    }

    // ------------------------------------------------------------- budgets

    /** Concurrent breach limit, scoped per dimension. */
    public boolean tryAcquireBreachSlot() {
        if (activeBreaches >= ModConfig.COMMON.maxConcurrentBreaches.get()) return false;
        activeBreaches++;
        return true;
    }

    public void releaseBreachSlot() {
        if (activeBreaches > 0) activeBreaches--;
    }

    public int getActiveBreaches() {
        return activeBreaches;
    }

    /** Global per-tick budget for follower full-path recalculations. */
    public boolean tryAcquirePathBudget() {
        if (pathBudget <= 0) return false;
        pathBudget--;
        return true;
    }

    // ------------------------------------------------------------- persistence

    public void saveNow() {
        HordeSavedData.saveFrom(this);
    }

    ServerLevel level() {
        return level;
    }

    Map<UUID, HordeController> hordesView() {
        return hordes;
    }

    Map<UUID, UUID> entityIndexView() {
        return entityToHorde;
    }
}
