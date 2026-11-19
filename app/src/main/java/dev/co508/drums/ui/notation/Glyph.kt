package dev.co508.drums.ui.notation

/**
 * The SMuFL glyphs we draw from Bravura, by codepoint, with their bounding
 * boxes in staff spaces (1 staff space = 1/4 em). The boxes come from
 * Bravura 1.482's metadata (`Bravura.json`, `glyphBBoxes`), so glyphs can be
 * placed by their real ink rather than the font's advance and baseline.
 * Positive y is up.
 */
enum class Glyph(
    val codepoint: Char,
    val west: Float,
    val south: Float,
    val east: Float,
    val north: Float,
) {
    NOTEHEAD_BLACK('\uE0A4', 0f, -0.5f, 1.18f, 0.5f),
    PARENTHESIS_LEFT('\uE0F5', 0f, -0.724f, 0.436f, 0.724f),
    PARENTHESIS_RIGHT('\uE0F6', -0.144f, -0.724f, 0.292f, 0.724f),
    ACCENT_ABOVE('\uE4A0', 0f, 0.004f, 1.356f, 0.98f),
    MARCATO_ABOVE('\uE4AC', -0.004f, -0.004f, 0.94f, 1.012f),
    BUZZ_ROLL('\uE22A', -0.62f, -0.464f, 0.624f, 0.464f),
    ;

    val text: String = codepoint.toString()
    val width: Float get() = east - west
    val height: Float get() = north - south
    val centerX: Float get() = (west + east) / 2
    val centerY: Float get() = (south + north) / 2

    companion object {
        /** Staff spaces per em: SMuFL fonts are drawn at 4 staff spaces to the em. */
        const val STAFF_SPACES_PER_EM = 4f
    }
}
