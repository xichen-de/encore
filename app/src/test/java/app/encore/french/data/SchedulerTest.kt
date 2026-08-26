package app.encore.french.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SchedulerTest {
    private val now = 1_700_000_000_000L
    private val card = CardEntity(deckName = "Test", front = "à côté de", back = "next to",
        fingerprint = "fp", normalizedFront = "à côté de", createdAt = now, dueAt = now)

    @Test fun newCardsUseOfficialOneAndTenMinuteLearningSteps() {
        val again = Scheduler.grade(card, Grade.AGAIN, now)
        val hard = Scheduler.grade(card, Grade.HARD, now)
        val good = Scheduler.grade(card, Grade.GOOD, now)
        val easy = Scheduler.grade(card, Grade.EASY, now)
        assertEquals(now + 60_000L, again.card.dueAt)
        assertEquals(now + 6 * 60_000L, hard.card.dueAt)
        assertEquals(now + 10 * 60_000L, good.card.dueAt)
        assertEquals(now + 8 * 86_400_000L, easy.card.dueAt)
        assertEquals(CardState.LEARNING, again.card.state)
        assertEquals(CardState.LEARNING, good.card.state)
        assertEquals(1, good.card.learningStep)
        assertEquals(CardState.REVIEW, easy.card.state)
        assertClose(0.212, again.card.stability)
        assertClose(2.3065, good.card.stability)
        assertClose(8.2956, easy.card.stability)
    }

    @Test fun learningCardGraduatesThroughFsrsShortTermState() {
        val firstGood = Scheduler.grade(card, Grade.GOOD, now).card
        val graduated = Scheduler.grade(firstGood, Grade.GOOD, now + 10 * 60_000L)
        assertEquals(CardState.REVIEW, graduated.card.state)
        assertEquals(2, graduated.card.scheduledDays)
        assertEquals(0, graduated.card.learningStep)
        assertClose(2.3065, graduated.card.stability)
    }

    @Test fun failedReviewEntersTenMinuteRelearningAndRecordsLapse() {
        val reviewed = card.copy(state = CardState.REVIEW, stability = 5.0, repetitions = 3, lastReviewedAt = now - 86_400_000)
        val result = Scheduler.grade(reviewed, Grade.AGAIN, now)
        assertEquals(1, result.card.lapses)
        assertEquals(4, result.card.repetitions)
        assertEquals(CardState.RELEARNING, result.card.state)
        assertEquals(now + 10 * 60_000L, result.card.dueAt)
        assertTrue(result.card.lastReviewedAt == now)
    }

    @Test fun forgettingCurveDefinesStabilityAtNinetyPercentRetention() {
        assertClose(0.9, Scheduler.retrievability(elapsedDays = 12.5, stability = 12.5))
        assertEquals(0.90, Scheduler.DESIRED_RETENTION, 0.0)
        assertEquals(21, Scheduler.DEFAULT_WEIGHTS.size)
    }

    @Test fun matureReviewIntervalsStayOrdered() {
        val reviewed = card.copy(state = CardState.REVIEW, stability = 10.0, difficulty = 5.0,
            repetitions = 8, lastReviewedAt = now - 10 * 86_400_000L)
        val preview = Scheduler.preview(reviewed, now)
        val hard = preview.getValue(Grade.HARD).card.scheduledDays
        val good = preview.getValue(Grade.GOOD).card.scheduledDays
        val easy = preview.getValue(Grade.EASY).card.scheduledDays
        assertTrue(hard < good)
        assertTrue(good < easy)
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue("Expected $expected, got $actual", abs(expected - actual) < 1e-7)
    }
}
