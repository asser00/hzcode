package com.nox.hzcode.entities.ai.goal;

import com.nox.hzcode.config.ModConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Central breach rules shared by the horde controller and the breach goal.
 * Enforces: mobGriefing gamerule, block whitelist, blacklist, and tool requirements.
 */
public final class ZombieBreachRules {
    private ZombieBreachRules() {}

    /** Hard rule: nothing is ever breached while mobGriefing is off (when configured to respect it). */
    public static boolean griefingAllowed(Level level) {
        if (!ModConfig.COMMON.respectMobGriefing.get()) return true;
        return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
    }

    public static boolean isProtected(Block block) {
        Set<Block> protectedBlocks = resolveSet(ModConfig.COMMON.protectedBlocks.get(), PROTECTED_DEFAULTS);
        return protectedBlocks.contains(block);
    }

    public static boolean isBreachable(Level level, BlockPos pos, BlockState state) {
        if (!ModConfig.COMMON.breachEnabled.get()) return false;
        if (!griefingAllowed(level)) return false;
        Block block = state.getBlock();
        if (isProtected(block)) return false;
        if (isWhitelisted(block)) return true;
        // default wooden things
        return state.is(BlockTags.DOORS) || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOODEN_FENCES) || state.is(BlockTags.WOODEN_DOORS);
    }

    /** Stone-tier blocks need a pickaxe in hand when configured. */
    public static boolean requiresTool(BlockState state) {
        if (!ModConfig.COMMON.breachRequiresToolForStone.get()) return false;
        // stone-tier: destroy speed >= 1.5 and requires correct tool for drops (cobblestone, stone, ores...)
        return state.requiresCorrectToolForDrops() && state.getDestroySpeed(null, BlockPos.ZERO) >= 1.5f;
    }

    public static boolean isWhitelisted(Block block) {
        Set<Block> whitelist = resolveSet(ModConfig.COMMON.breachableBlocks.get(), Set.of());
        return whitelist.contains(block);
    }

    private static final Set<Block> PROTECTED_DEFAULTS = new HashSet<>();

    static {
        String[] defaults = {
            "minecraft:bedrock", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:barrier",
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:end_portal_frame",
            "minecraft:nether_portal", "minecraft:end_gateway", "minecraft:spawner", "minecraft:trial_spawner",
            "minecraft:vault", "minecraft:reinforced_deepslate"
        };
        for (String id : defaults) {
            Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
            if (b != null) PROTECTED_DEFAULTS.add(b);
        }
    }

    private static Set<Block> resolveSet(java.util.List<? extends String> ids, Set<Block> fallback) {
        Set<Block> out = new HashSet<>(fallback);
        if (ids != null) {
            for (String id : ids) {
                Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
                if (b != null) out.add(b);
            }
        }
        return out;
    }
}
