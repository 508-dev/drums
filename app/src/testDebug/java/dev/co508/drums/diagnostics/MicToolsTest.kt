package dev.co508.drums.diagnostics

import dev.co508.drums.audio.Sound
import dev.co508.drums.hits.InputClockStats
import dev.co508.drums.hits.MicStats
import dev.co508.drums.hits.WavFile
import dev.co508.drums.playback.Click
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PatternEvent
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.TempoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class MicToolsTest {
    private val sound = Sound("test", FloatArray(8))

    // One bar of 2/4 in quarter notes, a flam on beat 2, clicks on both beats.
    private val pattern =
        Pattern(
            lengthQuarters = 2.0,
            beatQuarters = 1.0,
            countInBeats = 2,
            events =
                listOf(
                    PatternEvent(0.0, sound),
                    PatternEvent(1.0, sound, leadLevel = 1),
                    PatternEvent(1.0, sound),
                ),
            clicks = listOf(Click(0.0, true), Click(1.0, false)),
        )

    // 120 BPM from frame 0, with the count-in (positions −2..0): 1 quarter = 24 000 frames.
    private val tempoMap = TempoMap().apply { begin(0.0, -2.0, 1.0 / 24_000) }
    private val settings = PlaySettings(pattern, 120.0)
    private val grace = 576.0 // 12 ms

    private fun nearest(
        frame: Double,
        clicks: Boolean = true,
    ) = nearestScheduledFrame(settings, tempoMap, frame, grace, clicks)

    @Test
    fun `finds the nearest stroke, grace strokes early`() {
        // Pattern position 0 is frame 48 000; position 1 is 72 000, its grace 576 frames earlier.
        assertEquals(48_000.0, nearest(48_100.0), 1e-6)
        assertEquals(72_000.0 - grace, nearest(71_300.0), 1e-6)
        assertEquals(72_000.0, nearest(71_900.0), 1e-6)
        // The next loop's downbeat.
        assertEquals(96_000.0, nearest(95_000.0), 1e-6)
    }

    @Test
    fun `count-in clicks count only with clicks included`() {
        assertEquals(24_000.0, nearest(24_500.0), 1e-6)
        assertEquals(48_000.0, nearest(24_500.0, clicks = false), 1e-6)
    }

    @Test
    fun `nothing is scheduled before the map begins`() {
        assertTrue(nearestScheduledFrame(settings, TempoMap(), 0.0, grace, true).isNaN())
    }

    @Test
    fun `offset statistics`() {
        val stats = OffsetStats()
        listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0).forEach(stats::add)
        assertEquals(8, stats.count)
        assertEquals(5.0, stats.mean, 1e-12)
        assertEquals(2.138, stats.standardDeviation, 1e-3)
        stats.reset()
        assertEquals(0, stats.count)
    }

    @Test
    fun `records blocks and writes a WAV the fixture reader reads back`() {
        val recorder = WavRecorder(sampleRate = 1_000, maxSeconds = 1)
        val block = FloatArray(300) { (it - 150) / 150f }
        recorder.onBlock(block, block.size, 0) // Not recording yet: ignored.
        recorder.begin()
        recorder.onBlock(block, block.size, 300)
        recorder.onBlock(block, block.size, 600)
        recorder.onBlock(block, block.size, 900)
        recorder.onBlock(block, block.size, 1_200) // Fills the buffer at 1 000 frames.
        assertFalse(recorder.isRecording)
        val take = recorder.end()
        assertEquals(1_000, take.frames)
        assertEquals(300L, take.firstFrame)

        val file = File(createTempDirectory().toFile(), "take.wav")
        writeWav16(file, take.samples, take.frames, take.sampleRate)
        assertEquals(44L + 2_000, file.length())
        val wav = WavFile.read(file)
        assertEquals(1_000, wav.sampleRate)
        assertEquals(1_000, wav.samples.size)
        for (i in 0 until 1_000) assertEquals(block[i % 300], wav.samples[i], 1e-4f)
    }

    @Test
    fun `the report says how the mic is set up`() {
        assertEquals("Mic       off", micReport(MicStats()))
        val report =
            micReport(
                MicStats(
                    running = true,
                    source = "UNPROCESSED",
                    device = "built-in mic (AI2202)",
                    sampleRate = 48_000,
                    floatSamples = true,
                    bufferFrames = 4_800,
                    blockFrames = 192,
                    framesCaptured = 96_000,
                    onsets = 12,
                    noiseFloorDb = -71.25f,
                    levelDb = -40f,
                    clock = InputClockStats(anchored = true, exactTimestamps = true, updates = 20),
                ),
            )
        assertTrue(report, report.contains("listening: UNPROCESSED, 48000 Hz float"))
        assertTrue(report, report.contains("floor -71.3 dB"))
        assertTrue(report, report.contains("stream timestamps, 20 updates"))
    }
}
