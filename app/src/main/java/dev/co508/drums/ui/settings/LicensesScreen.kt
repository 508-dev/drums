package dev.co508.drums.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Hand-maintained alongside docs/third-party.md, with bundled offline legal texts. */
internal data class LicenseProject(
    val title: String,
    val credit: String,
    val license: String,
    val file: String,
    val source: String,
)

internal val licenseProjects =
    listOf(
        LicenseProject(
            "Bravura 1.482",
            "© Steinberg Media Technologies GmbH. Unmodified font; reserved name Bravura.",
            "SIL Open Font License 1.1",
            "Bravura-OFL.txt",
            "https://github.com/steinbergmedia/bravura",
        ),
        LicenseProject(
            "Versilian Community Sample Library",
            "Versilian Studios LLC, revision c1ea7bc. Snare hits downmixed, resampled, trimmed, normalized and faded; buzz built from ghost-hit bounces. Per-file provenance: samples/README.md in app assets and source.",
            "CC0 1.0",
            "CC0-1.0.txt",
            "https://github.com/sgossner/VCSL/tree/c1ea7bcc3c7309650ab0da9d15c9cd1fbc4a4c7e",
        ),
        LicenseProject(
            "Kotlin standard library 2.3.20",
            "JetBrains and Kotlin contributors.",
            "Apache License 2.0",
            "Apache-2.0.txt",
            "https://github.com/JetBrains/kotlin",
        ),
        LicenseProject(
            "kotlinx.serialization 1.11.0",
            "JetBrains and contributors. JSON manifests and settings.",
            "Apache License 2.0",
            "Apache-2.0.txt",
            "https://github.com/Kotlin/kotlinx.serialization",
        ),
        LicenseProject(
            "kotlinx.coroutines",
            "JetBrains and contributors. Background tasks; transitively resolved version.",
            "Apache License 2.0",
            "Apache-2.0.txt",
            "https://github.com/Kotlin/kotlinx.coroutines",
        ),
        LicenseProject(
            "AndroidX, Compose and DataStore",
            "The Android Open Source Project and contributors. Core 1.19.0, Activity 1.13.0, DataStore 1.2.1; Compose UI/Material 3 BOM 2026.08.00.",
            "Apache License 2.0",
            "Apache-2.0.txt",
            "https://android.googlesource.com/platform/frameworks/support/",
        ),
        LicenseProject(
            "Built-in lesson content",
            "Drums contributors. Original notation and wording, traditional patterns; no PAS endorsement. Share and adapt with attribution and share-alike licensing.",
            "CC BY-SA 4.0",
            "CC-BY-SA-4.0.txt",
            "https://github.com/508-dev/drums/tree/main/lessons",
        ),
        LicenseProject(
            "Drums app",
            "Drums contributors. Free software; corresponding source is in the public repository.",
            "GPL-3.0-only",
            "GPL-3.0.txt",
            "https://github.com/508-dev/drums",
        ),
    )

@Composable
internal fun LicensesContent() {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize()) {
        items(licenseProjects, key = { it.title }) { project ->
            Column(Modifier.padding(16.dp)) {
                Text(project.title, style = MaterialTheme.typography.titleMedium)
                Text(project.credit)
                Text(project.license, style = MaterialTheme.typography.labelLarge)
                SelectionContainer { Text(project.source, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { expanded = if (expanded == project.title) null else project.title }) {
                    Text(if (expanded == project.title) "Hide license" else "Read ${project.license}")
                }
                if (expanded == project.title) {
                    var text by remember(project.file) { mutableStateOf("Loading license…") }
                    LaunchedEffect(project.file) {
                        text =
                            try {
                                withContext(
                                    Dispatchers.IO,
                                ) {
                                    context.assets
                                        .open(project.file)
                                        .bufferedReader()
                                        .use { it.readText() }
                                }
                            } catch (_: IOException) {
                                "License unavailable. See ${project.source}"
                            }
                    }
                    SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                }
            }
            HorizontalDivider()
        }
    }
}
