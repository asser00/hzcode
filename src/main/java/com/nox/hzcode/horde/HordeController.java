package com.nox.hzcode.horde;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.IHordeEntity;
import com.nox.hzcode.entities.ZombieEntity;
import com.nox.hzcode.entities.ai.goal.ZombieBreachRules;
import com.nox.hzcode.faction.FactionManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

/**
 * ONE BRAIN per horde. Owns strategic target acquisition, the primary path, orders and formation.
 * Followers never run this; they read the published shared state and execute locally.
 * Exactly one controller exists per horde (enforced by {@link HordeRegistry}).
 */
public class HordeController {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("hzcode");
    private final ServerLevel level;
    private final HordeData data;
    private int ticksExisted;
    private int lastLootScan = -1;
    private int lastPathRecalc;
    private BlockPos lastPathDestination;

    public HordeController(ServerLevel level, HordeData data) {
        this.level = level;
        this.data = data;
    }

    public UUID getHordeId() { return data.getHordeId(); }
    public HordeType getType() { return data.getType(); }
    public HordeData getData() { return data; }
    public ServerLevel getLevel() { return level; }
    public int getMemberCount() { return data.getMembers().size(); }

    public HordeMember getMember(UUID entityId) { return data.getMembers().get(entityId); }

    // ------------------------------------------------------------------ membership

    /** Registers an entity as a member. Rejects duplicates and cross-horde theft. */
    public boolean registerMember(LivingEntity entity) {
        UUID id = entity.getUUID();
        HordeMember existing = data.getMembers().get(id);
        if (existing != null) return true;
        if (entity instanceof IHordeEntity hordeEntity && !data.getHordeId().equals(hordeEntity.getHordeId())) {
            return false; // belongs to another horde
        }
        HordeMember member = new HordeMember(id, data.getHordeId());
        if (entity instanceof IHordeEntity hordeEntity) {
            hordeEntity.setHordeId(data.getHordeId());
            member.setLeader(hordeEntity.isHordeLeader());
        }
        data.getMembers().put(id, member);
        if (member.isLeader()) data.setLeaderId(id);
        markDirty();
        return true;
    }

    public void unregisterMember(UUID entityId) {
        HordeMember removed = data.getMembers().remove(entityId);
        if (removed != null && removed.isLeader()) {
            data.setLeaderId(null);
        }
        markDirty();
    }

    // ------------------------------------------------------------------ ticking

    public void tick() {
        ticksExisted++;
        for (HordeMember member : data.getMembers().values()) member.tick();

        ModConfig.Common cfg = ModConfig.COMMON;
        int leaderCheck = cfg.leaderCheckInterval.get();
        int strategic = cfg.strategicInterval.get();
        int formation = cfg.formationInterval.get();

        if (ticksExisted % leaderCheck == 0) {
            cleanupMembers();
            ensureLeader();
        }
        if (data.getMembers().isEmpty()) {
            return; // registry will drop the empty horde
        }
        if (ticksExisted % strategic == 0) {
            makeStrategicDecisions();
        }
        if (ticksExisted % formation == 0) {
            recalculateFormation();
        }
        updatePrimaryPath();
    }

    /** Removes members whose entities are dead/removed. Unloaded (chunk-unloaded) members stay dormant. */
    private void cleanupMembers() {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, HordeMember> entry : data.getMembers().entrySet()) {
            LivingEntity entity = resolveMember(entry.getKey());
            if (entity == null) {
                entry.getValue().markDormant();
                continue;
            }
            entry.getValue().markActive();
            if (!entity.isAlive() || entity.isRemoved()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID id : toRemove) {
            unregisterMember(id);
        }
    }

