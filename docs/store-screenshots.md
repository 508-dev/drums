# Store screenshot capture notes

The six numbered PNGs in
`fastlane/metadata/android/en-US/images/phoneScreenshots/` were captured on the
Sony Xperia 10 VII (XQ-FE72, Android 16 / API 36) on 2026-11-20, after the
drummer check in #23. They show
Ratamatap Beta built with `assembleBeta` from `91d4f3a` (the merged icon/store-text
change). Beta is the release-like, R8-shrunk build without debug-only UI; this
local install uses the debug-key fallback, not a release signing key.

| File | Screen |
| --- | --- |
| `1.png` | Library, Rudiments families |
| `2.png` | Flam Accent, falling notes during playback, both hand highlights visible |
| `3.png` | Flam Accent, sheet notation during playback, orange playhead and highlighted note |
| `4.png` | Speed trainer during playback, at 70 BPM with the next +5 BPM step shown |
| `5.png` | History, containing only the two sessions generated while capturing these images |
| `6.png` | Settings, default practice sounds and sound/visual offset |

These are real-device captures, not test references or mockups. Each image is
1080×2276: only the top 64-pixel system status bar was cropped from the native
1080×2340 screenshot. App content and the navigation bar were not altered or
resized. PNG compression is lossless, metadata is stripped, and each file is
under 140 KB. No personal history or notifications appear.

## Refresh

1. Build from the intended commit with
   `ANDROID_HOME="$HOME/Android/Sdk" ./gradlew assembleBeta`.
2. Choose the phone's current wireless-adb serial. Always use `adb -s "$SERIAL"`:
   an mDNS transport may also list the same phone. Install
   `app/build/outputs/apk/beta/app-beta.apk` and open Ratamatap Beta. Do not clear
   or overwrite a user's existing practice history; use a separate fresh app
   install/profile if the screenshot instance already has personal data.
3. Note the current `sysui_demo_allowed` value, enable System UI demo mode, set
   a fixed clock and battery, and hide notifications. These captures used 09:41
   and 100% battery; the status bar was then cropped consistently from all six.
4. Navigate normally. Capture falling notes and sheet views while playback is
   running, not paused, and select frames where the highlights/playhead show.
   The trainer capture used start 60 BPM, +5 BPM every loop. History must contain
   only the demonstration sessions, never personal practice data.
5. Save a raw frame and crop/compress it (ImageMagick):

   ```bash
   "$HOME/Android/Sdk/platform-tools/adb" -s "$SERIAL" exec-out screencap -p > "$RAW"
   magick "$RAW" -crop 1080x2276+0+64 +repage -strip \
     -define png:compression-level=9 \
     fastlane/metadata/android/en-US/images/phoneScreenshots/1.png
   ```

   Recheck the phone's dimensions before reusing these crop values. Review every
   resulting image for clipping, private data, highlights, and debug controls.
6. Stop playback, exit demo mode and restore the original `sysui_demo_allowed`
   value. This capture restored it to `0` and force-stopped only Ratamatap Beta.

Keep store images in fastlane only. `scripts/fdroid-publish.sh` copies that tree
into the self-hosted repository metadata; no second image set is needed in
`fdroid/`. Roborazzi references under `app/src/testDebug/screenshots/` are
independent and were not changed for these store captures.
