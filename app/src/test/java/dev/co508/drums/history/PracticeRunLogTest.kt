package dev.co508.drums.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PracticeRunLogTest {
    private var wall = 1_000L
    private var nanos = 0L
    private val rows = mutableListOf<PracticeRecord>()
    private val log = PracticeRunLog(rows::add, { wall }, { nanos }, { "id-${rows.size}" })

    @Test fun logsMonotonicDurationAndMaximumNotFinalTempo() {
        log.start(PracticeIdentity("pack", "lesson"), 80.0, true)
        nanos = 90_000_000_000
        wall = -99_000 // User changing the date must not affect elapsed practice time.
        log.observeTempo(120.0)
        log.observeTempo(90.0)
        log.observeTempo(Double.NaN)
        log.observeTempo(0.0)
        log.finish()
        log.finish()
        assertEquals(listOf(PracticeRecord("id-0", 1_000, "pack", "lesson", 90_000, 80, 120, true)), rows)
    }

    @Test fun noRunMeansNoEntryAndReplacementClosesOldRun() {
        log.finish()
        assertTrue(rows.isEmpty())
        log.start(PracticeIdentity("a", "same-id"), 60.0, false)
        nanos = 1_000_000
        log.start(PracticeIdentity("b", "same-id"), 70.0, false)
        nanos = 2_000_000
        log.finish()
        assertEquals(listOf("a", "b"), rows.map { it.packId })
        assertEquals(listOf(1L, 1L), rows.map { it.durationMillis })
        assertEquals(listOf(60, 70), rows.map { it.highestTempo })
    }
}
