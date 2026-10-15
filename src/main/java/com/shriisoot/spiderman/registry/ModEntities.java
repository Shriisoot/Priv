package com.shriisoot.spiderman.registry;

import com.shriisoot.spiderman.SpiderManMod;
import com.shriisoot.spiderman.entity.SpiderManEntity;
import com.shriisoot.spiderman.entity.WebLineEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
    public static final EntityType<SpiderManEntity> SPIDER_MAN = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SpiderManMod.id("spider_man"),
            EntityType.Builder.of(SpiderManEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .build("spider_man"));

    public static final EntityType<WebLineEntity> WEB_LINE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            SpiderManMod.id("web_line"),
            EntityType.Builder.<WebLineEntity>of(WebLineEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSave()
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("web_line"));

    private ModEntities() {
    }

    public static void init() {
        FabricDefaultAttributeRegistry.register(SPIDER_MAN, SpiderManEntity.createAttributes());
    }
}
