# Hit detection

How M5 hears stick hits and puts them on the playback clock, how to check it on
a phone, and how real pad recordings become test fixtures. The design is in
[`DECISIONS.md`](../DECISIONS.md), "Hit Detection (M5)".

## The pipeline

1. **Capture** (`hits/MicCapture`): `AudioRecord`, mono, at the output's
   native rate. It uses UNPROCESSED where the device supports it, otherwise
   VOICE_RECOGNITION, otherwise MIC, and float samples when it can get them.
   The phone's own mic is preferred, and attached voice effects are switched
   off. A capture thread at audio priority reads 4 ms blocks and doesn't
   allocate.
2. **Onsets** (`hits/OnsetDetector`): high-pass at 2 kHz, then a rise gate
   over an adaptive noise floor finds each hit. A constant-fraction rule then
   times it to a fraction of a millisecond, and onsets far quieter than the
   playing are dropped.
3. **Capture clock** (`hits/InputClock`): `AudioRecord.getTimestamp` maps
   capture frames to `System.nanoTime`, smoothed. Read-time estimates are the
   fallback.
4. **Shared clock** (`hits/HitTimeline`): `AudioClock.frameAt` maps a hit's
   time to the output frame heard at that moment, and the transport's
   `TempoMap` maps that frame to a musical position. Calibration supplies
   `latencyNanos`.
5. **Calibration** (`hits/Calibrator`, `hits/CalibrationMath`, Settings → Hit
   timing calibration): a speaker loopback or a tap-along, stored per phone
   and output route. See below.
6. **Scoring** (`scoring/HitMatcher`, `scoring/ScoringSession`): hits matched
   to notes with strictness windows, published as immutable snapshots. Both
   views draw the marks (DECISIONS.md, "Matching Hits To Notes").

Hits are queued (`hits/HitQueue`, lock-free) for one consumer: the diagnostics
screen now, and scoring from #30 on.

## Synthetic fixtures (CI)

`app/src/test/.../hits/SyntheticSignals.kt` builds the test signals as
continuous functions of time, so each true onset is exact even between two
samples. They include:

- pad hits: inharmonic 0.4–9 kHz partials with random phases, short attacks
  and fast decays
- a ringing snare, whose high partials die faster
- white noise, low rumble, swelling noise and wall reflections
- tonal "whine" bursts like the ones described below

`OnsetDetectorTest` checks counts and timing for several cases. These include
dynamics across 40 dB, 32nds at 300 BPM with ghost notes, flams, merged
"flat" flams, clipping, noise, reflections, block-size independence and
allocation. Timing spread stays within ±0.3 ms; the constant bias is about
+0.3 ms, which calibration absorbs.

## Checking on a phone

Debug builds have a **Microphone (M5)** panel at the bottom of the
**Ratamatap timing** screen. It has Listen, live levels, the input timestamp
jitter, and each hit's level and offset from the nearest scheduled stroke (or
click). Every 10 s it logs a stats line under `RatamatapHits`. The screen shows
over the lock screen, so it can be driven over adb on a locked phone:

```bash
ADB="$HOME/Android/Sdk/platform-tools/adb -s <serial>"
P=dev.co508.ratamatap.debug
A=$P/dev.co508.drums.diagnostics.TimingDiagnosticsActivity
$ADB shell pm grant $P android.permission.RECORD_AUDIO   # or answer the dialog
$ADB logcat -c
# Listen while playing quarter-note clicks at 100 BPM for 20 s:
$ADB shell am start -n $A --ez listen true --ez play true --ei bpm 100 \
    --ei notesPerBeat 1 --ez click true --ez snare false --ei durationSeconds 20
$ADB logcat -s RatamatapHits MicCapture
```

With the speaker up, this is a loopback check: each click is heard by the mic,
and its offset is the speaker-to-mic round trip.

### 2026-11-20: ASUS Zenfone 9 (AI2202, Android 14)

The phone was locked and lying in an empty room, driven over wireless adb:

| Measure | Result |
| --- | --- |
| Source | UNPROCESSED, 48 kHz float, built-in mic, no voice effects attached |
| Input timestamps | Available; jitter against the smoothed clock mean 0.08 ms, max 0.54 ms, no resets |
| Noise floor (above 2 kHz) | about −82 dBFS |
| Loopback, click only (volume 8/30) | **+35.0 ms**, spread about ±0.15 ms over the run and across runs |
| Loopback, snare samples | about +46 ms, less steady |

Two findings:

