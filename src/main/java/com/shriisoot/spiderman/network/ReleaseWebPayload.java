package com.shriisoot.spiderman.network;

import com.shriisoot.spiderman.SpiderManMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ReleaseWebPayload() implements CustomPacketPayload {
    public static final ReleaseWebPayload INSTANCE = new ReleaseWebPayload();
    public static final Type<ReleaseWebPayload> TYPE = new Type<>(SpiderManMod.id("release_web"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseWebPayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
