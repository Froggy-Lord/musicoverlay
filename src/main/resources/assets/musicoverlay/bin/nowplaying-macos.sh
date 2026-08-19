#!/bin/bash
# Music Overlay - macOS now-playing helper.
#
# Two tiers, best available wins:
#   1. nowplaying-cli (brew install nowplaying-cli) - reads the system
#      now-playing centre, so it sees any app (when the OS still exposes it).
#   2. AppleScript against Spotify and the Music app - always available, covers
#      the two apps most people actually use.
#
# Writes the same nowplaying.json contract as the other platforms, saves album
# art, and drains the command file for play/pause and skip. Dependency-free
# beyond what macOS ships (osascript, md5, curl); no Python required.

BASE_DIR="${MC_NOWPLAYING_DIR:-$TMPDIR/musicoverlay}"
ART_DIR="$BASE_DIR/art"
JSON_PATH="$BASE_DIR/nowplaying.json"
COMMAND_PATH="$BASE_DIR/command"
mkdir -p "$ART_DIR"

POLL=0.25

has_npc=0
command -v nowplaying-cli >/dev/null 2>&1 && has_npc=1

# JSON string escaping: backslash, double-quote, and strip control chars.
json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/[[:cntrl:]]//g'
}

write_json() {
    # args: title artist album source positionMs durationMs status coverPath coverHash canControl
    local tmp="$JSON_PATH.tmp"
    cat > "$tmp" <<EOF
{"title":"$(json_escape "$1")","artist":"$(json_escape "$2")","album":"$(json_escape "$3")","sourceApp":"$(json_escape "$4")","positionMs":$5,"durationMs":$6,"status":"$7","coverPath":"$(json_escape "$8")","coverHash":"$9","canControl":${10},"timestamp":$(($(date +%s)*1000))}
EOF
    mv -f "$tmp" "$JSON_PATH"
}

write_empty() {
    write_json "" "" "" "" 0 0 "Stopped" "" "" false
}

app_running() {
    osascript -e "application \"$1\" is running" 2>/dev/null
}

# ---- AppleScript readers ----

read_spotify() {
    [ "$(app_running Spotify)" = "true" ] || return 1
    local state
    state=$(osascript -e 'tell application "Spotify" to player state as string' 2>/dev/null) || return 1
    [ -z "$state" ] && return 1
    local title artist album dur_ms pos_s art_url
    title=$(osascript -e 'tell application "Spotify" to name of current track' 2>/dev/null)
    artist=$(osascript -e 'tell application "Spotify" to artist of current track' 2>/dev/null)
    album=$(osascript -e 'tell application "Spotify" to album of current track' 2>/dev/null)
    dur_ms=$(osascript -e 'tell application "Spotify" to duration of current track' 2>/dev/null)   # Spotify: ms
    pos_s=$(osascript -e 'tell application "Spotify" to player position' 2>/dev/null)              # seconds (real)
    art_url=$(osascript -e 'tell application "Spotify" to artwork url of current track' 2>/dev/null)

    local status="Stopped"
    [ "$state" = "playing" ] && status="Playing"
    [ "$state" = "paused" ] && status="Paused"
    local pos_ms=0
    [ -n "$pos_s" ] && pos_ms=$(printf '%.0f' "$(echo "$pos_s * 1000" | bc -l 2>/dev/null || echo 0)")
    [ -z "$dur_ms" ] && dur_ms=0

    local hash="" cover=""
    if [ -n "$art_url" ]; then
        hash=$(printf '%s' "$art_url" | md5 -q 2>/dev/null || printf '%s' "$art_url" | md5)
        cover="$ART_DIR/$hash"
        [ -s "$cover" ] || curl -fsL "$art_url" -o "$cover" 2>/dev/null || cover=""
    fi
    write_json "$title" "$artist" "$album" "Spotify" "$pos_ms" "$dur_ms" "$status" "$cover" "$hash" true
    return 0
}

