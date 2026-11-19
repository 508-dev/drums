package dev.co508.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * [AudioEngine] on a Kotlin mixer thread writing one continuous float PCM
 * stream to a low-latency [AudioTrack]. See DECISIONS.md, "Kotlin AudioTrack
 * Mixer, Oboe Only If Needed".
 *
 * The mixer thread renders one burst at a time and writes it with a blocking
 * write, so it runs exactly one effective buffer ahead of the speaker. Frame N
 * written is frame N of the stream, and stays so across underruns, because the
 * track's frame position counts frames played, not time elapsed.
 */
class AudioTrackEngine(
    context: Context,
    private val config: Config = Config(),
) : AudioEngine {
    data class Config(
        /** Effective buffer to start with. Measured on the reference device in M1 (docs/timing.md). */
        val targetBufferMillis: Int = 40,
        /** How far the buffer may grow, one burst per underrun. */
        val maxBufferMillis: Int = 200,
        val maxVoices: Int = Mixer.DEFAULT_MAX_VOICES,
    )

    override val sampleRate: Int
    private val framesPerBurst: Int

    init {
        val audioManager = context.getSystemService(AudioManager::class.java)
        sampleRate =
            audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 } ?: FALLBACK_SAMPLE_RATE
        framesPerBurst =
            audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
                ?.toIntOrNull()
                ?.takeIf { it > 0 } ?: FALLBACK_FRAMES_PER_BURST
    }

    private val lock = Any()
    private var running: Running? = null

    @Volatile
    private var source: EventSource? = null

    // The last stopped stream's stats, so they stay readable after stop().
    @Volatile
    private var lastStats: AudioEngineStats? = null

    override val isRunning: Boolean get() = synchronized(lock) { running != null }

    override fun start() {
        synchronized(lock) {
            if (running != null) return
            val r = Running(createTrack(), Mixer(config.maxVoices))
            r.thread.start()
            running = r
        }
    }

    override fun stop() {
        val r =
            synchronized(lock) {
                running.also { running = null }
            } ?: return
        r.shutDown()
        lastStats = r.stats()
    }

    override fun setEventSource(source: EventSource?) {
        this.source = source
    }

    private val timestamp = AudioTimestamp()

    override fun outputTimestamp(out: OutputTimestamp): Boolean =
        synchronized(lock) {
            val track = running?.track ?: return false
            if (!track.getTimestamp(timestamp)) return false
            out.framePosition = timestamp.framePosition
            out.nanoTime = timestamp.nanoTime
            true
        }

    override fun stats(): AudioEngineStats =
        synchronized(lock) { running?.stats() }
            ?: lastStats
            ?: AudioEngineStats(
                sampleRate = sampleRate,
                framesPerBurst = framesPerBurst,
                bufferSizeFrames = 0,
                bufferCapacityFrames = 0,
                lowLatencyGranted = false,
                underruns = 0,
                framesRendered = 0,
                mixer = MixerStats(lateEvents = 0, droppedEvents = 0, stolenVoices = 0, peakVoices = 0),
                writeError = 0,
            )

    private fun createTrack(): AudioTrack {
        val initialFrames = BufferSizing.initialFrames(sampleRate, framesPerBurst, config.targetBufferMillis)
        val minBufferBytes =
            AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val capacityFrames =
            BufferSizing.capacityFrames(
                sampleRate = sampleRate,
                framesPerBurst = framesPerBurst,
                initialFrames = initialFrames,
                maxMillis = config.maxBufferMillis,
                minBufferFrames = maxOf(minBufferBytes, 0) / BYTES_PER_FRAME,
            )
        val track =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                ).setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setBufferSizeInBytes(capacityFrames * BYTES_PER_FRAME)
                .build()
        track.setBufferSizeInFrames(initialFrames)
        return track
    }

    private inner class Running(
        val track: AudioTrack,
        val mixer: Mixer,
    ) {
        val capacityFrames = track.bufferCapacityInFrames
        val lowLatencyGranted = track.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY

        @Volatile
        var keepRunning = true

        @Volatile
        var underruns = 0

        @Volatile
        var bufferSizeFrames = track.bufferSizeInFrames

        @Volatile
        var writeError = 0

        val thread = Thread(::run, "drums-mixer")

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val block = FloatArray(framesPerBurst * CHANNELS)
            // Floats of `block` already written. A short write keeps the rest of the
            // block for the next write, so no rendered frame is ever skipped.
            var offset = block.size

            // Fill the buffer before starting, so playback doesn't open on an underrun.
            var prefilled = 0
            while (keepRunning && prefilled + framesPerBurst <= bufferSizeFrames) {
                mixer.render(block, framesPerBurst, source)
                offset = track.write(block, 0, block.size, AudioTrack.WRITE_NON_BLOCKING)
                if (offset < 0) return fail(offset)
                prefilled += framesPerBurst
                if (offset < block.size) break
            }
            val baseUnderruns = track.underrunCount
            track.play()

            while (keepRunning) {
                if (offset == block.size) {
                    mixer.render(block, framesPerBurst, source)
                    offset = 0
                }
                val written = track.write(block, offset, block.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) return fail(written)
                offset += written
                val count = track.underrunCount - baseUnderruns
                if (count != underruns) {
                    underruns = count
                    // Timing beats latency: buy headroom so it doesn't happen again.
                    track.setBufferSizeInFrames(
                        BufferSizing.grownFrames(bufferSizeFrames, framesPerBurst, capacityFrames),
                    )
                    bufferSizeFrames = track.bufferSizeInFrames
                }
            }
        }

        private fun fail(error: Int) {
            writeError = error
            Log.e(TAG, "AudioTrack.write failed: $error")
        }

        fun shutDown() {
            keepRunning = false
            // A blocking write returns within one buffer duration while the track plays.
            thread.join(JOIN_TIMEOUT_MILLIS)
            if (thread.isAlive) {
                // Unblock a stuck write before releasing the track under it.
                track.pause()
                track.flush()
                thread.join(JOIN_TIMEOUT_MILLIS)
            }
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.release()
        }

        fun stats() =
            AudioEngineStats(
                sampleRate = sampleRate,
                framesPerBurst = framesPerBurst,
                bufferSizeFrames = bufferSizeFrames,
                bufferCapacityFrames = capacityFrames,
                lowLatencyGranted = lowLatencyGranted,
                underruns = underruns,
                framesRendered = mixer.framesRendered,
                mixer = mixer.stats(),
                writeError = writeError,
            )
    }

    private companion object {
        const val TAG = "AudioTrackEngine"
        const val CHANNELS = 2
        const val BYTES_PER_FRAME = CHANNELS * Float.SIZE_BYTES
        const val FALLBACK_SAMPLE_RATE = 48_000
        const val FALLBACK_FRAMES_PER_BURST = 256
        const val JOIN_TIMEOUT_MILLIS = 1_000L
    }
}
