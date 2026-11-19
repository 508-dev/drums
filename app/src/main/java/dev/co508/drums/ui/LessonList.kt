package dev.co508.drums.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.co508.drums.lesson.Lesson

/** The minimal M2 lesson list: the built-in lessons, in order. The real library is #15. */
@Composable
fun LessonList(
    lessons: List<Lesson>,
    onOpen: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        items(lessons, key = { it.id }) { lesson ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(lesson) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(lesson.manifest.title, style = MaterialTheme.typography.titleMedium)
                val tempo = lesson.manifest.tempo
                Text(
                    "${lesson.manifest.family.name.lowercase().replaceFirstChar { it.uppercase() }} · " +
                        "level ${lesson.manifest.level} · ${tempo.min}–${tempo.max} BPM",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }
    }
}
