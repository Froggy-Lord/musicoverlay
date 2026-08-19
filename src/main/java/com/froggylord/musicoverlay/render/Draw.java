package com.froggylord.musicoverlay.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers shared by the overlay and its screens. */
public final class Draw {
    private Draw() {}

    /**
     * Rectangle with the four corner pixels shaved off for a soft, rounded feel.
     * Cheap (three fills) and reads well at the sizes the overlay uses.
     */
    public static void roundedRect(GuiGraphicsExtractor g, int x, int y, int x2, int y2, int argb, boolean rounded) {
        if (!rounded) {
            g.fill(x, y, x2, y2, argb);
            return;
        }
        g.fill(x + 1, y, x2 - 1, y2, argb);
        g.fill(x, y + 1, x + 1, y2 - 1, argb);
        g.fill(x2 - 1, y + 1, x2, y2 - 1, argb);
    }

    /** Multiply a packed colour's alpha by a 0..1 opacity factor. */
    public static int withOpacity(int argb, float opacity) {
        int a = (argb >>> 24) & 0xFF;
        int scaled = Math.round(a * clamp01(opacity));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /** Trim a string with an ellipsis until it fits maxWidth. */
    public static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (font.width(text) <= maxWidth) return text;
        String out = text;
        while (!out.isEmpty() && font.width(out + "…") > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "…";
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
