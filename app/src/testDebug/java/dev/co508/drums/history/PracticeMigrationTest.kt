package dev.co508.drums.history

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Real upgrades from every exported schema. Version 1 shipped in v0.1.0;
 * version 2 (M5) adds scored sessions' results. No destructive fallback exists,
 * so a missing or wrong migration fails here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PracticeMigrationTest {
    @get:Rule val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PracticeDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun v1HistorySurvivesTheUpgradeToV2() =
        runBlocking {
            val name = "migration-1-2.db"
            context.deleteDatabase(name)
            // Created from the exported v1 schema, as a v0.1.0 install has it.
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO practice_sessions VALUES ('baseline', 123, 'pack', 'lesson', 60000, 60, 100, 1)")
                execSQL("INSERT INTO practice_sessions VALUES ('second', 456, 'pack', 'other', 30000, 80, 80, 0)")
                close()
            }
            // Runs MIGRATION_1_2 and checks the result against the exported v2 schema.
            helper.runMigrationsAndValidate(name, 2, true, PracticeDatabase.MIGRATION_1_2).apply {
                query("SELECT COUNT(*) FROM session_scores").use { assertTrue(it.moveToFirst() && it.getInt(0) == 0) }
                close()
            }
            val db =
                Room
                    .databaseBuilder(context, PracticeDatabase::class.java, name)
                    .addMigrations(PracticeDatabase.MIGRATION_1_2)
                    .build()
            try {
                val dao = db.practice()
                assertEquals(
                    listOf(
                        PracticeRecord("second", 456, "pack", "other", 30000, 80, 80, false),
                        PracticeRecord("baseline", 123, "pack", "lesson", 60000, 60, 100, true),
                    ),
                    dao.sessions().first(),
                )
                // The upgraded database takes scores for its old sessions.
                val score = score("baseline")
                dao.insertScore(score)
                assertEquals(listOf(score), dao.scores().first())
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }

    @Test fun theExportedV2SchemaValidatesAsCreatedFresh() {
        val name = "fresh-2.db"
        context.deleteDatabase(name)
        helper.createDatabase(name, 2).close()
        helper.runMigrationsAndValidate(name, 2, true).close()
        context.deleteDatabase(name)
    }

    companion object {
        fun score(sessionId: String) =
            SessionScore(
                sessionId = sessionId,
                strictness = "STANDARD",
                routeKey = "wired",
                calibrated = true,
                green = 40,
                yellow = 6,
                red = 2,
                misses = 3,
                extras = 1,
                meanOffsetMillis = 4.25,
                spreadMillis = 9.5,
                leftHits = 24,
                leftMisses = 2,
                leftMeanOffsetMillis = 7.0,
                rightHits = 24,
                rightMisses = 1,
                rightMeanOffsetMillis = 1.5,
                gracesHeard = 0,
                gracesUnheard = 0,
            )
    }
}
