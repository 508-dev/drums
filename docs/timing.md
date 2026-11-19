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
- **Recovery:** current state, track recreations/attempts and last error/reason.
  "Recreate output" requests the same worker-owned replacement used for errors
  and route changes; it needs no root. It does not emulate an audio-server
  crash. Playback stays in its existing run; a stopped run stays stopped.
- **Controls:** tempo (30 to 300 BPM, ±1/±5), snare notes per beat (4ths to
  32nds, plus triplets and sextuplets), click and snare on/off, count-in, the
  visual offset (±150 ms), a "300 BPM 32nds" preset and a stats reset.

While playing, it logs a one-line summary every 10 s under the tag
`DrumsTiming`.

## Output-recovery checks (#51)

JVM fake-track tests cover negative writes during prefill and playback
(`ERROR_DEAD_OBJECT` -6 and other errors), unavailable track creation, partial
and zero writes, bounded failures, cancellation during retry, route-requested
replacement, trainer/TempoMap continuity and clock-generation invalidation.
The diagnostics state also has a Roborazzi reference. No new shipped library
or permission is needed.

On a debug build, start practice in **Drums timing**, tap **Recreate output**,
and inspect `AudioTrackEngine`/`DrumsTiming` logcat. A normal replacement should
finish in roughly a second or less; new timestamps anchor the new stream,
without a count-in restart or a huge clock-jitter spike. A brief gap/discarded
old output buffer is expected. Exhausted attempts stop playback; Play retries.

The maintainer must check actual wired in/out and Bluetooth connect/disconnect
and judge sound/visual lock by eye and ear. Unplugging/focus loss intentionally
stops practice and must not auto-resume. Test re-opening playback on the new
route. `killall audioserver` requires root and is **not** a usable acceptance
test on the unrooted Sony; do not report the debug recreation as that test.

### 2026-11-19 Sony smoke and recovery observations

Current-main `f391f39` debug was installed first. UI automation traversed all
45 lesson titles (Timing 5, Rolls 15, Diddles 4, Flams 11, Drags 10). A Quarter
Notes favorite survived force-stop/relaunch. Settings/About/licenses opened,
the bundled OFL text expanded, and a short 60 BPM run appeared in History
(0.16 min, start/highest 60 BPM, manual tempo). No app crash was observed.
Screenshots and the collected lesson list were retained in workspace scratch.

The #51 debug build then ran the 300 BPM/32nds preset for 120.1 seconds on
speaker, 48 kHz/144-frame bursts and a 2016-frame (42 ms) buffer. One requested
recreation occurred during that timed run: recovery took 191 ms; final counters
were zero underruns/late/dropped/stolen events, writeError 0 and an anchored
clock (max reported jitter 1.99 ms). Subsequent recreations took 194–198 ms;
two while stopped did not resume practice. An additional playing recreation
and user Stop completed normally. Screenshots confirmed the recovery counts
and continuing metronome rendering; sound/visual synchrony was not judged.

The available adb audio command did not expose a second working media route;
no actual wired/Bluetooth transition or audio-server kill was performed. These
observations establish real-track replacement on speaker, **not** physical
route recovery. Physical routes, auditory timing, TalkBack/large-font checks
and backup/restore remain for the maintainer; the drummer lesson check is #23.

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

### 2026-11-19: Sony Xperia 10 VII (XQ-FE72, Android 16)

The exit test was run at commit `31d45b8` with a debug build. Setup:

- The phone was connected over **wireless adb** (through Tailscale), not
  USB. Charging state wasn't recorded.
- Output went to the built-in speaker, with the media volume at 0 of 30.
  Every stream was already at 0 and the shell couldn't raise it. The stream
  still plays and the mixer still pulls; underruns don't depend on volume.
- The screen was on throughout, at 120 Hz.

| Measure | Result |
| --- | --- |
| Run | 600.1 s, 300 BPM, 32nd notes plus click (45 events/s), count-in |
| **Underruns** | **0**, during the run and since the stream opened |
| Sample rate / burst | 48 000 Hz / 144 frames (3 ms) |
| Fast (low-latency) track | Granted |
| Effective buffer | 2 016 frames (42.0 ms), 14 bursts; never grew. Capacity 9 648 |
| Mixer | 0 late, 0 dropped, 0 stolen events; peak 12 voices; no write errors |
| Clock jitter | Each `getTimestamp` (5 653, about every 100 ms) compared with the previous one's extrapolation: mean 0.23 ms, max 1.51 ms |
| UI frames (app's count) | 68 544 frames in 600 s (114 fps average); 3 664 counted as dropped (5.3%); worst gap 183 ms |
| UI frames (`dumpsys gfxinfo`) | 70 779 frames; 2 400 janky (3.4%); frame time p50 8 ms, p90 10 ms, p95 16 ms, p99 25 ms; 2 335 slow UI thread |
| Thermal status afterwards | 1 (light); skin 45 °C |

**Audio: pass.** There were no underruns in 10 minutes at the exit-test load,
and the 40 ms buffer from DECISIONS.md never had to grow. The Kotlin
`AudioTrack` mixer meets M1's timing goal on the reference device, so Oboe is
not needed.

**Clock: pass.** The audio clock's timestamps agree with their own
extrapolation to within 1.5 ms, well under one 8.3 ms display frame.

**Visual lock:** this is judged by eye and ear by the maintainer and
recorded below.

**UI frame drops (for M2):** about 3 to 5% of frames miss the 120 Hz deadline,
mostly from a slow UI thread. This is a debug build, where Compose runs
noticeably slower, and the screen recomposes a block of stats text 4 times a
second. A dropped frame delays the picture by one frame; it doesn't shift the
metronome, because each frame is positioned from the audio clock. M2's
falling-notes and sheet views should be checked for jank in a release-type
build (`assembleBeta`).

### Visual lock and offset

On 2026-11-19 the maintainer judged it by eye and ear on the Sony with
**wired headphones**. **Locked:** the flash lands exactly with the click, at
a **visual offset of 0 ms**.

The loudspeaker and Bluetooth weren't checked. The 0 ms result is for wired
headphones; other outputs report their own latency, and Bluetooth headsets
may need a non-zero offset (DECISIONS.md, "One Audio Clock").

The maintainer also heard a very loud buzz starting about a second in, which
is when the 32nd-note snare stream begins after the count-in. It's being
investigated separately from this measurement.
