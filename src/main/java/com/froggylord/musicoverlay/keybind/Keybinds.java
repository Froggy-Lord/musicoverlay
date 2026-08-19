package com.froggylord.musicoverlay.keybind;

import com.froggylord.musicoverlay.MusicOverlay;
import com.froggylord.musicoverlay.config.ConfigManager;
import com.froggylord.musicoverlay.media.MediaControls;
import com.froggylord.musicoverlay.ui.RepositionScreen;
import com.froggylord.musicoverlay.ui.SettingsScreen;
import com.froggylord.musicoverlay.ui.SpotifyScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * All keybinds are unbound by default so the mod never fights an existing key.
 * Assign them under Options → Controls → Music Overlay.
 */
public final class Keybinds {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("musicoverlay", "musicoverlay"));

    private Keybinds() {}

    public static void init() {
        KeyMapping settings = register("settings");
        KeyMapping toggle = register("toggle");
        KeyMapping reposition = register("reposition");
        KeyMapping playPause = register("play_pause");
        KeyMapping next = register("next");
        KeyMapping previous = register("previous");
        KeyMapping spotifyMenu = register("spotify_menu");
        KeyMapping spotifyAdd = register("spotify_add");
        KeyMapping spotifyLike = register("spotify_like");
        KeyMapping spotifyQueue = register("spotify_queue");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settings.consumeClick()) {
                client.setScreenAndShow(new SettingsScreen(null));
            }
            while (toggle.consumeClick()) {
                ConfigManager.get().enabled = !ConfigManager.get().enabled;
                ConfigManager.save();
            }
            while (reposition.consumeClick()) {
                client.setScreenAndShow(new RepositionScreen(null));
            }
            while (playPause.consumeClick()) MediaControls.playPause();
            while (next.consumeClick()) MediaControls.next();
            while (previous.consumeClick()) MediaControls.previous();
            // Spotify actions open the setup walkthrough if you haven't connected yet,
            // so any attempt to use them explains what's needed first.
            while (spotifyMenu.consumeClick()) client.setScreenAndShow(new SpotifyScreen(null));
            while (spotifyAdd.consumeClick()) SpotifyScreen.openFor(() -> MusicOverlay.spotify().quickAddCurrent());
            while (spotifyLike.consumeClick()) SpotifyScreen.openFor(() -> MusicOverlay.spotify().toggleLikeCurrent());
            while (spotifyQueue.consumeClick()) SpotifyScreen.openFor(() -> MusicOverlay.spotify().addCurrentToQueue());
        });
    }

    private static KeyMapping register(String id) {
        KeyMapping key = new KeyMapping(
                "key.musicoverlay." + id,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                CATEGORY);
        KeyMappingHelper.registerKeyMapping(key);
        return key;
    }
}
