package dev.co508.drums.scoring

import android.Manifest
import androidx.annotation.RequiresPermission
import dev.co508.drums.hits.DetectedHit
import dev.co508.drums.hits.HitTimeline
import dev.co508.drums.hits.MicCapture
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.TempoMap
import dev.co508.drums.playback.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One scored run: listens with the [mic], places each hit on the shared clock
 * ([timeline], calibrated), matches it with a [HitMatcher] and publishes an
 * immutable [snapshot] for the views. The matching runs on a background
 * coroutine, never on the audio or capture threads; the UI only reads
 * [snapshot]. See DECISIONS.md, "Matching Hits To Notes".
 *
 * [start] at Play, [stop] at Stop: the session keeps listening a moment
 * longer, so the last notes' hits still arrive, then closes the microphone.
 */
class ScoringSession(
    private val mic: MicCapture,
    private val timeline: HitTimeline,
    private val clock: AudioClock,
    private val transport: Transport,
    private val scope: CoroutineScope,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    @Volatile
    var snapshot: ScoringSnapshot = ScoringSnapshot.EMPTY
        private set

    /** True from [start] until the microphone has closed after [stop]. */
    @Volatile
    var isListening = false
        private set

    private var job: Job? = null

    @Volatile private var stopRequestedAt = 0L

    private val map =
        object : TimeMap {
            val tempoMap: TempoMap get() = transport.tempoMap

            override fun frameAt(position: Double) = tempoMap.frameAt(position)

            override fun positionAt(frame: Double) = tempoMap.positionAt(frame)
        }

    /**
     * Starts listening for a run of [lesson]. Returns false if the microphone
     * couldn't open; playback goes on unscored.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(
        lesson: ScoredLesson,
        strictness: Strictness,
        loops: Boolean,
        latencyMillis: Double,
    ): Boolean {
        stopNow()
        timeline.latencyNanos = (latencyMillis * NANOS_PER_MILLI).toLong()
        if (!mic.start()) return false
        val hit = DetectedHit()
        while (mic.poll(hit)) Unit
        val matcher = HitMatcher(lesson, transport.sampleRate, strictness, loops)
        snapshot = ScoringSnapshot.EMPTY
        stopRequestedAt = 0L
        isListening = true
        job =
            scope.launch(Dispatchers.Default) {
                try {
                    while (isActive) {
                        while (mic.poll(hit)) matcher.addHit(timeline.outputFrame(hit.nanoTime))
                        val gap = transport.graceGapMillis * transport.sampleRate / 1000.0
                        val stopAt = stopRequestedAt
                        if (stopAt != 0L && nanoTime() - stopAt >= settleNanos()) {
                            val stopFrame = clock.frameAt(stopAt - timeline.latencyNanos)
                            matcher.finish(stopFrame, settledFrame(), map, gap)
                            snapshot = matcher.snapshot()
                            break
                        }
                        matcher.advance(settledFrame(), map, gap)
                        snapshot = matcher.snapshot()
                        delay(TICK_MILLIS)
                    }
                } finally {
                    mic.stop()
                    isListening = false
                }
            }
        return true
    }

    /** The run stopped: finish once the last hits have had time to arrive. */
    fun stop() {
        if (job?.isActive == true && stopRequestedAt == 0L) stopRequestedAt = nanoTime()
    }

    /** Stops at once, without finishing (leaving the screen). */
    fun stopNow() {
        job?.cancel()
        job = null
        if (mic.isRunning) mic.stop()
        isListening = false
    }

    /** Hits for frames up to here have all arrived: detection and the route's delay are behind us. */
    private fun settledFrame(): Double = clock.frameAt(nanoTime() - settleNanos())

    private fun settleNanos(): Long = timeline.latencyNanos.coerceAtLeast(0L) + SETTLE_NANOS

    private companion object {
        const val TICK_MILLIS = 15L
        const val SETTLE_NANOS = 80_000_000L
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
