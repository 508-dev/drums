# Drums Decisions

Why this app is built the way it is. Each entry records a call that was
genuinely open and the reasoning that closed it — not a description of the
code. Most were settled in the planning session of 2026-11-18/19. Add an entry
whenever you make a call a future reader would otherwise have to
reverse-engineer. The milestone plan lives in [`docs/roadmap.md`](docs/roadmap.md).

## Product

### Snare Rudiments, One Instrument Forever

The app helps drummers of every level practice snare-drum rudiments. Every
lesson has exactly one instrument: snare or practice pad now, possibly small
hand drums later, never a full drum kit. Each note carries a stroke from its
instrument's sound set (snare: normal, rimshot, buzz…), a hand, an accent or
dynamic, and ornaments. A hand drum can later arrive as a new sound set plus
samples, not a new model. The single-instrument scope is also what makes our
own notation renderer feasible (see "Our Own Notation Renderer").

### v0.1 Is Play-Along; Scoring Comes Next

v0.1 is a lesson library, animated notation, drum playback in sync with it,
tempo controls and a practice log. Hit detection — the app listening to the
player — is the next milestone (M5). It brings latency calibration and the mic
hearing the app's own sounds, which would double the first release's risk. v0.1
is still built on the single clock scoring will need ("One Audio Clock").

### Offline, No INTERNET Permission

v0.1 through v0.3 request no network permission. Content ships inside the app
or arrives as files through the system file picker. The network arrives with
paid packs (M7), which get their own design session.

### Two Practice Views; Falling Notes Is The Default

- **Falling notes** (default): notes fall top to bottom onto a hit line near the
  bottom and light up as they cross it.
- **Sheet**: the rudiment engraved as standard notation with a playhead
  sweeping across it; each note and its sticking light up as the playhead
  reaches them.

The app remembers which view was used last. Falling notes reads at a glance,
shows sticking spatially, and suits portrait, where the long axis becomes
look-ahead. The sheet view stays because reading notation is a core drumming
skill and teachers' material looks like it. Both views read the same lesson
model and the same clock. Portrait and landscape are both supported. The
falling-notes view stays vertical in both orientations for now; a horizontal
variant is in the backlog.

### Falling Notes: Hands Are Lanes

Two lanes, L on the left and R on the right, each colored per hand. Symbols come
from standard notation (Bravura glyphs): noteheads, accent `>`, ghost notes in
parentheses, rimshot notehead.

- **Grace notes:** a flam's grace note is a small notehead in the other lane,
  just *below* its main note, a fixed gap per grace note whatever the zoom.
  Time runs up the screen, so below is earlier: the grace note crosses the
  hit line, and lights up, just before its main note, as it sounds. (The
  first draft of this decision said "just above", which would make a grace
  note arrive after the note it leads into.)
- **Accents** sit beside the notehead rather than above it. Above is later
  in time, where the next note in the lane may already be.
- **Diddles and buzzes:** a diddle is two notes in one lane; a buzz is a
  notehead with a "z" and a tail as long as the buzz.
- **Grid:** thin lines mark beats, thick lines mark bars, and optional
  counting labels (1 e & a) sit in the margin.
- **Lighting:** notes light up as they cross the hit line, judged by where
  they are drawn, and fade once past it.

Rudiments are about sticking, and lanes make sticking visible without reading
letters. Notes with no sticking (possible in imported files, M6) fall in a
neutral middle lane, with a banner saying the file has no sticking.

### Linear Time In Both Views

Distance on screen is proportional to musical time. In the falling-notes view
the distance per beat is fixed (a zoom control changes it), so a pattern looks
the same at any tempo and simply scrolls faster. The sheet view uses
proportional spacing too, so its playhead moves at a constant, predictable
speed. Grace notes get a minimum visual gap so they stay legible.

### Practice Controls

v0.1 ships:

- a tempo slider (30–300 BPM) with ±1 and ±5 buttons
- a count-in bar
- loop
- click and snare playback, each with on/off and volume (snare off means
  playing along to the click alone)
- a speed trainer: start tempo, +N BPM every M loops, up to a target
- stereo-panned hands (R slightly right, L slightly left)
- lead with left, which swaps every R and L

