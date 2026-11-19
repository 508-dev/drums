package dev.co508.drums.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small app-owned geometric symbols; no icon-pack dependency. */
internal object LibraryIcons {
    val Back =
        icon("Back") {
            path(
                fill = null,
                stroke = ink,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(20f, 12f)
                lineTo(4f, 12f)
                moveTo(11f, 5f)
                lineTo(4f, 12f)
                lineTo(11f, 19f)
            }
        }
    val Next =
        icon("Next") {
            path(
                fill = null,
                stroke = ink,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 6f)
                lineTo(15f, 12f)
                lineTo(9f, 18f)
            }
        }
    val History =
        icon("History") {
            path(
                fill = null,
                stroke = ink,
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 8f)
                arcTo(9f, 9f, 0f, true, true, 4f, 17f)
                moveTo(3f, 3f)
                lineTo(3f, 8f)
                lineTo(8f, 8f)
                moveTo(12f, 7f)
                lineTo(12f, 12f)
                lineTo(16f, 14f)
            }
        }
    val Settings =
        icon("Settings") {
            path(fill = null, stroke = ink, strokeLineWidth = 1.7f, strokeLineJoin = StrokeJoin.Round) {
                for (point in 0 until 32) {
                    val angle = point * PI / 16 - PI / 2
                    val radius = if (point % 4 in 1..2) 10.0 else 8.0
                    val x = (12 + radius * cos(angle)).toFloat()
                    val y = (12 + radius * sin(angle)).toFloat()
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
                moveTo(15.5f, 12f)
                arcTo(3.5f, 3.5f, 0f, true, true, 8.5f, 12f)
                arcTo(3.5f, 3.5f, 0f, true, true, 15.5f, 12f)
            }
        }
    val Star = star(true)
    val StarOutline = star(false)

    private val ink get() = SolidColor(Color.Black)

    private fun star(filled: Boolean) =
        icon(if (filled) "Star" else "StarOutline") {
            path(
                fill = if (filled) ink else null,
                stroke = ink,
                strokeLineWidth = 1.6f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                for (point in 0 until 10) {
                    val angle = point * PI / 5 - PI / 2
                    val radius = if (point % 2 == 0) 10.0 else 4.5
                    val x = (12 + radius * cos(angle)).toFloat()
                    val y = (12 + radius * sin(angle)).toFloat()
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }

    private fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()
}
