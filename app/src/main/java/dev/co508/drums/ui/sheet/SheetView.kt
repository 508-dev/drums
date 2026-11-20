package dev.co508.drums.ui.sheet

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.co508.drums.R
import dev.co508.drums.lesson.Accent
import dev.co508.drums.lesson.Fraction
import dev.co508.drums.lesson.Hand
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.NoteValue
import dev.co508.drums.lesson.Ornament
import dev.co508.drums.lesson.StrokeTimeline
import dev.co508.drums.scoring.NoteMark
import dev.co508.drums.scoring.ScoringSnapshot
import dev.co508.drums.ui.falling.GradeColors
import dev.co508.drums.ui.falling.HandColors
import dev.co508.drums.ui.falling.drawExtra
import dev.co508.drums.ui.notation.Glyph
import kotlin.math.floor

private val ink = Color(0xFF212121)
private val stickingInk = Color(0xFF616161)
private val playheadColor = Color(0xFFFF8F00)

/** How long a note stays lit after the playhead reaches it, in quarter notes. */
const val SHEET_LIT_QUARTERS = 0.35

/**
 * The sheet view: the lesson engraved on a one-line staff with proportional
 * spacing, and a playhead sweeping across it. Each note and its sticking light
 * up as the playhead reaches them (DECISIONS.md, "Two Practice Views").
 *
 * [now] gives the transport position in quarter notes and is read while
 * drawing, as in the falling-notes view. The playhead is hidden during the
 * count-in and when [showPlayhead] is off.
 */
@Composable
fun SheetView(
    notation: Notation,
    timeline: StrokeTimeline,
    now: () -> Double,
    modifier: Modifier = Modifier,
    showPlayhead: Boolean = true,
    /** Scoring marks (M5), read while drawing; null when not scoring. */
    marks: () -> ScoringSnapshot? = { null },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.bravura) ?: Typeface.DEFAULT }
    val glyphPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val textPaint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                this.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        }

    BoxWithConstraints(modifier.clipToBounds()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val maxSpace = with(density) { MAX_STAFF_SPACE.dp.toPx() }
        // As big as fits: the widest measure must fit on a line at the spacing it needs.
        val (space, layout) =
            remember(notation, widthPx) {
                val needed = SheetLayout.spacesPerQuarterNeeded(notation)
                val widest =
                    notation.measures.maxOf { SheetLayout.naturalWidth(it, needed) } +
                        SheetLayout.CLEF_WIDTH + SheetLayout.TIME_SIGNATURE_WIDTH + MARGIN * 2
                val s = minOf(maxSpace, widthPx / widest)
                s to SheetLayout.layout(notation, widthPx / s - MARGIN * 2)
            }
        // Each written note's main strokes: a note repeated or diddled is played several times a loop.
        val strokesOf =
            remember(timeline) {
                timeline.strokes
                    .withIndex()
                    .filter { it.value.graceLead == 0 }
                    .groupBy({ it.value.source.measureIndex to it.value.source.noteIndex }, { it.index })
            }
        Canvas(Modifier.matchParentSize()) {
            val t = now()
            val snapshot = marks()
            val length = timeline.length.toDouble()
            val loopNow =
                if (!showPlayhead ||
                    snapshot == null
                ) {
                    snapshot?.latestLoop ?: 0L
                } else {
                    floor(t / length).toLong()
                }
            val painter =
                SheetPainter(this, space, glyphPaint, textPaint) { note ->
                    snapshot?.let { latestMark(it, strokesOf[note.measureIndex to note.noteIndex], loopNow, timeline) }
                }
            val position = if (showPlayhead) sheetPosition(timeline, t) else null
            layout.systems.forEachIndexed { s, system ->
                painter.system(
                    system,
                    top = TOP_MARGIN + s * SYSTEM_SPACING,
                    layoutWidth = layout.width,
                    position = position,
                )
            }
            for (tie in layout.ties) {
                val s = layout.systems.indexOfFirst { sys -> sys.measures.any { it.index == tie.from.measureIndex } }
                painter.tie(tie, TOP_MARGIN + s * SYSTEM_SPACING)
            }
            snapshot?.extras?.forEach { extra ->
                val fade = ScoringSnapshot.fade(loopNow - extra.loop)
                val at = sheetPosition(timeline, extra.position) ?: return@forEach
                if (fade <= 0f) return@forEach
                layout.systems.forEachIndexed { s, system ->
                    val m = system.measures.firstOrNull { it.index == at.measureIndex } ?: return@forEachIndexed
                    painter.extra(m.xAt(at.offset) + 0.59f, TOP_MARGIN + s * SYSTEM_SPACING, fade)
                }
            }
        }
    }
}

