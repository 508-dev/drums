package dev.co508.drums.hits

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission

/**
 * The microphone, listening for stick hits. See DECISIONS.md, "Mic Capture:
 * The Rawest Source, The Phone's Own Mic".
 *
 * [start] opens an `AudioRecord` on the rawest source the device offers
 * (UNPROCESSED, else VOICE_RECOGNITION, else MIC), prefers the phone's own
 * microphone over a headset's, and switches off any voice processing (gain
 * control, noise suppression, echo cancelling), which smears transients. A
 * capture thread at audio priority reads small blocks, finds onsets with an
 * [OnsetDetector] and queues them as [DetectedHit]s timed on the
 * [System.nanoTime] clock, ready for [poll]. Nothing is stored, and nothing
 * leaves the device.
 *
 * [start], [stop] and [poll] are for one controlling thread (the UI's); [stats]
 * and [tap] may be used from any thread.
 */
class MicCapture(
    context: Context,
    /** Capture rate: the output's, so both streams run at the device's native rate. */
    val sampleRate: Int,
    /** How hits are detected; takes effect at the next [start] (speaker mode adds notches). */
    @Volatile var detectorConfig: OnsetDetector.Config = OnsetDetector.Config(),
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val queue = HitQueue()

    /** Sees every raw block on the capture thread; see [CaptureTap]. */
    @Volatile
    var tap: CaptureTap? = null

    private var running: Running? = null
    private var lastStats: MicStats? = null

    val isRunning: Boolean get() = running != null

    /**
     * Opens the microphone and starts listening. Returns false, leaving it
     * closed, if no input could be opened. Does nothing if already running.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        if (running != null) return true
        val input = AudioRecordInput.open(audioManager, sampleRate) ?: return false
        queue.clear()
        val r = Running(input)
        running = r
        r.thread.start()
        return true
    }

    fun stop() {
        val r = running ?: return
        running = null
        r.shutDown()
        lastStats = r.stats()
    }

    /** Fills [out] with the oldest unread hit and returns true; false when there are none. */
    fun poll(out: DetectedHit): Boolean = queue.poll(out)

    fun stats(): MicStats = running?.stats() ?: lastStats ?: MicStats()

    private inner class Running(
        val input: AudioRecordInput,
    ) {
        val detector = OnsetDetector(input.sampleRate, detectorConfig)
        val clock = InputClock(input.sampleRate)
        val loop =
            CaptureLoop(
                input = input,
                detector = detector,
                clock = clock,
                queue = queue,
                blockFrames = input.blockFrames,
                tap = { tap },
                log = { Log.i(TAG, "$it wallTimeMillis=${System.currentTimeMillis()}") },
            )
        val thread =
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                loop.run()
            }, "ratamatap-mic")

        fun shutDown() {
            loop.requestStop()
            thread.interrupt()
            thread.join(JOIN_TIMEOUT_MILLIS)
            if (thread.isAlive) Log.e(TAG, "Capture didn't stop within timeout; release remains worker-owned")
        }

        fun stats() =
            MicStats(
                running = thread.isAlive,
                source = input.sourceName,
                device = input.deviceName,
                sampleRate = input.sampleRate,
                floatSamples = input.floatSamples,
                bufferFrames = input.bufferFrames,
                blockFrames = input.blockFrames,
                disabledEffects = input.disabledEffects,
                framesCaptured = loop.framesCaptured,
                onsets = detector.onsetCount,
                quietOnsets = detector.quietOnsetCount,
                droppedHits = queue.dropped,
                noiseFloorDb = detector.noiseFloorDb,
                levelDb = detector.blockPeakDb,
                clock = clock.stats(),
                readError = loop.readError,
            )
    }

    private companion object {
        const val TAG = "MicCapture"
        const val JOIN_TIMEOUT_MILLIS = 1_000L
    }
}

data class MicStats(
    val running: Boolean = false,
    /** The audio source in use: UNPROCESSED, VOICE_RECOGNITION or MIC. */
    val source: String? = null,
    /** The input device routed to, if known. */
    val device: String? = null,
    val sampleRate: Int = 0,
    /** Float capture; otherwise 16-bit, converted. */
    val floatSamples: Boolean = false,
    val bufferFrames: Int = 0,
    val blockFrames: Int = 0,
    /** Voice processing that was attached to the session and switched off. */
    val disabledEffects: List<String> = emptyList(),
    val framesCaptured: Long = 0,
    val onsets: Long = 0,
    /** Onsets ignored for being far quieter than the playing: room ticks, whine. */
    val quietOnsets: Long = 0,
    val droppedHits: Long = 0,
    val noiseFloorDb: Float = Float.NaN,
    /** The loudest moment of the latest block, dBFS: a level meter. */
    val levelDb: Float = Float.NEGATIVE_INFINITY,
    val clock: InputClockStats = InputClockStats(),
    val readError: Int = 0,
)

