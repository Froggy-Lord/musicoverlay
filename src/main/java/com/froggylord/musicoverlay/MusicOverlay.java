package com.froggylord.musicoverlay;

import com.froggylord.musicoverlay.config.ConfigManager;
import com.froggylord.musicoverlay.media.HelperProcessManager;
import com.froggylord.musicoverlay.media.NowPlayingBridge;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entry point: loads config, starts the media bridge and helper process. */
public class MusicOverlay implements ClientModInitializer {
    public static final String MOD_ID = "musicoverlay";
    public static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");

    private static HelperProcessManager helper;
    private static NowPlayingBridge bridge;

    @Override
    public void onInitializeClient() {
        LOG.info("[MusicOverlay] initializing");
        ConfigManager.load();

        helper = new HelperProcessManager();
        bridge = new NowPlayingBridge();

        helper.start();
        bridge.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bridge.stop();
            helper.stop();
        }, "MusicOverlay-Shutdown"));

        LOG.info("[MusicOverlay] ready");
    }

    public static NowPlayingBridge bridge() {
        return bridge;
    }
}
