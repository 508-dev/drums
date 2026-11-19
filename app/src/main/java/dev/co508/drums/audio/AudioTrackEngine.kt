package dev.co508.drums.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

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

    private val generation = AtomicLong()
    override val streamGeneration get() = generation.get()

    @Volatile
    private var source: EventSource? = null

    // The last stopped stream's stats, so they stay readable after stop().
    @Volatile
    private var lastStats: AudioEngineStats? = null

    override val isRunning: Boolean get() = synchronized(lock) { running != null }

    override fun start() {
        synchronized(lock) {
            if (running != null) return
            val r = Running()
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

    override fun outputTimestamp(out: OutputTimestamp): Boolean =
        synchronized(lock) { running?.output?.timestamp(out) == true }

    override fun requestOutputRestart() {
        synchronized(lock) { running?.output?.requestRestart("requested output recreation") }
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
                outputDevice = null,
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
        try {
            track.setBufferSizeInFrames(initialFrames)
            return track
        } catch (error: RuntimeException) {
            track.release()
            throw error
        }
    }

    private inner class Running {
        val output =
            RecoveringAudioOutput(
                sampleRate,
                framesPerBurst,
                config.maxVoices,
                createTrack = { AndroidTrack(createTrack()) { reason -> outputRestart(reason) } },
                source = { source },
                generationChanged = { generation.incrementAndGet() },
                log = { Log.i(TAG, "$it wallTimeMillis=${System.currentTimeMillis()}") },
            )

        private fun outputRestart(reason: String) {
            output.requestRestart(reason)
        }

        val thread =
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                output.run()
            }, "drums-mixer")

        fun shutDown() {
            output.requestStop()
            thread.interrupt()
            thread.join(JOIN_TIMEOUT_MILLIS)
            if (thread.isAlive) {
                // The worker alone owns release. Never release native state beneath a blocked write.
                Log.e(TAG, "Mixer didn't stop within timeout; release remains worker-owned")
            }
        }

        fun stats() = output.stats()
    }

    private inner class AndroidTrack(
        private val native: AudioTrack,
        onRouteChanged: (String) -> Unit,
    ) : PcmOutputTrack {
        private val timestamp = AudioTimestamp()
        private val routingLock = Any()

        @Volatile private var released = false
        private var previousDeviceId: Int? = null
        private val listener =
            android.media.AudioRouting.OnRoutingChangedListener {
                synchronized(routingLock) {
                    if (!released) {
                        try {
                            val id = native.routedDevice?.id
                            val previous = previousDeviceId
                            previousDeviceId = id
                            if (native.state != AudioTrack.STATE_INITIALIZED || (previous != null && id != previous)) {
                                onRouteChanged("output route changed $previous→$id")
                            }
                        } catch (_: RuntimeException) {
                            onRouteChanged("output route invalidated track")
                        }
                    }
                }
            }

        init {
            try {
                native.addOnRoutingChangedListener(listener, Handler(Looper.getMainLooper()))
            } catch (error: RuntimeException) {
                native.release()
                throw error
            }
        }

        override val capacityFrames get() = native.bufferCapacityInFrames
        override val bufferSizeFrames get() = native.bufferSizeInFrames
        override val lowLatencyGranted get() = native.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        override val outputDevice get() = native.routedDevice?.let(::describe)
        override val underrunCount get() = native.underrunCount

        override fun resizeBuffer(frames: Int) {
            native.setBufferSizeInFrames(frames)
        }

        override fun write(
            samples: FloatArray,
            offset: Int,
            count: Int,
            blocking: Boolean,
        ) = native.write(
            samples,
            offset,
            count,
            if (blocking) AudioTrack.WRITE_BLOCKING else AudioTrack.WRITE_NON_BLOCKING,
        )

        override fun play() {
            native.play()
            synchronized(routingLock) { previousDeviceId = native.routedDevice?.id }
        }

        override fun timestamp(out: OutputTimestamp): Boolean {
            if (!native.getTimestamp(timestamp)) return false
            out.framePosition = timestamp.framePosition
            out.nanoTime = timestamp.nanoTime
            return true
        }

        override fun pauseAndFlush() {
            native.pause()
            native.flush()
        }

        override fun release() {
            synchronized(routingLock) {
                released = true
                try {
                    native.removeOnRoutingChangedListener(listener)
                    native.stop()
                } finally {
                    native.release()
                }
            }
        }
    }

    private fun describe(device: AudioDeviceInfo): String {
        val type =
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "earpiece"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                else -> "type ${device.type}"
            }
        return "$type (${device.productName})"
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
