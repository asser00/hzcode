package com.nox.hzcode.horde;

import com.nox.hzcode.config.ModConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;

/**
 * Timed horde event: waves of zombie hordes spawn near players on a configurable interval.
 * Wave spawning is a thin layer over {@link HordeSpawner}; all group logic lives in the horde engine.
 */
public final class HordeManager {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("hzcode");
    private static int waveNumber;
    private static int ticksUntilNextWave;
    private static boolean hordeActive;
    private static final java.util.Random RANDOM = new java.util.Random();

    private HordeManager() {}

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModConfig.COMMON.enableHorde.get() || !hordeActive) return;
        ticksUntilNextWave--;
        if (ticksUntilNextWave <= 0) {
            ticksUntilNextWave = ModConfig.COMMON.waveInterval.get() * 20;
            spawnWave(event.getServer());
        }
    }

    private static void spawnWave(MinecraftServer server) {
        waveNumber++;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
            List<ServerPlayer> players = level.players();
            if (players.isEmpty()) continue;
            ServerPlayer player = players.get(RANDOM.nextInt(players.size()));
            if (countHordeMembers(level) >= ModConfig.COMMON.spawnCap.get()) continue;
            int growth = Math.min(ModConfig.COMMON.waveSizeGrowthCap.get(), (waveNumber / 2) * ModConfig.COMMON.waveSizeGrowthPerWave.get());
            int size = ModConfig.COMMON.zombieHordeSize.get() + growth;
            BlockPos center = player.blockPosition().offset(RANDOM.nextInt(48) - 24, 0, RANDOM.nextInt(48) - 24);
            BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
            HordeController controller = HordeSpawner.spawnHorde(level, HordeType.ZOMBIE, surface, size);
            if (controller != null) {
                LOGGER.info("wave {} spawned {} zombies at {} near {}", waveNumber, size, surface, player.getGameProfile().getName());
                for (ServerPlayer p : players) {
                    p.sendSystemMessage(Component.literal("Horde wave " + waveNumber + " (" + controller.getMemberCount() + " zombies)").withStyle(net.minecraft.ChatFormatting.DARK_RED));
                }
            }
        }
    }

    private static int countHordeMembers(ServerLevel level) {
        int count = 0;
        for (HordeController controller : HordeRegistry.get(level).getHordes().values()) {
            count += controller.getMemberCount();
        }
        return count;
    }

    public static void startHorde() {
        hordeActive = true;
        ticksUntilNextWave = Math.min(ticksUntilNextWave, 100);
    }

    public static void stopHorde() {
        hordeActive = false;
    }

    public static boolean isActive() {
        return hordeActive;
    }

    public static int getWaveNumber() {
        return waveNumber;
    }

    public static int getTicksUntilNextWave() {
        return ticksUntilNextWave;
    }
}
