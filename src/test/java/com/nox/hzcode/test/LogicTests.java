package com.nox.hzcode.test;

import com.nox.hzcode.faction.Faction;
import com.nox.hzcode.faction.FactionRelation;
import com.nox.hzcode.faction.FactionRelations;
import com.nox.hzcode.horde.FormationManager;
import com.nox.hzcode.horde.FormationMath;
import com.nox.hzcode.horde.FormationSlot;
import com.nox.hzcode.horde.FormationType;
import com.nox.hzcode.horde.HordeData;
import com.nox.hzcode.horde.HordeLink;
import com.nox.hzcode.horde.HordeMember;
import com.nox.hzcode.horde.HordeOrder;
import com.nox.hzcode.horde.HordeState;
import com.nox.hzcode.horde.HordeType;
import com.nox.hzcode.horde.LeaderElection;
import com.nox.hzcode.horde.LootTransfer;
import com.nox.hzcode.entities.Variant;
import com.nox.hzcode.equipment.WeaponStocker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Dependency-free logic tests (run via `gradle logicTest` or build-offline.ps1). Covers the parts
 * of the horde engine that are pure or only need the vanilla registries: formation math and slot
 * assignment, faction matrix, order/type parsing, deterministic leader election, NBT round-trips
 * of the persisted horde state, and the lossless loot transfer with real ItemStacks.
 *
 * MC-dependent tests bootstrap the vanilla registries without Forge/FML by flipping Bootstrap's
 * internal "bootstrapped" flag via reflection (full Bootstrap.bootStrap() pulls in Forge's
 * NetworkHooks, which cannot initialize outside FML). If that fails, those tests skip gracefully.
 */
public final class LogicTests {
    private static int passed;
    private static int failed;

