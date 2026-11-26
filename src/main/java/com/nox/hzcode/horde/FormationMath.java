package com.nox.hzcode.horde;

/**
 * Pure formation math. Slot offsets depend only on (formation, member index, member count,
 * spacing, facing) so assignments are stable and jitter-free. No Minecraft classes, unit-testable.
 * Facing convention matches Minecraft: forward = (-sin(yaw), -cos(yaw)) in (x, z).
 */
public final class FormationMath {
    private FormationMath() {}

    /**
     * @return {offsetX, offsetZ} relative to the anchor (leader position or hold anchor)
     */
    public static double[] slotOffset(FormationType type, int index, int memberCount, double spacing, double facingRadians) {
        if (index <= 0) return new double[]{0.0, 0.0};
        double fx = -Math.sin(facingRadians);
        double fz = -Math.cos(facingRadians);
        switch (type) {
            case FOLLOW_TRAIL: {
                // single file behind the leader, slight alternating side offset
                double dist = spacing * index;
                double side = (index % 2 == 0 ? 1.0 : -1.0) * spacing * 0.35 * (index / 2);
                double rx = fz;
                double rz = -fx;
                return new double[]{-fx * dist + rx * side, -fz * dist + rz * side};
            }
            case PROTECT_LEADER: {
                // ring around the leader
                int ringMembers = Math.max(1, memberCount - 1);
                double angle = (2.0 * Math.PI * index) / ringMembers;
                double radius = spacing * 1.5;
                return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
            }
            case COMBAT_SPREAD: {
                // arc in front of the leader, facing the threat
                int spreadMembers = Math.max(1, memberCount - 1);
                double spread = Math.toRadians(120.0); // total arc
                double angle = -spread / 2.0 + spread * ((double) index / spreadMembers);
                double row = 1.0 + (index - 1) / 4.0;
                double radius = spacing * row;
                double a = facingRadians + angle;
                return new double[]{-Math.sin(a) * radius, -Math.cos(a) * radius};
            }
            case HOLD_POSITION:
            default: {
                // ring around the hold anchor
                int ringMembers = Math.max(1, memberCount);
                double angle = (2.0 * Math.PI * index) / ringMembers;
                double radius = spacing * (1.0 + (index / 8));
                return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
            }
        }
    }
}
