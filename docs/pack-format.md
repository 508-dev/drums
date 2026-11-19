# Pack format

A pack is a folder: a `pack.json` beside the lessons' `.musicxml` files. The
built-in pack lives in the app's assets at `packs/builtin/`. Imported and paid
packs (M6, M7) are the same folder, zipped. Why the details live here and not
in the MusicXML: DECISIONS.md, "Lesson Details Live In `pack.json`". How the
MusicXML is written: [`musicxml-style.md`](musicxml-style.md).

```json
{
  "format": 1,
  "id": "builtin",
  "title": "Built-in lessons",
  "version": 1,
  "author": "Ratamatap contributors",
  "license": "CC-BY-SA-4.0",
  "lessons": [
    {
      "id": "quarter-notes",
      "file": "quarter-notes.musicxml",
      "title": "Quarter notes",
      "family": "timing",
      "level": 1,
      "tempo": { "min": 40, "max": 200, "start": 80 },
      "description": "Alternating single strokes on every beat, right hand first.",
      "tips": ["Let the stick rebound; don't press it into the head."]
    }
  ]
}
```

## Pack fields

| Field | Meaning |
| --- | --- |
| `format` | The version of this file format. The app reads format `1` and refuses others. |
| `id` | The pack's id: lowercase letters, digits and dashes. Stable, because practice history refers to it. |
| `title` | Shown in the library. |
| `version` | The pack's own version, an integer raised whenever its content changes. |
| `author` | Who wrote the lessons. |
| `license` | An SPDX identifier for the lessons. Built-in lessons are `CC-BY-SA-4.0` (DECISIONS.md, "Our Lessons Are CC BY-SA 4.0"). |
| `lessons` | At least one lesson. |

## Lesson fields

| Field | Meaning |
| --- | --- |
| `id` | Unique within the pack: lowercase letters, digits and dashes. Stable, because per-lesson settings and the practice log refer to it. |
| `file` | The lesson's `.musicxml` file, relative to the pack folder. It may not point outside the folder. |
| `title` | Shown in the library and on the practice screen. |
| `family` | `timing`, `rolls`, `diddles`, `flams` or `drags`. |
| `level` | 1 and up, from first steps to advanced. |
| `tempo` | `min`, `max` and `start`, in the lesson's beat unit per minute (a dotted quarter in compound time). Within 30 to 300, the tempo slider's range, with `min ≤ start ≤ max`. |
| `description` | A short paragraph in our own words. |
| `tips` | Optional: short practice tips, shown in the collapsible panel. |

Unknown fields are errors, so a typo doesn't go unnoticed.

## Loading and validation

`PackLoader` reads `pack.json`, checks it, then imports every lesson.
Built-in packs are imported in strict mode, where any departure from the
MusicXML style guide is an error. Every problem is reported at once, each
naming its file and, for MusicXML, its measure and beat, for example
`a.musicxml: Measure 2, beat 3: no sticking for this note.`
`PackLoaderTest` loads the real built-in pack strictly, so a broken built-in
lesson fails the build.
