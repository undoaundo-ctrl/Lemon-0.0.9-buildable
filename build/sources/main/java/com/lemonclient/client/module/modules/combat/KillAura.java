package com.lemonclient.client.module.modules.combat;

import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.DoubleSetting;
import com.lemonclient.api.setting.values.IntegerSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.util.player.SpoofRotationUtil;
import com.lemonclient.api.util.player.social.SocialManager;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketAnimation;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;

/** Port of SalHack's KillAuraModule (simple, low-overhead). */
@Module.Declaration(name = "KillAura", category = Category.Combat)
public class KillAura extends Module {
   ModeSetting mode = this.registerMode("Mode", Arrays.asList("Closest", "Priority", "Switch"), "Closest");
   DoubleSetting distance = this.registerDouble("Range", 5.0, 0.0, 10.0);
   BooleanSetting hitDelay = this.registerBoolean("Hit Delay", true);
   IntegerSetting ticks = this.registerInteger("Ticks", 10, 0, 40, () -> !this.hitDelay.getValue());
   IntegerSetting iterations = this.registerInteger("Iterations", 1, 1, 10);
   BooleanSetting players = this.registerBoolean("Players", true);
   BooleanSetting monsters = this.registerBoolean("Monsters", true);
   BooleanSetting neutrals = this.registerBoolean("Neutrals", false);
   BooleanSetting animals = this.registerBoolean("Animals", false);
   BooleanSetting ignoreFriends = this.registerBoolean("Ignore Friends", true);
   BooleanSetting swing = this.registerBoolean("Swing", true);
   BooleanSetting reach = this.registerBoolean("Reach", true);

   private Entity currentTarget = null;
   private int remainingTicks = 0;

   @Override
   protected void onEnable() {
      SpoofRotationUtil.ROTATION_UTIL.onEnable();
      this.remainingTicks = 0;
      this.currentTarget = null;
   }

   @Override
   protected void onDisable() {
      SpoofRotationUtil.ROTATION_UTIL.resetRotation();
      SpoofRotationUtil.ROTATION_UTIL.onDisable();
      SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(false);
      this.currentTarget = null;
   }

   @Override
   public void onUpdate() {
      if (mc.player == null || mc.world == null) return;

      if (this.remainingTicks > 0) {
         this.remainingTicks--;
      }

      // Target selection (SalHack modes)
      Entity target = this.currentTarget;
      String currentMode = this.mode.getValue();
      switch (currentMode) {
         case "Closest":
            target = this.findClosest();
            break;
         case "Priority":
            if (target == null) target = this.findClosest();
            break;
         case "Switch":
            Entity closest = this.findClosest();
            if (closest != null) target = closest;
            break;
      }

      if (target == null || target.isDead || mc.player.getDistance(target) > this.distance.getValue()) {
         this.currentTarget = null;
         SpoofRotationUtil.ROTATION_UTIL.resetRotation();
         return;
      }
      this.currentTarget = target;

      // Rotation spoof (Aimbot equivalent)
      SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(true);
      SpoofRotationUtil.ROTATION_UTIL.lookAtPacket(
         target.posX, target.posY + target.getEyeHeight(), target.posZ, mc.player
      );

      // Attack timing
      boolean ready;
      if (this.hitDelay.getValue()) {
         ready = mc.player.getCooledAttackStrength(0.0f) >= 1.0f;
      } else {
         ready = this.remainingTicks <= 0;
      }
      if (!ready) return;
      this.remainingTicks = this.ticks.getValue();

      for (int i = 0; i < this.iterations.getValue(); i++) {
         this.attack(target);
         mc.player.resetCooldown();
      }
   }

   private void attack(Entity victim) {
      double dist = mc.player.getDistance(victim);
      if (this.reach.getValue() && dist > 3.0 && dist <= this.distance.getValue()) {
         double dx = victim.posX - mc.player.posX;
         double dy = victim.posY - mc.player.posY;
         double dz = victim.posZ - mc.player.posZ;
         double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (len > 0.0001) {
            double factor = (len - 2.5) / len;
            mc.player.connection.sendPacket(new CPacketPlayer.Position(
               mc.player.posX + dx * factor, mc.player.posY + dy * factor, mc.player.posZ + dz * factor, mc.player.onGround
            ));
            mc.player.connection.sendPacket(new CPacketUseEntity(victim));
            mc.player.connection.sendPacket(new CPacketPlayer.Position(
               mc.player.posX, mc.player.posY, mc.player.posZ, mc.player.onGround
            ));
         }
      } else {
         mc.player.connection.sendPacket(new CPacketUseEntity(victim));
      }

      if (this.swing.getValue()) {
         mc.player.connection.sendPacket(new CPacketAnimation(EnumHand.MAIN_HAND));
      }
   }

   private Entity findClosest() {
      Entity best = null;
      double bestDist = Double.MAX_VALUE;
      List<Entity> entities = mc.world.loadedEntityList;
      for (Entity entity : entities) {
         if (!this.isValidTarget(entity)) continue;
         double dist = mc.player.getDistance(entity);
         if (dist < bestDist) {
            bestDist = dist;
            best = entity;
         }
      }
      return best;
   }

   private boolean isValidTarget(Entity entity) {
      if (entity == null || entity == mc.player || entity.isDead) return false;
      if (!(entity instanceof EntityLivingBase)) return false;

      if (entity instanceof EntityPlayer) {
         if (!this.players.getValue()) return false;
         if (this.ignoreFriends.getValue() && SocialManager.isFriend(entity.getName())) return false;
      } else {
         boolean hostile = entity.isCreatureType(EnumCreatureType.MONSTER, false) && !this.isNeutral(entity);
         boolean neutral = this.isNeutral(entity);
         boolean passive = this.isPassive(entity);

         if (hostile && !this.monsters.getValue()) return false;
         if (neutral && !this.neutrals.getValue()) return false;
         if (passive && !this.animals.getValue()) return false;
         if (!hostile && !neutral && !passive) return false;
      }

      EntityLivingBase living = (EntityLivingBase) entity;
      return living.getHealth() > 0.0f && mc.player.getDistance(entity) <= this.distance.getValue();
   }

   private boolean isNeutral(Entity entity) {
      return entity instanceof EntityPigZombie || entity instanceof EntityWolf || entity instanceof EntityEnderman;
   }

   private boolean isPassive(Entity entity) {
      if (entity instanceof EntityWolf && ((EntityWolf) entity).isAngry()) return false;
      return entity instanceof EntityAnimal
         || entity instanceof EntityTameable
         || entity instanceof EntityAmbientCreature
         || entity instanceof EntitySquid;
   }
}