Tempo is shown in the lesson's beat unit: a quarter note, or a dotted quarter in
compound meters. Tap tempo and "tempo as % of target" are left out on purpose;
tap tempo is a rabbit hole for little value. Other ideas wait in the backlog.
Tempo changes cost nothing because playback is generated from note events, not
recorded audio.

### Progress: Per-Lesson Settings And A Practice Log

Each lesson remembers its last tempo, its trainer settings and a favorite flag.
Every session is logged (date, lesson, minutes, tempos reached) and shown on a
History screen with the best tempo per lesson. The log is also where
hit-detection scores attach in M5. There are no streaks, goals or unlocks.

### Screens

- **Library** (home): packs, then families (Timing basics, Rolls, Diddles,
  Flams, Drags), then lessons with their level, a favorite star and the last
  tempo. History and Settings are icons in its top bar; there is no bottom
  navigation bar.
- **Practice** opens directly from a lesson, with no detail page in between. The
  description and tips sit in a collapsible panel, the view toggle at the top,
  and play/stop, tempo and the speed trainer at the bottom. The screen stays on
  while playing.
- **History**: the practice log and the best tempo per lesson.
- **Settings**: default sounds and volumes, the sound/visual offset, the default
  count-in, and about/licenses.

Speed of use wins over ceremony.

### Content: The 40 Standard Rudiments, In Our Own Words

The built-in content is the 40 standard rudiments (the PAS International Drum
Rudiments list) in their four families. Each rudiment has a description,
sticking and a suggested tempo range. A Timing basics pack (single strokes as
quarters, 8ths, triplets and 16ths) sits alongside. The patterns are
traditional; PAS's published sheets and wording are not, so we write our own
notation and text and never imply PAS endorsement. Method books such as
*Stick Control* are treated as copyrighted.

Development starts from a seven-rudiment test set that covers every notation
feature: single stroke roll, double stroke open roll, single paradiddle, flam,
drag, multiple bounce roll, and one triplet-based rudiment.

### Sounds

The sound set:

- snare samples at three dynamics (ghost, normal, accent), with a few variations
  each so repeats don't sound robotic
- a buzz sound for multiple-bounce rolls
- a click generated in code, with an accented downbeat

Samples must be CC0 or CC BY, verified per file, and credited on the licenses
screen; VSCO 2 CE is a candidate source. Recording our own is a later upgrade.

## Architecture

### Native Android, Following `soundboard`

This repo began as the 508.dev devkit, which is web-shaped. Its stacks were
deleted and its hygiene kept; `MANIFEST.md` records the devkit inventory. The
Android conventions deliberately match the sibling app `soundboard`:

- a single `:app` module
- the same AGP, Kotlin and Compose pins (re-checked at scaffold time)
- ktlint, and `scripts/*.sh` as `./gradlew` wrappers
- Jetpack Compose with Material 3
- no dependency-injection framework: a handful of objects built in the
  `Application` subclass
- `minSdk 26`, with `targetSdk` one below `compileSdk`

Where this app differs from `soundboard`, an entry here says why.

### One Audio Clock

Everything time-based reads one clock: the audio output stream's frame
position. Hits are scheduled at exact sample frames. The visuals compute "now"
from `AudioTrack` timestamps rather than a separate timer, so sound and picture
cannot drift apart. Hit detection (M5) maps mic input timestamps onto the same
clock. A sound/visual offset setting covers devices that misreport their output
latency, such as some Bluetooth headsets.

### Kotlin AudioTrack Mixer, Oboe Only If Needed

A Kotlin mixer thread writes one continuous float PCM stream to an `AudioTrack`
in low-latency performance mode. It places every sample at its exact frame and
handles voice mixing and panning. The goal is stable, sample-exact timing, not
minimum latency: a slightly larger buffer absorbs garbage-collection pauses,
and the extra latency is invisible because the visuals follow the audio clock.

SoundPool and ExoPlayer cannot schedule to the sample, so neither is used. The
engine sits behind an `AudioEngine` interface. Oboe (C++) replaces it only if
M1's measurements on the reference device, a Sony Xperia 10 VII, fail.

M1 measured it on the Sony (`docs/timing.md`). There were no underruns in 10
minutes of 32nd notes at 300 BPM with a fast track and the 40 ms buffer, so
Oboe is not needed.

### The Mixer Pulls Events, One Block At A Time

