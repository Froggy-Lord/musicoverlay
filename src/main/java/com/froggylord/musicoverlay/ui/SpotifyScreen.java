package com.froggylord.musicoverlay.ui;

import com.froggylord.musicoverlay.MusicOverlay;
import com.froggylord.musicoverlay.render.Draw;
import com.froggylord.musicoverlay.spotify.SpotifyConfig;
import com.froggylord.musicoverlay.spotify.SpotifyConfigManager;
import com.froggylord.musicoverlay.spotify.SpotifyManager;
import com.froggylord.musicoverlay.spotify.SpotifyPlaylist;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The Spotify control panel. When not connected it shows a plain-language
 * walkthrough (what the feature does, that the login is stored only on this PC,
 * and that playback control needs Premium) plus the setup steps. Once connected
 * it becomes the playlist browser and track controls. Reached from the settings
 * screen, the Spotify keybind, or any Spotify action while not set up.
 */
public class SpotifyScreen extends Screen {
    private final Screen parent;
    private EditBox clientIdBox;
    private String status = "";
    private boolean requestedPlaylists = false;

    // playlist list geometry (connected view)
    private int listTop, listBottom, listX, listW;
    private static final int ROW_H = 22;
    private int scroll = 0;

    public SpotifyScreen(Screen parent) {
        super(Component.literal("Spotify"));
        this.parent = parent;
    }

    private SpotifyManager sp() { return MusicOverlay.spotify(); }
    private SpotifyConfig cfg() { return SpotifyConfigManager.get(); }

    @Override
    protected void init() {
        int cx = this.width / 2;

        if (sp().isConnected()) {
            initConnected(cx);
        } else {
            initWalkthrough(cx);
        }

        addRenderableWidget(Button.builder(Component.literal(sp().isConnected() ? "Done" : "Not now"),
                        b -> onClose())
                .bounds(cx - 50, this.height - 26, 100, 20).build());
    }

