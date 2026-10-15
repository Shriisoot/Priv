package com.shriisoot.spiderman.item;

import com.shriisoot.spiderman.entity.WebLineEntity;
import com.shriisoot.spiderman.registry.ModItems;
import com.shriisoot.spiderman.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class WebShooterItem extends Item {
    private static final double WEB_SPEED = 3.6;

    public WebShooterItem(Properties properties) {
        super(properties);
    }

    public static boolean isHolding(Player player) {
        return player.getMainHandItem().is(ModItems.WEB_SHOOTER) || player.getOffhandItem().is(ModItems.WEB_SHOOTER);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            WebLineEntity existing = WebLineEntity.findFor(player);
            if (existing != null) {
                existing.release();
            } else {
                byte mode = player.isShiftKeyDown() ? WebLineEntity.MODE_ZIP : WebLineEntity.MODE_SWING;
                Vec3 look = player.getLookAngle();
                boolean rightHand = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
                float yaw = player.getYRot() * ((float) Math.PI / 180F);
                Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(rightHand ? 0.3 : -0.3);
                Vec3 from = player.getEyePosition().add(side).add(0, -0.25, 0).add(look.scale(0.5));
                WebLineEntity.shoot(level, player, mode, from, look, WEB_SPEED);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.THWIP, SoundSource.PLAYERS,
                        1.0F, 0.95F + level.random.nextFloat() * 0.15F);
            }
        }
        player.getCooldowns().addCooldown(this, 5);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.shoot").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.zip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.swing").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.reel").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.release").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.spiderman.web_shooter.tip.yank").withStyle(ChatFormatting.DARK_RED));
    }
}