Nothing is queued in the engine. Before rendering each block (one device
burst), the mixer thread asks an `EventSource` for the events that start inside
that block, then starts each one on its exact frame. The source, the transport
from M1 on, computes those events from the pattern and the current tempo.

A push queue was rejected: events queued ahead would have to be found and
cancelled whenever the tempo slider moves, the loop changes or the player
stops. Pulling makes a tempo change take effect within one output buffer with
nothing to undo. The cost is that the source runs on the mixer thread and must
not allocate or block, the same rule the mixer follows. An event reported for
an earlier block is played at the block start with its attack intact and
counted as late; one reported beyond the block is dropped and counted.

### Output Buffer: 40 ms, Plus A Burst Per Underrun

The mixer writes one device burst at a time with blocking writes, so it runs
one effective buffer ahead of the speaker. That buffer starts at 40 ms (at least
four bursts) and grows by one burst after every underrun, up to 200 ms. 40 ms
covers a garbage-collection pause or a late wake-up with room to spare, and the
added latency is invisible because the visuals follow the audio clock. The
number is a starting point; M1's measurements on the Sony (`docs/timing.md`)
confirm or replace it.

Frame numbers stay exact across an underrun: the output stream's frame position
counts frames played, so frame N of what we wrote is still frame N on the
audio clock, only later in wall time.

### Soft Clipping On The Mix

The mixer's output passes through a soft clipper, not a hard clamp at full
scale. Below 0.9 the signal is untouched. Above it, the level bends smoothly
towards 1 (a tanh curve whose slope matches at the knee), so peaks from
overlapping voices are rounded off instead of squared off into harsh
distortion.

M1's offline render of the 300 BPM 32nd-note stress test found this in
practice: up to 12 overlapping placeholder snares peaked at 1.13x full scale.
That was only 0.01% of samples, but sampled accents (M3) will push harder.

There is no master attenuation. A drummer practicing on an acoustic snare
needs a loud click, and the clipper costs nothing until the mix is near full
scale. A look-ahead limiter would be cleaner on sustained overload, but it is
more machinery than a drum mix needs. Revisit if real samples clip audibly.

### Musical Time Runs On A Tempo Map

The transport measures musical position in quarter notes from the pattern's
start. The count-in bar sits at negative positions, and loop *k* starts at *k*
times the pattern length. A tempo map converts between that position and
output frames in both directions. The map is a chain of constant-tempo
segments, and every tempo change starts a new segment at the position the old
one had reached, so position never jumps. Positions are doubles and are never
accumulated from block to block, so 32nd-note triplets and odd tempos land on
the first frame at or after their exact time, the same frame whatever the
block size.

There are two kinds of tempo change, as "Practice Controls" needs:

- The **slider** applies from the start of the next block, within one burst
  of being moved.
- The **speed trainer** applies exactly on the loop boundary, even when that
  boundary falls between two frames.

The UI asks the audio clock which frame is being heard and asks the tempo map
for the position at that frame. Hit detection (M5) does the same with
microphone timestamps, which share the clock's time base. When the output
stream restarts at frame 0 mid-play, the map starts a new epoch and the
position carries on.

### Playback Yields To Calls And Unplugged Headphones

Pressing play takes audio focus (media usage). Any focus loss stops playback:
a phone call, another player, or a transient "may duck" loss. A ducked
metronome is no use to someone keeping time against it. Unplugging the
headphones (`ACTION_AUDIO_BECOMING_NOISY`) also stops playback, so the click
never jumps to the loudspeaker. Regaining focus doesn't resume; the player
presses play again. Focus is given back on stop.

The output stream stays open, playing silence, while the practicing screen is
shown. Play then starts on a clock that is already running and anchored, and
stopping lets the last notes ring out. The stream closes when the screen
leaves.

### Our Own Notation Renderer, With Bravura

Both views are drawn on a Compose `Canvas` using Bravura, the SMuFL reference
music font (SIL OFL 1.1). With one staff line and every note at the same height,
beams are always flat and the hardest parts of general engraving disappear.
What remains is grace notes, accents, tremolo slashes, buzz marks, ties,
tuplets, rests, time signatures, repeats and sticking.

Rejected:

- **Verovio**: it needs an NDK build, and its SVG output can't be drawn
  natively on Android.
- **JavaScript notation libraries in a WebView**: timing would have to cross
  into JavaScript, and f-droid.org wants bundled JavaScript built from source.