read_music() {
    [ "$(app_running Music)" = "true" ] || return 1
    local state
    state=$(osascript -e 'tell application "Music" to player state as string' 2>/dev/null) || return 1
    [ -z "$state" ] && return 1
    local title artist album dur_s pos_s
    title=$(osascript -e 'tell application "Music" to name of current track' 2>/dev/null)
    artist=$(osascript -e 'tell application "Music" to artist of current track' 2>/dev/null)
    album=$(osascript -e 'tell application "Music" to album of current track' 2>/dev/null)
    dur_s=$(osascript -e 'tell application "Music" to duration of current track' 2>/dev/null)     # Music: seconds
    pos_s=$(osascript -e 'tell application "Music" to player position' 2>/dev/null)

    local status="Stopped"
    [ "$state" = "playing" ] && status="Playing"
    [ "$state" = "paused" ] && status="Paused"
    local pos_ms=0 dur_ms=0
    [ -n "$pos_s" ] && pos_ms=$(printf '%.0f' "$(echo "$pos_s * 1000" | bc -l 2>/dev/null || echo 0)")
    [ -n "$dur_s" ] && dur_ms=$(printf '%.0f' "$(echo "$dur_s * 1000" | bc -l 2>/dev/null || echo 0)")
    write_json "$title" "$artist" "$album" "Music" "$pos_ms" "$dur_ms" "$status" "" "" true
    return 0
}

# ---- nowplaying-cli reader ----

read_npc() {
    local title artist album dur_s pos_s rate
    title=$(nowplaying-cli get title 2>/dev/null)
    [ "$title" = "null" ] && title=""
    if [ -z "$title" ]; then return 1; fi
    artist=$(nowplaying-cli get artist 2>/dev/null); [ "$artist" = "null" ] && artist=""
    album=$(nowplaying-cli get album 2>/dev/null); [ "$album" = "null" ] && album=""
    dur_s=$(nowplaying-cli get duration 2>/dev/null)
    pos_s=$(nowplaying-cli get elapsedTime 2>/dev/null)
    rate=$(nowplaying-cli get playbackRate 2>/dev/null)

    local status="Paused"
    [ "$rate" = "1" ] || [ "$rate" = "1.0" ] && status="Playing"
    local pos_ms=0 dur_ms=0
    case "$pos_s" in ''|null) pos_s=0;; esac
    case "$dur_s" in ''|null) dur_s=0;; esac
    pos_ms=$(printf '%.0f' "$(echo "$pos_s * 1000" | bc -l 2>/dev/null || echo 0)")
    dur_ms=$(printf '%.0f' "$(echo "$dur_s * 1000" | bc -l 2>/dev/null || echo 0)")

    local hash="" cover=""
    hash=$(printf '%s|%s|%s' "$title" "$artist" "$album" | md5 -q 2>/dev/null)
    if [ -n "$hash" ]; then
        cover="$ART_DIR/$hash"
        if [ ! -s "$cover" ]; then
            nowplaying-cli get artworkData 2>/dev/null | base64 --decode > "$cover" 2>/dev/null
            [ -s "$cover" ] || cover=""
        fi
    fi
    write_json "$title" "$artist" "$album" "now-playing" "$pos_ms" "$dur_ms" "$status" "$cover" "$hash" true
    return 0
}

drain_commands() {
    [ -f "$COMMAND_PATH" ] || return
    local lines
    lines=$(cat "$COMMAND_PATH" 2>/dev/null)
    : > "$COMMAND_PATH"
    local line
    while IFS= read -r line; do
        case "$(echo "$line" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')" in
            playpause) run_control togglePlayPause playpause ;;
            next)      run_control next next ;;
            previous)  run_control previous previous ;;
        esac
    done <<< "$lines"
}

run_control() {
    # $1 = nowplaying-cli verb, $2 = applescript verb
    if [ "$has_npc" = "1" ]; then
        nowplaying-cli "$1" >/dev/null 2>&1 && return
    fi
    if [ "$(app_running Spotify)" = "true" ]; then
        case "$2" in
            playpause) osascript -e 'tell application "Spotify" to playpause' >/dev/null 2>&1 ;;
            next)      osascript -e 'tell application "Spotify" to next track' >/dev/null 2>&1 ;;
            previous)  osascript -e 'tell application "Spotify" to previous track' >/dev/null 2>&1 ;;
        esac
    elif [ "$(app_running Music)" = "true" ]; then
        case "$2" in
            playpause) osascript -e 'tell application "Music" to playpause' >/dev/null 2>&1 ;;
            next)      osascript -e 'tell application "Music" to next track' >/dev/null 2>&1 ;;
            previous)  osascript -e 'tell application "Music" to previous track' >/dev/null 2>&1 ;;
        esac
    fi
}

echo "Music Overlay macOS helper running (nowplaying-cli=$has_npc, AppleScript fallback)."

while true; do
    drain_commands
    if read_spotify; then :
    elif read_music; then :
    elif [ "$has_npc" = "1" ] && read_npc; then :
    else write_empty
    fi
    sleep "$POLL"
done
