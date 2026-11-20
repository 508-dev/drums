# Ratamatap

Ratamatap is an offline Android app for practicing snare rudiments: a lesson library,
falling-note and sheet-notation views synchronized to drum playback, tempo and
speed-training controls, and a practice log. Hit detection comes after v0.1.

**Status: v0.1.0, the first public release.** It's a play-along practice app:
it doesn't listen to or score your playing yet. Hit detection comes in v0.2.0.

## Install

Android 8.0 (API 26) or newer.

- **F-Droid (self-hosted repository):** in the F-Droid app, add this repository:
  `https://508-dev.github.io/ratamatap/fdroid/repo?fingerprint=BEDB25359D045E86E61A7901A0D2716DF6447EBF1EBDCB1FAC565FBB7C8F4C7C`.
  Check that the fingerprint matches
  `BE:DB:25:35:9D:04:5E:86:E6:1A:79:01:A0:D2:71:6D:F6:44:7E:BF:1E:BD:CB:1F:AC:56:5F:BB:7C:8F:4C:7C`.
  Updates arrive through F-Droid. Ratamatap isn't on f-droid.org yet; that's
  planned for v0.3.0.
- **APK:** download the latest signed APK from
  [GitHub Releases](https://github.com/508-dev/ratamatap/releases/latest), and
  check it against the `.sha256` file beside it.

Both channels are signed with the same key, so you can switch between them
without reinstalling.

## Roadmap

- M0: Android repository and build skeleton.
- M1–M3: timing core, notation, lessons and practice UI.
- M4: v0.1.0 via GitHub Releases and our self-hosted F-Droid repository (released 2026-11-20).
- M5: microphone hit detection (v0.2.0).
- M6: lesson imports and f-droid.org submission (v0.3.0).
- M7–M8: design paid packs and a teacher marketplace; Google Play undecided.

See the [roadmap](docs/roadmap.md) and [decisions](DECISIONS.md).

## Build and run

Open in Android Studio, or install Android SDK platform 37 and a JDK to launch
the wrapper (JDK 21 works; Gradle provisions its pinned JDK 25 daemon).
Set `ANDROID_HOME` or create a gitignored `local.properties` with
`sdk.dir=/absolute/path/to/Android/Sdk`.

```bash
./scripts/check-all.sh
./scripts/sync-version.sh --check
./gradlew installDebug
adb shell am start -n dev.co508.ratamatap.debug/dev.co508.drums.MainActivity
```

Android 8.0 (API 26) or newer is required. See [development](docs/development.md),
[release setup](docs/deployment.md), and [beta builds](docs/beta-builds.md).

## License

App code is **GPL-3.0-only**; see [LICENSE](LICENSE). Built-in lessons
are CC BY-SA 4.0; audio samples must be CC0 or CC BY with per-file credits.
Dependencies must be free software;
there are no Google Play Services, Firebase or proprietary SDKs.
