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
    /** Starting tempo, in the pattern's beats ([Pattern.beatQuarters]) per minute. */
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
 * Grace strokes ([PatternEvent.leadLevel] above 0) sound a fixed time before
 * their beat. Each lead level has its own window of positions, running that
 * many grace gaps ahead of the block. Those windows tile the timeline just as
 * the clicks' one does, so every grace stroke plays exactly once. Every
 * instrument window also runs [Pattern.attackFrames] ahead: a sampled snare
 * starts that early, so its attack, not its first sample, is on the beat.
 *
 * Control methods ([start], [stop], [setTempo], the volumes, the grace gap)
 * may be called from any thread and take effect at the next block. [events]
 * runs on the mixer thread and does not allocate.
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

    // The metronome's sounds: swappable for speaker mode, read once per click.
    @Volatile private var clickSound = click

    @Volatile private var accentClickSound = accentClick

    @Volatile private var clickLeadFrames = maxOf(click.attackFrames, accentClick.attackFrames)

    /** Plays the metronome with [normal] and [accent] (speaker mode) from the next block. */
    fun setClickSounds(
        normal: Sound,
        accent: Sound,
    ) {
        clickSound = normal
        accentClickSound = accent
        clickLeadFrames = maxOf(normal.attackFrames, accent.attackFrames)
    }

    /** Back to the usual metronome. */
    fun resetClickSounds() = setClickSounds(click, accentClick)

    /** Metronome volume, 0 for off. */
    @Volatile
    var clickVolume = 1f

    /** Pattern (snare) volume, 0 for off. */
    @Volatile
    var instrumentVolume = 1f

    /** How far apart grace strokes are, and how far the last one is before its beat. */
    @Volatile
    var graceGapMillis = DEFAULT_GRACE_GAP_MILLIS

    /**
     * Starts instrument sounds this much earlier still, on top of their measured
     * attack ([dev.co508.drums.audio.Sound.attackFrames]). 0 in the app; the
     * debug screen's ear test of where a snare's attack is heard.
     */
    @Volatile
    var extraAttackLeadMillis = 0.0

    /** Updated by the mixer thread, so it runs ahead of what is heard by the output latency. */
    @Volatile
    var state = State.STOPPED
        private set

    /** The settings being played, set by the mixer thread when playback starts. */
    @Volatile
    var settings: PlaySettings? = null
        private set

    /** Published after a Start is accepted by the mixer, even when reusing the same settings object. */
    @Volatile
    var startGeneration = 0L
        private set

    // Mixer-thread state.
    private var tempo = 0.0
    private var loopIndex = 0L
    private var windowEnd = 0.0
    private var clickWindowEnd = 0.0
    private val leadWindowEnds = DoubleArray(Pattern.MAX_LEAD_LEVEL + 1)
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
        val pattern = settings?.pattern ?: return Double.NaN
        return tempoMap.rateAt(frame) * 60.0 * sampleRate / pattern.beatQuarters
    }

    fun highestBeatsPerMinuteThrough(frame: Double): Double {
        val pattern = settings?.pattern ?: return Double.NaN
        return tempoMap.highestRateThrough(frame) * 60.0 * sampleRate / pattern.beatQuarters
    }

    override fun events(
        fromFrame: Long,
        toFrame: Long,
        sink: EventSink,
    ) {
        when (val c = command.getAndSet(null)) {
            is Start -> begin(c.settings, fromFrame)
            Stop -> halt(fromFrame)
            null ->
                if (fromFrame != expectedFrame) {
                    if (state == State.PLAYING) {
                        reanchor(fromFrame)
                    } else if (state == State.FINISHED) {
                        tempoMap.begin(fromFrame.toDouble(), windowEnd, 0.0)
                    }
                }
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

        // Clicks lead by their own attack (0 for the usual click), like instrument sounds.
        val clickLead = clickLeadFrames.toDouble()
        val clickEnd = maxOf(clickWindowEnd, if (clickLead > 0) tempoMap.positionAt(lastFrame + clickLead) else end)
        emit(playing, CLICKS, clickWindowEnd, clickEnd, clickLead, fromFrame, toFrame, sink)
        clickWindowEnd = clickEnd
        windowEnd = end
        val gapFrames = graceGapMillis.coerceIn(0.0, MAX_GRACE_GAP_MILLIS) * sampleRate / 1000.0
        // Instrument sounds start early by their attack, so the attack is heard on the beat.
        val attack =
            playing.pattern.attackFrames + extraAttackLeadMillis.coerceIn(0.0, MAX_EXTRA_ATTACK_LEAD_MILLIS) *
                sampleRate / 1000.0
        for (level in 0..playing.pattern.maxLeadLevel) {
            val lead = level * gapFrames + attack
            // max(): a shrinking lead must not reopen positions already played.
            val leadEnd = maxOf(leadWindowEnds[level], tempoMap.positionAt(lastFrame + lead))
            emit(playing, level, leadWindowEnds[level], leadEnd, lead, fromFrame, toFrame, sink)
            leadWindowEnds[level] = leadEnd
        }
        windowIncludesStart = false
        if (finished) state = State.FINISHED
    }

    private fun begin(
        playing: PlaySettings,
        fromFrame: Long,
    ) {
        settings = playing
        tempo = clampTempo(playing.beatsPerMinute)
        val pattern = playing.pattern
        val start = if (playing.countIn) -pattern.countInBeats * pattern.beatQuarters else 0.0
        tempoMap.begin(fromFrame.toDouble(), start, rateOf(tempo, playing))
        loopIndex = if (playing.countIn) -1 else 0
        windowEnd = start
        clickWindowEnd = start
        leadWindowEnds.fill(start)
        windowIncludesStart = true
        state = State.PLAYING
        startGeneration++
    }

    private fun halt(fromFrame: Long) {
        if (state == State.PLAYING) {
            if (fromFrame == expectedFrame) {
                tempoMap.changeRateAtFrame(fromFrame.toDouble(), 0.0)
            } else {
                // Stop can arrive while a replacement stream is being built, before frame 0.
                tempoMap.begin(fromFrame.toDouble(), windowEnd, 0.0)
            }
        }
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

    /**
     * Emits, in the window (start, end], the clicks ([level] = [CLICKS]) or the
     * instrument events of one lead level (0 = on the beat), each [leadFrames] early.
     */
    private fun emit(
        playing: PlaySettings,
        level: Int,
        start: Double,
        end: Double,
        leadFrames: Double,
        fromFrame: Long,
        toFrame: Long,
        sink: EventSink,
    ) {
        val pattern = playing.pattern
        val clickGain = clickVolume
        val instrumentGain = instrumentVolume

        if (level == CLICKS && playing.countIn && start < 0.0 && clickGain > 0f) {
            for (m in 0 until pattern.countInBeats) {
                val position = (m - pattern.countInBeats) * pattern.beatQuarters
                if (inWindow(position, start, end)) {
                    sink.play(
                        frameOf(position, leadFrames, fromFrame, toFrame),
                        if (m ==
                            0
                        ) {
                            accentClickSound
                        } else {
                            clickSound
                        },
                        clickGain,
                        0f,
                    )
                }
            }
        }
        if (end < 0.0) return

        val length = pattern.lengthQuarters
        val firstLoop = if (start < 0.0) 0L else floor(start / length).toLong()
        val lastLoop = if (playing.loop) floor(end / length).toLong() else 0L
        for (k in firstLoop..lastLoop) {
            val base = k * length
            if (level == CLICKS && clickGain > 0f) {
                val clicks = pattern.clickPositions
                var i = scanStart(clicks, start - base)
                while (i < clicks.size && base + clicks[i] <= end) {
                    val position = base + clicks[i]
                    if (inWindow(position, start, end)) {
                        val sound = if (pattern.clickAccents[i]) accentClickSound else clickSound
                        sink.play(frameOf(position, leadFrames, fromFrame, toFrame), sound, clickGain, 0f)
                    }
                    i++
                }
            }
            if (level != CLICKS && instrumentGain > 0f) {
                val positions = pattern.eventPositions
                var i = scanStart(positions, start - base)
                while (i < positions.size && base + positions[i] <= end) {
                    val position = base + positions[i]
                    if (pattern.eventLeadLevels[i] == level && inWindow(position, start, end)) {
                        val frame = frameOf(position, leadFrames, fromFrame, toFrame)
                        val sound = pattern.eventSounds[i]
                        val gain = pattern.eventGains[i] * instrumentGain
                        val duration = pattern.eventDurations[i]
                        if (duration > 0.0) {
                            val frames = tempoMap.frameAt(position + duration) - tempoMap.frameAt(position)
                            sink.playFor(
                                frame,
                                sound,
                                gain,
                                pattern.eventPans[i],
                                frames.toLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
                            )
                        } else {
                            sink.play(frame, sound, gain, pattern.eventPans[i])
                        }
                    }
                    i++
                }
            }
        }
    }

    /** Where to start scanning sorted [values] for positions after [x]: one early, so rounding can't skip one. */
    private fun scanStart(
        values: DoubleArray,
        x: Double,
    ): Int = maxOf(0, firstAbove(values, x) - 1)

    private fun inWindow(
        position: Double,
        start: Double,
        end: Double,
    ): Boolean = (position > start || (windowIncludesStart && position == start)) && position <= end

    /**
     * The first frame at or after [position], less [leadFrames]. An event within [FRAME_EPSILON] of a
     * whole frame lands on it, so float rounding can't make it a frame late. The
     * window test decides which block an event belongs to; clamping keeps
     * rounding from moving it out.
     */
    private fun frameOf(
        position: Double,
        leadFrames: Double,
        fromFrame: Long,
        toFrame: Long,
    ): Long = ceil(tempoMap.frameAt(position) - leadFrames - FRAME_EPSILON).toLong().coerceIn(fromFrame, toFrame - 1)

    private fun rateOf(
        beatsPerMinute: Double,
        playing: PlaySettings,
    ): Double = beatsPerMinute * playing.pattern.beatQuarters / (60.0 * sampleRate)

    private fun clampTempo(beatsPerMinute: Double): Double =
        if (beatsPerMinute.isNaN()) MIN_BPM else beatsPerMinute.coerceIn(MIN_BPM, MAX_BPM)

    companion object {
        const val MIN_BPM = 1.0
        const val MAX_BPM = 1_000.0

        /** Chosen by ear on the reference device (DECISIONS.md, "Grace Notes Sound Just Before The Beat"). */
        const val DEFAULT_GRACE_GAP_MILLIS = 12.0
        const val MAX_GRACE_GAP_MILLIS = 150.0
        const val MAX_EXTRA_ATTACK_LEAD_MILLIS = 30.0
        private const val CLICKS = -1
        private const val FRAME_EPSILON = 1e-6
        private val NO_TEMPO = Double.NaN.toRawBits()
    }
}
