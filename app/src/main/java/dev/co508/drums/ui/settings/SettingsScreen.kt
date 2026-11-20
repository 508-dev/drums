package dev.co508.drums.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.co508.drums.settings.AppDefaults
import kotlin.math.roundToInt

@Composable
internal fun SettingsContent(
    defaults: AppDefaults,
    enabled: Boolean,
    onChange: (AppDefaults) -> Unit,
    onAbout: () -> Unit,
    onCalibration: () -> Unit = {},
) {
    val value = defaults.normalized()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Default practice sounds", style = MaterialTheme.typography.titleMedium)
        Text("Used for lessons without saved settings. Existing lesson choices are kept.")
        SettingSwitch("Default click", value.click, enabled) { onChange(value.copy(click = it)) }
        SettingVolume(
            "Click volume",
            value.clickVolume,
            enabled && value.click,
        ) { onChange(value.copy(clickVolume = it)) }
        SettingSwitch("Default snare", value.snare, enabled) { onChange(value.copy(snare = it)) }
        SettingVolume(
            "Snare volume",
            value.snareVolume,
            enabled && value.snare,
        ) { onChange(value.copy(snareVolume = it)) }
        SettingSwitch("Default count-in", value.countIn, enabled) { onChange(value.copy(countIn = it)) }
        Text("Sound/visual offset: ${value.visualOffsetMillis} ms", style = MaterialTheme.typography.titleMedium)
        Text(
            "Global for every lesson. Positive values delay the visuals; negative values advance them. Audio timing is unchanged.",
        )
        Slider(
            value.visualOffsetMillis.toFloat(),
            { onChange(value.copy(visualOffsetMillis = it.roundToInt())) },
            valueRange = -500f..500f,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Sound/visual offset" },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (step in listOf(-5, 5)) {
                OutlinedButton(onClick = {
                    onChange(
                        value.copy(
                            visualOffsetMillis =
                                (value.visualOffsetMillis + step).coerceIn(-500, 500),
                        ),
                    )
                }, enabled = enabled) {
                    Text(if (step > 0) "+5 ms" else "−5 ms")
                }
            }
            TextButton(onClick = { onChange(value.copy(visualOffsetMillis = 0)) }, enabled = enabled) { Text("Reset") }
        }
        Text("Hit timing", style = MaterialTheme.typography.titleMedium)
        Text("Measure your phone's delay per output, so scored hits line up with what you hear.")
        OutlinedButton(onClick = onCalibration) { Text("Calibrate hit timing") }
        TextButton(onClick = onAbout) { Text("About") }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun SettingVolume(
    label: String,
    value: Float,
    enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    Text("$label: ${(value * 100).roundToInt()}%")
    Slider(value, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
}

@Composable
internal fun AboutContent(
    version: String,
    onLicenses: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ratamatap", style = MaterialTheme.typography.headlineMedium)
        Text("Version $version")
        Text("An offline snare-rudiment practice app. No analytics, ads or network permission.")
        Text("App code: GPL-3.0-only. Built-in lessons: CC BY-SA 4.0.")
        Text("Source: https://github.com/508-dev/ratamatap")
        TextButton(onClick = onLicenses) { Text("Licenses and credits") }
    }
}
