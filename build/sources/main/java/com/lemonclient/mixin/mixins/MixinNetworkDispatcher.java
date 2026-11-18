package com.lemonclient.mixin.mixins;

import com.lemonclient.client.LemonClient;
import io.netty.channel.ChannelHandlerContext;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic only: {@code cleanAttributes} is the single place that nulls the
 * {@code fml:dispatcher} channel attribute which {@code NetHandlerPlayClient.handleJoinGame}
 * dereferences. Logging a stack trace from here identifies whoever closes/disconnects the
 * play channel in the middle of joining.
 */
@Mixin(NetworkDispatcher.class)
public class MixinNetworkDispatcher {
   @Inject(method = "cleanAttributes", at = @At("HEAD"), remap = false)
   private void lemon$logCleanAttributes(ChannelHandlerContext ctx, CallbackInfo callbackInfo) {
      LemonClient.LOGGER.debug("NetworkDispatcher.cleanAttributes called", new Throwable("who cleared fml:dispatcher?"));
   }
}