    public static void main(String[] args) {
        testFormationMath();
        testFormationManager();
        testFactionMatrix();
        testFactionSymmetry();
        testOrderParsing();
        testHordeTypeParse();
        testLeaderElection();
        testNbtRoundTrip();
        testLootTransfer();
        testWeaponStocker();
        testSkinVariant();
        testVariants();
        System.out.println("===================================");
        System.out.println("LogicTests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }

    // ------------------------------------------------------------------ formation math

    private static void testFormationMath() {
        // leader slot is always the anchor
        double[] leader = FormationMath.slotOffset(FormationType.FOLLOW_TRAIL, 0, 6, 2.0, 0.0);
        check(leader[0] == 0.0 && leader[1] == 0.0, "formation.leaderAtAnchor");

        // slots are deterministic
        double[] a = FormationMath.slotOffset(FormationType.COMBAT_SPREAD, 3, 6, 2.0, 1.0);
        double[] b = FormationMath.slotOffset(FormationType.COMBAT_SPREAD, 3, 6, 2.0, 1.0);
        check(a[0] == b[0] && a[1] == b[1], "formation.deterministic");

        // distinct indices -> distinct slots (no stacking)
        Set<String> slots = new HashSet<>();
        for (FormationType type : FormationType.values()) {
            slots.clear();
            for (int i = 0; i < 8; i++) {
                double[] s = FormationMath.slotOffset(type, i, 8, 2.0, 0.7);
                slots.add(s[0] + "," + s[1]);
            }
            check(slots.size() == 8, "formation.distinctSlots." + type);
        }

        // FOLLOW_TRAIL: followers are behind the leader (yaw 0 faces -z, so behind is +z)
        double[] behind = FormationMath.slotOffset(FormationType.FOLLOW_TRAIL, 2, 6, 2.0, 0.0);
        check(behind[1] > 0.0, "formation.trailBehindLeader");
    }

    // ------------------------------------------------------------------ formation manager

    private static void testFormationManager() {
        UUID hordeId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        Map<UUID, HordeMember> members = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID();
            members.put(id, new HordeMember(id, hordeId));
        }
        HordeMember leader = new HordeMember(leaderId, hordeId);
        leader.setLeader(true);
        members.put(leaderId, leader);

        FormationManager.assignSlots(members, leaderId, FormationType.PROTECT_LEADER, 2.0, 0.5, 100.0, 64.0, 200.0);
        FormationSlot leaderSlot = members.get(leaderId).getFormationSlot();
        check(leaderSlot.getIndex() == 0, "fm.leaderSlotZero");
        check(leaderSlot.getWorldX() == 100.0 && leaderSlot.getWorldZ() == 200.0, "fm.leaderAtAnchor");

        boolean allAssigned = true;
        Set<String> worldSlots = new HashSet<>();
        for (HordeMember m : members.values()) {
            FormationSlot s = m.getFormationSlot();
            if (s == null) { allAssigned = false; continue; }
            worldSlots.add(s.getWorldX() + "," + s.getWorldZ());
        }
        check(allAssigned, "fm.allAssigned");
        check(worldSlots.size() == members.size(), "fm.distinctWorldSlots");

        // determinism: identical input -> identical slot world positions
        Map<UUID, HordeMember> again = new LinkedHashMap<>();
        for (Map.Entry<UUID, HordeMember> e : members.entrySet()) {
            again.put(e.getKey(), new HordeMember(e.getKey(), e.getValue().getHordeId()));
        }
        FormationManager.assignSlots(again, leaderId, FormationType.PROTECT_LEADER, 2.0, 0.5, 100.0, 64.0, 200.0);
        boolean same = true;
        for (Map.Entry<UUID, HordeMember> e : members.entrySet()) {
            FormationSlot x = e.getValue().getFormationSlot();
            FormationSlot y = again.get(e.getKey()).getFormationSlot();
            if (x.getWorldX() != y.getWorldX() || x.getWorldZ() != y.getWorldZ()) same = false;
        }
        check(same, "fm.deterministic");

        // regression: leader always slot 0 and all world slots unique, regardless of UUID sort order
        boolean regressionOk = true;
        for (int trial = 0; trial < 25; trial++) {
            Map<UUID, HordeMember> m2 = new LinkedHashMap<>();
            UUID lid = UUID.randomUUID();
            for (int i = 0; i < 6; i++) { UUID id = UUID.randomUUID(); m2.put(id, new HordeMember(id, hordeId)); }
            HordeMember l = new HordeMember(lid, hordeId);
            l.setLeader(true);
            m2.put(lid, l);
            FormationManager.assignSlots(m2, lid, FormationType.PROTECT_LEADER, 2.0, 0.5, 0.0, 64.0, 0.0);
            if (m2.get(lid).getFormationSlot().getIndex() != 0) regressionOk = false;
            Set<String> ws = new HashSet<>();
            for (HordeMember mm : m2.values()) {
                FormationSlot s = mm.getFormationSlot();
                ws.add(s.getWorldX() + "," + s.getWorldZ());
            }
            if (ws.size() != m2.size()) regressionOk = false;
        }
        check(regressionOk, "fm.noSlotCollision25Trials");
    }

    // ------------------------------------------------------------------ factions

    private static void testFactionMatrix() {
        check(FactionRelations.isHostile(Faction.ZOMBIE, Faction.PLAYER), "faction.zombieHostileToPlayer");
        check(FactionRelations.isHostile(Faction.SOLDIER, Faction.ZOMBIE), "faction.soldierHostileToZombie");
        check(FactionRelations.isAllied(Faction.PLAYER, Faction.SOLDIER), "faction.playerAlliedSoldier");
        check(FactionRelations.isAllied(Faction.ZOMBIE, Faction.ZOMBIE), "faction.selfAllied");
        check(FactionRelations.isHostile(Faction.SOLDIER, Faction.TERRORIST), "faction.soldierHostileToTerrorist");
        check(FactionRelations.get(Faction.PLAYER, Faction.SCAVENGER) == FactionRelation.NEUTRAL, "faction.playerScavengerDefaultNeutral");
        check(FactionRelations.isAllied(Faction.MEDIC, Faction.PLAYER), "faction.medicAlliedPlayer");
        check(FactionRelations.isAllied(Faction.MEDIC, Faction.SOLDIER), "faction.medicAlliedSoldier");
        check(FactionRelations.isAllied(Faction.MEDIC, Faction.SCAVENGER), "faction.medicAlliedScavenger");
        check(FactionRelations.isHostile(Faction.MEDIC, Faction.ZOMBIE), "faction.medicHostileZombie");
        check(FactionRelations.isHostile(Faction.MEDIC, Faction.TERRORIST), "faction.medicHostileTerrorist");
        check(FactionRelations.isHostile(Faction.ZOMBIE, Faction.MEDIC), "faction.zombieHostileMedic");
        check(FactionRelations.get(Faction.PLAYER, Faction.TERRORIST) == FactionRelation.HOSTILE, "faction.playerTerroristDefaultHostile");
        check(!FactionRelations.isHostile(Faction.SCAVENGER, Faction.SCAVENGER), "faction.scavengerNotHostileSelf");

        // overrides are symmetric
        FactionRelations.setOverride(Faction.PLAYER, Faction.SCAVENGER, FactionRelation.ALLIED);
        check(FactionRelations.isAllied(Faction.SCAVENGER, Faction.PLAYER), "faction.overrideSymmetric");
        FactionRelations.setOverride(Faction.PLAYER, Faction.SCAVENGER, FactionRelation.NEUTRAL); // restore

        check(FactionRelation.parse("hostile", FactionRelation.NEUTRAL) == FactionRelation.HOSTILE, "faction.parseLower");
        check(FactionRelation.parse("garbage", FactionRelation.NEUTRAL) == FactionRelation.NEUTRAL, "faction.parseFallback");
        check(FactionRelation.parse(null, FactionRelation.ALLIED) == FactionRelation.ALLIED, "faction.parseNullFallback");
        check(FactionRelation.parse("  allied ", FactionRelation.NEUTRAL) == FactionRelation.ALLIED, "faction.parseTrimmed");
    }

    private static void testFactionSymmetry() {
        for (Faction a : Faction.values()) {
            check(FactionRelations.isAllied(a, a), "faction.selfAllied." + a);
            for (Faction b : Faction.values()) {
                check(FactionRelations.get(a, b) == FactionRelations.get(b, a), "faction.symmetric." + a + "." + b);
            }
        }
        FactionRelations.setOverride(Faction.SOLDIER, Faction.SCAVENGER, FactionRelation.HOSTILE);
        check(FactionRelations.isHostile(Faction.SCAVENGER, Faction.SOLDIER), "faction.overrideHostileSymmetric");
        FactionRelations.setOverride(Faction.SOLDIER, Faction.SCAVENGER, FactionRelation.NEUTRAL); // restore default
        check(FactionRelations.get(Faction.SOLDIER, Faction.SCAVENGER) == FactionRelation.NEUTRAL, "faction.overrideRestored");
        check(FactionRelations.get(null, Faction.ZOMBIE) == FactionRelation.NEUTRAL, "faction.nullFactionNeutral");
    }

    // ------------------------------------------------------------------ orders / types

    private static void testOrderParsing() {
        check(HordeOrder.parse("follow") == HordeOrder.FOLLOW, "order.parseFollow");
        check(HordeOrder.parse("HOLD_POSITION") == HordeOrder.HOLD_POSITION, "order.parseHold");
        check(HordeOrder.parse(" cease_fire ") == HordeOrder.CEASE_FIRE, "order.parseCeaseFireTrimmed");
        check(HordeOrder.parse("move_to") == HordeOrder.MOVE_TO, "order.parseMoveTo");
        check(HordeOrder.parse("bogus") == null, "order.parseInvalid");
        check(HordeOrder.parse(null) == null, "order.parseNull");
        check(HordeOrder.values().length == 9, "order.nineOrders");
    }

    private static void testHordeTypeParse() {
        check(HordeType.parse("zombie", null) == HordeType.ZOMBIE, "type.parseLower");
        check(HordeType.parse(" Soldier ", null) == HordeType.SOLDIER, "type.parseTrimmed");
        check(HordeType.parse("bogus", HordeType.SCAVENGER) == HordeType.SCAVENGER, "type.parseFallback");
        check(HordeType.parse(null, HordeType.TERRORIST) == HordeType.TERRORIST, "type.parseNullFallback");
        check(HordeType.values().length == 6, "type.sixTypes");
        check(HordeType.SCAVENGER.faction() == Faction.SCAVENGER, "type.factionMapping");
    }

    // ------------------------------------------------------------------ leader election

    private static void testLeaderElection() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        List<LeaderElection.Candidate> candidates = new ArrayList<>();
        candidates.add(new LeaderElection.Candidate(a, false, 10.0f, 5));
        candidates.add(new LeaderElection.Candidate(b, true, 5.0f, 100)); // current leader wins
        candidates.add(new LeaderElection.Candidate(c, false, 20.0f, 1000));
        check(LeaderElection.pickLeader(candidates).equals(b), "election.currentLeaderKept");

        candidates.clear();
        candidates.add(new LeaderElection.Candidate(a, false, 10.0f, 5));
        candidates.add(new LeaderElection.Candidate(c, false, 20.0f, 1)); // higher health wins
        check(LeaderElection.pickLeader(candidates).equals(c), "election.healthBreaksTie");

        candidates.clear();
        candidates.add(new LeaderElection.Candidate(a, false, 10.0f, 5));
        candidates.add(new LeaderElection.Candidate(b, false, 10.0f, 5)); // exact tie -> uuid order
        UUID expected = a.toString().compareTo(b.toString()) < 0 ? a : b;
        check(LeaderElection.pickLeader(candidates).equals(expected), "election.deterministicTiebreak");

        // result is stable under list reordering
        List<LeaderElection.Candidate> shuffled = new ArrayList<>(candidates);
        java.util.Collections.reverse(shuffled);
        check(LeaderElection.pickLeader(shuffled).equals(expected), "election.orderIndependent");

        check(LeaderElection.pickLeader(List.of()) == null, "election.empty");
        check(LeaderElection.pickLeader(null) == null, "election.nullList");

        // null / id-less candidates are skipped
        List<LeaderElection.Candidate> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(new LeaderElection.Candidate(null, true, 100.0f, 100));
        withNull.add(new LeaderElection.Candidate(a, false, 1.0f, 0));
        check(LeaderElection.pickLeader(withNull).equals(a), "election.nullCandidatesSkipped");
    }

