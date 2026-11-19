package dev.co508.drums.ui

import dev.co508.drums.lesson.Family
import dev.co508.drums.lesson.Lesson
import dev.co508.drums.lesson.LessonManifest
import dev.co508.drums.lesson.Notation
import dev.co508.drums.lesson.Pack
import dev.co508.drums.lesson.PackManifest
import dev.co508.drums.lesson.TempoRange
import dev.co508.drums.ui.library.LibraryDestination
import dev.co508.drums.ui.library.LibraryRoute
import dev.co508.drums.ui.library.families
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRouteTest {
    private val lessons =
        listOf(Family.TIMING, Family.DRAGS, Family.ROLLS, Family.DRAGS).mapIndexed { i, family ->
            Lesson(
                "pack",
                LessonManifest(
                    "lesson$i",
                    "lesson$i.musicxml",
                    "Test",
                    family,
                    1,
                    TempoRange(30, 200, 60),
                    "Test",
                ),
                Notation(null, null, emptyList()),
            )
        }
    private val pack =
        Pack(
            PackManifest(
                1,
                "pack",
                "Test",
                1,
                "Test",
                "CC-BY-SA-4.0",
                lessons.map { it.manifest },
            ),
            lessons,
        )

    @Test fun orderComesFromPackNotEnumOrAlphabet() {
        assertEquals(listOf(Family.TIMING, Family.DRAGS, Family.ROLLS), pack.families())
    }

    @Test fun backAndRemovedContentResolveToNearestExistingParent() {
        val practice = LibraryRoute("pack", Family.TIMING, "lesson0")
        assertEquals(practice, practice.validated(listOf(pack)))
        assertEquals(practice, practice.copy(destination = LibraryDestination.SETTINGS).back())
        assertEquals(LibraryRoute("pack", Family.TIMING), practice.back())
        assertEquals(LibraryRoute("pack"), practice.back().back())
        assertEquals(LibraryRoute(), practice.back().back().back())
        assertEquals(practice.back(), practice.copy(lessonId = "gone").validated(listOf(pack)))
        assertEquals(LibraryRoute("pack"), practice.copy(family = Family.FLAMS).validated(listOf(pack)))
        assertEquals(LibraryRoute(), practice.validated(emptyList()))
    }
}
