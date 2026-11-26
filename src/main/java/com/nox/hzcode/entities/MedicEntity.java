package com.nox.hzcode.entities;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ai.goal.HealAlliesGoal;
import com.nox.hzcode.entities.ai.goal.HordeFollowerGoal;
import com.nox.hzcode.entities.ai.goal.TamedFollowGoal;
import com.nox.hzcode.horde.HordeType;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Medic (healer): a horde member that never fights - it follows the horde/owner and heals all
 * friendly entities around it (HealAlliesGoal). Wears the medic skin pool. Tameable like soldiers.
 */
public class MedicEntity extends AbstractHordeMob {
    public MedicEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new HealAlliesGoal(this));
        this.goalSelector.addGoal(4, new TamedFollowGoal(this));
        this.goalSelector.addGoal(5, new HordeFollowerGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData groupData, net.minecraft.nbt.CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(ModConfig.COMMON.medicHealth.get());
        return result;
    }

    @Override
    public HordeType getHordeType() { return HordeType.MEDIC; }
}
