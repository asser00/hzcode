package com.nox.hzcode.entities;

import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.ai.goal.FactionTargetGoal;
import com.nox.hzcode.entities.ai.goal.HordeFollowerGoal;
import com.nox.hzcode.entities.ai.goal.SoldierTaczCombatGoal;
import com.nox.hzcode.horde.HordeType;
import com.nox.hzcode.equipment.WeaponStocker;
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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/** Terrorist: hostile raider faction with its own relationships; optionally armed with TACZ guns. */
public class TerroristEntity extends AbstractHordeMob {
    private boolean taczEquipped;

    public TerroristEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new SoldierTaczCombatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new HordeFollowerGoal(this));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal<>(this, true));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, net.minecraft.world.entity.SpawnGroupData groupData, CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
        var health = this.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(ModConfig.COMMON.terroristHealth.get());
        var damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(ModConfig.COMMON.terroristDamage.get());
        return result;
    }

    public void provisionEquipment() {
        if (taczEquipped || !(this.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        if (!TacZAdapter.isTaczAvailable()) return;
        ModConfig.Common cfg = ModConfig.COMMON;
        net.minecraft.world.item.ItemStack gun;
        if (cfg.terroristsRandomWeapons.get()) {
            // distinct loadout: terrorists draw from their own configured weapon classes (smg/shotgun/pistol)
            gun = WeaponStocker.randomTaczGunFrom(this.level().random, WeaponStocker.gunsOfClasses(cfg.terroristWeaponClasses.get()));
            if (gun.isEmpty()) gun = TacZAdapter.createGunStack(cfg.soldierGun.get());
        } else {
            gun = TacZAdapter.createGunStack(cfg.soldierGun.get());
        }
        if (gun.isEmpty()) {
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
        if (!this.level().isClientSide && !taczEquipped && ModConfig.COMMON.terroristsUseGuns.get() && this.tickCount % 20 == 0) {
            provisionEquipment();
        }
    }

    @Override
    public HordeType getHordeType() { return HordeType.TERRORIST; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TacZEquipped", taczEquipped);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        taczEquipped = tag.getBoolean("TacZEquipped");
    }
}
