package dev.co508.drums.ui.practice

import android.content.res.Configuration
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.playback.DrumKit
import dev.co508.drums.playback.LessonPattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.ui.falling.FallingNotesLayout
import dev.co508.drums.ui.falling.FallingNotesView
import dev.co508.drums.ui.sheet.SheetView
import kotlin.math.roundToInt

/**
 * The minimal M2 practice screen: the falling-notes view with play/stop and a
 * tempo slider (±1/±5). The full set of v0.1 controls is #16.
 *
 * Every frame is positioned from the audio clock and the transport's tempo
 * map, never from a separate timer (DECISIONS.md, "One Audio Clock").
 */
@Composable
fun PracticeScreen(
    lesson: Lesson,
    session: PlaybackSession,
    kit: DrumKit,
    onBack: () -> Unit,
) {
    val timeline = remember(lesson) { Expansion.expand(lesson.notation) }
    val layout = remember(timeline) { FallingNotesLayout.from(timeline) }
    val pattern = remember(timeline) { LessonPattern.build(timeline, kit) }
    var beatsPerMinute by rememberSaveable(lesson.id) { mutableIntStateOf(lesson.manifest.tempo.start) }
    var pixelsPerBeat by rememberSaveable { mutableFloatStateOf(DEFAULT_PIXELS_PER_BEAT) }
    var showCounts by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val viewPreference = remember { PracticeViewPreference(context) }
    var practiceView by remember { mutableStateOf(viewPreference.view) }
    var playing by remember { mutableStateOf(session.isPlaying) }
    var message by remember { mutableStateOf<String?>(null) }
    var frameNanos by remember { mutableLongStateOf(0L) }

    // The session stops by itself on focus loss or unplugged headphones; follow it every frame.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { t ->
                frameNanos = t
                if (playing != session.isPlaying) playing = session.isPlaying
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { session.close() }
    }
    val view = LocalView.current
    DisposableEffect(playing) {
        view.keepScreenOn = playing
        onDispose { view.keepScreenOn = false }
    }

    fun play() {
        session.transport.clickVolume = 1f
        session.transport.instrumentVolume = 1f
        message =
            if (session.play(PlaySettings(pattern, beatsPerMinute.toDouble(), countIn = true, loop = true))) {
                null
            } else {
                "Playback isn't available right now (a call in progress?)"
            }
        playing = session.isPlaying
    }

    fun stop() {
        session.stop()
        playing = false
    }

    fun changeTempo(bpm: Int) {
        beatsPerMinute = bpm.coerceIn(PackLoader.MIN_BPM, PackLoader.MAX_BPM)
        session.transport.setTempo(beatsPerMinute.toDouble())
    }

    val now: () -> Double = {
        val frame = session.clock.visualFrameAt(frameNanos) // Reading frameNanos redraws every frame.
        val position = if (frame.isNaN()) Double.NaN else session.transport.positionAt(frame)
        if (playing && !position.isNaN()) position else 0.0
    }

    val notes: @Composable (Modifier) -> Unit = { modifier ->
        when (practiceView) {
            PracticeView.FALLING_NOTES ->
                FallingNotesView(
                    layout,
                    now,
                    modifier,
                    pixelsPerBeat = pixelsPerBeat.dp,
                    showCounts = showCounts,
                    lightUp = playing,
                )
            PracticeView.SHEET -> SheetView(lesson.notation, timeline, now, modifier, showPlayhead = playing)
        }
    }
    val controls: @Composable () -> Unit = {
        Controls(
            playing = playing,
            onPlay = ::play,
            onStop = ::stop,
            beatsPerMinute = beatsPerMinute,
            onTempo = ::changeTempo,
            pixelsPerBeat = pixelsPerBeat,
            onZoom = { pixelsPerBeat = it },
            showCounts = showCounts,
            onShowCounts = { showCounts = it },
            fallingNotes = practiceView == PracticeView.FALLING_NOTES,
            message = message,
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                stop()
                onBack()
            }) { Text("Back") }
            Text(lesson.manifest.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            for ((option, label) in listOf(PracticeView.FALLING_NOTES to "Falling", PracticeView.SHEET to "Sheet")) {
                FilterChip(
                    selected = practiceView == option,
                    onClick = {
                        practiceView = option
                        viewPreference.view = option
                    },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Landscape: the lanes stay vertical, with the controls beside them.
            Row(Modifier.fillMaxSize()) {
                notes(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) { controls() }
            }
        } else {
            notes(Modifier.weight(1f).fillMaxWidth())
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Column { controls() } }
        }
    }
}

@Composable
private fun Controls(
    playing: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    beatsPerMinute: Int,
    onTempo: (Int) -> Unit,
    pixelsPerBeat: Float,
    onZoom: (Float) -> Unit,
    showCounts: Boolean,
    onShowCounts: (Boolean) -> Unit,
    fallingNotes: Boolean,
    message: String?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (playing) Button(onClick = onStop) { Text("Stop") } else Button(onClick = onPlay) { Text("Play") }
        Text("$beatsPerMinute BPM", style = MaterialTheme.typography.titleMedium)
    }
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (step in listOf(-5, -1, 1, 5)) {
            OutlinedButton(onClick = { onTempo(beatsPerMinute + step) }) { Text(if (step > 0) "+$step" else "$step") }
        }
    }
    Slider(
        value = beatsPerMinute.toFloat(),
        onValueChange = { onTempo(it.roundToInt()) },
        valueRange = PackLoader.MIN_BPM.toFloat()..PackLoader.MAX_BPM.toFloat(),
    )
    // Zoom and counting belong to the falling-notes view.
    if (!fallingNotes) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Zoom")
        Slider(value = pixelsPerBeat, onValueChange = onZoom, valueRange = 48f..240f, modifier = Modifier.weight(1f))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Counting")
        Switch(checked = showCounts, onCheckedChange = onShowCounts)
    }
}

private const val DEFAULT_PIXELS_PER_BEAT = 110f
