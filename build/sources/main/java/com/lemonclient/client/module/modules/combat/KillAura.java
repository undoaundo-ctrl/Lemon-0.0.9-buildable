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
import net.minecraft.util.EnumHand;

@Module.Declaration(name = "KillAura", category = Category.Combat)
public class KillAura extends Module {
   DoubleSetting range = this.registerDouble("Range", 4.0, 1.0, 6.0);
   BooleanSetting rotate = this.registerBoolean("Rotate", true);
   BooleanSetting swing = this.registerBoolean("Swing", true);
   BooleanSetting players = this.registerBoolean("Players", true);
   BooleanSetting hostile = this.registerBoolean("Hostile Mobs", true);
   BooleanSetting passive = this.registerBoolean("Passive Animals", true);
   BooleanSetting other = this.registerBoolean("Other Entities", false);
   BooleanSetting ignoreFriends = this.registerBoolean("Ignore Friends", true);

   private Entity target = null;

   @Override
   protected void onEnable() {
      SpoofRotationUtil.ROTATION_UTIL.onEnable();
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

      // Attack on cooldown
      if (mc.player.getCooledAttackStrength(0.0f) >= 1.0f) {
         mc.playerController.attackEntity(mc.player, this.target);
         if (this.swing.getValue()) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
         }
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
