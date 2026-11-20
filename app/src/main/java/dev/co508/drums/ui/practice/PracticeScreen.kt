package dev.co508.drums.ui.practice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.co508.drums.history.PracticeHistory
import dev.co508.drums.history.PracticeIdentity
import dev.co508.drums.history.SessionScore
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.StrokeTimeline
import dev.co508.drums.playback.DrumKit
import dev.co508.drums.playback.LessonPattern
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.playback.Transport
import dev.co508.drums.scoring.ScoredLesson
import dev.co508.drums.scoring.ScoringSession
import dev.co508.drums.scoring.ScoringSnapshot
import dev.co508.drums.scoring.SpeakerMode
import dev.co508.drums.scoring.Strictness
import dev.co508.drums.scoring.toSessionScore
import dev.co508.drums.settings.LessonPreferences
import dev.co508.drums.settings.PracticePreferences
import dev.co508.drums.settings.PracticeSettingsStore
import dev.co508.drums.settings.preferenceKey
import dev.co508.drums.ui.falling.FallingNotesLayout
import dev.co508.drums.ui.falling.FallingNotesView
import dev.co508.drums.ui.history.scoreLines
import dev.co508.drums.ui.settings.thisPhone
import dev.co508.drums.ui.sheet.SheetView
import java.io.IOException
import kotlin.math.roundToInt

