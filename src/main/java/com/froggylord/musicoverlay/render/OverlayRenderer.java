package com.froggylord.musicoverlay.render;

import com.froggylord.musicoverlay.MusicOverlay;
import com.froggylord.musicoverlay.config.ConfigManager;
import com.froggylord.musicoverlay.config.LayoutMode;
import com.froggylord.musicoverlay.config.OverlayConfig;
import com.froggylord.musicoverlay.media.NowPlaying;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the now-playing card. Everything is laid out relative to (0,0) and moved
 * into place with a single translate+scale on the GUI matrix, so the anchor,
 * free-drag offset and scale all compose cleanly regardless of GUI scale.
 */
public final class OverlayRenderer {
    private static final AlbumArt ART = new AlbumArt();

    private static final int PAD = 6;
    private static final int GAP = 4;

    // Last drawn card rectangle in real screen pixels — used by the reposition screen.
    private static int lastX, lastY, lastW, lastH;

    private OverlayRenderer() {}

    public static void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH, boolean hudHidden) {
        OverlayConfig c = ConfigManager.get();
        if (!c.enabled) return;
        if (c.hideWithHud && hudHidden) return;

        NowPlaying data = MusicOverlay.bridge().current();
        if (!shouldShow(c, data)) return;

        ART.sync(data);
        draw(g, font, c, data, screenW, screenH, true);
    }

    /** Shared by the HUD and the reposition preview; {@code live} gates the show rules. */
    public static void draw(GuiGraphicsExtractor g, Font font, OverlayConfig c, NowPlaying data,
                            int screenW, int screenH, boolean live) {
        float scale = c.scale;
        int boxW = boxWidth(c);
        List<String> titleLines = new ArrayList<>();
        List<String> artistLines = new ArrayList<>();
        int boxH = measure(font, c, data, boxW, titleLines, artistLines);

        int realW = Math.round(boxW * scale);
        int realH = Math.round(boxH * scale);
        int rx = c.anchor.baseX(screenW, realW) + c.offsetX;
        int ry = c.anchor.baseY(screenH, realH) + c.offsetY;
        lastX = rx; lastY = ry; lastW = realW; lastH = realH;

        g.pose().pushMatrix();
        g.pose().translate(rx, ry);
        g.pose().scale(scale, scale);
        drawCard(g, font, c, data, boxW, boxH, titleLines, artistLines);
        g.pose().popMatrix();
    }

    // ---- layout ----

    private static int boxWidth(OverlayConfig c) {
        return switch (c.layout) {
            case ART_ONLY -> artSize(c) + PAD * 2;
            case COMPACT -> 178;
            case MINIMAL -> 150;
            case TEXT_ONLY -> 172;
            case CARD -> 196;
        };
    }

    private static int artSize(OverlayConfig c) {
        return switch (c.layout) {
            case ART_ONLY -> 56;
            case COMPACT -> 20;
            default -> 42;
        };
    }

    private static boolean showsArt(OverlayConfig c) {
        return c.showAlbumArt && c.layout.showsArt();
    }

    /** Computes total height and fills in the wrapped title/artist lines. */
    private static int measure(Font font, OverlayConfig c, NowPlaying data, int boxW,
                               List<String> titleOut, List<String> artistOut) {
        int lh = font.lineHeight + 1;

        if (c.layout == LayoutMode.ART_ONLY) {
            return artSize(c) + PAD * 2;
        }
        if (c.layout == LayoutMode.COMPACT) {
            return artSize(c) + PAD * 2 - 4;
        }

        int art = showsArt(c) ? artSize(c) : 0;
        int textW = boxW - PAD * 2 - (art > 0 ? art + PAD : 0);

        int h = PAD;
        if (c.showTitle) {
            titleOut.addAll(wrap(font, data.title(), textW, c.layout == LayoutMode.MINIMAL ? 1 : 2));
            h += titleOut.size() * lh;
        }
        if (c.showArtist && c.layout != LayoutMode.MINIMAL && !data.artist().isEmpty()) {
            artistOut.addAll(wrap(font, data.artist(), textW, 1));
            h += artistOut.size() * lh;
        }
        if (c.showAlbum && !data.album().isEmpty()) h += lh;
        if (c.showSourceApp && !data.sourceApp().isEmpty()) h += lh;
        if (c.showProgressBar) h += GAP + 4;
        if (c.showTimes && c.layout != LayoutMode.MINIMAL) h += lh;
        h += PAD;

        // Never shorter than the album art.
        if (art > 0) h = Math.max(h, art + PAD * 2);
        return h;
    }

    // ---- drawing ----

    private static void drawCard(GuiGraphicsExtractor g, Font font, OverlayConfig c, NowPlaying data,
                                 int boxW, int boxH, List<String> titleLines, List<String> artistLines) {
        int bg = Draw.withOpacity(c.backgroundColor, c.opacity);
        Draw.roundedRect(g, 0, 0, boxW, boxH, bg, c.roundedCorners);
        if (c.showBorder) {
            int bc = Draw.withOpacity(c.borderColor, c.opacity);
            g.fill(0, 0, boxW, 1, bc);
            g.fill(0, boxH - 1, boxW, boxH, bc);
            g.fill(0, 0, 1, boxH, bc);
            g.fill(boxW - 1, 0, boxW, boxH, bc);
        }

        boolean art = showsArt(c);
        int artSize = artSize(c);
        if (art) {
            int ax = PAD, ay = (boxH - artSize) / 2;
            if (ART.ready()) {
                g.blit(RenderPipelines.GUI_TEXTURED, ART.textureId(), ax, ay, 0f, 0f,
                        artSize, artSize, ART.width(), ART.height(), ART.width(), ART.height());
            } else {
                g.fill(ax, ay, ax + artSize, ay + artSize, Draw.withOpacity(0xFF2A2A3E, c.opacity));
                String note = "♪";
                g.text(font, note, ax + artSize / 2 - font.width(note) / 2,
                        ay + artSize / 2 - font.lineHeight / 2,
                        Draw.withOpacity(c.accentColor, c.opacity), false);
            }
        }
        if (c.layout == LayoutMode.ART_ONLY) return;

        int textX = art ? PAD + artSize + PAD : PAD;
        int textW = boxW - textX - PAD;
        int lh = font.lineHeight + 1;

        if (c.layout == LayoutMode.COMPACT) {
            String line = data.title();
            if (!data.artist().isEmpty()) line += " · " + data.artist();
            int ty = (boxH - font.lineHeight) / 2;
            g.text(font, Draw.fit(font, line, textW), textX, ty,
                    Draw.withOpacity(c.titleColor, c.opacity), c.textShadow);
            return;
        }

        int y = PAD;
        for (String line : titleLines) {
            g.text(font, line, textX, y, Draw.withOpacity(c.titleColor, c.opacity), c.textShadow);
            y += lh;
        }
        for (String line : artistLines) {
            g.text(font, line, textX, y, Draw.withOpacity(c.artistColor, c.opacity), c.textShadow);
            y += lh;
        }
        if (c.showAlbum && !data.album().isEmpty()) {
            g.text(font, Draw.fit(font, data.album(), textW), textX, y,
                    Draw.withOpacity(c.artistColor, c.opacity), c.textShadow);
            y += lh;
        }
        if (c.showSourceApp && !data.sourceApp().isEmpty()) {
            g.text(font, Draw.fit(font, data.sourceApp(), textW), textX, y,
                    Draw.withOpacity(c.timeColor, c.opacity), c.textShadow);
            y += lh;
        }

        if (c.showProgressBar) {
            y += GAP;
            drawProgress(g, c, data, textX, y, textW);
            y += 4;
        }
        if (c.showTimes && c.layout != LayoutMode.MINIMAL) {
            y += 2;
            int col = Draw.withOpacity(c.timeColor, c.opacity);
            g.text(font, data.formattedEstimatedPosition(), textX, y, col, c.textShadow);
            String dur = data.formattedDuration();
            g.text(font, dur, textX + textW - font.width(dur), y, col, c.textShadow);
        }
    }

    private static void drawProgress(GuiGraphicsExtractor g, OverlayConfig c, NowPlaying data,
                                     int x, int y, int width) {
        int track = Draw.withOpacity(c.progressTrackColor, c.opacity);
        int fill = Draw.withOpacity(c.accentColor, c.opacity);
        g.fill(x, y, x + width, y + 4, track);
        int filled = Math.max(1, Math.round(width * data.estimatedProgress()));
        g.fill(x, y, x + filled, y + 4, fill);
        if (c.showProgressHead) {
            int hx = x + filled;
            g.fill(hx - 2, y - 2, hx + 2, y + 6, fill);
        }
    }

    // ---- visibility + text ----

    private static boolean shouldShow(OverlayConfig c, NowPlaying data) {
        if (!data.hasData()) return false;
        return switch (c.visibility) {
            case ALWAYS -> true;
            case WHEN_PLAYING -> data.isPlaying();
            case WHEN_RECENT -> data.isPlaying()
                    || (System.currentTimeMillis() - MusicOverlay.bridge().lastPlayingMs())
                        < c.recentLingerSeconds * 1000L;
        };
    }

    private static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        if (font.width(text) <= maxWidth) { lines.add(text); return lines; }

        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(test) > maxWidth) {
                if (cur.isEmpty()) {
                    lines.add(Draw.fit(font, word, maxWidth));
                    cur = new StringBuilder();
                } else {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                }
                if (lines.size() == maxLines) {
                    // Squeeze the remainder into the final line with an ellipsis.
                    if (!cur.isEmpty()) {
                        lines.set(maxLines - 1, Draw.fit(font, lines.get(maxLines - 1) + " " + cur, maxWidth));
                    }
                    return lines;
                }
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty() && lines.size() < maxLines) lines.add(cur.toString());
        return lines;
    }

    // Accessors for the reposition screen.
    public static int lastX() { return lastX; }
    public static int lastY() { return lastY; }
    public static int lastW() { return lastW; }
    public static int lastH() { return lastH; }
}
