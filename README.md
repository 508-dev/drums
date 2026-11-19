# Ratamatap

Ratamatap is an offline Android app for practicing snare rudiments: a lesson library,
falling-note and sheet-notation views synchronized to drum playback, tempo and
speed-training controls, and a practice log. Hit detection comes after v0.1.

**Status: pre-release.** The practice app is under active development; the
first public release is planned for M4. Hit detection and networking come later.

## Roadmap

- M0: Android repository and build skeleton.
- M1–M3: timing core, notation, lessons and practice UI.
- M4: v0.1.0 via GitHub Releases and our self-hosted F-Droid repository.
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
