package dev.co508.drums.history

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.co508.drums.audio.AudioEngine
import dev.co508.drums.audio.AudioEngineStats
import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.EventSource
import dev.co508.drums.audio.MixerStats
import dev.co508.drums.audio.OutputTimestamp
import dev.co508.drums.audio.Sound
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.playback.SpeedTrainer
import dev.co508.drums.playback.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaybackHistoryTest {
    private class FakeEngine : AudioEngine {
        override val sampleRate = 48_000
        override var isRunning = false
        var frame = 0L
        var error = 0

        override fun start() {
            isRunning = true
        }

        override fun stop() {
            isRunning = false
        }

        override fun setEventSource(source: EventSource?) = Unit

        override fun outputTimestamp(out: OutputTimestamp): Boolean {
            out.framePosition = frame
            out.nanoTime = System.nanoTime()
            return isRunning
        }

        override fun stats() =
            AudioEngineStats(48_000, 192, 384, 768, true, "fake", 0, frame, MixerStats(0, 0, 0, 0), error)
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = FakeEngine()
    private val sound = Sound("test", floatArrayOf(1f))
    private val transport = Transport(48_000, sound, sound)
    private val clock = AudioClock(engine, 48_000, refreshIntervalNanos = 0)
    private val rows = mutableListOf<PracticeRecord>()
    private var nanos = 0L
    private val session =
        PlaybackSession(context, engine, transport, clock, PracticeRunLog(rows::add, { 123L }, { nanos }))
    private val settings get() =
        PlaySettings(
            Pattern(4.0, 1.0, 4, emptyList(), emptyList()),
            60.0,
        )
    private val sink = EventSink { _, _, _, _ -> }

    @Test fun stopAndRepeatedCloseWriteOnceIncludingHeardTempoNotVisualOrFutureTempo() {
        assertTrue(session.play(settings, PracticeIdentity("pack", "lesson")))
        transport.events(0, 192, sink)
        transport.tempoMap.changeRateAtFrame(48_000.0, 2.0 / 48_000) // 120 BPM, heard.
        transport.tempoMap.changeRateAtFrame(96_000.0, 4.0 / 48_000) // 240 BPM, still buffered.
        engine.frame = 60_000
        clock.visualOffsetMillis = -5000.0 // Must not inflate recorded tempo.
        nanos = 1_250_000_000
        session.stop()
        session.close()
        session.close()
        assertEquals(1, rows.size)
        assertEquals(120, rows.single().highestTempo)
        assertEquals(1250L, rows.single().durationMillis)
    }

    @Test fun rejectedFocusDoesNotCreateHistory() {
        shadowOf(
            context.getSystemService(AudioManager::class.java),
        ).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(session.play(settings, PracticeIdentity("pack", "lesson")))
        session.close()
        assertTrue(rows.isEmpty())
    }

    @Test fun unplugAndErrorAndNaturalFinishCloseRunsOnce() {
        for (reason in listOf("unplug", "error", "finished", "screen")) {
            assertTrue(session.play(settings, PracticeIdentity("pack", reason)))
            transport.events(0, 192, sink)
            when (reason) {
                "unplug" -> {
                    context.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                    shadowOf(android.os.Looper.getMainLooper()).idle()
                }
                "error" -> {
                    engine.error = -6
                    session.checkOutput()
                    engine.error = 0
                }
                "finished" -> session.stop(PlaybackSession.StopReason.TRANSPORT_FINISHED)
                else -> session.close()
            }
            assertFalse(session.isPlaying)
            session.close()
        }
        assertEquals(listOf("unplug", "error", "finished", "screen"), rows.map { it.lessonId })
    }

    @Test fun pendingReplacementCannotAttributeOldTempoToNewLesson() {
        val reused = settings
        assertTrue(session.play(reused, PracticeIdentity("pack", "old")))
        transport.events(0, 192, sink)
        transport.tempoMap.changeRateAtFrame(1.0, 4.0 / 48_000)
        engine.frame = 100
        session.updatePracticeLog()
        assertTrue(session.play(reused, PracticeIdentity("pack", "new")))
        session.updatePracticeLog() // Mixer has not yet accepted the replacement.
        session.stop()
        session.close()
        assertEquals(listOf(240, 60), rows.map { it.highestTempo })
    }

    @Test fun trainerLogsHeardStepNotUnreachedTarget() {
        val trained = PlaySettings(settings.pattern, 60.0, countIn = false, trainer = SpeedTrainer(20.0, 1, 200.0))
        assertTrue(session.play(trained, PracticeIdentity("pack", "trainer")))
        transport.events(0, 192_001, sink) // Render through first loop boundary: 60→80.
        engine.frame = 192_100 // Past the boundary, allowing the fake timestamp's call overhead.
        session.stop()
        session.close()
        assertEquals(60, rows.single().startingTempo)
        assertEquals(80, rows.single().highestTempo)
        assertTrue(rows.single().trainerUsed)
    }

    @Test fun focusLossClosesOnceButNotificationDuckingKeepsRunOpen() {
        val audio = shadowOf(context.getSystemService(AudioManager::class.java))
        for (loss in listOf(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS)) {
            assertTrue(session.play(settings, PracticeIdentity("pack", "focus")))
            val listener = audio.lastAudioFocusRequest.listener
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertTrue(session.isPlaying)
            listener.onAudioFocusChange(loss)
            assertFalse(session.isPlaying)
            listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertFalse(session.isPlaying)
            session.close()
        }
        assertEquals(2, rows.size)
    }
}
