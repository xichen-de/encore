package app.encore.french.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReviewQueueOrderTest {
    @Test fun shufflesWithinStateGroupsWhilePreservingPriority() {
        val cards = buildList {
            CardState.entries.forEach { state ->
                repeat(5) { index -> add(card(id = state.ordinal * 10L + index, state = state)) }
            }
        }

        val shuffled = ReviewQueueOrder.shuffled(cards, Random(42))

        assertEquals(
            listOf(CardState.REVIEW, CardState.RELEARNING, CardState.LEARNING, CardState.NEW),
            shuffled.map(CardEntity::state).distinct()
        )
        CardState.entries.forEach { state ->
            val before = cards.filter { it.state == state }
            val after = shuffled.filter { it.state == state }
            assertEquals(before.map(CardEntity::id).toSet(), after.map(CardEntity::id).toSet())
            assertNotEquals(before.map(CardEntity::id), after.map(CardEntity::id))
        }
    }

    @Test fun keepsEveryCardExactlyOnce() {
        val cards = listOf(
            card(1, CardState.NEW),
            card(2, CardState.REVIEW),
            card(3, CardState.LEARNING),
            card(4, CardState.RELEARNING)
        )

        val shuffled = ReviewQueueOrder.shuffled(cards, Random(7))

        assertEquals(cards.map(CardEntity::id).toSet(), shuffled.map(CardEntity::id).toSet())
        assertEquals(cards.size, shuffled.size)
    }

    private fun card(id: Long, state: CardState) = CardEntity(
        id = id,
        deckName = "Test",
        front = "front-$id",
        back = "back-$id",
        fingerprint = "fingerprint-$id",
        normalizedFront = "front-$id",
        state = state,
        createdAt = id
    )
}
