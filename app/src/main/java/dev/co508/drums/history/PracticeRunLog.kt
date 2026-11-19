package dev.co508.drums.history

import java.util.UUID
import kotlin.math.roundToInt

/** Main-thread run bookkeeping, independent of Compose and the mixer thread. */
class PracticeRunLog(
    private val save: (PracticeRecord) -> Unit,
    private val wallTimeMillis: () -> Long = System::currentTimeMillis,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var active: PracticeRecord? = null
    private var startedNanos = 0L

    fun start(
        identity: PracticeIdentity,
        tempo: Double,
        trainerUsed: Boolean,
    ) {
        finish()
        val bpm = tempo.roundToInt()
        active = PracticeRecord(newId(), wallTimeMillis(), identity.packId, identity.lessonId, 0, bpm, bpm, trainerUsed)
        startedNanos = monotonicNanos()
    }

    fun observeTempo(tempo: Double) {
        if (!tempo.isFinite() || tempo <= 0) return
        val record = active ?: return
        val bpm = tempo.roundToInt()
        if (bpm > record.highestTempo) active = record.copy(highestTempo = bpm)
    }

    fun finish() {
        val record = active ?: return
        active = null
        save(record.copy(durationMillis = ((monotonicNanos() - startedNanos) / 1_000_000).coerceAtLeast(0)))
    }
}
