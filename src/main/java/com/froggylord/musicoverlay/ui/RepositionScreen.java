package com.froggylord.musicoverlay.ui;

import com.froggylord.musicoverlay.MusicOverlay;
import com.froggylord.musicoverlay.config.ConfigManager;
import com.froggylord.musicoverlay.config.OverlayConfig;
import com.froggylord.musicoverlay.media.NowPlaying;
import com.froggylord.musicoverlay.render.OverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Drag-to-place screen. Renders a live preview of the overlay and lets the user
 * drag it anywhere; the movement is stored as the free-drag offset on top of the
 * current anchor, so the card keeps its corner behaviour afterwards.
 */
public class RepositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;

    public RepositionScreen(Screen parent) {
        super(Component.literal("Reposition Overlay"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);

        OverlayConfig c = ConfigManager.get();
        NowPlaying data = MusicOverlay.bridge().current();
        if (!data.hasData()) data = NowPlaying.sample();
        OverlayRenderer.draw(g, this.font, c, data, this.width, this.height, false);

        // Highlight the draggable area.
        int x = OverlayRenderer.lastX(), y = OverlayRenderer.lastY();
        int x2 = x + OverlayRenderer.lastW(), y2 = y + OverlayRenderer.lastH();
        g.fill(x - 2, y - 2, x2 + 2, y - 1, 0xFF1DB954);
        g.fill(x - 2, y2 + 1, x2 + 2, y2 + 2, 0xFF1DB954);
        g.fill(x - 2, y - 2, x - 1, y2 + 2, 0xFF1DB954);
        g.fill(x2 + 1, y - 2, x2 + 2, y2 + 2, 0xFF1DB954);

        String hint = "Drag the card to move it · " + c.anchor.display() + " anchor · Esc to finish";
        g.text(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 24, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int x = OverlayRenderer.lastX(), y = OverlayRenderer.lastY();
            int x2 = x + OverlayRenderer.lastW(), y2 = y + OverlayRenderer.lastH();
            if (event.x() >= x - 2 && event.x() <= x2 + 2 && event.y() >= y - 2 && event.y() <= y2 + 2) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            OverlayConfig c = ConfigManager.get();
            c.offsetX += (int) Math.round(dragX);
            c.offsetY += (int) Math.round(dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            ConfigManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
