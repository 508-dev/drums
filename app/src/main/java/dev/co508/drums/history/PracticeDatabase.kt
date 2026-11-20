package dev.co508.drums.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeDao {
    // A retry must not duplicate a session whose first insert actually committed.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: PracticeRecord)

    @Query("SELECT * FROM practice_sessions ORDER BY startedAtMillis DESC, id DESC")
    fun sessions(): Flow<List<PracticeRecord>>

    @Query(
        "SELECT packId, lessonId, MAX(highestTempo) AS highestTempo FROM practice_sessions GROUP BY packId, lessonId ORDER BY packId, lessonId",
    )
    fun bestTempos(): Flow<List<LessonBest>>

    // A retry must not duplicate a score whose first insert committed.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertScore(score: SessionScore)

    @Query("SELECT * FROM session_scores")
    fun scores(): Flow<List<SessionScore>>
}

@Database(entities = [PracticeRecord::class, SessionScore::class], version = 2, exportSchema = true)
abstract class PracticeDatabase : RoomDatabase() {
    abstract fun practice(): PracticeDao

    companion object {
        const val FILE_NAME = "practice.db"

        /**
         * v2 (M5): scored sessions' results, in their own table keyed by the
         * session. Adds a table; touches no existing row. The SQL matches
         * `schemas/…/2.json`, which the migration test validates against.
         */
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `session_scores` (`sessionId` TEXT NOT NULL, " +
                            "`strictness` TEXT NOT NULL, `routeKey` TEXT, `calibrated` INTEGER NOT NULL, " +
                            "`green` INTEGER NOT NULL, `yellow` INTEGER NOT NULL, `red` INTEGER NOT NULL, " +
                            "`misses` INTEGER NOT NULL, `extras` INTEGER NOT NULL, `meanOffsetMillis` REAL, " +
                            "`spreadMillis` REAL, `leftHits` INTEGER NOT NULL, `leftMisses` INTEGER NOT NULL, " +
                            "`leftMeanOffsetMillis` REAL, `rightHits` INTEGER NOT NULL, " +
                            "`rightMisses` INTEGER NOT NULL, `rightMeanOffsetMillis` REAL, " +
                            "`gracesHeard` INTEGER NOT NULL, `gracesUnheard` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`sessionId`), FOREIGN KEY(`sessionId`) REFERENCES " +
                            "`practice_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                    )
                }
            }

        fun open(context: Context): PracticeDatabase =
            Room
                .databaseBuilder(context.applicationContext, PracticeDatabase::class.java, FILE_NAME)
                // Never destructively migrate practice history: every version step is a real migration.
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
