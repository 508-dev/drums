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
import dev.co508.drums.ui.notation.Glyph

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
        val longestTail = layout.notes.maxOfOrNull { it.buzzQuarters } ?: 0.0
        for (k in layout.loopsBetween(maxOf(0.0, visible.start - longestTail), visible.endInclusive)) {
            if (k < 0) continue
            val base = k * layout.lengthQuarters
            for (note in layout.notes) {
                val p = base + note.position
                if (p > visible.endInclusive || p + note.buzzQuarters < visible.start) continue
                painter.draw(note, p, t)
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
    ) {
        val color = HandColors.of(note.lane)
        val x = geometry.laneCenterX(note.lane)
        val grace = note.graceLead > 0
        val scale = if (grace) GRACE_SCALE else 1f
        // A grace note sounds just before its main note, so it sits just below it
        // (earlier, nearer the hit line), a fixed gap per grace whatever the zoom.
        val y = geometry.y(position, now) + note.graceLead * staffSpace * GRACE_STEP
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
        glyph(Glyph.NOTEHEAD_BLACK, x, y, headScale, color.copy(alpha = alpha))
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
        const val GRACE_STEP = 0.85f
    }
}
