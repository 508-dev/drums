package dev.co508.drums.scoring

import dev.co508.drums.lesson.Hand
import kotlin.math.abs
import kotlin.math.floor

/** Musical position ↔ output frame, as the transport's tempo map gives them. */
interface TimeMap {
    fun frameAt(position: Double): Double

    fun positionAt(frame: Double): Double
}

/**
 * Matches detected hits to a lesson's strokes, loop after loop, and keeps
 * the marks and totals. See DECISIONS.md, "Matching Hits To Notes".
 *
 * Everything is in output frames on the shared clock: hits arrive already
 * mapped (and calibrated) through [dev.co508.drums.hits.HitTimeline], and
 * notes' times come from the [TimeMap]. [advance] is called with the latest
 * *settled* frame, the one up to which every hit has arrived; notes are
 * decided in order once their late window has passed it:
 *
 * - A **main stroke** takes the nearest unclaimed hit within its windows
 *   ([NoteWindows]); otherwise it is missed.
 * - Its **grace strokes** are optional. They take only separate onsets
 *   before the main stroke's hit, within their own early window. A flam or
 *   drag whose grace merged with its main stroke into one onset scores the
 *   main stroke alone; its grace is [NoteMark.Kind.GRACE_UNHEARD], not missed.
 * - A **buzz roll** is scored at its start, and absorbs the onsets of its
 *   own bounces until it ends.
 * - Any other hit is an **extra**, except hits during the count-in.
 *
 * Not thread-safe: one thread (the scoring session's) calls everything;
 * [snapshot] is an immutable copy for others.
 */
