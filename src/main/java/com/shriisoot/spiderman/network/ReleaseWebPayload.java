package com.shriisoot.spiderman.network;

import com.shriisoot.spiderman.SpiderManMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: let go of the given web. Carrying the id stops a late packet from dropping a newer web. */
public record ReleaseWebPayload(int webId) implements CustomPacketPayload {
    public static final Type<ReleaseWebPayload> TYPE = new Type<>(SpiderManMod.id("release_web"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseWebPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(ReleaseWebPayload::new, ReleaseWebPayload::webId).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
