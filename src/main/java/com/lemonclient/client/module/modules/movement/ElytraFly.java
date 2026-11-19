package com.lemonclient.client.module.modules.movement;

import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.setting.values.IntegerSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.util.chat.Notification;
import com.lemonclient.api.util.misc.MessageBus;
import com.lemonclient.api.util.player.SpoofRotationUtil;
import com.lemonclient.api.util.world.MotionUtil;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketEntityAction.Action;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.util.EnumHand;

/**
 * ElytraFlight ported from Lambda 3.4.0 behaviour.
 * Modes: Boost, Control, Creative, Packet, Vanilla, Fireworks.
 */
@Module.Declaration(name = "ElytraFly", category = Category.Movement)
public class ElytraFly extends Module {
   // General
   ModeSetting mode = this.registerMode(
      "Mode", Arrays.asList("Control", "Boost", "Creative", "Packet", "Vanilla", "Fireworks"), "Control"
   );
   BooleanSetting easyTakeoff = this.registerBoolean("Easy Takeoff", true);
   BooleanSetting autoLanding = this.registerBoolean("Auto Landing", false);
   BooleanSetting durabilityWarning = this.registerBoolean("Durability Warning", true);
   IntegerSetting threshold = this.registerInteger("Threshold", 10, 0, 100, () -> this.durabilityWarning.getValue());
   BooleanSetting swing = this.registerBoolean("Swing", true);
   BooleanSetting spoofPitch = this.registerBoolean("Spoof Pitch", false);
   IntegerSetting forwardPitch = this.registerInteger("Forward Pitch", 0, -90, 90, () -> this.spoofPitch.getValue());

   // Boost
   DoubleSetting speedBoost = this.registerDouble("Boost Speed", 1.5, 0.1, 10.0, () -> this.mode.getValue().equals("Boost"));
   DoubleSetting upSpeedBoost = this.registerDouble("Boost Up Speed", 0.4, 0.0, 5.0, () -> this.mode.getValue().equals("Boost"));
   DoubleSetting downSpeedBoost = this.registerDouble("Boost Down Speed", 0.4, 0.0, 5.0, () -> this.mode.getValue().equals("Boost"));

   // Control
   DoubleSetting speedControl = this.registerDouble("Control Speed", 1.0, 0.1, 10.0, () -> this.mode.getValue().equals("Control"));
   DoubleSetting upSpeedControl = this.registerDouble("Control Up Speed", 0.4, 0.0, 5.0, () -> this.mode.getValue().equals("Control"));
   DoubleSetting downSpeedControl = this.registerDouble("Control Down Speed", 0.4, 0.0, 5.0, () -> this.mode.getValue().equals("Control"));
   DoubleSetting fallSpeedControl = this.registerDouble("Control Fall Speed", 0.1, 0.0, 5.0, () -> this.mode.getValue().equals("Control"));

   // Creative
   DoubleSetting speedCreative = this.registerDouble("Creative Speed", 2.0, 0.1, 20.0, () -> this.mode.getValue().equals("Creative"));
   DoubleSetting upSpeedCreative = this.registerDouble("Creative Up Speed", 1.0, 0.0, 10.0, () -> this.mode.getValue().equals("Creative"));
   DoubleSetting downSpeedCreative = this.registerDouble("Creative Down Speed", 1.0, 0.0, 10.0, () -> this.mode.getValue().equals("Creative"));

   // Packet
   DoubleSetting speedPacket = this.registerDouble("Packet Speed", 3.0, 0.1, 20.0, () -> this.mode.getValue().equals("Packet"));
   BooleanSetting packetSync = this.registerBoolean("Packet Sync Client", true, () -> this.mode.getValue().equals("Packet"));

   // Fireworks
   ModeSetting fireworkMode = this.registerMode(
      "Firework Mode", Arrays.asList("Speed", "Delay"), "Speed", () -> this.mode.getValue().equals("Fireworks")
   );
   DoubleSetting fireworkStartSpeed = this.registerDouble(
      "Firework Start Speed", 1.0, 0.1, 10.0, () -> this.mode.getValue().equals("Fireworks") && this.fireworkMode.getValue().equals("Speed")
   );
   IntegerSetting fireworkDelay = this.registerInteger(
      "Firework Delay", 10, 1, 100, () -> this.mode.getValue().equals("Fireworks") && this.fireworkMode.getValue().equals("Delay")
   );
   DoubleSetting speedFireworks = this.registerDouble("Fireworks Speed", 1.0, 0.1, 10.0, () -> this.mode.getValue().equals("Fireworks"));

