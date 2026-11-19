package com.lemonclient.client.module.modules.gui;

import com.lemonclient.api.event.events.Render2DEvent;
import com.lemonclient.api.setting.values.BooleanSetting;
import com.lemonclient.api.setting.values.IntegerSetting;
import com.lemonclient.api.setting.values.ModeSetting;
import com.lemonclient.api.setting.values.StringSetting;
import com.lemonclient.api.util.font.CFontRenderer;
import com.lemonclient.client.LemonClient;
import com.lemonclient.client.module.Category;
import com.lemonclient.client.module.Module;
import java.awt.Font;
import java.util.Arrays;

/**
 * Visible GUI settings for the client-wide custom font. Changes apply live.
 */
@Module.Declaration(name = "Custom Font", category = Category.GUI, enabled = true, drawn = false)
public class CustomFont extends Module {
   public BooleanSetting customFont = this.registerBoolean("Custom Font", true);
   public ModeSetting fontName = this.registerMode(
      "Font Name",
      Arrays.asList(
         "Comic Sans Ms", "Arial", "Tahoma", "Verdana", "Segoe UI", "Calibri", "Roboto", "Helvetica", "Dialog", "Monospaced", "Custom"
      ),
      "Comic Sans Ms"
   );
   public StringSetting customName = this.registerString("Custom Name", "Arial", () -> this.fontName.getValue().equals("Custom"));
   public IntegerSetting fontSize = this.registerInteger("Font Size", 17, 8, 40);
   public BooleanSetting antiAlias = this.registerBoolean("Anti Alias", false);
   public BooleanSetting metrics = this.registerBoolean("Fractional Metrics", true);

   private boolean initialized = false;
   private String appliedName = "";
   private int appliedSize = -1;
   private boolean appliedAlias;
   private boolean appliedMetrics;
   private boolean lastCustomFont = true;

   @Override
   protected void onEnable() {
      this.initialized = false;
      if (ColorMain.INSTANCE != null) {
         this.customFont.setValue(ColorMain.INSTANCE.customFont.getValue());
      }
      this.lastCustomFont = this.customFont.getValue();
   }

   @Override
   public void onRender2D(Render2DEvent event) {
      if (ColorMain.INSTANCE != null) {
         // Adopt changes made from the Colors module, otherwise push ours there
         if (ColorMain.INSTANCE.customFont.getValue() != this.lastCustomFont) {
            this.customFont.setValue(ColorMain.INSTANCE.customFont.getValue());
         }
         this.lastCustomFont = this.customFont.getValue();
         ColorMain.INSTANCE.customFont.setValue(this.customFont.getValue());
      }
      this.apply();
   }

   private void apply() {
      CFontRenderer renderer = LemonClient.INSTANCE != null ? LemonClient.INSTANCE.cFontRenderer : null;
      if (renderer == null) return;

      if (!this.initialized) {
         // Pick up whatever font is already loaded (default or from CustomFont.json)
         String current = renderer.getFontName();
         if (current != null && this.fontName.getModes().contains(current)) {
            this.fontName.setValue(current);
         } else if (current != null) {
            this.fontName.setValue("Custom");
            this.customName.setText(current);
         }
         this.fontSize.setValue(renderer.getFontSize());
         this.antiAlias.setValue(renderer.getAntiAlias());
         this.metrics.setValue(renderer.getFractionalMetrics());
         this.appliedName = current == null ? "" : current;
         this.appliedSize = renderer.getFontSize();
         this.appliedAlias = renderer.getAntiAlias();
         this.appliedMetrics = renderer.getFractionalMetrics();
         this.initialized = true;
      }

      String name = this.fontName.getValue().equals("Custom") ? this.customName.getText() : this.fontName.getValue();
      if (name == null || name.trim().isEmpty()) return;
      name = name.trim();

      int size = this.fontSize.getValue();
      boolean alias = this.antiAlias.getValue();
      boolean fractionalMetrics = this.metrics.getValue();

      if (name.equals(this.appliedName)
         && size == this.appliedSize
         && alias == this.appliedAlias
         && fractionalMetrics == this.appliedMetrics) {
         return;
      }

      CFontRenderer newRenderer = new CFontRenderer(new Font(name, Font.PLAIN, size), alias, fractionalMetrics);
      newRenderer.setAntiAlias(alias);
      newRenderer.setFractionalMetrics(fractionalMetrics);
      newRenderer.setFontName(name);
      newRenderer.setFontSize(size);
      LemonClient.INSTANCE.cFontRenderer = newRenderer;

      this.appliedName = name;
      this.appliedSize = size;
      this.appliedAlias = alias;
      this.appliedMetrics = fractionalMetrics;
   }
}
