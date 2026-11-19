package dev.co508.drums.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTempoTest {
    @Test fun maximumIgnoresFutureRenderedTempoAndSurvivesTempoDropAndFinish() {
        val map = TempoMap()
        assertTrue(map.highestRateThrough(0.0).isNaN())
        map.begin(100.0, 0.0, 1.0)
        map.changeRateAtFrame(200.0, 3.0)
        map.changeRateAtFrame(300.0, 2.0)
        map.changeRateAtFrame(400.0, 0.0)
        assertEquals(0.0, map.highestRateThrough(99.0), 0.0)
        assertEquals(1.0, map.highestRateThrough(199.0), 0.0)
        assertEquals(3.0, map.highestRateThrough(200.0), 0.0)
        assertEquals(3.0, map.highestRateThrough(500.0), 0.0)
        map.begin(0.0, 0.0, 0.5)
        assertEquals(0.5, map.highestRateThrough(100.0), 0.0)
    }

    @Test fun maximumHandlesRingWrapWithoutReadingOlderEpochs() {
        val map = TempoMap(capacity = 4)
        map.begin(0.0, 0.0, 99.0)
        for (i in 1..10) map.changeRateAtFrame(i.toDouble(), i.toDouble())
        assertEquals(9.0, map.highestRateThrough(9.0), 0.0)
        map.begin(0.0, 0.0, 1.0)
        assertEquals(1.0, map.highestRateThrough(100.0), 0.0)
    }
}
