package com.nox.horde_soldiers.horde;

import net.minecraft.nbt.CompoundTag;

/** A stable formation slot: member index plus a relative offset from the horde anchor. */
public class FormationSlot {
    private final int index;
    private double offsetX;
    private double offsetZ;
    private double worldX;
    private double worldY;
    private double worldZ;

    public FormationSlot(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public double getOffsetX() { return offsetX; }
    public double getOffsetZ() { return offsetZ; }
    public void setOffset(double x, double z) { this.offsetX = x; this.offsetZ = z; }

    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public double getWorldZ() { return worldZ; }
    public void setWorldPos(double x, double y, double z) { this.worldX = x; this.worldY = y; this.worldZ = z; }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", index);
        tag.putDouble("OffsetX", offsetX);
        tag.putDouble("OffsetZ", offsetZ);
        return tag;
    }

    public static FormationSlot deserializeNBT(CompoundTag tag) {
        FormationSlot slot = new FormationSlot(tag.getInt("Index"));
        slot.setOffset(tag.getDouble("OffsetX"), tag.getDouble("OffsetZ"));
        return slot;
    }
}
