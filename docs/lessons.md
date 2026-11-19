# Writing lessons

Built-in lessons are written in MuseScore 4, following
[`musicxml-style.md`](musicxml-style.md), so authoring them doubles as a
check that the style guide works in MuseScore.

| What | Where |
| --- | --- |
| Source scores | `lessons/builtin/<id>.mscz` |
| Exports the app ships | `app/src/main/assets/packs/builtin/<id>.musicxml` |
| Lesson details | `app/src/main/assets/packs/builtin/pack.json` ([format](pack-format.md)) |
| Reference renders | `lessons/builtin/renders/<id>.png` |

## Adding or changing a lesson

1. Open or create `lessons/builtin/<id>.mscz` in MuseScore 4. Use the Snare
   Drum instrument (a one-line staff).
   - **Sticking:** add a Sticking element under every note: `R` or `L`. For
     grace notes, use one word for the whole group, with lowercase grace
     letters before the main note: `lR` for a flam, `llR` for a drag.
     Never add a separate Sticking to a grace note.
   - **Repeat:** put repeat barlines around the exercise, so it loops.
2. Add or update its entry in `pack.json`: id, file, title, family, level,
   tempo range and start, description and tips. Use our own words.
3. Run `./scripts/export-lessons.sh`. It needs MuseScore 4 as `mscore`, or
   set `MSCORE`. It rewrites the `.musicxml` exports and the renders.
4. Run `./scripts/test.sh`. `PackLoaderTest` imports every built-in lesson in
   strict mode, where any departure from the style guide fails with the
   file, measure and beat. `BuiltInLessonsTest` checks what each lesson
   plays.
5. Look at the render, and have a drummer check the lesson on a beta build
   (DECISIONS.md, "Process").

## How the M2 test set was made

The seven test rudiments (#12) were written by an agent, which can run
MuseScore only headlessly. Each was:

1. written as style-guide MusicXML
2. imported into MuseScore
3. given native Sticking elements in MuseScore's own `.mscx` format (the
   command-line import makes plain staff text instead)
4. saved as the `.mscz` source

Opening and re-saving them in MuseScore's editor is the same as editing any
other score.

## Roll practice phrases

The numbered roll exercises in this pack spell out individual strokes instead
of using tremolo slashes. They use a closing accent and rests to make the
beginning and end of each phrase easy to hear. The even-numbered exercises
end with two single strokes. The single-stroke and triple-stroke exercises
also separate their groups with rests. These are practice settings, not the
only possible rhythmic interpretations; the drummer review in #23 should
check their rhythmic choices, stickings, and accents.
