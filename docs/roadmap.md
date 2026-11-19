# Roadmap

Each milestone below is a GitHub milestone of the same name. Issues inside a
milestone are ordered with GitHub's "blocked by" links, and a milestone starts
once the previous one's exit criteria are met. The reasoning behind each choice
is in [`DECISIONS.md`](../DECISIONS.md).

| Milestone | Ships | Goal |
| --- | --- | --- |
| M0 Cleanup and skeleton | — | A clean Android repo that builds in CI |
| M1 Timing core | — | Sample-exact playback and a clock the visuals can trust |
| M2 Notation | — | Lessons from MusicXML, drawn in both views, in sync |
| M3 App | — | v0.1 feature-complete, with all content |
| M4 Release | v0.1.0 | First public release |
| M5 Hit detection | v0.2.0 | The app listens and shows how far off you were |
| M6 Import and f-droid.org | v0.3.0 | Your own lessons; listing on f-droid.org |
| M7 Paid packs | — | Design session first |
| M8 Teacher marketplace | — | Design session first |

## M0 — Cleanup and skeleton

**Scope:**

- **Delete the devkit's non-Android parts:**
  - the TypeScript, Python and Ruby stacks
  - the Bun, Biome and pnpm files
  - the Docker Compose files and `.dockerignore`
  - `.pre-commit-config.yaml`, `.sops.yaml.example` and `.env.example`
  - `extras/`, except `extras/github`
  - the devkit's `skills/`
  - the devkit-only docs, plus `frontend.md`, `interfaces.md` and
    `observability.md`
  - the ports, Compose and dev scripts
  - the non-Android CI jobs
- **Adopt `stacks/android`, then delete `stacks/`:**
  - the release, PR-title and beta workflows
  - the version, F-Droid and beta-signing scripts
  - the store text, F-Droid metadata and beta resources
  - `docs/beta-builds.md`
- Switch LICENSE to GPL-3.0.
- Rewrite README, AGENTS.md, CLAUDE.md, `llms.txt`, CONTRIBUTING, SECURITY and
  the remaining docs for this app. Keep `MANIFEST.md` as a record of the devkit.
- Add an empty Compose app (`dev.co508.drums`) that mirrors `soundboard`'s
  Gradle setup.
- Turn `scripts/{lint,test,check-all}.sh` into Gradle wrappers.
- Make CI a Gradle build with unit tests, ktlint and a version-drift check.

**Exit criteria:**

- CI is green on `main`.
- `./gradlew assembleDebug` works from a clean checkout.
- The beta workflow builds a beta APK.
- The maintainer has created the beta signing key and secrets.

## M1 — Timing core

**Scope:**

- The `AudioEngine` interface and its Kotlin `AudioTrack` mixer: float PCM,
  low-latency mode, voices, panning, a generated click and a placeholder snare
  sample.
- A transport (tempo, loop, count-in, tempo changes) that converts between audio
  frames and musical time.
- An audio-clock "now" for the UI.
- A debug-only timing diagnostics screen with a visual metronome.

**Exit criteria, on the Sony Xperia 10 VII:**

- No audio underruns in 10 minutes of 32nd notes at 300 BPM.
- The visual metronome is visibly locked to the click.
- The measurements are recorded in `docs/timing.md`.

If any of these fail, decide on Oboe before M2 starts.

## M2 — Notation

**Scope:**

- `docs/musicxml-style.md`, validated against real MuseScore 4 exports,
  including the sticking and grace-note convention.
- The lesson model, the MusicXML importer and the `pack.json` loader.
- Expanding written notes into timed strokes: grace timing, tremolo diddles and
  buzz rolls.
- The seven-rudiment test set.
- The falling-notes view, then the sheet view.
- A minimal practice screen with tempo controls.
- Roborazzi screenshot tests.

**Exit criteria:** the seven test rudiments import, render in both views and
play in sync on the Sony, and the screenshot tests run in CI.

## M3 — App

**Scope:**

- The library and navigation.
- The complete practice screen: every v0.1 control, the collapsible tips, the
  remembered view, keeping the screen on, portrait and landscape.
- The practice log and History screen.
- The Settings and licenses screens.
- The drum samples, sourced and credited.
- All 40 rudiments and the Timing basics pack.
- A drummer's check of every lesson on a beta build.

**Exit criteria:** every lesson is verified by a drummer, and every v0.1 control
works in both orientations.

## M4 — Release v0.1.0

**Scope:**

- The name brainstorm and the final application ID.
- The icon, store text and screenshots.
- The release signing key and secrets (maintainer).
- Merging the release PR, which ships the GitHub Release APK and puts the
  self-hosted F-Droid repository live.

**Exit criteria:** v0.1.0 installs from GitHub Releases and from the
self-hosted F-Droid repository.

## M5 — Hit detection (v0.2.0)

**Scope:**

- Mic capture and onset detection on the shared audio clock.
- Calibration: automatic loopback through the speaker, tap-along with
  headphones.
- Matching hits to notes, with strictness windows.
- Per-note marks in both views.
- A run summary, with its stats saved to the practice log.
- Speaker mode: snare muted, click pitched into a narrow band.
- The v0.2.0 release.

**Exit criteria:** on the Sony, with headphones, hits on a practice pad match
the right notes, and their offsets are consistent across repeated runs.

## M6 — Import and f-droid.org (v0.3.0)

**Scope:**

- Importing `.musicxml` and `.mxl` files and zipped packs through the system
  file picker.
- Extracting the snare part from multi-part scores, and reporting notation we
  don't support.
- A neutral middle lane, plus a banner, for notes without sticking.
- The v0.3.0 release, then the f-droid.org submission.

**Exit criteria:** a teacher's MuseScore export of a snare part imports and
plays, and the f-droid.org submission is accepted.

## M7 — Paid packs, and M8 — Teacher marketplace

Each starts with its own design session. Topics:

- **M7:** backend, accounts, payment provider, sales tax, copy protection (or
  none), the Google Play question, and the network permission.
- **M8:** teacher uploads, review, payouts, revenue split and the teacher
  agreement.

## Backlog

These have no milestone yet:

- Silent bars: the click drops out to test your time.
- Tap tempo.
- MIDI input.
- A horizontal falling-notes view.
- Hand drums.
- Suggested sticking for files that have none.
- Accent detection.
- The vibration experiment, after v0.2.0.