    /** Deterministic leader election: exactly one leader, promoted only when needed. */
    private void ensureLeader() {
        LivingEntity currentLeader = resolveMember(data.getLeaderId());
        if (currentLeader != null && currentLeader.isAlive() && data.getMembers().containsKey(currentLeader.getUUID())) {
            return; // leader is fine
        }
        List<LeaderElection.Candidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, HordeMember> entry : data.getMembers().entrySet()) {
            LivingEntity entity = resolveMember(entry.getKey());
            if (entity == null || !entity.isAlive()) continue; // dormant members cannot lead
            candidates.add(new LeaderElection.Candidate(
                    entry.getKey(), entry.getValue().isLeader(), entity.getHealth(), entry.getValue().getTicksInHorde()));
        }
        UUID newLeaderId = LeaderElection.pickLeader(candidates);
        if (newLeaderId == null) {
            data.setLeaderId(null);
            return;
        }
        if (newLeaderId.equals(data.getLeaderId())) return;
        // demote old
        HordeMember old = data.getMembers().get(data.getLeaderId());
        if (old != null) {
            old.setLeader(false);
            LivingEntity oldEntity = resolveMember(old.getEntityId());
            if (oldEntity instanceof IHordeEntity hordeEntity) hordeEntity.setHordeLeader(false);
        }
        HordeMember newLeader = data.getMembers().get(newLeaderId);
        LOGGER.info("horde {} leader changed -> {}", getHordeId(), newLeaderId);
        newLeader.setLeader(true);
        data.setLeaderId(newLeaderId);
        LivingEntity newLeaderEntity = resolveMember(newLeaderId);
        if (newLeaderEntity instanceof IHordeEntity hordeEntity) hordeEntity.setHordeLeader(true);
        markDirty();
    }

    // ------------------------------------------------------------------ strategy

    private void makeStrategicDecisions() {
        data.setLastStrategicUpdate(ticksExisted);
        LivingEntity leader = resolveLeader();
        if (leader == null) return; // leader chunk unloaded; followers hold position
        switch (data.getType()) {
            case SOLDIER, TERRORIST, MERCENARY -> makeSoldierDecisions(leader);
            case SCAVENGER -> makeScavengerDecisions(leader);
            case ZOMBIE -> makeZombieDecisions(leader);
        }
    }

    private void makeSoldierDecisions(LivingEntity leader) {
        ModConfig.Common cfg = ModConfig.COMMON;
        HordeOrder order = data.getOrder();
        if (order == HordeOrder.CEASE_FIRE) {
            clearSharedTarget();
            data.setState(HordeState.ACTIVE);
            return;
        }
        if (order == HordeOrder.RETREAT) {
            data.setState(HordeState.RETREATING);
            data.setDestination(findRetreatPosition(leader));
            data.setFormation(FormationType.FOLLOW_TRAIL);
            clearSharedTarget();
            return;
        }
        if (order == HordeOrder.HOLD_POSITION || order == HordeOrder.DEFEND_POSITION) {
            if (data.getAnchor() == null) data.setAnchor(leader.blockPosition());
            data.setDestination(data.getAnchor());
            data.setFormation(FormationType.HOLD_POSITION);
            data.setState(HordeState.ACTIVE);
            return;
        }
        if (order == HordeOrder.PROTECT_PLAYER) {
            Player player = findNearestPlayer(leader, cfg.targetAcquisitionRange.get());
            if (player != null) {
                data.setDestination(player.blockPosition());
                data.setFormation(FormationType.PROTECT_LEADER);
                data.setState(HordeState.ACTIVE);
                return;
            }
            // fall through to combat if no player nearby
        }
        // default / ATTACK_TARGET / FOLLOW: acquire hostile target (leader only)
        if (ticksExisted % cfg.targetRefreshInterval.get() == 0 || !isValidTarget()) {
            acquireTarget(leader);
        }
        if (isValidTarget()) {
            LivingEntity target = resolveTarget();
            data.setState(HordeState.COMBAT);
            data.setDestination(target.blockPosition());
            data.setFormation(FormationType.COMBAT_SPREAD);
            shareTargetWithMembers(target);
        } else {
            data.setState(HordeState.ACTIVE);
            if (order == HordeOrder.FOLLOW) {
                Player player = findNearestPlayer(leader, 48.0);
                if (player != null) data.setDestination(player.blockPosition());
                data.setFormation(FormationType.FOLLOW_TRAIL);
            }
        }
    }

    private void makeScavengerDecisions(LivingEntity leader) {
        ModConfig.Common cfg = ModConfig.COMMON;
        // major threats first (single scan by the leader)
        if (ticksExisted % cfg.targetRefreshInterval.get() == 0 || !isValidTarget()) {
            acquireTarget(leader);
        }
        if (isValidTarget()) {
            LivingEntity target = resolveTarget();
            data.setState(HordeState.COMBAT);
            data.setDestination(target.blockPosition());
            data.setFormation(FormationType.COMBAT_SPREAD);
            shareTargetWithMembers(target);
            data.setLootTarget(null);
            data.setLootAssignee(null);
            return;
        }
        // leader-driven, throttled loot discovery: ONE scan per interval for the whole horde
        int lootInterval = cfg.scavengerLootInterval.get();
        if ((data.getLootTarget() == null || !isLootTargetValid(data.getLootTarget()))
                && (lastLootScan < 0 || ticksExisted - lastLootScan >= lootInterval)) {
            lastLootScan = ticksExisted;
            BlockPos found = findNearbyLootContainer(leader.blockPosition(), cfg.scavengerLootRadius.get());
            if (found != null) {
                data.setLootTarget(found);
                data.setLootAssignee(nearestMemberId(found));
            }
        }
        if (data.getLootTarget() != null && isLootTargetValid(data.getLootTarget())) {
            data.setDestination(data.getLootTarget());
            data.setFormation(FormationType.FOLLOW_TRAIL);
            data.setState(HordeState.ACTIVE);
        } else {
            data.setState(HordeState.IDLE);
            if (data.getAnchor() == null) data.setAnchor(leader.blockPosition());
            data.setDestination(data.getAnchor());
            data.setFormation(FormationType.HOLD_POSITION);
        }
    }

    private void makeZombieDecisions(LivingEntity leader) {
        ModConfig.Common cfg = ModConfig.COMMON;
        if (ticksExisted % cfg.targetRefreshInterval.get() == 0 || !isValidTarget()) {
            acquireTarget(leader);
        }
        if (isValidTarget()) {
            LivingEntity target = resolveTarget();
            data.setState(HordeState.COMBAT);
            data.setDestination(target.blockPosition());
            data.setFormation(FormationType.COMBAT_SPREAD);
            shareTargetWithMembers(target);
            // coordinate breaching: designate a breacher and publish the blocking block
            if (cfg.breachEnabled.get() && data.getBreachTarget() == null) {
                BlockPos blocking = findBlockingBlockOnPath(leader.blockPosition(), target.blockPosition());
                if (blocking != null) {
                    data.setBreachTarget(blocking);
                    assignBreacher();
                }
            }
        } else {
            data.setState(HordeState.ACTIVE);
            data.setFormation(FormationType.FOLLOW_TRAIL);
            data.setBreachTarget(null);
        }
    }

    // ------------------------------------------------------------------ targeting

    /** The ONLY strategic target acquisition in the horde. Runs on the leader, throttled. */
    private void acquireTarget(LivingEntity leader) {
        double range = ModConfig.COMMON.targetAcquisitionRange.get();
        AABB box = leader.getBoundingBox().inflate(range, range / 2.0, range);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != leader && e.isAlive() && FactionManager.isHostileToHorde(data.getType().faction(), e))) {
            double dist = leader.distanceToSqr(candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        data.setTargetId(best == null ? null : best.getUUID());
    }

    private boolean isValidTarget() {
        LivingEntity target = resolveTarget();
        return target != null && target.isAlive() && FactionManager.isHostileToHorde(data.getType().faction(), target);
    }

    private LivingEntity resolveTarget() {
        return resolveMember(data.getTargetId());
    }

    /** Publishes the shared target to every loaded follower so they execute without scanning. */
    private void shareTargetWithMembers(LivingEntity target) {
        for (Map.Entry<UUID, HordeMember> entry : data.getMembers().entrySet()) {
            if (entry.getKey().equals(data.getLeaderId())) continue;
            LivingEntity entity = resolveMember(entry.getKey());
            if (entity instanceof Mob mob && mob.getTarget() == null) {
                mob.setTarget(target);
            }
        }
    }

    private void clearSharedTarget() {
        data.setTargetId(null);
        for (UUID id : data.getMembers().keySet()) {
            LivingEntity entity = resolveMember(id);
            if (entity instanceof Mob mob) mob.setTarget(null);
        }
    }

    // ------------------------------------------------------------------ navigation

    /** Leader computes the one strategic path; followers only read formation slots. */
    private void updatePrimaryPath() {
        LivingEntity leader = resolveLeader();
        if (leader == null || !(leader instanceof Mob mob)) return;
        BlockPos dest = data.getDestination();
        if (dest == null) return;
        int pathInterval = ModConfig.COMMON.leaderPathInterval.get();
        boolean destChanged = lastPathDestination == null || lastPathDestination.distSqr(dest) > 16;
        if (destChanged || mob.getNavigation().isDone() || ticksExisted - lastPathRecalc >= pathInterval) {
            if (destChanged || mob.getNavigation().isDone()) {
                double speed = mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 8.0 + 0.6;
                mob.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed);
                lastPathRecalc = ticksExisted;
                lastPathDestination = dest;
                data.setLastPathUpdate(ticksExisted);
            }
        }
    }

    public void recalculateFormation() {
        LivingEntity leader = resolveLeader();
        double anchorX, anchorY, anchorZ, facing;
        if (data.getFormation() == FormationType.HOLD_POSITION && data.getAnchor() != null) {
            anchorX = data.getAnchor().getX() + 0.5;
            anchorY = data.getAnchor().getY();
            anchorZ = data.getAnchor().getZ() + 0.5;
            facing = leader != null ? Math.toRadians(leader.getYRot()) : 0.0;
        } else if (leader != null) {
            anchorX = leader.getX();
            anchorY = leader.getY();
            anchorZ = leader.getZ();
            facing = Math.toRadians(leader.getYRot());
        } else {
            return;
        }
        FormationManager.assignSlots(data.getMembers(), data.getLeaderId(), data.getFormation(),
                ModConfig.COMMON.formationSpacing.get(), facing, anchorX, anchorY, anchorZ);
    }

    // ------------------------------------------------------------------ helpers

    private LivingEntity resolveLeader() {
        return resolveMember(data.getLeaderId());
    }

    private LivingEntity resolveMember(UUID id) {
        if (id == null) return null;
        if (!(level.getEntity(id) instanceof LivingEntity living)) return null;
        return living;
    }

    private Player findNearestPlayer(LivingEntity near, double range) {
        return level.getNearestPlayer(near.getX(), near.getY(), near.getZ(), range, false);
    }

    private BlockPos findRetreatPosition(LivingEntity leader) {
        LivingEntity target = resolveTarget();
        Vec3 away = target != null
                ? leader.position().subtract(target.position()).normalize()
                : new Vec3(leader.getRandom().nextDouble() - 0.5, 0, leader.getRandom().nextDouble() - 0.5).normalize();
        BlockPos pos = leader.blockPosition().offset((int) (away.x * 12), 0, (int) (away.z * 12));
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
    }

    /** Leader-driven loot scan: one throttled scan for the whole horde, block-entity check only. */
    private BlockPos findNearbyLootContainer(BlockPos center, int radius) {
        int r = Math.min(radius, 32);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                for (int dy = -2; dy <= 2; dy += 2) {
                    mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (isLootContainer(mutable.immutable())) {
                        return mutable.immutable();
                    }
                }
            }
        }
        return null;
    }

    public static boolean isLootContainer(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    private boolean isLootContainer(BlockPos pos) {
        return isLootContainer(level, pos);
    }

    private boolean isLootTargetValid(BlockPos pos) {
        return isLootContainer(pos);
    }

    private UUID nearestMemberId(BlockPos pos) {
        UUID best = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID id : data.getMembers().keySet()) {
            LivingEntity entity = resolveMember(id);
            if (entity == null) continue;
            double dist = entity.blockPosition().distSqr(pos);
            if (dist < bestDist) {
                bestDist = dist;
                best = id;
            }
        }
        return best;
    }

    /** Finds a breachable block roughly on the line between two positions. */
    private BlockPos findBlockingBlockOnPath(BlockPos from, BlockPos to) {
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            BlockPos pos = from.offset(
                    (to.getX() - from.getX()) * i / steps,
                    (to.getY() - from.getY()) * i / steps,
                    (to.getZ() - from.getZ()) * i / steps);
            BlockState state = level.getBlockState(pos);
            if (ZombieBreachRules.isBreachable(level, pos, state)) {
                return pos;
            }
        }
        return null;
    }

    /** Marks the first eligible zombie as breacher so role-specific behavior is reachable. */
    private void assignBreacher() {
        for (Map.Entry<UUID, HordeMember> entry : data.getMembers().entrySet()) {
            LivingEntity entity = resolveMember(entry.getKey());
            if (entity instanceof ZombieEntity zombie) {
                zombie.setBreacher(true);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ orders

    /** Applies a validated order. Persisted in HordeData; survives leader death. */
    public void setOrder(HordeOrder newOrder, BlockPos moveTarget) {
        data.setOrder(newOrder);
        switch (newOrder) {
            case HOLD_POSITION, DEFEND_POSITION -> {
                LivingEntity leader = resolveLeader();
                if (leader != null) data.setAnchor(leader.blockPosition());
                data.setFormation(FormationType.HOLD_POSITION);
                data.setState(HordeState.ACTIVE);
            }
            case PROTECT_PLAYER -> data.setFormation(FormationType.PROTECT_LEADER);
            case ATTACK_TARGET, FOLLOW -> data.setFormation(FormationType.COMBAT_SPREAD);
            case RETREAT -> data.setState(HordeState.RETREATING);
            case CEASE_FIRE -> clearSharedTarget();
            case RECALL -> {
                LivingEntity leader = resolveLeader();
                if (leader != null) data.setDestination(leader.blockPosition());
            }
            case MOVE_TO -> {
                if (moveTarget != null) {
                    data.setAnchor(moveTarget);
                    data.setDestination(moveTarget);
                    data.setFormation(FormationType.HOLD_POSITION);
                }
            }
        }
        markDirty();
    }

    public HordeOrder getOrder() { return data.getOrder(); }

    // ------------------------------------------------------------------ persistence

    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    public static HordeController deserializeNBT(CompoundTag tag, ServerLevel level) {
        return new HordeController(level, HordeData.deserializeNBT(tag));
    }

    private void markDirty() {
        HordeRegistry.get(level).markDirty();
    }
}
