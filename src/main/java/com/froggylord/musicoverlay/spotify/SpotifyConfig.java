package com.froggylord.musicoverlay.spotify;

/**
 * Spotify Web API settings and stored tokens. Kept in its own file
 * (config/musicoverlay-spotify.json) so the OAuth tokens stay separate from the
 * main overlay config. Everything here is inert until a Client ID is set, so the
 * base mod needs no setup at all.
 */
public class SpotifyConfig {
    /** The user's Spotify app Client ID. Empty means the whole layer is off. */
    public String clientId = "";
    /** Loopback port the OAuth redirect comes back on. Must match the app's redirect URI. */
    public int redirectPort = 54321;

    // Stored after the OAuth consent; refreshed automatically.
    public String accessToken = "";
    public String refreshToken = "";
    public long expiresAtEpoch = 0;

    /** Optional playlist the "quick add" keybind drops the current track into. */
    public String quickAddPlaylistId = "";
    public String quickAddPlaylistName = "";

    public boolean hasClientId() {
        return clientId != null && !clientId.isBlank();
    }

    public boolean hasTokens() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** The exact redirect URI the user must register in their Spotify app dashboard. */
    public String redirectUri() {
        return "http://127.0.0.1:" + redirectPort + "/callback";
    }
}