   // State
   private boolean wasFlying = false;
   private boolean warned = false;
   private int fireworkTimer = 0;

   @Override
   protected void onEnable() {
      this.wasFlying = false;
      this.warned = false;
      this.fireworkTimer = 0;
      SpoofRotationUtil.ROTATION_UTIL.onEnable();
   }

   @Override
   protected void onDisable() {
      SpoofRotationUtil.ROTATION_UTIL.resetRotation();
      SpoofRotationUtil.ROTATION_UTIL.onDisable();
      this.wasFlying = false;
   }

   @Override
   public void onUpdate() {
      if (mc.player == null || mc.world == null || mc.player.isDead) return;

      if (!this.isElytraEquipped()) {
         SpoofRotationUtil.ROTATION_UTIL.resetRotation();
         return;
      }

      if (this.durabilityWarning.getValue()) {
         this.checkDurability();
      }

      boolean flying = mc.player.isElytraFlying();

      if (!flying) {
         if (this.easyTakeoff.getValue()) {
            this.tryTakeoff();
         }
         if (mc.player.isElytraFlying()) {
            // takeoff succeeded this tick
            MessageBus.sendClientPrefixMessage("ElytraFly: takeoff", Notification.Type.SUCCESS);
         }
         SpoofRotationUtil.ROTATION_UTIL.resetRotation();
         this.wasFlying = false;
         return;
      }

      if (!this.wasFlying) {
         this.wasFlying = true;
         this.fireworkTimer = 0;
      }

      // Spoof the pitch of packets so server-side movement matches look direction
      if (this.spoofPitch.getValue()) {
         SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(true);
         SpoofRotationUtil.ROTATION_UTIL.setYawAndPitch(mc.player.rotationYaw, (float) Math.toRadians(this.forwardPitch.getValue()));
      } else {
         SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(false);
      }

      String currentMode = this.mode.getValue();
      switch (currentMode) {
         case "Boost":
            this.boostMode();
            break;
         case "Control":
            this.controlMode();
            break;
         case "Creative":
            this.creativeMode();
            break;
         case "Packet":
            this.packetMode();
            break;
         case "Fireworks":
            this.fireworksMode();
            break;
         case "Vanilla":
         default:
            // Let vanilla handle movement; only takeoff / durability are applied
            break;
      }

      if (this.autoLanding.getValue()) {
         this.autoLanding();
      }

      if (this.swing.getValue()) {
         mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
      }
   }

   private boolean isElytraEquipped() {
      ItemStack chest = mc.player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
      return chest != null && chest.getItem() instanceof ItemElytra;
   }

   private void checkDurability() {
      ItemStack chest = mc.player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
      int durability = chest.getMaxDamage() - chest.getItemDamage();
      if (durability <= this.threshold.getValue()) {
         if (!this.warned) {
            MessageBus.sendClientPrefixMessage(
               "ElytraFly: elytra durability low (" + durability + ")", Notification.Type.WARNING
            );
            this.warned = true;
         }
      } else {
         this.warned = false;
      }
   }

   private void tryTakeoff() {
      EntityPlayerSP player = mc.player;
      if (player.onGround || player.isInWater() || player.isInLava()) return;
      if (!mc.gameSettings.keyBindJump.isKeyDown()) return;
      if (player.fallDistance < 0.5f) return;

      // Entity.setFlag is protected; reflection covers both MCP (dev) and SRG (runtime) names.
      Method setFlag = null;
      try {
         setFlag = net.minecraft.entity.Entity.class.getDeclaredMethod("setFlag", int.class, boolean.class);
      } catch (NoSuchMethodException ignored) {
         try {
            setFlag = net.minecraft.entity.Entity.class.getDeclaredMethod("func_70052_a", int.class, boolean.class);
         } catch (NoSuchMethodException ignored2) {
         }
      }
      if (setFlag != null) {
         try {
            setFlag.setAccessible(true);
            setFlag.invoke(player, 7, true);
         } catch (Exception ignored) {
         }
      }
      player.connection.sendPacket(new CPacketEntityAction(player, Action.START_FALL_FLYING));
   }

   /** Offset that cancels vanilla elytra gravity (0.08 * 0.98). */
   private double hoverOffset() {
      return 0.08;
   }

