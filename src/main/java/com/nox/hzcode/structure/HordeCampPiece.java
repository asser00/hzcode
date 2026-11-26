package com.nox.hzcode.structure;

import com.nox.hzcode.HordeSoldiersMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.ChunkPos;

/** Builds the actual blocks of a horde camp when its chunk is post-processed. */
public class HordeCampPiece extends StructurePiece {
    private final BlockPos origin;

    public HordeCampPiece(StructurePieceType type, int genDepth, BoundingBox box, BlockPos origin) {
        super(type, genDepth, box);
        this.origin = origin;
        // the 3-arg super ctor leaves mirror/rotation null; placeBlock() NPEs on them otherwise.
        // setOrientation initializes orientation + mirror(NONE) + rotation(NONE).
        this.setOrientation(net.minecraft.core.Direction.NORTH);
    }

    public HordeCampPiece(StructurePieceType type, CompoundTag tag) {
        super(type, tag);
        this.origin = new BlockPos(tag.getInt("OX"), tag.getInt("OY"), tag.getInt("OZ"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("OX", this.origin.getX());
        tag.putInt("OY", this.origin.getY());
        tag.putInt("OZ", this.origin.getZ());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator chunkGenerator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pos) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        // 9x9 platform one block below the surface point
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockState floor = (dx * dx + dz * dz > 12) ? cobble : planks;
                placeBlock(level, floor, x + dx, y - 1, z + dz, this.boundingBox);
            }
        }
        // clear the air above the platform
        generateAirBox(level, this.boundingBox, x - 4, y, z - 4, x + 4, y + 3, z + 4);
        // corner fence posts + center post with torch
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        placeBlock(level, fence, x - 4, y, z - 4, this.boundingBox);
        placeBlock(level, fence, x + 4, y, z - 4, this.boundingBox);
        placeBlock(level, fence, x - 4, y, z + 4, this.boundingBox);
        placeBlock(level, fence, x + 4, y, z + 4, this.boundingBox);
        placeBlock(level, fence, x, y, z, this.boundingBox);
        placeBlock(level, Blocks.TORCH.defaultBlockState(), x, y + 1, z, this.boundingBox);
        // campfire next to the center
        placeBlock(level, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, Boolean.TRUE), x - 2, y, z, this.boundingBox);
        // loot chest
        BlockPos chestPos = new BlockPos(x + 2, y, z);
        placeBlock(level, Blocks.CHEST.defaultBlockState(), chestPos.getX(), chestPos.getY(), chestPos.getZ(), this.boundingBox);
        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (blockEntity instanceof RandomizableContainerBlockEntity container) {
            container.setLootTable(new ResourceLocation(HordeSoldiersMod.MOD_ID, "chests/horde_camp"), random.nextLong());
        }
        // a little debris
        if (random.nextBoolean()) {
            placeBlock(level, Blocks.HAY_BLOCK.defaultBlockState(), x + 3, y, z - 2, this.boundingBox);
        }
        if (random.nextBoolean()) {
            placeBlock(level, Blocks.BARREL.defaultBlockState(), x - 2, y, z + 2, this.boundingBox);
            BlockEntity barrel = level.getBlockEntity(new BlockPos(x - 2, y, z + 2));
            if (barrel instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(new ResourceLocation(HordeSoldiersMod.MOD_ID, "chests/horde_camp"), random.nextLong());
            }
        }
    }
}
