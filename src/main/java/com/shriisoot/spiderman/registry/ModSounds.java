package com.shriisoot.spiderman.registry;

import com.shriisoot.spiderman.SpiderManMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent THWIP = register("thwip");
    public static final SoundEvent WEB_HIT = register("web_hit");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        ResourceLocation id = SpiderManMod.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {
    }
}
