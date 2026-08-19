# Music Overlay

A Fabric mod that shows whatever you're listening to as a small overlay inside Minecraft. Album art, a live progress bar, and time-synced lyrics, pulled straight from your system's media session so it works with Spotify, a browser tab, or any other player that shows up in your media keys.

No account linking, no API keys, no Spotify Premium requirement. If your OS knows a song is playing, the overlay shows it.

![icon](src/main/resources/assets/musicoverlay/icon.png)

## Features

- Reads the real OS media session, so it isn't tied to one app. Spotify, YouTube in a browser, Apple Music, foobar2000, whatever is playing wins.
- Album art rendered in game, updated as the track changes.
- Smooth progress bar that keeps moving between updates instead of ticking once a second.
- Time-synced lyrics from [LRCLIB](https://lrclib.net) (free, no key), with an active-line highlight and a timing offset if your player runs a little ahead or behind.
- Control playback from in game. Bind play/pause, next and previous and they drive the actual player.
- Five layout presets: card, compact, art only, text only, and minimal.
- Nine-point anchoring plus drag-to-place, so it sits exactly where you want at any GUI scale.
- Full colour theming through hex fields, per-element toggles, scale, opacity, rounded corners.
- Settings screen built in, and a ModMenu config button if you have ModMenu.
- Optional Spotify layer: browse playlists, add the current song to a playlist, like, queue, and start playlists from in game (needs a free Client ID; playback control needs Premium).

## Supported versions

| Minecraft | Loader | Fabric API |
|-----------|--------|------------|
| 26.2      | 0.19+  | required   |
| 26.1.2    | 0.19+  | required   |

Client side only. It doesn't need to be on the server and won't do anything if a server has it.

## Setup

Drop the jar for your Minecraft version into `mods/` alongside Fabric API. Then one small per-OS step so the mod can read your media session:

**Linux**: install `playerctl` (it talks to players over MPRIS/D-Bus):
```
sudo pacman -S playerctl      # Arch / CachyOS
sudo apt install playerctl    # Debian / Ubuntu
```

**Windows**: nothing to install. It reads the same System Media Transport Controls that power the little media popup in the corner.

**macOS**: Spotify and the Music app work out of the box through AppleScript. For any other app, install [`nowplaying-cli`](https://github.com/kirtan-shah/nowplaying-cli):
```
brew install nowplaying-cli
```

The mod ships the small helper script for each platform inside the jar and launches it for you. It writes a snapshot to a temp file that the game reads a few times a second.

## Using it

Open **Options -> Controls -> Music Overlay** to bind keys (all unbound by default so nothing clashes):

- Open settings
- Toggle the overlay
- Reposition (drag it around with a live preview)
- Play / pause, next, previous

Everything else lives in the settings screen, split into General, Layout, Elements, Lyrics and Colors. Changes save the moment you make them.

The config file is `config/musicoverlay.json` if you'd rather edit it by hand. Colours there are `#AARRGGBB`.

## Spotify controls (optional)

The overlay works with any player and needs no account. If you use Spotify and
want to do more than play/pause from in game, there's an optional layer that
talks to the Spotify Web API: browse your playlists, add the current song to a
playlist (or a one-key "quick add" playlist), like a track, add it to the queue,
skip, and start a whole playlist.

None of this is required. The overlay itself never asks for a login.

Two things to know before you start:

- **Your login stays on your PC.** Auth uses Authorization Code with PKCE, so there's no client secret involved. The only thing stored is the token Spotify hands back, saved locally in `config/musicoverlay-spotify.json`. This mod has no server and never sends your data anywhere except Spotify itself.
- **Playback control needs Spotify Premium.** Skip, queue, and starting a playlist go through Spotify's Web API, which only allows those on Premium (that's Spotify's rule, not the mod's). Browsing your playlists and adding songs to them work on a free account too.

Setup, step by step (the same steps show up in game the first time you use a Spotify feature):

1. Go to [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) and create an app.
2. In the app settings, add this exact Redirect URI: `http://127.0.0.1:54321/callback`
3. Copy the app's Client ID.
4. In game, open the Spotify menu (settings screen button or its keybind), paste the Client ID, and hit Connect. Approve access in the browser tab that opens, then close it.

Skip/queue/play need Spotify open on some device, since the Web API drives
whatever your account is actively playing on.

## How it works

Minecraft can't read your media session on its own, so the mod bundles a tiny helper per platform:

- Linux: `playerctl` over MPRIS
- Windows: SMTC through WinRT in PowerShell
- macOS: `nowplaying-cli` when present, otherwise AppleScript against Spotify / Music

The helper writes `nowplaying.json` (title, artist, position, art path, and so on) into a temp folder. The game polls that file, interpolates the progress so the bar stays smooth, and fetches synced lyrics when the track changes. Playback commands go back the other way through a small command file the helper watches.

## Building from source

Java 25 is required (Minecraft 26.x needs it).

```
./gradlew build                 # builds the default target (26.2)
./build-all.sh                  # builds a jar for every supported version
```

Jars land in `build/libs/musicoverlay-<version>+mc<mc>.jar`.

26.1 was the first non-obfuscated Minecraft, so there's no mappings step. The one thing that differs between 26.1.2 and 26.2 is where the HUD's `extractRenderState` hook lives (`Gui` vs the split-out `Hud`), handled by a per-version mixin picked from the `minecraft_version` property.

## License

MIT. Do what you like with it.
