package com.lemonclient.mixin.mixins;

import com.lemonclient.api.event.events.DeathEvent;
import com.lemonclient.api.util.player.Locks;
import com.lemonclient.client.LemonClient;
import com.lemonclient.client.manager.managers.TotemPopManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.play.server.SPacketEntityMetadata;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
   @Inject(method = "handleEntityMetadata", at = @At("RETURN"), cancellable = true)
   private void handleEntityMetadataHook(SPacketEntityMetadata sPacketEntityMetadata, CallbackInfo callbackInfo) {
      Entity getEntityByID;
      EntityPlayer entityPlayer;
      if (Minecraft.getMinecraft().world != null
         && (getEntityByID = Minecraft.getMinecraft().world.getEntityByID(sPacketEntityMetadata.getEntityId())) instanceof EntityPlayer
         && (entityPlayer = (EntityPlayer)getEntityByID).getHealth() <= 0.0F) {
         LemonClient.EVENT_BUS.post(new DeathEvent(entityPlayer));
         if (TotemPopManager.INSTANCE.sendMsgs) {
            TotemPopManager.INSTANCE.death(entityPlayer);
         }
      }
   }

   @Redirect(method = "handleHeldItemChange", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/InventoryPlayer;currentItem:I"))
   public void handleHeldItemChangeHook(InventoryPlayer inventoryPlayer, int value) {
      Locks.acquire(Locks.PLACE_SWITCH_LOCK, () -> inventoryPlayer.currentItem = value);
   }

   /**
    * Forge resolves the join dimension through the FML handshake dispatcher:
    * {@code NetworkDispatcher.get(getNetworkManager()).getOverrideDimension(packetIn)}.
    *
    * <p>On some connections the {@code fml:dispatcher} channel attribute is already gone by the
    * time {@code handleJoinGame} runs on the client thread, which makes the dispatcher {@code null}
    * and throws a {@link NullPointerException} before {@code loadWorld} is ever reached. That in turn
    * leaves {@code Minecraft.world}/{@code Minecraft.player} null and every subsequent packet
    * handler explodes as well.
    *
    * <p>Falling back to the dimension carried by the packet itself is exactly what a vanilla
    * (dispatcher-less) connection would do, so joining keeps working either way.
    */
   @Redirect(
      method = "handleJoinGame",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraftforge/fml/common/network/handshake/NetworkDispatcher;getOverrideDimension(Lnet/minecraft/network/play/server/SPacketJoinGame;)I",
         remap = false
      )
   )
   private int lemon$getOverrideDimension(NetworkDispatcher dispatcher, SPacketJoinGame packetIn) {
      if (dispatcher == null) {
         LemonClient.LOGGER.warn(
            "NetworkDispatcher is gone while joining, falling back to packet dimension {}",
            packetIn.getDimension()
         );
         return packetIn.getDimension();
      }
      return dispatcher.getOverrideDimension(packetIn);
   }
}
