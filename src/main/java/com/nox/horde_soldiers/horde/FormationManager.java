package com.nox.horde_soldiers.horde;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stable formation assignment: members are sorted by UUID (deterministic), indexed, and each
 * index maps to a fixed offset via {@link FormationMath}. No randomness, so no jitter.
 */
public final class FormationManager {
    private FormationManager() {}

    /**
     * Assigns formation slots for the given members.
     *
     * @param members       member map (uuid -> member)
     * @param leaderId      current leader (gets slot 0)
     * @param formation     formation type
     * @param spacing       configured spacing
     * @param facingRadians anchor facing in radians (Minecraft yaw convention)
     * @param anchorX       anchor world x (leader x or hold anchor x)
     * @param anchorY       anchor world y
     * @param anchorZ       anchor world z
     */
    public static void assignSlots(Map<UUID, HordeMember> members, UUID leaderId, FormationType formation,
                                   double spacing, double facingRadians, double anchorX, double anchorY, double anchorZ) {
        List<UUID> sorted = new ArrayList<>(members.keySet());
        sorted.sort(Comparator.comparing(UUID::toString));
        int count = sorted.size();
        int nextSlot = 1; // leader takes slot 0; everyone else gets a unique 1..n-1
        for (int i = 0; i < count; i++) {
            UUID id = sorted.get(i);
            HordeMember member = members.get(id);
            int slotIndex = id.equals(leaderId) ? 0 : nextSlot++;
            FormationSlot slot = new FormationSlot(slotIndex);
            double[] offset = FormationMath.slotOffset(formation, slotIndex, count, spacing, facingRadians);
            slot.setOffset(offset[0], offset[1]);
            slot.setWorldPos(anchorX + offset[0], anchorY, anchorZ + offset[1]);
            member.setFormationSlot(slot);
        }
    }
}