private const val MAX_STAFF_SPACE = 10f
private const val MARGIN = 1f
private const val TOP_MARGIN = 8f
private const val SYSTEM_SPACING = 16f

/** Where scoring marks sit: below the staff and its accents, above the sticking. */
private const val MARK_Y = 2.7f

/**
 * The mark to show on a written note: the newest of its strokes' marks at
 * [loop] or before (the most recent pass, for a note played several times a
 * loop), with its fade.
 */
private fun latestMark(
    snapshot: ScoringSnapshot,
    strokes: List<Int>?,
    loop: Long,
    timeline: StrokeTimeline,
): Pair<NoteMark, Float>? {
    var best: NoteMark? = null
    for (s in strokes ?: return null) {
        val mark = snapshot.markFor(s, loop) ?: continue
        val current = best
        if (current == null ||
            mark.loop > current.loop ||
            (mark.loop == current.loop && timeline.strokes[s].position > timeline.strokes[current.strokeIndex].position)
        ) {
            best = mark
        }
    }
    val mark = best ?: return null
    if (mark.kind == NoteMark.Kind.GRACE_UNHEARD) return null
    return mark to ScoringSnapshot.fade(loop - mark.loop)
}

/** Draws engraving in staff-space units: (x, y) here are staff spaces from the margin and the staff line. */
private class SheetPainter(
    private val scope: DrawScope,
    private val space: Float,
    private val glyphPaint: Paint,
    private val textPaint: Paint,
    private val markOf: (PlacedNote) -> Pair<NoteMark, Float>? = { null },
) {
    private var lineY = 0f

    private fun px(x: Float) = (x + MARGIN) * space

    private fun py(y: Float) = (lineY + y) * space

    fun system(
        system: SheetSystem,
        top: Float,
        layoutWidth: Float,
        position: SheetPosition?,
    ) {
        lineY = top
        line(0f, 0f, layoutWidth, 0f, 0.13f, ink)
        glyph(Glyph.PERCUSSION_CLEF, 0.6f, 0f, ink, alignLeft = true)
        for (measure in system.measures) measure(measure, position)

        // The playhead, if it is in this line.
        val here = position?.let { p -> system.measures.firstOrNull { it.index == p.measureIndex } } ?: return
        val x = here.xAt(position.offset) + 0.59f
        scope.drawLine(
            playheadColor.copy(alpha = 0.8f),
            Offset(px(x), py(-6.5f)),
            Offset(px(x), py(6.5f)),
            0.3f * space,
        )
    }

    private fun measure(
        m: PlacedMeasure,
        position: SheetPosition?,
    ) {
        var x = m.x
        if (m.showTimeSignature) {
            val signature = m.measure.timeSignature
            digits(signature.beats, x + 1.3f, -1f, timeSignature = true)
            digits(signature.beatType, x + 1.3f, 1f, timeSignature = true)
            x += SheetLayout.TIME_SIGNATURE_WIDTH
        }
        if (m.measure.repeatStart) {
            line(x + 0.25f, -2f, x + 0.25f, 2f, 0.5f, ink)
            line(x + 0.9f, -2f, x + 0.9f, 2f, 0.16f, ink)
            dot(x + 1.35f, -0.6f)
            dot(x + 1.35f, 0.6f)
        }
        val end = m.x + m.width
        if (m.measure.repeatTimes != null) {
            dot(end - 1.35f, -0.6f)
            dot(end - 1.35f, 0.6f)
            line(end - 0.9f, -2f, end - 0.9f, 2f, 0.16f, ink)
            line(end - 0.25f, -2f, end - 0.25f, 2f, 0.5f, ink)
            if (m.measure.repeatTimes > 2) text("×${m.measure.repeatTimes}", end - 1f, -3f, stickingInk, 1.6f)
        } else {
            line(end, -2f, end, 2f, 0.16f, ink)
        }

        for (rest in m.rests) rest(rest)
        m.notes.forEachIndexed { i, note ->
            // Lit until the next note starts, so only one note is lit at a time.
            val until = m.notes.getOrNull(i + 1)?.offset ?: m.measure.length
            val lit = position != null && position.measureIndex == m.index && litFor(note, until, position.offset)
            note(note, lit, m)
        }
        for (group in m.beams) beams(group)
        for (tuplet in m.tuplets) tuplet(tuplet)
    }

    private fun litFor(
        note: PlacedNote,
        next: Fraction,
        offset: Double,
    ): Boolean {
        val since = offset - note.offset.toDouble()
        return since >= 0 && since < minOf(SHEET_LIT_QUARTERS, (next - note.offset).toDouble())
    }

    private fun note(
        n: PlacedNote,
        lit: Boolean,
        m: PlacedMeasure,
    ) {
        val note = n.note
        val color = if (lit) HandColors.of(note.hand) else ink
        val mark = markOf(n)
        // A note missed on its latest pass is drawn hollow.
        val missed = mark != null && mark.first.kind == NoteMark.Kind.MISS && mark.second >= 1f
        val head =
            when {
                note.value == NoteValue.WHOLE -> Glyph.NOTEHEAD_WHOLE
                note.value == NoteValue.HALF || missed -> Glyph.NOTEHEAD_HALF
                else -> Glyph.NOTEHEAD_BLACK
            }
        if (mark != null) mark(n, m, mark.first, mark.second)
        if (lit) scope.drawCircle(color.copy(alpha = 0.18f), 1.6f * space, Offset(px(n.x + 0.59f), py(0f)))
        glyph(head, n.x, 0f, color, alignLeft = true)
        if (note.ghost) {
            glyph(Glyph.PARENTHESIS_LEFT, n.x - 0.65f, 0f, color, alignLeft = true)
            glyph(Glyph.PARENTHESIS_RIGHT, n.x + 1.18f + 0.35f, 0f, color, alignLeft = true)
        }
        repeat(note.dots) { glyph(Glyph.AUGMENTATION_DOT, n.x + 1.6f + it * 0.6f, -0.5f, color, alignLeft = true) }

        if (note.value != NoteValue.WHOLE) {
            line(n.stemX, -Glyph.STEM_UP_Y, n.stemX, STEM_TOP, STEM_THICKNESS, color)
            if (!n.beamed) {
                val flag =
                    when (n.beams) {
                        1 -> Glyph.FLAG_8TH_UP
                        2 -> Glyph.FLAG_16TH_UP
                        0 -> null
                        else -> Glyph.FLAG_32ND_UP
                    }
                if (flag !=
                    null
                ) {
                    glyph(flag, n.stemX - STEM_THICKNESS / 2, STEM_TOP, color, alignLeft = true, baseline = true)
                }
            }
        }
        when (val o = note.ornament) {
            is Ornament.Tremolo -> {
                val g =
                    when (o.slashes) {
                        1 -> Glyph.TREMOLO_1
                        2 -> Glyph.TREMOLO_2
                        else -> Glyph.TREMOLO_3
                    }
                glyph(g, n.stemX, -1.9f, color)
            }
            Ornament.Buzz -> glyph(Glyph.BUZZ_ROLL, n.stemX, -1.9f, color)
            null -> Unit
        }
        when (note.accent) {
            Accent.ACCENT -> glyph(Glyph.ACCENT_BELOW, n.x + 0.59f, 1.0f + 0.49f, color)
            Accent.MARCATO -> glyph(Glyph.MARCATO_BELOW, n.x + 0.59f, 1.0f + 0.51f, color)
            Accent.NONE -> Unit
        }
        graces(n, color)
        if (n.sticking.isNotEmpty()) {
            text(
                n.sticking,
                n.x + 0.59f,
                4.4f,
                if (lit) HandColors.of(note.hand) else stickingInk,
                if (lit) 1.9f else 1.6f,
            )
        }
    }

    /**
     * Below the staff, where the hit was against the note: a line from the
     * note to a dot, left if early, right if late, coloured by window. A miss
     * from an earlier loop is a faint ring.
     */
    private fun mark(
        n: PlacedNote,
        m: PlacedMeasure,
        mark: NoteMark,
        fade: Float,
    ) {
        val x = n.x + 0.59f
        when (mark.kind) {
            NoteMark.Kind.HIT -> {
                val color = GradeColors.of(mark.grade).copy(alpha = fade)
                val to = x + (mark.offsetQuarters * m.spacesPerQuarter).toFloat()
                line(x, MARK_Y, to, MARK_Y, 0.22f, color)
                scope.drawCircle(color, 0.38f * space, Offset(px(to), py(MARK_Y)))
            }
            NoteMark.Kind.MISS ->
                if (fade < 1f) {
                    scope.drawCircle(
                        GradeColors.red.copy(alpha = fade * 0.8f),
                        1.1f * space,
                        Offset(px(x), py(0f)),
                        style = Stroke(width = 0.15f * space),
                    )
                }
            NoteMark.Kind.GRACE_UNHEARD -> Unit
        }
    }

    /** An extra hit at [x] on the line at [top]: a small × below the staff. */
    fun extra(
        x: Float,
        top: Float,
        fade: Float,
    ) {
        lineY = top
        scope.drawExtra(px(x), py(MARK_Y), 0.45f * space, fade)
    }

    /** Grace notes, to the left of their note: a slashed one as the acciaccatura glyph, others beamed small notes. */
    private fun graces(
        n: PlacedNote,
        color: Color,
    ) {
        val graces = n.note.graceNotes
        if (graces.isEmpty()) return
        if (graces.size == 1 && graces[0].slashed) {
            glyph(
                Glyph.ACCIACCATURA_STEM_UP,
                n.x - 0.35f - Glyph.ACCIACCATURA_STEM_UP.width,
                0f,
                color,
                alignLeft = true,
                baseline = true,
            )
            return
        }
        val scale = 0.66f
        val step = 1.15f
        val firstX = n.x - 0.4f - graces.size * step
        val stemTop = -2.6f
        val stems =
            graces.indices.map { i ->
                val gx = firstX + i * step
                glyph(Glyph.NOTEHEAD_BLACK, gx, 0f, color, alignLeft = true, scale = scale)
                val sx = gx + (Glyph.STEM_UP_X - STEM_THICKNESS) * scale
                line(sx, -Glyph.STEM_UP_Y * scale, sx, stemTop, STEM_THICKNESS * scale, color)
                sx
            }
        if (graces.size == 1) {
            glyph(Glyph.FLAG_8TH_UP, stems[0], stemTop, color, alignLeft = true, baseline = true, scale = scale)
        } else {
            val beams = graces.maxOf { it.value?.beams ?: 1 }.coerceIn(1, 3)
            for (b in 0 until beams) {
                val y = stemTop + b * 0.75f * scale
                rect(stems.first(), y, stems.last() + STEM_THICKNESS * scale, y + 0.5f * scale, color)
            }
        }
    }

    private fun beams(group: BeamGroup) {
        val notes = group.notes
        val first = notes.first().stemX - STEM_THICKNESS / 2
        val last = notes.last().stemX + STEM_THICKNESS / 2
        rect(first, STEM_TOP, last, STEM_TOP + BEAM_THICKNESS, ink)
        // Secondary beams: across neighbours that both have them, else a stub toward the group.
        for (level in 2..3) {
            val y = STEM_TOP + (level - 1) * (BEAM_THICKNESS + BEAM_SPACING)
            for (i in notes.indices) {
                if (notes[i].beams < level) continue
                val next = notes.getOrNull(i + 1)
                val prev = notes.getOrNull(i - 1)
                when {
                    next != null && next.beams >= level ->
                        rect(
                            notes[i].stemX - STEM_THICKNESS / 2,
                            y,
                            next.stemX + STEM_THICKNESS / 2,
                            y + BEAM_THICKNESS,
                            ink,
                        )
                    prev != null && prev.beams >= level -> Unit // Drawn from the previous note.
                    next != null -> rect(notes[i].stemX, y, notes[i].stemX + 1.2f, y + BEAM_THICKNESS, ink)
                    else -> rect(notes[i].stemX - 1.2f, y, notes[i].stemX, y + BEAM_THICKNESS, ink)
                }
            }
        }
    }

    private fun tuplet(t: TupletMark) {
        val left = t.notes.first().x
        val right = t.notes.last().stemX
        val center = (left + right) / 2
        val y = STEM_TOP - 1.4f
        digits(t.number, center, y, timeSignature = false)
        if (t.bracket) {
            line(left, y + 0.7f, center - 0.9f, y + 0.7f, 0.16f, ink)
            line(center + 0.9f, y + 0.7f, right, y + 0.7f, 0.16f, ink)
            line(left, y + 0.7f, left, y + 1.3f, 0.16f, ink)
            line(right, y + 0.7f, right, y + 1.3f, 0.16f, ink)
        }
    }

    private fun rest(r: PlacedRest) {
        val d = r.duration
        val (glyph, dotted) =
            when (d) {
                Fraction.of(4) -> Glyph.REST_WHOLE to false
                Fraction.of(3) -> Glyph.REST_HALF to true
                Fraction.of(2) -> Glyph.REST_HALF to false
                Fraction.of(3, 2) -> Glyph.REST_QUARTER to true
                Fraction.ONE -> Glyph.REST_QUARTER to false
                Fraction.of(3, 4) -> Glyph.REST_8TH to true
                Fraction.of(1, 2) -> Glyph.REST_8TH to false
                Fraction.of(1, 4) -> Glyph.REST_16TH to false
                else -> Glyph.REST_32ND to false
            }
        val y =
            if (glyph == Glyph.REST_WHOLE) {
                0.5f
            } else if (glyph == Glyph.REST_HALF) {
                -0.3f
            } else {
                0f
            }
        glyph(glyph, r.x, y, ink, alignLeft = true)
        if (dotted) glyph(Glyph.AUGMENTATION_DOT, r.x + glyph.width + 0.4f, -0.5f, ink, alignLeft = true)
    }

    fun tie(
        tie: Tie,
        top: Float,
    ) {
        lineY = top
        val x1 = px(tie.from.x + 1.0f)
        val x2 = px(tie.to.x + 0.2f)
        val y = py(0.8f)
        val sag = 0.7f * space
        val path =
            Path().apply {
                moveTo(x1, y)
                quadraticTo((x1 + x2) / 2, y + sag * 2, x2, y)
            }
        scope.drawPath(path, ink, style = Stroke(width = 0.22f * space))
    }

    private fun digits(
        number: Int,
        centerX: Float,
        centerY: Float,
        timeSignature: Boolean,
    ) {
        val glyphs =
            number.toString().map { c ->
                if (timeSignature) {
                    Glyph.timeSignatureDigit(c - '0')
                } else {
                    Glyph.tupletDigit(
                        c - '0',
                    )
                }
            }
        val total = glyphs.sumOf { it.width.toDouble() }.toFloat()
        var x = centerX - total / 2
        for (g in glyphs) {
            glyph(g, x + g.width / 2, centerY, ink)
            x += g.width
        }
    }

    private fun dot(
        x: Float,
        y: Float,
    ) = scope.drawCircle(ink, 0.2f * space, Offset(px(x), py(y)))

    private fun line(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        thickness: Float,
        color: Color,
    ) = scope.drawLine(color, Offset(px(x1), py(y1)), Offset(px(x2), py(y2)), thickness * space)

    private fun rect(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Color,
    ) = scope.drawRect(color, Offset(px(x1), py(y1)), Size((x2 - x1) * space, (y2 - y1) * space))

    private fun text(
        s: String,
        cx: Float,
        y: Float,
        color: Color,
        size: Float,
    ) {
        textPaint.color = color.toArgb()
        textPaint.textSize = size * space
        scope.drawIntoCanvas { it.nativeCanvas.drawText(s, px(cx), py(y), textPaint) }
    }

    /**
     * Draws [glyph] centred on (x, y) in staff spaces, or with its left edge at x
     * ([alignLeft]), or with its own origin at (x, y) ([baseline], for glyphs
     * designed to attach at their origin, like flags and the grace-note glyph).
     */
    private fun glyph(
        glyph: Glyph,
        x: Float,
        y: Float,
        color: Color,
        alignLeft: Boolean = false,
        baseline: Boolean = false,
        scale: Float = 1f,
    ) {
        glyphPaint.textSize = space * scale * Glyph.STAFF_SPACES_PER_EM
        glyphPaint.color = color.toArgb()
        val originX = if (alignLeft) x - glyph.west * scale else x - glyph.centerX * scale
        val originY = if (baseline) y else y + glyph.centerY * scale
        scope.drawIntoCanvas { it.nativeCanvas.drawText(glyph.text, px(originX), py(originY), glyphPaint) }
    }

    private companion object {
        const val STEM_TOP = -3.5f
        const val BEAM_THICKNESS = 0.5f
        const val BEAM_SPACING = 0.25f
    }
}

private fun HandColors.of(hand: Hand?): Color =
    when (hand) {
        Hand.LEFT -> left
        Hand.RIGHT -> right
        null -> none
    }
