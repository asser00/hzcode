package com.nox.hzcode.items;

import com.nox.hzcode.horde.HordeController;
import com.nox.hzcode.horde.HordeSpawner;
import com.nox.hzcode.horde.HordeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Support Call: right-click to call in a soldier horde of a fixed size (1-5) at your position. Consumed in survival. */
public class SupportCallItem extends Item {
    private final int count;

    public SupportCallItem(int count) {
        super(new Item.Properties().stacksTo(16));
        this.count = count;
    }

    public int getCount() {
        return count;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            HordeController controller = HordeSpawner.spawnHorde(serverLevel, HordeType.SOLDIER, player.blockPosition(), count);
            if (controller != null) {
                player.displayClientMessage(Component.literal("Support arrived: " + controller.getMemberCount() + " soldiers!")
                        .withStyle(ChatFormatting.GREEN), true);
                level.playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0f, 0.8f);
                if (!player.getAbilities().instabuild) stack.shrink(1);
                player.getCooldowns().addCooldown(this, 200);
            } else {
                player.displayClientMessage(Component.literal("Support failed to arrive.").withStyle(ChatFormatting.RED), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
