package dev.co508.drums.audio

import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.SpeedTrainer
import dev.co508.drums.playback.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RecoveringAudioOutputTest {
    private class FakeTrack(
        val script: List<Int> = emptyList(),
        val afterWrite: (Int) -> Unit = {},
    ) : PcmOutputTrack {
        override val capacityFrames = 100
        override var bufferSizeFrames = 20
        override val lowLatencyGranted = true
        override val outputDevice = "fake speaker"
        override val underrunCount = 0
        var writes = 0
        var releases = 0
        var paused = false
        var played = false
        val offsets = mutableListOf<Int>()

        override fun resizeBuffer(frames: Int) {
            bufferSizeFrames = frames
        }

        override fun write(
            samples: FloatArray,
            offset: Int,
            count: Int,
            blocking: Boolean,
        ): Int {
            check(releases == 0)
            offsets += offset
            val result = script.getOrNull(writes++) ?: count
            afterWrite(writes)
            return result
        }

        override fun play() {
            check(releases == 0)
            played = true
        }

        override fun timestamp(out: OutputTimestamp): Boolean {
            check(releases == 0)
            out.framePosition = 5
            out.nanoTime = 100
            return played
        }

        override fun pauseAndFlush() {
            paused = true
        }

        override fun release() {
            releases++
        }
    }

    private lateinit var output: RecoveringAudioOutput
    private var generation = 0L
    private val delays = mutableListOf<Long>()

    private fun runner(
        factory: () -> PcmOutputTrack,
        source: EventSource? = null,
    ) = RecoveringAudioOutput(
        100,
        10,
        8,
        factory,
        { source },
        { generation++ },
        delays::add,
    ).also { output = it }

    @Test fun deadObjectAndOtherWriteErrorsRecreateReleaseAndResume() {
        for (error in listOf(-6, -3, -2)) {
            val broken = FakeTrack(listOf(20, 20, error))
            val healthy = FakeTrack(afterWrite = { if (it == 5) output.requestStop() })
            var created = 0
            runner({ if (created++ == 0) broken else healthy }).run()
            assertEquals(2, created)
            assertEquals(1, broken.releases)
            assertEquals(1, healthy.releases)
            assertTrue(healthy.played)
            assertEquals(1, output.stats().recoveries)
            assertEquals(error, output.stats().lastWriteError)
            assertEquals(0, output.stats().writeError)
            assertFalse(output.timestamp(OutputTimestamp()))
        }
    }

    @Test fun prefillErrorAndTrackCreationOrPlayFailureAreRecoverable() {
        val broken = FakeTrack(listOf(-6))
        val playFailure = FakeTrack()
        val cannotPlay =
            object : PcmOutputTrack by playFailure {
                override fun play(): Unit = throw IllegalStateException("track died before play")
            }
        val healthy = FakeTrack(afterWrite = { if (it == 4) output.requestStop() })
        var created = 0
        runner({
            when (created++) {
                0 -> throw IllegalStateException("audio server unavailable")
                1 -> broken
                2 -> cannotPlay
                else -> healthy
            }
        }).run()
        assertFalse(broken.played)
        assertEquals(4, created)
        assertEquals(3, output.stats().recoveryAttempts)
        assertEquals(1, playFailure.releases)
        assertEquals(0, output.stats().writeError)
    }

    @Test fun repeatedFailuresAreBoundedAndTerminalErrorRemainsVisible() {
        val tracks = mutableListOf<FakeTrack>()
        runner({ FakeTrack(listOf(-6)).also(tracks::add) }).run()
        assertEquals(4, tracks.size)
        assertTrue(tracks.all { it.releases == 1 })
        assertEquals(3, output.stats().recoveryAttempts)
        assertEquals(-6, output.stats().writeError)
        assertFalse(output.stats().recovering)
        assertEquals(listOf(100L, 200L, 300L), delays)
    }

    @Test fun oneSecondOfHealthyWritesResetsTheRetryBudget() {
        var created = 0
        runner({
            when (created++) {
                0, 1, 2 -> FakeTrack(listOf(-6))
                // Two prefill blocks, then one second (ten blocks) of successful writes.
                3 -> FakeTrack(List(12) { 20 } + -6)
                else -> FakeTrack(afterWrite = { if (it == 4) output.requestStop() })
            }
        }).run()
        assertEquals(5, created)
        assertEquals(listOf(100L, 200L, 300L, 100L), delays)
        assertEquals(0, output.stats().writeError)
        assertEquals(2, output.stats().recoveries)
    }

    @Test fun stopDuringRetryNeverRecreatesOrResumes() {
        var created = 0
        output =
            RecoveringAudioOutput(
                100,
                10,
                8,
                {
                    created++
                    FakeTrack(listOf(-6))
                },
                { null },
                { generation++ },
                delayMillis = { output.requestStop() },
            )
        output.run()
        assertEquals(1, created)
        assertEquals(0, output.stats().recoveries)
        assertEquals(0, output.stats().writeError)
    }

    @Test fun shortAndZeroWritesKeepTheRemainderOfTheSameBlock() {
        val track = FakeTrack(listOf(6, 0, 4, 10), afterWrite = { if (it == 6) output.requestStop() })
        val frames = mutableListOf<Long>()
        runner({ track }, EventSource { from, _, _ -> frames += from }).run()
        assertEquals(listOf(0, 6, 6, 10, 0, 0), track.offsets)
        assertEquals(listOf(0L, 10L, 20L), frames)
        assertEquals(0, output.stats().recoveries)
    }

    @Test fun stalledZeroWritesRecoverRatherThanSpinningForever() {
        var created = 0
        val stalled = FakeTrack(List(251) { 0 })
        val healthy = FakeTrack(afterWrite = { if (it == 4) output.requestStop() })
        runner({ if (created++ == 0) stalled else healthy }).run()
        assertEquals(1, output.stats().recoveries)
        assertEquals(-3, output.stats().lastWriteError)
        assertEquals(0, output.stats().writeError)
    }

    @Test fun routeChangeReanchorsTransportWithoutRestartingCountInOrTrainer() {
        val sound = Sound("test", floatArrayOf(1f))
        val transport = Transport(100, sound, sound)
        val settings =
            PlaySettings(
                Pattern(1.0, 1.0, 4, emptyList(), emptyList()),
                120.0,
                countIn = false,
                trainer = SpeedTrainer(20.0, 1, 200.0),
            )
        transport.start(settings)
        val positions = mutableListOf<Double>()
        val tempos = mutableListOf<Double>()
        val source =
            EventSource { from, to, sink ->
                transport.events(from, to, sink)
                positions += transport.positionAt(from.toDouble())
                tempos += transport.beatsPerMinuteAt(from.toDouble())
            }
        val old = FakeTrack(afterWrite = { if (it == 8) output.requestRestart("output route changed") })
        val next = FakeTrack(afterWrite = { if (it == 4) output.requestStop() })
        var created = 0
        runner({ if (created++ == 0) old else next }, source).run()
        assertEquals(2, created)
        assertTrue(positions.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(tempos.last() > 120.0)
        assertEquals(1L, transport.startGeneration)
        assertEquals("output route changed", output.stats().lastRecoveryReason)
    }

    @Test fun streamGenerationInvalidatesClockEvenBeforeRefreshIntervalAndWithoutTimestamp() {
        var available = true
        var frame = 100L
        var generation = 1L
        val source =
            object : OutputTimestampSource {
                override val streamGeneration get() = generation

                override fun outputTimestamp(out: OutputTimestamp): Boolean {
                    out.framePosition = frame
                    out.nanoTime = 0
                    return available
                }
            }
        val clock = AudioClock(source, 100, systemNanoTime = { 0 })
        assertEquals(100.0, clock.frameAt(0), 0.0)
        generation++
        available = false
        assertTrue(clock.frameAt(0).isNaN())
        generation++
        available = true
        frame = 0
        assertEquals(0.0, clock.frameAt(0), 0.0)
        assertEquals(0L, clock.stats().updates) // A new epoch is not clock jitter.
    }

    @Test fun shutdownUnblocksWriteAndWorkerReleasesOnlyAfterItReturns() {
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        val writing = AtomicBoolean(false)
        val fake = FakeTrack()
        val blocked =
            object : PcmOutputTrack by fake {
                override fun write(
                    samples: FloatArray,
                    offset: Int,
                    count: Int,
                    blocking: Boolean,
                ): Int {
                    writing.set(true)
                    entered.countDown()
                    check(unblock.await(2, TimeUnit.SECONDS))
                    writing.set(false)
                    return count
                }

                override fun pauseAndFlush() {
                    unblock.countDown()
                }

                override fun release() {
                    check(!writing.get())
                    fake.release()
                }
            }
        runner({ blocked })
        val worker = Thread(output::run)
        worker.start()
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        output.requestStop()
        worker.join(2000)
        assertFalse(worker.isAlive)
        assertEquals(1, fake.releases)
        assertFalse(fake.played)
    }

    @Test fun generationChangeDuringTimestampReadRejectsTheOldEpoch() {
        var generation = 0L
        val clock =
            AudioClock(
                object : OutputTimestampSource {
                    override val streamGeneration get() = generation

                    override fun outputTimestamp(out: OutputTimestamp): Boolean {
                        out.framePosition = 999
                        generation++
                        return true
                    }
                },
                100,
                systemNanoTime = { 0 },
            )
        assertTrue(clock.frameAt(0).isNaN())
    }

    @Test fun finishedTransportStaysFinishedAcrossNewOutputEpoch() {
        val sound = Sound("test", floatArrayOf(1f))
        val transport = Transport(100, sound, sound)
        transport.start(
            PlaySettings(Pattern(1.0, 1.0, 4, emptyList(), emptyList()), 120.0, countIn = false, loop = false),
        )
        val sink = EventSink { _, _, _, _ -> }
        transport.events(0, 100, sink)
        assertEquals(Transport.State.FINISHED, transport.state)
        transport.events(0, 10, sink)
        assertEquals(Transport.State.FINISHED, transport.state)
        assertEquals(1.0, transport.positionAt(0.0), 0.0)
        assertEquals(0.0, transport.beatsPerMinuteAt(0.0), 0.0)
    }

    @Test fun pendingStopDuringReplacementDoesNotWriteIntoThePreviousTempoEpoch() {
        val sound = Sound("test", floatArrayOf(1f))
        val transport = Transport(100, sound, sound)
        transport.start(PlaySettings(Pattern(1.0, 1.0, 4, emptyList(), emptyList()), 120.0, countIn = false))
        val sink = EventSink { _, _, _, _ -> }
        transport.events(0, 80, sink)
        transport.setTempo(180.0)
        transport.events(80, 100, sink)
        transport.stop()
        transport.events(0, 10, sink)
        assertEquals(Transport.State.STOPPED, transport.state)
        assertEquals(0.0, transport.beatsPerMinuteAt(0.0), 0.0)
    }
}
