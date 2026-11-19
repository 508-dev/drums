#!/usr/bin/env bash
# Everything CI runs: ktlint, Android lint, unit tests, debug assembly.
# No emulator required.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# Screenshots are compared with the committed references (Roborazzi).
./gradlew check assembleDebug -Proborazzi.test.verify=true
