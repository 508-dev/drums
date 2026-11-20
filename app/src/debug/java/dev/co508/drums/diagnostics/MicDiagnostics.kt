package dev.co508.drums.diagnostics

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.hits.CalibrationMethod
import dev.co508.drums.hits.Calibrator
import dev.co508.drums.hits.DetectedHit
import dev.co508.drums.hits.HitTimeline
import dev.co508.drums.hits.MicCapture
import dev.co508.drums.lesson.StrokeTimeline
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.scoring.NoteMark
import dev.co508.drums.scoring.ScoredLesson
import dev.co508.drums.scoring.ScoringSession
import dev.co508.drums.scoring.Strictness
import dev.co508.drums.ui.settings.resultText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HITS_TAG = "RatamatapHits"
private const val RECENT_HITS = 10
private const val STATS_LOG_INTERVAL_NANOS = 10_000_000_000L

/** What the microphone panel shows. Plain data, so it can be screenshot-tested. */
data class MicPanelState(
    val listening: Boolean = false,
    val report: String = "Mic       off",
    val includeClicks: Boolean = true,
    val summary: String = "",
    val recentHits: List<String> = emptyList(),
    val recording: Boolean = false,
    val recordingSeconds: Double = 0.0,
    val saving: Boolean = false,
    val label: String = "take",
    val lastSaved: String? = null,
    val message: String? = null,
)

/**
 * Debug-only microphone tools for M5 (#28): listen for hits, show each one's
 * offset from the nearest scheduled sound on the shared clock, and record the
 * raw mic to WAV for test fixtures. See docs/hit-detection.md.
 */
