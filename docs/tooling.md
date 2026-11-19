# Tooling

Kotlin + Jetpack Compose Material 3, Gradle Kotlin DSL, one `:app` module.
Pins mirror soundboard at M0 (2026-11-19), trimmed to an empty Compose app.

| Component | Pin | Location / purpose |
| --- | --- | --- |
| AGP | 9.2.0 | Android build plugin |
| Gradle | 9.4.1 | SHA-256-verified wrapper |
| Daemon JDK | 25 | `gradle/gradle-daemon-jvm.properties` |
| JVM bytecode | 17 | `android.compileOptions` |
| Kotlin / Compose compiler plugin | 2.3.20 | Compose sub-plugin only |
| Compose BOM | 2026.08.00 | UI and Material 3 versions |
| Core KTX | 1.19.0 | AndroidX core utilities |
| Activity Compose | 1.13.0 | Activity/Compose integration |
| DataStore | 1.2.1 | Typed JSON practice settings; matches soundboard |
| ktlint Gradle plugin | 14.2.0 | Uses bundled ktlint engine |
| Kotlin serialization plugin | 2.3.20 | `@Serializable` for `pack.json` |
| kotlinx.serialization JSON | 1.11.0 | `pack.json` (matches soundboard) |
| JUnit | 4.13.2 | JVM unit tests |
| Robolectric | 4.18.1 | Test only: runs the screenshot tests on the JVM |
| Roborazzi (+ Gradle plugin) | 1.79.0 | Test only: screenshot tests ([development](development.md#screenshot-tests)) |
| AndroidX Test ext JUnit | 1.3.0 | Test only: the AndroidJUnit4 runner |
| Compose UI test | BOM | Test only (`ui-test-manifest` in debug builds) |
| kxml2 | 2.3.0 | Test only: the real XmlPullParser in JVM tests (DECISIONS.md, "XML: Pull Parser, No DTDs, No Entities") |
| min / target / compile SDK | 26 / 36 / 37 | Review behavior changes before targeting 37 |

Dependencies and plugin pins live in `gradle/libs.versions.toml`. No navigation,
Media3, Room or DI libraries are added speculatively.
Renovate applies the existing seven-day release-age policy; lint warnings about
new versions are not a reason to bump opportunistically.

## AGP built-in Kotlin

**AGP 9+ compiles Kotlin itself. Do not apply `org.jetbrains.kotlin.android`**
(`kotlin-android`) in either Gradle file: doing so is a hard build error.
`org.jetbrains.kotlin.plugin.compose` still applies separately. JVM target
comes from `android.compileOptions.targetCompatibility`. See
<https://developer.android.com/build/migrate-to-built-in-kotlin>.

The daemon JDK pin is separate from both the wrapper's launcher JDK and the
app bytecode target. Gradle can provision JDK 25; use a JDK 21 installation to
launch it if needed. See [development](development.md).

## Checks and dependency constraints

`scripts/check-all.sh` runs `./gradlew check assembleDebug`; CI additionally
checks derived version files. `ktlintCheck`, `testDebugUnitTest` and `lintDebug`
can run individually without an emulator.

All shipped dependencies, transitively, must be free software: AndroidX or
Kotlin-class libraries. No Google Play Services, Firebase or closed-source SDKs.
No Play Services OSS-license plugin; the future licenses screen is hand-maintained.
