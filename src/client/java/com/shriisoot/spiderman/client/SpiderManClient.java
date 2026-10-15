package com.shriisoot.spiderman.client;

import com.shriisoot.spiderman.client.render.SpiderManModel;
import com.shriisoot.spiderman.client.render.SpiderManRenderer;
import com.shriisoot.spiderman.client.render.WebLineRenderer;
import com.shriisoot.spiderman.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class SpiderManClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(SpiderManModel.LAYER, SpiderManModel::createLayer);
        EntityRendererRegistry.register(ModEntities.SPIDER_MAN, SpiderManRenderer::new);
        EntityRendererRegistry.register(ModEntities.WEB_LINE, WebLineRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWebCache.tick(client);
            WebSwingController.tick(client);
        });
    }
}
