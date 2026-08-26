package app.encore.french.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

class Converters {
    @TypeConverter fun fromState(value: CardState): String = value.name
    @TypeConverter fun toState(value: String): CardState = CardState.valueOf(value)
    @TypeConverter fun fromGrade(value: Grade): String = value.name
    @TypeConverter fun toGrade(value: String): Grade = Grade.valueOf(value)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY front COLLATE NOCASE, back COLLATE NOCASE, id")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckName = :deckName ORDER BY front COLLATE NOCASE, back COLLATE NOCASE, id")
    fun observeDeck(deckName: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE front LIKE '%' || :query || '%' OR back LIKE '%' || :query || '%' ORDER BY front COLLATE NOCASE, back COLLATE NOCASE, id")
    fun search(query: String): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckName = :deckName AND (front LIKE '%' || :query || '%' OR back LIKE '%' || :query || '%') ORDER BY front COLLATE NOCASE, back COLLATE NOCASE, id")
    fun searchDeck(query: String, deckName: String): Flow<List<CardEntity>>

    @Query("SELECT DISTINCT deckName FROM cards ORDER BY deckName COLLATE NOCASE")
    fun observeDeckNames(): Flow<List<String>>

    @Query("SELECT deckName, COUNT(*) AS count FROM cards GROUP BY deckName ORDER BY deckName COLLATE NOCASE")
    fun observeDeckCounts(): Flow<List<DeckCount>>

    @Query("SELECT id FROM cards WHERE deckName = :deckName")
    suspend fun idsForDeck(deckName: String): List<Long>

    @Query("UPDATE cards SET deckName = :newName WHERE deckName = :oldName")
    suspend fun renameDeck(oldName: String, newName: String)

    @Query("SELECT * FROM cards WHERE dueAt <= :now ORDER BY CASE state WHEN 'REVIEW' THEN 0 WHEN 'RELEARNING' THEN 1 WHEN 'LEARNING' THEN 2 ELSE 3 END, dueAt, createdAt LIMIT :limit")
    suspend fun reviewQueue(now: Long, limit: Int = 100): List<CardEntity>

    @Query("SELECT * FROM cards WHERE deckName = :deckName AND dueAt <= :now ORDER BY CASE state WHEN 'REVIEW' THEN 0 WHEN 'RELEARNING' THEN 1 WHEN 'LEARNING' THEN 2 ELSE 3 END, dueAt, createdAt LIMIT :limit")
    suspend fun reviewQueueForDeck(now: Long, deckName: String, limit: Int = 100): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE state != 'NEW' AND dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE state = 'NEW'")
    fun observeNewCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE deckName = :deckName AND state != 'NEW' AND dueAt <= :now")
    fun observeDueCountForDeck(now: Long, deckName: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cards WHERE deckName = :deckName AND state = 'NEW'")
    fun observeNewCountForDeck(deckName: String): Flow<Int>

    @Query("SELECT * FROM cards WHERE fingerprint IN (:fingerprints) ORDER BY createdAt")
    suspend fun existingCards(fingerprints: List<String>): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE fingerprint = :fingerprint")
    suspend fun fingerprintCount(fingerprint: String): Int

    @Query("SELECT COUNT(*) FROM cards WHERE fingerprint = :fingerprint AND id != :excludedId")
    suspend fun fingerprintCountExcept(fingerprint: String, excludedId: Long): Int

    @Query("SELECT DISTINCT normalizedFront FROM cards WHERE normalizedFront IN (:fronts)")
    suspend fun existingFronts(fronts: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    @Update suspend fun update(card: CardEntity)
    @Update suspend fun updateAll(cards: List<CardEntity>)
    @Query("SELECT * FROM cards WHERE id IN (:ids)") suspend fun findByIds(ids: List<Long>): List<CardEntity>
    @Query("DELETE FROM cards WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface ReviewDao {
    @Insert suspend fun insert(log: ReviewLogEntity)
    @Query("DELETE FROM review_logs WHERE cardId IN (:cardIds)")
    suspend fun deleteForCards(cardIds: List<Long>)
}

@Database(entities = [CardEntity::class, ReviewLogEntity::class], version = 4, exportSchema = true)
@TypeConverters(Converters::class)
abstract class EncoreDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        @Volatile private var instance: EncoreDatabase? = null
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN learningStep INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cards ADD COLUMN scheduledDays INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE review_logs ADD COLUMN elapsedDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN stateBefore TEXT NOT NULL DEFAULT 'NEW'")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN stabilityBefore REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN difficultyBefore REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN stateAfter TEXT NOT NULL DEFAULT 'NEW'")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN stabilityAfter REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE review_logs ADD COLUMN difficultyAfter REAL NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_cards_fingerprint")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cards_fingerprint ON cards(fingerprint)")
            }
        }
        fun get(context: Context): EncoreDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, EncoreDatabase::class.java, "encore.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
