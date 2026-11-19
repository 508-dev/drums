# MusicXML style for lessons

The subset of MusicXML our lessons are written in and our importer accepts.
It matches what MuseScore 4 exports for a one-line snare part, so a teacher's
MuseScore export and a lesson written by hand go down the same path
(DECISIONS.md, "MusicXML Is The Lesson Source Format" and "Sticking Follows
MuseScore").

Everything marked **verified** was checked against real exports from
**MuseScore Studio 4.7.5**, run headlessly. The reference scores and their
exports are in
[`app/src/test/resources/musicxml/musescore-4.7.5/`](../app/src/test/resources/musicxml/musescore-4.7.5/),
and `MuseScoreFixturesTest` pins the facts below to them.

## Authoring rules at a glance

- One uncompressed `.musicxml` file per lesson: MusicXML 4.0, `score-partwise`,
  UTF-8.
- One part, one staff with one line, percussion clef, voice 1 only.
- Every note is the snare: `<unpitched>` E4 plus `<instrument id>`.
- Sticking is a `<words>` direction placed below and written before the notes
  it belongs to. Each written note gets one sticking word. Grace strokes are
  lowercase letters in front of the main note's letter: `lR` is a flam, `llR`
  a drag.
- Accent `<accent/>`, ghost note `<notehead parentheses="yes">`, diddle
  `<tremolo type="single">`, buzz roll `<tremolo type="unmeasured">0`.
- Tempo goes in `<metronome>` plus `<sound tempo>`, in quarter notes per
  minute.
- Nothing else belongs in the notes. Lesson details live in `pack.json`.

## File and part

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN"
  "http://www.musicxml.org/dtds/partwise.dtd">
<score-partwise version="4.0">
  <work><work-title>Single Paradiddle</work-title></work>
  <part-list>
    <score-part id="P1">
      <part-name>Snare Drum</part-name>
      <score-instrument id="P1-I39"><instrument-name>Acoustic Snare</instrument-name></score-instrument>
      <midi-instrument id="P1-I39"><midi-channel>10</midi-channel><midi-unpitched>39</midi-unpitched></midi-instrument>
    </score-part>
  </part-list>
  <part id="P1">
    <measure number="1">
      <attributes>
        <divisions>4</divisions>
        <key><fifths>0</fifths></key>
        <time><beats>4</beats><beat-type>4</beat-type></time>
        <clef><sign>percussion</sign></clef>
        <staff-details><staff-lines>1</staff-lines></staff-details>
      </attributes>
      …
```

**Verified.** MuseScore exports a snare part this way. It imports the header
above as its "Snare Drum" instrument: drumset pitch 38, a one-line staff.

- The instrument id is `P1-I` plus the MIDI note plus 1, so the snare is
  `P1-I39` and `<midi-unpitched>` is 39.
- Every note has `<unpitched><display-step>E</display-step><display-octave>4</display-octave></unpitched>`
  and `<instrument id="P1-I39"/>`.
- The part has no `<backup>` or `<forward>`, and every note is in voice 1.

**Importer.** Find the snare by its `<midi-unpitched>` (39) or its instrument
name, never by a hard-coded id. A teacher's file may number parts and
instruments differently. The staff position (E4) is display only.

## Time

- `<divisions>` can be any value. MuseScore picks the smallest that works
  (verified: 2, 6). Positions are counted in divisions from the start of each
  measure.
- Time signatures go in `<time>`. Compound meters are written as they are
  (6/8, verified). The lesson's beat unit (a dotted quarter in 6/8) comes from
  the meter.
- Rests are `<note><rest/>…</note>` (verified).

## Sticking

```xml
<direction placement="below">
  <direction-type><words>R</words></direction-type>
</direction>
<note>…</note>
```

**Verified (MuseScore 4.7.5):**

- MuseScore's Sticking element exports as a `<words>` direction placed below,
  written *before* the note at the same time position. It is not attached to
  the note; only its position in the measure ties it to one.
- When the note has grace notes, the sticking comes before the grace notes
  too. The order is words, then grace notes, then the main note.
- A Sticking belongs to the time position (the segment), never to a grace
  note. MuseScore silently drops a Sticking written inside a grace chord in
  its own file format.
- Two Stickings at one time position, such as "L" meant for a flam's grace
  note and "R" for its main note, export as two loose words in the order they
  were entered. MuseScore also draws them **on top of each other**, so they
  can't be read.
- So a grace note can't carry its own sticking in MuseScore. **Our convention
  is one word per main note, with the grace strokes as lowercase letters in
  front:** `lR` is a left-hand flam onto a right-hand note, and `llR` is a drag.
  MuseScore keeps such a word intact, and it renders legibly under the note
  (verified).
- MuseScore's command-line import turns these words into plain staff text,
  not Sticking. Plain staff text exports identically, apart from a layout
  offset, so the round trip is lossless.
- Directions at one time position come out in MuseScore's own order, not the
  order they were entered. For example, a sticking may come before or after a
  dynamic or a tempo mark at the same time.

**Authoring:**

- Every written note in a built-in lesson gets exactly one sticking word.
- Use `R` and `L` for the written note. Prefix one lowercase letter per grace
  note (`r`, `l`) in the order they're played: `lR`, `rL`, `llR`, `rrL`.
- A diddle or buzz roll gets the single letter of the hand that plays it.
  `R` on a diddled note means both strokes are right-handed.
- Don't put sticking on rests.

**Importer:**

- Attach each `<words>` direction to the note group (grace notes plus main
  note) at the same time position, whatever order directions and notes
  arrive in.
- Accept only words matching `^[rl]*[RL]$`, after trimming. Ignore other text
  (fingering instructions, "rit."…).
- The lowercase letters are the grace strokes, in order. Their count must
  match the grace notes; otherwise report the mismatch.
- If several sticking words share one time position, concatenate them in
  document order and apply the same rule. That order is not reliable, so
  only accept the result if it parses unambiguously.
- Sticking written as single-letter lyrics or `<fingering>` is accepted for
  imports (M6) but never written.

## Strokes and ornaments

| Notation | MusicXML (verified) | Meaning for playback |
| --- | --- | --- |
| Accent | `<notations><articulations><accent/>` | Accented stroke |
| Marcato | `<articulations><strong-accent type="up"/>` | Treated as an accent in v0.1 |
| Ghost note | `<notehead parentheses="yes">normal</notehead>` | Ghost stroke |
| Dynamics | `<direction><direction-type><dynamics><p/>`…, with `<sound dynamics>` | Optional; not used for v0.1 playback |
| Flam | one `<grace slash="yes"/>` eighth before the main note | Grace stroke just before the beat |
| Drag | two `<grace/>` 16ths, beamed, before the main note | Two grace strokes just before the beat |
| Diddle | `<ornaments><tremolo type="single">N</tremolo>` | The note split into strokes N beams shorter |
| Buzz roll | `<ornaments><tremolo type="unmeasured">0</tremolo>` | A buzz for the note's whole duration |

- **Grace notes** have no `<duration>` (verified), and their timing before the
  beat is ours to choose (DECISIONS.md, "Grace Notes Sound Just Before The
  Beat"). MuseScore writes a slashed grace note for an acciaccatura and a plain
  `<grace/>` for an appoggiatura; both are just grace strokes to us. Flams use
  the slashed eighth; drags use two plain 16ths.
- **Diddles.** Each slash adds one beam, so the note splits into strokes of its
  value halved N times: an eighth with N = 1 is two 16ths, and a dotted quarter
  with N = 2 is six 16ths (both verified in the export). N runs from 1 to 4
  in MuseScore (from its source, not exported here).
- **Buzz rolls.** The value is `0`, not empty (verified). On import, also
  accept an empty value, as MuseScore itself does. A roll tied over several
  notes repeats the tremolo on every tied note (verified), and it lasts the
  whole tied duration.
- **Noteheads** other than normal (x, cross…) describe appearance only and
  don't say rimshot or cross-stick. Rimshots and other stroke types aren't in
  the v0.1 subset.

## Tuplets and ties

- **Tuplets:** `<time-modification>` (`actual-notes`/`normal-notes`) on every
  note, plus `<notations><tuplet type="start">` and `"stop"` (verified for 3:2).
  Read the ratio from the file; don't assume one.
- **Ties:** both `<tie type="start|stop"/>` (playback) and
  `<notations><tied …/>` (drawing) (verified). Tied notes are one stroke,
  except under a buzz roll, where the roll continues.

## Repeats

- A forward repeat is `<barline location="left">` with heavy-light bars and
  `<repeat direction="forward"/>`. A backward repeat is
  `<barline location="right">` with light-heavy bars and
  `<repeat direction="backward"/>` (verified).
- `times` is **omitted for the default two passes** and written otherwise, for
  example `times="3"` (verified).
- Voltas (`<ending>`) aren't used in built-in lessons. The importer reports
  them as unsupported until they're needed.

## Tempo

```xml
<direction placement="above">
  <direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>100</per-minute></metronome></direction-type>
  <sound tempo="100"/>
</direction>
```

`<sound tempo>` is always in **quarter notes per minute** (verified). A 6/8
lesson at dotted quarter = 60 is written `<beat-unit>quarter</beat-unit><beat-unit-dot/><per-minute>60</per-minute>`
with `<sound tempo="90"/>`. A lesson's tempo range and start tempo live in
`pack.json`. A tempo mark in the file is only the default display.

## What the importer ignores

MuseScore adds layout and metadata we don't use:

- `<identification>`/`<encoding>` (software and date)
- `<defaults>`, `<credit>` and `<print>`
- `default-x`, `default-y` and `relative-*` attributes
- `<stem>` and `<beam>`, because we draw our own
- `system="only-top"` on directions

Any of these may change between MuseScore versions without changing the
music. `<work-title>` is read only as a fallback title; `pack.json` is
authoritative.

## The reference fixtures

`app/src/test/resources/musicxml/musescore-4.7.5/` holds three MuseScore
scores (`.mscz`, with native Sticking elements) and their MusicXML exports:

| Fixture | Contents |
| --- | --- |
| `sticking-and-ornaments` | 4/4 at 100, sticking on every note, an accent, a ghost note, an `lR` flam, an `llR` drag, diddles, a triplet, a tied buzz roll, forward and backward repeats |
| `grace-sticking-stacked` | The same, except the flam has two Stickings (L and R) instead of `lR`, showing why we don't do that |
| `compound-and-repeats` | 6/8 at dotted quarter = 60, dynamics, marcato, a two-slash diddle on a dotted quarter, a rest, a backward repeat with `times="3"` |

The `.mscz` files were written in MuseScore's own `.mscx` format, starting
from MuseScore's import of a hand-written file. The Sticking elements were
made native, because the command-line import produces plain staff text.
`scripts/export-musescore-fixtures.sh` re-exports them with whatever MuseScore
is installed. Then run `./scripts/test.sh`: `MuseScoreFixturesTest` fails if
any fact above has changed.

**Not verified:** we couldn't drive MuseScore's user interface headlessly. We
haven't directly observed what the Sticking command does when a grace note is
selected. MuseScore's source attaches it to the main note's segment, and its
file format can't hold anything else, so the result is the stacked case
above. A teacher's file may still contain it, which is why the importer
merges same-time sticking words.
