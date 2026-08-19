package com.froggylord.musicoverlay.mixin;

import com.froggylord.musicoverlay.render.OverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.x variant. Here the HUD's {@code extractRenderState} still lives on
 * {@code Gui} (26.2 split it into a dedicated {@code Hud} class). The F1
 * hide-HUD state isn't exposed on this class, so that check is a no-op on 26.1.
 */
@Mixin(Gui.class)
public class HudRenderMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void musicoverlay$renderOverlay(GuiGraphicsExtractor g, DeltaTracker tracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        OverlayRenderer.render(g, mc.font, w, h, false);
    }
}
