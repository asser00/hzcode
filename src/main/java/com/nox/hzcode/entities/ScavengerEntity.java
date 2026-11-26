package com.nox.hzcode.entities;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ai.goal.FactionTargetGoal;
import com.nox.hzcode.entities.ai.goal.HordeFollowerGoal;
import com.nox.hzcode.entities.ai.goal.LootContainerGoal;
import com.nox.hzcode.equipment.WeaponStocker;
import com.nox.hzcode.entities.ai.goal.ScavengerDefenseGoal;
import com.nox.hzcode.entities.ai.goal.ScavengerWorkGoal;
import com.nox.hzcode.entities.ai.goal.TamedFollowGoal;
import com.nox.hzcode.entities.ai.goal.TamedOwnerDefenseGoal;
import com.nox.hzcode.horde.HordeType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Scavenger: independent survivor. Loot discovery is leader-driven (controller scans throttled);
 * the assigned member executes the container transfer via {@link LootContainerGoal}. Inventory
 * persists in NBT and transfers never lose items.
 */
public class ScavengerEntity extends AbstractHordeMob {
    private boolean meleeAssigned;

    public ScavengerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(3, new ScavengerDefenseGoal(this));
        this.goalSelector.addGoal(4, new LootContainerGoal(this));
        this.goalSelector.addGoal(4, new TamedFollowGoal(this));
        this.goalSelector.addGoal(5, new HordeFollowerGoal(this));
        this.goalSelector.addGoal(6, new ScavengerWorkGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal<>(this, true));
        this.targetSelector.addGoal(2, new TamedOwnerDefenseGoal(this));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData groupData, CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(ModConfig.COMMON.scavengerHealth.get());
        assignMeleeWeapon();
        return result;
    }

    /** Gives the scavenger a random melee weapon (once). Called at spawn and lazily on load. */
    private void assignMeleeWeapon() {
        if (meleeAssigned) return;
        if (!getMainHandItem().isEmpty()) { meleeAssigned = true; return; }
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        if (!ModConfig.COMMON.scavengersRandomMelee.get()) return;
        net.minecraft.world.item.ItemStack weapon = WeaponStocker.randomMelee(this.level().random);
        if (!weapon.isEmpty()) {
            setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
            meleeAssigned = true;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !meleeAssigned && this.tickCount % 20 == 0) {
            assignMeleeWeapon();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("MeleeAssigned", meleeAssigned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        meleeAssigned = tag.getBoolean("MeleeAssigned");
    }

    @Override
    public HordeType getHordeType() { return HordeType.SCAVENGER; }
}
