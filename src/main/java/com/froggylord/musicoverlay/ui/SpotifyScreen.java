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
 * The Spotify control panel: paste a Client ID, connect, then browse playlists
 * and act on the current track. Reachable from the settings screen and the
 * Spotify keybind. Everything here is optional; the overlay works without it.
 */
public class SpotifyScreen extends Screen {
    private final Screen parent;
    private EditBox clientIdBox;
    private String status = "";
    private boolean requestedPlaylists = false;

    // playlist list geometry
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
        SpotifyConfig c = cfg();
        int cx = this.width / 2;

        clientIdBox = new EditBox(this.font, cx - 150, 44, 300, 18, Component.literal("Client ID"));
        clientIdBox.setMaxLength(64);
        clientIdBox.setHint(Component.literal("Paste your Spotify app Client ID"));
        clientIdBox.setValue(c.clientId);
        clientIdBox.setResponder(v -> {
            c.clientId = v.trim();
            SpotifyConfigManager.save();
        });
        addRenderableWidget(clientIdBox);

        if (sp().isConnected()) {
            addRenderableWidget(Button.builder(Component.literal("Like"), b -> sp().toggleLikeCurrent())
                    .bounds(cx - 150, 68, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Queue"), b -> sp().addCurrentToQueue())
                    .bounds(cx - 74, 68, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal("◀ Prev"), b -> sp().previous())
                    .bounds(cx + 2, 68, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Skip ▶"), b -> sp().next())
                    .bounds(cx + 78, 68, 72, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Refresh"),
                            b -> sp().refreshPlaylists(this::rebuildWidgets))
                    .bounds(cx - 150, 92, 148, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Disconnect"), b -> {
                        sp().disconnect();
                        rebuildWidgets();
                    }).bounds(cx + 2, 92, 148, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Connect Spotify"),
                            b -> sp().beginAuth(s -> { this.status = s; }))
                    .bounds(cx - 150, 68, 300, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(cx - 50, this.height - 26, 100, 20).build());

        listX = cx - 150;
        listW = 300;
        listTop = 120;
        listBottom = this.height - 34;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        int cx = this.width / 2;
        center(g, "§bSpotify", 16);

        if (!sp().isConnected() && !cfg().hasClientId()) {
            // First-run help.
            small(g, "1. Create an app at developer.spotify.com/dashboard", cx - 150, 116);
            small(g, "2. Add this Redirect URI to it:", cx - 150, 128);
            g.text(this.font, "§a" + cfg().redirectUri(), cx - 150, 140, 0xFFFFFFFF, false);
            small(g, "3. Paste the app's Client ID above, then Connect.", cx - 150, 152);
            if (!status.isBlank()) g.text(this.font, "§7" + status, cx - 150, 168, 0xFFFFFFFF, false);
            return;
        }

        if (!sp().isConnected()) {
            small(g, "Redirect URI (must be registered in your app):", cx - 150, 96);
            g.text(this.font, "§a" + cfg().redirectUri(), cx - 150, 108, 0xFFFFFFFF, false);
            if (!status.isBlank()) g.text(this.font, "§7" + status, cx - 150, 124, 0xFFFFFFFF, false);
            return;
        }

        // Connected: fetch playlists once, then draw them.
        if (!requestedPlaylists) {
            requestedPlaylists = true;
            sp().refreshPlaylists(null);
            sp().refreshPlayback(null);
        }

        var pb = sp().cachedPlayback();
        String now = pb != null && pb.hasTrack()
                ? "Now: " + pb.title() + " · " + pb.artist() + (pb.isSaved() ? "  ♥" : "")
                : "Nothing playing (open Spotify on a device)";
        g.text(this.font, "§7" + Draw.fit(this.font, now, listW), listX, 114, 0xFFFFFFFF, false);

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
                String star = isQuick ? "§e★" : "§8☆";
                g.text(this.font, star, listX + 6, y + 7, 0xFFFFFFFF, false);
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
                        // star column: set as quick-add
                        cfg().quickAddPlaylistId = p.id();
                        cfg().quickAddPlaylistName = p.name();
                        SpotifyConfigManager.save();
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

    private void small(GuiGraphicsExtractor g, String text, int x, int y) {
        g.text(this.font, "§7" + text, x, y, 0xFFFFFFFF, false);
    }

    @Override
    public void onClose() {
        SpotifyConfigManager.save();
        if (this.minecraft != null) this.minecraft.setScreenAndShow(parent);
    }
}
