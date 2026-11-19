package dev.co508.drums.audio

/**
 * A continuous stereo output stream that plays [Sound]s at exact sample frames.
 *
 * Frame numbers count output frames from the moment [start] was called: frame 0
 * is the first frame the engine renders. Every [start] begins a new stream at
 * frame 0. See DECISIONS.md, "One Audio Clock" and "Pull-Based Event
 * Scheduling".
 */
interface AudioEngine {
    /** Output sample rate in Hz. Sounds must be generated or resampled at this rate. */
    val sampleRate: Int

    val isRunning: Boolean

    /** Opens the output stream and starts the mixer thread. Does nothing if already running. */
    fun start()

    /** Stops the mixer thread and releases the output stream. Does nothing if stopped. */
    fun stop()

    /**
     * Sets what the mixer asks for events before rendering each block, or null for
     * silence. Takes effect from the next block. May be called from any thread.
     */
    fun setEventSource(source: EventSource?)

    /** A snapshot of the engine's counters, for diagnostics. May be called from any thread. */
    fun stats(): AudioEngineStats
}

/**
 * Supplies the events that start inside a block of output frames.
 *
 * Called on the mixer thread once per block, just before that block is
 * rendered. Implementations must not allocate, block or take locks shared with
 * slow code: anything that stalls this call can cause an audible underrun.
 */
fun interface EventSource {
    /**
     * Report, through [sink], every event that starts in `[fromFrame, toFrame)`.
     * Events may be reported in any order.
     */
    fun events(
        fromFrame: Long,
        toFrame: Long,
        sink: EventSink,
    )
}

/** Receives events from an [EventSource]. Only valid during the [EventSource.events] call. */
fun interface EventSink {
    /**
     * Starts [sound] so that its first sample plays at output [frame].
     *
     * @param gain linear amplitude multiplier, 1 = the sound as recorded.
     * @param pan -1 = hard left, 0 = centre, 1 = hard right (constant-power law).
     */
    fun play(
        frame: Long,
        sound: Sound,
        gain: Float,
        pan: Float,
    )
}

data class AudioEngineStats(
    val sampleRate: Int,
    /** The device's native burst size; the mixer renders and writes in blocks of this size. */
    val framesPerBurst: Int,
    /** Effective output buffer size. It grows by one burst after each underrun. */
    val bufferSizeFrames: Int,
    val bufferCapacityFrames: Int,
    /** Whether the system granted a low-latency (fast) track. */
    val lowLatencyGranted: Boolean,
    /** Underruns reported by the output stream since [AudioEngine.start]. */
    val underruns: Int,
    val framesRendered: Long,
    val mixer: MixerStats,
    /**
     * The negative AudioTrack error code that stopped the mixer thread, or 0.
     * After an error the engine is silent until [AudioEngine.stop] and [AudioEngine.start].
     */
    val writeError: Int,
)