- **Snare samples peak late.** Their stick transient is heard about 2 ms in,
  but the drum's body peaks 9–13 ms in and the wires later still. Through a
  phone speaker and the 2 kHz band, the detector times the later part. #83
  now starts the samples early so their perceived attack is on the beat
  (DECISIONS.md, "A Snare's Attack Is On The Beat"). Loopback calibration
  (#29) uses the click, and speaker mode (#32) mutes the snare anyway.
- **Rooms tick.** With nothing playing, the mic caught about 5 events a second
  at −53 to −68 dBFS: short tonal bursts at about 7.4 kHz, probably electrical
  whine. These are why the detector drops onsets more than 30 dB below the
  recent hits. Before the first real hit there is nothing to compare with, so a
  few get through; scoring (#30) only counts hits during a run.

The volume was returned to 0 afterwards. The raw recording made to diagnose the
bursts was ambient sound from the maintainer's home, so it was deleted rather
than committed. Real pad playing, the actual target, still needs the
maintainer (below).

## Calibration

The design is in DECISIONS.md, "Calibration Per Output Route". The debug
screen runs the same calibration as Settings and saves the result:

```bash
$ADB shell am start -n $A --es calibrate LOOPBACK   # or TAP_ALONG
$ADB logcat -s RatamatapHits | grep calibration
```

While listening, the debug **Microphone** panel takes the current route's
saved calibration off every hit (its offsets line says "calibrated +35.0 ms"),
as scoring will.

On the ASUS, with the speaker at volume 8/30, three consecutive loopback
calibrations measured 35.03, 34.97 and 34.92 ms, each with about 0.1 ms of
spread. With the result applied, the click loopback's offsets centred on
−0.08 ms (22 clicks).

## Speaker mode over adb

`--ez speakerMode true` gives the timing screen speaker mode: the low click,
the snare muted and the speaker-mode detector. Listening while the click plays
through the speaker shows whether the mic still hears it:

```bash
$ADB shell am start -n $A --ez listen true --ez play true --ei bpm 100 \
    --ez snare false --ez click true --ez speakerMode true --ei durationSeconds 15
$ADB logcat -s RatamatapHits | grep "hit "    # offsetMs≈0 would be the click
```

On the ASUS, the usual click came back at every beat, but the speaker-mode
click didn't, at media volumes 12, 20 and 30 out of 30 (DECISIONS.md, "Speaker
Mode"). With `--es score <lesson>` added, a scored run logs the click guard's
learned leak and the echoes it dropped.

## Scored runs over adb

```bash
$ADB shell am start -n $A --ez play true --es score single-paradiddle --ei bpm 90 \
    --ez snare true --ez click false --ei durationSeconds 20
$ADB logcat -s RatamatapHits | grep score
```

This plays a lesson with scoring on and the current route's calibration, and
logs the totals every 3 s and at the end. With the speaker up, the phone
scores its own snare: on the ASUS, 113 of 117 notes matched, nearly all green
at a steady +9 ms.

## Recording pad fixtures (maintainer)

The detector is tuned on synthetic hits. Real recordings decide whether the
2 kHz band, the 6 dB rise and the 30 dB gate are right. Recordings are made
on a debug build, in the room where you practice:

1. Install a debug build: `./gradlew installDebug`.
2. Open **Ratamatap timing**, scroll to **Microphone (M5)** and tap
   **Listen**. Allow the microphone.
3. Put the phone where it will sit while practicing, about 0.5–1.5 m from the
   pad, and wear headphones so the click doesn't reach the mic.
4. Pick the pattern and tempo above the panel, type the take's label (for
   example `singles-120`), tap **Play**, then **Record**. Play the pattern,
   and tap **Save** to finish (takes stop by themselves after 60 s).
5. Pull the takes:
   `adb pull /sdcard/Android/data/dev.co508.ratamatap.debug/files/mic/`.
   Each take has a WAV file (raw mic, 16-bit) and a CSV file. The CSV holds the
   onsets as detected on the phone, plus the device, source, route and tempo.

The takes to record, about 15 s each:

| Label | What to play |
| --- | --- |
| `singles-80` | Single strokes, 8th notes at 80 BPM, medium volume |
| `dynamics-100` | 8ths at 100 BPM, from as soft as you play to accents |
| `accents-ghosts-100` | Paradiddles at 100 BPM with clear accents and ghost notes |
| `32nds-150` | Single-stroke 32nds at 150 BPM, or as fast as is clean |
| `flams-90` | Flams on quarter notes at 90 BPM, normal width, then flat |
| `drags-90` | Drags at 90 BPM |
| `room-30s` | 30 s without playing: the room's own noise (talking is fine) |

Fixtures are committed under `app/src/test/resources/hits/` as the
maintainer's own recordings, licensed CC0. Their tests check counts, the
lack of false hits in `room-30s`, and timing consistency. Real playing has
no exact ground truth, so timing is judged against hand-marked onsets in a
few takes.
