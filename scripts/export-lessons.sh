#!/usr/bin/env bash
# Export the built-in lessons from their MuseScore sources:
#   lessons/builtin/<id>.mscz -> app/src/main/assets/packs/builtin/<id>.musicxml
#                             -> lessons/builtin/renders/<id>.png (reference render)
# Needs MuseScore 4 (`mscore`, or set MSCORE). Not run in CI; the exports are
# committed, and PackLoaderTest checks them. See docs/lessons.md.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

mscore="${MSCORE:-mscore}"
if ! command -v "$mscore" >/dev/null; then
  echo "MuseScore 4 not found; install it or set MSCORE" >&2
  exit 1
fi
QT_QPA_PLATFORM=offscreen "$mscore" --version

assets=app/src/main/assets/packs/builtin
renders=lessons/builtin/renders
mkdir -p "$renders"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

shopt -s nullglob
for score in lessons/builtin/*.mscz; do
  id="$(basename "$score" .mscz)"
  echo "export $id"
  QT_QPA_PLATFORM=offscreen "$mscore" -o "$assets/$id.musicxml" "$score"
  # PNG export names pages <name>-1.png, <name>-2.png…; lessons are one page.
  QT_QPA_PLATFORM=offscreen "$mscore" -r 150 -T 20 -o "$tmp/$id.png" "$score"
  mv "$tmp/$id"*.png "$renders/$id.png"
done