/** [PcmInput] on an Android [AudioRecord], mono, float when the device allows it. */
internal class AudioRecordInput private constructor(
    private val record: AudioRecord,
    override val sampleRate: Int,
    val sourceName: String,
    val floatSamples: Boolean,
    val bufferFrames: Int,
    val blockFrames: Int,
    private val effects: List<AudioEffect>,
    val disabledEffects: List<String>,
) : PcmInput {
    private val shorts = if (floatSamples) ShortArray(0) else ShortArray(blockFrames)
    private val androidTimestamp = AudioTimestamp()

    val deviceName: String? get() = runCatching { record.routedDevice?.let(::describe) }.getOrNull()

    override fun startRecording() = record.startRecording()

    override fun read(
        buffer: FloatArray,
        frames: Int,
    ): Int {
        if (floatSamples) return record.read(buffer, 0, frames, AudioRecord.READ_BLOCKING)
        val n = record.read(shorts, 0, frames, AudioRecord.READ_BLOCKING)
        for (i in 0 until n) buffer[i] = shorts[i] / SHORT_SCALE
        return n
    }

    override fun timestamp(out: InputTimestamp): Boolean {
        if (record.getTimestamp(androidTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) != AudioRecord.SUCCESS) {
            return false
        }
        out.framePosition = androidTimestamp.framePosition
        out.nanoTime = androidTimestamp.nanoTime
        return true
    }

    override fun stop() = record.stop()

    override fun release() {
        for (effect in effects) runCatching { effect.release() }
        record.release()
    }

    companion object {
        private const val TAG = "MicCapture"
        private const val SHORT_SCALE = 32_768f
        private const val BLOCK_MILLIS = 4
        private const val BUFFER_MILLIS = 100

        /** Opens the rawest available source at [sampleRate], or returns null if none opens. */
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        fun open(
            audioManager: AudioManager,
            sampleRate: Int,
        ): AudioRecordInput? {
            val unprocessed =
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val sources =
                buildList {
                    if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
                    add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
                    add(MediaRecorder.AudioSource.MIC to "MIC")
                }
            val builtInMic =
                audioManager
                    .getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            for ((source, name) in sources) {
                for (float in listOf(true, false)) {
                    val input = tryOpen(source, name, float, sampleRate, builtInMic)
                    if (input != null) return input
                }
            }
            Log.e(TAG, "No microphone input could be opened")
            return null
        }

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        private fun tryOpen(
            source: Int,
            sourceName: String,
            float: Boolean,
            sampleRate: Int,
            device: AudioDeviceInfo?,
        ): AudioRecordInput? {
            val encoding = if (float) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
            val bytesPerFrame = if (float) Float.SIZE_BYTES else Short.SIZE_BYTES
            val minBytes = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, encoding)
            if (minBytes <= 0) return null
            val bufferFrames = maxOf(minBytes / bytesPerFrame * 2, sampleRate * BUFFER_MILLIS / 1000)
            val record =
                try {
                    AudioRecord
                        .Builder()
                        .setAudioSource(source)
                        .setAudioFormat(
                            AudioFormat
                                .Builder()
                                .setEncoding(encoding)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build(),
                        ).setBufferSizeInBytes(bufferFrames * bytesPerFrame)
                        .build()
                } catch (error: Exception) {
                    // UnsupportedOperationException, IllegalArgumentException…
                    Log.i(TAG, "$sourceName float=$float unavailable: $error")
                    return null
                }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return null
            }
            if (device != null) record.setPreferredDevice(device)
            val effects = mutableListOf<AudioEffect>()
            val disabled = mutableListOf<String>()

            fun disable(
                name: String,
                create: () -> AudioEffect?,
            ) {
                val effect = runCatching(create).getOrNull() ?: return
                effects += effect
                if (effect.enabled) {
                    runCatching { effect.enabled = false }
                    disabled += name
                }
            }
            val session = record.audioSessionId
            if (AutomaticGainControl.isAvailable()) disable("AGC") { AutomaticGainControl.create(session) }
            if (NoiseSuppressor.isAvailable()) disable("NS") { NoiseSuppressor.create(session) }
            if (AcousticEchoCanceler.isAvailable()) disable("AEC") { AcousticEchoCanceler.create(session) }
            val blockFrames = sampleRate * BLOCK_MILLIS / 1000
            Log.i(
                TAG,
                "opened source=$sourceName float=$float rate=$sampleRate bufferFrames=$bufferFrames " +
                    "blockFrames=$blockFrames disabledEffects=$disabled",
            )
            return AudioRecordInput(
                record,
                sampleRate,
                sourceName,
                float,
                bufferFrames,
                blockFrames,
                effects,
                disabled,
            )
        }

        private fun describe(device: AudioDeviceInfo): String {
            val type =
                when (device.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in mic"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset mic"
                    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset mic"
                    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO mic"
                    else -> "type ${device.type}"
                }
            return "$type (${device.productName})"
        }
    }
}
