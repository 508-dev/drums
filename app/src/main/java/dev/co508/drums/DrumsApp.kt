package dev.co508.drums

import android.app.Application
import dev.co508.drums.audio.AudioTrackEngine
import dev.co508.drums.audio.PlaceholderSounds
import dev.co508.drums.audio.SampledSounds
import dev.co508.drums.history.PracticeDatabase
import dev.co508.drums.history.PracticeHistory
import dev.co508.drums.history.PracticeRunLog
import dev.co508.drums.hits.HitTimeline
import dev.co508.drums.hits.MicCapture
import dev.co508.drums.lesson.AssetPackSource
import dev.co508.drums.lesson.PackLoader
import dev.co508.drums.lesson.PackResult
import dev.co508.drums.musicxml.MusicXmlImporter
import dev.co508.drums.playback.AudioClock
import dev.co508.drums.playback.DrumKit
import dev.co508.drums.playback.PlaybackSession
import dev.co508.drums.playback.Transport
import dev.co508.drums.scoring.ScoringSession
import dev.co508.drums.scoring.SpeakerMode
import dev.co508.drums.settings.PracticeSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DrumsApp : Application() {
    val settings: PracticeSettingsStore by lazy { PracticeSettingsStore(this) }
    val history: PracticeHistory by lazy { PracticeHistory(PracticeDatabase.open(this).practice()) }

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
        PlaybackSession(this, engine, transport, AudioClock(engine, rate), PracticeRunLog(history::record))
    }

    /**
     * The microphone, for hit detection (M5). Created on first use; it opens
     * only when scoring asks for it, after the RECORD_AUDIO permission.
     */
    val mic: MicCapture by lazy { MicCapture(this, playback.engine.sampleRate) }

    /** Places mic hits on the playback clock and the transport's musical timeline. */
    val hitTimeline: HitTimeline by lazy { HitTimeline(playback.clock, playback.transport.tempoMap) }

    /** Application-lifetime work: scoring's background matching. */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** Scored runs (M5): the mic, matched against the playing lesson off the audio threads. */
    val scoring: ScoringSession by lazy {
        ScoringSession(mic, hitTimeline, playback.clock, playback.transport, appScope)
    }

    /** Speaker mode for scored runs on the phone speaker: snare muted, a click the mic ignores. */
    val speakerMode: SpeakerMode by lazy { SpeakerMode(playback.transport) }

    /** The sounds lessons play with, loaded at the output's sample rate. */
    val kit: DrumKit by lazy {
        val rate = playback.engine.sampleRate
        val ghost = SampledSounds.snare("ghost", rate, assets::open)
        DrumKit(
            snare = SampledSounds.snare("normal", rate, assets::open),
            buzz = SampledSounds.buzz(ghost, rate),
            ghost = ghost,
            accent = SampledSounds.snare("accent", rate, assets::open),
        )
    }

    /** The built-in pack, loaded (and strictly checked) on first use. Call off the main thread. */
    val builtInPack: PackResult by lazy {
        PackLoader(MusicXmlImporter()).load(AssetPackSource(assets, PackLoader.BUILT_IN_FOLDER), strict = true)
    }
}
