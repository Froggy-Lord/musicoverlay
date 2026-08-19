package com.froggylord.musicoverlay.config;

/** When the overlay is allowed to show, assuming it's toggled on at all. */
public enum VisibilityMode {
    /** Show whenever there's track data, even paused. */
    ALWAYS,
    /** Only while something is actively playing. */
    WHEN_PLAYING,
    /** Show while playing, then linger for a few seconds after it pauses/stops. */
    WHEN_RECENT;

    public VisibilityMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String display() {
        return switch (this) {
            case ALWAYS -> "Always";
            case WHEN_PLAYING -> "While Playing";
            case WHEN_RECENT -> "Recently Played";
        };
    }
}
