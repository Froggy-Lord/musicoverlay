package com.froggylord.musicoverlay.lyrics;

/** One timed line of an LRC lyric file. */
public record LyricLine(long timeMs, String text) implements Comparable<LyricLine> {
    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(this.timeMs, other.timeMs);
    }
}
