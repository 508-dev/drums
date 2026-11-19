package dev.co508.drums.playback

import dev.co508.drums.audio.Sound
import dev.co508.drums.lesson.Expansion
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.musicxml.ImportResult
import dev.co508.drums.musicxml.fixture
import dev.co508.drums.musicxml.testImporter
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonPatternTest {
    private val kit = DrumKit(Sound("snare", floatArrayOf(1f)), Sound("buzz", floatArrayOf(1f)))

    private fun pattern(fixtureName: String): Pattern {
        val notation = (testImporter().read(fixture(fixtureName), strict = true) as ImportResult.Success).notation
        return LessonPattern.build(Expansion.expand(notation), kit)
    }

    @Test
    fun aLessonBecomesAPattern() {
        val p = pattern("sticking-and-ornaments.musicxml")
        assertEquals(12.0, p.lengthQuarters, 0.0)
        assertEquals(1.0, p.beatQuarters, 0.0)
        assertEquals(4, p.countInBeats)
        assertEquals(12, p.clicks.size)
        assertEquals(listOf(true, false, false, false), p.clicks.take(4).map { it.accent })
        val first = p.events.first()
        assertEquals(LessonPattern.gainOf(dev.co508.drums.lesson.StrokeDynamic.ACCENT), first.gain)
        assertEquals(0.3f, first.pan) // Right hand.
        val flamGrace = p.events.first { it.leadLevel == 1 }
        assertEquals(2.0, flamGrace.position, 0.0)
        assertEquals(-0.3f, flamGrace.pan)
        val buzz = p.events.first { it.sound.name == "buzz" }
        assertEquals(2.0, buzz.durationQuarters, 0.0)
        assertEquals(2, p.maxLeadLevel)
    }

    @Test
    fun compoundTimeCountsInDottedQuarters() {
        val p = pattern("compound-and-repeats.musicxml")
        assertEquals(1.5, p.beatQuarters, 0.0)
        assertEquals(2, p.countInBeats)
        // 6/8 bars: clicks on each dotted quarter, accented on each bar.
        assertEquals(listOf(0.0, 1.5, 3.0, 4.5), p.clicks.take(4).map { it.position })
        assertEquals(listOf(true, false, true, false), p.clicks.take(4).map { it.accent })
    }

    @Test
    fun dynamicsChooseTheRecordedLayerWithoutChangingGains() {
        val notation =
            (
                testImporter().read(
                    fixture("sticking-and-ornaments.musicxml"),
                    strict = true,
                ) as ImportResult.Success
            ).notation
        val timeline = Expansion.expand(notation)
        val layered = DrumKit(kit.snare, kit.buzz, Sound("ghost", floatArrayOf(1f)), Sound("accent", floatArrayOf(1f)))
        val events = LessonPattern.build(timeline, layered).events
        for (stroke in timeline.strokes) {
            val expected =
                if (stroke.buzzLength != null) {
                    "buzz"
                } else {
                    when (stroke.dynamic) {
                        StrokeDynamic.ACCENT -> "accent"
                        StrokeDynamic.NORMAL -> "snare"
                        StrokeDynamic.GHOST, StrokeDynamic.GRACE -> "ghost"
                    }
                }
            val matches =
                events.filter {
                    it.position == stroke.position.toDouble() && it.leadLevel == stroke.graceLead
                }
            org.junit.Assert.assertTrue(
                matches.any {
                    it.sound.name == expected &&
                        it.gain == LessonPattern.gainOf(stroke.dynamic)
                },
            )
        }
    }
}
