package com.shriisoot.spiderman.util;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Swinging players get a grace window so letting go mid-air doesn't splat them. */
public final class FallProtection {
    private static final Map<UUID, Long> PROTECTED_UNTIL = new ConcurrentHashMap<>();

    private FallProtection() {
    }

    public static void grant(ServerPlayer player, int ticks) {
        long until = player.serverLevel().getGameTime() + ticks;
        PROTECTED_UNTIL.merge(player.getUUID(), until, Math::max);
        player.resetFallDistance();
    }

    public static void forget(ServerPlayer player) {
        PROTECTED_UNTIL.remove(player.getUUID());
    }

    public static void clear() {
        PROTECTED_UNTIL.clear();
    }

    public static boolean isProtected(ServerPlayer player) {
        Long until = PROTECTED_UNTIL.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (player.serverLevel().getGameTime() > until) {
            PROTECTED_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
