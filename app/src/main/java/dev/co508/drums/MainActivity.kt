package dev.co508.drums

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.ui.library.LibraryScreen
import dev.co508.drums.ui.practice.PracticeScreen
import dev.co508.drums.ui.settings.CalibrationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val app get() = application as DrumsApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                DrumsScreen(app)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app stops playback and releases the output stream.
        app.playback.close()
    }
}

@Composable
private fun DrumsScreen(app: DrumsApp) {
    var pack by remember { mutableStateOf<PackResult?>(null) }
    LaunchedEffect(Unit) { pack = withContext(Dispatchers.Default) { app.builtInPack } }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val result = pack) {
                null -> Text(stringResource(R.string.loading), Modifier.align(Alignment.Center))
                is PackResult.Failure ->
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.pack_failed), style = MaterialTheme.typography.titleMedium)
                        result.errors.forEach { Text(it) }
                    }
                is PackResult.Success -> {
                    LibraryScreen(
                        listOf(result.pack),
                        app.settings,
                        app.history,
                        calibration = { CalibrationScreen(app.playback, app.mic, app.settings) },
                    ) { lesson, onBack ->
                        PracticeScreen(
                            lesson,
                            app.playback,
                            app.kit,
                            app.settings,
                            onBack = onBack,
                            scoring = app.scoring,
                            history = app.history,
                        )
                    }
                }
            }
        }
    }
}
