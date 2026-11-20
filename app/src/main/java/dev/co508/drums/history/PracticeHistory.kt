package dev.co508.drums.history

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Application-owned writes survive navigation; failed entries stay pending for
 * an explicit retry. Writes keep their order, so a session's score is never
 * inserted before the session it belongs to.
 */
class PracticeHistory(
    private val dao: PracticeDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val queue = Channel<Any?>(Channel.UNLIMITED)
    private val failure = MutableStateFlow<String?>(null)
    val writeError = failure.asStateFlow()
    val data =
        combine(dao.sessions(), dao.bestTempos(), dao.scores()) { sessions, bests, scores ->
            HistoryData(sessions, bests, scores.associateBy { it.sessionId })
        }

    init {
        scope.launch {
            val pending = linkedMapOf<String, Any>()
            for (entry in queue) {
                when (entry) {
                    is PracticeRecord -> pending["session:${entry.id}"] = entry
                    is SessionScore -> pending["score:${entry.sessionId}"] = entry
                }
                try {
                    val iterator = pending.iterator()
                    while (iterator.hasNext()) {
                        when (val next = iterator.next().value) {
                            is PracticeRecord -> dao.insert(next)
                            is SessionScore -> dao.insertScore(next)
                        }
                        iterator.remove()
                    }
                    failure.value = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("PracticeHistory", "Couldn't save practice log", error)
                    failure.value = "Couldn't save practice history. Retry before closing the app."
                }
            }
        }
    }

    fun record(record: PracticeRecord) {
        queue.trySend(record)
    }

    /** A scored session's results; recorded after its session. Any thread. */
    fun recordScore(score: SessionScore) {
        queue.trySend(score)
    }

    fun retry() {
        queue.trySend(null)
    }
}
