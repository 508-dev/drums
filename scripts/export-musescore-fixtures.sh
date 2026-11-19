#!/usr/bin/env bash
# Re-export the MuseScore reference fixtures: every .mscz under
# app/src/test/resources/musicxml/musescore-*/ becomes a .musicxml beside it.
# Needs MuseScore 4 (`mscore`, or set MSCORE). Not run in CI.
# See docs/musicxml-style.md.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

mscore="${MSCORE:-mscore}"
if ! command -v "$mscore" >/dev/null; then
  echo "MuseScore 4 not found; install it or set MSCORE" >&2
  exit 1
fi
QT_QPA_PLATFORM=offscreen "$mscore" --version

shopt -s nullglob
for score in app/src/test/resources/musicxml/musescore-*/*.mscz; do
  echo "export ${score%.mscz}.musicxml"
  QT_QPA_PLATFORM=offscreen "$mscore" -o "${score%.mscz}.musicxml" "$score"
done