/** Positions and the displayed trainer tempo follow the heard audio, not a UI timer. */
@Composable
fun PracticeScreen(
    lesson: Lesson,
    session: PlaybackSession,
    kit: DrumKit,
    store: PracticeSettingsStore,
    onBack: () -> Unit,
    scoring: ScoringSession? = null,
    history: PracticeHistory? = null,
    speakerMode: SpeakerMode? = null,
) {
    var initial by remember(lesson.preferenceKey) { mutableStateOf<PracticePreferences?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(lesson.preferenceKey, retry) {
        loadError = false
        try {
            initial = store.snapshot()
        } catch (_: IOException) {
            loadError = true
        }
    }
    DisposableEffect(session) {
        onDispose {
            // Finishes in the background, so the run's score is still saved.
            scoring?.stop()
            speakerMode?.exit()
            session.close()
        }
    }
    val loaded = initial
    if (loaded == null) {
        Column {
            TextButton(onClick = onBack) { Text("Back") }
            Text(if (loadError) "Couldn't load practice settings." else "Loading practice settings…")
            if (loadError) Button(onClick = { retry++ }) { Text("Retry") }
        }
        return
    }
    var settings by remember(lesson.preferenceKey) { mutableStateOf(loaded.lesson(lesson)) }
    LaunchedEffect(session, loaded.defaults) { loaded.defaults.applyTo(session.clock) }
    var practiceView by remember { mutableStateOf(loaded.practiceView) }
    var pixelsPerBeat by remember { mutableStateOf(loaded.zoomPixelsPerBeat) }
    var playing by remember { mutableStateOf(session.isPlaying) }
    var message by remember { mutableStateOf<String?>(null) }
    var frameNanos by remember { mutableLongStateOf(0L) }
    var heardTempo by remember { mutableIntStateOf(settings.tempo) }
    var runSettings by remember { mutableStateOf<PlaySettings?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var tempoStepped by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var scoringOn by remember { mutableStateOf(loaded.scoring && scoring != null && micGranted(context)) }
    var explainMic by remember { mutableStateOf(false) }
    var scoringNote by remember { mutableStateOf<String?>(null) }
    var scoredRun by remember { mutableStateOf(false) }
    var onSpeaker by remember { mutableStateOf(false) }
    var lastScore by remember(lesson.preferenceKey) { mutableStateOf<SessionScore?>(null) }
    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            scoringOn = granted
            store.saveScoring(granted)
            scoringNote = if (granted) null else "Scoring needs the microphone. Practice works without it."
        }
    val writeError by store.writeError.collectAsState()
    val timeline = remember(lesson, settings.leadWithLeft) { Expansion.expand(lesson.notation, settings.leadWithLeft) }
    val pattern = remember(timeline, kit) { LessonPattern.build(timeline, kit) }

    fun change(value: LessonPreferences) {
        settings = value.normalized()
        store.saveLesson(lesson, settings)
        session.transport.clickVolume = if (settings.click) settings.clickVolume else 0f
        val snare = if (settings.snare) settings.snareVolume else 0f
        session.transport.instrumentVolume = speakerMode?.instrumentVolume(snare) ?: snare
    }

    // With scoring on, keep the output open so its route (speaker or headphones) is known before Play.
    LaunchedEffect(scoringOn) { if (scoringOn) session.open() }

    LaunchedEffect(session, lesson.preferenceKey) {
        var highlightUntil = 0L
        var nextOutputCheck = 0L
        while (true) {
            withFrameNanos { t ->
                frameNanos = t
                session.updatePracticeLog()
                if (t >= nextOutputCheck) {
                    session.checkOutput()
                    onSpeaker = SpeakerMode.appliesTo(session.engine.stats().outputRoute)
                    nextOutputCheck = t + 1_000_000_000L
                }
                val frame = session.clock.visualFrameAt(t)
                if (session.isPlaying &&
                    runSettings != null &&
                    session.transport.settings === runSettings &&
                    frame.isFinite()
                ) {
                    val bpm = session.transport.beatsPerMinuteAt(frame)
                    if (bpm.isFinite() && bpm > 0) {
                        if (runSettings?.trainer != null &&
                            bpm.roundToInt() > heardTempo
                        ) {
                            highlightUntil = t + 800_000_000L
                        }
                        heardTempo = bpm.roundToInt()
                        if (settings.tempo != heardTempo) change(settings.rememberHeardTempo(bpm))
                        progress =
                            practiceProgress(requireNotNull(runSettings), session.transport.positionAt(frame), bpm)
                    }
                    // FINISHED is ahead of the speaker by the output buffer. Wait for the heard end.
                    if (session.transport.state == Transport.State.FINISHED &&
                        session.transport.positionAt(frame) >= pattern.lengthQuarters
                    ) {
                        session.stop(PlaybackSession.StopReason.TRANSPORT_FINISHED)
                    }
                }
                playing = session.isPlaying
                if (!playing && scoring?.isListening == true) scoring.stop()
                if (speakerMode != null) {
                    if (playing && scoredRun && !speakerMode.isActive && onSpeaker) {
                        // The route became known (or changed to the speaker) after Play.
                        speakerMode.enter()
                    } else if (!playing && speakerMode.isActive) {
                        speakerMode.exit()
                        change(settings) // The snare's own volume again.
                    }
                }
                tempoStepped = playing && t < highlightUntil
                if (!playing) progress = null
            }
        }
    }
    val view = LocalView.current
    DisposableEffect(view, playing) {
        val previous = view.keepScreenOn
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = previous }
    }
    DisposableEffect(session) {
        session.onInterrupted = { reason ->
            message =
                when (reason) {
                    PlaybackSession.StopReason.HEADPHONES_UNPLUGGED -> "Playback stopped: headphones disconnected."
                    PlaybackSession.StopReason.ENGINE_ERROR ->
                        "Playback stopped: audio recovery failed. Press Play to try again."
                    else -> "Playback stopped: a call or another player took audio focus."
                }
        }
        onDispose { session.onInterrupted = null }
    }
    val lastPosition = remember(lesson.preferenceKey) { doubleArrayOf(0.0) }
    val now: () -> Double = {
        val frame = session.clock.visualFrameAt(frameNanos)
        if (playing && frame.isFinite()) {
            val position = session.transport.positionAt(frame)
            if (position.isFinite()) lastPosition[0] = position
        }
        if (playing) lastPosition[0] else 0.0
    }
    if (explainMic) {
        AlertDialog(
            onDismissRequest = { explainMic = false },
            title = { Text("Score your playing?") },
            text = {
                Text(
                    "Ratamatap listens through the microphone to hear your stick hits and shows how far " +
                        "off each one was. The sound is analysed on the phone as you play; nothing is " +
                        "recorded or sent anywhere. Headphones work best.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainMic = false
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explainMic = false }) { Text("Not now") } },
        )
    }
    PracticeContent(
        lesson = lesson,
        settings = settings,
        practiceView = practiceView,
        playing = playing,
        heardTempo = heardTempo,
        now = now,
        onChange = ::change,
        onTempo = {
            change(settings.copy(tempo = it))
            session.transport.setTempo(settings.tempo.toDouble())
        },
        onView = {
            practiceView = it
            store.saveView(it)
        },
        onPlayStop = {
            if (playing) {
                session.stop()
                scoring?.stop()
            } else {
                // A scored run on the phone speaker mutes the snare and swaps the click from the first note.
                val route = session.engine.stats().outputRoute
                if (scoringOn && scoring != null && micGranted(context) && SpeakerMode.appliesTo(route)) {
                    speakerMode?.enter()
                }
                change(settings)
                // Speaker mode learns how loudly the click leaks into the mic from the count-in.
                val playSettings =
                    settings.playSettings(pattern).let {
                        if (speakerMode?.isActive == true && !it.countIn) {
                            PlaySettings(
                                it.pattern,
                                it.beatsPerMinute,
                                countIn = true,
                                loop = it.loop,
                                trainer = it.trainer,
                            )
                        } else {
                            it
                        }
                    }
                heardTempo = playSettings.beatsPerMinute.roundToInt()
                message =
                    if (session.play(
                            playSettings,
                            PracticeIdentity(lesson.packId, lesson.id),
                        )
                    ) {
                        null
                    } else {
                        "Playback isn't available right now (a call in progress?)"
                    }
                runSettings = playSettings
                scoredRun = false
                if (session.isPlaying && scoringOn && scoring != null) {
                    val route = session.engine.stats().outputRoute
                    val calibration = route?.let { loaded.calibration(thisPhone, it) }
                    val runId = session.runId
                    val strictness = Strictness.of(loaded.strictness)
                    lastScore = null
                    scoredRun =
                        micGranted(context) &&
                        startScoring(
                            scoring,
                            timeline,
                            strictness,
                            playSettings.loop,
                            calibration?.latencyMillis ?: 0.0,
                            if (speakerMode?.isActive == true) playSettings.pattern else null,
                        ) { final ->
                            val stats = final.stats
                            if (runId != null && stats.hits + stats.misses > 0) {
                                val score = stats.toSessionScore(runId, strictness, route?.key, calibration != null)
                                history?.recordScore(score)
                                lastScore = score
                            }
                        }
                    if (!scoredRun && speakerMode?.isActive == true) {
                        speakerMode.exit()
                        change(settings)
                    }
                    scoringNote =
                        when {
                            !scoredRun -> "Couldn't open the microphone; this run isn't scored."
                            calibration == null ->
                                "${route?.label ?: "This output"} isn't calibrated: Settings → Calibrate hit timing."
                            else -> null
                        }
                }
            }
            playing = session.isPlaying
        },
        scoring = scoringOn,
        onScoring =
            if (scoring == null) {
                null
            } else {
                { on ->
                    if (on && !micGranted(context)) {
                        explainMic = true
                    } else {
                        scoringOn = on
                        store.saveScoring(on)
                        scoringNote = null
                    }
                }
            },
        scoringNote =
            listOfNotNull(scoringNote, if (scoringOn && onSpeaker) SPEAKER_NOTE else null)
                .joinToString("\n")
                .ifEmpty { null },
        marks = { if (scoringOn || scoredRun) scoring?.snapshot else null },
        runSummary = if (playing) null else lastScore?.let { scoreLines(it) },
        onBack = {
            session.stop()
            onBack()
        },
        message = message ?: writeError,
        progress = progress,
        tempoStepped = tempoStepped,
        pixelsPerBeat = pixelsPerBeat,
        onZoom = {
            pixelsPerBeat = it
            store.saveZoom(it)
        },
    )
}

