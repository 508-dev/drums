# Ratamatap Decisions

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
  just *below* its main note. Its offset is at most 0.7 staff spaces per
  grace note. It never uses more than 30% of the gap back to the previous
  note in its own lane, so in dense music (flam accents' triplets) it closes
  up beside its main note instead of looking like part of its neighbour.
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

### The Sheet View Engraves What's Written

The sheet view draws the lesson as written, not as played. Repeats keep
their repeat barlines, and the playhead returns to the start of the section
on each pass. The transport's position is mapped back to a written measure
through the unrolled timeline. Engraving follows MuseScore's output for a
one-line snare part (compare `lessons/builtin/renders/` with the sheet
screenshots):

- **Stems and beams:** stems up and beams flat. Notes are beamed within each
  beat (a dotted quarter in compound time), and rests break beams.
- **Below the line:** accents, then the sticking word (`lR`, `llR`), as in
  MuseScore.
- **Grace notes:** a slashed grace note uses SMuFL's acciaccatura glyph; other
  grace notes are small beamed noteheads.
- **Tuplets:** a beamed tuplet shows only its number; an unbeamed one also
  gets a bracket.

Deliberate differences from MuseScore:

- **Spacing is proportional within each line,** so the playhead sweeps at a
  constant speed ("Linear Time In Both Views"). There is a minimum gap
  between notes so noteheads and stickings never collide.
- **Line length:** as many bars per line as fit at that spacing, stretched
  to the width.
- **Omitted:** there is no title, tempo mark or instrument name; the practice
  screen shows those.
- **Sticking letters** are a bold sans-serif. They are grey, and turn the
  hand's colour while lit.

A note and its sticking light up from when the playhead reaches it until the
next note starts, at most a third of a beat.

The last view used is remembered globally in DataStore. The M2 SharedPreferences
value is migrated once on first read (#16).

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

The library uses an ID-only, saved navigation stack: pack, family, lesson.
Family and lesson order follow first appearance in `pack.json`; Timing basics
is the built-in pack's first family. Back returns to the previous list and
its scroll position, including after rotation. History and Settings are
top-bar destinations, initially placeholders until their own M3 issues land.
Favorites are stored separately from playback preferences in the same
DataStore, so concurrent favorite and tempo saves cannot overwrite each other.
A lesson without saved playback preferences shows “Last tempo: —”.

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

### Output Errors Recreate The Track, Not The Practice Session

The mixer worker owns AudioTrack creation, write and release. Negative writes
(including ERROR_DEAD_OBJECT), track-operation exceptions and a sustained run
of zero writes trigger bounded recovery; a routing callback also requests
replacement when the output device changes or the track becomes invalid.
The first route announcement is a baseline, not a reason to restart forever.
Three retries wait 100/200/300 ms; one second of healthy writes resets
the retry budget. Exhaustion exposes a terminal error and stops practice with
an explanation; a subsequent explicit Play starts a fresh engine attempt.
Calls/focus loss and unplugging still stop without auto-resume.

A replacement mixer starts output frames at zero. Transport uses its existing
TempoMap epoch/reanchor hook to continue at the last rendered musical position,
preserving trainer/loop state and not replaying the count-in. The broken track's
unplayed buffer may be discarded, so recovery is a brief gap, not gapless audio.
The engine publishes a stream-generation change before frame-zero rendering;
AudioClock invalidates its old anchor immediately, even while new timestamps
are unavailable. Visuals hold their last position until the replacement clock
anchors instead of extrapolating across stream epochs. Recovery is part of the
same History run; it does not synthesize another Play or reacquire focus.

Only the worker releases a track, after its writes finish. Shutdown cancels
retries and pauses/flushes to unblock writes; it never releases native state
beneath a still-running writer. Recovery counts, attempts, last error/reason
and current state appear in debug diagnostics and timestamped logcat. A debug
"Recreate output" control exercises the same replacement path without root;
it is not a substitute for actual write-error and physical-route testing.

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

Pressing play takes audio focus (media usage). Permanent or transient focus
loss stops playback (a phone call or another player). A transient "may duck"
loss does **not** stop practice: notification pings may briefly auto-duck the
audio. The original "never duck a metronome" policy made ordinary message
notifications end a run, which is worse than briefly lowering its volume.
Unplugging the
headphones (`ACTION_AUDIO_BECOMING_NOISY`) also stops playback, so the click
never jumps to the loudspeaker. Regaining focus doesn't resume; the player
presses play again. Focus is given back on stop.

Stop requests log their reason and wall-clock timestamp (`PlaybackSession` in
logcat); focus callbacks also log their numeric type. Output write failures
log their error code and timestamp. The practice screen explains interruptions
and polls for output failure; restarting a failed output is handled by #51.

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

### A Snare's Attack Is On The Beat

The VCSL snare samples (#19) open with a sharp stick transient. Within 1–2 ms
it reaches 30–40% of the hit's peak amplitude, about 9 dB below it. The drum's
body then builds to its peak 9–13 ms in, and the snare wires peak later still.
The transport used to start a sample at the beat, so whatever a listener
takes as "the hit" landed somewhat late (#83, found by the M5 loopback).

**What counts as the attack** is the perceptual onset: the first moment the
loudness reaches **−15 dB** of its maximum. Loudness here is power integrated
over 10 ms, roughly as the ear integrates it (`SampledSounds.perceivedAttack`).
That threshold rule follows the perceptual-onset studies (Vos and Rasch,
1981); work on where listeners place the beat of drum sounds puts sharp
attacks within a few ms of where they physically start. It measures
**1.8–1.9 ms** for the accent and normal samples and **2.6–2.7 ms** for the
ghosts.

Aligning the *peak* (or 50% of the maximum, about 9–10 ms) was considered
and rejected. It would put that clearly audible stick transient about 10 ms
ahead of the click: a flam with the metronome.

**How it's applied:** at load, each variation is padded in front with silence
until its measured attack sits exactly **4 ms** in (`Sound.attackFrames`).
The transport starts every instrument event that much early, through the same
per-level lead windows that place grace notes, so grace strokes and buzz rolls
get it too: a roll's bounces are the same padded ghost hits. Clicks are
synthesized with their attack at sample 0 and stay on the beat. A pattern's
sounds must share one attack offset. Without a count-in, the very first stroke
can't start before Play, so it is up to 4 ms late, as a grace note there
already was.

A JVM test renders every dynamic and variation through the transport and the
mixer and checks that each is heard within 1 ms of its beat (it measures well
under 0.1 ms). The debug timing screen's **Extra snare lead** slider moves the
snare up to 15 ms earlier still. That is an ear test of this choice against
the M1 sync, like the grace gap's, and the maintainer's verdict is pending.

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

Practice settings use one typed JSON DataStore (1.2.1, matching `soundboard`).
Per-lesson keys include both pack and lesson IDs, so imported packs cannot
overwrite a built-in lesson's choices. The view toggle and falling-note zoom
remain global, shared across lessons and restarts. The default spacing is
220 dp per beat (twice the M2 default), so sixteenths are more separated at
the hit line; the zoom slider spans 48–480 dp per beat. Start
tempo is separate from the last tempo reached, so replaying a speed trainer
starts at its configured start rather than its previous target. Count-in,
loop, lead hand and trainer setup change while stopped; sound switches,
volumes and the manual tempo control apply live. Accepted writes are queued
in application scope so navigating away does not cancel them.
The heard tempo and loop/trainer progress stay visible above the notation;
trainer steps get a brief highlight. A loop means the whole expanded lesson,
including written repeats, not one written bar.

Global sound/volume and count-in defaults seed only lessons with no saved
playback preferences; changing defaults never overwrites deliberate per-lesson
choices. The sound/visual offset is always global (−500 to +500 ms), applied to
the shared AudioClock when opening practice. Positive delays the visuals,
negative advances them; audio scheduling itself never changes.

About and project-grouped credits are available from Settings. License texts
are bundled from `licenses/` and readable offline; credits are maintained by
hand alongside `docs/third-party.md`, with no license-discovery SDK.

Each accepted Play→Stop is one Room practice-log entry, including count-in.
Rejected audio focus creates no entry. User stop, completion, focus loss,
unplugging, output errors and leaving the app/screen close the active run once.
The date is the start wall-clock time; elapsed milliseconds use monotonic time
so clock/time-zone changes do not alter duration. History formats minutes and
shows newest sessions first. Highest tempo comes from already-heard raw audio
clock/tempo-map segments (not rendered-ahead trainer targets or visual offset).
Best tempo is a playback milestone, **not** a measured playing-accuracy score.
Writes are queued in application scope, not a departing composable's scope.
Failed writes remain pending in memory with a visible Retry action; sudden
process death before a completed write can still lose an active/pending run.

Room v1 uses a stable UUID session primary key and separate pack/lesson IDs,
with no foreign key to the installed lesson library: removing a pack must not
erase its history. M5 can attach hit summaries/per-hand/per-note rows by
`sessionId` foreign key in a related table through a versioned migration; we do
not invent empty hit-stat fields now. Exported schema JSON is tracked and the
Room migration helper validates the v1 baseline/data preservation. Real
upgrade paths get tests when another schema version exists; no destructive
migration fallback is allowed.

**Backup is enabled for history and settings.** They are user-owned progress
worth keeping on a phone change, and contain no credentials or microphone data.
Legacy full-backup rules and Android 12+ data-extraction rules include only the
database domain (including SQLite journal/WAL sidecars) and `files/datastore/`.
Bundled lessons/samples, caches and unrelated files are not included. Modern
cloud backup requires encryption capabilities; device transfer includes the
same data. Legacy cloud backup follows the platform/user's backup settings.
This delegates backup to Android's configured transport/user opt-in: it is not
an app-operated upload service, adds no SDK or INTERNET permission, and does
not promise backup on every vendor/device. Physical transfer/restore remains a
manual validation item; JVM tests check the manifest/rules and database reopen.

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

### Mic Capture: The Rawest Source, The Phone's Own Mic

The detector needs the transient as it hit the mic. Automatic gain control,
noise suppression and echo cancelling all smear or remove it.

- **Source.** Capture uses UNPROCESSED where the device says it supports it
  (`PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED`). Otherwise it uses
  VOICE_RECOGNITION, which the compatibility definition keeps free of gain
  control, and MIC last. Any gain control, noise suppression or echo
  cancelling attached to the session is switched off.
- **Format.** Mono float samples at the output's native rate, or 16-bit
  converted to float where float capture isn't offered.
- **Device.** The phone's built-in mic is preferred even when a wired headset
  has one. The phone sits on the stand near the pad, and one input path keeps
  calibration per output route meaningful.
- **Threading.** A capture thread at audio priority reads 4 ms blocks from a
  100 ms buffer, the same discipline as the mixer: no allocation, no locks.
  Hits go to the UI through a lock-free single-producer queue. Latency of
  delivery doesn't matter for scoring, because every hit carries its own
  timestamp.

The permission is asked for only when the player calibrates or turns scoring
on, with an explanation, and practice works without it. In #28 only the debug
diagnostics screen listened, so `RECORD_AUDIO` was in the debug manifest. It
moved to the main manifest with calibration (#29), the first shipped feature
to use the mic.

### Mic Timestamps Are Smoothed, Not Trusted Raw

`AudioRecord.getTimestamp(TIMEBASE_MONOTONIC)` gives the capture time of a
frame on the same clock as the output's `AudioTrack` timestamps. The input
clock is anchored on those timestamps 10 times a second. Each new timestamp
moves the anchor a tenth of the way towards itself. That averages away
jitter (about 0.08 ms mean on the ASUS) while still following drift between
the two clocks, and a jump of more than 20 ms re-anchors at once.

Where a device gives no input timestamps, the clock estimates them from when
reads return. Reads only return late, so estimates follow their earliest
values. The estimates carry the input latency as a constant, which calibration
removes. A brief gap in real timestamps keeps extrapolating instead of falling
back, because an estimate's bias would shift every hit.

A hit at mic time T lines up with the output frame heard at T, less the
calibrated latency. The tempo map then gives its musical position, so hits
and notes share one clock ("One Audio Clock").

### Onsets: A Rise Gate, An Adaptive Floor And A Constant Fraction

Hits are found in the time domain, in Kotlin, without ML or spectral
transforms. The steps:

- **Band.** A 24 dB/octave high-pass at **2 kHz**. A stick on a pad is a
  broadband click; rumble, voices, a drum's ring and its slowly beating low
  partials all live lower. In tests, 500 Hz let a ringing snare's beating
  re-trigger; 1.5 kHz and 2 kHz both worked. 2 kHz also leaves room for
  speaker mode (#32) to pitch the click below the band.
- **Trigger.** A 0.5 ms power envelope must clear the noise floor by 12 dB
  (and −70 dBFS). It must also have risen 6 dB above the loudest moment of a
  3 ms window ending 2 ms earlier. A tail only falls, so it can't trigger, while
  a stroke 25 ms after an accent (32nds at 300 BPM) or a flam's main note
  12 ms after its grace still can. The window, not a single point, keeps a
  tail's beating partials from counting as a rise.
- **One hit, one onset.** After a trigger the detector waits at least 5 ms and
  until the envelope falls. Strokes closer than that, such as a "flat" flam,
  merge into one onset at the first sound. Matching (#30) handles the merge.
- **Timing.** The onset is where a centred 0.5 ms power envelope first reaches
  **5%** of the way from the pre-hit level to the peak. The time is
  interpolated between samples, and dips shorter than the window are bridged
  so a merged pair reports its start. A constant fraction makes the time
  independent of how hard the pad was hit. A sweep over window and fraction
  found low fractions tightest: about 0.1 ms standard deviation over 40 dB of
  dynamics. The remaining +0.3 ms bias is constant, and calibration absorbs it.
- **Noise floor.** The floor uses minimum statistics: the quietest moment of a
  5 ms envelope in each 20 ms block. It drops at once. After a 300 ms warm-up it
  rises at most 2 dB a second, and not at all within 250 ms of a hit, so
  continuous playing or a ringing drum can't drag it up into the hits.
- **Relative gate.** Onsets more than 30 dB below the loudest recent hit
  (forgotten at 3 dB a second) are dropped. A quiet ASUS in an empty room
  heard about 5 tonal bursts a second at −53 to −68 dBFS; a ghost note is
  about 15 dB under an accent. A dropped onset doesn't hold off a real hit
  just after it.

All of this is tuned on synthetic signals (`docs/hit-detection.md`). The
numbers are starting points: the maintainer's pad recordings, committed as
fixtures, confirm or change them.

### Calibration Per Output Route

Output and input timestamps put a hit on the output clock, but not exactly.
What they leave out can be anywhere from a millisecond (wired) to 200 ms
(Bluetooth): converters, mic and speaker processing, air, latency a headset
doesn't report, and the player's ear. Calibration measures it once and
`HitTimeline.latencyNanos` takes it off every hit.

- **Per route, per phone.** The engine reports the output route: the speaker,
  wired (one route for any analog jack), each Bluetooth device by name, or
  each USB device. A calibration is stored for *phone model + route* in the
  practice settings. They are backed up, and a phone restored from another
  phone's backup must not inherit that phone's delays.
- **Speaker: automatic loopback.** After a silent count-in bar, the phone
  plays 16 sharp 3 ms noise-burst clicks at 100 BPM and listens to itself. The
  click is broadband so the 2 kHz detector hears it well; the metronome's
  click is pitched. The delay is the earliest one that 75% of the clicks agree
  on to within 2 ms, averaged over those clicks. A first version took each
  click's earliest sound instead, and the ASUS's room ticks (about 5 a second)
  landed before the click often enough to fail one run in three. On the ASUS,
  three consecutive runs measured 35.03, 34.97 and 34.92 ms, each with about
  0.1 ms of spread. Applying the result brought the click loopback's offsets
  to −0.08 ms. Loopback can't see the air between the phone and the player,
  or the player's ear, so speaker scoring stays best-effort.
- **Any output: tap-along.** After a count-in, the player hits the pad on 16
  metronome clicks at 80 BPM. Each tap belongs to the nearest click within
  half a beat (so up to 375 ms of Bluetooth delay maps correctly), and the
  first tap per click counts. The result is the median of taps within three
  robust spreads of it. A run needs 10 usable taps with a robust spread of at
  most 25 ms, or it asks to try again. This measures everything at once,
  including the player's habit of anticipating, which is what scoring should
  compare against.
- It lives in **Settings → Hit timing calibration**. The screen opens the
  output (to know the route), asks for the microphone only on Start, and shows
  this phone's saved calibrations with Forget. The speaker check is offered
  only on the speaker; tap-along is always offered.

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

### Ratamatap Name And Application ID

The app is named Ratamatap, with application ID `dev.co508.ratamatap`. The
Kotlin namespace remains `dev.co508.drums`; source packages need not match the
application ID. The repository was renamed on 2026-11-19.

A collision scan of about 15 candidates found existing same-name drum or music
apps for Stickwork, Hitline, Ratatap and others. No conflict was found for
Ratamatap (ratamacue + tap). This scan was not legal clearance.

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

Signing keys are created deliberately with `scripts/setup-release-signing.sh`
(release) and `scripts/setup-beta-signing.sh` (beta). The maintainer runs
them, or an agent does when the maintainer explicitly asks; never as a side
effect of other work. The release script stores the key in pass under
`508/android/<slug>/`, named exactly after the GitHub secrets, sets the Prod
environment secrets with `gh`, and refuses to replace an existing key. The
Ratamatap release key was created this way on 2026-11-20. Its certificate's
SHA-256 fingerprint is
`BE:DB:25:35:9D:04:5E:86:E6:1A:79:01:A0:D2:71:6D:F6:44:7E:BF:1E:BD:CB:1F:AC:56:5F:BB:7C:8F:4C:7C`.

## Process

Work is planned as GitHub milestones M0–M8. Issues are ordered with "blocked by"
links ([`docs/roadmap.md`](docs/roadmap.md)). AI agents build on branches and
open PRs, and Claude reviews and merges them. The maintainer merges release PRs
and anything touching signing keys or secrets, and a drummer checks every lesson
on a beta build.
