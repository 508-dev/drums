package dev.co508.drums.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.hits.CalibrationMethod
import dev.co508.drums.hits.CalibrationOutcome
import dev.co508.drums.hits.Calibrator
import dev.co508.drums.hits.MicCapture
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.settings.PracticePreferences
import dev.co508.drums.settings.PracticeSettingsStore
import dev.co508.drums.settings.StoredCalibration
import dev.co508.drums.settings.calibrationKey
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** The phone, as calibrations are keyed (see [calibrationKey]). */
val thisPhone: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Settings → Hit timing calibration. Owns the output stream while shown, like
 * the practice screen, and asks for the microphone only when a calibration
 * starts.
 */
@Composable
fun CalibrationScreen(
    session: PlaybackSession,
    mic: MicCapture,
    store: PracticeSettingsStore,
) {
    val context = LocalContext.current
    val calibrator = remember(session, mic) { Calibrator(session, mic) }
    var state by remember { mutableStateOf<Calibrator.State>(Calibrator.State.Idle) }
    var route by remember { mutableStateOf<OutputRoute?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<CalibrationMethod?>(null) }
    val preferences by store.data.collectAsState(initial = PracticePreferences())

    DisposableEffect(session) {
        session.open()
        session.onInterrupted = calibrator::onInterrupted
        onDispose {
            calibrator.cancel()
            session.onInterrupted = null
            session.close()
        }
    }

    // Only reached with the permission granted (checked or just granted).
    @SuppressLint("MissingPermission")
    fun run(method: CalibrationMethod) {
        message = null
        calibrator.start(method)
        state = calibrator.state
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val method = pending
            pending = null
            if (granted && method != null) {
                run(method)
            } else if (!granted) {
                message = "Calibration needs the microphone. Practice works without it."
            }
        }

    fun begin(method: CalibrationMethod) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            run(method)
        } else {
            pending = method
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(calibrator) {
        while (true) {
            calibrator.tick()
            val now = calibrator.state
            if (now != state) {
                state = now
                if (now is Calibrator.State.Finished) saveMeasuredCalibration(now, store)
            }
            route = session.engine.stats().outputRoute
            delay(TICK_MILLIS)
        }
    }

    CalibrationContent(
        route = route,
        current = route?.let { preferences.calibration(thisPhone, it) },
        saved = preferences.calibrations.filterKeys { it.startsWith("$thisPhone|") },
        state = state,
        message = message,
        onStart = ::begin,
        onCancel = {
            calibrator.cancel()
            state = calibrator.state
        },
        onForget = store::forgetCalibration,
    )
}

/** Saves a measured calibration for the route it ran on; failures and unknown routes save nothing. */
fun saveMeasuredCalibration(
    finished: Calibrator.State.Finished,
    store: PracticeSettingsStore,
) {
    val outcome = finished.outcome as? CalibrationOutcome.Measured ?: return
    val route = finished.route ?: return
    store.saveCalibration(
        calibrationKey(thisPhone, route),
        StoredCalibration(
            latencyMillis = outcome.latencyMillis,
            spreadMillis = outcome.spreadMillis,
            method = finished.method.name,
            routeLabel = route.label,
            savedAtMillis = System.currentTimeMillis(),
        ),
    )
}

private const val TICK_MILLIS = 50L

