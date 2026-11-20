package dev.co508.drums.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import dev.co508.drums.audio.AudioEngine
import dev.co508.drums.history.PracticeIdentity
import dev.co508.drums.history.PracticeRunLog

/**
 * Ties the engine, transport and clock to the system: the output stream's
 * lifetime, audio focus and headphone unplugging. See DECISIONS.md, "Playback
 * Yields To Calls And Unplugged Headphones".
 *
 * [open] starts the output stream (silent until [play]) and [close] releases
 * it, following the screen that plays. Keeping the stream open between plays
 * means play starts on an already-running clock and stop lets the last notes
 * ring out. [play] takes audio focus; [stop], or losing focus, gives it back.
 *
 * Main thread only.
 */
class PlaybackSession(
    private val context: Context,
    val engine: AudioEngine,
    val transport: Transport,
    val clock: AudioClock,
    private val runLog: PracticeRunLog? = null,
) {
    enum class StopReason {
        USER,
        SCREEN_LEFT,
        FOCUS_LOSS,
        FOCUS_TRANSIENT,
        HEADPHONES_UNPLUGGED,
        TRANSPORT_FINISHED,
        ENGINE_ERROR,
    }

    /**
     * Called on the main thread when the system stops playback: focus lost (a
     * call, another player) or headphones unplugged.
     */
    var onInterrupted: ((StopReason) -> Unit)? = null

    /** Whether this session holds audio focus and is playing (or has finished but not been stopped). */
    var isPlaying = false
        private set
    private var loggedSettings: PlaySettings? = null

    /** The practice-log session of the current run, if it is logged: scores attach to it. */
    val runId: String? get() = runLog?.activeId
    private var previousStartGeneration = 0L

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val focusRequest =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            // Notification pings may briefly auto-duck us, but must not end practice.
            .setWillPauseWhenDucked(false)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
            .build()

    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action ==
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY
                ) {
                    interrupt(StopReason.HEADPHONES_UNPLUGGED)
                }
            }
        }

    /** Starts the output stream, silent until [play]. Does nothing if already open. */
    fun open() {
        // An explicit Play/open after exhausted retries starts a fresh attempt budget.
        if (engine.isRunning && engine.stats().writeError < 0) engine.stop()
        if (engine.isRunning) return
        clock.reset()
        engine.setEventSource(transport)
        engine.start()
    }

    /** Stops playback and releases the output stream. */
    fun close() {
        stop(StopReason.SCREEN_LEFT)
        engine.stop()
    }

    /**
     * Takes audio focus and starts [settings] from the count-in. Returns false,
     * without playing, if focus is refused (for example during a call).
     */
    fun play(
        settings: PlaySettings,
        identity: PracticeIdentity? = null,
    ): Boolean {
        if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        if (!isPlaying) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            isPlaying = true
        }
        open()
        if (isPlaying) updatePracticeLog()
        runLog?.finish()
        if (identity != null) runLog?.start(identity, settings.beatsPerMinute, settings.trainer != null)
        loggedSettings = settings
        previousStartGeneration = transport.startGeneration
        transport.start(settings)
        return true
    }

    /**
     * Stops the transport and gives up audio focus. Also call this when the
     * transport reaches [Transport.State.FINISHED]. The stream stays open.
     */
    fun stop(reason: StopReason = StopReason.USER) {
        Log.i(TAG, "stop reason=$reason playing=$isPlaying wallTimeMillis=${System.currentTimeMillis()}")
        if (!isPlaying) return
        updatePracticeLog()
        runLog?.finish()
        loggedSettings = null
        isPlaying = false
        transport.stop()
        context.unregisterReceiver(noisyReceiver)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onFocusChange(focusChange: Int) {
        Log.i(TAG, "focusChange=$focusChange wallTimeMillis=${System.currentTimeMillis()}")
        if (stopsPlayback(focusChange)) {
            interrupt(
                if (focusChange ==
                    AudioManager.AUDIOFOCUS_LOSS
                ) {
                    StopReason.FOCUS_LOSS
                } else {
                    StopReason.FOCUS_TRANSIENT
                },
            )
        }
    }

    /** Transient errors recover in the mixer; only exhausted recovery interrupts the session. */
    fun checkOutput() {
        if (isPlaying && engine.stats().writeError < 0) interrupt(StopReason.ENGINE_ERROR)
    }

    /** Uses the raw heard clock, never the user-adjusted visual offset or rendered-ahead tempo. */
    fun updatePracticeLog() {
        if (runLog == null ||
            !isPlaying ||
            transport.startGeneration == previousStartGeneration ||
            transport.settings !== loggedSettings
        ) {
            return
        }
        val frame = clock.frameAt(System.nanoTime())
        if (frame.isFinite()) runLog.observeTempo(transport.highestBeatsPerMinuteThrough(frame))
    }

    private fun interrupt(reason: StopReason) {
        if (!isPlaying) return
        stop(reason)
        onInterrupted?.invoke(reason)
    }

    companion object {
        private const val TAG = "PlaybackSession"

        /** Calls/other players stop playback; notification ducking doesn't. Gaining focus never resumes. */
        fun stopsPlayback(focusChange: Int): Boolean =
            focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
    }
}
