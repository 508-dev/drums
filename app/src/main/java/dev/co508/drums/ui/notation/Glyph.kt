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
    NOTEHEAD_HALF('\uE0A3', 0f, -0.5f, 1.18f, 0.5f),
    NOTEHEAD_WHOLE('\uE0A2', 0f, -0.5f, 1.688f, 0.5f),
    FLAG_8TH_UP('\uE240', 0f, -3.24f, 1.056f, 0.036f),
    FLAG_16TH_UP('\uE242', 0f, -3.252f, 1.116f, 0.008f),
    FLAG_32ND_UP('\uE244', 0f, -3.248f, 1.044f, 0.596f),
    AUGMENTATION_DOT('\uE1E7', 0f, -0.2f, 0.4f, 0.2f),
    ACCENT_BELOW('\uE4A1', 0f, -0.976f, 1.356f, 0f),
    MARCATO_BELOW('\uE4AD', -0.004f, -1.016f, 0.94f, 0f),
    TREMOLO_1('\uE220', -0.6f, -0.372f, 0.6f, 0.376f),
    TREMOLO_2('\uE221', -0.604f, -0.748f, 0.596f, 0.748f),
    TREMOLO_3('\uE222', -0.6f, -1.12f, 0.6f, 1.112f),
    ACCIACCATURA_STEM_UP('\uE560', 0f, -0.332f, 1.428f, 2.096f),
    PERCUSSION_CLEF('\uE069', 0f, -1f, 1.528f, 1f),
    REST_WHOLE('\uE4E3', 0f, -0.54f, 1.128f, 0.036f),
    REST_HALF('\uE4E4', 0f, -0.008f, 1.128f, 0.568f),
    REST_QUARTER('\uE4E5', 0.004f, -1.5f, 1.08f, 1.492f),
    REST_8TH('\uE4E6', 0f, -1.004f, 0.988f, 0.696f),
    REST_16TH('\uE4E7', 0f, -2f, 1.28f, 0.716f),
    REST_32ND('\uE4E8', 0f, -2f, 1.452f, 1.704f),
    REPEAT_DOT('\uE044', 0f, -0.2f, 0.4f, 0.2f),
    TIME_SIG_0('\uE080', 0.08f, -1f, 1.8f, 1.004f),
    TIME_SIG_1('\uE081', 0.08f, -1f, 1.256f, 1.004f),
    TIME_SIG_2('\uE082', 0.08f, -1.028f, 1.704f, 1.016f),
    TIME_SIG_3('\uE083', 0.08f, -1.004f, 1.604f, 0.996f),
    TIME_SIG_4('\uE084', 0.08f, -1f, 1.8f, 1.004f),
    TIME_SIG_5('\uE085', 0.08f, -1.004f, 1.532f, 0.984f),
    TIME_SIG_6('\uE086', 0.08f, -0.996f, 1.656f, 1.004f),
    TIME_SIG_7('\uE087', 0.08f, -1f, 1.684f, 0.996f),
    TIME_SIG_8('\uE088', 0.08f, -1.036f, 1.664f, 1.036f),
    TIME_SIG_9('\uE089', 0.08f, -0.996f, 1.656f, 1.004f),
    TUPLET_0('\uE880', 0f, -0.032f, 1.272f, 1.5f),
    TUPLET_1('\uE881', 0.04f, 0f, 1.024f, 1.488f),
    TUPLET_2('\uE882', 0.04f, -0.024f, 1.316f, 1.5f),
    TUPLET_3('\uE883', 0.04f, -0.032f, 1.224f, 1.5f),
    TUPLET_4('\uE884', 0.04f, 0f, 1.252f, 1.488f),
    TUPLET_5('\uE885', 0.04f, -0.032f, 1.308f, 1.492f),
    TUPLET_6('\uE886', 0.04f, -0.032f, 1.256f, 1.5f),
    TUPLET_7('\uE887', 0.12f, -0.016f, 1.332f, 1.488f),
    TUPLET_8('\uE888', 0.04f, -0.032f, 1.292f, 1.5f),
    TUPLET_9('\uE889', 0.04f, -0.032f, 1.256f, 1.5f),
    ;

    val text: String = codepoint.toString()
    val width: Float get() = east - west
    val height: Float get() = north - south
    val centerX: Float get() = (west + east) / 2
    val centerY: Float get() = (south + north) / 2

    companion object {
        /** Staff spaces per em: SMuFL fonts are drawn at 4 staff spaces to the em. */
        const val STAFF_SPACES_PER_EM = 4f

        /** Where an up-stem attaches to a black or half notehead (Bravura's stemUpSE anchor). */
        const val STEM_UP_X = 1.18f
        const val STEM_UP_Y = 0.168f

        fun timeSignatureDigit(d: Int): Glyph = entries.first { it.name == "TIME_SIG_$d" }

        fun tupletDigit(d: Int): Glyph = entries.first { it.name == "TUPLET_$d" }
    }
}
