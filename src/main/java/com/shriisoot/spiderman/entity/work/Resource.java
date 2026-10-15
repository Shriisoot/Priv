package com.shriisoot.spiderman.entity.work;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

/** Things Spider-Man knows how to go and get. */
public enum Resource {
    WOOD("wood", tag(BlockTags.LOGS), Items.DIAMOND_AXE, "wood", "woods", "log", "logs", "tree", "trees", "timber", "lumber", "planks"),
    STONE("stone", tag(BlockTags.BASE_STONE_OVERWORLD), Items.DIAMOND_PICKAXE, "stone", "stones", "cobble", "cobblestone", "rock", "rocks"),
    COAL("coal", tag(BlockTags.COAL_ORES), Items.DIAMOND_PICKAXE, "coal"),
    IRON("iron", tag(BlockTags.IRON_ORES), Items.DIAMOND_PICKAXE, "iron"),
    COPPER("copper", tag(BlockTags.COPPER_ORES), Items.DIAMOND_PICKAXE, "copper"),
    GOLD("gold", tag(BlockTags.GOLD_ORES), Items.DIAMOND_PICKAXE, "gold"),
    REDSTONE("redstone", tag(BlockTags.REDSTONE_ORES), Items.DIAMOND_PICKAXE, "redstone"),
    LAPIS("lapis", tag(BlockTags.LAPIS_ORES), Items.DIAMOND_PICKAXE, "lapis", "lazuli"),
    DIAMOND("diamonds", tag(BlockTags.DIAMOND_ORES), Items.DIAMOND_PICKAXE, "diamond", "diamonds"),
    EMERALD("emeralds", tag(BlockTags.EMERALD_ORES), Items.DIAMOND_PICKAXE, "emerald", "emeralds"),
    SAND("sand", state -> state.is(BlockTags.SAND) && !state.is(Blocks.SUSPICIOUS_SAND), Items.DIAMOND_SHOVEL, "sand"),
    GRAVEL("gravel", blocks(Blocks.GRAVEL), Items.DIAMOND_SHOVEL, "gravel", "flint"),
    CLAY("clay", blocks(Blocks.CLAY), Items.DIAMOND_SHOVEL, "clay"),
    DIRT("dirt", blocks(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT), Items.DIAMOND_SHOVEL, "dirt", "soil");

    private final String displayName;
    private final Predicate<BlockState> matcher;
    private final Item tool;
    private final String[] keywords;

    Resource(String displayName, Predicate<BlockState> matcher, Item tool, String... keywords) {
        this.displayName = displayName;
        this.matcher = matcher;
        this.tool = tool;
        this.keywords = keywords;
    }

    public boolean matches(BlockState state) {
        return matcher.test(state);
    }

    public ItemStack tool() {
        return new ItemStack(tool);
    }

    public String displayName() {
        return displayName;
    }

    public boolean needsNaturalCheck() {
        return this == WOOD;
    }

    @Nullable
    public static Resource fromText(String text) {
        String[] words = text.toLowerCase(Locale.ROOT).split("[^a-z]+");
        for (String word : words) {
            for (Resource resource : values()) {
                for (String keyword : resource.keywords) {
                    if (keyword.equals(word)) {
                        return resource;
                    }
                }
            }
        }
        return null;
    }

    private static Predicate<BlockState> tag(TagKey<Block> tag) {
        return state -> state.is(tag);
    }

    private static Predicate<BlockState> blocks(Block... blocks) {
        return state -> {
            for (Block block : blocks) {
                if (state.is(block)) {
                    return true;
                }
            }
            return false;
        };
    }
}