    private void initWalkthrough(int cx) {
        SpotifyConfig c = cfg();
        // Step 2 row: copy the redirect URI (button sits on the "Add Redirect URI" line).
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
                    if (this.minecraft != null) this.minecraft.keyboardHandler.setClipboard(c.redirectUri());
                    status = "Redirect URI copied to clipboard.";
                }).bounds(cx + 92, 145, 58, 18).build());

        // Step 3: Client ID field.
        clientIdBox = new EditBox(this.font, cx - 150, 205, 300, 18, Component.literal("Client ID"));
        clientIdBox.setMaxLength(64);
        clientIdBox.setHint(Component.literal("Paste your Spotify app Client ID here"));
        clientIdBox.setValue(c.clientId);
        clientIdBox.setResponder(v -> { c.clientId = v.trim(); SpotifyConfigManager.save(); });
        addRenderableWidget(clientIdBox);

        // Step 4: connect.
        addRenderableWidget(Button.builder(Component.literal("Connect Spotify"),
                        b -> sp().beginAuth(s -> this.status = s))
                .bounds(cx - 150, 236, 300, 20).build());
    }

    private void initConnected(int cx) {
        addRenderableWidget(Button.builder(Component.literal("Like"), b -> sp().toggleLikeCurrent())
                .bounds(cx - 150, 44, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Queue"), b -> sp().addCurrentToQueue())
                .bounds(cx - 74, 44, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("◀ Prev"), b -> sp().previous())
                .bounds(cx + 2, 44, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Skip ▶"), b -> sp().next())
                .bounds(cx + 78, 44, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"),
                        b -> sp().refreshPlaylists(this::rebuildWidgets))
                .bounds(cx - 150, 68, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Disconnect"), b -> {
                    sp().disconnect();
                    rebuildWidgets();
                }).bounds(cx + 2, 68, 148, 20).build());

        listX = cx - 150;
        listW = 300;
        listTop = 118;
        listBottom = this.height - 34;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        center(g, "§bSpotify", 16);
        if (sp().isConnected()) {
            renderConnected(g, mouseX, mouseY);
        } else {
            renderWalkthrough(g);
        }
    }

    private void renderWalkthrough(GuiGraphicsExtractor g) {
        int x = this.width / 2 - 150;

        line(g, "§fWhat this does", x, 30);
        line(g, "§7Browse playlists, add the current song, like, queue, skip,", x, 41);
        line(g, "§7and play whole playlists, all from in game.", x, 51);

        line(g, "§e♥ Stored on this PC only", x, 66);
        line(g, "§7Your login is saved locally in config/musicoverlay-spotify.json", x, 77);
        line(g, "§7and is only ever sent to Spotify. No server, nothing shared.", x, 87);

        line(g, "§6★ Spotify Premium needed for playback control", x, 102);
        line(g, "§7Skip, queue and play-a-playlist require Premium (Spotify's rule).", x, 113);
        line(g, "§7Browsing and adding to playlists work on a free account too.", x, 123);

        // Setup steps
        line(g, "§b1  §7Create an app at developer.spotify.com/dashboard", x, 133);
        line(g, "§b2  §7Add this Redirect URI to the app:", x, 147);
        line(g, "§a" + cfg().redirectUri(), x, 159);
        line(g, "§b3  §7Paste your Client ID below", x, 192);
        line(g, "§b4  §7Then click Connect", x, 224);

        if (!status.isBlank()) {
            g.text(this.font, "§7" + status, x, this.height - 42, 0xFFFFFFFF, false);
        }
    }

    private void renderConnected(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!requestedPlaylists) {
            requestedPlaylists = true;
            sp().refreshPlaylists(null);
            sp().refreshPlayback(null);
        }
        var pb = sp().cachedPlayback();
        String now = pb != null && pb.hasTrack()
                ? "Now: " + pb.title() + " · " + pb.artist() + (pb.isSaved() ? "  ♥" : "")
                : "Nothing playing (open Spotify on a device)";
        g.text(this.font, "§7" + Draw.fit(this.font, now, listW), listX, 100, 0xFFFFFFFF, false);
        drawPlaylists(g, mouseX, mouseY);
    }

    private void drawPlaylists(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<SpotifyPlaylist> lists = sp().cachedPlaylists();
        g.fill(listX, listTop, listX + listW, listBottom, 0x40000000);
        if (lists.isEmpty()) {
            g.text(this.font, "§7No playlists loaded yet…", listX + 6, listTop + 6, 0xFFFFFFFF, false);
            return;
        }
        String quickId = cfg().quickAddPlaylistId;
        int y = listTop - scroll;
        for (SpotifyPlaylist p : lists) {
            if (y + ROW_H >= listTop && y <= listBottom) {
                boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY < y + ROW_H;
                g.fill(listX, y, listX + listW, y + ROW_H, hover ? 0x3020C060 : 0x20FFFFFF);
                boolean isQuick = p.id().equals(quickId);
                g.text(this.font, isQuick ? "§e★" : "§8☆", listX + 6, y + 7, 0xFFFFFFFF, false);
                g.text(this.font, Draw.fit(this.font, p.name(), listW - 90), listX + 20, y + 3, 0xFFFFFFFF, false);
                g.text(this.font, "§7" + p.trackCount() + " tracks", listX + 20, y + 12, 0xFFFFFFFF, false);
                g.text(this.font, "§a[+add]", listX + listW - 44, y + 7, 0xFFFFFFFF, false);
            }
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (sp().isConnected() && event.button() == 0) {
            double mx = event.x(), my = event.y();
            if (mx >= listX && mx <= listX + listW && my >= listTop && my <= listBottom) {
                List<SpotifyPlaylist> lists = sp().cachedPlaylists();
                int idx = (int) ((my - listTop + scroll) / ROW_H);
                if (idx >= 0 && idx < lists.size()) {
                    SpotifyPlaylist p = lists.get(idx);
                    if (mx <= listX + 18) {
                        cfg().quickAddPlaylistId = p.id();
                        cfg().quickAddPlaylistName = p.name();
                        SpotifyConfigManager.save();
                        status = "Quick-add playlist set to " + p.name();
                    } else if (mx >= listX + listW - 46) {
                        sp().addCurrentToPlaylist(p);
                    } else {
                        sp().playPlaylist(p);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sp().isConnected()) {
            int contentH = sp().cachedPlaylists().size() * ROW_H;
            int maxScroll = Math.max(0, contentH - (listBottom - listTop));
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - sy * 16));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    private void center(GuiGraphicsExtractor g, String text, int y) {
        String plain = text.replaceAll("§.", "");
        g.text(this.font, text, this.width / 2 - this.font.width(plain) / 2, y, 0xFFFFFFFF, true);
    }

    private void line(GuiGraphicsExtractor g, String text, int x, int y) {
        g.text(this.font, text, x, y, 0xFFFFFFFF, false);
    }

    @Override
    public void onClose() {
        SpotifyConfigManager.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }

    /** Opens this screen only when Spotify features are triggered without setup. */
    public static void openFor(Runnable ifConnected) {
        SpotifyManager sp = MusicOverlay.spotify();
        if (sp.isConnected()) {
            ifConnected.run();
        } else {
            net.minecraft.client.Minecraft.getInstance()
                    .setScreenAndShow(new SpotifyScreen(null));
        }
    }
}
