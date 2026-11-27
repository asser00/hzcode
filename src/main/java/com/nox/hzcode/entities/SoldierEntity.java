package com.nox.hzcode.entities;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ai.goal.FactionTargetGoal;
import com.nox.hzcode.entities.ai.goal.HordeFollowerGoal;
import com.nox.hzcode.entities.ai.goal.SoldierTaczCombatGoal;
import com.nox.hzcode.horde.HordeType;
import com.nox.hzcode.equipment.WeaponStocker;
import com.nox.hzcode.entities.ai.goal.TamedFollowGoal;
import com.nox.hzcode.entities.ai.goal.TamedOwnerDefenseGoal;
import com.nox.hzcode.tacz.TacZAdapter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/**
 * Soldier: armed horde member. Receives a real TACZ firearm plus ammunition through
 * {@link TacZAdapter#equipSoldier} and fights with {@link SoldierTaczCombatGoal}.
 */
public class SoldierEntity extends AbstractHordeMob {
    private static final EntityDataAccessor<Boolean> MERCENARY =
            SynchedEntityData.defineId(SoldierEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean taczEquipped;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MERCENARY, false);
    }

    /** Mercenaries are trade-only soldiers: they can never be tamed. Synched so the client renderer can pick mercenary skins. */
    public boolean isMercenary() { return this.entityData.get(MERCENARY); }

    public void setMercenary(boolean mercenary) { this.entityData.set(MERCENARY, mercenary); }

    public SoldierEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new SoldierTaczCombatGoal(this));
        this.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.MeleeAttackGoal(this, 1.0, false)); // fallback when unarmed
        this.goalSelector.addGoal(4, new TamedFollowGoal(this));
        this.goalSelector.addGoal(5, new HordeFollowerGoal(this));
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
        if (health != null) health.setBaseValue(ModConfig.COMMON.soldierHealth.get());
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(ModConfig.COMMON.soldierDamage.get());
        return result;
    }

    /** Provisions the soldier with a TACZ gun and ammo. Called by the horde spawner and on load. */
    public void provisionEquipment() {
        if (taczEquipped || !(this.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        if (!TacZAdapter.isTaczAvailable()) return;
        ModConfig.Common cfg = ModConfig.COMMON;
        net.minecraft.world.item.ItemStack gun;
        if (cfg.soldiersRandomWeapons.get()) {
            gun = WeaponStocker.randomTaczGun(this.level().random, true); // soldiers get random rifles
            if (gun.isEmpty()) gun = TacZAdapter.createGunStack(cfg.soldierGun.get());
        } else {
            gun = TacZAdapter.createGunStack(cfg.soldierGun.get());
        }
        if (gun.isEmpty()) {
            // never leave a soldier unarmed: fall back to a random melee weapon
            net.minecraft.world.item.ItemStack melee = WeaponStocker.randomMelee(this.level().random);
            if (!melee.isEmpty()) {
                setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, melee);
                taczEquipped = true;
            }
            return;
        }
        net.minecraft.world.item.ItemStack ammo = WeaponStocker.matchingTaczAmmo(gun, Math.max(1, cfg.soldierAmmoStacks.get()));
        if (ammo.isEmpty()) ammo = TacZAdapter.createAmmoStack(cfg.soldierAmmo.get(), Math.max(1, cfg.soldierAmmoStacks.get()));
        TacZAdapter.equipSoldier(this, gun, ammo, cfg.soldierLoadedAmmo.get());
        taczEquipped = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && !taczEquipped && this.tickCount % 20 == 0) {
            provisionEquipment();
        }
    }

    @Override
    public HordeType getHordeType() { return HordeType.SOLDIER; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TacZEquipped", taczEquipped);
        tag.putBoolean("Mercenary", isMercenary());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        taczEquipped = tag.getBoolean("TacZEquipped");
        setMercenary(tag.getBoolean("Mercenary"));
    }
}
