package dev.co508.drums.hits

import android.Manifest
import androidx.annotation.RequiresPermission
import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.audio.Sound
import dev.co508.drums.playback.Click
import dev.co508.drums.playback.Pattern
import dev.co508.drums.playback.PatternEvent
import dev.co508.drums.playback.PlaySettings
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.playback.Transport
import kotlin.math.exp
import kotlin.random.Random

/**
 * Runs one calibration (DECISIONS.md, "Calibration Per Output Route"): plays
 * clicks, listens with the microphone, and turns what it heard into a delay
 * with [CalibrationMath].
 *
 * - [CalibrationMethod.LOOPBACK]: 16 sharp broadband clicks through the phone
 *   speaker after a silent bar; the phone hears itself.
 * - [CalibrationMethod.TAP_ALONG]: a bar of count-in, then 16 metronome
 *   clicks the player taps along to on the pad.
 *
 * Main thread only: [start], then [tick] every frame or so until the state is
 * no longer [State.Listening]. The caller saves a measured result for the
 * [State.Finished.route] it ran on.
 */
class Calibrator(
    private val session: PlaybackSession,
    private val mic: MicCapture,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    sealed interface State {
        data object Idle : State

        data class Listening(
            val method: CalibrationMethod,
            val heard: Int,
            val expected: Int,
        ) : State

        data class Finished(
            val method: CalibrationMethod,
            val route: OutputRoute?,
            val outcome: CalibrationOutcome,
        ) : State

        /** Didn't run to the end: the reason, for the player. */
        data class Stopped(
            val message: String,
        ) : State
    }

    var state: State = State.Idle
        private set

    private val click = calibrationClick(session.engine.sampleRate)
    private val hit = DetectedHit()
    private val hitFrames = ArrayList<Double>()
    private var method = CalibrationMethod.LOOPBACK
    private var route: OutputRoute? = null
    private var settings: PlaySettings? = null
    private var finishedAt = 0L
    private var savedClickVolume = 1f
    private var savedInstrumentVolume = 1f

    /**
     * Starts [method] on the current output. Returns false (with the reason in
     * [state]) if it can't: a speaker check off the speaker, no microphone, or
     * audio focus refused.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(method: CalibrationMethod): Boolean {
        cancel()
        session.open()
        val route = session.engine.stats().outputRoute
        if (method == CalibrationMethod.LOOPBACK && route != null && route.kind != OutputRoute.Kind.SPEAKER) {
            state =
                State.Stopped(
                    "The speaker check only works through the phone's speaker. Unplug headphones, or tap along.",
                )
            return false
        }
        if (!mic.start()) {
            state = State.Stopped("Couldn't open the microphone.")
            return false
        }
        while (mic.poll(hit)) Unit // Nothing from before this run.
        hitFrames.clear()
        val transport = session.transport
        savedClickVolume = transport.clickVolume
        savedInstrumentVolume = transport.instrumentVolume
        val play =
            when (method) {
                CalibrationMethod.LOOPBACK -> {
                    // A silent count-in lets the detector learn the room before the first click.
                    transport.clickVolume = 0f
                    transport.instrumentVolume = 1f
                    PlaySettings(loopbackPattern(click), LOOPBACK_BPM, countIn = true, loop = false)
                }
                CalibrationMethod.TAP_ALONG -> {
                    transport.clickVolume = 1f
                    transport.instrumentVolume = 0f
                    PlaySettings(tapPattern(), TAP_BPM, countIn = true, loop = false)
                }
            }
        if (!session.play(play)) {
            mic.stop()
            restoreVolumes()
            state = State.Stopped("Another app is using audio, or a call is in progress.")
            return false
        }
        this.method = method
        this.route = route
        settings = play
        finishedAt = 0L
        state = State.Listening(method, 0, BEATS)
        return true
    }

    /** Takes in what the mic heard and finishes the run once its last click has had time to arrive. */
    fun tick() {
        val listening = state as? State.Listening ?: return
        while (mic.poll(hit)) {
            val frame = session.clock.frameAt(hit.nanoTime)
            if (frame.isFinite()) hitFrames += frame
        }
        val transport = session.transport
        val first = transport.tempoMap.frameAt(0.0)
        val heard = if (first.isFinite()) hitFrames.count { it >= first - FRAMES_BEFORE_FIRST } else 0
        if (heard != listening.heard) state = listening.copy(heard = heard)
        if (transport.settings === settings && transport.state == Transport.State.FINISHED) {
            val now = nanoTime()
            if (finishedAt == 0L) finishedAt = now
            // The output buffer, the route's delay and a lazy tap still have to arrive.
            if (now - finishedAt >= TAIL_NANOS) finish()
        }
    }

    /** The session was interrupted (focus lost, headphones unplugged…). */
    fun onInterrupted(reason: PlaybackSession.StopReason) {
        if (state !is State.Listening) return
        cleanUp()
        state =
            State.Stopped(
                when (reason) {
                    PlaybackSession.StopReason.HEADPHONES_UNPLUGGED -> "Stopped: the output changed. Try again."
                    PlaybackSession.StopReason.ENGINE_ERROR -> "Stopped: the audio output failed. Try again."
                    else -> "Stopped: a call or another app took the audio."
                },
            )
    }

    fun cancel() {
        if (state is State.Listening) {
            session.stop()
            cleanUp()
        }
        state = State.Idle
    }

    private fun finish() {
        val playing = settings ?: return
        val transport = session.transport
        val pattern = playing.pattern
        val positions =
            when (method) {
                CalibrationMethod.LOOPBACK -> pattern.events.map { it.position }
                CalibrationMethod.TAP_ALONG -> pattern.clicks.map { it.position }
            }
        val clicks = DoubleArray(positions.size) { transport.tempoMap.frameAt(positions[it]) }
        val rate = session.engine.sampleRate
        val hits = hitFrames.toDoubleArray()
        val outcome =
            when (method) {
                CalibrationMethod.LOOPBACK -> CalibrationMath.loopback(clicks, hits, rate)
                CalibrationMethod.TAP_ALONG -> CalibrationMath.tapAlong(clicks, hits, rate)
            }
        session.stop(PlaybackSession.StopReason.TRANSPORT_FINISHED)
        cleanUp()
        state = State.Finished(method, route, outcome)
    }

    private fun cleanUp() {
        mic.stop()
        restoreVolumes()
        settings = null
    }

    private fun restoreVolumes() {
        session.transport.clickVolume = savedClickVolume
        session.transport.instrumentVolume = savedInstrumentVolume
    }

    companion object {
        const val BEATS = 16
        const val LOOPBACK_BPM = 100.0
        const val TAP_BPM = 80.0
        private const val TAIL_NANOS = 1_000_000_000L
        private const val FRAMES_BEFORE_FIRST = 2_400

        /** 16 calibration clicks, one a beat, after a 4-beat silent count-in. */
        fun loopbackPattern(click: Sound): Pattern =
            Pattern(
                lengthQuarters = BEATS.toDouble(),
                beatQuarters = 1.0,
                countInBeats = 4,
                events = List(BEATS) { PatternEvent(it.toDouble(), click) },
                clicks = emptyList(),
            )

        /** A 4/4 count-in, then 16 metronome clicks to tap along to. */
        fun tapPattern(): Pattern =
            Pattern(
                lengthQuarters = BEATS.toDouble(),
                beatQuarters = 1.0,
                countInBeats = 4,
                events = emptyList(),
                clicks = List(BEATS) { Click(it.toDouble(), accent = it % 4 == 0) },
            )

        /**
         * A sharp, broadband 3 ms noise burst: unlike the metronome's pitched
         * click, it has plenty of energy where the onset detector listens, and
         * its attack is sample 0.
         */
        fun calibrationClick(sampleRate: Int): Sound {
            val random = Random(5)
            val samples = FloatArray(sampleRate * 3 / 1000)
            val decay = sampleRate * 0.0006
            for (i in samples.indices) {
                samples[i] = ((random.nextDouble() * 2 - 1) * exp(-i / decay)).toFloat() * 0.9f
            }
            samples[0] = 0.9f
            return Sound("calibration-click", samples)
        }
    }
}
