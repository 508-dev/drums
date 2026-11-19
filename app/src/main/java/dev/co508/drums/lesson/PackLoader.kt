package dev.co508.drums.lesson

import android.content.res.AssetManager
import dev.co508.drums.musicxml.ImportResult
import dev.co508.drums.musicxml.MusicXmlImporter
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Reads the files of one pack folder, wherever it lives. */
fun interface PackSource {
    /** Opens [path], relative to the pack folder. Throws [FileNotFoundException] if it's missing. */
    fun open(path: String): InputStream
}

/** A pack folder inside the app's assets, such as `packs/builtin`. */
class AssetPackSource(
    private val assets: AssetManager,
    private val folder: String,
) : PackSource {
    override fun open(path: String): InputStream = assets.open("$folder/$path")
}

/** A pack folder on disk (tests; unpacked imports later). */
class DirectoryPackSource(
    private val folder: File,
) : PackSource {
    override fun open(path: String): InputStream {
        val file = File(folder, path).canonicalFile
        // Keep `file` entries inside the pack folder.
        if (!file.path.startsWith(folder.canonicalPath + File.separator)) throw FileNotFoundException(path)
        return file.inputStream()
    }
}

sealed interface PackResult {
    data class Success(
        val pack: Pack,
    ) : PackResult

    data class Failure(
        /** Readable problems, each naming the file (and measure and beat) concerned. */
        val errors: List<String>,
    ) : PackResult
}

/**
 * Loads and checks a pack: `pack.json`, then every lesson's MusicXML.
 *
 * With [strict] (built-in packs) the MusicXML must follow the style guide
 * exactly; see [MusicXmlImporter].
 */
class PackLoader(
    private val importer: MusicXmlImporter,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun load(
        source: PackSource,
        strict: Boolean,
    ): PackResult {
        val manifest =
            try {
                source.open(PACK_FILE).use { json.decodeFromString<PackManifest>(it.readBytes().decodeToString()) }
            } catch (e: FileNotFoundException) {
                return PackResult.Failure(listOf("$PACK_FILE is missing."))
            } catch (e: SerializationException) {
                return PackResult.Failure(listOf("$PACK_FILE: ${e.message}"))
            } catch (e: IllegalArgumentException) {
                return PackResult.Failure(listOf("$PACK_FILE: ${e.message}"))
            } catch (e: IOException) {
                return PackResult.Failure(listOf("$PACK_FILE couldn't be read: ${e.message}"))
            }

        val errors = checkManifest(manifest).toMutableList()
        val lessons = mutableListOf<Lesson>()
        for (lesson in manifest.lessons) {
            val result =
                try {
                    source.open(lesson.file).use { importer.read(it, strict) }
                } catch (e: IOException) {
                    errors += "${lesson.file} (lesson \"${lesson.id}\") is missing or unreadable."
                    continue
                }
            when (result) {
                is ImportResult.Success -> lessons += Lesson(manifest.id, lesson, result.notation)
                is ImportResult.Failure -> errors += result.errors.map { "${lesson.file}: $it" }
            }
        }
        return if (errors.isEmpty()) PackResult.Success(Pack(manifest, lessons)) else PackResult.Failure(errors)
    }

    private fun checkManifest(manifest: PackManifest): List<String> {
        val errors = mutableListOf<String>()

        fun check(
            ok: Boolean,
            message: () -> String,
        ) {
            if (!ok) errors += "$PACK_FILE: ${message()}"
        }
        check(manifest.format == PackManifest.CURRENT_FORMAT) {
            "format ${manifest.format} isn't supported (this app reads format ${PackManifest.CURRENT_FORMAT})."
        }
        check(ID.matches(manifest.id)) { "pack id \"${manifest.id}\" should be lowercase letters, digits and dashes." }
        check(manifest.title.isNotBlank()) { "the pack has no title." }
        check(manifest.lessons.isNotEmpty()) { "the pack has no lessons." }
        val duplicates =
            manifest.lessons
                .groupBy { it.id }
                .filterValues { it.size > 1 }
                .keys
        check(duplicates.isEmpty()) { "lesson ids appear more than once: ${duplicates.joinToString()}." }
        for (lesson in manifest.lessons) {
            val name = "lesson \"${lesson.id}\""
            check(ID.matches(lesson.id)) { "$name: the id should be lowercase letters, digits and dashes." }
            check(lesson.file.endsWith(".musicxml")) { "$name: the file should be an uncompressed .musicxml file." }
            check(lesson.title.isNotBlank()) { "$name has no title." }
            check(lesson.level >= 1) { "$name: the level should be 1 or more." }
            val t = lesson.tempo
            check(t.min in MIN_BPM..MAX_BPM && t.max in MIN_BPM..MAX_BPM && t.min <= t.max) {
                "$name: the tempo range ${t.min}-${t.max} should lie within $MIN_BPM-$MAX_BPM."
            }
            check(t.start in t.min..t.max) { "$name: the start tempo ${t.start} is outside ${t.min}-${t.max}." }
        }
        return errors
    }

    companion object {
        const val PACK_FILE = "pack.json"
        const val BUILT_IN_FOLDER = "packs/builtin"

        /** The tempo slider's range (DECISIONS.md, "Practice Controls"). */
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        private val ID = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
    }
}
