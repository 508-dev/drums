package dev.co508.drums.audio

/**
 * Output buffer sizes, in frames. Timing beats latency (DECISIONS.md, "Kotlin
 * AudioTrack Mixer, Oboe Only If Needed"): the buffer is sized to ride out GC
 * and scheduling hiccups, not to be the smallest the device allows. The extra
 * latency is invisible because the visuals follow the audio clock.
 */
internal object BufferSizing {
    /** The effective buffer size to start with: at least [targetMillis] and [MIN_BURSTS] bursts, in whole bursts. */
    fun initialFrames(
        sampleRate: Int,
        framesPerBurst: Int,
        targetMillis: Int,
    ): Int {
        val target = maxOf(millisToFrames(sampleRate, targetMillis), MIN_BURSTS * framesPerBurst)
        return roundUpToBursts(target, framesPerBurst)
    }

    /** The buffer capacity to request: room for the initial size to grow to [maxMillis] after underruns. */
    fun capacityFrames(
        sampleRate: Int,
        framesPerBurst: Int,
        initialFrames: Int,
        maxMillis: Int,
        minBufferFrames: Int,
    ): Int {
        val wanted = maxOf(initialFrames, millisToFrames(sampleRate, maxMillis), minBufferFrames)
        return roundUpToBursts(wanted, framesPerBurst)
    }

    /** The buffer size to use after an underrun: one burst more, up to [capacityFrames]. */
    fun grownFrames(
        currentFrames: Int,
        framesPerBurst: Int,
        capacityFrames: Int,
    ): Int = minOf(currentFrames + framesPerBurst, capacityFrames)

    private fun millisToFrames(
        sampleRate: Int,
        millis: Int,
    ): Int = ((sampleRate.toLong() * millis + 999) / 1000).toInt()

    private fun roundUpToBursts(
        frames: Int,
        framesPerBurst: Int,
    ): Int = (frames + framesPerBurst - 1) / framesPerBurst * framesPerBurst

    const val MIN_BURSTS = 4
}
