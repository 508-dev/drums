package dev.co508.drums.audio

/** Small AudioTrack boundary so write failures, short writes and replacement are JVM-testable. */
internal interface PcmOutputTrack {
    val capacityFrames: Int
    val bufferSizeFrames: Int
    val lowLatencyGranted: Boolean
    val outputDevice: String?
    val outputRoute: OutputRoute? get() = null
    val underrunCount: Int

    fun resizeBuffer(frames: Int)

    fun write(
        samples: FloatArray,
        offset: Int,
        count: Int,
        blocking: Boolean,
    ): Int

    fun play()

    fun timestamp(out: OutputTimestamp): Boolean

    /** May run concurrently with write to unblock shutdown; never releases the track. */
    fun pauseAndFlush()

    fun release()
}
