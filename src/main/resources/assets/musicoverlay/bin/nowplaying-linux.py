#!/usr/bin/env python3
"""
Music Overlay - Linux now-playing helper.

Reads the active MPRIS media session through `playerctl`, writes a small JSON
snapshot the mod polls, downloads album art into a cache dir, and drains a
command file so the in-game keybinds can drive play/pause/skip against whatever
app owns the session (Spotify, a browser, a native player, anything MPRIS).

No third-party Python packages - just the standard library and the `playerctl`
binary, which is packaged on every mainstream distro.
"""

import base64
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

POLL_SECONDS = 0.25

BASE_DIR = os.environ.get(
    "MC_NOWPLAYING_DIR", os.path.join(tempfile.gettempdir(), "musicoverlay")
)
ART_DIR = os.path.join(BASE_DIR, "art")
JSON_PATH = os.path.join(BASE_DIR, "nowplaying.json")
COMMAND_PATH = os.path.join(BASE_DIR, "command")

METADATA_FORMAT = (
    "{{title}}\x1f{{artist}}\x1f{{album}}\x1f"
    "{{mpris:length}}\x1f{{mpris:artUrl}}\x1f{{playerName}}"
)


def log(msg):
    print(msg, flush=True)


def run(args, timeout=1.5):
    """Run a playerctl command, returning stripped stdout or None on failure."""
    try:
        out = subprocess.run(
            args,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
        if out.returncode != 0:
            return None
        return out.stdout.strip()
    except (subprocess.TimeoutExpired, OSError):
        return None


def cache_art(art_url):
    """Resolve an MPRIS artUrl to a local PNG/JPG path, cached by URL hash."""
    if not art_url:
        return "", ""
    digest = hashlib.md5(art_url.encode("utf-8")).hexdigest()
    target = os.path.join(ART_DIR, digest)
    if os.path.exists(target) and os.path.getsize(target) > 0:
        return target, digest
    try:
        os.makedirs(ART_DIR, exist_ok=True)
        if art_url.startswith("file://"):
            src = urllib.parse.unquote(urllib.parse.urlparse(art_url).path)
            if os.path.exists(src):
                shutil.copyfile(src, target)
        elif art_url.startswith("data:"):
            header, _, payload = art_url.partition(",")
            raw = base64.b64decode(payload) if ";base64" in header else payload.encode()
            with open(target, "wb") as fh:
                fh.write(raw)
        elif art_url.startswith("http://") or art_url.startswith("https://"):
            req = urllib.request.Request(art_url, headers={"User-Agent": "MusicOverlay"})
            with urllib.request.urlopen(req, timeout=4) as resp, open(target, "wb") as fh:
                shutil.copyfileobj(resp, fh)
        else:
            return "", ""
        if os.path.exists(target) and os.path.getsize(target) > 0:
            return target, digest
    except Exception as exc:  # noqa: BLE001 - art is best-effort, never fatal
        log(f"art fetch failed: {exc}")
    return "", ""


def pick_player():
    """Choose the most relevant player: prefer one that's actually playing,
    otherwise fall back to playerctl's default (first available). This keeps a
    paused browser tab from hiding the song you're actually listening to."""
    listing = run(["playerctl", "-l"])
    if not listing:
        return None
    players = [p for p in listing.splitlines() if p.strip()]
    if not players:
        return None
    for name in players:
        if (run(["playerctl", "-p", name, "status"]) or "") == "Playing":
            return name
    return players[0]


def read_snapshot():
    player = pick_player()
    base = ["playerctl"] + (["-p", player] if player else [])

    meta = run(base + ["metadata", "--format", METADATA_FORMAT])
    if meta is None:
        return None  # no player available
    parts = meta.split("\x1f")
    while len(parts) < 6:
        parts.append("")
    title, artist, album, length_us, art_url, player = parts[:6]

    status = run(base + ["status"]) or "Stopped"

    position_s = run(base + ["position"])
    try:
        position_ms = int(float(position_s) * 1000) if position_s else 0
    except ValueError:
        position_ms = 0

    try:
        duration_ms = int(int(length_us) / 1000) if length_us else 0
    except ValueError:
        duration_ms = 0

    cover_path, cover_hash = cache_art(art_url)

    return {
        "title": title,
        "artist": artist,
        "album": album,
        "sourceApp": player,
        "positionMs": position_ms,
        "durationMs": duration_ms,
        "status": status,
        "coverPath": cover_path,
        "coverHash": cover_hash,
        "canControl": True,
        "timestamp": int(time.time() * 1000),
    }


EMPTY = {
    "title": "", "artist": "", "album": "", "sourceApp": "",
    "positionMs": 0, "durationMs": 0, "status": "Stopped",
    "coverPath": "", "coverHash": "", "canControl": False,
    "timestamp": 0,
}


def write_json(data):
    os.makedirs(BASE_DIR, exist_ok=True)
    tmp = JSON_PATH + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        json.dump(data, fh)
    os.replace(tmp, JSON_PATH)  # atomic - the poller never sees a half-written file


def drain_commands():
    if not os.path.exists(COMMAND_PATH):
        return
    try:
        with open(COMMAND_PATH, "r+", encoding="utf-8") as fh:
            lines = fh.read().splitlines()
            fh.seek(0)
            fh.truncate()
    except OSError:
        return
    mapping = {"playpause": "play-pause", "next": "next", "previous": "previous"}
    player = pick_player()
    base = ["playerctl"] + (["-p", player] if player else [])
    for line in lines:
        action = mapping.get(line.strip().lower())
        if action:
            run(base + [action])


def main():
    if shutil.which("playerctl") is None:
        log("playerctl is not installed; the overlay cannot read your media session.")
        write_json(EMPTY)
        return
    log("Music Overlay Linux helper running (playerctl / MPRIS).")
    while True:
        try:
            drain_commands()
            snap = read_snapshot()
            write_json(snap if snap else EMPTY)
        except Exception as exc:  # noqa: BLE001 - keep the loop alive no matter what
            log(f"loop error: {exc}")
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
