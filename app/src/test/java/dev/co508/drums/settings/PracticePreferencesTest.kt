package dev.co508.drums.settings

import androidx.datastore.core.CorruptionException
import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.Sound
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.Family
import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.GraceNote
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.LessonManifest
import dev.co508.drums.lesson.Measure
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.Note
import dev.co508.drums.lesson.NoteValue
import dev.co508.drums.lesson.TempoRange
import dev.co508.drums.lesson.TimeSignature
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.Transport
import dev.co508.drums.ui.practice.PracticeView
import dev.co508.drums.ui.practice.forDisplay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PracticePreferencesTest {
    @Test
    fun rememberedTempoIsTheLastHeardStepButManualTempoDoesNotDrift() {
        val sound = Sound("test", floatArrayOf(0f))
        val transport = Transport(48_000, sound, sound)
        val pattern = Pattern(4.0, 1.0, 4, emptyList(), emptyList())
        val sink =
            object : EventSink {
                override fun play(
                    frame: Long,
                    sound: Sound,
                    gain: Float,
                    pan: Float,
                ) = Unit
            }
        var manual = LessonPreferences(tempo = 85, countIn = false)
        transport.start(manual.playSettings(pattern))
        for (frame in 0L until 48_000L * 60 step 240) {
            transport.events(frame, frame + 240, sink)
            manual = manual.rememberHeardTempo(transport.beatsPerMinuteAt(frame.toDouble()))
            assertEquals(85, manual.tempo)
        }
        var trainer =
            manual.copy(
                trainer = true,
                trainerStart = 80,
                trainerStep = 5,
                trainerLoops = 1,
                trainerTarget = 95,
            )
        transport.start(trainer.playSettings(pattern))
        for (frame in 0L until 48_000L * 30 step 240) {
            transport.events(frame, frame + 240, sink)
            trainer = trainer.rememberHeardTempo(transport.beatsPerMinuteAt(frame.toDouble()))
        }
        assertEquals(95, trainer.tempo)
        assertEquals(80, trainer.trainerStart)
        assertEquals(80.0, trainer.playSettings(pattern).beatsPerMinute, 0.0)
        assertEquals(trainer, trainer.rememberHeardTempo(0.0))
        assertEquals(trainer, trainer.rememberHeardTempo(Double.NaN))
    }

    @Test
    fun defaultsUseLessonMetadataAndKeysIncludePack() {
        val lesson = lesson("built-in")
        val custom = LessonPreferences(tempo = 137, leadWithLeft = true)
        val preferences = PracticePreferences(lessons = mapOf(lesson.preferenceKey to custom))
        assertEquals(custom, preferences.lesson(lesson))
        assertEquals(72, preferences.lesson(lesson("imported")).tempo)
        assertEquals(144, preferences.lesson(lesson("imported")).trainerTarget)
        assertNotEquals(lesson("a").preferenceKey, lesson("aa").preferenceKey)
    }

    @Test
    fun trainerStartsAtConfiguredStartNotLastTempoAndRequiresLoop() {
        val pattern = Pattern(4.0, 1.0, 4, emptyList(), emptyList())
        val settings =
            LessonPreferences(
                tempo = 150,
                countIn = false,
                trainer = true,
                trainerStart = 70,
                trainerStep = 7,
                trainerLoops = 2,
                trainerTarget = 90,
            )
        val play = settings.playSettings(pattern)
        assertEquals(70.0, play.beatsPerMinute, 0.0)
        assertFalse(play.countIn)
        val trainer = requireNotNull(play.trainer)
        assertEquals(70.0, trainer.tempoEntering(1, 70.0), 0.0)
        assertEquals(77.0, trainer.tempoEntering(2, 70.0), 0.0)
        assertEquals(90.0, trainer.tempoEntering(4, 88.0), 0.0)
        assertNull(settings.copy(loop = false).playSettings(pattern).trainer)
    }

    @Test
    fun invalidNumbersAreBounded() {
        val settings =
            LessonPreferences(
                tempo = -1,
                trainerStart = 999,
                trainerStep = 0,
                trainerLoops = -2,
                trainerTarget = 20,
                clickVolume = Float.NaN,
                snareVolume = -1f,
                pixelsPerBeat = Float.POSITIVE_INFINITY,
            ).normalized()
        assertEquals(30, settings.tempo)
        assertEquals(300, settings.trainerTarget)
        assertEquals(1, settings.trainerStep)
        assertEquals(1, settings.trainerLoops)
        assertEquals(1f, settings.clickVolume)
        assertEquals(0f, settings.snareVolume)
        assertEquals(110f, settings.pixelsPerBeat)
    }

    @Test
    fun serializerRoundTripsEveryControlAndIgnoresFutureFields() =
        runBlocking {
            val settings =
                LessonPreferences(
                    tempo = 132,
                    countIn = false,
                    loop = false,
                    click = false,
                    clickVolume = 0.4f,
                    snare = false,
                    snareVolume = 0.7f,
                    leadWithLeft = true,
                    trainer = true,
                    trainerStart = 60,
                    trainerStep = 3,
                    trainerLoops = 7,
                    trainerTarget = 180,
                    pixelsPerBeat = 200f,
                    showCounts = false,
                )
            val original = PracticePreferences(PracticeView.SHEET.name, mapOf("lesson" to settings))
            val output = ByteArrayOutputStream()
            PracticePreferencesSerializer.writeTo(original, output)
            assertEquals(original, PracticePreferencesSerializer.readFrom(ByteArrayInputStream(output.toByteArray())))
            assertEquals(PracticeView.FALLING_NOTES, read("""{"future":42,"view":"FUTURE"}""").practiceView)
            assertEquals(LessonPreferences(), read("""{"lessons":{"old":{}}}""").lessons["old"])
        }

    @Test(expected = CorruptionException::class)
    fun malformedSettingsAreNotSilentlyOverwritten() =
        runBlocking<Unit> {
            read("not json")
            Unit
        }

    @Test
    fun displaySwapsMainAndGraceHandsWithoutMutatingSource() {
        val notation =
            Notation(
                null,
                null,
                listOf(
                    Measure(
                        "1",
                        Fraction.ZERO,
                        Fraction.of(4),
                        TimeSignature.FOUR_FOUR,
                        listOf(
                            Note(
                                Fraction.ZERO,
                                Fraction.ONE,
                                Hand.RIGHT,
                                graceNotes = listOf(GraceNote(Hand.LEFT, true, NoteValue.EIGHTH)),
                            ),
                        ),
                    ),
                ),
            )
        val swapped = notation.forDisplay(true)
        assertEquals(Hand.LEFT, swapped.notes.single().hand)
        assertEquals(
            Hand.RIGHT,
            swapped.notes
                .single()
                .graceNotes
                .single()
                .hand,
        )
        assertEquals(Hand.RIGHT, notation.notes.single().hand)
        assertEquals(Expansion.expand(notation, true), Expansion.expand(swapped))
    }

    private suspend fun read(text: String) =
        PracticePreferencesSerializer.readFrom(ByteArrayInputStream(text.toByteArray()))

    private fun lesson(pack: String) =
        Lesson(
            pack,
            LessonManifest(
                "same",
                "same.musicxml",
                "Test",
                Family.ROLLS,
                1,
                TempoRange(30, 144, 72),
                "Description",
            ),
            Notation(null, null, emptyList()),
        )
}
