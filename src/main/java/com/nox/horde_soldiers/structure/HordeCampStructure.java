package com.nox.horde_soldiers.structure;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * "Horde camp": a small survivor/raider camp generated on the overworld surface - plank/cobble
 * platform, corner fence posts, a torch, a campfire and a loot chest (blocks are placed by
 * HordeCampPiece). Placement (spacing/separation/salt) and biome filter live in the data pack:
 * data/hzcode/worldgen/structure/horde_camp.json + structure_set/horde_camp.json.
 */
public class HordeCampStructure extends Structure {
    public static final Codec<HordeCampStructure> CODEC = Structure.simpleCodec(HordeCampStructure::new);

    public HordeCampStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HORDE_CAMP.get();
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMinBlockX() + context.random().nextInt(16);
        int z = chunkPos.getMinBlockZ() + context.random().nextInt(16);
        int y = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        if (y <= context.heightAccessor().getMinBuildHeight() + 2) return Optional.empty();
        final BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, piecesBuilder -> {
            BoundingBox box = BoundingBox.fromCorners(
                    new BlockPos(x - 4, y - 2, z - 4), new BlockPos(x + 4, y + 5, z + 4));
            piecesBuilder.addPiece(new HordeCampPiece(ModStructures.pieceType(), 0, box, origin));
        }));
    }
}