### MusicXML Is The Lesson Source Format

Lessons are uncompressed `.musicxml` files matching what MuseScore 4 exports for
a one-line snare part. Teachers already write in MuseScore, Sibelius or Dorico
and export MusicXML. That gives built-in lessons, teacher packs and user imports
a single path, and the importer gets exercised from day one. A custom text
format was rejected: friendlier to write by hand, but a second format to
maintain, and one teachers would never use.

Lessons are written by agents following `docs/musicxml-style.md` (written in
M2), validated by our importer, compared against MuseScore 4 reference renders
(`QT_QPA_PLATFORM=offscreen mscore -o …` works headlessly), and checked by a
drummer on beta builds.

### Built-In Lessons Are MuseScore Files

Each built-in lesson's source is a MuseScore 4 score
(`lessons/builtin/<id>.mscz`). The `.musicxml` the app ships, and a
reference render, are exported from it by `scripts/export-lessons.sh`.
Writing lessons in the tool teachers use proves the style guide works in
MuseScore. It keeps every lesson editable by anyone with MuseScore, and
nobody hand-edits the MusicXML. The
exports are committed, so CI and the app need no MuseScore. The rule for
adding or changing a lesson is in `docs/lessons.md`.

The built-in pack holds the rudiments. M2's one-lesson placeholder
("Quarter notes") moved out; quarter notes return in M3's Timing basics
pack.

### Lesson Details Live In `pack.json`

MusicXML holds the notes, not tempo ranges, families, levels, descriptions or
tips. Each pack is a folder with a `pack.json` beside its `.musicxml` files:

- pack level: id, title, version, author, license
- per lesson: id, file, title, family, level, tempo range, start tempo,
  description, tips

The built-in pack ships in the app's assets; imported and paid packs are the
same folder, zipped. MusicXML's custom metadata fields were rejected because
MuseScore doesn't promise to keep them and they have no natural home for
pack-level details.

### Sticking Follows MuseScore

MuseScore 4 writes its Sticking tool's letters as text directions below the
staff (`<direction><direction-type><words>R</words>…`). They are not attached
to a note; their position in the measure's timeline is what ties them to one.
Our files do the same, and our importer attaches each letter to the note at the
same time position. The importer also accepts sticking written as lyrics or
fingering. Built-in lessons must carry sticking on every note.

Writing grace-note hands as lowercase letters in the main note's word ("lR",
"llR") is our own convention, not MuseScore's. Real exports from MuseScore
4.7.5 (M2) show why it's needed:

- A Sticking belongs to a time position, never to a grace note. MuseScore
  drops one written inside a grace chord.
- A grace note's own sticking can only be a second Sticking at the main
  note's position. It exports as a second loose word, in entry order, and is
  drawn on top of the first.
- A combined word exports intact, before the grace notes, and reads cleanly
  under the note.

The details, and the importer's rules for teachers' files that stack
stickings anyway, are in `docs/musicxml-style.md`.

### XML: Pull Parser, No DTDs, No Entities

MusicXML is read with `XmlPullParser`: Android's built-in KXml in the app,
and the real kxml2 as a test-only dependency in JVM unit tests (android.jar's
copy is only a stub there). `javax.xml` DOM exists on both too, but it is
Xerces on the JVM and KXml on Android, so tests would exercise a different
parser from the one that ships. With kxml2, the security tests run against
the same parser family as the app.

Lesson files are untrusted from M6 on, and every MusicXML file names a remote
DTD in its DOCTYPE. The reader is safe by construction, not just because
the app has no INTERNET permission:

- **Raw text is checked first.** Before any parser sees the file, its raw
  text is scanned: an internal DTD subset (`[ … ]`) or any `<!ENTITY`
  declaration rejects it. Files over 8 MB are rejected too.
- **DOCTYPE processing is turned off.** The DTD is never fetched or read.
  Android's `Xml.newPullParser()` turns DOCTYPE processing *on* (found on the
  Sony in M2), so the reader switches it off. kxml2 has it off already.
- **Only standard entities are allowed.** Any entity reference other than
  XML's five predefined ones and character references is rejected.

Checking the raw text first makes the protection independent of what the
parser would do with a DTD. It rules out external-entity (XXE) and
entity-expansion attacks. Tests feed it both, including through a parser that
fails the test if it is ever handed such a file.

