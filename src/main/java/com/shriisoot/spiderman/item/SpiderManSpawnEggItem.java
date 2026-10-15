package com.shriisoot.spiderman.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Spawner;

/** A spawn egg that can't be used to turn mob spawners into Spider-Man factories. */
public class SpiderManSpawnEggItem extends SpawnEggItem {
    public SpiderManSpawnEggItem(EntityType<? extends Mob> type, int primaryColor, int secondaryColor, Properties properties) {
        super(type, primaryColor, secondaryColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockEntity blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (blockEntity instanceof Spawner) {
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