internal class MicDiagnostics(
    private val mic: MicCapture,
    private val timeline: HitTimeline,
    private val session: PlaybackSession,
    private val graceGapMillis: () -> Double,
    private val recordingsDir: () -> File?,
    private val hasPermission: () -> Boolean,
    private val requestPermission: () -> Unit,
    private val describeRun: () -> String,
    /** The stored calibration for a route, in ms, if there is one. */
    private val calibrationMillis: (OutputRoute) -> Double? = { null },
) {
    var state by mutableStateOf(MicPanelState())
        private set

    private val recorder by lazy { WavRecorder(mic.sampleRate) }
    private val offsets = OffsetStats()
    private val recent = ArrayDeque<String>()
    private val takeHits = mutableListOf<String>()
    private val hit = DetectedHit()
    private var lastStatsLogNanos = 0L
    private var recordLimitSeconds = Double.POSITIVE_INFINITY

    fun toggle() {
        when {
            mic.isRunning -> stop()
            hasPermission() -> start()
            else -> requestPermission()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            start()
        } else {
            state = state.copy(message = "Microphone permission denied; playback still works without it.")
        }
    }

    // Only reached after hasPermission() or a granted request.
    @SuppressLint("MissingPermission")
    private fun start() {
        if (!mic.start()) {
            state = state.copy(message = "No microphone input could be opened.")
            return
        }
        mic.tap = recorder
        applyCalibration()
        state = state.copy(listening = true, message = null)
        Log.i(HITS_TAG, "listening ${mic.stats()} device=${Build.MANUFACTURER} ${Build.MODEL}")
    }

    /** Takes the current route's stored calibration off every hit, as scoring will. */
    private fun applyCalibration() {
        val route = session.engine.stats().outputRoute
        val millis = route?.let(calibrationMillis)
        timeline.latencyNanos = ((millis ?: 0.0) * 1_000_000).toLong()
        Log.i(HITS_TAG, "calibration route=${route?.key} latencyMs=$millis")
        calibrationNote = if (millis == null) "uncalibrated" else "calibrated %+.1f ms".format(Locale.ROOT, millis)
    }

    private var calibrationNote = "uncalibrated"
    private var calibrator: Calibrator? = null

    /** Runs a Settings calibration (adb: `--es calibrate LOOPBACK`) and saves its result. */
    @SuppressLint("MissingPermission")
    fun calibrate(
        method: CalibrationMethod,
        scope: CoroutineScope,
    ) {
        if (!hasPermission()) {
            state = state.copy(message = "Grant the microphone permission first.")
            return
        }
        if (mic.isRunning) stop()
        val run = Calibrator(session, mic)
        calibrator = run
        val previousHandler = session.onInterrupted
        session.onInterrupted = run::onInterrupted
        run.start(method)
        Log.i(
            HITS_TAG,
            "calibration start method=$method route=${session.engine.stats().outputRoute} state=${run.state}",
        )
        scope.launch {
            while (run.state is Calibrator.State.Listening) {
                run.tick()
                delay(50)
            }
            session.onInterrupted = previousHandler
            val finished = run.state
            Log.i(HITS_TAG, "calibration result $finished")
            if (finished is Calibrator.State.Finished) saveCalibration(finished)
            state = state.copy(message = "Calibration: ${resultText(finished)?.first ?: finished}")
            calibrator = null
        }
    }

    var saveCalibration: (Calibrator.State.Finished) -> Unit = {}

    var scoring: ScoringSession? = null

    /**
     * Scores the lesson now playing (adb: `--es score <lesson>`), with the
     * current route's calibration, and logs its totals every few seconds and
     * at the end. With the speaker on, the phone scores its own snare.
     */
    @SuppressLint("MissingPermission")
    fun score(
        timeline: StrokeTimeline,
        isPlaying: () -> Boolean,
        scope: CoroutineScope,
    ) {
        val session = scoring ?: return
        if (!hasPermission()) return
        if (mic.isRunning) stop()
        val route =
            this.session.engine
                .stats()
                .outputRoute
        val latency = route?.let(calibrationMillis) ?: 0.0
        val started = session.start(ScoredLesson.from(timeline), Strictness.STANDARD, true, latency)
        Log.i(HITS_TAG, "score start=$started route=${route?.key} latencyMs=$latency")
        scope.launch {
            while (isPlaying()) {
                delay(3_000)
                Log.i(HITS_TAG, "score progress ${session.snapshot.stats}")
            }
            session.stop()
            while (session.isListening) delay(50)
            val snapshot = session.snapshot
            val hits = snapshot.marks.flatten().filter { it.kind == NoteMark.Kind.HIT }
            Log.i(HITS_TAG, "score done ${snapshot.stats}")
            Log.i(
                HITS_TAG,
                "score offsets " + hits.takeLast(24).joinToString { "%.1f".format(Locale.ROOT, it.offsetMillis) },
            )
        }
    }

    fun stop() {
        if (recorder.isRecording) recorder.end()
        mic.stop()
        mic.tap = null
        state = state.copy(listening = false, recording = false, report = micReport(mic.stats()))
    }

    fun setIncludeClicks(on: Boolean) {
        state = state.copy(includeClicks = on)
    }

    fun setLabel(label: String) {
        state = state.copy(label = label.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40))
    }

    fun resetOffsets() {
        offsets.reset()
        recent.clear()
        state = state.copy(summary = "", recentHits = emptyList())
    }

    /** Called a few times a second: takes the new hits and refreshes the numbers. */
    fun tick(scope: CoroutineScope) {
        val rate = session.engine.sampleRate
        var changed = false
        while (mic.poll(hit)) {
            changed = true
            val frame = timeline.outputFrame(hit.nanoTime)
            val settings = session.transport.settings
            var offsetMillis = Double.NaN
            if (frame.isFinite() && settings != null && session.isPlaying) {
                val nearest =
                    nearestScheduledFrame(
                        settings,
                        session.transport.tempoMap,
                        frame,
                        graceGapMillis() * rate / 1000.0,
                        state.includeClicks,
                    )
                if (nearest.isFinite()) offsetMillis = (frame - nearest) * 1000.0 / rate
            }
            if (offsetMillis.isFinite()) offsets.add(offsetMillis)
            val position = if (frame.isFinite()) session.transport.positionAt(frame) else Double.NaN
            val line =
                "%6.1f dB  %s  pos %s".format(
                    Locale.ROOT,
                    hit.levelDb,
                    if (offsetMillis.isFinite()) "%+7.2f ms".format(Locale.ROOT, offsetMillis) else "     — ms",
                    if (position.isFinite()) "%.3f".format(Locale.ROOT, position) else "—",
                )
            recent.addFirst(line)
            while (recent.size > RECENT_HITS) recent.removeLast()
            val first = recorder.firstFrame
            if (recorder.isRecording && first >= 0 && hit.inputFrame >= first) {
                takeHits +=
                    "%.2f,%.3f,%.1f".format(
                        Locale.ROOT,
                        hit.inputFrame - first,
                        (hit.inputFrame - first) * 1000.0 / recorder.sampleRate,
                        hit.levelDb,
                    )
            }
            Log.i(
                HITS_TAG,
                "hit nanoTime=${hit.nanoTime} inputFrame=${"%.2f".format(Locale.ROOT, hit.inputFrame)} " +
                    "levelDb=${"%.1f".format(
                        Locale.ROOT,
                        hit.levelDb,
                    )} outputFrame=${"%.2f".format(Locale.ROOT, frame)} " +
                    "position=$position offsetMs=$offsetMillis",
            )
        }
        val summary =
            if (offsets.count == 0) {
                ""
            } else {
                "Offsets   n=${offsets.count}, mean %+.2f ms, SD %.2f ms (vs nearest %s; %s)".format(
                    Locale.ROOT,
                    offsets.mean,
                    offsets.standardDeviation,
                    if (state.includeClicks) "stroke or click" else "stroke",
                    calibrationNote,
                )
            }
        state =
            state.copy(
                report = micReport(mic.stats()),
                summary = summary,
                recentHits = if (changed) recent.toList() else state.recentHits,
                recordingSeconds = recorder.seconds,
            )
        val now = System.nanoTime()
        if (mic.isRunning && now - lastStatsLogNanos >= STATS_LOG_INTERVAL_NANOS) {
            lastStatsLogNanos = now
            Log.i(HITS_TAG, "stats ${mic.stats()} ${state.summary}")
        }
        // The recorder stops itself when its buffer is full.
        if (state.recording && (!recorder.isRecording || recorder.seconds >= recordLimitSeconds)) {
            finishRecording(scope)
        }
    }

    fun toggleRecording(scope: CoroutineScope) {
        if (state.recording) {
            finishRecording(scope)
        } else {
            startRecording(Double.POSITIVE_INFINITY)
        }
    }

    /** Starts a take that saves itself after [seconds] (adb-driven: `--ei recordSeconds`). */
    fun startRecording(
        seconds: Double,
        label: String? = null,
    ) {
        if (!mic.isRunning || state.saving || state.recording) return
        if (label != null) setLabel(label)
        recordLimitSeconds = seconds
        takeHits.clear()
        recorder.begin()
        state = state.copy(recording = true, lastSaved = null)
    }

    private fun finishRecording(scope: CoroutineScope) {
        val take = recorder.end()
        val hits = takeHits.toList()
        val stats = mic.stats()
        val label = state.label.ifEmpty { "take" }
        val header =
            listOf(
                "# label=$label",
                "# device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE}",
                "# source=${stats.source} inputDevice=\"${stats.device}\" rate=${take.sampleRate} " +
                    "float=${stats.floatSamples} effectsOff=${stats.disabledEffects}",
                "# outputRoute=\"${session.engine.stats().outputDevice}\" playing=${session.isPlaying} " +
                    describeRun(),
                "# noiseFloorDb=${stats.noiseFloorDb} capturedFirstFrame=${take.firstFrame}",
                "frame,time_ms,level_db",
            )
        state = state.copy(recording = false, saving = true)
        scope.launch {
            val saved =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = recordingsDir() ?: error("no external files directory")
                        dir.mkdirs()
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
                        val base = "mic-$stamp-$label"
                        writeWav16(File(dir, "$base.wav"), take.samples, take.frames, take.sampleRate)
                        File(dir, "$base.csv").writeText((header + hits).joinToString("\n", postfix = "\n"))
                        File(dir, "$base.wav").path
                    }
                }
            saved
                .onSuccess {
                    Log.i(HITS_TAG, "saved $it frames=${take.frames} hits=${hits.size}")
                    state = state.copy(saving = false, lastSaved = "$it (${hits.size} hits)")
                }.onFailure {
                    Log.e(HITS_TAG, "save failed", it)
                    state = state.copy(saving = false, message = "Saving failed: $it")
                }
        }
    }
}

