package app.encore.french.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.random.Random

object ReviewQueueOrder {
    private val statePriority = listOf(
        CardState.REVIEW,
        CardState.RELEARNING,
        CardState.LEARNING,
        CardState.NEW
    )

    fun shuffled(cards: List<CardEntity>, random: Random = Random.Default): List<CardEntity> =
        statePriority.flatMap { state -> cards.filter { it.state == state }.shuffled(random) }
}

class EncoreRepository(private val db: EncoreDatabase) {
    private val cards = db.cardDao()

    fun observeCards(query: String, deckName: String?): Flow<List<CardEntity>> = when {
        deckName == null && query.isBlank() -> cards.observeAll()
        deckName == null -> cards.search(query.trim())
        query.isBlank() -> cards.observeDeck(deckName)
        else -> cards.searchDeck(query.trim(), deckName)
    }

    fun observeDeckNames(): Flow<List<String>> = cards.observeDeckNames()

    fun observeDeckCounts(): Flow<List<DeckCount>> = cards.observeDeckCounts()

    suspend fun renameDeck(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == oldName) return
        cards.renameDeck(oldName, trimmed)
    }

    suspend fun deleteDeck(deckName: String) = deleteCards(cards.idsForDeck(deckName))

    fun observeToday(now: Long, deckName: String?): Flow<TodayCounts> = if (deckName == null) {
        combine(cards.observeDueCount(now), cards.observeNewCount(), ::TodayCounts)
    } else {
        combine(cards.observeDueCountForDeck(now, deckName), cards.observeNewCountForDeck(deckName), ::TodayCounts)
    }

    suspend fun preview(deck: FDeck): ImportPreview {
        val unique = deck.cards.distinctBy { Fingerprints.card(it.front, it.back) }
        val fingerprints = unique.map { Fingerprints.card(it.front, it.back) }
        val fronts = unique.map { Fingerprints.normalize(it.front) }.distinct()
        val existingCards = chunked(fingerprints) { cards.existingCards(it) }
        val existingFronts = chunked(fronts) { cards.existingFronts(it) }.toSet()
        return ImportPlanner.plan(deck, existingCards, existingFronts)
    }

    suspend fun import(
        preview: ImportPreview,
        now: Long,
        deckName: String = preview.deck.name,
        duplicateAction: DuplicateImportAction = DuplicateImportAction.SKIP
    ): ImportOutcome = db.withTransaction {
        val targetDeck = deckName.trim().ifEmpty { preview.deck.name }
        val additions = preview.newCards.toMutableList()
        val updates = mutableListOf<CardEntity>()
        var updated = 0
        var moved = 0
        when (duplicateAction) {
            DuplicateImportAction.SKIP -> Unit
            DuplicateImportAction.KEEP_COPY -> additions += preview.duplicateMatches.map(DuplicateMatch::incoming)
            DuplicateImportAction.MOVE_TO_DECK -> preview.duplicateMatches.forEach { match ->
                if (match.existing.deckName != targetDeck) {
                    updates += match.existing.copy(deckName = targetDeck)
                    moved += 1
                }
            }
            DuplicateImportAction.UPDATE_MISSING -> preview.duplicateMatches.forEach { match ->
                val merged = ImportResolver.mergeMissingMetadata(match.existing, match.incoming)
                if (merged != match.existing) { updates += merged; updated += 1 }
            }
        }
        if (updates.isNotEmpty()) cards.updateAll(updates)
        val inserted = cards.insertAll(additions.map { importEntity(it, targetDeck, now) }).count { it != -1L }
        ImportOutcome(inserted, updated, moved)
    }

    suspend fun queue(now: Long, deckName: String?, limit: Int): List<CardEntity> {
        val eligible = if (deckName == null) cards.reviewQueue(now, limit) else cards.reviewQueueForDeck(now, deckName, limit)
        return ReviewQueueOrder.shuffled(eligible)
    }
    suspend fun delete(card: CardEntity) = deleteCards(listOf(card.id))

    suspend fun deleteCards(ids: List<Long>) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.reviewDao().deleteForCards(ids)
            cards.deleteByIds(ids)
        }
    }

    suspend fun resetProgress(ids: List<Long>, now: Long) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.reviewDao().deleteForCards(ids)
            cards.updateAll(cards.findByIds(ids).map { CardFactory.resetProgress(it, now) })
        }
    }

    suspend fun addCard(card: ImportCard, deckName: String, now: Long): Boolean {
        val entity = CardFactory.manual(card, deckName, now)
        if (cards.fingerprintCount(entity.fingerprint) > 0) return false
        return cards.insertAll(listOf(entity)).single() != -1L
    }

    suspend fun updateCard(existing: CardEntity, card: ImportCard, deckName: String) {
        val updated = CardFactory.updated(existing, card, deckName)
        require(cards.fingerprintCountExcept(updated.fingerprint, existing.id) == 0) {
            "Another card already has this French and English text."
        }
        cards.update(updated)
    }

    suspend fun grade(card: CardEntity, grade: Grade, now: Long) = db.withTransaction {
        val result = Scheduler.grade(card, grade, now)
        cards.update(result.card)
        db.reviewDao().insert(ReviewLogEntity(cardId = card.id, grade = grade, reviewedAt = now,
            previousDueAt = card.dueAt, scheduledDays = result.intervalDays, elapsedDays = result.elapsedDays,
            stateBefore = card.state, stabilityBefore = card.stability, difficultyBefore = card.difficulty,
            stateAfter = result.card.state, stabilityAfter = result.card.stability, difficultyAfter = result.card.difficulty))
        result.card
    }

    private suspend fun <T, R> chunked(values: List<T>, block: suspend (List<T>) -> List<R>): List<R> {
        if (values.isEmpty()) return emptyList()
        return values.chunked(500).flatMap { block(it) }
    }

    private fun importEntity(card: ImportCard, deckName: String, now: Long) = CardEntity(
        deckName = deckName, front = card.front.trim(), back = card.back.trim(), gender = card.gender,
        example = card.example, exampleTranslation = card.exampleTranslation, note = card.note,
        tags = card.tags.joinToString(TAG_SEPARATOR), fingerprint = Fingerprints.card(card.front, card.back),
        normalizedFront = Fingerprints.normalize(card.front), dueAt = now, createdAt = now
    )

}

