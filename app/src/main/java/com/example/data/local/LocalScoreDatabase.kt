package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_scores")
data class LocalScoreEntity(
    @PrimaryKey val modeKey: String,
    val modeLabel: String,
    val xWins: Int = 0,
    val oWins: Int = 0,
    val draws: Int = 0,
    val totalGames: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_match_history")
data class LocalMatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeKey: String,
    val modeLabel: String,
    val winnerLabel: String,
    val movesCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface LocalScoreDao {
    @Query("SELECT * FROM local_scores ORDER BY modeKey ASC")
    fun observeAllScores(): Flow<List<LocalScoreEntity>>

    @Query("SELECT * FROM local_scores WHERE modeKey = :modeKey LIMIT 1")
    suspend fun getScoreForMode(modeKey: String): LocalScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScore(score: LocalScoreEntity)

    @Query("DELETE FROM local_scores WHERE modeKey = :modeKey")
    suspend fun resetScoreForMode(modeKey: String)

    @Query("DELETE FROM local_scores")
    suspend fun resetAllScores()

    @Query("SELECT * FROM local_match_history ORDER BY timestamp DESC LIMIT 50")
    fun observeRecentMatches(): Flow<List<LocalMatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchHistory(entry: LocalMatchHistoryEntity)

    @Query("DELETE FROM local_match_history")
    suspend fun clearMatchHistory()
}

@Database(
    entities = [LocalScoreEntity::class, LocalMatchHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun localScoreDao(): LocalScoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tictactoe_pro_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LocalScoreRepository(private val dao: LocalScoreDao) {
    val allScores: Flow<List<LocalScoreEntity>> = dao.observeAllScores()
    val recentMatches: Flow<List<LocalMatchHistoryEntity>> = dao.observeRecentMatches()

    suspend fun recordRoundOutcome(
        modeKey: String,
        modeLabel: String,
        winner: String, // "X", "O", or "DRAW"
        movesCount: Int
    ) {
        val current = dao.getScoreForMode(modeKey) ?: LocalScoreEntity(
            modeKey = modeKey,
            modeLabel = modeLabel
        )
        val updated = current.copy(
            xWins = current.xWins + if (winner == "X") 1 else 0,
            oWins = current.oWins + if (winner == "O") 1 else 0,
            draws = current.draws + if (winner == "DRAW") 1 else 0,
            totalGames = current.totalGames + 1,
            lastUpdated = System.currentTimeMillis()
        )
        dao.upsertScore(updated)
        dao.insertMatchHistory(
            LocalMatchHistoryEntity(
                modeKey = modeKey,
                modeLabel = modeLabel,
                winnerLabel = winner,
                movesCount = movesCount
            )
        )
    }

    suspend fun resetModeScore(modeKey: String) {
        dao.resetScoreForMode(modeKey)
    }

    suspend fun clearAllLocalData() {
        dao.resetAllScores()
        dao.clearMatchHistory()
    }
}
