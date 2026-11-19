package dev.co508.drums.settings

import dev.co508.drums.audio.OutputTimestampSource
import dev.co508.drums.lesson.Family
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.LessonManifest
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.TempoRange
import dev.co508.drums.playback.AudioClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AppDefaultsTest {
    private val lesson =
        Lesson(
            "pack",
            LessonManifest(
                "new",
                "new.musicxml",
                "New",
                Family.TIMING,
                1,
                TempoRange(30, 200, 60),
                "Test",
            ),
            Notation(null, null, emptyList()),
        )

    @Test fun defaultsSeedOnlyUnsavedLessons() {
        val defaults =
            AppDefaults(click = false, clickVolume = 0.4f, snare = false, snareVolume = 0.6f, countIn = false)
        val preferences = PracticePreferences(defaults = defaults)
        val seeded = preferences.lesson(lesson)
        assertFalse(seeded.click)
        assertFalse(seeded.snare)
        assertFalse(seeded.countIn)
        assertEquals(0.4f, seeded.clickVolume)
        assertEquals(0.6f, seeded.snareVolume)
        assertEquals(60, seeded.tempo)
        val saved = LessonPreferences(tempo = 97, clickVolume = 0.8f)
        assertEquals(saved, preferences.copy(lessons = mapOf(lesson.preferenceKey to saved)).lesson(lesson))
    }

    @Test fun offsetChangesOnlyVisualClockAndHandlesBothSigns() {
        val source =
            OutputTimestampSource {
                it.framePosition = 48_000
                it.nanoTime = 1_000_000_000
                true
            }
        val clock = AudioClock(source, 48_000, systemNanoTime = { 1_000_000_000 })
        AppDefaults(visualOffsetMillis = 100).applyTo(clock)
        assertEquals(43_200.0, clock.visualFrameAt(1_000_000_000), 0.001)
        assertEquals(48_000.0, clock.frameAt(1_000_000_000), 0.001)
        AppDefaults(visualOffsetMillis = -100).applyTo(clock)
        assertEquals(52_800.0, clock.visualFrameAt(1_000_000_000), 0.001)
        AppDefaults().applyTo(clock)
        assertEquals(48_000.0, clock.visualFrameAt(1_000_000_000), 0.001)
    }

    @Test fun defaultsRoundTripAndOldSettingsKeepOriginalDefaults() =
        runBlocking {
            val defaults = AppDefaults(false, 0.4f, false, 0.6f, false, -125)
            val out = ByteArrayOutputStream()
            PracticePreferencesSerializer.writeTo(PracticePreferences(defaults = defaults), out)
            assertEquals(
                defaults,
                PracticePreferencesSerializer.readFrom(ByteArrayInputStream(out.toByteArray())).defaults,
            )
            assertEquals(
                AppDefaults(),
                PracticePreferencesSerializer.readFrom(ByteArrayInputStream("{}".toByteArray())).defaults,
            )
            assertEquals(
                AppDefaults(clickVolume = 1f, snareVolume = 0f, visualOffsetMillis = 500),
                AppDefaults(clickVolume = Float.NaN, snareVolume = -1f, visualOffsetMillis = 999).normalized(),
            )
        }
}
