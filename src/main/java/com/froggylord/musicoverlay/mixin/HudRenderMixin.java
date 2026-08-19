package com.froggylord.musicoverlay.mixin;

import com.froggylord.musicoverlay.render.OverlayRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the overlay at the tail of the HUD render pass. 26.x routes GUI drawing
 * through {@code Hud#extractRenderState}, which hands us the {@link GuiGraphicsExtractor}.
 */
@Mixin(Hud.class)
public abstract class HudRenderMixin {
    @Shadow
    public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void musicoverlay$renderOverlay(GuiGraphicsExtractor g, DeltaTracker tracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        OverlayRenderer.render(g, mc.font, w, h, isHidden());
    }
}
