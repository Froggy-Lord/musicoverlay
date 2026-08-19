package com.froggylord.musicoverlay.config;

/**
 * Every knob the overlay exposes. Plain fields, serialised straight to JSON by
 * {@link ConfigManager} so the file stays hand-editable. Colours are stored as
 * packed ARGB ints (0xAARRGGBB); the settings screen edits them as hex.
 */
public class OverlayConfig {

    // ---- master ----
    public boolean enabled = true;
    public VisibilityMode visibility = VisibilityMode.ALWAYS;
    /** Seconds the card lingers after playback stops in "Recently Played" mode. */
    public int recentLingerSeconds = 8;
    public boolean hideInMenus = false;
    /** Respect the vanilla "hide HUD" key (F1). */
    public boolean hideWithHud = true;
    public boolean hideInDebug = true;

    // ---- placement ----
    public Anchor anchor = Anchor.TOP_LEFT;
    public int offsetX = 6;
    public int offsetY = 6;
    public float scale = 1.0f;
    public float opacity = 0.85f;

    // ---- layout / elements ----
    public LayoutMode layout = LayoutMode.CARD;
    public boolean showAlbumArt = true;
    public boolean showTitle = true;
    public boolean showArtist = true;
    public boolean showAlbum = false;
    public boolean showProgressBar = true;
    public boolean showProgressHead = true;
    public boolean showTimes = true;
    public boolean showSourceApp = false;
    public boolean showPausedIcon = true;
    public boolean textShadow = true;
    public boolean roundedCorners = true;
    public boolean showBorder = false;

    // ---- colours (ARGB) ----
    public int backgroundColor = 0xFF1A1A2E;
    public int accentColor = 0xFF1DB954;   // Spotify green by default
    public int titleColor = 0xFFFFFFFF;
    public int artistColor = 0xFFAAAAAA;
    public int timeColor = 0xFFAAAAAA;
    public int borderColor = 0xFF1DB954;
    public int progressTrackColor = 0xFF555555;

    // ---- lyrics ----
    public boolean showLyrics = true;
    public LyricsPlacement lyricsPlacement = LyricsPlacement.BELOW;
    public int lyricsLines = 3;
    public boolean lyricsCentered = false;
    public int lyricsOffsetMs = 0;
    public int lyricsActiveColor = 0xFFFFFFFF;
    public int lyricsInactiveColor = 0xFF9A9AB0;

    // ---- behaviour ----
    /** How often the game re-reads the now-playing file, in milliseconds. */
    public int pollIntervalMs = 250;
    /** Allow the play/pause/next/prev keybinds to drive the real player. */
    public boolean enableMediaControls = true;
    /** Fetch time-synced lyrics from LRCLIB. */
    public boolean fetchLyricsOnline = true;

    /** Reset every field to defaults in place (keeps the same instance). */
    public void resetToDefaults() {
        OverlayConfig d = new OverlayConfig();
        this.enabled = d.enabled;
        this.visibility = d.visibility;
        this.recentLingerSeconds = d.recentLingerSeconds;
        this.hideInMenus = d.hideInMenus;
        this.hideWithHud = d.hideWithHud;
        this.hideInDebug = d.hideInDebug;
        this.anchor = d.anchor;
        this.offsetX = d.offsetX;
        this.offsetY = d.offsetY;
        this.scale = d.scale;
        this.opacity = d.opacity;
        this.layout = d.layout;
        this.showAlbumArt = d.showAlbumArt;
        this.showTitle = d.showTitle;
        this.showArtist = d.showArtist;
        this.showAlbum = d.showAlbum;
        this.showProgressBar = d.showProgressBar;
        this.showProgressHead = d.showProgressHead;
        this.showTimes = d.showTimes;
        this.showSourceApp = d.showSourceApp;
        this.showPausedIcon = d.showPausedIcon;
        this.textShadow = d.textShadow;
        this.roundedCorners = d.roundedCorners;
        this.showBorder = d.showBorder;
        this.backgroundColor = d.backgroundColor;
        this.accentColor = d.accentColor;
        this.titleColor = d.titleColor;
        this.artistColor = d.artistColor;
        this.timeColor = d.timeColor;
        this.borderColor = d.borderColor;
        this.progressTrackColor = d.progressTrackColor;
        this.showLyrics = d.showLyrics;
        this.lyricsPlacement = d.lyricsPlacement;
        this.lyricsLines = d.lyricsLines;
        this.lyricsCentered = d.lyricsCentered;
        this.lyricsOffsetMs = d.lyricsOffsetMs;
        this.lyricsActiveColor = d.lyricsActiveColor;
        this.lyricsInactiveColor = d.lyricsInactiveColor;
        this.pollIntervalMs = d.pollIntervalMs;
        this.enableMediaControls = d.enableMediaControls;
        this.fetchLyricsOnline = d.fetchLyricsOnline;
    }

    /** Clamp anything a hand-edit could push out of range. */
    public void sanitise() {
        if (visibility == null) visibility = VisibilityMode.ALWAYS;
        if (anchor == null) anchor = Anchor.TOP_LEFT;
        if (layout == null) layout = LayoutMode.CARD;
        if (lyricsPlacement == null) lyricsPlacement = LyricsPlacement.BELOW;
        scale = clamp(scale, 0.4f, 3.0f);
        opacity = clamp(opacity, 0.0f, 1.0f);
        lyricsLines = (int) clamp(lyricsLines, 1, 7);
        lyricsOffsetMs = (int) clamp(lyricsOffsetMs, -10000, 10000);
        recentLingerSeconds = (int) clamp(recentLingerSeconds, 1, 60);
        pollIntervalMs = (int) clamp(pollIntervalMs, 100, 2000);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
