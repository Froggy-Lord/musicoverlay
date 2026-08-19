package com.froggylord.musicoverlay.lyrics;

/** A lyric line ready to draw: its text and whether it's the one playing now. */
public record RenderLyric(String text, boolean active) {}
