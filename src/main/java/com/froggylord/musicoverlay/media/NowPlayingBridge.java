package com.froggylord.musicoverlay.media;

import com.froggylord.musicoverlay.config.ConfigManager;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Polls the helper's {@code nowplaying.json} on a background thread and exposes
 * the latest snapshot. Fires a callback whenever the track changes so lyrics can
 * be fetched. Reading a small local file a few times a second is cheap and keeps
 * all the platform-specific mess out of the render thread.
 */
public final class NowPlayingBridge {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final Gson GSON = new Gson();

    private volatile NowPlaying current = new NowPlaying();
    private volatile String lastSongKey = "";
    private ScheduledExecutorService scheduler;
    private Consumer<NowPlaying> onSongChanged;
    private long lastPlayingMs = 0;

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MusicOverlay-Poller");
            t.setDaemon(true);
            return t;
        });
        int interval = ConfigManager.get().pollIntervalMs;
        scheduler.scheduleAtFixedRate(this::poll, 0, interval, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    public void setOnSongChanged(Consumer<NowPlaying> cb) {
        this.onSongChanged = cb;
    }

    public NowPlaying current() {
        return current;
    }

    /** Epoch millis of the last time we saw something playing (for linger mode). */
    public long lastPlayingMs() {
        return lastPlayingMs;
    }

    private void poll() {
        try {
            if (!Files.exists(BridgeFiles.NOW_PLAYING)) return;

            String json = Files.readString(BridgeFiles.NOW_PLAYING);
            NowPlaying data = GSON.fromJson(json, NowPlaying.class);
            if (data == null) return;

            data.markReceived(System.currentTimeMillis());
            this.current = data;
            if (data.isPlaying()) lastPlayingMs = System.currentTimeMillis();

            String key = data.songKey();
            if (!key.equals(lastSongKey)) {
                lastSongKey = key;
                if (data.hasData() && onSongChanged != null) {
                    onSongChanged.accept(data);
                }
            }
        } catch (Exception e) {
            LOG.debug("[MusicOverlay] poll failed: {}", e.getMessage());
        }
    }
}
