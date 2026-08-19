package com.froggylord.musicoverlay.spotify;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Front door for the Spotify layer. Owns auth + client, caches the user's
 * playlists and current track, and exposes the high-level actions the keybinds
 * and settings screen call. Every method is safe to call when Spotify isn't set
 * up - it just no-ops or reports back.
 */
public final class SpotifyManager {
    private final SpotifyAuth auth = new SpotifyAuth();
    private final SpotifyClient client = new SpotifyClient(auth);

    private volatile List<SpotifyPlaylist> playlists = Collections.emptyList();
    private volatile PlaybackState playback = null;

    public SpotifyAuth auth() { return auth; }

    public boolean isEnabled() {
        return SpotifyConfigManager.get().hasClientId();
    }

    public boolean isConnected() {
        return auth.isConnected();
    }

    public List<SpotifyPlaylist> cachedPlaylists() { return playlists; }
    public PlaybackState cachedPlayback() { return playback; }

    public void beginAuth(Consumer<String> status) {
        auth.beginAuth(status);
    }

    public void disconnect() {
        auth.disconnect();
        playlists = Collections.emptyList();
        playback = null;
    }

    public void refreshPlaylists(Runnable done) {
        if (!isConnected()) return;
        client.playlists().thenAccept(list -> {
            playlists = list;
            if (done != null) Minecraft.getInstance().execute(done);
        });
    }

    public void refreshPlayback(Runnable done) {
        if (!isConnected()) return;
        client.currentPlayback().thenAccept(state -> {
            playback = state;
            if (done != null) Minecraft.getInstance().execute(done);
        });
    }

    // ---- actions used by keybinds and the screen ----

    /** Add the currently-playing track to the configured quick-add playlist. */
    public void quickAddCurrent() {
        if (notConnected()) return;
        SpotifyConfig cfg = SpotifyConfigManager.get();
        if (cfg.quickAddPlaylistId.isBlank()) {
            notify("Pick a quick-add playlist in the Spotify menu first.");
            return;
        }
        client.currentPlayback().thenAccept(state -> {
            if (state == null || !state.hasTrack()) { notify("Nothing is playing."); return; }
            client.addToPlaylist(cfg.quickAddPlaylistId, state.trackUri()).thenAccept(ok ->
                    notify(ok ? "Added \"" + state.title() + "\" to " + cfg.quickAddPlaylistName
                              : "Couldn't add to playlist."));
        });
    }

    public void addCurrentToPlaylist(SpotifyPlaylist playlist) {
        if (notConnected()) return;
        client.currentPlayback().thenAccept(state -> {
            if (state == null || !state.hasTrack()) { notify("Nothing is playing."); return; }
            client.addToPlaylist(playlist.id(), state.trackUri()).thenAccept(ok ->
                    notify(ok ? "Added \"" + state.title() + "\" to " + playlist.name()
                              : "Couldn't add to playlist."));
        });
    }

    /** Like or unlike the current track. */
    public void toggleLikeCurrent() {
        if (notConnected()) return;
        client.currentPlayback().thenAccept(state -> {
            if (state == null || state.trackId().isBlank()) { notify("Nothing is playing."); return; }
            boolean target = !state.isSaved();
            client.setSaved(state.trackId(), target).thenAccept(ok ->
                    notify(ok ? (target ? "Liked \"" + state.title() + "\"" : "Removed from Liked Songs")
                              : "Couldn't update Liked Songs."));
        });
    }

    public void addCurrentToQueue() {
        if (notConnected()) return;
        client.currentPlayback().thenAccept(state -> {
            if (state == null || !state.hasTrack()) { notify("Nothing is playing."); return; }
            client.addToQueue(state.trackUri()).thenAccept(ok ->
                    notify(ok ? "Queued \"" + state.title() + "\"" : "Couldn't queue (Premium + active device needed)."));
        });
    }

    public void playPlaylist(SpotifyPlaylist playlist) {
        if (notConnected()) return;
        client.playContext(playlist.uri()).thenAccept(ok ->
                notify(ok ? "Playing " + playlist.name() : "Couldn't start playback (Premium + active device needed)."));
    }

    public void next() {
        if (notConnected()) return;
        client.next().thenAccept(ok -> { if (!ok) notify("Skip failed (Premium + active device needed)."); });
    }

    public void previous() {
        if (notConnected()) return;
        client.previous().thenAccept(ok -> { if (!ok) notify("Previous failed (Premium + active device needed)."); });
    }

    // ---- helpers ----

    private boolean notConnected() {
        if (!isConnected()) {
            notify("Connect Spotify first (Spotify menu).");
            return true;
        }
        return false;
    }

    static void notify(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§1♪ §r" + msg));
            }
        });
    }
}
