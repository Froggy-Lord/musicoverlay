package com.froggylord.musicoverlay.spotify;

/** A playlist owned by or followed by the user. */
public record SpotifyPlaylist(String id, String name, int trackCount, String ownerName) {
    public String uri() {
        return "spotify:playlist:" + id;
    }
}
