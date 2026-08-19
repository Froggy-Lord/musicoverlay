package com.froggylord.musicoverlay.config;

/**
 * Where the overlay sticks to on screen. The free-drag offset in the config is
 * applied on top of the anchor, so a card anchored BOTTOM_RIGHT keeps hugging
 * the corner no matter the GUI scale or window size.
 */
public enum Anchor {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    MIDDLE_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    MIDDLE_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    public final float fx;
    public final float fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }

    /** X of the box's top-left corner, before the user offset. */
    public int baseX(int screenW, int boxW) {
        return Math.round((screenW - boxW) * fx);
    }

    /** Y of the box's top-left corner, before the user offset. */
    public int baseY(int screenH, int boxH) {
        return Math.round((screenH - boxH) * fy);
    }

    public Anchor next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String display() {
        return switch (this) {
            case TOP_LEFT -> "Top Left";
            case TOP_CENTER -> "Top Center";
            case TOP_RIGHT -> "Top Right";
            case MIDDLE_LEFT -> "Middle Left";
            case CENTER -> "Center";
            case MIDDLE_RIGHT -> "Middle Right";
            case BOTTOM_LEFT -> "Bottom Left";
            case BOTTOM_CENTER -> "Bottom Center";
            case BOTTOM_RIGHT -> "Bottom Right";
        };
    }
}