class HitMatcher(
    private val lesson: ScoredLesson,
    private val sampleRate: Int,
    private val strictness: Strictness,
    /** False for a lesson played once: there is no loop 1. */
    private val loops: Boolean = true,
) {
    private val pending = ArrayList<Double>()
    private var nextMain = 0L
    private val absorbers = ArrayList<DoubleArray>()
    private val marks = List(lesson.strokes.size) { ArrayDeque<NoteMark>() }
    private val extras = ArrayDeque<ExtraMark>()
    private var stats = ScoringStats()
    private var changed = true
    private var latest: ScoringSnapshot = ScoringSnapshot.EMPTY

    private val perMilli = sampleRate / 1000.0
    private val mainCount = lesson.mains.size

    /** A hit at output [frame], already calibrated. Hits may arrive slightly out of order. */
    fun addHit(frame: Double) {
        if (!frame.isFinite()) return
        val i = pending.binarySearch(frame).let { if (it < 0) -it - 1 else it }
        pending.add(i, frame)
    }

    /**
     * Decides every note whose windows end before [settledFrame], with grace
     * strokes [graceGapFrames] apart, and classifies the hits left behind.
     */
    fun advance(
        settledFrame: Double,
        map: TimeMap,
        graceGapFrames: Double,
        /** Hits from here on are never called extras (after a stop). */
        extrasBefore: Double = Double.POSITIVE_INFINITY,
    ) {
        if (mainCount == 0 || !settledFrame.isFinite()) return
        val start = map.frameAt(0.0)
        if (!start.isFinite()) return
        while (true) {
            val g = nextMain
            val e = frameOf(g, map)
            if (!e.isFinite()) break
            val windows = windowsOf(g, e, map)
            val late = windows.lateMillis * perMilli
            val earliest = minOf(earliestStart(g, e, windows, graceGapFrames, map), extrasBefore)
            flushExtras(earliest, start, map)
            if (e + late > settledFrame) break
            decide(g, e, windows, graceGapFrames, map)
            nextMain++
        }
    }

    /**
     * The run stopped at [stopFrame] (when the player heard it stop), and every
     * hit up to [settledFrame] has arrived. Notes before the stop are decided;
     * hits before it that matched nothing are extras; the rest is dropped.
     */
    fun finish(
        stopFrame: Double,
        settledFrame: Double,
        map: TimeMap,
        graceGapFrames: Double,
    ) {
        if (mainCount > 0 && stopFrame.isFinite()) {
            // Notes whose windows close by the stop, plus the last one cut short by it.
            advance(minOf(settledFrame, stopFrame + strictness.matchMillis * perMilli), map, graceGapFrames, stopFrame)
            val start = map.frameAt(0.0)
            if (start.isFinite()) flushExtras(stopFrame, start, map)
        }
        pending.clear()
    }

    /** An immutable copy of the marks and totals, cached until something changes. */
    fun snapshot(): ScoringSnapshot {
        if (changed) {
            latest = ScoringSnapshot(marks.map { it.toList() }, extras.toList(), stats)
            changed = false
        }
        return latest
    }

    private fun positionOf(g: Long): Double {
        val loop = Math.floorDiv(g, mainCount.toLong())
        val i = Math.floorMod(g, mainCount.toLong()).toInt()
        return loop * lesson.lengthQuarters + lesson.strokes[lesson.mains[i]].position
    }

    private fun frameOf(
        g: Long,
        map: TimeMap,
    ): Double = if (!loops && g >= mainCount) Double.POSITIVE_INFINITY else map.frameAt(positionOf(g))

    private fun windowsOf(
        g: Long,
        e: Double,
        map: TimeMap,
    ): NoteWindows {
        val before = if (g == 0L) Double.POSITIVE_INFINITY else (e - frameOf(g - 1, map)) / perMilli
        val after = (frameOf(g + 1, map) - e) / perMilli
        return NoteWindows.of(
            strictness,
            before.coerceAtLeast(0.0),
            after.let {
                if (it.isNaN()) Double.POSITIVE_INFINITY else it
            },
        )
    }

    /** Where the windows of main note [g] and its graces begin: hits before this can match nothing later. */
    private fun earliestStart(
        g: Long,
        e: Double,
        windows: NoteWindows,
        graceGapFrames: Double,
        map: TimeMap,
    ): Double {
        var earliest = e - windows.earlyMillis * perMilli
        val graces = lesson.graces[mainStroke(g)] ?: return earliest
        for (s in graces) {
            val eg = e - lesson.strokes[s].graceLead * graceGapFrames
            earliest = minOf(earliest, eg - graceEarly(g, eg, map) * perMilli)
        }
        return earliest
    }

    private fun graceEarly(
        g: Long,
        eg: Double,
        map: TimeMap,
    ): Double {
        val before = if (g == 0L) Double.POSITIVE_INFINITY else (eg - frameOf(g - 1, map)) / perMilli
        return minOf(strictness.matchMillis, Strictness.MAX_GAP_SHARE * before.coerceAtLeast(0.0))
    }

    private fun mainStroke(g: Long): Int = lesson.mains[Math.floorMod(g, mainCount.toLong()).toInt()]

    private fun decide(
        g: Long,
        e: Double,
        windows: NoteWindows,
        graceGapFrames: Double,
        map: TimeMap,
    ) {
        val loop = Math.floorDiv(g, mainCount.toLong())
        val stroke = lesson.strokes[mainStroke(g)]
        val position = positionOf(g)
        val from = e - windows.earlyMillis * perMilli
        val to = e + windows.lateMillis * perMilli
        var best = -1
        for (i in pending.indices) {
            val h = pending[i]
            if (h < from || h > to) continue
            if (best < 0 || abs(h - e) < abs(pending[best] - e)) best = i
        }
        val hitFrame = if (best >= 0) pending.removeAt(best) else Double.NaN
        if (hitFrame.isFinite()) {
            val offset = (hitFrame - e) / perMilli
            val grade = windows.grade(offset)
            record(NoteMark(stroke.index, loop, NoteMark.Kind.HIT, offset, map.positionAt(hitFrame) - position, grade))
            stats =
                stats.copy(
                    green = stats.green + if (grade == Grade.GREEN) 1 else 0,
                    yellow = stats.yellow + if (grade == Grade.YELLOW) 1 else 0,
                    red = stats.red + if (grade == Grade.RED) 1 else 0,
                    sumOffsetMillis = stats.sumOffsetMillis + offset,
                    sumSquaredOffsetMillis = stats.sumSquaredOffsetMillis + offset * offset,
                    perHand =
                        stats.perHand.withHand(stroke.hand) {
                            it.copy(
                                hits = it.hits + 1,
                                sumOffsetMillis =
                                    it.sumOffsetMillis + offset,
                            )
                        },
                )
        } else {
            record(NoteMark(stroke.index, loop, NoteMark.Kind.MISS))
            stats =
                stats.copy(
                    misses = stats.misses + 1,
                    perHand = stats.perHand.withHand(stroke.hand) { it.copy(misses = it.misses + 1) },
                )
        }
        decideGraces(g, loop, e, if (hitFrame.isFinite()) hitFrame else e, graceGapFrames, map)
        if (stroke.buzzQuarters > 0) {
            val end = map.frameAt(position + stroke.buzzQuarters)
            val begin = if (hitFrame.isFinite()) hitFrame else e
            if (end.isFinite()) absorbers += doubleArrayOf(begin, end + windows.lateMillis * perMilli)
        }
    }

    private fun decideGraces(
        g: Long,
        loop: Long,
        e: Double,
        before: Double,
        graceGapFrames: Double,
        map: TimeMap,
    ) {
        val graces = lesson.graces[mainStroke(g)] ?: return
        if (graces.isEmpty()) return
        val expected = DoubleArray(graces.size) { e - lesson.strokes[graces[it]].graceLead * graceGapFrames }
        val from = expected.indices.minOf { expected[it] - graceEarly(g, expected[it], map) * perMilli }
        val candidates = pending.filter { it >= from && it < before }
        val assigned = arrayOfNulls<Double>(graces.size)
        if (candidates.size >= graces.size) {
            // As many onsets as graces (or more): the ones nearest the main stroke, in order.
            val use = candidates.takeLast(graces.size)
            for (i in graces.indices) assigned[i] = use[i]
        } else {
            for (h in candidates) {
                val i = graces.indices.filter { assigned[it] == null }.minByOrNull { abs(h - expected[it]) } ?: break
                assigned[i] = h
            }
        }
        for (i in graces.indices) {
            val stroke = lesson.strokes[graces[i]]
            val h = assigned[i]
            if (h == null) {
                record(NoteMark(stroke.index, loop, NoteMark.Kind.GRACE_UNHEARD))
                stats = stats.copy(gracesUnheard = stats.gracesUnheard + 1)
            } else {
                pending.remove(h)
                val offset = (h - expected[i]) / perMilli
                val early = graceEarly(g, expected[i], map)
                val grade = NoteWindows(strictness, early, early).grade(offset)
                val graceAt = map.positionAt(expected[i])
                record(NoteMark(stroke.index, loop, NoteMark.Kind.HIT, offset, map.positionAt(h) - graceAt, grade))
                stats = stats.copy(gracesHeard = stats.gracesHeard + 1)
            }
        }
    }

    /** Hits before [frame] can no longer match: extras, unless absorbed by a roll or before the pattern [start]. */
    private fun flushExtras(
        frame: Double,
        start: Double,
        map: TimeMap,
    ) {
        while (pending.isNotEmpty() && pending[0] < frame) {
            val h = pending.removeAt(0)
            if (h < start - strictness.matchMillis * perMilli) continue // The count-in.
            if (absorbers.any { h >= it[0] && h <= it[1] }) continue
            val position = map.positionAt(h)
            extras.addLast(ExtraMark(position, floor(position / lesson.lengthQuarters).toLong()))
            while (extras.size > ScoringSnapshot.MAX_EXTRAS) extras.removeFirst()
            stats = stats.copy(extras = stats.extras + 1)
            changed = true
        }
        absorbers.removeAll { it[1] < frame }
    }

    private fun record(mark: NoteMark) {
        val list = marks[mark.strokeIndex]
        list.addLast(mark)
        while (list.size > ScoringSnapshot.MARK_LOOPS) list.removeFirst()
        changed = true
    }

    private inline fun Map<Hand, HandStats>.withHand(
        hand: Hand?,
        update: (HandStats) -> HandStats,
    ): Map<Hand, HandStats> = if (hand == null) this else this + (hand to update(this[hand] ?: HandStats()))
}
