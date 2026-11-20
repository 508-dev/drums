package dev.co508.drums.ui.falling

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.co508.drums.R
import dev.co508.drums.lesson.StrokeDynamic
import dev.co508.drums.scoring.Grade
import dev.co508.drums.scoring.NoteMark
import dev.co508.drums.scoring.ScoringSnapshot
import dev.co508.drums.ui.notation.Glyph
import kotlin.math.floor

/** Hand colours (DECISIONS.md, "Falling Notes: Hands Are Lanes"). */
object HandColors {
    val left = Color(0xFF1565C0)
    val right = Color(0xFFC62828)
    val none = Color(0xFF616161)

    fun of(lane: Lane) =
        when (lane) {
            Lane.LEFT -> left
            Lane.RIGHT -> right
            Lane.MIDDLE -> none
        }
}

private val gridColor = Color(0xFF9E9E9E)
private val hitLineColor = Color(0xFF212121)
private val labelColor = Color(0xFF616161)

/**
 * The falling-notes view: L and R lanes, notes falling onto a hit line near
 * the bottom and lighting up as they cross it. Time runs at a fixed
 * [pixelsPerBeat], so a pattern looks the same at every tempo.
 *
 * [now] gives the musical position (quarter notes) on the hit line. It is
 * read while drawing, so a [now] that reads per-frame state redraws every
 * frame without recomposing. The layout repeats every loop; positions before
 * 0 (the count-in) show the grid only.
 */
@Composable
fun FallingNotesView(
    layout: FallingNotesLayout,
    now: () -> Double,
    modifier: Modifier = Modifier,
    pixelsPerBeat: Dp = 96.dp,
    showCounts: Boolean = true,
    /** Light notes up as they cross the hit line: on while playing, off when stopped. */
    lightUp: Boolean = true,
    /** Scoring marks (M5), read while drawing; null when not scoring. */
    marks: () -> ScoringSnapshot? = { null },
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val typeface = remember { ResourcesCompat.getFont(context, R.font.bravura) ?: Typeface.DEFAULT }
    val glyphPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface } }
    val labelPaint =
        remember {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.RIGHT
                color = labelColor.toArgb()
            }
        }
    val ppb = with(density) { pixelsPerBeat.toPx() }
    val lanes =
        remember(layout) {
            if (layout.hasMiddleLane) listOf(Lane.LEFT, Lane.MIDDLE, Lane.RIGHT) else listOf(Lane.LEFT, Lane.RIGHT)
        }
    val maxLane = with(density) { 120.dp.toPx() }
    val labelSize = with(density) { 14.dp.toPx() }

    Canvas(modifier.clipToBounds()) {
        val laneWidth = minOf(size.width * 0.8f / lanes.size, maxLane)
        val geometry = FallingNotesGeometry(size.width, size.height, ppb, layout.beatQuarters, lanes, laneWidth)
        val painter = NotePainter(this, geometry, glyphPaint, staffSpace = laneWidth * 0.2f, lightUp = lightUp)
        val t = now()
        val left = geometry.laneCenterX(lanes.first()) - laneWidth / 2
        val right = geometry.laneCenterX(lanes.last()) + laneWidth / 2

        for (lane in lanes) {
            drawRect(
                HandColors.of(lane).copy(alpha = 0.06f),
                topLeft = Offset(geometry.laneCenterX(lane) - laneWidth / 2, 0f),
                size = Size(laneWidth, size.height),
            )
        }

        val visible = geometry.visiblePositions(t, margin = laneWidth)
        labelPaint.textSize = labelSize
        for (k in layout.loopsBetween(visible.start, visible.endInclusive)) {
            val base = k * layout.lengthQuarters
            for (line in layout.grid) {
                val p = base + line.position
                if (p !in visible) continue
                val y = geometry.y(p, t)
                drawLine(
                    gridColor.copy(alpha = if (line.isBar) 0.9f else 0.45f),
                    Offset(left, y),
                    Offset(right, y),
                    if (line.isBar) {
                        3f *
                            density.density
                    } else {
                        1f * density.density
                    },
                )
            }
            if (showCounts) {
                for (label in layout.counts) {
                    val p = base + label.position
                    if (p !in visible) continue
                    labelPaint.isFakeBoldText = label.isBeat
                    labelPaint.alpha = if (label.isBeat) 255 else 150
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(
                            label.text,
                            left - labelSize * 0.6f,
                            geometry.y(p, t) + labelSize * 0.35f,
                            labelPaint,
                        )
                    }
                }
            }
        }

        drawLine(
            hitLineColor,
            Offset(left - laneWidth * 0.15f, geometry.hitLineY),
            Offset(right + laneWidth * 0.15f, geometry.hitLineY),
            4f * density.density,
        )

        // Notes, from the first loop on (the count-in has none). A buzz's tail reaches up past its start.
        val snapshot = marks()
        // Stopped, the screen shows loop 0: give it the latest marks.
        val loopShift = if (lightUp || snapshot == null) 0L else snapshot.latestLoop
        val longestTail = layout.notes.maxOfOrNull { it.buzzQuarters } ?: 0.0
        for (k in layout.loopsBetween(maxOf(0.0, visible.start - longestTail), visible.endInclusive)) {
            if (k < 0) continue
            val base = k * layout.lengthQuarters
            for (note in layout.notes) {
                val p = base + note.position
                if (p > visible.endInclusive || p + note.buzzQuarters < visible.start) continue
                val loop = k + loopShift
                val mark = snapshot?.markFor(note.strokeIndex, loop)
                painter.draw(
                    note,
                    p,
                    t,
                    mark,
                    mark?.let { ScoringSnapshot.fade(loop - it.loop) } ?: 0f,
                    mark?.loop == loop,
                )
            }
        }
        if (snapshot != null) {
            val nowLoop = floor(t / layout.lengthQuarters).toLong() + loopShift
            val centre = (left + right) / 2
            for (extra in snapshot.extras) {
                val fade = ScoringSnapshot.fade(nowLoop - extra.loop)
                val p = extra.position - loopShift * layout.lengthQuarters
                if (fade <= 0f || p !in visible) continue
                drawExtra(centre, geometry.y(p, t), laneWidth * 0.12f, fade)
            }
        }

        // Lane letters under the hit line.
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.isFakeBoldText = true
        labelPaint.alpha = 255
        labelPaint.textSize = labelSize * 1.3f
        for (lane in lanes) {
            val letter =
                when (lane) {
                    Lane.LEFT -> "L"
                    Lane.RIGHT -> "R"
                    Lane.MIDDLE -> "?"
                }
            labelPaint.color = HandColors.of(lane).toArgb()
            drawIntoCanvas {
                it.nativeCanvas.drawText(
                    letter,
                    geometry.laneCenterX(lane),
                    geometry.hitLineY + labelSize * 2.2f,
                    labelPaint,
                )
            }
        }
        labelPaint.color = labelColor.toArgb()
        labelPaint.textAlign = Paint.Align.RIGHT
    }
}

