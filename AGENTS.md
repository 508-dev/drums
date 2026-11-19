# Agent Development Guide

## Scope and authority

This is a single-module native Android app: Kotlin, Jetpack Compose Material 3,
Gradle Kotlin DSL. Read `DECISIONS.md` and `docs/roadmap.md` before feature work.
They are the product/architecture authority. `MANIFEST.md` is historical devkit
inventory, not the current file list. No Node, Python, Ruby or Docker stack.

## Build and validation

- Prefer `./gradlew` and repo scripts. Inspect scripts before running them.
- Android SDK: use `ANDROID_HOME` or gitignored `local.properties`. Do not guess
  a successful build when the SDK or network is missing.
- The wrapper pins Gradle 9.4.1; `gradle/gradle-daemon-jvm.properties` pins JDK
  25 as in soundboard. A local JDK 21 can launch the wrapper; Gradle can download
  the daemon JDK. Java bytecode target is 17.
- AGP 9 has built-in Kotlin: **never apply kotlin-android**. The Compose
  compiler sub-plugin is still required.

```bash
./scripts/lint.sh              # ktlintCheck lintDebug
./scripts/test.sh              # testDebugUnitTest
./scripts/check-all.sh         # check assembleDebug
./scripts/sync-version.sh --check
./gradlew assembleBeta         # debug-key fallback without beta credentials
```

Use `./gradlew ktlintFormat` for Kotlin formatting. JVM tests need no emulator;
`installDebug` needs a device. Build/test success does not verify sound or visual
timing. Instrumented tests are deferred. Report what was actually tested.

## Editing

- Read target code, callers and tests. Keep changes surgical and test behavior.
- All code is in `app/`; release metadata is in `fastlane/` and `fdroid/`.
- Keep shipped dependencies transitively free software: AndroidX/Kotlin-class
  libraries only. No Play Services, Firebase, analytics, ads or closed SDKs.
- No DI framework; construct shared objects in `DrumsApp` when needed.
- Do not add permissions speculatively. No INTERNET permission through v0.3.
- Pins live in `gradle/libs.versions.toml`; prefer Renovate and update
  `docs/tooling.md` for deliberate changes. Do not add unused future libraries.
- Once the licenses screen exists, maintain its dependency list by hand when
  adding/removing shipped libraries; never use the Play Services license plugin.
- Update docs when changing developer workflows.

## Releases and secrets

- `version.txt` is the version source. release-please updates it and CHANGELOG.
  Run `scripts/sync-version.sh` after a deliberate version change and commit
  generated Gradle literals and store notes. Do not hand-edit derived values.
- `versionCode` and `versionName` must be plain literals, each on its own line
  in `app/build.gradle.kts`; F-Droid parses them textually.
- Beta-only overrides belong in `androidComponents`, not defaultConfig.
- Store copy belongs in `fastlane/metadata/android/`; do not duplicate it.
- **Agents never create signing keys or secrets**, execute
  `scripts/setup-beta-signing.sh`, push tags, create releases or submit to stores
  unless the maintainer explicitly authorizes the relevant action. Key/secret
  creation remains human-only. Release and beta keys must be separate.
- Never read aloud, print, stage or commit real signing properties, keystores,
  passwords or `RATAMATAP_KEYSTORE_*`, `RATAMATAP_BETA_KEYSTORE_*`, `FDROID_*` values.
- Google Play is deferred to M7. Do not enable it or a new distribution target
  without a product decision. See `docs/deployment.md` and `docs/secrets.md`.

## Workspace scratch

`.context/` is gitignored workspace-local scratch. Never commit it. Durable
knowledge belongs in tracked README/docs/DECISIONS, not raw logs/transcripts.
Treat unfamiliar changes as user work; do not reset or discard them.
