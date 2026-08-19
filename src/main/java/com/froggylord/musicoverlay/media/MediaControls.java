package com.froggylord.musicoverlay.media;

import com.froggylord.musicoverlay.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Sends playback commands to the helper by appending a line to the shared
 * command file. The helper tails that file and drives the real player, so
 * play/pause/skip work against whatever app owns the media session.
 */
public final class MediaControls {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");

    private MediaControls() {}

    public static void playPause() { send("playpause"); }
    public static void next()      { send("next"); }
    public static void previous()  { send("previous"); }

    private static void send(String command) {
        if (!ConfigManager.get().enableMediaControls) return;
        try {
            Files.createDirectories(BridgeFiles.DIR);
            Files.writeString(
                    BridgeFiles.COMMAND,
                    command + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
        } catch (Exception e) {
            LOG.warn("[MusicOverlay] could not send '{}' command: {}", command, e.getMessage());
        }
    }
}
