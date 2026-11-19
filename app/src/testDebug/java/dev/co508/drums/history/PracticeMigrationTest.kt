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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Version 1 is the first shipped schema: validate its baseline, then retain it for future upgrades. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PracticeMigrationTest {
    @get:Rule val helper =
        MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PracticeDatabase::class.java)

    @Test fun exportedBaselineValidatesAndReopensWithoutLosingHistory() =
        runBlocking {
            val name = "migration-test.db"
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO practice_sessions VALUES ('baseline', 123, 'pack', 'lesson', 60000, 60, 100, 1)")
                close()
            }
            // No invented v0 or destructive fallback. When v2 exists, test 1→2 here with real migrations.
            helper.runMigrationsAndValidate(name, 1, true).close()
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.databaseBuilder(context, PracticeDatabase::class.java, name).build()
            try {
                assertEquals(
                    PracticeRecord("baseline", 123, "pack", "lesson", 60000, 60, 100, true),
                    db
                        .practice()
                        .sessions()
                        .first()
                        .single(),
                )
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
}
