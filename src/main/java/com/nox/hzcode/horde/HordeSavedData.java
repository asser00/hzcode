package com.nox.hzcode.horde;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-save persistence for hordes. Stores controller state (ids, orders, destinations) keyed by
 * horde id. Entity membership itself persists on each entity's own NBT and is re-attached on load.
 */
public class HordeSavedData extends SavedData {
    private static final String NAME = "hzcode_hordes";

    private CompoundTag root = new CompoundTag();

    public static HordeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HordeSavedData::load, HordeSavedData::new, NAME);
    }

    public static HordeSavedData load(CompoundTag tag) {
        HordeSavedData data = new HordeSavedData();
        data.root.merge(tag);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.merge(root);
        return tag;
    }

    static void loadInto(HordeRegistry registry) {
        HordeSavedData saved = get(registry.level());
        CompoundTag root = saved.root;
        ListTag hordes = root.getList("Hordes", Tag.TAG_COMPOUND);
        for (int i = 0; i < hordes.size(); i++) {
            CompoundTag hordeTag = hordes.getCompound(i);
            UUID hordeId = hordeTag.getUUID("HordeId");
            if (registry.getController(hordeId) == null) {
                HordeController loaded = HordeController.deserializeNBT(hordeTag, registry.level());
                registry.hordesView().put(hordeId, loaded);
                for (UUID memberId : loaded.getData().memberIds()) {
                    registry.entityIndexView().put(memberId, hordeId);
                }
            }
        }
        ListTag entities = root.getList("Entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entry = entities.getCompound(i);
            registry.entityIndexView().put(entry.getUUID("Entity"), entry.getUUID("Horde"));
        }
    }

    static void saveFrom(HordeRegistry registry) {
        HordeSavedData saved = get(registry.level());
        CompoundTag root = new CompoundTag();
        ListTag hordes = new ListTag();
        for (HordeController controller : registry.hordesView().values()) {
            hordes.add(controller.serializeNBT());
        }
        root.put("Hordes", hordes);
        ListTag entities = new ListTag();
        for (Map.Entry<UUID, UUID> entry : registry.entityIndexView().entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Entity", entry.getKey());
            e.putUUID("Horde", entry.getValue());
            entities.add(e);
        }
        root.put("Entities", entities);
        saved.root.merge(root);
        saved.setDirty();
    }
}
