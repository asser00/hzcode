package com.nox.hzcode.horde;

import java.util.Collection;
import java.util.UUID;

/**
 * Deterministic leader election. The candidate with the highest score wins; ties break on UUID
 * string order so every observer picks the same leader. Pure and unit-testable.
 */
public final class LeaderElection {
    private LeaderElection() {}

    public record Candidate(UUID id, boolean currentLeader, float health, int ticksInHorde) {}

    public static UUID pickLeader(Collection<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;
        Candidate best = null;
        long bestScore = Long.MIN_VALUE;
        String bestId = null;
        for (Candidate c : candidates) {
            if (c == null || c.id() == null) continue;
            long score = (c.currentLeader() ? 1_000_000_000L : 0L)
                    + (long) (c.health() * 1000.0f)
                    + c.ticksInHorde();
            int cmp = Long.compare(score, bestScore);
            if (cmp > 0 || (cmp == 0 && bestId != null && c.id().toString().compareTo(bestId) < 0)) {
                best = c;
                bestScore = score;
                bestId = c.id().toString();
            }
        }
        return best == null ? null : best.id();
    }
}
