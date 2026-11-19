# Development

Drums has one Android `:app` module and no host services or infrastructure.
Open the repo root in Android Studio, let Gradle sync, choose a device and Run.

## CLI setup

Install Android SDK platform 37, build tools and platform-tools. Set
`ANDROID_HOME=$HOME/Android/Sdk`, or put `sdk.dir=/absolute/path/to/Android/Sdk`
in gitignored `local.properties`. Accept SDK licenses with the SDK manager.
No local signing properties are required for debug or beta compile checks.

Use a local JDK (JDK 21 is suitable) to launch `gradlew`; for example:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew --version
```

Use the actual JDK path on your system. The daemon is pinned to **JDK 25**,
matching soundboard. Gradle provisions it from the committed Foojay URLs if not
already available; the first build therefore needs network access. The compiled
app targets JVM 17, independent of the daemon JDK.

## Commands

```bash
./scripts/lint.sh                # ktlint and Android lint
./scripts/test.sh                # JVM unit tests
./scripts/check-all.sh           # check + assembleDebug, no emulator
./scripts/sync-version.sh --check
./gradlew ktlintFormat
./gradlew assembleBeta           # release shrinking, separate .beta identity
./gradlew installDebug
adb shell am start -n dev.co508.drums.debug/dev.co508.drums.MainActivity
```

APK outputs: `app/build/outputs/apk/{debug,beta}/`. Debug uses
`dev.co508.drums.debug`, beta uses `dev.co508.drums.beta`; both install beside
production. Without beta secrets, beta uses the standard Android debug key.
`assembleRelease` is unsigned without release credentials.

## Validation limits

The M0 JVM test is a trivial wiring smoke test. Launch the placeholder on a
device to check UI/insets/rotation; compilation alone does not verify these.
The audio mixer's frame placement, mixing and allocation-free rendering are
unit-tested on the JVM (`app/src/test/.../audio/`); the `AudioTrack` output
itself is not. M1 must measure audio/visual timing on the Sony Xperia 10 VII;
unit tests cannot prove those timing goals. Instrumented tests wait
until the relevant device-dependent behavior exists.

## Local state

`.worktreeinclude` allows `local.properties` and `keystore.properties` to be
copied to sibling worktrees. Both are ignored; never copy another app's signing
key. Agents must never generate signing keys or secrets. `.context/` is ignored
scratch only; move durable findings into tracked docs, not raw logs.
