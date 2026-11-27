package com.nox.hzcode.faction;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ITameableHorde;
import com.nox.hzcode.entities.MedicEntity;
import com.nox.hzcode.entities.ScavengerEntity;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.TerroristEntity;
import com.nox.hzcode.entities.ZombieEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/**
 * Centralized, Minecraft-aware faction system. All combat and targeting decisions
 * must go through this class; nothing may hardcode hostility elsewhere.
 */
public final class FactionManager {
    private FactionManager() {}

    /** Applies the configurable relation overrides from the config file. Call on config load/reload. */
    public static void applyConfig() {
        ModConfig.Common c = ModConfig.COMMON;
        if (c == null) return;
        setOverride(Faction.PLAYER, Faction.SCAVENGER, FactionRelation.parse(c.relationPlayerScavenger.get(), FactionRelation.NEUTRAL));
        setOverride(Faction.PLAYER, Faction.TERRORIST, FactionRelation.parse(c.relationPlayerTerrorist.get(), FactionRelation.HOSTILE));
        setOverride(Faction.SOLDIER, Faction.SCAVENGER, FactionRelation.parse(c.relationSoldierScavenger.get(), FactionRelation.NEUTRAL));
        setOverride(Faction.SOLDIER, Faction.TERRORIST, FactionRelation.parse(c.relationSoldierTerrorist.get(), c.terroristsHostileToSoldiers.get() ? FactionRelation.HOSTILE : FactionRelation.NEUTRAL));
        setOverride(Faction.SCAVENGER, Faction.TERRORIST, FactionRelation.parse(c.relationScavengerTerrorist.get(), FactionRelation.NEUTRAL));
        if (!c.soldiersFriendlyToPlayer.get()) {
            setOverride(Faction.PLAYER, Faction.SOLDIER, FactionRelation.NEUTRAL);
        }
    }

    private static void setOverride(Faction a, Faction b, FactionRelation r) {
        FactionRelations.setOverride(a, b, r);
    }

    /** Returns the faction of an entity, or null for entities outside the faction system. */
    public static Faction getFactionOf(Entity entity) {
        if (entity instanceof Player) return Faction.PLAYER;
        if (entity instanceof SoldierEntity s && s.isMercenary()) return Faction.MERCENARY;
        if (entity instanceof SoldierEntity) return Faction.SOLDIER;
        if (entity instanceof ScavengerEntity) return Faction.SCAVENGER;
        if (entity instanceof TerroristEntity) return Faction.TERRORIST;
        if (entity instanceof MedicEntity) return Faction.MEDIC;
        if (entity instanceof ZombieEntity || entity instanceof Zombie) return Faction.ZOMBIE;
        return null;
    }

    public static FactionRelation getRelation(Entity a, Entity b) {
        FactionRelation tamed = tamedRelation(a, b);
        if (tamed != null) return tamed;
        return FactionRelations.get(getFactionOf(a), getFactionOf(b));
    }

    /** Tamed mobs are always allied with their owner and with the owner's other tamed mobs. */
    private static FactionRelation tamedRelation(Entity a, Entity b) {
        if (a instanceof ITameableHorde ta && ta.isTamed()) {
            UUID owner = ta.getOwnerUUID();
            if (owner != null) {
                if (owner.equals(b.getUUID())) return FactionRelation.ALLIED;
                if (b instanceof ITameableHorde tb && tb.isTamed() && owner.equals(tb.getOwnerUUID())) return FactionRelation.ALLIED;
            }
        }
        if (b instanceof ITameableHorde tb && tb.isTamed()) {
            UUID owner = tb.getOwnerUUID();
            if (owner != null && owner.equals(a.getUUID())) return FactionRelation.ALLIED;
        }
        return null;
    }

    public static boolean isHostileTo(Entity a, Entity b) {
        return FactionRelations.isHostile(getFactionOf(a), getFactionOf(b));
    }

    public static boolean isHostileToHorde(Faction hordeFaction, LivingEntity candidate) {
        return FactionRelations.isHostile(hordeFaction, getFactionOf(candidate));
    }

    public static boolean isAlliedWith(Entity a, Entity b) {
        return FactionRelations.isAllied(getFactionOf(a), getFactionOf(b));
    }
}
