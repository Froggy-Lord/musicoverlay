#!/usr/bin/env bash
# Build a Music Overlay jar for every supported Minecraft version.
# Output lands in build/libs/musicoverlay-<modver>+mc<mcver>.jar
set -euo pipefail
cd "$(dirname "$0")"

build() {
    local mc="$1" fabric="$2" loom="$3" loader="$4" modmenu="$5"
    echo "==> building for Minecraft $mc"
    ./gradlew build \
        -Pminecraft_version="$mc" \
        -Pfabric_api_version="$fabric" \
        -Ploom_version="$loom" \
        -Ploader_version="$loader" \
        -Pmod_menu_version="$modmenu" \
        --no-daemon --max-workers=4 --console=plain
}

# Default target first (26.3), then the preserved earlier targets.
build 26.3   0.161.0+26.3   1.16.3        0.19.5 21.0.0
build 26.2   0.152.2+26.2   1.16.3        0.19.3 20.0.0-beta.3
build 26.1.2 0.154.2+26.1.2 1.16-SNAPSHOT 0.19.3 20.0.0-beta.3

echo
echo "Done. Jars:"
ls -1 build/libs/*.jar | grep -v sources
