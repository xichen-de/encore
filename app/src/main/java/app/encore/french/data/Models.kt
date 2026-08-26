package app.encore.french.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }
enum class Grade { AGAIN, HARD, GOOD, EASY }

@Entity(
    tableName = "cards",
    indices = [Index("fingerprint"), Index("normalizedFront"), Index("dueAt")]
)
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckName: String,
    val front: String,
    val back: String,
    val gender: String? = null,
    val example: String? = null,
    val exampleTranslation: String? = null,
    val note: String? = null,
    val tags: String = "",
    val fingerprint: String,
    val normalizedFront: String,
    val state: CardState = CardState.NEW,
    val dueAt: Long = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 5.0,
    val repetitions: Int = 0,
    val lapses: Int = 0,
    val learningStep: Int = 0,
    val scheduledDays: Int = 0,
    val createdAt: Long,
    val lastReviewedAt: Long? = null
)

@Entity(tableName = "review_logs", indices = [Index("cardId")])
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val grade: Grade,
    val reviewedAt: Long,
    val previousDueAt: Long,
    val scheduledDays: Double,
    val elapsedDays: Int,
    val stateBefore: CardState,
    val stabilityBefore: Double,
    val difficultyBefore: Double,
    val stateAfter: CardState,
    val stabilityAfter: Double,
    val difficultyAfter: Double
)

data class TodayCounts(val due: Int, val new: Int)

data class DeckCount(val deckName: String, val count: Int)

data class ImportCard(
    val front: String,
    val back: String,
    val gender: String? = null,
    val example: String? = null,
    val exampleTranslation: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList()
)

data class FDeck(val name: String, val cards: List<ImportCard>)

enum class DuplicateImportAction { SKIP, UPDATE_MISSING, MOVE_TO_DECK, KEEP_COPY }

data class DuplicateMatch(val incoming: ImportCard, val existing: CardEntity)

data class ImportOutcome(val added: Int, val updated: Int = 0, val moved: Int = 0) {
    val changed: Int get() = added + updated + moved
}

data class ImportPreview(
    val deck: FDeck,
    val newCards: List<ImportCard>,
    val duplicateCount: Int,
    val conflictCount: Int,
    val duplicateMatches: List<DuplicateMatch> = emptyList(),
    val repeatedInFileCount: Int = 0
) {
    val totalCount: Int get() = deck.cards.size
}

sealed class DeckParseException(message: String) : IllegalArgumentException(message) {
    class Invalid(message: String) : DeckParseException(message)
    class UnsupportedVersion(version: Int) : DeckParseException("This deck uses unsupported fdeck version $version. Encore supports version 1.")
}
