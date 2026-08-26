#!/bin/sh
set -eu
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
GRADLE_VERSION="8.2.2"
CACHE_DIR="${HOME}/.gradle/wrapper/dists/orbitshell-${GRADLE_VERSION}"
GRADLE_HOME="$CACHE_DIR/gradle-${GRADLE_VERSION}"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$CACHE_DIR"
  ZIP="$CACHE_DIR/gradle-${GRADLE_VERSION}-bin.zip"
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    curl -fL --retry 3 -o "$ZIP" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  fi
  rm -rf "$GRADLE_HOME.tmp"
  mkdir -p "$GRADLE_HOME.tmp"
  unzip -q -o "$ZIP" -d "$GRADLE_HOME.tmp"
  mv "$GRADLE_HOME.tmp/gradle-${GRADLE_VERSION}" "$GRADLE_HOME"
  rmdir "$GRADLE_HOME.tmp" 2>/dev/null || true
fi
exec "$GRADLE_HOME/bin/gradle" --project-dir "$ROOT_DIR" "$@"
