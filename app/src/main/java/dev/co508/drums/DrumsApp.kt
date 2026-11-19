package dev.co508.drums

import android.app.Application
import dev.co508.drums.audio.AudioTrackEngine
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.playback.Transport

class DrumsApp : Application() {
    /**
     * The app's one audio output, transport and clock. Created on first use;
     * the screen that plays opens and closes its stream.
     */
    val playback: PlaybackSession by lazy {
        val engine = AudioTrackEngine(this)
        val rate = engine.sampleRate
        val transport =
            Transport(
                sampleRate = rate,
                click = PlaceholderSounds.click(rate, accented = false),
                accentClick = PlaceholderSounds.click(rate, accented = true),
            )
        PlaybackSession(this, engine, transport, AudioClock(engine, rate))
    }
}
