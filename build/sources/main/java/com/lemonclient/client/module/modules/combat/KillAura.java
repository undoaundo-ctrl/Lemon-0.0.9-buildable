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
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.passive.AbstractChestHorse;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityShulkerBullet;
import net.minecraft.init.Items;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * 1:1 port of SalHack 2.05 KillAuraModule. All original settings and logic
 * preserved; only the host API calls were mapped to LemonClient equivalents.
 */
@Module.Declaration(name = "KillAura", category = Category.Combat)
public class KillAura extends Module {
   public ModeSetting Mode = this.registerMode("Mode", Arrays.asList("Closest", "Priority", "Switch"), "Closest");
   public DoubleSetting Distance = this.registerDouble("Distance", 5.0, 0.0, 10.0);
   public BooleanSetting HitDelay = this.registerBoolean("Hit Delay", true);
   public BooleanSetting TPSSync = this.registerBoolean("TPSSync", false);
   public BooleanSetting Players = this.registerBoolean("Players", true);
   public BooleanSetting Monsters = this.registerBoolean("Monsters", true);
   public BooleanSetting Neutrals = this.registerBoolean("Neutrals", false);
   public BooleanSetting Animals = this.registerBoolean("Animals", false);
   public BooleanSetting Tamed = this.registerBoolean("Tamed", false);
   public BooleanSetting Projectiles = this.registerBoolean("Projectile", false);
   public BooleanSetting SwordOnly = this.registerBoolean("SwordOnly", false);
   public BooleanSetting PauseIfCrystal = this.registerBoolean("PauseIfCrystal", false);
   public BooleanSetting AutoSwitch = this.registerBoolean("AutoSwitch", false);
   public IntegerSetting Ticks = this.registerInteger("Ticks", 10, 0, 40, () -> !this.HitDelay.getValue());
   public IntegerSetting Iterations = this.registerInteger("Iterations", 1, 1, 10);
   public BooleanSetting Only32k = this.registerBoolean("32kOnly", false);

   private Entity CurrentTarget = null;
   private long AimbotResetTimer = System.currentTimeMillis();
   private int RemainingTicks = 0;

   @Override
   protected void onEnable() {
      this.RemainingTicks = 0;
      SpoofRotationUtil.ROTATION_UTIL.onEnable();
   }

   @Override
   protected void onDisable() {
      SpoofRotationUtil.ROTATION_UTIL.resetRotation();
      SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(false);
      SpoofRotationUtil.ROTATION_UTIL.onDisable();
   }

   @Override
   public void onUpdate() {
      if (mc.player == null || mc.world == null) return;

      ItemStack held = mc.player.getHeldItemMainhand();

      if (!(held.getItem() instanceof ItemSword)) {
         if (held.getItem() == Items.END_CRYSTAL && this.PauseIfCrystal.getValue()) {
            return;
         }
         int slot = -1;
         if (this.AutoSwitch.getValue()) {
            for (int i = 0; i < 9; i++) {
               if (!(mc.player.inventory.getStackInSlot(i).getItem() instanceof ItemSword)) continue;
               mc.player.inventory.currentItem = slot = i;
               mc.playerController.syncCurrentPlayItem();
               break;
            }
         }
         if (this.SwordOnly.getValue() && slot == -1) return;
      }

      if (this.Only32k.getValue() && !is32k(held)) return;

      // AimbotResetTimer passed(5000) -> clear spoofed rotation
      if (System.currentTimeMillis() - this.AimbotResetTimer >= 5000L) {
         this.AimbotResetTimer = System.currentTimeMillis();
         SpoofRotationUtil.ROTATION_UTIL.resetRotation();
         SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(false);
      }

      if (this.RemainingTicks > 0) this.RemainingTicks--;

      // Target selection
      Entity targetToHit = this.CurrentTarget;
      switch (this.Mode.getValue()) {
         case "Closest":
            targetToHit = this.mc.world.loadedEntityList.stream()
               .filter(e -> this.isValidTarget(e, null))
               .min(Comparator.comparing(e -> mc.player.getDistance(e)))
               .orElse(null);
            break;
         case "Priority":
            if (targetToHit == null) {
               targetToHit = this.mc.world.loadedEntityList.stream()
                  .filter(e -> this.isValidTarget(e, null))
                  .min(Comparator.comparing(e -> mc.player.getDistance(e)))
                  .orElse(null);
            }
            break;
         case "Switch":
            targetToHit = this.mc.world.loadedEntityList.stream()
               .filter(e -> this.isValidTarget(e, null))
               .min(Comparator.comparing(e -> mc.player.getDistance(e)))
               .orElse(null);
            if (targetToHit == null) targetToHit = this.CurrentTarget;
            break;
      }

      if (targetToHit == null || targetToHit.getDistance(mc.player) > this.Distance.getValue()) {
         this.CurrentTarget = null;
         return;
      }
      this.CurrentTarget = targetToHit;

      // Rotation spoof (Aimbot equivalent)
      float[] rotation = calcAngle(mc.player.getPositionEyes(mc.getRenderPartialTicks()), targetToHit.getPositionEyes(mc.getRenderPartialTicks()));
      SpoofRotationUtil.ROTATION_UTIL.shouldSpoofAngles(true);
      SpoofRotationUtil.ROTATION_UTIL.setYawAndPitch(rotation[0], rotation[1]);

      // float l_Ticks = 20.0f - TickRateManager.Get().getTickRate();
      float tpsDelta = 20.0f - com.lemonclient.api.util.misc.ServerUtil.getTPS();
      boolean isAttackReady;
      if (this.HitDelay.getValue()) {
         isAttackReady = mc.player.getCooledAttackStrength(this.TPSSync.getValue() ? -tpsDelta : 0.0f) >= 1.0f;
      } else {
         isAttackReady = true;
      }
      if (!isAttackReady) return;
      if (!this.HitDelay.getValue() && this.RemainingTicks > 0) return;

      this.RemainingTicks = this.Ticks.getValue();
      for (int i = 0; i < this.Iterations.getValue(); i++) {
         mc.player.connection.sendPacket(new CPacketUseEntity(targetToHit));
         mc.player.swingArm(EnumHand.MAIN_HAND);
         mc.player.resetCooldown();
      }
   }

