package dev.co508.drums.lesson

import kotlinx.serialization.Serializable

/**
 * A pack's `pack.json`: the lesson details MusicXML doesn't hold (DECISIONS.md,
 * "Lesson Details Live In pack.json"). The format is described in
 * docs/pack-format.md.
 */
@Serializable
data class PackManifest(
    /** The format version of this file; this app reads [CURRENT_FORMAT]. */
    val format: Int,
    val id: String,
    val title: String,
    /** The pack's own version, raised whenever its content changes. */
    val version: Int,
    val author: String,
    /** An SPDX licence identifier for the pack's lessons, such as "CC-BY-SA-4.0". */
    val license: String,
    val lessons: List<LessonManifest>,
) {
    companion object {
        const val CURRENT_FORMAT = 1
    }
}

@Serializable
data class LessonManifest(
    /** Unique within the pack; stable, because practice history refers to it. */
    val id: String,
    /** The MusicXML file, relative to the pack folder. */
    val file: String,
    val title: String,
    val family: Family,
    /** 1 (first steps) and up. */
    val level: Int,
    /** In the lesson's beat unit per minute (a dotted quarter in compound time). */
    val tempo: TempoRange,
    val description: String,
    val tips: List<String> = emptyList(),
)

@Serializable
data class TempoRange(
    val min: Int,
    val max: Int,
    val start: Int,
)

/** The library's lesson families (DECISIONS.md, "Screens"). */
@Serializable
enum class Family {
    @kotlinx.serialization.SerialName("timing")
    TIMING,

    @kotlinx.serialization.SerialName("rolls")
    ROLLS,

    @kotlinx.serialization.SerialName("diddles")
    DIDDLES,

    @kotlinx.serialization.SerialName("flams")
    FLAMS,

    @kotlinx.serialization.SerialName("drags")
    DRAGS,
}

/** A lesson ready to practice: its details and its notation. */
data class Lesson(
    val packId: String,
    val manifest: LessonManifest,
    val notation: Notation,
) {
    val id: String get() = manifest.id
}

data class Pack(
    val manifest: PackManifest,
    val lessons: List<Lesson>,
)
