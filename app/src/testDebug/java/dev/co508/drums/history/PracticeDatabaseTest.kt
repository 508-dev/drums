package dev.co508.drums.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PracticeDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun applicationWritesPersistAndBestsAreScopedByPackAndLesson() =
        runBlocking {
            val name = "history-test.db"
            context.deleteDatabase(name)
            val db = Room.databaseBuilder(context, PracticeDatabase::class.java, name).build()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val history = PracticeHistory(db.practice(), scope)
                val records =
                    listOf(
                        PracticeRecord("old", 100, "a", "one", 60_000, 60, 140, true),
                        PracticeRecord("new", 200, "a", "one", 30_000, 70, 90, false),
                        PracticeRecord("other", 150, "b", "one", 1_000, 80, 80, false),
                    )
                records.forEach(history::record)
                history.record(records.first()) // Same stable session id is idempotent.
                val data = withTimeout(10_000) { history.data.first { it.sessions.size == 3 && it.bests.size == 2 } }
                assertEquals(listOf("new", "other", "old"), data.sessions.map { it.id })
                assertEquals(listOf(LessonBest("a", "one", 140), LessonBest("b", "one", 80)), data.bests)
            } finally {
                scope.cancel()
                db.close()
            }
            val reopened = Room.databaseBuilder(context, PracticeDatabase::class.java, name).build()
            try {
                val rows = reopened.practice().sessions().first()
                assertEquals(3, rows.size)
                assertEquals(60_000L, rows.last().durationMillis)
                assertEquals(true, rows.last().trainerUsed)
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun failedWritesAreVisibleAndRetryKeepsOriginalSessionId(): Unit =
        runBlocking {
            val backing = Room.inMemoryDatabaseBuilder(context, PracticeDatabase::class.java).build()
            var fail = true
            val fake =
                object : PracticeDao by backing.practice() {
                    override suspend fun insert(record: PracticeRecord) {
                        if (fail) throw java.io.IOException("full disk")
                        backing.practice().insert(record)
                    }
                }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val history = PracticeHistory(fake, scope)
                history.record(PracticeRecord("pending", 100, "pack", "lesson", 1000, 60, 60, false))
                assertNotNull(withTimeout(10_000) { history.writeError.first { it != null } })
                fail = false
                history.retry()
                val row = withTimeout(10_000) { history.data.first { it.sessions.size == 1 } }.sessions.single()
                assertEquals("pending", row.id)
                withTimeout(10_000) { history.writeError.first { it == null } }
            } finally {
                scope.cancel()
                backing.close()
            }
        }

    @Test fun scoresAttachToTheirSessionInOrderAndGoWithIt(): Unit =
        runBlocking {
            val backing = Room.inMemoryDatabaseBuilder(context, PracticeDatabase::class.java).build()
            var fail = true
            // The session's first insert fails: its score, queued after it, must wait rather than orphan.
            val fake =
                object : PracticeDao by backing.practice() {
                    override suspend fun insert(record: PracticeRecord) {
                        if (fail) throw java.io.IOException("full disk")
                        backing.practice().insert(record)
                    }
                }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val history = PracticeHistory(fake, scope)
                history.record(PracticeRecord("scored", 100, "pack", "lesson", 1000, 60, 60, false))
                history.recordScore(PracticeMigrationTest.score("scored"))
                assertNotNull(withTimeout(10_000) { history.writeError.first { it != null } })
                assertEquals(emptyList<SessionScore>(), backing.practice().scores().first())
                fail = false
                history.retry()
                val data = withTimeout(10_000) { history.data.first { it.scores.isNotEmpty() } }
                assertEquals(PracticeMigrationTest.score("scored"), data.scores["scored"])
                // Deleting a session takes its score with it.
                backing.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
                backing.openHelper.writableDatabase.execSQL("DELETE FROM practice_sessions WHERE id = 'scored'")
                assertEquals(emptyList<SessionScore>(), backing.practice().scores().first())
            } finally {
                scope.cancel()
                backing.close()
            }
        }
}
