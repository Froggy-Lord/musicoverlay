package com.froggylord.musicoverlay.config;

/** Where the synced lyrics box sits relative to the now-playing card. */
public enum LyricsPlacement {
    BELOW,
    ABOVE;

    public LyricsPlacement next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String display() {
        return switch (this) {
            case BELOW -> "Below Card";
            case ABOVE -> "Above Card";
        };
    }
}