   private void boostMode() {
      double yaw = Math.toRadians(mc.player.rotationYaw);
      double pitch = Math.toRadians(mc.player.rotationPitch);
      double speed = this.speedBoost.getValue();

      // Looking up/down scales vertical speed
      double vertical = -Math.sin(pitch) * speed;
      if (vertical > 0.0) {
         vertical += this.upSpeedBoost.getValue();
      } else if (vertical < 0.0) {
         vertical -= this.downSpeedBoost.getValue();
      }

      mc.player.motionX = -Math.sin(yaw) * Math.cos(pitch) * speed;
      mc.player.motionY = vertical + this.hoverOffset();
      mc.player.motionZ = Math.cos(yaw) * Math.cos(pitch) * speed;
   }

   private void controlMode() {
      double[] dir = MotionUtil.forward(this.speedControl.getValue());
      mc.player.motionX = dir[0];
      mc.player.motionZ = dir[1];

      if (mc.gameSettings.keyBindJump.isKeyDown()) {
         mc.player.motionY = this.upSpeedControl.getValue() + this.hoverOffset();
      } else if (mc.gameSettings.keyBindSneak.isKeyDown()) {
         mc.player.motionY = -this.downSpeedControl.getValue() + this.hoverOffset();
      } else if (mc.player.motionY < 0.0) {
         mc.player.motionY = -this.fallSpeedControl.getValue() + this.hoverOffset();
      } else {
         mc.player.motionY = this.hoverOffset();
      }
   }

   private void creativeMode() {
      double[] dir = MotionUtil.forward(this.speedCreative.getValue());
      mc.player.motionX = dir[0];
      mc.player.motionZ = dir[1];

      if (mc.gameSettings.keyBindJump.isKeyDown()) {
         mc.player.motionY = this.upSpeedCreative.getValue() + this.hoverOffset();
      } else if (mc.gameSettings.keyBindSneak.isKeyDown()) {
         mc.player.motionY = -this.downSpeedCreative.getValue() + this.hoverOffset();
      } else {
         mc.player.motionY = this.hoverOffset();
      }
   }

   private void packetMode() {
      double yaw = Math.toRadians(mc.player.rotationYaw);
      double pitch = Math.toRadians(mc.player.rotationPitch);
      double speed = this.speedPacket.getValue();

      double mx = -Math.sin(yaw) * Math.cos(pitch) * speed;
      double my = -Math.sin(pitch) * speed + this.hoverOffset();
      double mz = Math.cos(yaw) * Math.cos(pitch) * speed;

      double x = mc.player.posX + mx;
      double y = mc.player.posY + my;
      double z = mc.player.posZ + mz;

      mc.player.connection.sendPacket(new CPacketPlayer.Position(x, y, z, false));

      if (this.packetSync.getValue()) {
         mc.player.motionX = mx;
         mc.player.motionY = my;
         mc.player.motionZ = mz;
      }
   }

   private void fireworksMode() {
      // Fly like boost while automatically firing rockets
      double yaw = Math.toRadians(mc.player.rotationYaw);
      double pitch = Math.toRadians(mc.player.rotationPitch);
      double speed = this.speedFireworks.getValue();

      mc.player.motionX = -Math.sin(yaw) * Math.cos(pitch) * speed;
      mc.player.motionZ = Math.cos(yaw) * Math.cos(pitch) * speed;
      mc.player.motionY = -Math.sin(pitch) * speed + this.hoverOffset();

      boolean use = false;
      if (this.fireworkMode.getValue().equals("Speed")) {
         double flat = Math.sqrt(mc.player.motionX * mc.player.motionX + mc.player.motionZ * mc.player.motionZ);
         use = flat < this.fireworkStartSpeed.getValue();
      } else {
         if (this.fireworkTimer <= 0) {
            use = true;
            this.fireworkTimer = this.fireworkDelay.getValue();
         }
         this.fireworkTimer--;
      }

      if (use) {
         this.useFirework();
      }
   }

   private void useFirework() {
      EnumHand hand = this.hasFirework(EnumHand.MAIN_HAND) ? EnumHand.MAIN_HAND
         : this.hasFirework(EnumHand.OFF_HAND) ? EnumHand.OFF_HAND : null;
      if (hand != null) {
         mc.player.connection.sendPacket(new CPacketPlayerTryUseItem(hand));
         mc.player.connection.sendPacket(new CPacketAnimation(hand));
      }
   }

   private boolean hasFirework(EnumHand hand) {
      ItemStack stack = mc.player.getHeldItem(hand);
      return stack != null && stack.getItem() == Items.FIREWORKS;
   }

   private void autoLanding() {
      if (mc.gameSettings.keyBindSneak.isKeyDown()) {
         mc.player.motionY = -0.5;
         mc.player.motionX *= 0.6;
         mc.player.motionZ *= 0.6;
      }
   }
}
