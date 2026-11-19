package dev.co508.drums.history

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
}

@Database(entities = [PracticeRecord::class], version = 1, exportSchema = true)
abstract class PracticeDatabase : RoomDatabase() {
    abstract fun practice(): PracticeDao

    companion object {
        const val FILE_NAME = "practice.db"

        fun open(context: Context): PracticeDatabase =
            Room
                .databaseBuilder(context.applicationContext, PracticeDatabase::class.java, FILE_NAME)
                // Never destructively migrate practice history. Register real migrations here.
                .build()
    }
}
