package dev.co508.drums.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.co508.drums.settings.LessonPreferences
import kotlin.math.roundToInt

@Composable
internal fun PracticeControls(
    settings: LessonPreferences,
    playing: Boolean,
    heardTempo: Int,
    view: PracticeView,
    onChange: (LessonPreferences) -> Unit,
    onTempo: (Int) -> Unit,
    onPlayStop: () -> Unit,
    message: String?,
    beatLabel: String,
    pixelsPerBeat: Float,
    onZoom: (Float) -> Unit,
) {
    val tempo = if (playing) heardTempo else settings.tempo
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onPlayStop) { Text(if (playing) "Stop" else "Play") }
        Text("$tempo BPM", style = MaterialTheme.typography.titleMedium)
    }
    Text(beatLabel, style = MaterialTheme.typography.bodySmall)
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (step in listOf(-5, -1, 1, 5)) {
            OutlinedButton(onClick = { onTempo((tempo + step).coerceIn(30, 300)) }, modifier = Modifier.weight(1f)) {
                Text(if (step > 0) "+$step" else "$step")
            }
        }
    }
    Slider(
        value = tempo.toFloat().coerceIn(30f, 300f),
        onValueChange = { onTempo(it.roundToInt()) },
        valueRange = 30f..300f,
        modifier = Modifier.semantics { contentDescription = "Tempo" },
    )
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
    ) { Text(if (expanded) "Hide practice options" else "Practice options") }
    if (!expanded) return
    Text("Hands are panned slightly left and right.", style = MaterialTheme.typography.bodySmall)
    if (playing) {
        Text(
            "Stop to change count-in, loop, lead hand or trainer setup.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    Toggle("Count-in", settings.countIn, !playing) { onChange(settings.copy(countIn = it)) }
    Toggle("Loop", settings.loop, !playing) { onChange(settings.copy(loop = it)) }
    Toggle("Lead with left", settings.leadWithLeft, !playing) { onChange(settings.copy(leadWithLeft = it)) }
    Volume(
        "Click",
        settings.click,
        settings.clickVolume,
        onToggle = { onChange(settings.copy(click = it)) },
        onVolume = { onChange(settings.copy(clickVolume = it)) },
    )
    Volume(
        "Snare",
        settings.snare,
        settings.snareVolume,
        onToggle = { onChange(settings.copy(snare = it)) },
        onVolume = { onChange(settings.copy(snareVolume = it)) },
    )
    Toggle("Speed trainer", settings.trainer, !playing && settings.loop) { onChange(settings.copy(trainer = it)) }
    if (!settings.loop) Text("Turn on Loop to use the speed trainer.", style = MaterialTheme.typography.bodySmall)
    if (settings.trainer) {
        NumberSetting("Start BPM", settings.trainerStart, 30..300, !playing) {
            onChange(settings.copy(trainerStart = it, trainerTarget = maxOf(it, settings.trainerTarget)))
        }
        NumberSetting(
            "Increase BPM",
            settings.trainerStep,
            1..50,
            !playing,
        ) { onChange(settings.copy(trainerStep = it)) }
        NumberSetting(
            "Every loops",
            settings.trainerLoops,
            1..100,
            !playing,
        ) { onChange(settings.copy(trainerLoops = it)) }
        NumberSetting("Target BPM", settings.trainerTarget, settings.trainerStart..300, !playing) {
            onChange(settings.copy(trainerTarget = it))
        }
        Text(
            "Starts at ${settings.trainerStart}; +${settings.trainerStep} BPM every ${settings.trainerLoops} loops, up to ${settings.trainerTarget}.",
        )
    }
    if (view == PracticeView.FALLING_NOTES) {
        Text("Zoom")
        Slider(
            value = pixelsPerBeat,
            onValueChange = onZoom,
            valueRange = 48f..480f,
            modifier = Modifier.semantics { contentDescription = "Zoom" },
        )
        Toggle("Counting", settings.showCounts) { onChange(settings.copy(showCounts = it)) }
    }
}

@Composable
private fun Toggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun Volume(
    label: String,
    enabled: Boolean,
    value: Float,
    onToggle: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
) {
    Toggle(label, enabled, onChange = onToggle)
    Text("$label volume ${(value * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
    Slider(
        value = value,
        onValueChange = onVolume,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = "$label volume" },
    )
}

@Composable
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label: $value")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled && range.first < range.last,
            modifier = Modifier.semantics { contentDescription = label },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (step in listOf(-1, 1)) {
                OutlinedButton(
                    onClick = { onChange((value + step).coerceIn(range)) },
                    enabled = enabled && value + step in range,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "$label ${if (step > 0) "increase" else "decrease"}"
                        },
                ) {
                    Text(if (step > 0) "+1" else "−1")
                }
            }
        }
    }
}
