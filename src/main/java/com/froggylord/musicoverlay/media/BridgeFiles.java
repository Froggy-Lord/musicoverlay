package com.froggylord.musicoverlay.media;

import java.nio.file.Path;

/**
 * Shared filesystem contract between the mod and the platform helper. The helper
 * writes {@code nowplaying.json} into this directory and watches {@code command}
 * for playback control requests; the mod does the mirror image.
 */
public final class BridgeFiles {
    private BridgeFiles() {}

    /** tmpdir/musicoverlay — a stable, per-user location both sides agree on. */
    public static final Path DIR =
            Path.of(System.getProperty("java.io.tmpdir"), "musicoverlay");

    public static final Path NOW_PLAYING = DIR.resolve("nowplaying.json");
    public static final Path COMMAND = DIR.resolve("command");
    public static final Path ART_DIR = DIR.resolve("art");
}
