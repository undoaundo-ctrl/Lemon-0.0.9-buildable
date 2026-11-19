package com.lemonclient.client.module.modules.combat;

import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.util.player.SpoofRotationUtil;
import com.lemonclient.api.util.player.social.SocialManager;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;

@Module.Declaration(name = "KillAura", category = Category.Combat)
public class KillAura extends Module {
   DoubleSetting range = this.registerDouble("Range", 4.0, 1.0, 10.0);
   BooleanSetting rotate = this.registerBoolean("Rotate", true);
   BooleanSetting swing = this.registerBoolean("Swing", true);
   BooleanSetting players = this.registerBoolean("Players", true);
   BooleanSetting hostile = this.registerBoolean("Hostile Mobs", true);
   BooleanSetting passive = this.registerBoolean("Passive Animals", true);
   BooleanSetting other = this.registerBoolean("Other Entities", false);
   BooleanSetting ignoreFriends = this.registerBoolean("Ignore Friends", true);
   BooleanSetting ignoreCooldown = this.registerBoolean("Ignore Cooldown", false);
   BooleanSetting reachBypass = this.registerBoolean("Reach Bypass", true);
   DoubleSetting reach = this.registerDouble("Reach", 4.5, 3.0, 10.0, () -> this.reachBypass.getValue());

   private Entity target = null;
   private int attackTicks = 0;

   @Override
   protected void onEnable() {
      SpoofRotationUtil.ROTATION_UTIL.onEnable();
      this.attackTicks = 0;
   }

   @Override
   protected void onDisable() {
      SpoofRotationUtil.ROTATION_UTIL.resetRotation();
      SpoofRotationUtil.ROTATION_UTIL.onDisable();
      this.target = null;
   }

   @Override
   public void onUpdate() {
      if (mc.player == null || mc.world == null) return;

      // Find nearest valid living target
      Entity best = null;
      double bestDist = this.range.getValue();
      for (Entity entity : mc.world.loadedEntityList) {
         if (!(entity instanceof EntityLivingBase)) continue;
         if (entity == mc.player) continue;
         if (!entity.isEntityAlive()) continue;
         if (!this.isTarget((EntityLivingBase) entity)) continue;
         if (this.ignoreFriends.getValue()
            && entity instanceof EntityPlayer
            && SocialManager.isFriend(entity.getName())) continue;

         double dist = mc.player.getDistance(entity);
         if (dist <= bestDist) {
            bestDist = dist;
            best = entity;
         }
      }
      this.target = best;

      if (this.target == null) {
         SpoofRotationUtil.ROTATION_UTIL.resetRotation();
         return;
      }

      if (this.rotate.getValue()) {
         SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(true);
         SpoofRotationUtil.ROTATION_UTIL.lookAtPacket(
            this.target.posX,
            this.target.posY + this.target.getEyeHeight(),
            this.target.posZ,
            mc.player
         );
      }

      // Attack timing: vanilla cooldown or every tick
      this.attackTicks++;
      boolean ready = this.ignoreCooldown.getValue()
         ? this.attackTicks >= 1
         : mc.player.getCooledAttackStrength(0.0f) >= 1.0f;
      if (ready) {
         this.attack(this.target);
         this.attackTicks = 0;
      }
   }

   private void attack(Entity victim) {
      double dist = mc.player.getDistance(victim);
      boolean bypass = this.reachBypass.getValue() && dist > 3.0 && dist <= this.reach.getValue();

      if (bypass) {
         // Server only accepts attacks within 3 blocks (9.0 sq). Move the
         // reported position close enough, attack, then restore it.
         double dx = victim.posX - mc.player.posX;
         double dy = victim.posY - mc.player.posY;
         double dz = victim.posZ - mc.player.posZ;
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len > 0.0001) {
            double factor = (len - 2.5) / len;
            double fx = mc.player.posX + dx * factor;
            double fy = mc.player.posY + dy * factor;
            double fz = mc.player.posZ + dz * factor;
            mc.player.connection.sendPacket(new CPacketPlayer.Position(fx, fy, fz, mc.player.onGround));
            mc.player.connection.sendPacket(new CPacketUseEntity(victim));
            mc.player.connection.sendPacket(
               new CPacketPlayer.Position(mc.player.posX, mc.player.posY, mc.player.posZ, mc.player.onGround)
            );
         }
      } else if (dist <= 3.0 || !this.reachBypass.getValue()) {
         mc.player.connection.sendPacket(new CPacketUseEntity(victim));
      }

      if (this.swing.getValue()) {
         mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
      }
   }

   private boolean isTarget(EntityLivingBase entity) {
      if (entity instanceof EntityPlayer) return this.players.getValue();
      if (entity instanceof EntityMob) return this.hostile.getValue();
      // Bosses (Wither / Ender Dragon) are EntityLiving, not EntityMob
      if (entity instanceof EntityWither || entity instanceof EntityDragon) return this.hostile.getValue();
      if (entity instanceof EntityAnimal) return this.passive.getValue();
      return this.other.getValue();
   }
}
