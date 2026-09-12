package app.encore.french.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EncoreRepositoryDeviceTest {
    @Test fun bulkResetAndDeleteHandleMoreThanOneSqliteBatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, EncoreDatabase::class.java).build()
        try {
            val repository = EncoreRepository(db)
            val now = 1_700_000_000_000L
            val ids = db.cardDao().insertAll((1..1200).map { index ->
                CardFactory.manual(ImportCard("front $index", "back $index"), "Bulk", now)
                    .copy(state = CardState.REVIEW, repetitions = 4, stability = 10.0)
            })
            val first = db.cardDao().findByIds(listOf(ids.first())).single()
            repository.grade(first, Grade.GOOD, now)
            repository.resetProgress(ids, now)
            val reset = ids.chunked(500).flatMap { db.cardDao().findByIds(it) }
            assertEquals(1200, reset.size)
            assertTrue(reset.all { it.state == CardState.NEW && it.repetitions == 0 })
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM review_logs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            repository.grade(reset.first(), Grade.GOOD, now)
            repository.deleteCards(ids)
            assertTrue(db.cardDao().idsForDeck("Bulk").isEmpty())
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM review_logs").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            db.close()
        }
    }
}