object ImportResolver {
    fun mergeMissingMetadata(existing: CardEntity, incoming: ImportCard): CardEntity {
        val incomingTags = incoming.tags.map(String::trim).filter(String::isNotEmpty)
        val tags = (existing.tags.split(TAG_SEPARATOR).filter(String::isNotBlank) + incomingTags).distinct().joinToString(TAG_SEPARATOR)
        return existing.copy(
            gender = existing.gender ?: incoming.gender,
            example = existing.example ?: incoming.example,
            exampleTranslation = existing.exampleTranslation ?: incoming.exampleTranslation,
            note = existing.note ?: incoming.note,
            tags = tags
        )
    }
}

object ImportPlanner {
    fun plan(deck: FDeck, existingCards: List<CardEntity>, existingFronts: Set<String>): ImportPreview {
        val unique = deck.cards.distinctBy { Fingerprints.card(it.front, it.back) }
        val existingByFingerprint = existingCards.groupBy(CardEntity::fingerprint)
        val newCards = unique.filter { Fingerprints.card(it.front, it.back) !in existingByFingerprint }
        val duplicateMatches = unique.mapNotNull { incoming ->
            existingByFingerprint[Fingerprints.card(incoming.front, incoming.back)]?.firstOrNull()?.let { DuplicateMatch(incoming, it) }
        }
        val conflicts = newCards.count { Fingerprints.normalize(it.front) in existingFronts }
        return ImportPreview(deck, newCards, deck.cards.size - newCards.size, conflicts, duplicateMatches, deck.cards.size - unique.size)
    }
}

object CardFactory {
    fun manual(card: ImportCard, deckName: String, now: Long): CardEntity {
        val front = card.front.trim()
        val back = card.back.trim()
        require(front.isNotEmpty()) { "French text is required." }
        require(back.isNotEmpty()) { "English translation is required." }
        require(card.gender == null || card.gender in setOf("m", "f")) { "Gender must be m or f." }
        return CardEntity(
            deckName = deckName.trim().ifEmpty { "My cards" }, front = front, back = back,
            gender = card.gender, example = card.example?.trim()?.takeIf(String::isNotEmpty),
            exampleTranslation = card.exampleTranslation?.trim()?.takeIf(String::isNotEmpty),
            note = card.note?.trim()?.takeIf(String::isNotEmpty),
            tags = card.tags.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(TAG_SEPARATOR),
            fingerprint = Fingerprints.card(front, back), normalizedFront = Fingerprints.normalize(front),
            dueAt = now, createdAt = now
        )
    }

    fun updated(existing: CardEntity, card: ImportCard, deckName: String): CardEntity {
        val content = manual(card, deckName, existing.createdAt)
        return existing.copy(
            deckName = content.deckName, front = content.front, back = content.back,
            gender = content.gender, example = content.example,
            exampleTranslation = content.exampleTranslation, note = content.note,
            tags = content.tags, fingerprint = content.fingerprint,
            normalizedFront = content.normalizedFront
        )
    }

    fun resetProgress(existing: CardEntity, now: Long): CardEntity = existing.copy(
        state = CardState.NEW,
        dueAt = now,
        stability = 0.0,
        difficulty = 5.0,
        repetitions = 0,
        lapses = 0,
        learningStep = 0,
        scheduledDays = 0,
        lastReviewedAt = null
    )
}
