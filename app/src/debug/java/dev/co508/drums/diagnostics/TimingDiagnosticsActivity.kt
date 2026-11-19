package dev.co508.drums.diagnostics

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.co508.drums.DrumsApp
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.audio.Sound
import dev.co508.drums.playback.Meter
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.PlaybackSession
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Debug-only M1 timing diagnostics: a visual metronome driven by the audio
 * clock, and the numbers behind it (underruns, buffer, fast track, clock
 * jitter, dropped UI frames). See docs/timing.md for the exit test.
 *
 * Launch from the "Drums timing" launcher entry of a debug build, or run the
 * exit test directly:
 *
 *     adb shell am start -n dev.co508.drums.debug/dev.co508.drums.diagnostics.TimingDiagnosticsActivity \
 *         --ez exitTest true --ei durationSeconds 600
 *
 * While playing, a summary is logged every 10 s under the tag `DrumsTiming`.
 */
class TimingDiagnosticsActivity : ComponentActivity() {
    private val session: PlaybackSession get() = (application as DrumsApp).playback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = DiagnosticsController(session, PlaceholderSounds.snare(session.engine.sampleRate))
        session.onInterrupted = {
            controller.onInterrupted()
        }
        val exitTestSeconds =
            if (intent.getBooleanExtra(EXTRA_EXIT_TEST, false)) {
                intent.getIntExtra(EXTRA_DURATION_SECONDS, DEFAULT_EXIT_TEST_SECONDS)
            } else {
                0
            }
        setContent {
            MaterialTheme {
                DiagnosticsScreen(controller, exitTestSeconds)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        session.open()
    }

    override fun onStop() {
        super.onStop()
        session.close()
    }

    companion object {
        const val EXTRA_EXIT_TEST = "exitTest"
        const val EXTRA_DURATION_SECONDS = "durationSeconds"
        const val DEFAULT_EXIT_TEST_SECONDS = 600
    }
}

private const val TAG = "DrumsTiming"
private const val LOG_INTERVAL_NANOS = 10_000_000_000L
private const val FLASH_MILLIS = 60.0

private class DiagnosticsController(
    val session: PlaybackSession,
    private val snare: Sound,
) {
    var beatsPerMinute by mutableDoubleStateOf(120.0)
    var notesPerBeat by mutableIntStateOf(1)
    var clickOn by mutableStateOf(true)
    var snareOn by mutableStateOf(true)
    var countIn by mutableStateOf(true)
    var playing by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var visualOffsetMillis by mutableFloatStateOf(0f)
    var statsText by mutableStateOf("")
    var frameNanos by mutableLongStateOf(0L)

    val frameDrops = FrameDropCounter()
    var refreshRateHz = 60f
    private var playStartNanos = 0L
    private var underrunBaseline = 0
    private var stopAtNanos = 0L
    private var lastLogNanos = 0L

    private val transport get() = session.transport

    fun play() {
        applyVolumes()
        val settings = PlaySettings(subdivisionPattern(notesPerBeat, snare), beatsPerMinute, countIn = countIn)
        if (!session.play(settings)) {
            message = "Audio focus refused (a call in progress?)"
            return
        }
        message = null
        playing = true
        playStartNanos = SystemClock.elapsedRealtimeNanos()
        resetStats()
        Log.i(TAG, "start ${describeRun()}")
    }

    fun stop() {
        if (!playing) return
        log("stop")
        session.stop()
        playing = false
        stopAtNanos = 0
    }

    fun onInterrupted() {
        log("interrupted")
        playing = false
        stopAtNanos = 0
        message = "Stopped by the system (focus lost or headphones unplugged)"
    }

    fun runExitTest(seconds: Int) {
        beatsPerMinute = 300.0
        notesPerBeat = 8
        clickOn = true
        snareOn = true
        countIn = true
        play()
        if (playing && seconds > 0) stopAtNanos = playStartNanos + seconds * 1_000_000_000L
    }

    fun changeTempo(bpm: Double) {
        beatsPerMinute = bpm.coerceIn(MIN_BPM, MAX_BPM).roundToInt().toDouble()
        transport.setTempo(beatsPerMinute)
    }

    fun changeSubdivision(n: Int) {
        notesPerBeat = n
        if (playing) play() // A new pattern restarts from the count-in.
    }

    fun applyVolumes() {
        transport.clickVolume = if (clickOn) 1f else 0f
        transport.instrumentVolume = if (snareOn) 1f else 0f
    }

    fun applyOffset(millis: Float) {
        visualOffsetMillis = millis
        session.clock.visualOffsetMillis = millis.toDouble()
    }

    fun resetStats() {
        underrunBaseline = session.engine.stats().underruns
        session.clock.resetStats()
        frameDrops.reset()
    }

    /** Called a few times a second: refreshes the numbers, logs, and ends a timed run. */
    fun tick() {
        val now = SystemClock.elapsedRealtimeNanos()
        statsText = statsReport(now)
        if (playing && now - lastLogNanos >= LOG_INTERVAL_NANOS) {
            lastLogNanos = now
            log("progress")
        }
        if (playing && stopAtNanos != 0L && now >= stopAtNanos) {
            log("EXIT TEST DONE")
            stop()
        }
    }

    private fun elapsedSeconds(now: Long) = if (playing) (now - playStartNanos) / 1e9 else 0.0

    private fun describeRun() =
        "bpm=${beatsPerMinute.toInt()} notesPerBeat=$notesPerBeat click=$clickOn snare=$snareOn " +
            "device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE}"

    private fun log(event: String) {
        val now = SystemClock.elapsedRealtimeNanos()
        val e = session.engine.stats()
        val c = session.clock.stats()
        Log.i(
            TAG,
            "$event elapsedS=${"%.1f".format(Locale.ROOT, elapsedSeconds(now))} ${describeRun()} " +
                "underrunsRun=${e.underruns - underrunBaseline} underrunsTotal=${e.underruns} " +
                "bufferFrames=${e.bufferSizeFrames} capacityFrames=${e.bufferCapacityFrames} " +
                "burst=${e.framesPerBurst} rate=${e.sampleRate} lowLatency=${e.lowLatencyGranted} " +
                "route=\"${e.outputDevice}\" late=${e.mixer.lateEvents} dropped=${e.mixer.droppedEvents} " +
                "stolen=${e.mixer.stolenVoices} peakVoices=${e.mixer.peakVoices} writeError=${e.writeError} " +
                "clockUpdates=${c.updates} jitterMeanMs=${fmt(c.meanAbsErrorMillis)} " +
                "jitterMaxMs=${fmt(c.maxAbsErrorMillis)} uiFrames=${frameDrops.frames} " +
                "uiDropped=${frameDrops.dropped} uiWorstGapMs=${fmt(frameDrops.worstGapMillis)} " +
                "refreshHz=$refreshRateHz visualOffsetMs=$visualOffsetMillis",
        )
    }

    private fun statsReport(now: Long): String {
        val e = session.engine.stats()
        val c = session.clock.stats()
        val bufferMs = e.bufferSizeFrames * 1000.0 / e.sampleRate
        val elapsed = elapsedSeconds(now)
        return buildString {
            appendLine("Output    ${e.sampleRate} Hz, burst ${e.framesPerBurst} frames")
            appendLine("Fast track ${if (e.lowLatencyGranted) "granted" else "NOT granted"}")
            appendLine("Route     ${e.outputDevice ?: "unknown"}")
            appendLine(
                "Buffer    ${e.bufferSizeFrames} frames (${fmt(bufferMs)} ms), capacity ${e.bufferCapacityFrames}",
            )
            appendLine("Underruns ${e.underruns - underrunBaseline} this run, ${e.underruns} since open")
            appendLine(
                "Mixer     late ${e.mixer.lateEvents}, dropped ${e.mixer.droppedEvents}, " +
                    "stolen ${e.mixer.stolenVoices}, peak voices ${e.mixer.peakVoices}",
            )
            if (e.writeError != 0) appendLine("WRITE ERROR ${e.writeError}")
            appendLine(
                "Clock     ${if (c.anchored) "anchored" else "no timestamp yet"}, ${c.updates} updates",
            )
            appendLine(
                "Jitter    last ${fmt(c.lastErrorMillis)} ms, mean ${fmt(c.meanAbsErrorMillis)} ms, " +
                    "max ${fmt(c.maxAbsErrorMillis)} ms",
            )
            appendLine(
                "UI        ${frameDrops.frames} frames, ${frameDrops.dropped} dropped, " +
                    "worst gap ${fmt(frameDrops.worstGapMillis)} ms at ${refreshRateHz.roundToInt()} Hz",
            )
            append("Run       ${"%d:%02d".format(Locale.ROOT, elapsed.toInt() / 60, elapsed.toInt() % 60)}")
            if (stopAtNanos != 0L) append(" of ${(stopAtNanos - playStartNanos) / 1_000_000_000L / 60} min exit test")
        }
    }

    private fun fmt(value: Double) = "%.2f".format(Locale.ROOT, value)

    companion object {
        const val MIN_BPM = 30.0
        const val MAX_BPM = 300.0
    }
}

@Composable
private fun DiagnosticsScreen(
    controller: DiagnosticsController,
    exitTestSeconds: Int,
) {
    val view = LocalView.current
    LaunchedEffect(Unit) {
        if (exitTestSeconds > 0) controller.runExitTest(exitTestSeconds)
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                controller.frameNanos = t
                controller.frameDrops.onFrame(t, (1e9 / controller.refreshRateHz).toLong())
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            controller.refreshRateHz = view.display?.refreshRate ?: 60f
            controller.tick()
            delay(250)
        }
    }

