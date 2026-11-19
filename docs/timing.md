# Timing

How the M1 timing core is measured, and what it measured on the reference
device. The design is in [`DECISIONS.md`](../DECISIONS.md): "One Audio Clock",
"Kotlin AudioTrack Mixer, Oboe Only If Needed", "The Mixer Pulls Events, One
Block At A Time", "Output Buffer: 40 ms, Plus A Burst Per Underrun" and
"Musical Time Runs On A Tempo Map".

## The diagnostics screen

Debug builds have a second launcher entry, **Drums timing**
(`TimingDiagnosticsActivity`, in `app/src/debug/`). Release and beta builds
don't include it. It shows:

- **A visual metronome** positioned only from the audio clock. The top band
  flashes on every click (orange on the downbeat, blue on other beats) for
  60 ms, the beat dot lights up, and a ball sweeps from side to side, reaching
  an edge exactly on each beat.
- **Output:** sample rate, burst size, whether a low-latency (fast) track was
  granted, and the route (speaker, headphones…).
- **Buffer:** the effective buffer size and its capacity. The buffer grows one
  burst per underrun.
- **Underruns:** this run, and since the stream opened.
- **Mixer:** late, dropped and stolen events, and the peak voice count.
- **Clock jitter:** how far each new `AudioTrack` timestamp lands from the
  extrapolation of the previous one (last, mean and max, in ms).
- **UI frames:** frames drawn, frames dropped (from gaps between frame times)
  and the worst gap.
- **Controls:** tempo (30 to 300 BPM, ±1/±5), snare notes per beat (4ths to
  32nds, plus triplets and sextuplets), click and snare on/off, count-in, the
  visual offset (±150 ms), a "300 BPM 32nds" preset and a stats reset.

While playing, it logs a one-line summary every 10 s under the tag
`DrumsTiming`.

## The M1 exit test

On the Sony Xperia 10 VII:

- 32nd notes at 300 BPM (40 snare strokes and 5 clicks a second) for 10
  minutes, with zero underruns
- the visual metronome visibly locked to the click

```bash
ADB=~/Android/Sdk/platform-tools/adb
./gradlew installDebug
$ADB logcat -c
$ADB shell am start -n dev.co508.drums.debug/dev.co508.drums.diagnostics.TimingDiagnosticsActivity \
    --ez exitTest true --ei durationSeconds 600
$ADB logcat -s DrumsTiming    # progress every 10 s, then "EXIT TEST DONE"
```

"Locked" is judged by eye and ear on the device. The flash should land on the
click, not before or after it, and the ball should reach the edge on each
click without stutter. If it is consistently early or late, move the visual
offset slider until it looks right and record the value.

## Results

To be measured on the Sony Xperia 10 VII (XQ-FE72, Android 16).
