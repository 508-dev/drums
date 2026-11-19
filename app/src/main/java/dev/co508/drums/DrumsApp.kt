package dev.co508.drums

import android.app.Application
import dev.co508.drums.audio.AudioTrackEngine
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.lesson.AssetPackSource
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.MusicXmlImporter
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.DrumKit
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

    /** The sounds lessons play with, at the output's sample rate. Placeholders until #19. */
    val kit: DrumKit by lazy {
        val rate = playback.engine.sampleRate
        DrumKit(PlaceholderSounds.snare(rate), PlaceholderSounds.buzz(rate))
    }

    /** The built-in pack, loaded (and strictly checked) on first use. Call off the main thread. */
    val builtInPack: PackResult by lazy {
        PackLoader(MusicXmlImporter()).load(AssetPackSource(assets, PackLoader.BUILT_IN_FOLDER), strict = true)
    }
}