    Scaffold { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Timing diagnostics", style = MaterialTheme.typography.titleLarge)
            Metronome(controller, Modifier.fillMaxWidth().height(200.dp))
            Controls(controller)
            controller.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text(controller.statsText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

private val accentColor = Color(0xFFE65100)
private val beatColor = Color(0xFF1565C0)
private val idleColor = Color(0xFFB0BEC5)

/**
 * Flashes on every click (orange on the downbeat, blue otherwise) and sweeps a
 * ball from side to side, arriving at each edge exactly on a beat. Both are
 * positioned from the audio clock alone.
 */
@Composable
private fun Metronome(
    controller: DiagnosticsController,
    modifier: Modifier,
) {
    val session = controller.session
    Canvas(modifier) {
        val frameNanos = controller.frameNanos // Read in the draw phase: redraws every frame.
        val frame = session.clock.visualFrameAt(frameNanos)
        val position = if (frame.isNaN()) Double.NaN else session.transport.positionAt(frame)
        val meter =
            session.transport.settings
                ?.pattern
                ?.meter ?: Meter.FOUR_FOUR
        if (!controller.playing || position.isNaN()) {
            drawBeats(meter, current = -1, flash = 0f)
            return@Canvas
        }
        val phase = beatPhase(position, meter, session.transport.beatsPerMinuteAt(frame))
        val flash =
            if (phase.millisSinceBeat <
                FLASH_MILLIS
            ) {
                (1 - phase.millisSinceBeat / FLASH_MILLIS).toFloat()
            } else {
                0f
            }
        val color = if (phase.beatInBar == 0) accentColor else beatColor
        drawRect(color.copy(alpha = 0.85f * flash), size = size.copy(height = size.height * 0.45f))
        drawBeats(meter, phase.beatInBar, flash)
        // The ball reaches an edge on every beat, alternating sides.
        val beatIndex = floor(position / meter.beatQuarters).toLong()
        val x = if (Math.floorMod(beatIndex, 2L) == 0L) phase.fraction else 1 - phase.fraction
        val radius = size.height * 0.08f
        drawCircle(
            color,
            radius,
            Offset(radius + (size.width - 2 * radius) * x.toFloat(), size.height * 0.88f),
        )
    }
}

private fun DrawScope.drawBeats(
    meter: Meter,
    current: Int,
    flash: Float,
) {
    val n = meter.beatsPerBar
    val radius = minOf(size.width / (n * 3f), size.height * 0.12f)
    for (i in 0 until n) {
        val center = Offset(size.width * (i + 0.5f) / n, size.height * 0.65f)
        val color = if (i == 0) accentColor else beatColor
        if (i == current) {
            drawCircle(color.copy(alpha = 0.35f + 0.65f * flash), radius, center)
        } else {
            drawCircle(idleColor, radius, center, style = Stroke(width = 3f))
        }
    }
}

@Composable
private fun Controls(controller: DiagnosticsController) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (controller.playing) {
            Button(onClick = controller::stop) { Text("Stop") }
        } else {
            Button(onClick = controller::play) { Text("Play") }
        }
        OutlinedButton(onClick = { controller.runExitTest(0) }) { Text("300 BPM 32nds") }
        OutlinedButton(onClick = controller::resetStats) { Text("Reset stats") }
    }

    Text("Tempo ${controller.beatsPerMinute.toInt()} BPM")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (step in listOf(-5, -1, 1, 5)) {
            OutlinedButton(onClick = { controller.changeTempo(controller.beatsPerMinute + step) }) {
                Text(if (step > 0) "+$step" else "$step")
            }
        }
    }
    Slider(
        value = controller.beatsPerMinute.toFloat(),
        onValueChange = { controller.changeTempo(it.toDouble()) },
        valueRange = DiagnosticsController.MIN_BPM.toFloat()..DiagnosticsController.MAX_BPM.toFloat(),
    )

    Text("Snare notes per beat")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((n, label) in listOf(1 to "4th", 2 to "8th", 3 to "trip", 4 to "16th", 6 to "6let", 8 to "32nd")) {
            FilterChip(
                selected = controller.notesPerBeat == n,
                onClick = { controller.changeSubdivision(n) },
                label = { Text(label) },
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LabeledSwitch("Click", controller.clickOn) {
            controller.clickOn = it
            controller.applyVolumes()
        }
        LabeledSwitch("Snare", controller.snareOn) {
            controller.snareOn = it
            controller.applyVolumes()
        }
        LabeledSwitch("Count-in", controller.countIn) { controller.countIn = it }
    }

    Text("Visual offset ${controller.visualOffsetMillis.roundToInt()} ms (positive delays the visuals)")
    Slider(
        value = controller.visualOffsetMillis,
        onValueChange = { controller.applyOffset(it.roundToInt().toFloat()) },
        valueRange = -150f..150f,
    )
}

@Composable
private fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