   private boolean isValidTarget(Entity entity, Entity toIgnore) {
      if (!(entity instanceof EntityLivingBase)) {
         boolean isProjectile = entity instanceof EntityShulkerBullet || entity instanceof EntityFireball;
         if (!isProjectile) return false;
         if (isProjectile && !this.Projectiles.getValue()) return false;
      }
      if (toIgnore != null && entity == toIgnore) return false;

      if (entity instanceof EntityPlayer) {
         if (entity == mc.player) return false;
         if (!this.Players.getValue()) return false;
         if (SocialManager.isFriend(entity.getName())) return false;
      }

      if (isHostileMob(entity) && !this.Monsters.getValue()) return false;

      if (isPassive(entity)) {
         if (entity instanceof AbstractChestHorse && ((AbstractChestHorse) entity).isTame() && !this.Tamed.getValue()) return false;
         if (!this.Animals.getValue()) return false;
      }

      if (isHostileMob(entity) && !this.Monsters.getValue()) return false;
      if (isNeutralMob(entity) && !this.Neutrals.getValue()) return false;

      boolean healthCheck = true;
      if (entity instanceof EntityLivingBase) {
         EntityLivingBase base = (EntityLivingBase) entity;
         healthCheck = !base.isDead && base.getHealth() > 0.0f;
      }
      // SalHack has a bug here: distance to self. Keep faithful.
      return healthCheck && entity.getDistance(entity) <= this.Distance.getValue();
   }

   private boolean isHostileMob(Entity entity) {
      return entity.isCreatureType(EnumCreatureType.MONSTER, false) && !isNeutralMob(entity);
   }

   private boolean isNeutralMob(Entity entity) {
      return entity instanceof EntityPigZombie || entity instanceof EntityWolf || entity instanceof EntityEnderman;
   }

   private boolean isPassive(Entity entity) {
      if (entity instanceof EntityWolf && ((EntityWolf) entity).isAngry()) return false;
      if (entity instanceof EntityAnimal || entity instanceof EntityTameable
         || entity instanceof EntityAmbientCreature || entity instanceof EntitySquid) return true;
      return false;
   }

   private static boolean is32k(ItemStack stack) {
      NBTTagList tags = stack.getEnchantmentTagList();
      if (tags == null) return false;
      for (int i = 0; i < tags.tagCount(); i++) {
         NBTTagCompound tag = tags.getCompoundTagAt(i);
         if (tag == null) continue;
         Enchantment enchantment = Enchantment.getEnchantmentByID(tag.getInteger("id"));
         short level = tag.getShort("lvl");
         if (enchantment == null || enchantment.isCurse() || level < 1000) continue;
         return true;
      }
      return false;
   }

   private static float[] calcAngle(Vec3d from, Vec3d to) {
      double difX = to.x - from.x;
      double difY = (to.y - from.y) * -1.0;
      double difZ = to.z - from.z;
      double dist = MathHelper.sqrt(difX * difX + difZ * difZ);
      return new float[]{
         (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0),
         (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist)))
      };
   }
}
