package com.nox.horde_soldiers.horde;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Shared, serializable horde state: identity, membership, leader, target, destination, order,
 * formation and faction-specific shared state. Contains identifiers and positions only - never
 * live Entity references.
 */
public class HordeData {
    private final UUID hordeId;
    private HordeType type;
    private UUID leaderId;
    private final Map<UUID, HordeMember> members = new LinkedHashMap<>();
    private HordeState state = HordeState.IDLE;
    private UUID targetId;
    private BlockPos destination;
    private BlockPos anchor;
    private HordeOrder order = HordeOrder.FOLLOW;
    private FormationType formation = FormationType.FOLLOW_TRAIL;
    private BlockPos lootTarget;
    private UUID lootAssignee;
    private BlockPos breachTarget;
    private int lastStrategicUpdate;
    private int lastPathUpdate;

    public HordeData(UUID hordeId, HordeType type) {
        this.hordeId = hordeId;
        this.type = type;
    }

    public UUID getHordeId() { return hordeId; }
    public HordeType getType() { return type; }
    public void setType(HordeType type) { this.type = type; }
    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }
    public Map<UUID, HordeMember> getMembers() { return members; }
    public HordeState getState() { return state; }
    public void setState(HordeState state) { this.state = state; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID targetId) { this.targetId = targetId; }
    public BlockPos getDestination() { return destination; }
    public void setDestination(BlockPos destination) { this.destination = destination; }
    public BlockPos getAnchor() { return anchor; }
    public void setAnchor(BlockPos anchor) { this.anchor = anchor; }
    public HordeOrder getOrder() { return order; }
    public void setOrder(HordeOrder order) { this.order = order; }
    public FormationType getFormation() { return formation; }
    public void setFormation(FormationType formation) { this.formation = formation; }
    public BlockPos getLootTarget() { return lootTarget; }
    public void setLootTarget(BlockPos lootTarget) { this.lootTarget = lootTarget; }
    public UUID getLootAssignee() { return lootAssignee; }
    public void setLootAssignee(UUID lootAssignee) { this.lootAssignee = lootAssignee; }
    public BlockPos getBreachTarget() { return breachTarget; }
    public void setBreachTarget(BlockPos breachTarget) { this.breachTarget = breachTarget; }
    public int getLastStrategicUpdate() { return lastStrategicUpdate; }
    public void setLastStrategicUpdate(int v) { this.lastStrategicUpdate = v; }
    public int getLastPathUpdate() { return lastPathUpdate; }
    public void setLastPathUpdate(int v) { this.lastPathUpdate = v; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("HordeId", hordeId);
        tag.putString("Type", type.name());
        if (leaderId != null) tag.putUUID("LeaderId", leaderId);
        ListTag memberList = new ListTag();
        for (HordeMember member : members.values()) {
            memberList.add(member.serializeNBT());
        }
        tag.put("Members", memberList);
        tag.putString("State", state.name());
        if (targetId != null) tag.putUUID("TargetId", targetId);
        if (destination != null) tag.putLong("Destination", destination.asLong());
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
        tag.putString("Order", order.name());
        tag.putString("Formation", formation.name());
        if (lootTarget != null) tag.putLong("LootTarget", lootTarget.asLong());
        if (lootAssignee != null) tag.putUUID("LootAssignee", lootAssignee);
        if (breachTarget != null) tag.putLong("BreachTarget", breachTarget.asLong());
        tag.putInt("LastStrategicUpdate", lastStrategicUpdate);
        tag.putInt("LastPathUpdate", lastPathUpdate);
        return tag;
    }

    public static HordeData deserializeNBT(CompoundTag tag) {
        UUID hordeId = tag.getUUID("HordeId");
        HordeType type;
        try {
            type = HordeType.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = HordeType.ZOMBIE;
        }
        HordeData data = new HordeData(hordeId, type);
        if (tag.hasUUID("LeaderId")) data.setLeaderId(tag.getUUID("LeaderId"));
        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            HordeMember member = HordeMember.deserializeNBT(memberList.getCompound(i));
            data.members.put(member.getEntityId(), member);
        }
        try { data.setState(HordeState.valueOf(tag.getString("State"))); } catch (IllegalArgumentException ignored) {}
        if (tag.hasUUID("TargetId")) data.setTargetId(tag.getUUID("TargetId"));
        if (tag.contains("Destination")) data.setDestination(BlockPos.of(tag.getLong("Destination")));
        if (tag.contains("Anchor")) data.setAnchor(BlockPos.of(tag.getLong("Anchor")));
        try { data.setOrder(HordeOrder.valueOf(tag.getString("Order"))); } catch (IllegalArgumentException ignored) {}
        try { data.setFormation(FormationType.valueOf(tag.getString("Formation"))); } catch (IllegalArgumentException ignored) {}
        if (tag.contains("LootTarget")) data.setLootTarget(BlockPos.of(tag.getLong("LootTarget")));
        if (tag.hasUUID("LootAssignee")) data.setLootAssignee(tag.getUUID("LootAssignee"));
        if (tag.contains("BreachTarget")) data.setBreachTarget(BlockPos.of(tag.getLong("BreachTarget")));
        data.setLastStrategicUpdate(tag.getInt("LastStrategicUpdate"));
        data.setLastPathUpdate(tag.getInt("LastPathUpdate"));
        return data;
    }

    public List<UUID> memberIds() {
        return new ArrayList<>(members.keySet());
    }
}
