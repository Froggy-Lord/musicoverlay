package com.froggylord.musicoverlay.spotify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and persists {@link SpotifyConfig} to config/musicoverlay-spotify.json. */
public final class SpotifyConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("musicoverlay-spotify.json");

    private static SpotifyConfig config;

    private SpotifyConfigManager() {}

    public static SpotifyConfig get() {
        if (config == null) load();
        return config;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                config = GSON.fromJson(Files.readString(PATH), SpotifyConfig.class);
                if (config == null) config = new SpotifyConfig();
            } else {
                config = new SpotifyConfig();
            }
        } catch (Exception e) {
            LOG.error("[MusicOverlay] failed to load Spotify config", e);
            config = new SpotifyConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(get()));
        } catch (Exception e) {
            LOG.error("[MusicOverlay] failed to save Spotify config", e);
        }
    }
}
