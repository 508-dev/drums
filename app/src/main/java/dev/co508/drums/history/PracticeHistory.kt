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

/** Application-owned writes survive navigation; failed entries stay pending for an explicit retry. */
class PracticeHistory(
    private val dao: PracticeDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val queue = Channel<PracticeRecord?>(Channel.UNLIMITED)
    private val failure = MutableStateFlow<String?>(null)
    val writeError = failure.asStateFlow()
    val data = combine(dao.sessions(), dao.bestTempos(), ::HistoryData)

    init {
        scope.launch {
            val pending = linkedMapOf<String, PracticeRecord>()
            for (record in queue) {
                if (record != null) pending[record.id] = record
                try {
                    val iterator = pending.iterator()
                    while (iterator.hasNext()) {
                        dao.insert(iterator.next().value)
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

    fun retry() {
        queue.trySend(null)
    }
}
