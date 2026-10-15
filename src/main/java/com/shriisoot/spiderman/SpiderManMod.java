package com.shriisoot.spiderman;

import com.shriisoot.spiderman.command.SpideyCommands;
import com.shriisoot.spiderman.network.ReleaseWebPayload;
import com.shriisoot.spiderman.registry.ModEntities;
import com.shriisoot.spiderman.registry.ModItems;
import com.shriisoot.spiderman.registry.ModSounds;
import com.shriisoot.spiderman.util.FallProtection;
import com.shriisoot.spiderman.entity.WebLineEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpiderManMod implements ModInitializer {
    public static final String MOD_ID = "spiderman";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModSounds.init();
        ModEntities.init();
        ModItems.init();
        SpideyCommands.init();

        PayloadTypeRegistry.playC2S().register(ReleaseWebPayload.TYPE, ReleaseWebPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReleaseWebPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            WebLineEntity web = WebLineEntity.findFor(player);
            if (web != null && web.getId() == payload.webId()) {
                web.release();
            }
        });

        // fallDistance > 0 separates a real landing from ender-pearl damage, which resets it before hurting.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player
                        && source.is(DamageTypeTags.IS_FALL)
                        && player.fallDistance > 0
                        && FallProtection.isProtected(player)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> FallProtection.forget(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FallProtection.clear());

        LOGGER.info("Your friendly neighborhood Spider-Man is ready.");
    }
}