/** Pure screen content: also used by orientation, controls and screenshot tests. */
@Composable
internal fun PracticeContent(
    lesson: Lesson,
    settings: LessonPreferences,
    practiceView: PracticeView,
    playing: Boolean,
    heardTempo: Int,
    now: () -> Double,
    onChange: (LessonPreferences) -> Unit,
    onTempo: (Int) -> Unit,
    onView: (PracticeView) -> Unit,
    onPlayStop: () -> Unit,
    onBack: () -> Unit,
    message: String? = null,
    progress: String? = null,
    tempoStepped: Boolean = false,
    pixelsPerBeat: Float = PracticePreferences.DEFAULT_ZOOM,
    onZoom: (Float) -> Unit = {},
    scoring: Boolean = false,
    onScoring: ((Boolean) -> Unit)? = null,
    scoringNote: String? = null,
    marks: () -> ScoringSnapshot? = { null },
    runSummary: List<String>? = null,
) {
    val timeline = remember(lesson, settings.leadWithLeft) { Expansion.expand(lesson.notation, settings.leadWithLeft) }
    val layout = remember(timeline) { FallingNotesLayout.from(timeline) }
    val notation = remember(lesson, settings.leadWithLeft) { lesson.notation.forDisplay(settings.leadWithLeft) }
    val signature = timeline.timeSignature
    val beatLabel =
        if (signature.isCompound) {
            "Dotted-quarter beats"
        } else {
            when (signature.beatType) {
                2 -> "Half-note beats"
                4 -> "Quarter-note beats"
                8 -> "Eighth-note beats"
                else -> "1/${signature.beatType}-note beats"
            }
        }
    var tipsExpanded by rememberSaveable(lesson.preferenceKey) { mutableStateOf(false) }
    val notes: @Composable (Modifier) -> Unit = { modifier ->
        when (practiceView) {
            PracticeView.FALLING_NOTES ->
                FallingNotesView(
                    layout,
                    now,
                    modifier,
                    pixelsPerBeat = pixelsPerBeat.dp,
                    showCounts = settings.showCounts,
                    lightUp = playing,
                    marks = marks,
                )
            PracticeView.SHEET -> SheetView(notation, timeline, now, modifier, showPlayhead = playing, marks = marks)
        }
    }
    val controls: @Composable () -> Unit = {
        PracticeControls(
            settings,
            playing,
            heardTempo,
            practiceView,
            onChange,
            onTempo,
            onPlayStop,
            message,
            beatLabel,
            pixelsPerBeat,
            onZoom,
            scoring,
            onScoring,
            scoringNote,
            runSummary,
        )
        TextButton(onClick = { tipsExpanded = !tipsExpanded }) {
            Text(if (tipsExpanded) "Hide description and tips" else "Description and tips")
        }
        if (tipsExpanded) {
            Text(lesson.manifest.description)
            lesson.manifest.tips.forEach { Text("• $it", Modifier.padding(top = 8.dp)) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(lesson.manifest.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((option, label) in listOf(PracticeView.FALLING_NOTES to "Falling", PracticeView.SHEET to "Sheet")) {
                FilterChip(selected = practiceView == option, onClick = { onView(option) }, label = { Text(label) })
            }
        }
        progress?.let {
            val colors = MaterialTheme.colorScheme
            Text(
                "$heardTempo BPM · $it",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (tempoStepped) colors.tertiaryContainer else colors.surfaceContainer,
                        ).padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (tempoStepped) colors.onTertiaryContainer else colors.onSurface,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Row(Modifier.weight(1f)) {
                notes(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    controls()
                }
            }
        } else {
            notes(Modifier.weight(1f).fillMaxWidth())
            // Keep notation visible even with large fonts or expanded tips; every control scrolls.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) { controls() }
            }
        }
    }
}

/** The last scored run, under the controls until the next Play (DECISIONS.md, "Run Summaries In The Practice Log"). */
@Composable
internal fun RunSummaryCard(lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(12.dp),
    ) {
        Text("Last scored run", style = MaterialTheme.typography.titleSmall)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** The headphones recommendation (DECISIONS.md, "Speaker Mode"). */
internal const val SPEAKER_NOTE =
    "Headphones work best. On the phone speaker, scored runs mute the snare and play a low click " +
        "the microphone ignores, so it hears only you."

private fun micGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

// Only called after micGranted().
@SuppressLint("MissingPermission")
private fun startScoring(
    scoring: ScoringSession,
    timeline: StrokeTimeline,
    strictness: Strictness,
    loops: Boolean,
    latencyMillis: Double,
    speakerClicks: Pattern?,
    onFinished: (ScoringSnapshot) -> Unit,
): Boolean = scoring.start(ScoredLesson.from(timeline), strictness, loops, latencyMillis, speakerClicks, onFinished)

/** The sheet must show the same swapped grace and main hands as the expanded playback. */
internal fun Notation.forDisplay(leadWithLeft: Boolean): Notation =
    if (!leadWithLeft) {
        this
    } else {
        copy(
            measures =
                measures.map { measure ->
                    measure.copy(
                        notes =
                            measure.notes.map { note ->
                                note.copy(
                                    hand = note.hand.swapped(),
                                    graceNotes = note.graceNotes.map { it.copy(hand = it.hand.swapped()) },
                                )
                            },
                    )
                },
        )
    }

private fun Hand?.swapped(): Hand? =
    when (this) {
        Hand.RIGHT -> Hand.LEFT
        Hand.LEFT -> Hand.RIGHT
        null -> null
    }
