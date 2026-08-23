package com.froggylord.musicoverlay.render;

import com.froggylord.musicoverlay.media.NowPlaying;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

/**
 * Turns the album-art file the helper saved into a GUI texture the overlay can
 * blit, keeping a single live texture and swapping it only when the track's art
 * actually changes. Registered dynamic textures are released as they're replaced
 * so we never leak GPU handles across a listening session.
 */
public final class AlbumArt {
    private static final Logger LOG = LoggerFactory.getLogger("MusicOverlay");

    private Identifier textureId;
    private String loadedHash = "";
    private String failedHash = "";
    private int width;
    private int height;

    /** Ensure the live texture matches the current track; cheap no-op if unchanged. */
    public void sync(NowPlaying data) {
        String hash = data.coverHash();
        if (hash.isEmpty() || hash.equals(loadedHash) || hash.equals(failedHash)) return;

        String pathStr = data.coverPath();
        if (pathStr.isEmpty()) { clear(); return; }
        Path path = Path.of(pathStr);
        if (!Files.exists(path)) return;

        try {
            NativeImage image = readImage(path);
            Minecraft mc = Minecraft.getInstance();
            Identifier id = Identifier.fromNamespaceAndPath("musicoverlay", "cover_" + hash.toLowerCase());
            DynamicTexture texture = new DynamicTexture(() -> "musicoverlay_cover", image);

            if (textureId != null) {
                mc.getTextureManager().release(textureId);
            }
            mc.getTextureManager().register(id, texture);
            textureId = id;
            width = image.getWidth();
            height = image.getHeight();
            loadedHash = hash;
            failedHash = "";
        } catch (Exception e) {
            LOG.warn("[MusicOverlay] failed to load album art: {}", e.getMessage());
            failedHash = hash;
            clear();
        }
    }

    /** Minecraft's native decoder is PNG-only; MPRIS/Spotify commonly returns JPEG. */
    private static NativeImage readImage(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] signature = in.readNBytes(8);
            if (signature.length == 8
                    && signature[0] == (byte) 0x89 && signature[1] == 'P'
                    && signature[2] == 'N' && signature[3] == 'G') {
                try (InputStream png = Files.newInputStream(path)) {
                    return NativeImage.read(png);
                }
            }
        }

        BufferedImage source = ImageIO.read(path.toFile());
        if (source == null) throw new java.io.IOException("unsupported album-art image format");
        try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
            if (!ImageIO.write(source, "png", png)) {
                throw new java.io.IOException("could not convert album art to PNG");
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(png.toByteArray())) {
                return NativeImage.read(in);
            }
        }
    }

    public boolean ready() {
        return textureId != null;
    }

    public Identifier textureId() {
        return textureId;
    }

    public int width()  { return width; }
    public int height() { return height; }

    public void clear() {
        if (textureId != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(textureId);
            } catch (Exception ignored) {}
        }
        textureId = null;
        loadedHash = "";
    }
}
