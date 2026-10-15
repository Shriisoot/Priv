package com.shriisoot.spiderman.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a hanging web reset vanilla's "floating too long" counter, so swinging isn't treated as flying. */
@Mixin(ServerGamePacketListenerImpl.class)
public interface ServerGamePacketListenerImplAccessor {
    @Accessor("aboveGroundTickCount")
    void spiderman$setAboveGroundTickCount(int ticks);
}