@Composable
internal fun CalibrationContent(
    route: OutputRoute?,
    current: StoredCalibration?,
    saved: Map<String, StoredCalibration>,
    state: Calibrator.State,
    message: String?,
    onStart: (CalibrationMethod) -> Unit,
    onCancel: () -> Unit,
    onForget: (String) -> Unit,
) {
    val listening = state as? Calibrator.State.Listening
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "When Ratamatap scores your playing, it hears your hits through the microphone. " +
                "Each output (the phone speaker, wired headphones, each Bluetooth headset) adds its own " +
                "delay, so measure it once for each. The microphone is only on while measuring; " +
                "nothing is recorded or leaves the phone.",
        )
        Text("Output now: ${route?.label ?: "unknown"}", style = MaterialTheme.typography.titleMedium)
        Text(
            current?.let { "Calibrated: ${describe(it)}" }
                ?: "Not calibrated yet. Headphones are best for scoring; the speaker works too.",
        )

        if (listening != null) {
            val what =
                if (listening.method ==
                    CalibrationMethod.LOOPBACK
                ) {
                    "Listening to the clicks…"
                } else {
                    "Tap along on your pad…"
                }
            Text(what, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(
                progress = { (listening.heard.toFloat() / listening.expected).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Heard ${listening.heard} of ${listening.expected}")
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        } else {
            if (route == null || route.kind == OutputRoute.Kind.SPEAKER) {
                Text("Speaker check (automatic)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn the media volume up, put the phone down in a quiet room and stay quiet: " +
                        "it plays 16 clicks and listens to itself. About 15 seconds.",
                )
                Button(onClick = { onStart(CalibrationMethod.LOOPBACK) }) { Text("Start speaker check") }
            }
            Text("Tap along", style = MaterialTheme.typography.titleMedium)
            Text(
                "With headphones on and the phone near your pad: after a bar of count-in, " +
                    "hit the pad on each of 16 clicks, as you would when playing. About 15 seconds.",
            )
            Button(onClick = { onStart(CalibrationMethod.TAP_ALONG) }) { Text("Start tap-along") }
        }

        resultText(state)?.let { (text, isError) ->
            Text(
                text,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (saved.isNotEmpty()) {
            Text("Saved on this phone", style = MaterialTheme.typography.titleMedium)
            for ((key, value) in saved.entries.sortedBy { it.value.routeLabel }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${value.routeLabel}: ${describe(value)}", Modifier.weight(1f))
                    TextButton(onClick = { onForget(key) }) { Text("Forget") }
                }
            }
        }
    }
}

private fun describe(value: StoredCalibration): String {
    val how = if (value.method == CalibrationMethod.LOOPBACK.name) "speaker check" else "tap-along"
    val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(value.savedAtMillis))
    return "%+.1f ms (%s, %s)".format(Locale.ROOT, value.latencyMillis, how, date)
}

/** The last run's result for the player, and whether it is a failure. */
internal fun resultText(state: Calibrator.State): Pair<String, Boolean>? =
    when (state) {
        is Calibrator.State.Stopped -> state.message to true
        is Calibrator.State.Finished -> {
            val loopback = state.method == CalibrationMethod.LOOPBACK
            val things = if (loopback) "clicks" else "taps"
            when (val outcome = state.outcome) {
                is CalibrationOutcome.Measured -> {
                    val saved =
                        state.route?.let { " Saved for ${it.label}." }
                            ?: " The output wasn't known, so it wasn't saved."
                    "Measured %+.1f ms, consistent to ±%.1f ms.%s".format(
                        Locale.ROOT,
                        outcome.latencyMillis,
                        outcome.spreadMillis,
                        saved,
                    ) to false
                }
                is CalibrationOutcome.Failed ->
                    when (outcome.reason) {
                        CalibrationOutcome.Reason.NOT_HEARD ->
                            if (loopback) {
                                "Didn't hear the clicks. Turn the media volume up and try again."
                            } else {
                                "Didn't hear any taps. Hit the pad firmly, with the phone nearby."
                            }
                        CalibrationOutcome.Reason.TOO_FEW ->
                            "Heard only ${outcome.heard} of ${outcome.expected} $things. " +
                                if (loopback) "Turn the volume up and keep the room quiet." else "Tap on every click."
                        CalibrationOutcome.Reason.INCONSISTENT ->
                            if (loopback) {
                                "The clicks came back at different delays; something else may be making noise. Try again."
                            } else {
                                "Your taps varied by about ±%.0f ms. Tap steadily with the click and try again."
                                    .format(Locale.ROOT, outcome.spreadMillis)
                            }
                    } to true
            }
        }
        else -> null
    }
