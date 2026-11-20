package dev.co508.drums.audio

import java.util.concurrent.atomic.AtomicReference

/** One worker owns track creation/write/release. Readers never use a released track. */
internal class RecoveringAudioOutput(
    private val sampleRate: Int,
    private val framesPerBurst: Int,
    private val maxVoices: Int,
    private val createTrack: () -> PcmOutputTrack,
    private val source: () -> EventSource?,
    private val generationChanged: () -> Unit,
    private val delayMillis: (Long) -> Unit = Thread::sleep,
    private val log: (String) -> Unit = {},
) {
    private val trackLock = Any()
    private var track: PcmOutputTrack? = null
    private var timestampsReady = false
    private val restart = AtomicReference<String?>(null)

    @Volatile private var stopped = false

    @Volatile private var mixer = Mixer(maxVoices)

    @Volatile private var capacity = 0

    @Volatile private var bufferSize = 0

    @Volatile private var fast = false

    @Volatile private var device: String? = null

    @Volatile private var route: OutputRoute? = null

    @Volatile private var underruns = 0

    @Volatile var recovering = false
        private set

    @Volatile var writeError = 0
        private set

    @Volatile var lastWriteError = 0
        private set

    @Volatile var recoveryAttempts = 0
        private set

    @Volatile var recoveries = 0
        private set

    @Volatile var lastRecoveryReason: String? = null
        private set

    fun requestRestart(reason: String) {
        restart.compareAndSet(null, reason)
    }

    fun requestStop() {
        stopped = true
        synchronized(trackLock) {
            timestampsReady = false
            try {
                track?.pauseAndFlush()
            } catch (_: RuntimeException) {
                // Already dead. Worker still releases it.
            }
        }
    }

    fun timestamp(out: OutputTimestamp): Boolean =
        synchronized(trackLock) {
            if (!timestampsReady) return false
            try {
                track?.timestamp(out) == true
            } catch (_: RuntimeException) {
                false
            }
        }

    /** Blocking loop; caller supplies the urgent-audio worker thread. */
    fun run() {
        var consecutiveFailures = 0
        var cause: String? = null
        try {
            while (!stopped) {
                if (cause != null) {
                    recovering = true
                    lastRecoveryReason = cause
                    if (consecutiveFailures >= MAX_RECOVERY_ATTEMPTS) {
                        writeError = if (lastWriteError < 0) lastWriteError else TRACK_EXCEPTION
                        log("recovery exhausted reason=$cause error=$writeError")
                        return
                    }
                    recoveryAttempts++
                    consecutiveFailures++
                    log("recovering attempt=$recoveryAttempts reason=$cause")
                    delayMillis(RETRY_DELAY_MILLIS * consecutiveFailures)
                    if (stopped) return
                }
                generationChanged()
                mixer = Mixer(maxVoices)
                restart.set(null) // A replacement also satisfies route/restart requests queued for the dead track.
                try {
                    val current = createTrack()
                    synchronized(trackLock) {
                        track = current
                        timestampsReady = false
                    }
                    capacity = current.capacityFrames
                    bufferSize = current.bufferSizeFrames
                    fast = current.lowLatencyGranted
                    device = current.outputDevice
                    route = current.outputRoute
                    val block = FloatArray(framesPerBurst * CHANNELS)
                    var offset = block.size
                    var prefilled = 0
                    while (!stopped && prefilled + framesPerBurst <= bufferSize) {
                        mixer.render(block, framesPerBurst, source())
                        offset = current.write(block, 0, block.size, false)
                        if (offset < 0) throw WriteFailure(offset)
                        prefilled += offset / CHANNELS
                        if (offset < block.size) break
                    }
                    val baseUnderruns = current.underrunCount
                    synchronized(trackLock) {
                        if (stopped) return
                        current.play()
                        timestampsReady = true
                    }
                    if (cause != null) {
                        recoveries++
                        log("recovered count=$recoveries reason=$cause")
                    }
                    recovering = false
                    var healthyFrames = 0L
                    var zeroWrites = 0
                    var previousUnderruns = 0
                    while (!stopped) {
                        restart.getAndSet(null)?.let { throw RouteChanged(it) }
                        if (offset == block.size) {
                            mixer.render(block, framesPerBurst, source())
                            offset = 0
                        }
                        val written = current.write(block, offset, block.size - offset, true)
                        if (written < 0) throw WriteFailure(written)
                        offset += written
                        healthyFrames += written / CHANNELS
                        if (healthyFrames >= sampleRate) consecutiveFailures = 0
                        if (written == 0) {
                            if (++zeroWrites >= MAX_ZERO_WRITES) throw WriteFailure(TRACK_EXCEPTION)
                            delayMillis(1)
                        } else {
                            zeroWrites = 0
                        }
                        val count = current.underrunCount - baseUnderruns
                        if (count > previousUnderruns) {
                            underruns += count - previousUnderruns
                            previousUnderruns = count
                            current.resizeBuffer(BufferSizing.grownFrames(bufferSize, framesPerBurst, capacity))
                            bufferSize = current.bufferSizeFrames
                        }
                    }
                } catch (error: WriteFailure) {
                    lastWriteError = error.code
                    cause = "write ${error.code}"
                } catch (error: RouteChanged) {
                    cause = error.reason
                } catch (error: RuntimeException) {
                    lastWriteError = TRACK_EXCEPTION
                    cause = "track ${error.javaClass.simpleName}"
                } finally {
                    synchronized(trackLock) {
                        timestampsReady = false
                        generationChanged()
                        val old = track
                        track = null
                        try {
                            old?.release()
                        } catch (_: RuntimeException) {
                            // Dead native object.
                        }
                    }
                }
            }
        } catch (_: InterruptedException) {
            if (!stopped) writeError = TRACK_EXCEPTION
        } finally {
            recovering = false
        }
    }

    fun stats(): AudioEngineStats {
        synchronized(trackLock) {
            try {
                track?.outputDevice?.let { device = it }
                track?.outputRoute?.let { route = it }
            } catch (
                _: RuntimeException,
            ) {
                // Recovery is worker-owned.
            }
        }
        return AudioEngineStats(
            sampleRate,
            framesPerBurst,
            bufferSize,
            capacity,
            fast,
            device,
            underruns,
            mixer.framesRendered,
            mixer.stats(),
            writeError,
            recovering = recovering,
            recoveryAttempts = recoveryAttempts,
            recoveries = recoveries,
            lastWriteError = lastWriteError,
            lastRecoveryReason = lastRecoveryReason,
            outputRoute = route,
        )
    }

    private class WriteFailure(
        val code: Int,
    ) : RuntimeException()

    private class RouteChanged(
        val reason: String,
    ) : RuntimeException()

    private companion object {
        const val CHANNELS = 2
        const val MAX_RECOVERY_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 100L
        const val MAX_ZERO_WRITES = 250
        const val TRACK_EXCEPTION = -3
    }
}
