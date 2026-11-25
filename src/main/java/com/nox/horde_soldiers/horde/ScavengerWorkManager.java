package com.nox.horde_soldiers.horde;

import com.nox.horde_soldiers.entities.ModEntities;
import com.nox.horde_soldiers.entities.ScavengerEntity;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * Tamed-scavenger work system: a tamed scavenger periodically "goes to work" (farming, chopping
 * trees, mining) - it disappears for 5 minutes and returns to its owner carrying loot in its
 * inventory. Pending work is persisted in a SavedData so workers survive relogs/restarts.
 */
public final class ScavengerWorkManager {
    public static final long WORK_DURATION_TICKS = 5 * 60 * 20L; // 5 minutes

    public static final class PendingWork {
        public UUID workerId;
        public UUID ownerId;
        public long returnTime;
        public BlockPos home;
        public List<ItemStack> loot = new ArrayList<>();
    }

    private static final Map<UUID, PendingWork> PENDING = new ConcurrentHashMap<>();

    private ScavengerWorkManager() {}

    /** Loot categories a working scavenger can bring back. */
    private static final String[][] LOOT_POOLS = {
            {"minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log", "minecraft:jungle_log", "minecraft:cherry_log"},
            {"minecraft:cobblestone", "minecraft:stone", "minecraft:coal", "minecraft:iron_ore", "minecraft:gravel", "minecraft:sand"},
            {"minecraft:wheat", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot", "minecraft:melon_slice", "minecraft:pumpkin"},
            {"minecraft:apple", "minecraft:bread", "minecraft:cooked_beef", "minecraft:leather", "minecraft:string"},
    };

    private static List<ItemStack> generateLoot(net.minecraft.util.RandomSource random) {
        List<ItemStack> loot = new ArrayList<>();
        int categories = 1 + random.nextInt(2);
        for (int c = 0; c < categories; c++) {
            String[] pool = LOOT_POOLS[random.nextInt(LOOT_POOLS.length)];
            int stacks = 2 + random.nextInt(3);
            for (int i = 0; i < stacks; i++) {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(pool[random.nextInt(pool.length)]));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    loot.add(new ItemStack(item, 8 + random.nextInt(24)));
                }
            }
        }
        return loot;
    }

    /** Starts a work trip: the scavenger is removed from the world and scheduled to return with loot. */
    public static void startWork(ScavengerEntity mob, ServerLevel level) {
        if (!mob.isTamed()) return;
        UUID ownerId = mob.getOwnerUUID();
        if (ownerId == null) return;
        PendingWork work = new PendingWork();
        work.workerId = mob.getUUID();
        work.ownerId = ownerId;
        work.returnTime = level.getGameTime() + WORK_DURATION_TICKS;
        work.home = mob.blockPosition();
        work.loot = generateLoot(level.random);
        PENDING.put(work.workerId, work);
        persist(level);
        mob.discard();
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.literal("Your scavenger went to work (farming/chopping/mining) - back in 5 minutes with loot."));
        }
    }

    /** Called every server tick (throttled internally). Returns due workers to their owner. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || PENDING.isEmpty()) return;
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        long now = overworld.getGameTime();
        Iterator<Map.Entry<UUID, PendingWork>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            PendingWork work = it.next().getValue();
            if (now < work.returnTime) continue;
            it.remove();
            ServerPlayer owner = server.getPlayerList().getPlayer(work.ownerId);
            BlockPos spawnPos = owner != null ? owner.blockPosition() : work.home;
            ScavengerEntity scavenger = ModEntities.SCAVENGER.get().create(overworld);
            if (scavenger == null) { continue; }
            scavenger.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, overworld.random.nextFloat() * 360f, 0f);
            scavenger.setTamed(work.ownerId);
            overworld.addFreshEntity(scavenger);
            // fill its inventory with the loot; overflow drops at its feet
            var cap = scavenger.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
            if (cap.isPresent()) {
                IItemHandler inv = cap.orElse(null);
                for (ItemStack stack : work.loot) {
                    ItemStack remainder = stack;
                    for (int i = 0; i < inv.getSlots() && !remainder.isEmpty(); i++) {
                        remainder = inv.insertItem(i, remainder, false);
                    }
                    if (!remainder.isEmpty()) {
                        overworld.addFreshEntity(new ItemEntity(overworld, scavenger.getX(), scavenger.getY(), scavenger.getZ(), remainder));
                    }
                }
            }
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("Your scavenger returned with " + work.loot.size() + " loot stack(s)!"));
            }
            persist(overworld);
        }
    }

    // ---------- persistence ----------

    public static void loadFromDisk(ServerLevel overworld) {
        WorkSavedData data = WorkSavedData.get(overworld);
        PENDING.clear();
        for (PendingWork work : data.pending) PENDING.put(work.workerId, work);
    }

    private static void persist(ServerLevel overworld) {
        WorkSavedData data = WorkSavedData.get(overworld);
        data.pending.clear();
        data.pending.addAll(PENDING.values());
        data.setDirty();
    }

    /** SavedData holding pending scavenger work trips. */
    public static class WorkSavedData extends SavedData {
        public final List<PendingWork> pending = new ArrayList<>();

        public static WorkSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(WorkSavedData::load, WorkSavedData::new, "hzcode_scavenger_work");
        }

        public static WorkSavedData load(CompoundTag tag) {
            WorkSavedData data = new WorkSavedData();
            ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                PendingWork work = new PendingWork();
                work.workerId = t.getUUID("Worker");
                work.ownerId = t.getUUID("Owner");
                work.returnTime = t.getLong("ReturnTime");
                work.home = new BlockPos(t.getInt("HX"), t.getInt("HY"), t.getInt("HZ"));
                ListTag lootTag = t.getList("Loot", Tag.TAG_COMPOUND);
                for (int j = 0; j < lootTag.size(); j++) {
                    work.loot.add(ItemStack.of(lootTag.getCompound(j)));
                }
                data.pending.add(work);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (PendingWork work : pending) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Worker", work.workerId);
                t.putUUID("Owner", work.ownerId);
                t.putLong("ReturnTime", work.returnTime);
                t.putInt("HX", work.home.getX());
                t.putInt("HY", work.home.getY());
                t.putInt("HZ", work.home.getZ());
                ListTag lootTag = new ListTag();
                for (ItemStack stack : work.loot) lootTag.add(stack.save(new CompoundTag()));
                t.put("Loot", lootTag);
                list.add(t);
            }
            tag.put("Pending", list);
            return tag;
        }
    }
}
