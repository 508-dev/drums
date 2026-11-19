package dev.co508.drums.ui.library

import androidx.compose.runtime.saveable.listSaver
import dev.co508.drums.lesson.Family
import dev.co508.drums.lesson.Pack

enum class LibraryDestination { HISTORY, SETTINGS }

/** Small explicit back stack; stores only stable IDs, not parsed lesson objects. */
data class LibraryRoute(
    val packId: String? = null,
    val family: Family? = null,
    val lessonId: String? = null,
    val destination: LibraryDestination? = null,
) {
    val canGoBack: Boolean get() = packId != null || destination != null

    val listKey: String get() = "${packId?.length ?: 0}:${packId.orEmpty()}:${family?.name.orEmpty()}"

    fun back(): LibraryRoute =
        when {
            destination != null -> copy(destination = null)
            lessonId != null -> copy(lessonId = null)
            family != null -> copy(family = null)
            else -> LibraryRoute()
        }

    fun validated(packs: List<Pack>): LibraryRoute {
        val pack =
            packs.firstOrNull { it.manifest.id == packId }
                ?: return LibraryRoute(destination = destination)
        val validFamily = family?.takeIf { selected -> pack.lessons.any { it.manifest.family == selected } }
        val validLesson =
            lessonId?.takeIf { selected ->
                pack.lessons.any { it.id == selected && it.manifest.family == validFamily }
            }
        return copy(family = validFamily, lessonId = validLesson)
    }

    companion object {
        val Saver =
            listSaver<LibraryRoute, String>(
                save = {
                    listOf(
                        it.packId.orEmpty(),
                        it.family?.name.orEmpty(),
                        it.lessonId.orEmpty(),
                        it.destination?.name.orEmpty(),
                    )
                },
                restore = { saved ->
                    LibraryRoute(
                        packId = saved[0].ifEmpty { null },
                        family = Family.entries.firstOrNull { it.name == saved[1] },
                        lessonId = saved[2].ifEmpty { null },
                        destination = LibraryDestination.entries.firstOrNull { it.name == saved[3] },
                    )
                },
            )
    }
}

/** First appearance in pack.json is authoritative, not alphabetic or enum order. */
internal fun Pack.families(): List<Family> = lessons.map { it.manifest.family }.distinct()

internal val Family.title: String get() =
    when (this) {
        Family.TIMING -> "Timing basics"
        Family.ROLLS -> "Rolls"
        Family.DIDDLES -> "Diddles"
        Family.FLAMS -> "Flams"
        Family.DRAGS -> "Drags"
    }
