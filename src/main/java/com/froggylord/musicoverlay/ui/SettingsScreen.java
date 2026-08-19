package com.froggylord.musicoverlay.ui;

import com.froggylord.musicoverlay.config.ConfigManager;
import com.froggylord.musicoverlay.config.OverlayConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
// SpotifyScreen is in this same package.

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Tabbed configuration screen. Everything the JSON exposes is reachable here:
 * placement, scale and opacity, which elements show, the lyric options, and the
 * full colour palette via hex fields. Changes apply and save immediately.
 */
public class SettingsScreen extends Screen {
    private enum Tab { GENERAL, LAYOUT, ELEMENTS, LYRICS, COLORS }

    private final Screen parent;
    private Tab tab = Tab.GENERAL;

    // labels drawn next to hex colour fields on the Colors tab
    private final List<int[]> labelPositions = new ArrayList<>();
    private final List<String> labelTexts = new ArrayList<>();

    public SettingsScreen(Screen parent) {
        super(Component.literal("Music Overlay"));
        this.parent = parent;
    }

    private OverlayConfig cfg() {
        return ConfigManager.get();
    }

    @Override
    protected void init() {
        labelPositions.clear();
        labelTexts.clear();

        // Tab bar
        int tabs = Tab.values().length;
        int tabW = 88;
        int totalW = tabW * tabs + 4 * (tabs - 1);
        int tx = (this.width - totalW) / 2;
        for (Tab t : Tab.values()) {
            Tab target = t;
            String label = (t == tab ? "▸ " : "") + name(t);
            addRenderableWidget(Button.builder(Component.literal(label), b -> { tab = target; rebuildWidgets(); })
                    .bounds(tx, 30, tabW, 18).build());
            tx += tabW + 4;
        }

        int y = 62;
        switch (tab) {
            case GENERAL -> buildGeneral(y);
            case LAYOUT -> buildLayout(y);
            case ELEMENTS -> buildElements(y);
            case LYRICS -> buildLyrics(y);
            case COLORS -> buildColors(y);
        }

        // Footer
        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), b -> {
            cfg().resetToDefaults();
            ConfigManager.save();
            rebuildWidgets();
        }).bounds(this.width / 2 - 154, this.height - 28, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 + 4, this.height - 28, 150, 20).build());
    }

    // ---- tabs ----

    private void buildGeneral(int y) {
        OverlayConfig c = cfg();
        int x = col(0), step = 24;
        toggle(x, y, "Overlay", () -> c.enabled, v -> c.enabled = v);
        cycle(col(1), y, "Show", c.visibility.display(), () -> { c.visibility = c.visibility.next(); });
        y += step;
        slider(x, y, 1, 60, c.recentLingerSeconds, v -> (int) v + "s linger", v -> c.recentLingerSeconds = (int) v);
        slider(col(1), y, 100, 1000, c.pollIntervalMs, v -> "Poll " + (int) v + "ms", v -> c.pollIntervalMs = (int) v);
        y += step;
        toggle(x, y, "Hide with HUD (F1)", () -> c.hideWithHud, v -> c.hideWithHud = v);
        toggle(col(1), y, "Media controls", () -> c.enableMediaControls, v -> c.enableMediaControls = v);
        y += step;
        toggle(x, y, "Fetch lyrics online", () -> c.fetchLyricsOnline, v -> c.fetchLyricsOnline = v);
        addRenderableWidget(Button.builder(Component.literal("Spotify controls…"),
                        b -> this.minecraft.setScreenAndShow(new SpotifyScreen(this)))
                .bounds(col(1), y, 150, 20).build());
    }

    private void buildLayout(int y) {
        OverlayConfig c = cfg();
        int x = col(0), step = 24;
        cycle(x, y, "Layout", c.layout.display(), () -> { c.layout = c.layout.next(); });
        cycle(col(1), y, "Anchor", c.anchor.display(), () -> { c.anchor = c.anchor.next(); });
        y += step;
        slider(x, y, 40, 300, Math.round(c.scale * 100), v -> "Scale " + (int) v + "%", v -> c.scale = (float) (v / 100.0));
        slider(col(1), y, 0, 100, Math.round(c.opacity * 100), v -> "Opacity " + (int) v + "%", v -> c.opacity = (float) (v / 100.0));
        y += step;
        toggle(x, y, "Rounded corners", () -> c.roundedCorners, v -> c.roundedCorners = v);
        toggle(col(1), y, "Border", () -> c.showBorder, v -> c.showBorder = v);
        y += step;
        toggle(x, y, "Text shadow", () -> c.textShadow, v -> c.textShadow = v);
        addRenderableWidget(Button.builder(Component.literal("Reposition (drag)…"),
                        b -> this.minecraft.setScreenAndShow(new RepositionScreen(this)))
                .bounds(col(1), y, 150, 20).build());
    }

    private void buildElements(int y) {
        OverlayConfig c = cfg();
        int step = 24;
        toggle(col(0), y, "Album art", () -> c.showAlbumArt, v -> c.showAlbumArt = v);
        toggle(col(1), y, "Title", () -> c.showTitle, v -> c.showTitle = v);
        y += step;
        toggle(col(0), y, "Artist", () -> c.showArtist, v -> c.showArtist = v);
        toggle(col(1), y, "Album", () -> c.showAlbum, v -> c.showAlbum = v);
        y += step;
        toggle(col(0), y, "Progress bar", () -> c.showProgressBar, v -> c.showProgressBar = v);
        toggle(col(1), y, "Progress head", () -> c.showProgressHead, v -> c.showProgressHead = v);
        y += step;
        toggle(col(0), y, "Times", () -> c.showTimes, v -> c.showTimes = v);
        toggle(col(1), y, "Source app", () -> c.showSourceApp, v -> c.showSourceApp = v);
    }

    private void buildLyrics(int y) {
        OverlayConfig c = cfg();
        int x = col(0), step = 24;
        toggle(x, y, "Show lyrics", () -> c.showLyrics, v -> c.showLyrics = v);
        cycle(col(1), y, "Position", c.lyricsPlacement.display(), () -> { c.lyricsPlacement = c.lyricsPlacement.next(); });
        y += step;
        slider(x, y, 1, 7, c.lyricsLines, v -> (int) v + " lines", v -> c.lyricsLines = (int) v);
        toggle(col(1), y, "Centered", () -> c.lyricsCentered, v -> c.lyricsCentered = v);
        y += step;
        slider(x, y, -5000, 5000, c.lyricsOffsetMs, v -> "Offset " + (int) v + "ms", v -> c.lyricsOffsetMs = (int) v);
    }

    private void buildColors(int y) {
        OverlayConfig c = cfg();
        int step = 24;
        hex(y, "Background", c.backgroundColor, v -> c.backgroundColor = v); y += step;
        hex(y, "Accent", c.accentColor, v -> c.accentColor = v); y += step;
        hex(y, "Title", c.titleColor, v -> c.titleColor = v); y += step;
        hex(y, "Artist / album", c.artistColor, v -> c.artistColor = v); y += step;
        hex(y, "Lyric active", c.lyricsActiveColor, v -> c.lyricsActiveColor = v); y += step;
        hex(y, "Lyric inactive", c.lyricsInactiveColor, v -> c.lyricsInactiveColor = v);
    }

    // ---- widget helpers ----

    private int col(int i) {
        return this.width / 2 + (i == 0 ? -154 : 4);
    }

    private void toggle(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set) {
        Button b = Button.builder(Component.literal(label + ": " + (get.getAsBoolean() ? "On" : "Off")), btn -> {
            boolean nv = !get.getAsBoolean();
            set.accept(nv);
            ConfigManager.save();
            btn.setMessage(Component.literal(label + ": " + (nv ? "On" : "Off")));
        }).bounds(x, y, 150, 20).build();
        addRenderableWidget(b);
    }

    private void cycle(int x, int y, String label, String current, Runnable onNext) {
        Button b = Button.builder(Component.literal(label + ": " + current), btn -> {
            onNext.run();
            ConfigManager.save();
            rebuildWidgets();
        }).bounds(x, y, 150, 20).build();
        addRenderableWidget(b);
    }

    private void slider(int x, int y, double min, double max, double current,
                        java.util.function.DoubleFunction<String> display, java.util.function.DoubleConsumer apply) {
        addRenderableWidget(new OptionSlider(x, y, 150, 20, min, max, current, display, apply));
    }

    private void hex(int y, String label, int current, Consumer<Integer> apply) {
        int x = this.width / 2 - 40;
        labelPositions.add(new int[]{x - 8, y + 6});
        labelTexts.add(label);
        EditBox box = new EditBox(this.font, x, y, 120, 18, Component.literal(label));
        box.setMaxLength(9);
        box.setValue(String.format("#%08X", current));
        box.setResponder(text -> {
            Integer parsed = parseHex(text);
            if (parsed != null) {
                apply.accept(parsed);
                ConfigManager.save();
            }
        });
        addRenderableWidget(box);
    }

    // ---- rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(this.font, this.title.getString(),
                this.width / 2 - this.font.width(this.title.getString()) / 2, 14, 0xFFFFFFFF, true);
        for (int i = 0; i < labelPositions.size(); i++) {
            int[] p = labelPositions.get(i);
            String t = labelTexts.get(i);
            g.text(this.font, t, p[0] - this.font.width(t), p[1], 0xFFB0B0C0, false);
        }
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    // ---- utils ----

    private static String name(Tab t) {
        return switch (t) {
            case GENERAL -> "General";
            case LAYOUT -> "Layout";
            case ELEMENTS -> "Elements";
            case LYRICS -> "Lyrics";
            case COLORS -> "Colors";
        };
    }

    /** Parse #AARRGGBB / AARRGGBB / #RRGGBB / RRGGBB into a packed ARGB int. */
    private static Integer parseHex(String text) {
        if (text == null) return null;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 6) s = "FF" + s;
        if (s.length() != 8) return null;
        try {
            return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // kept for symmetry with future preview work
    @SuppressWarnings("unused")
    private Supplier<OverlayConfig> configSupplier() {
        return this::cfg;
    }
}
