package com.nox.hzcode.event;

import com.nox.hzcode.entities.AbstractHordeMob;
import com.nox.hzcode.config.ModConfig;
import com.nox.hzcode.entities.IHordeEntity;
import com.nox.hzcode.horde.HordeType;
import com.nox.hzcode.entities.ModEntities;
import com.nox.hzcode.entities.ScavengerEntity;
import com.nox.hzcode.entities.SoldierEntity;
import com.nox.hzcode.entities.TerroristEntity;
import com.nox.hzcode.faction.FactionManager;
import com.nox.hzcode.horde.HordeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModEvents {
    private static boolean configApplied;

    private ModEvents() {}

    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        ModEntities.onEntityAttributeCreation(event);
    }

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SoldierEntity.class);
        event.register(ScavengerEntity.class);
        event.register(TerroristEntity.class);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.nox.hzcode.commands.ModCommands.onRegisterCommands(event);
    }

    /** Re-attach loaded entities to their persisted horde; never force-loads chunks. */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && event.getEntity() instanceof LivingEntity living) {
            HordeRegistry.get(serverLevel).attach(living);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof IHordeEntity && event.getEntity().level() instanceof ServerLevel serverLevel) {
            HordeRegistry.get(serverLevel).detach(event.getEntity().getUUID());
        }
    }

    /** Re-apply faction relation overrides whenever the COMMON config (re)loads. */
    public static void onConfigEvent(net.minecraftforge.fml.event.config.ModConfigEvent event) {
        if (event.getConfig().getSpec() == com.nox.hzcode.config.ModConfig.COMMON_SPEC) {
            FactionManager.applyConfig();
        }
    }

    public static void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null) com.nox.hzcode.horde.ScavengerWorkManager.loadFromDisk(overworld);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        com.nox.hzcode.horde.ScavengerWorkManager.tick(event.getServer());
        if (!configApplied) {
            configApplied = true;
            FactionManager.applyConfig();
        }
        HordeRegistry.tickAll(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        HordeRegistry.onServerStopping(event.getServer());
    }

    /**
     * Soldier/scavenger/medic interactions (server side):
     * - Weapon swap (soldiers only): right-click a soldier with a gun or melee weapon to swap its
     *   main-hand weapon with yours; right-click with TACZ ammo to load it into the soldier's inventory.
     * - Barter trading: right-click with a "trade good" to receive a random reward (works tamed or wild).
     * - Wolf-like taming: hold the taming treat and right-click (chance per use); sneak + empty hand releases.
     * - Otherwise an action-bar hint tells the player what item tames the mob.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getTarget() instanceof AbstractHordeMob mob)) return;
        HordeType type = mob.getHordeType();
        if (type != HordeType.SOLDIER && type != HordeType.SCAVENGER && type != HordeType.MEDIC) return;
        ModConfig.Common cfg = ModConfig.COMMON;
        Player player = event.getEntity();
        net.minecraft.world.item.ItemStack held = event.getItemStack();

        // --- weapon swap: soldiers only ---
        if (mob instanceof com.nox.hzcode.entities.SoldierEntity soldier && !held.isEmpty()) {
            boolean gun = com.nox.hzcode.tacz.TacZAdapter.isGunStack(held);
            boolean melee = held.getItem() instanceof net.minecraft.world.item.SwordItem
                    || held.getItem() instanceof net.minecraft.world.item.AxeItem
                    || held.getItem() instanceof net.minecraft.world.item.TridentItem;
            if (gun || melee) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                net.minecraft.world.item.ItemStack old = soldier.getMainHandItem().copy();
                if (!player.getAbilities().instabuild) {
                    soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held.copy());
                    held.setCount(0);
                    if (!old.isEmpty()) player.getInventory().add(old);
                } else {
                    soldier.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, held.copy());
                }
                player.displayClientMessage(Component.literal("Soldier now uses " + held.getHoverName().getString()), true);
                return;
            }
            if (com.nox.hzcode.tacz.TacZAdapter.isAmmoStack(held)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                var cap = soldier.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null);
                if (cap.isPresent()) {
                    net.minecraft.world.item.ItemStack remainder = held.copy();
                    var inv = cap.orElse(null);
                    for (int i = 0; i < inv.getSlots() && !remainder.isEmpty(); i++) {
                        remainder = inv.insertItem(i, remainder, false);
                    }
                    held.setCount(remainder.getCount());
                    player.displayClientMessage(Component.literal("Ammo stored in the soldier's inventory."), true);
                }
                return;
            }
        }

        // --- barter trading (tamed or wild) ---
        if (cfg.tradingEnabled.get() && !held.isEmpty() && isTradeGood(held)) {
            long cooldownUntil = mob.getPersistentData().getLong("TradeCooldown");
            if (mob.tickCount < cooldownUntil) {
                player.displayClientMessage(Component.literal("It is not ready to trade yet."), true);
                return;
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            net.minecraft.network.chat.Component gave = held.getHoverName();
            if (!player.getAbilities().instabuild) held.shrink(1);
            mob.getPersistentData().putLong("TradeCooldown", mob.tickCount + 20);
            net.minecraft.world.item.ItemStack reward = randomTradeReward(mob.level().random);
            if (!reward.isEmpty()) {
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                player.displayClientMessage(Component.literal("Traded " + gave.getString() + " for "
                        + reward.getHoverName().getString() + (reward.getCount() > 1 ? " x" + reward.getCount() : "")), true);
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.2f);
                }
            }
            return;
        }

        // --- release a tamed mob (sneak + empty hand by owner) ---
        if (mob.isTamed()) {
            if (player.isShiftKeyDown() && held.isEmpty()
                    && mob.getOwnerUUID() != null && mob.getOwnerUUID().equals(player.getUUID())) {
                mob.setTamed(null);
                mob.setHordeId(null);
                player.displayClientMessage(Component.literal("Released the " + type.name().toLowerCase(java.util.Locale.ROOT) + "."), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        // --- taming ---
        Item tameItem = BuiltInRegistries.ITEM.get(new ResourceLocation(cfg.tameItem.get()));
        boolean tameItemValid = tameItem != null && tameItem != net.minecraft.world.item.Items.AIR;
        boolean holdingTameItem = tameItemValid && !held.isEmpty() && held.getItem() == tameItem;

        // --- mercenaries: hired with a contract + payment (taming treat does not work on them) ---
        if (mob instanceof com.nox.hzcode.entities.SoldierEntity ms && ms.isMercenary()) {
            net.minecraft.world.item.Item contractItem = BuiltInRegistries.ITEM.get(new ResourceLocation("hzcode:mercenary_contract"));
            if (holdingTameItem) {
                player.displayClientMessage(Component.literal("Mercenaries don't take treats - hire them with a Mercenary Contract + payment."), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            if (contractItem != null && !held.isEmpty() && held.getItem() == contractItem) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (mob.isTamed()) {
                    player.displayClientMessage(Component.literal("This mercenary is already under your contract."), true);
                    return;
                }
                net.minecraft.world.item.Item payItem = BuiltInRegistries.ITEM.get(new ResourceLocation(cfg.mercenaryPaymentItem.get()));
                boolean payValid = payItem != null && payItem != net.minecraft.world.item.Items.AIR;
                int need = Math.max(1, cfg.mercenaryPaymentCount.get());
                int have = 0;
                if (payValid) {
                    for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
                        if (!s.isEmpty() && s.getItem() == payItem) have += s.getCount();
                    }
                }
                String payName = payValid ? payItem.getName(new net.minecraft.world.item.ItemStack(payItem)).getString() : cfg.mercenaryPaymentItem.get();
                if (!payValid || have < need) {
                    player.displayClientMessage(Component.literal("Hiring costs " + need + " x " + payName + " - you have " + have + "."), true);
                    return;
                }
                if (!player.getAbilities().instabuild) {
                    int left = need;
                    for (net.minecraft.world.item.ItemStack s : player.getInventory().items) {
                        if (left <= 0) break;
                        if (!s.isEmpty() && s.getItem() == payItem) {
                            int take = Math.min(left, s.getCount());
                            s.shrink(take);
                            left -= take;
                        }
                    }
                    held.shrink(1);
                }
                mob.setTamed(player.getUUID());
                if (mob.getHordeId() != null && mob.level() instanceof ServerLevel serverLevel) {
                    HordeRegistry.get(serverLevel).detach(mob.getUUID());
                    mob.setHordeId(null);
                }
                if (mob.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1.0, mob.getZ(), 7, 0.3, 0.3, 0.3, 0.1);
                }
                player.displayClientMessage(Component.literal("Mercenary hired for " + need + " x " + payName + "! It will follow and defend you. Sneak + empty hand to release."), true);
                return;
            }
            player.displayClientMessage(Component.literal("Hire this mercenary with a Mercenary Contract + " + Math.max(1, cfg.mercenaryPaymentCount.get()) + " x payment (barter also works)."), true);
            return;
        }

        if (cfg.tamingEnabled.get() && holdingTameItem) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            net.minecraft.network.chat.Component heldName = held.getHoverName();
            if (!player.getAbilities().instabuild) held.shrink(1);
            if (mob.level() instanceof ServerLevel serverLevel
                    && serverLevel.random.nextDouble() < cfg.tameChance.get()) {
                mob.setTamed(player.getUUID());
                if (mob.getHordeId() != null) {
                    HordeRegistry.get(serverLevel).detach(mob.getUUID());
                    mob.setHordeId(null);
                }
                serverLevel.sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 1.0, mob.getZ(), 7, 0.3, 0.3, 0.3, 0.1);
                player.displayClientMessage(Component.literal("Tamed! It will follow and defend you. Sneak + empty hand to release."), true);
            } else if (mob.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 1.0, mob.getZ(), 7, 0.3, 0.3, 0.3, 0.1);
                player.displayClientMessage(Component.literal("It resisted " + heldName.getString() + "... try again."), true);
            }
            return;
        }

        if (cfg.tamingEnabled.get()) {
            player.displayClientMessage(Component.literal("Tame this " + type.name().toLowerCase(java.util.Locale.ROOT)
                    + " with: " + (tameItemValid ? tameItem.getName(new net.minecraft.world.item.ItemStack(tameItem)).getString() : cfg.tameItem.get())), true);
        }
    }

    private static boolean isTradeGood(net.minecraft.world.item.ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String s : ModConfig.COMMON.tradeGiveItems.get()) {
            if (s.equals(id.toString())) return true;
        }
        return false;
    }

    private static net.minecraft.world.item.ItemStack randomTradeReward(net.minecraft.util.RandomSource random) {
        java.util.List<Item> valid = new java.util.ArrayList<>();
        for (String s : ModConfig.COMMON.tradeReceiveItems.get()) {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(s));
            if (item != null && item != net.minecraft.world.item.Items.AIR) valid.add(item);
        }
        if (valid.isEmpty()) return net.minecraft.world.item.ItemStack.EMPTY;
        Item pick = valid.get(random.nextInt(valid.size()));
        int count = pick == net.minecraft.world.item.Items.GOLDEN_APPLE ? 1 : 1 + random.nextInt(3);
        return new net.minecraft.world.item.ItemStack(pick, count);
    }
}