    // ------------------------------------------------------------------ NBT round-trips

    private static void testNbtRoundTrip() {
        UUID hordeId = UUID.randomUUID();
        UUID leaderId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        HordeData data = new HordeData(hordeId, HordeType.SOLDIER);
        HordeMember leader = new HordeMember(leaderId, hordeId);
        leader.setLeader(true);
        leader.setState(com.nox.hzcode.horde.MemberState.ACTIVE);
        leader.setFormationSlot(new FormationSlot(0));
        data.getMembers().put(leaderId, leader);
        data.getMembers().put(followerId, new HordeMember(followerId, hordeId));
        data.setLeaderId(leaderId);
        data.setState(HordeState.COMBAT);
        UUID targetId = UUID.randomUUID();
        data.setTargetId(targetId);
        data.setDestination(new BlockPos(1, 64, -3));
        data.setAnchor(new BlockPos(5, 70, 9));
        data.setOrder(HordeOrder.MOVE_TO);
        data.setFormation(FormationType.COMBAT_SPREAD);
        data.setLootTarget(new BlockPos(7, 65, 7));
        data.setLootAssignee(followerId);
        data.setBreachTarget(new BlockPos(-2, 60, 11));
        data.setLastStrategicUpdate(123);
        data.setLastPathUpdate(45);

        HordeData copy = HordeData.deserializeNBT(data.serializeNBT());
        check(copy.getHordeId().equals(hordeId), "nbt.hordeId");
        check(copy.getType() == HordeType.SOLDIER, "nbt.type");
        check(leaderId.equals(copy.getLeaderId()), "nbt.leaderId");
        check(copy.getMembers().size() == 2, "nbt.memberCount");
        check(copy.getMembers().get(leaderId) != null && copy.getMembers().get(leaderId).isLeader(), "nbt.memberLeaderFlag");
        check(copy.getMembers().get(leaderId).getFormationSlot().getIndex() == 0, "nbt.memberSlot");
        check(copy.getState() == HordeState.COMBAT, "nbt.state");
        check(targetId.equals(copy.getTargetId()), "nbt.targetId");
        check(new BlockPos(1, 64, -3).equals(copy.getDestination()), "nbt.destination");
        check(new BlockPos(5, 70, 9).equals(copy.getAnchor()), "nbt.anchor");
        check(copy.getOrder() == HordeOrder.MOVE_TO, "nbt.order");
        check(copy.getFormation() == FormationType.COMBAT_SPREAD, "nbt.formation");
        check(new BlockPos(7, 65, 7).equals(copy.getLootTarget()), "nbt.lootTarget");
        check(followerId.equals(copy.getLootAssignee()), "nbt.lootAssignee");
        check(new BlockPos(-2, 60, 11).equals(copy.getBreachTarget()), "nbt.breachTarget");
        check(copy.getLastStrategicUpdate() == 123 && copy.getLastPathUpdate() == 45, "nbt.timestamps");

        // entity-side link round-trip
        HordeLink link = new HordeLink();
        link.setHordeId(hordeId);
        link.setLeader(true);
        CompoundTag tag = new CompoundTag();
        link.writeNBT(tag);
        HordeLink link2 = new HordeLink();
        link2.readNBT(tag);
        check(hordeId.equals(link2.getHordeId()) && link2.isLeader(), "nbt.link");

        // corrupt enum values fall back to safe defaults instead of crashing
        CompoundTag bad = new CompoundTag();
        bad.putUUID("HordeId", hordeId);
        bad.putString("Type", "NOPE");
        bad.putString("State", "NOPE");
        bad.putString("Order", "NOPE");
        bad.putString("Formation", "NOPE");
        HordeData d2 = HordeData.deserializeNBT(bad);
        check(d2.getType() == HordeType.ZOMBIE, "nbt.badTypeFallback");
        check(d2.getOrder() == HordeOrder.FOLLOW, "nbt.badOrderKeepsDefault");
        check(d2.getState() == HordeState.IDLE, "nbt.badStateKeepsDefault");
        check(d2.getFormation() == FormationType.FOLLOW_TRAIL, "nbt.badFormationKeepsDefault");
    }