/** Timing-window colours (DECISIONS.md, "Showing How Far Off You Were"). */
object GradeColors {
    val green = Color(0xFF2E7D32)
    val yellow = Color(0xFFF9A825)
    val red = Color(0xFFD50000)
    val extra = Color(0xFF6A1B9A)

    fun of(grade: Grade?) =
        when (grade) {
            Grade.GREEN -> green
            Grade.YELLOW -> yellow
            Grade.RED, null -> red
        }
}

/** An extra hit: a small × where it was heard. */
internal fun DrawScope.drawExtra(
    x: Float,
    y: Float,
    half: Float,
    fade: Float,
) {
    val color = GradeColors.extra.copy(alpha = fade)
    drawLine(color, Offset(x - half, y - half), Offset(x + half, y + half), half * 0.45f)
    drawLine(color, Offset(x - half, y + half), Offset(x + half, y - half), half * 0.45f)
}

/** Draws notes with Bravura glyphs, sized by the staff space. */
private class NotePainter(
    private val scope: DrawScope,
    private val geometry: FallingNotesGeometry,
    private val paint: Paint,
    private val staffSpace: Float,
    private val lightUp: Boolean,
) {
    fun draw(
        note: FallingNote,
        position: Double,
        now: Double,
        mark: NoteMark? = null,
        fade: Float = 0f,
        ownLoop: Boolean = false,
    ) {
        val color = HandColors.of(note.lane)
        val x = geometry.laneCenterX(note.lane)
        val grace = note.graceLead > 0
        val scale = if (grace) GRACE_SCALE else 1f
        // A grace note sounds just before its main note, so it sits just below it
        // (earlier, nearer the hit line), and always nearer it than its lane neighbour.
        val y =
            geometry.y(position, now) +
                graceOffset(
                    note.graceLead,
                    note.graceCount,
                    note.laneGapQuarters,
                    geometry.pixelsPerQuarter,
                    staffSpace * GRACE_STEP,
                )
        // Lit and passed are judged by where the note is drawn, so a grace note lights
        // as it crosses the line, just before its main note.
        val quartersPast = (y - geometry.hitLineY) / geometry.pixelsPerQuarter
        val lit = if (lightUp) litAmount(0.0, quartersPast.toDouble()) else 0f
        val passed = quartersPast > LIT_QUARTERS
        val alpha =
            when {
                passed -> 0.35f
                note.dynamic == StrokeDynamic.GHOST -> 0.6f
                else -> 1f
            }

        if (note.buzzQuarters > 0) {
            val top = geometry.y(position + note.buzzQuarters, now)
            scope.drawLine(color.copy(alpha = alpha * 0.55f), Offset(x, y), Offset(x, top), staffSpace * 0.35f)
            glyph(Glyph.BUZZ_ROLL, x, y - staffSpace * 1.6f, 1f, color.copy(alpha = alpha))
        }
        if (lit > 0f) {
            scope.drawCircle(
                color.copy(alpha = 0.35f * lit),
                radius = staffSpace * (1.3f + 0.6f * lit) * scale,
                center = Offset(x, y),
            )
        }
        val headScale = scale * (1f + 0.2f * lit)
        // The hit mark goes behind the notehead, so it never hides the note itself.
        if (mark?.kind == NoteMark.Kind.HIT && fade > 0f) hitMark(mark, x, y, scale, fade)
        val missed = mark?.kind == NoteMark.Kind.MISS && fade > 0f
        // A missed note is drawn hollow; in later loops, a faint ring recalls the miss.
        glyph(
            if (missed &&
                ownLoop
            ) {
                Glyph.NOTEHEAD_HALF
            } else {
                Glyph.NOTEHEAD_BLACK
            },
            x,
            y,
            headScale,
            color.copy(alpha = alpha),
        )
        if (missed && !ownLoop) {
            scope.drawCircle(
                GradeColors.red.copy(alpha = fade * 0.8f),
                radius = staffSpace * 1.1f * scale,
                center = Offset(x, y),
                style = Stroke(width = staffSpace * 0.15f),
            )
        }
        if (mark?.kind == NoteMark.Kind.HIT && fade > 0f) hitMark(mark, x, y, scale, fade)
        val halfHead = Glyph.NOTEHEAD_BLACK.width / 2 * staffSpace * headScale
        if (note.dynamic == StrokeDynamic.GHOST) {
            val gap = staffSpace * 0.15f
            glyph(
                Glyph.PARENTHESIS_LEFT,
                x - halfHead - gap - Glyph.PARENTHESIS_LEFT.width / 2 * staffSpace,
                y,
                1f,
                color.copy(alpha = alpha),
            )
            glyph(
                Glyph.PARENTHESIS_RIGHT,
                x + halfHead + gap + Glyph.PARENTHESIS_RIGHT.width / 2 * staffSpace,
                y,
                1f,
                color.copy(alpha = alpha),
            )
        }
        if (note.dynamic == StrokeDynamic.ACCENT) {
            // Beside the notehead, not above it: above is later in time, where the
            // next note in the lane may be.
            val left = x + halfHead + staffSpace * 0.3f
            glyph(
                Glyph.ACCENT_ABOVE,
                left + Glyph.ACCENT_ABOVE.width / 2 * staffSpace,
                y,
                1f,
                color.copy(alpha = alpha),
            )
        }
    }

    /**
     * Where the hit was, against the note: a bar across the lane, below the
     * note if early (nearer the hit line), above if late, joined to it.
     */
    private fun hitMark(
        mark: NoteMark,
        x: Float,
        y: Float,
        scale: Float,
        fade: Float,
    ) {
        val color = GradeColors.of(mark.grade).copy(alpha = fade)
        val markY = y - (mark.offsetQuarters * geometry.pixelsPerQuarter).toFloat()
        val half = staffSpace * 1.25f * scale
        scope.drawLine(color, Offset(x, y), Offset(x, markY), staffSpace * 0.1f)
        scope.drawLine(color, Offset(x - half, markY), Offset(x + half, markY), staffSpace * 0.24f * scale)
    }

    /** Draws [glyph] with the centre of its ink at ([cx], [cy]). */
    private fun glyph(
        glyph: Glyph,
        cx: Float,
        cy: Float,
        scale: Float,
        color: Color,
    ) {
        val space = staffSpace * scale
        paint.textSize = space * Glyph.STAFF_SPACES_PER_EM
        paint.color = color.toArgb()
        // SMuFL's y axis points up from the baseline; the screen's points down.
        val baselineX = cx - glyph.centerX * space
        val baselineY = cy + glyph.centerY * space
        scope.drawIntoCanvas { it.nativeCanvas.drawText(glyph.text, baselineX, baselineY, paint) }
    }

    private companion object {
        const val GRACE_SCALE = 0.6f
        const val GRACE_STEP = 0.7f
    }
}
