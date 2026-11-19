package dev.co508.drums.playback

import dev.co508.drums.audio.EventSink
import dev.co508.drums.audio.EventSource
import dev.co508.drums.audio.Sound
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil
import kotlin.math.floor

/** How the transport should play a pattern. Immutable. */
class PlaySettings(
    val pattern: Pattern,
    /** Starting tempo, in the pattern meter's beats per minute. */
    val beatsPerMinute: Double,
    /** Play one bar of clicks before the pattern. */
    val countIn: Boolean = true,
    /** Repeat the pattern until stopped; otherwise play it once and finish. */
    val loop: Boolean = true,
    val trainer: SpeedTrainer? = null,
)

/**
 * Plays a [Pattern] against the audio clock: tempo, count-in, looping and tempo
 * changes. It is the engine's [EventSource]: each block, it works out which
 * pattern events and clicks start in that block and at exactly which frame.
 *
 * Musical position is in quarter notes, from the pattern's start: the count-in
 * bar occupies negative positions, and loop `k` covers `[k * length, (k + 1) *
 * length)`. The [tempoMap] converts between position and output frames in both
 * directions, and stays continuous across tempo changes.
 *
 * Control methods ([start], [stop], [setTempo], the volumes) may be called from
 * any thread and take effect at the next block. [events] runs on the mixer
 * thread and does not allocate.
 */