    // ------------------------------------------------------------------ loot transfer

    private static void testLootTransfer() {
        if (!bootstrapMc()) return;
        net.minecraft.world.item.ItemStack stone64 = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64);

        // case 1: normal transfer conserves every item
        ItemStackHandler container = new ItemStackHandler(4);
        container.setStackInSlot(0, stone64.copy());
        container.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 10));
        ItemStackHandler dest = new ItemStackHandler(4);
        int ops = LootTransfer.transferSafely(container, dest, 64);
        check(ops == 2, "loot.twoOperations");
        check(countItems(container) == 0, "loot.containerEmptied");
        check(countItems(dest) == 74, "loot.allItemsConserved");

        // case 2: destination full -> container untouched (no item loss)
        ItemStackHandler full = new ItemStackHandler(2);
        full.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        full.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 64));
        ItemStackHandler src = new ItemStackHandler(2);
        src.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRAVEL, 33));
        int ops2 = LootTransfer.transferSafely(src, full, 64);
        check(ops2 == 0, "loot.fullDestNoOperations");
        check(countItems(src) == 33, "loot.fullDestSourceUntouched");

        // case 3: partial stack merge - 1-slot dest holds 60/64 stone, container has 64 -> 4 move, 60 remain
        ItemStackHandler partial = new ItemStackHandler(1);
        partial.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 60));
        ItemStackHandler src2 = new ItemStackHandler(1);
        src2.setStackInSlot(0, stone64.copy());
        LootTransfer.transferSafely(src2, partial, 64);
        check(countItems(partial) == 64, "loot.partialMergeDestFull64");
        check(countItems(src2) == 60, "loot.partialRemainderPreserved");

        // case 3b: same setup but dest has a second empty slot -> the whole stack moves, nothing lost
        ItemStackHandler partial2 = new ItemStackHandler(2);
        partial2.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 60));
        ItemStackHandler src3 = new ItemStackHandler(1);
        src3.setStackInSlot(0, stone64.copy());
        LootTransfer.transferSafely(src3, partial2, 64);
        check(countItems(partial2) == 124 && countItems(src3) == 0, "loot.wholeStackMovesWithRoom");

        // case 4: maxOperations caps the work per call, nothing is lost
        ItemStackHandler c4 = new ItemStackHandler(4);
        for (int i = 0; i < 4; i++) c4.setStackInSlot(i, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 10));
        ItemStackHandler d4 = new ItemStackHandler(4);
        int ops4 = LootTransfer.transferSafely(c4, d4, 2);
        check(ops4 == 2, "loot.maxOpsCapped");
        check(countItems(c4) == 20 && countItems(d4) == 20, "loot.maxOpsConserved");

        // case 5: empty container -> zero operations
        check(LootTransfer.transferSafely(new ItemStackHandler(2), new ItemStackHandler(2), 64) == 0, "loot.emptySourceNoOps");

        // case 6: mixed item types across slots all move when there is room
        ItemStackHandler c6 = new ItemStackHandler(3);
        c6.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 5));
        c6.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT, 7));
        c6.setStackInSlot(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRAVEL, 9));
        ItemStackHandler d6 = new ItemStackHandler(3);
        LootTransfer.transferSafely(c6, d6, 64);
        check(countItems(c6) == 0 && countItems(d6) == 21, "loot.mixedAllMoved");

        // case 7: maxOperations = 0 -> no work at all
        ItemStackHandler c7 = new ItemStackHandler(1);
        c7.setStackInSlot(0, stone64.copy());
        check(LootTransfer.transferSafely(c7, new ItemStackHandler(1), 0) == 0, "loot.zeroMaxOps");
        check(countItems(c7) == 64, "loot.zeroMaxOpsUntouched");
    }

    /**
     * Brings up just enough of vanilla MC for ItemStack/ItemStackHandler to work: flips the
     * internal Bootstrap "bootstrapped" flag via reflection so the registries self-initialize,
     * without running the full bootStrap() (which requires Forge FML and fails headless).
     */
    private static boolean bootstrapMc() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            java.lang.reflect.Field flag = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
            flag.setAccessible(true);
            flag.setBoolean(null, true);
            net.minecraft.world.item.ItemStack probe =
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 1);
            return !probe.isEmpty();
        } catch (Throwable t) {
            System.out.println("SKIP MC-dependent tests (registry bootstrap unavailable in this JVM): " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ weapons / skins

    private static void testWeaponStocker() {
        check(com.nox.hzcode.equipment.WeaponStocker.isMeleePath("iron_sword"), "melee.ironSword");
        check(com.nox.hzcode.equipment.WeaponStocker.isMeleePath("battleaxe"), "melee.battleaxe");
        check(com.nox.hzcode.equipment.WeaponStocker.isMeleePath("katana"), "melee.katana");
        check(com.nox.hzcode.equipment.WeaponStocker.isMeleePath("machete"), "melee.machete");
        check(!com.nox.hzcode.equipment.WeaponStocker.isMeleePath("ak47"), "melee.ak47NotMelee");
        check(!com.nox.hzcode.equipment.WeaponStocker.isMeleePath("chestplate"), "melee.chestplateNotMelee");
        check(!com.nox.hzcode.equipment.WeaponStocker.isMeleePath("backpack"), "melee.backpackNotMelee");
        check(!com.nox.hzcode.equipment.WeaponStocker.isMeleePath("stone"), "melee.stoneNotMelee");
        check(!com.nox.hzcode.equipment.WeaponStocker.isMeleePath(null), "melee.nullSafe");
        check(com.nox.hzcode.equipment.WeaponStocker.isRifle("ak47"), "melee.ak47IsRifle");
        check(com.nox.hzcode.equipment.WeaponStocker.isRifle("m4a1"), "melee.m4a1IsRifle");
        check(!com.nox.hzcode.equipment.WeaponStocker.isRifle("deagle"), "melee.deagleNotRifle");
        check(!com.nox.hzcode.equipment.WeaponStocker.isRifle(null), "melee.rifleNullSafe");
    }

    private static void testSkinVariant() {
        java.util.UUID id = java.util.UUID.randomUUID();
        int n = 7;
        int idx = com.nox.hzcode.entities.SkinVariant.index(id, n);
        check(idx == com.nox.hzcode.entities.SkinVariant.index(id, n) && idx >= 0 && idx < n, "skin.deterministicInRange");
        check(com.nox.hzcode.entities.SkinVariant.index(id, 0) == 0, "skin.zeroCountSafe");
        check(com.nox.hzcode.entities.SkinVariant.texture("zombie", 3).getPath()
                .equals("textures/entity/skins/zombie/3.png"), "skin.texturePath");
    }

    private static int countItems(ItemStackHandler handler) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) total += handler.getStackInSlot(i).getCount();
        return total;
    }

    private static void testVariants() {
        UUID id = UUID.randomUUID();
        check(Variant.isFemale(id, false) == Variant.isFemale(id, false), "variant.deterministic");
        check(Variant.isFemale(id, true), "variant.forcedFemale");
        int females = 0;
        for (int i = 0; i < 2000; i++) if (Variant.isFemale(UUID.randomUUID(), false)) females++;
        check(females > 800 && females < 1200, "variant.roughlyBalanced");
        check(WeaponStocker.isRifle("ak47"), "rifle.ak47");
        check(WeaponStocker.isRifle("mcx_spear_lt"), "rifle.mcxSpearLt");
        check(!WeaponStocker.isRifle("glock_17"), "rifle.pistolRejected");
        check(!WeaponStocker.isRifle("minigun"), "rifle.minigunRejected");
        check(HordeType.parse("mercenary", null) == HordeType.MERCENARY, "type.mercenaryParse");
        check(WeaponStocker.ALL_GUN_IDS.size() == 70, "guns.total70");
        check(WeaponStocker.GUNS_BY_CLASS.get("rifle").size() == 21, "guns.rifle21");
        check(WeaponStocker.gunsOfClasses(List.of("smg", "shotgun")).size() == 13, "guns.classUnion");
        check(WeaponStocker.gunsOfClasses(List.of("bogus")).isEmpty(), "guns.unknownClassEmpty");
        check(WeaponStocker.AMMO_IDS.size() == 25, "ammo.total25");
        check(WeaponStocker.AMMO_IDS.contains("762x39"), "ammo.762x39");
    }
}
