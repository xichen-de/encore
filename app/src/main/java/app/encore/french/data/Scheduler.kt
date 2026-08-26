package app.encore.french.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class ScheduleResult(val card: CardEntity, val intervalDays: Double, val elapsedDays: Int)
private data class MemoryState(val stability: Double, val difficulty: Double)

/**
 * FSRS-6 using the official 21 default parameters and 90% desired retention.
 * Learning steps: 1m, 10m. Relearning step: 10m. Fuzzing is intentionally off,
 * matching the reference default and keeping schedules deterministic.
 */
object Scheduler {
    const val DESIRED_RETENTION = 0.90
    const val MAXIMUM_INTERVAL_DAYS = 36_500
    private const val DAY_MS = 86_400_000L
    private const val MINUTE_MS = 60_000L
    private const val S_MIN = 0.001
    private const val S_MAX = 36_500.0

    val DEFAULT_WEIGHTS = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194,
        0.001, 1.8722, 0.1666, 0.796, 1.4835, 0.0614, 0.2629,
        1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542
    )
    private val decay = -DEFAULT_WEIGHTS[20]
    private val factor = exp(ln(0.9) / decay) - 1.0
    private val intervalModifier = (DESIRED_RETENTION.pow(1.0 / decay) - 1.0) / factor

    fun grade(card: CardEntity, grade: Grade, now: Long): ScheduleResult {
        val elapsedDays = elapsedDays(card.lastReviewedAt, now)
        val nextMemory = nextMemoryState(card, grade, elapsedDays)
        val scheduling = schedule(card, grade, nextMemory.stability, elapsedDays)
        val updated = card.copy(
            state = scheduling.state,
            dueAt = now + scheduling.delayMillis,
            stability = nextMemory.stability,
            difficulty = nextMemory.difficulty,
            repetitions = card.repetitions + 1,
            lapses = card.lapses + if (grade == Grade.AGAIN && card.state == CardState.REVIEW) 1 else 0,
            learningStep = scheduling.learningStep,
            scheduledDays = scheduling.scheduledDays,
            lastReviewedAt = now
        )
        return ScheduleResult(updated, scheduling.delayMillis.toDouble() / DAY_MS, elapsedDays)
    }

    fun preview(card: CardEntity, now: Long): Map<Grade, ScheduleResult> =
        Grade.entries.associateWith { grade(card, it, now) }

    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + factor * elapsedDays / stability).pow(decay)

    private fun nextMemoryState(card: CardEntity, grade: Grade, elapsedDays: Int): MemoryState {
        val rating = grade.rating
        if (card.state == CardState.NEW || card.stability <= 0.0 || card.difficulty <= 0.0) {
            return MemoryState(initStability(rating), initDifficulty(rating))
        }
        val r = retrievability(elapsedDays.toDouble(), card.stability)
        val stability = when {
            elapsedDays == 0 -> nextShortTermStability(card.stability, rating)
            grade == Grade.AGAIN -> nextForgetStability(card.difficulty, card.stability, r)
            else -> nextRecallStability(card.difficulty, card.stability, r, grade)
        }
        return MemoryState(stability, nextDifficulty(card.difficulty, rating))
    }

    private data class Scheduling(val state: CardState, val delayMillis: Long, val learningStep: Int, val scheduledDays: Int)

    private fun schedule(card: CardEntity, grade: Grade, stability: Double, elapsedDays: Int): Scheduling {
        val isReviewLapse = card.state == CardState.REVIEW && grade == Grade.AGAIN
        val steps = if (card.state == CardState.REVIEW || card.state == CardState.RELEARNING) intArrayOf(10) else intArrayOf(1, 10)
        val step = card.learningStep.coerceIn(0, steps.size)
        val minutesAndStep = if (card.state == CardState.REVIEW && grade != Grade.AGAIN) null else when (grade) {
            Grade.AGAIN -> steps.first() to 0
            Grade.HARD -> {
                val minutes = if (steps.size == 1) (steps.first() * 1.5).roundToInt() else ((steps[0] + steps[1]) / 2.0).roundToInt()
                minutes to step
            }
            Grade.GOOD -> steps.getOrNull(step + 1)?.let { it to (step + 1) }
            Grade.EASY -> null
        }

        if (minutesAndStep != null && minutesAndStep.first in 1..1439) {
            val state = if (isReviewLapse || card.state == CardState.RELEARNING) CardState.RELEARNING else CardState.LEARNING
            return Scheduling(state, minutesAndStep.first * MINUTE_MS, minutesAndStep.second, 0)
        }

        var interval = nextInterval(stability)
        if (card.state == CardState.REVIEW && grade != Grade.AGAIN) {
            val hardInterval = nextInterval(nextMemoryState(card, Grade.HARD, elapsedDays).stability)
            val rawGood = nextInterval(nextMemoryState(card, Grade.GOOD, elapsedDays).stability)
            val goodInterval = max(rawGood, min(hardInterval, rawGood) + 1)
            interval = when (grade) {
                Grade.HARD -> min(interval, rawGood)
                Grade.GOOD -> goodInterval
                Grade.EASY -> max(interval, goodInterval + 1)
                Grade.AGAIN -> interval
            }
        }
        return Scheduling(CardState.REVIEW, interval * DAY_MS, 0, interval)
    }

    private fun nextInterval(stability: Double): Int =
        (stability * intervalModifier).roundToInt().coerceIn(1, MAXIMUM_INTERVAL_DAYS)

    private fun initStability(rating: Int): Double = max(DEFAULT_WEIGHTS[rating - 1], 0.1)

    private fun initDifficulty(rating: Int): Double =
        (DEFAULT_WEIGHTS[4] - exp((rating - 1) * DEFAULT_WEIGHTS[5]) + 1.0).coerceIn(1.0, 10.0)

    private fun nextDifficulty(difficulty: Double, rating: Int): Double {
        val delta = -DEFAULT_WEIGHTS[6] * (rating - 3)
        val damped = delta * (10.0 - difficulty) / 9.0
        val next = difficulty + damped
        return (DEFAULT_WEIGHTS[7] * initDifficulty(4) + (1.0 - DEFAULT_WEIGHTS[7]) * next).coerceIn(1.0, 10.0)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, grade: Grade): Double {
        val hardPenalty = if (grade == Grade.HARD) DEFAULT_WEIGHTS[15] else 1.0
        val easyBonus = if (grade == Grade.EASY) DEFAULT_WEIGHTS[16] else 1.0
        return (s * (1.0 + exp(DEFAULT_WEIGHTS[8]) * (11.0 - d) * s.pow(-DEFAULT_WEIGHTS[9]) *
            (exp((1.0 - r) * DEFAULT_WEIGHTS[10]) - 1.0) * hardPenalty * easyBonus)).coerceIn(S_MIN, S_MAX)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val failed = DEFAULT_WEIGHTS[11] * d.pow(-DEFAULT_WEIGHTS[12]) *
            ((s + 1.0).pow(DEFAULT_WEIGHTS[13]) - 1.0) * exp((1.0 - r) * DEFAULT_WEIGHTS[14])
        val upperBound = s / exp(DEFAULT_WEIGHTS[17] * DEFAULT_WEIGHTS[18])
        return min(max(failed, S_MIN), upperBound).coerceIn(S_MIN, S_MAX)
    }

    private fun nextShortTermStability(s: Double, rating: Int): Double {
        val increase = s.pow(-DEFAULT_WEIGHTS[19]) * exp(DEFAULT_WEIGHTS[17] * (rating - 3 + DEFAULT_WEIGHTS[18]))
        val bounded = if (rating >= 2) max(increase, 1.0) else increase
        return (s * bounded).coerceIn(S_MIN, S_MAX)
    }

    private fun elapsedDays(lastReview: Long?, now: Long): Int {
        if (lastReview == null) return 0
        val previousDate = Instant.ofEpochMilli(lastReview).atZone(ZoneOffset.UTC).toLocalDate()
        val currentDate = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        return max(0, ChronoUnit.DAYS.between(previousDate, currentDate).toInt())
    }

    private val Grade.rating: Int get() = when (this) {
        Grade.AGAIN -> 1
        Grade.HARD -> 2
        Grade.GOOD -> 3
        Grade.EASY -> 4
    }
}