### The Lesson Model Is What's Written

The importer produces a `Notation`: measures of notes as written. Each note
has its hand, grace notes (with their hands), accent or ghost, tremolo or
buzz, tuplet and ties; measures have their repeats, dynamics and tempo marks.
Expanding that into timed strokes (grace timing, diddles, buzz, repeats,
"lead with left") is a separate step (#11). Then the timeline and its
consumers can change without touching the importer. Every note carries its
hand, so the strokes M5 scores against come out of that expansion with hands
attached.

- **Positions are exact.** Positions and durations are exact fractions of a
  quarter note, so tuplets never accumulate rounding. They become doubles
  only on the way to the transport.
- **Hands may be missing.** A hand is null when an imported file has no
  sticking; such a note goes in the neutral lane (M6). Built-in lessons are
  imported in strict mode, where a missing hand, an off-style sticking word
  or a non-snare part is an error naming the measure and beat.
- **Parts are chosen by id.** The importer reads one part, selected by id.
  For now a score with more than one part is reported as unsupported. M6's
  part picker only has to list the parts and pass an id.

### Grace Notes Sound Just Before The Beat

Flams and drags put the main note on the beat and their grace notes shortly
before it, played quieter. MusicXML gives grace notes no duration, so the gap is
our choice.

- **The gap is a time, not a note value.** A flam sounds the same width at 60
  and at 200 BPM, as a drummer plays it. Each grace stroke sounds one gap
  before the next stroke, so a drag's two graces are one gap apart and the
  second is one gap before the beat.
- **The transport places grace strokes exactly.** It does this with a
  lookahead window per grace level, so they play exactly once even while
  the tempo or the gap changes.
- **Grace strokes are quieter:** a gain of 0.3, against 0.55 for a normal
  stroke (see "A Lesson Plays As Written").

The gap is **12 ms** (`Transport.DEFAULT_GRACE_GAP_MILLIS`). The maintainer
chose it by ear on the Sony with the debug screen's "Grace gap" slider and
its "Ornament check" lesson; it sounded better than the first guess of 30 ms.
It was judged with the synthesized placeholder snare, so it should be
checked again once the real samples arrive (#19).

### A Lesson Plays As Written

A lesson's `Notation` is expanded (`Expansion`) into a stroke timeline that
the transport loops:

- **Repeats are unrolled:** a backward repeat goes back to the last forward
  repeat, or the start, for its number of passes. Repeats don't nest.
- **Ties hold, they don't restrike,** except that a tied buzz roll continues
  through the tie.
- **Tremolo slashes become diddles.** Each slash adds a beam to the note's
  own: an eighth with one slash is two 16ths. Every stroke gets the note's
  hand.
- **A buzz roll is one sustained buzz,** cut to the roll's length at the
  current tempo with a 5 ms fade. Until #19 brings samples, the buzz is
  synthesized:
  - **Strokes:** the hands alternate, each stroke coming in as the last one's
    bounces run out.
  - **Bounces:** each stroke is 5 to 8 short noise bounces that come faster
    and quieter as the stick settles.
  - **Randomness:** spacing and levels are seeded random, so the sound is the
    same every time.

  The first version pulsed steadily at 28 Hz and sounded like a sprinkler; a
  test now checks that the buzz is far less periodic than that. The real fix
  is #19's: a looped press-roll sample, or rolls built from single sampled
  bounces with random timing, level and sample choice.
- **Dynamics are gains:** accent (and marcato) 1.0, normal 0.55, grace 0.3,
  ghost 0.18. These are placeholder levels for the synthesized snare. The
  samples in M3 come at three dynamics and will set their own.
- **"Lead with left" swaps every R and L,** grace notes included.
- **The metronome follows each bar's own time signature,** and the count-in
  is one bar of the first.

The transport's `Pattern` is the expanded lesson as events and clicks. M1's
placeholder `Meter` is gone.

### Storage

The practice log uses Room, because it's history that gets queried. Settings
and per-lesson preferences use DataStore. Lessons are files, not database rows.

### Testing

- JVM unit tests for timing math, MusicXML import and layout math.
- Roborazzi screenshot tests for both views, so every PR shows rendering changes
  as image diffs. Roborazzi is a test-only dependency; nothing ships.
  - The tests run under Robolectric's native graphics, in `src/testDebug`.
  - The reference images are committed in `app/src/testDebug/screenshots`.
  - CI and `scripts/check-all.sh` verify against them and upload the diffs on
    failure.
  - `./gradlew recordRoborazziDebug` re-records them after an intended change.
- A debug-only timing diagnostics screen showing audio underruns, audio-clock
  jitter and dropped frames.

Instrumented `androidTest` coverage is deferred, as in `soundboard`.

## Hit Detection (M5)

### Mic Only, For Now

The microphone is the input; it works with any acoustic pad or snare. MIDI input
is not planned for now (backlog). Vibration sensing gets a time-boxed experiment
only after the hit-detection MVP ships: phone accelerometers sample fast enough,
but a pad on a stand barely shakes a phone.

### Headphones Recommended, Speaker Mode Best-Effort

The mic also hears the app's own click and snare, exactly when the player hits.

- **Headphones** are recommended for scored runs: wired is best, Bluetooth works
  after calibration. Calibration is tap-along.
- **Speaker mode** is best-effort: snare playback mutes during scored runs, and
  the click is pitched into a narrow band the detector ignores. Calibration is
  an automatic loopback: the phone plays clicks and listens to itself.

### Showing How Far Off You Were

- **Per note**: a mark ahead of or behind the note, colored by timing window
  (for example green within ±15 ms, yellow within ±35 ms, red beyond). Missed
  notes are drawn hollow; extra hits are flagged.
- **Per run**: average offset (rushing or dragging), spread, percent of hits in
  window, misses and extras, all saved to the practice log. Also a per-hand
  split: the mic can't tell hands apart, but each hit maps to an expected R or
  L, which exposes a weak hand.
- **Windows** are fixed in milliseconds, with a strictness setting, and narrow
  at fast tempos so neighboring notes don't overlap.
- **Accent detection** comes later.

## Licensing And Distribution

### GPL-3.0-only, AGPL-3.0 For Any Future Server

App code is GPL-3.0-only, like `soundboard`. AGPL only differs when modified
code runs as a network service, so for an offline app it adds nothing. Any
future store server should be AGPL-3.0.

### Our Lessons Are CC BY-SA 4.0; Other People's Need Not Be

Anything inside the APK must be freely licensed, or F-Droid marks the app
`NonFreeAssets`. Built-in lessons are CC BY-SA 4.0 and samples CC0 or CC BY.
Teacher and user content carries no such requirement: teachers keep their
copyright and license distribution to us, and user imports stay on the device.
Selling proprietary packs (M7) will earn an F-Droid anti-feature flag, which is
accepted.

### No Analytics, Ads Or Proprietary SDKs

No Google Play Services, Firebase, analytics, crash reporting or ads. All
dependencies are AndroidX- or Kotlin-class free software, so f-droid.org can
build the app.

### Distribution

v0.1.0 (M4) ships GitHub Release APKs, the self-hosted F-Droid repository and
per-branch beta APKs. The f-droid.org submission happens in M6. Google Play is
decided together with paid packs (M7): Play requires its own billing for digital
goods, which can't ship in an F-Droid build, so Play would mean a second build
flavor.

### The Application ID Is Provisional Until v0.1.0

Development uses `dev.co508.drums` and the display name "Drums". The real name
is chosen at the start of M4, and the ID may change until the first release.
Signing keys aren't bound to an ID; the first installable release and store
listings are what make it permanent. The Kotlin namespace can stay
`dev.co508.drums` even if `applicationId` changes.

## Release Machinery

Adopted from the devkit's Android stack; `docs/deployment.md` (written in M0) is
the full description:

- releases are gated by a release PR (release-please)
- `versionCode` is derived from `version.txt` and kept as a literal
- per-branch beta builds have their own identity and signing key
- there are two F-Droid paths (the self-hosted repo from M4, f-droid.org from
  M6)
- store copy lives in `fastlane/`
- the licenses screen is maintained by hand

Signing keys and secrets are created by a human, never by an agent.

## Process

Work is planned as GitHub milestones M0–M8. Issues are ordered with "blocked by"
links ([`docs/roadmap.md`](docs/roadmap.md)). AI agents build on branches and
open PRs, and Claude reviews and merges them. The maintainer merges release PRs
and anything touching signing keys or secrets, and a drummer checks every lesson
on a beta build.
