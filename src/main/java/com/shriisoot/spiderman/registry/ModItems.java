package com.shriisoot.spiderman.registry;

import com.shriisoot.spiderman.SpiderManMod;
import com.shriisoot.spiderman.item.SpiderManSpawnEggItem;
import com.shriisoot.spiderman.item.WebShooterItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final Item WEB_SHOOTER = register("web_shooter",
            new WebShooterItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final Item SPIDER_MAN_SPAWN_EGG = register("spider_man_spawn_egg",
            new SpiderManSpawnEggItem(ModEntities.SPIDER_MAN, 0xC8102E, 0x1F3F99, new Item.Properties()));

    public static final CreativeModeTab TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            SpiderManMod.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(WEB_SHOOTER))
                    .title(Component.translatable("itemGroup.spiderman"))
                    .displayItems((params, output) -> {
                        output.accept(SPIDER_MAN_SPAWN_EGG);
                        output.accept(WEB_SHOOTER);
                    })
                    .build());

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(BuiltInRegistries.ITEM, SpiderManMod.id(name), item);
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.SPAWN_EGGS).register(e -> e.accept(SPIDER_MAN_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> e.accept(WEB_SHOOTER));
    }
}
