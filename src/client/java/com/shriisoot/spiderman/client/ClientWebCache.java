package com.shriisoot.spiderman.client;

import com.shriisoot.spiderman.entity.WebLineEntity;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Owner id -> active web, rebuilt once per tick so per-frame lookups (arm poses) are free. */
public final class ClientWebCache {
    private static final Int2ObjectMap<WebLineEntity> BY_OWNER = new Int2ObjectOpenHashMap<>();

    private ClientWebCache() {
    }

    public static void tick(Minecraft client) {
        BY_OWNER.clear();
        if (client.level == null) {
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof WebLineEntity web && web.isAlive()) {
                BY_OWNER.put(web.getOwnerId(), web);
            }
        }
    }

    @Nullable
    public static WebLineEntity get(Entity owner) {
        WebLineEntity web = BY_OWNER.get(owner.getId());
        return web != null && web.isAlive() ? web : null;
    }
}
