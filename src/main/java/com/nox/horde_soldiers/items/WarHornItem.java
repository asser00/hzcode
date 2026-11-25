package com.nox.horde_soldiers.items;

import com.nox.horde_soldiers.horde.HordeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Horde War Horn: right-click to start/stop the timed horde event (the "timing" product). */
public class WarHornItem extends Item {
    public WarHornItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (HordeManager.isActive()) {
                HordeManager.stopHorde();
                player.displayClientMessage(Component.literal("Horde event STOPPED.").withStyle(ChatFormatting.RED), true);
            } else {
                HordeManager.startHorde();
                player.displayClientMessage(Component.literal("Horde event STARTED - first wave incoming!").withStyle(ChatFormatting.GOLD), true);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(this, 100);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
