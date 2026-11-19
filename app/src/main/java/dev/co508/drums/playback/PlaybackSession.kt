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
import androidx.core.content.ContextCompat
import dev.co508.drums.audio.AudioEngine

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
) {
    /**
     * Called on the main thread when the system stops playback: focus lost (a
     * call, another player) or headphones unplugged.
     */
    var onInterrupted: (() -> Unit)? = null

    /** Whether this session holds audio focus and is playing (or has finished but not been stopped). */
    var isPlaying = false
        private set

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
            // Ask to be told about "may duck" losses instead of being ducked:
            // a quieter metronome is no use, so we stop.
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(::onFocusChange, Handler(Looper.getMainLooper()))
            .build()

    private val noisyReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
            }
        }

    /** Starts the output stream, silent until [play]. Does nothing if already open. */
    fun open() {
        if (engine.isRunning) return
        clock.reset()
        engine.setEventSource(transport)
        engine.start()
    }

    /** Stops playback and releases the output stream. */
    fun close() {
        stop()
        engine.stop()
    }

    /**
     * Takes audio focus and starts [settings] from the count-in. Returns false,
     * without playing, if focus is refused (for example during a call).
     */
    fun play(settings: PlaySettings): Boolean {
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
        transport.start(settings)
        return true
    }

    /**
     * Stops the transport and gives up audio focus. Also call this when the
     * transport reaches [Transport.State.FINISHED]. The stream stays open.
     */
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        transport.stop()
        context.unregisterReceiver(noisyReceiver)
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onFocusChange(focusChange: Int) {
        if (stopsPlayback(focusChange)) interrupt()
    }

    private fun interrupt() {
        if (!isPlaying) return
        stop()
        onInterrupted?.invoke()
    }

    companion object {
        /** Any focus loss stops playback; a metronome is never ducked. Regaining focus doesn't resume. */
        fun stopsPlayback(focusChange: Int): Boolean =
            focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
    }
}
