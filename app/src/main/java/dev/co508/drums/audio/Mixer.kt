package dev.co508.drums.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Renders interleaved stereo float blocks from events pulled from an
 * [EventSource]. Pure Kotlin with no Android dependencies, so its frame and
 * mixing math is unit-tested on the JVM.
 *
 * [render] must be called from one thread only (the mixer thread). It does not
 * allocate: voices live in preallocated parallel arrays, and the event sink is
 * a single long-lived object.
 *
 * Output frame N of the stream is the Nth frame ever rendered. A sound played at
 * frame F has its sample 0 at output frame F, whatever the block size.
 */
class Mixer(
    val maxVoices: Int = DEFAULT_MAX_VOICES,
    private val masterGain: Float = 1f,
) {
    init {
        require(maxVoices > 0) { "maxVoices must be positive" }
    }

    private val voiceSound = arrayOfNulls<Sound>(maxVoices)

    // Index of the voice's next sample to render, at the start of the current
    // block. Negative while the voice is waiting to start inside the block.
    private val voicePosition = IntArray(maxVoices)
    private val voiceGainLeft = FloatArray(maxVoices)
    private val voiceGainRight = FloatArray(maxVoices)

    private var blockStart = 0L
    private var blockFrames = 0

    /** Frames rendered so far; also the frame number the next block starts at. */
    @Volatile
    var framesRendered: Long = 0L
        private set

    // Written only on the render thread; volatile so stats() can read them.
    @Volatile
    private var lateEvents = 0L

    @Volatile
    private var droppedEvents = 0L

    @Volatile
    private var stolenVoices = 0L

    @Volatile
    private var peakVoices = 0

    private val sink = EventSink { frame, sound, gain, pan -> startVoice(frame, sound, gain, pan) }

    /**
     * Renders the next [frames] frames into [out] as interleaved stereo
     * (L, R, L, R…), after asking [source] for the events starting in them.
     * Samples stay within [-1, 1]: see [softClip].
     */
    fun render(
        out: FloatArray,
        frames: Int,
        source: EventSource?,
    ) {
        require(frames >= 0 && out.size >= frames * 2) { "out is too small for $frames stereo frames" }
        val from = framesRendered
        blockStart = from
        blockFrames = frames
        source?.events(from, from + frames, sink)

        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        var active = 0
        for (v in 0 until maxVoices) {
            val sound = voiceSound[v] ?: continue
            mixVoice(v, sound.samples, out, frames)
            val next = voicePosition[v] + frames
            if (next >= sound.samples.size) {
                voiceSound[v] = null
            } else {
                voicePosition[v] = next
            }
            active++
        }
        if (active > peakVoices) peakVoices = active

        for (i in 0 until frames * 2) {
            out[i] = softClip(out[i] * masterGain)
        }
        framesRendered = from + frames
    }

    fun stats(): MixerStats =
        MixerStats(
            lateEvents = lateEvents,
            droppedEvents = droppedEvents,
            stolenVoices = stolenVoices,
            peakVoices = peakVoices,
        )

    private fun mixVoice(
        v: Int,
        samples: FloatArray,
        out: FloatArray,
        frames: Int,
    ) {
        val position = voicePosition[v]
        val first = if (position < 0) -position else 0
        val end = minOf(frames, samples.size - position)
        val left = voiceGainLeft[v]
        val right = voiceGainRight[v]
        var i = first
        var s = position + first
        while (i < end) {
            val x = samples[s]
            out[2 * i] += x * left
            out[2 * i + 1] += x * right
            i++
            s++
        }
    }

    private fun startVoice(
        frame: Long,
        sound: Sound,
        gain: Float,
        pan: Float,
    ) {
        var offset = frame - blockStart
        if (offset < 0) {
            // The source missed this event's block. Play it now, attack intact,
            // rather than drop it or skip its start.
            lateEvents++
            offset = 0
        } else if (offset >= blockFrames) {
            // Outside the block the source was asked about; the contract says
            // this doesn't happen, and holding it would need an unbounded queue.
            droppedEvents++
            return
        }
        if (sound.samples.isEmpty()) return

        val v = freeVoice()
        voiceSound[v] = sound
        voicePosition[v] = -offset.toInt()
        val angle = (pan.coerceIn(-1f, 1f) + 1f) * (PI / 4)
        val g = gain.coerceAtLeast(0f)
        voiceGainLeft[v] = g * cos(angle).toFloat()
        voiceGainRight[v] = g * sin(angle).toFloat()
    }

    /** A free voice, or the one that has played longest if all are busy. */
    private fun freeVoice(): Int {
        var oldest = 0
        for (v in 0 until maxVoices) {
            if (voiceSound[v] == null) return v
            if (voicePosition[v] > voicePosition[oldest]) oldest = v
        }
        stolenVoices++
        return oldest
    }

    companion object {
        const val DEFAULT_MAX_VOICES = 64

        /** Below this level the mix passes unchanged. */
        const val SOFT_CLIP_KNEE = 0.9f

        /**
         * Keeps a sample within [-1, 1] without a hard edge. It is linear up to
         * [SOFT_CLIP_KNEE], then bends smoothly (tanh, with matching slope at the
         * knee) towards full scale, so loud overlapping voices are rounded off
         * rather than squared off. See DECISIONS.md, "Soft Clipping On The Mix".
         */
        fun softClip(x: Float): Float {
            val magnitude = abs(x)
            if (magnitude <= SOFT_CLIP_KNEE) return x
            val room = 1f - SOFT_CLIP_KNEE
            val bent = SOFT_CLIP_KNEE + room * tanh((magnitude - SOFT_CLIP_KNEE) / room)
            return if (x < 0f) -bent else bent
        }
    }
}

data class MixerStats(
    /** Events reported for a frame before the block they were reported in; played at the block start. */
    val lateEvents: Long,
    /** Events reported for a frame after the block they were reported in; not played. */
    val droppedEvents: Long,
    /** Voices cut short because every voice was busy. */
    val stolenVoices: Long,
    /** The most voices sounding in one block. */
    val peakVoices: Int,
)
