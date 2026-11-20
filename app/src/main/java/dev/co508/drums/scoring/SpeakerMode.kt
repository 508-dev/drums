package dev.co508.drums.scoring

import dev.co508.drums.audio.OutputRoute
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.hits.OnsetDetector
import dev.co508.drums.playback.Transport

/**
 * Speaker mode (DECISIONS.md, "Speaker Mode"): while a scored run plays
 * through the phone's speaker, the mic hears the app as loudly as the player.
 * So the snare is muted and the metronome switches to a pure low click the
 * onset detector doesn't hear. It applies by itself on the speaker route and
 * never with headphones. Main thread only.
 */
class SpeakerMode(
    private val transport: Transport,
) {
    private val click = PlaceholderSounds.speakerClick(transport.sampleRate, accented = false)
    private val accent = PlaceholderSounds.speakerClick(transport.sampleRate, accented = true)

    var isActive = false
        private set

    /** Enters speaker mode if [route] is the phone speaker; otherwise makes sure it's off. */
    fun applyFor(route: OutputRoute?): Boolean {
        if (route?.kind == OutputRoute.Kind.SPEAKER) enter() else exit()
        return isActive
    }

    /** The snare volume to use: none while in speaker mode. */
    fun instrumentVolume(requested: Float): Float = if (isActive) 0f else requested

    fun enter() {
        isActive = true
        transport.instrumentVolume = 0f
        transport.setClickSounds(click, accent)
    }

    /** Leaves speaker mode, restoring the metronome; the caller restores the snare's own volume. */
    fun exit() {
        if (!isActive) return
        isActive = false
        transport.resetClickSounds()
    }

    companion object {
        /**
         * What the onset detector notches out in speaker mode: the click's two
         * pitches and their 2nd and 3rd harmonics, which a straining phone
         * speaker adds.
         */
        val NOTCHES: List<Double> =
            listOf(PlaceholderSounds.SPEAKER_CLICK_HZ, PlaceholderSounds.SPEAKER_ACCENT_CLICK_HZ).flatMap { f ->
                listOf(f, 2 * f, 3 * f)
            }

        /**
         * The onset detector in speaker mode. The click sits centimetres from
         * the mic, tens of dB louder than a pad across the room, so the usual
         * 24 dB/octave high-pass leaves too much of it: twice as steep, plus
         * wide notches.
         */
        val DETECTOR = OnsetDetector.Config(highPassSections = 4, notchHz = NOTCHES, notchQ = 2.0)

        /** True when scoring on [route] runs in speaker mode. */
        fun appliesTo(route: OutputRoute?): Boolean = route?.kind == OutputRoute.Kind.SPEAKER
    }
}
