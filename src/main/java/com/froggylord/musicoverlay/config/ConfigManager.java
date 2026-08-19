package com.froggylord.musicoverlay.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads and persists {@link OverlayConfig} to config/musicoverlay.json. */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH =
            FabricLoader.getInstance().getConfigDir().resolve("musicoverlay.json");

    private static OverlayConfig config;

    private ConfigManager() {}

    public static OverlayConfig get() {
        if (config == null) load();
        return config;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                String json = Files.readString(PATH);
                config = GSON.fromJson(json, OverlayConfig.class);
                if (config == null) config = new OverlayConfig();
                config.sanitise();
                LOG.info("[MusicOverlay] config loaded from {}", PATH);
            } else {
                config = new OverlayConfig();
                save();
                LOG.info("[MusicOverlay] wrote default config to {}", PATH);
            }
        } catch (Exception e) {
            LOG.error("[MusicOverlay] failed to load config, using defaults", e);
            config = new OverlayConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(get()));
        } catch (IOException e) {
            LOG.error("[MusicOverlay] failed to save config", e);
        }
    }
}
