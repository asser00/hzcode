package com.nox.horde_soldiers.structure;

import com.nox.horde_soldiers.HordeSoldiersMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registers the mod's structure type(s) and structure piece type(s). Data-driven JSON lives in data/hzcode/worldgen/. */
public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, HordeSoldiersMod.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, HordeSoldiersMod.MOD_ID);

    public static final RegistryObject<StructureType<?>> HORDE_CAMP =
            STRUCTURE_TYPES.register("horde_camp", () -> (StructureType<HordeCampStructure>) () -> HordeCampStructure.CODEC);

    public static final RegistryObject<StructurePieceType> HORDE_CAMP_PIECE =
            STRUCTURE_PIECES.register("horde_camp", () -> (context, tag) -> new HordeCampPiece(pieceType(), tag));

    private ModStructures() {}

    public static StructurePieceType pieceType() { return HORDE_CAMP_PIECE.get(); }

    public static void register(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECES.register(modBus);
    }
}
