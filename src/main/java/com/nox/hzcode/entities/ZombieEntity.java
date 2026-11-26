package com.nox.hzcode.entities;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ai.goal.FactionTargetGoal;
import com.nox.hzcode.entities.ai.goal.HordeFollowerGoal;
import com.nox.hzcode.entities.ai.goal.ZombieBreachGoal;
import com.nox.hzcode.horde.HordeLink;
import com.nox.hzcode.horde.HordeType;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;

/** Horde zombie: aggressive follower/leader. Strategic decisions come from the horde controller. */
public class ZombieEntity extends Zombie implements IHordeEntity {
    private final HordeLink hordeLink = new HordeLink();
    private boolean breacher;

    public ZombieEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(3, new ZombieBreachGoal(this));
        this.goalSelector.addGoal(5, new HordeFollowerGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal<>(this, true));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData groupData, CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        setCanBreakDoors(false); // vanilla door breaking bypasses ZombieBreachRules; breaching only via ZombieBreachGoal
        applyConfig();
        return result;
    }

    private void applyConfig() {
        if (this.level() instanceof ServerLevel) {
            var health = this.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(ModConfig.COMMON.zombieHealth.get());
            var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(ModConfig.COMMON.zombieDamage.get());
        }
    }

    @Override
    protected boolean isSunSensitive() {
        return ModConfig.COMMON.zombiesBurnInSunlight.get();
    }

    public void setBreacher(boolean breacher) { this.breacher = breacher; }
    public boolean isBreacher() { return breacher; }

    @Override
    public UUID getHordeId() { return hordeLink.getHordeId(); }
    @Override
    public void setHordeId(UUID hordeId) { hordeLink.setHordeId(hordeId); }
    @Override
    public boolean isHordeLeader() { return hordeLink.isLeader(); }
    @Override
    public void setHordeLeader(boolean leader) { hordeLink.setLeader(leader); }
    @Override
    public HordeType getHordeType() { return HordeType.ZOMBIE; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        hordeLink.writeNBT(tag);
        tag.putBoolean("Breacher", breacher);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hordeLink.readNBT(tag);
        breacher = tag.getBoolean("Breacher");
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return getHordeId() == null && super.removeWhenFarAway(distanceToClosestPlayer);
    }
}
