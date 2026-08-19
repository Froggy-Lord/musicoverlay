package com.froggylord.musicoverlay.spotify;

/**
 * The bit of the Web API playback state we act on: the current track's URI (for
 * add-to-playlist / like) plus a readable label. Distinct from the OS media
 * session snapshot because playlist and library actions need the real track URI.
 */
public record PlaybackState(String trackUri, String trackId, String title, String artist, boolean isSaved) {
    public boolean hasTrack() {
        return trackUri != null && !trackUri.isBlank();
    }
}
