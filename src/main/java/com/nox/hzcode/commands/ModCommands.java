package com.nox.hzcode.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.nox.hzcode.horde.HordeController;
import com.nox.hzcode.horde.HordeManager;
import com.nox.hzcode.horde.HordeOrder;
import com.nox.hzcode.horde.HordeRegistry;
import com.nox.hzcode.horde.HordeSpawner;
import com.nox.hzcode.horde.HordeType;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Player commands. Orders are applied to horde controllers, never per-entity. */
public final class ModCommands {
    private ModCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("horde")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            HordeManager.startHorde();
                            ctx.getSource().sendSuccess(() -> Component.literal("Horde event started"), true);
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            HordeManager.stopHorde();
                            ctx.getSource().sendSuccess(() -> Component.literal("Horde event stopped"), true);
                            return 1;
                        }))
                .then(Commands.literal("status")
                        .executes(ModCommands::cmdStatus))
                .then(Commands.literal("list")
                        .executes(ModCommands::cmdList))
                .then(Commands.literal("wave")
                        .executes(ctx -> spawnWaves(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> spawnWaves(ctx, IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(ctx -> spawnHorde(ctx, StringArgumentType.getString(ctx, "type"), 0))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> spawnHorde(ctx, StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(Commands.literal("order")
                        .then(Commands.argument("order", StringArgumentType.word())
                                .executes(ctx -> applyOrder(ctx, StringArgumentType.getString(ctx, "order"), 64.0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                        .executes(ctx -> applyOrder(ctx, StringArgumentType.getString(ctx, "order"), IntegerArgumentType.getInteger(ctx, "radius"))))))
                .then(Commands.literal("dismiss")
                        .executes(ctx -> dismiss(ctx, 64.0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
                                .executes(ctx -> dismiss(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        int hordes = 0;
        int members = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            for (HordeController c : HordeRegistry.get(level).getHordes().values()) {
                hordes++;
                members += c.getMemberCount();
            }
        }
        final int hordesF = hordes;
        final int membersF = members;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Active: " + HordeManager.isActive() + ", wave: " + HordeManager.getWaveNumber()
                        + ", hordes: " + hordesF + ", members: " + membersF), false);
        return 1;
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        java.util.List<HordeController> hordes = new java.util.ArrayList<>(HordeRegistry.get(level).getHordes().values());
        if (hordes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No active hordes in " + level.dimension().location()), false);
            return 1;
        }
        for (HordeController controller : hordes) {
            var leader = level.getEntity(controller.getData().getLeaderId());
            String leaderPos = leader == null ? "unloaded" : leader.blockPosition().toShortString();
            String type = controller.getType().name().toLowerCase(java.util.Locale.ROOT);
            String id = controller.getHordeId().toString().substring(0, 8);
            source.sendSuccess(() -> Component.literal(
                    "[" + type + "#" + id + "] members=" + controller.getMemberCount()
                            + " state=" + controller.getData().getState()
                            + " order=" + controller.getData().getOrder()
                            + " formation=" + controller.getData().getFormation()
                            + " leader=" + leaderPos), false);
        }
        return hordes.size();
    }

    private static int spawnWaves(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Vec3 pos = source.getPosition();
            BlockPos center = BlockPos.containing(pos.x + (level.random.nextInt(32) - 16), pos.y, pos.z + (level.random.nextInt(32) - 16));
            BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
            HordeController controller = HordeSpawner.spawnHorde(level, HordeType.ZOMBIE, surface, 0);
            if (controller != null) spawned++;
        }
        final int n = spawned;
        source.sendSuccess(() -> Component.literal("Spawned " + n + " wave(s)"), true);
        return n;
    }

    private static int spawnHorde(CommandContext<CommandSourceStack> ctx, String typeName, int count) {
        CommandSourceStack source = ctx.getSource();
        HordeType type = HordeType.parse(typeName, null);
        if (type == null) {
            source.sendFailure(Component.literal("Unknown horde type. Valid: soldier, scavenger, zombie, terrorist"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        BlockPos center = BlockPos.containing(pos.x, pos.y, pos.z);
        HordeController controller = HordeSpawner.spawnHorde(level, type, center, count);
        if (controller == null) {
            source.sendFailure(Component.literal("Failed to spawn horde"));
            return 0;
        }
        final int size = controller.getMemberCount();
        final String name = type.name().toLowerCase(java.util.Locale.ROOT);
        source.sendSuccess(() -> Component.literal("Spawned " + size + " " + name + " horde"), true);
        return size;
    }

    private static int applyOrder(CommandContext<CommandSourceStack> ctx, String orderName, double radius) {
        HordeOrder order = HordeOrder.parse(orderName);
        CommandSourceStack source = ctx.getSource();
        if (order == null) {
            source.sendFailure(Component.literal("Unknown order. Valid: FOLLOW, HOLD_POSITION, DEFEND_POSITION, PROTECT_PLAYER, ATTACK_TARGET, RETREAT, CEASE_FIRE, RECALL, MOVE_TO"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        int affected = 0;
        for (HordeController controller : HordeRegistry.get(level).getHordes().values()) {
            var leader = level.getEntity(controller.getData().getLeaderId());
            if (leader == null) continue;
            if (leader.position().distanceToSqr(pos) > radius * radius) continue;
            controller.setOrder(order, BlockPos.containing(pos));
            affected++;
        }
        if (affected == 0) {
            source.sendFailure(Component.literal("No hordes in range"));
            return 0;
        }
        final int n = affected;
        final String o = order.name();
        source.sendSuccess(() -> Component.literal("Order " + o + " applied to " + n + " horde(s)"), true);
        return n;
    }

    private static int dismiss(CommandContext<CommandSourceStack> ctx, double radius) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        int dismissed = 0;
        for (HordeController controller : HordeRegistry.get(level).getHordes().values()) {
            var leader = level.getEntity(controller.getData().getLeaderId());
            if (leader == null) continue;
            if (leader.position().distanceToSqr(pos) > radius * radius) continue;
            for (UUID memberId : controller.getData().memberIds()) {
                var entity = level.getEntity(memberId);
                if (entity != null) entity.discard();
            }
            dismissed++;
        }
        final int n = dismissed;
        source.sendSuccess(() -> Component.literal("Dismissed " + n + " horde(s)"), true);
        return n;
    }
}
