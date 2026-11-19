package dev.co508.drums.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.co508.drums.history.HistoryData
import dev.co508.drums.history.PracticeHistory
import dev.co508.drums.lesson.Pack
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryScreen(
    history: PracticeHistory,
    packs: List<Pack>,
) {
    var data by remember(history) { mutableStateOf<HistoryData?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val writeError by history.writeError.collectAsState()
    LaunchedEffect(history, retry) {
        readError = null
        try {
            history.data.collect { data = it }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            readError = "Couldn't load practice history."
        }
    }
    HistoryContent(data, packs, readError ?: writeError, onRetry = {
        history.retry()
        retry++
    })
}

/** The best tempo is a playback milestone, not a scored claim of playing accuracy. */
@Composable
internal fun HistoryContent(
    data: HistoryData?,
    packs: List<Pack>,
    error: String? = null,
    onRetry: () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val formatter =
        remember(locale, zone) {
            DateTimeFormatter
                .ofLocalizedDateTime(
                    FormatStyle.MEDIUM,
                    FormatStyle.SHORT,
                ).withLocale(locale)
                .withZone(zone)
        }
    val names =
        remember(packs) {
            packs
                .flatMap { pack ->
                    pack.lessons.map {
                        (pack.manifest.id to it.id) to
                            it.manifest.title
                    }
                }.toMap()
        }

    fun title(
        packId: String,
        lessonId: String,
    ): String = names[packId to lessonId] ?: lessonId
    LazyColumn(Modifier.fillMaxSize()) {
        if (error != null) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry history") }
                }
            }
        }
        if (data == null) {
            if (error == null) item { Text("Loading practice history…", Modifier.padding(24.dp)) }
        } else if (data.sessions.isEmpty()) {
            item { Text("No practice sessions yet. Play a lesson to start your log.", Modifier.padding(24.dp)) }
        } else {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text("Best tempo per lesson", style = MaterialTheme.typography.titleLarge)
                    Text("Highest playback tempo reached, not a playing accuracy score.")
                }
            }
            items(data.bests, key = { "best:${it.packId.length}:${it.packId}:${it.lessonId}" }) { best ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(title(best.packId, best.lessonId), style = MaterialTheme.typography.titleMedium)
                    Text("${best.highestTempo} BPM · ${best.packId} / ${best.lessonId}")
                }
            }
            item {
                HorizontalDivider()
                Text("Session log · newest first", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            }
            items(data.sessions, key = { "session:${it.id}" }) { session ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(title(session.packId, session.lessonId), style = MaterialTheme.typography.titleMedium)
                    Text(formatter.format(Instant.ofEpochMilli(session.startedAtMillis)))
                    Text(
                        "${String.format(
                            locale,
                            "%.2f",
                            session.durationMillis / 60_000.0,
                        )} min · Start ${session.startingTempo} BPM · Highest ${session.highestTempo} BPM",
                    )
                    Text(if (session.trainerUsed) "Speed trainer" else "Manual tempo")
                    Text("${session.packId} / ${session.lessonId}", style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
            }
        }
    }
}