class Transport(
    val sampleRate: Int,
    private val click: Sound,
    private val accentClick: Sound,
    val tempoMap: TempoMap = TempoMap(),
) : EventSource {
    enum class State { STOPPED, PLAYING, FINISHED }

    private sealed interface Command

    private class Start(
        val settings: PlaySettings,
    ) : Command

    private data object Stop : Command

    private val command = AtomicReference<Command?>(null)
    private val pendingTempo = AtomicLong(NO_TEMPO)

    /** Metronome volume, 0 for off. */
    @Volatile
    var clickVolume = 1f

    /** Pattern (snare) volume, 0 for off. */
    @Volatile
    var instrumentVolume = 1f

    /** Updated by the mixer thread, so it runs ahead of what is heard by the output latency. */
    @Volatile
    var state = State.STOPPED
        private set

    /** The settings being played, set by the mixer thread when playback starts. */
    @Volatile
    var settings: PlaySettings? = null
        private set

    // Mixer-thread state.
    private var tempo = 0.0
    private var loopIndex = 0L
    private var windowEnd = 0.0
    private var windowIncludesStart = false
    private var expectedFrame = -1L

    /** Starts playing from the count-in (or the pattern start), replacing whatever is playing. */
    fun start(settings: PlaySettings) {
        pendingTempo.set(NO_TEMPO)
        command.set(Start(settings))
    }

    fun stop() {
        command.set(Stop)
    }

    /** Changes the tempo from the next block on (the tempo slider). */
    fun setTempo(beatsPerMinute: Double) {
        pendingTempo.set(beatsPerMinute.toRawBits())
    }

    /** Musical position, in quarter notes, at an output [frame]. NaN before the first playback. */
    fun positionAt(frame: Double): Double = tempoMap.positionAt(frame)

    /** The tempo in effect at an output [frame], in beats per minute; 0 when stopped. */
    fun beatsPerMinuteAt(frame: Double): Double {
        val meter = settings?.pattern?.meter ?: return Double.NaN
        return tempoMap.rateAt(frame) * 60.0 * sampleRate / meter.beatQuarters
    }

    override fun events(
        fromFrame: Long,
        toFrame: Long,
        sink: EventSink,
    ) {
        when (val c = command.getAndSet(null)) {
            is Start -> begin(c.settings, fromFrame)
            Stop -> halt(fromFrame)
            null -> if (state == State.PLAYING && fromFrame != expectedFrame) reanchor(fromFrame)
        }
        expectedFrame = toFrame
        if (state != State.PLAYING) return
        val playing = settings ?: return
        val length = playing.pattern.lengthQuarters

        val tempoBits = pendingTempo.getAndSet(NO_TEMPO)
        if (tempoBits != NO_TEMPO) {
            tempo = clampTempo(Double.fromBits(tempoBits))
            tempoMap.changeRateAtFrame(fromFrame.toDouble(), rateOf(tempo, playing))
        }

        // This block covers the positions in (windowEnd, end], where end is the
        // position of its last frame; consecutive blocks tile the timeline.
        val lastFrame = (toFrame - 1).toDouble()
        var end = tempoMap.positionAt(lastFrame)
        var finished = false
        while (true) {
            val boundary = (loopIndex + 1) * length
            if (boundary > end) break
            if (!playing.loop && loopIndex >= 0) {
                tempoMap.changeRateAtPosition(boundary, 0.0)
                end = boundary
                finished = true
                break
            }
            loopIndex++
            // Not `trainer?.tempoEntering(…) ?: tempo`: that boxes the Double.
            val trainer = playing.trainer
            val next = if (trainer != null) trainer.tempoEntering(loopIndex, tempo) else tempo
            if (next != tempo) {
                tempo = next
                tempoMap.changeRateAtPosition(boundary, rateOf(tempo, playing))
                end = tempoMap.positionAt(lastFrame)
            }
        }

        emit(playing, end, fromFrame, toFrame, sink)
        windowEnd = end
        windowIncludesStart = false
        if (finished) state = State.FINISHED
    }

    private fun begin(
        playing: PlaySettings,
        fromFrame: Long,
    ) {
        settings = playing
        tempo = clampTempo(playing.beatsPerMinute)
        val start = if (playing.countIn) -playing.pattern.meter.barQuarters else 0.0
        tempoMap.begin(fromFrame.toDouble(), start, rateOf(tempo, playing))
        loopIndex = if (playing.countIn) -1 else 0
        windowEnd = start
        windowIncludesStart = true
        state = State.PLAYING
    }

    private fun halt(fromFrame: Long) {
        if (state == State.PLAYING) tempoMap.changeRateAtFrame(fromFrame.toDouble(), 0.0)
        state = State.STOPPED
    }

    /**
     * The output stream restarted (its frames begin again at 0) while playing.
     * Carry on from the position reached, in a new tempo-map epoch.
     */
    private fun reanchor(fromFrame: Long) {
        val playing = settings ?: return
        val rate = rateOf(tempo, playing)
        tempoMap.begin(fromFrame.toDouble(), windowEnd + rate, rate)
    }

    private fun emit(
        playing: PlaySettings,
        end: Double,
        fromFrame: Long,
        toFrame: Long,
        sink: EventSink,
    ) {
        val pattern = playing.pattern
        val meter = pattern.meter
        val start = windowEnd
        val clickGain = clickVolume
        val instrumentGain = instrumentVolume

        if (playing.countIn && start < 0.0 && clickGain > 0f) {
            for (m in 0 until meter.beatsPerBar) {
                val position = (m - meter.beatsPerBar) * meter.beatQuarters
                if (inWindow(position, start, end)) {
                    sink.play(frameOf(position, fromFrame, toFrame), if (m == 0) accentClick else click, clickGain, 0f)
                }
            }
        }
        if (end < 0.0) return

        val length = pattern.lengthQuarters
        val firstLoop = if (start < 0.0) 0L else floor(start / length).toLong()
        val lastLoop = if (playing.loop) floor(end / length).toLong() else 0L
        for (k in firstLoop..lastLoop) {
            val base = k * length
            if (clickGain > 0f) {
                val clicks = pattern.clickPositions
                for (i in clicks.indices) {
                    val position = base + clicks[i]
                    if (inWindow(position, start, end)) {
                        val sound = if (pattern.clickAccents[i]) accentClick else click
                        sink.play(frameOf(position, fromFrame, toFrame), sound, clickGain, 0f)
                    }
                }
            }
            if (instrumentGain > 0f) {
                val positions = pattern.eventPositions
                for (i in positions.indices) {
                    val position = base + positions[i]
                    if (inWindow(position, start, end)) {
                        sink.play(
                            frameOf(position, fromFrame, toFrame),
                            pattern.eventSounds[i],
                            pattern.eventGains[i] * instrumentGain,
                            pattern.eventPans[i],
                        )
                    }
                }
            }
        }
    }

    private fun inWindow(
        position: Double,
        start: Double,
        end: Double,
    ): Boolean = (position > start || (windowIncludesStart && position == start)) && position <= end

    /**
     * The first frame at or after [position]. An event within [FRAME_EPSILON] of a
     * whole frame lands on it, so float rounding can't make it a frame late. The
     * window test decides which block an event belongs to; clamping keeps
     * rounding from moving it out.
     */
    private fun frameOf(
        position: Double,
        fromFrame: Long,
        toFrame: Long,
    ): Long = ceil(tempoMap.frameAt(position) - FRAME_EPSILON).toLong().coerceIn(fromFrame, toFrame - 1)

    private fun rateOf(
        beatsPerMinute: Double,
        playing: PlaySettings,
    ): Double = beatsPerMinute * playing.pattern.meter.beatQuarters / (60.0 * sampleRate)

    private fun clampTempo(beatsPerMinute: Double): Double =
        if (beatsPerMinute.isNaN()) MIN_BPM else beatsPerMinute.coerceIn(MIN_BPM, MAX_BPM)

    companion object {
        const val MIN_BPM = 1.0
        const val MAX_BPM = 1_000.0
        private const val FRAME_EPSILON = 1e-6
        private val NO_TEMPO = Double.NaN.toRawBits()
    }
}
