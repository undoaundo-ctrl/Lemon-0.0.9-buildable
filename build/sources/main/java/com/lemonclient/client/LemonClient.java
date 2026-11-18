package com.lemonclient.client;

import com.lemonclient.api.config.LoadConfig;
import com.lemonclient.api.util.chat.notification.NotificationType;
import com.lemonclient.api.util.chat.notification.NotificationsManager;
import com.lemonclient.api.util.chat.notification.notifications.BottomRightNotification;
import com.lemonclient.api.util.font.CFontRenderer;
import com.lemonclient.api.util.log4j.Fixer;
import com.lemonclient.api.util.misc.IconUtil;
import com.lemonclient.api.util.misc.ServerUtil;
import com.lemonclient.api.util.player.PositionUtil;
import com.lemonclient.api.util.player.SpeedUtil;
import com.lemonclient.api.util.render.CapeUtil;
import com.lemonclient.client.clickgui.LemonClientGUI;
import com.lemonclient.client.command.CommandManager;
import com.lemonclient.client.manager.ManagerLoader;
import com.lemonclient.client.module.ModuleManager;
import java.awt.Font;
import java.io.InputStream;
import java.nio.ByteBuffer;
import me.zero.alpine.bus.EventBus;
import me.zero.alpine.bus.EventManager;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.util.Util.EnumOS;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.Display;

@Mod(modid = "lemonclient", name = "Lemon Client", version = "v0.0.9")
public class LemonClient {
   public static final String MODNAME = "Lemon Client";
   public static final String MODID = "lemonclient";
   public static final String MODVER = "v0.0.9";
   public static String Ver = "009";
   public static final Logger LOGGER = LogManager.getLogger("Lemon Client");
   public static final EventBus EVENT_BUS = new EventManager();
   public static PositionUtil positionUtil;
   public static ServerUtil serverUtil;
   public static SpeedUtil speedUtil;
   public static boolean isMe;
   @Instance
   public static LemonClient INSTANCE;
   public CFontRenderer cFontRenderer;
   public LemonClientGUI gameSenseGUI;

   @EventHandler
   public void construct(FMLConstructionEvent event) {
      try {
         Fixer.disableJndiManager();
      } catch (Exception var3) {
         throw new ExceptionInInitializerError(var3);
      }
   }

   @EventHandler
   public void preInit(FMLPreInitializationEvent event) {
      Fixer.doRuntimeTest(event.getModLog());
   }

   public LemonClient() {
      INSTANCE = this;
   }

   @EventHandler
   public void init(FMLInitializationEvent event) {
      LOGGER.info("Starting up Lemon Client v0.0.9!");
      this.startClient();
      LOGGER.info("Finished initialization for Lemon Client v0.0.9!");
      NotificationType type = NotificationType.WELCOME;
      int length = 20;
      String msg = "You are on the latest version";
      NotificationsManager.show(new BottomRightNotification(type, "LemonClient", msg, length));
      CapeUtil.init();
      Display.setTitle("Lemon Client v0.0.9");
      setWindowIcon();
   }

   private void startClient() {
      this.cFontRenderer = new CFontRenderer(new Font("Comic Sans Ms", 0, 17), false, true);
      LoadConfig.init();
      ModuleManager.init();
      CommandManager.init();
      ManagerLoader.init();
      this.gameSenseGUI = new LemonClientGUI();
      LoadConfig.init();
      positionUtil = new PositionUtil();
      serverUtil = new ServerUtil();
      speedUtil = new SpeedUtil();
      INSTANCE.gameSenseGUI.refresh();
   }

   public static void setWindowIcon() {
      if (Util.getOSType() != EnumOS.OSX) {
         try (
            InputStream inputStream16x = Minecraft.class.getResourceAsStream("/assets/lemonclient/icons/icon-16x.png");
            InputStream inputStream32x = Minecraft.class.getResourceAsStream("/assets/lemonclient/icons/icon-32x.png");
         ) {
            ByteBuffer[] icons = new ByteBuffer[]{IconUtil.INSTANCE.readImageToBuffer(inputStream32x), IconUtil.INSTANCE.readImageToBuffer(inputStream32x)};
            Display.setIcon(icons);
         } catch (Exception var32) {
         }
      }
   }
}
