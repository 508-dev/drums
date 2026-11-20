package dev.co508.drums.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.co508.drums.history.HistoryData
import dev.co508.drums.history.PracticeHistory
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.Pack
import dev.co508.drums.settings.AppDefaults
import dev.co508.drums.settings.PracticePreferences
import dev.co508.drums.settings.PracticeSettingsStore
import dev.co508.drums.settings.preferenceKey
import dev.co508.drums.ui.history.HistoryContent
import dev.co508.drums.ui.history.HistoryScreen
import dev.co508.drums.ui.settings.AboutContent
import dev.co508.drums.ui.settings.LicensesContent
import dev.co508.drums.ui.settings.SettingsContent
import java.io.IOException

@Composable
fun LibraryScreen(
    packs: List<Pack>,
    store: PracticeSettingsStore,
    history: PracticeHistory,
    calibration: @Composable () -> Unit = {},
    practice: @Composable (Lesson, () -> Unit) -> Unit,
) {
    var preferences by remember { mutableStateOf<PracticePreferences?>(null) }
    var readError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val writeError by store.writeError.collectAsState()
    val context = LocalContext.current
    val version =
        remember(context) {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: "unknown"
        }
    LaunchedEffect(store, retry) {
        readError = null
        try {
            store.data.collect { preferences = it }
        } catch (_: IOException) {
            readError = "Couldn't load saved favorites and tempos."
        }
    }
    LibraryNavigation(
        packs = packs,
        preferences = preferences ?: PracticePreferences(),
        preferencesReady = preferences != null && readError == null,
        onFavorite = store::toggleFavorite,
        onDefaults = store::saveDefaults,
        appVersion = version,
        history = { HistoryScreen(history, packs) },
        calibration = calibration,
        message = readError ?: writeError ?: if (preferences == null) "Loading saved preferences…" else null,
        onRetry = if (readError != null) ({ retry++ }) else null,
        practice = practice,
    )
}

/** ID-only navigation, with separate saved list positions at every library level. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryNavigation(
    packs: List<Pack>,
    preferences: PracticePreferences,
    onFavorite: (Lesson) -> Unit,
    preferencesReady: Boolean = true,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    onDefaults: (AppDefaults) -> Unit = {},
    appVersion: String = "unknown",
    history: @Composable () -> Unit = { HistoryContent(HistoryData(), packs) },
    calibration: @Composable () -> Unit = {},
    practice: @Composable (Lesson, () -> Unit) -> Unit,
) {
    var savedRoute by rememberSaveable(stateSaver = LibraryRoute.Saver) { mutableStateOf(LibraryRoute()) }
    val route = savedRoute.validated(packs)
    val pack = packs.firstOrNull { it.manifest.id == route.packId }
    val lesson = pack?.lessons?.firstOrNull { it.id == route.lessonId }
    val holder = rememberSaveableStateHolder()

    fun back() {
        savedRoute = route.back()
    }
    BackHandler(route.canGoBack) { back() }
    if (lesson != null && route.destination == null) {
        practice(lesson, ::back)
        return
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                val title =
                    when (route.destination) {
                        LibraryDestination.HISTORY -> "History"
                        LibraryDestination.SETTINGS -> "Settings"
                        LibraryDestination.ABOUT -> "About"
                        LibraryDestination.LICENSES -> "Licenses and credits"
                        LibraryDestination.CALIBRATION -> "Hit timing calibration"
                        null -> route.family?.title ?: pack?.manifest?.title ?: "Library"
                    }
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigationIcon = {
                if (route.canGoBack) {
                    IconButton(
                        onClick = ::back,
                    ) { Icon(LibraryIcons.Back, contentDescription = "Back") }
                }
            },
            actions = {
                if (route.destination == null) {
                    IconButton(onClick = { savedRoute = route.copy(destination = LibraryDestination.HISTORY) }) {
                        Icon(LibraryIcons.History, contentDescription = "History")
                    }
                    IconButton(onClick = { savedRoute = route.copy(destination = LibraryDestination.SETTINGS) }) {
                        Icon(LibraryIcons.Settings, contentDescription = "Settings")
                    }
                }
            },
            // MainActivity's outer Scaffold owns system-bar insets, including in practice.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        if (route.destination != null) {
            message?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
            onRetry?.let { TextButton(onClick = it) { Text("Retry") } }
            when (route.destination) {
                LibraryDestination.SETTINGS ->
                    SettingsContent(
                        preferences.defaults,
                        preferencesReady,
                        onDefaults,
                        onAbout = { savedRoute = route.copy(destination = LibraryDestination.ABOUT) },
                        onCalibration = { savedRoute = route.copy(destination = LibraryDestination.CALIBRATION) },
                    )
                LibraryDestination.CALIBRATION -> calibration()
                LibraryDestination.ABOUT ->
                    AboutContent(appVersion) {
                        savedRoute = route.copy(destination = LibraryDestination.LICENSES)
                    }
                LibraryDestination.LICENSES -> LicensesContent()
                LibraryDestination.HISTORY -> holder.SaveableStateProvider("history") { history() }
            }
            return@Column
        }
        message?.let {
            Row(Modifier.padding(horizontal = 16.dp)) {
                Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                onRetry?.let { retry -> TextButton(onClick = retry) { Text("Retry") } }
            }
        }
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(route.listKey) {
                LazyColumn(Modifier.fillMaxSize()) {
                    when {
                        pack == null -> {
                            if (packs.isEmpty()) item { Text("No lesson packs available.", Modifier.padding(24.dp)) }
                            items(packs, key = { it.manifest.id }) { item ->
                                NavigationRow(
                                    item.manifest.title,
                                    "${item.lessons.size} lessons · ${item.manifest.author}",
                                ) {
                                    savedRoute = LibraryRoute(packId = item.manifest.id)
                                }
                            }
                        }
                        route.family == null ->
                            items(pack.families(), key = { it.name }) { family ->
                                val count = pack.lessons.count { it.manifest.family == family }
                                NavigationRow(family.title, "$count ${if (count == 1) "lesson" else "lessons"}") {
                                    savedRoute = route.copy(family = family)
                                }
                            }
                        else ->
                            items(pack.lessons.filter { it.manifest.family == route.family }, key = { it.id }) { item ->
                                LessonRow(item, preferences, preferencesReady, onFavorite) {
                                    savedRoute = route.copy(lessonId = item.id)
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(LibraryIcons.Next, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
    )
    HorizontalDivider()
}

@Composable
private fun LessonRow(
    lesson: Lesson,
    preferences: PracticePreferences,
    preferencesReady: Boolean,
    onFavorite: (Lesson) -> Unit,
    onOpen: () -> Unit,
) {
    val favorite = preferences.isFavorite(lesson)
    val lastTempo = preferences.lessons[lesson.preferenceKey]?.normalized()?.tempo
    ListItem(
        headlineContent = { Text(lesson.manifest.title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            val last = if (!preferencesReady) "…" else lastTempo?.let { "$it BPM" } ?: "—"
            Text("Level ${lesson.manifest.level} · Last tempo: $last")
        },
        trailingContent = {
            IconToggleButton(checked = favorite, onCheckedChange = { onFavorite(lesson) }, enabled = preferencesReady) {
                Icon(
                    if (favorite) LibraryIcons.Star else LibraryIcons.StarOutline,
                    contentDescription = "Favorite ${lesson.manifest.title}",
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
    )
    HorizontalDivider()
}
