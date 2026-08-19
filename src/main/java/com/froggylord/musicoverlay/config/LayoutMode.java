package com.froggylord.musicoverlay.config;

/**
 * How much of the card to draw. Each mode is a preset; individual elements can
 * still be toggled on top of it from the settings screen.
 */
public enum LayoutMode {
    /** Album art, title, artist, progress bar and times — the full widget. */
    CARD,
    /** A single slim row: art thumbnail + scrolling "title - artist". */
    COMPACT,
    /** Just the album art square, nothing else. */
    ART_ONLY,
    /** Text and progress only, no album art. */
    TEXT_ONLY,
    /** Title + a thin progress line. Smallest footprint that still reads. */
    MINIMAL;

    public LayoutMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean showsArt() {
        return this == CARD || this == COMPACT || this == ART_ONLY;
    }

    public boolean showsText() {
        return this != ART_ONLY;
    }

    public String display() {
        return switch (this) {
            case CARD -> "Card";
            case COMPACT -> "Compact";
            case ART_ONLY -> "Art Only";
            case TEXT_ONLY -> "Text Only";
            case MINIMAL -> "Minimal";
        };
    }
}