@Composable
internal fun MicPanel(
    state: MicPanelState,
    onToggle: () -> Unit,
    onIncludeClicks: (Boolean) -> Unit,
    onResetOffsets: () -> Unit,
    onLabel: (String) -> Unit,
    onRecord: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Microphone (M5)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Listens for stick hits and shows each one's offset from the nearest scheduled sound. " +
                "Audio stays on the phone; Record saves the raw mic to a WAV file in app storage.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.listening) {
                Button(onClick = onToggle) { Text("Stop mic") }
            } else {
                Button(onClick = onToggle) { Text("Listen") }
            }
            OutlinedButton(onClick = onResetOffsets) { Text("Reset offsets") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Offsets include clicks")
            Switch(checked = state.includeClicks, onCheckedChange = onIncludeClicks)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.label,
                onValueChange = onLabel,
                label = { Text("Take label") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onRecord, enabled = state.listening && !state.saving) {
                Text(if (state.recording) "Save %.0f s".format(Locale.ROOT, state.recordingSeconds) else "Record")
            }
        }
        state.lastSaved?.let { Text("Saved $it", style = MaterialTheme.typography.bodySmall) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val mono = Modifier.fillMaxWidth()
        Text(state.report, modifier = mono, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
        if (state.summary.isNotEmpty()) {
            Text(state.summary, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 15.sp)
        }
        if (state.recentHits.isNotEmpty()) {
            Text(
                state.recentHits.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
