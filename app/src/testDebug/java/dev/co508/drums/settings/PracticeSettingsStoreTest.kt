package dev.co508.drums.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.co508.drums.lesson.Family
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.LessonManifest
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.TempoRange
import dev.co508.drums.ui.practice.PracticeView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PracticeSettingsStoreTest {
    @Test
    fun migratesViewAndRetainsOrderedWritesAcrossRecreation() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context
                .getSharedPreferences("practice", Context.MODE_PRIVATE)
                .edit()
                .putString("view", "SHEET")
                .commit()
            val lesson =
                Lesson(
                    "pack",
                    LessonManifest(
                        "test",
                        "test.musicxml",
                        "Test",
                        Family.ROLLS,
                        1,
                        TempoRange(30, 120, 60),
                        "Test",
                    ),
                    Notation(null, null, emptyList()),
                )
            val firstJob = SupervisorJob()
            try {
                val store = PracticeSettingsStore(context, CoroutineScope(firstJob + Dispatchers.IO))
                assertEquals(PracticeView.SHEET, store.data.first().practiceView)
                // Simulate rapid slider edits followed by immediate navigation away.
                for (tempo in 60..160) store.saveLesson(lesson, LessonPreferences(tempo = tempo))
                store.saveView(PracticeView.FALLING_NOTES)
                store.saveZoom(320f)
                store.saveDefaults(
                    AppDefaults(click = false, clickVolume = 0.3f, countIn = false, visualOffsetMillis = 125),
                )
                store.toggleFavorite(lesson)
                // A stale practice snapshot must not overwrite a library favorite.
                store.saveLesson(lesson, LessonPreferences(tempo = 160))
                assertEquals(true, store.snapshot().isFavorite(lesson))
                store.toggleFavorite(lesson)
                assertEquals(false, store.snapshot().isFavorite(lesson))
                store.toggleFavorite(lesson)
                assertEquals(false, store.snapshot().isFavorite(lesson.copy(packId = "another-pack")))
                assertEquals(160, store.snapshot().lesson(lesson).tempo)
                assertEquals(320f, store.snapshot().zoomPixelsPerBeat)
                withTimeout(10_000) {
                    store.data.first { it.lesson(lesson).tempo == 160 && it.practiceView == PracticeView.FALLING_NOTES }
                }
            } finally {
                firstJob.cancelAndJoin()
            }
            assertFalse(context.getSharedPreferences("practice", Context.MODE_PRIVATE).contains("view"))
            val secondJob = SupervisorJob()
            try {
                val reopened = PracticeSettingsStore(context, CoroutineScope(secondJob + Dispatchers.IO)).data.first()
                assertEquals(160, reopened.lesson(lesson).tempo)
                assertEquals(PracticeView.FALLING_NOTES, reopened.practiceView)
                assertEquals(320f, reopened.zoomPixelsPerBeat)
                assertEquals(125, reopened.defaults.visualOffsetMillis)
                assertEquals(false, reopened.defaults.countIn)
                assertEquals(0.3f, reopened.defaults.clickVolume)
                assertEquals(true, reopened.isFavorite(lesson))
            } finally {
                secondJob.cancelAndJoin()
            }
        }
}
