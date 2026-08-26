package app.encore.french.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintsTest {
    @Test fun unicodeCaseAndWhitespaceNormalizeToSameFingerprint() {
        val composed = Fingerprints.card("  ÉGLISE ", " Church ")
        val decomposed = Fingerprints.card("e\u0301glise", "church")
        assertEquals(composed, decomposed)
    }

    @Test fun phrasesAndAccentsRemainMeaningful() {
        assertEquals("à côté de", Fingerprints.normalize("  À CÔTÉ   DE "))
        assertEquals("dépêcher", Fingerprints.normalize("DÉPÊCHER"))
        assertEquals("où", Fingerprints.normalize("OÙ"))
    }

    @Test fun sameFrontWithDifferentBackIsNotDuplicate() {
        assertNotEquals(Fingerprints.card("l’endroit", "place"), Fingerprints.card("l’endroit", "location"))
    }

    @Test fun repeatedDeckImportHasNoNewCards() {
        val card = ImportCard("à côté de", "next to")
        val deck = FDeck("Lesson 15", listOf(card, card))
        val existing = listOf(CardFactory.manual(card, "Existing", 1L).copy(id = 1L))
        val preview = ImportPlanner.plan(deck, existing, setOf(Fingerprints.normalize(card.front)))
        assertEquals(0, preview.newCards.size)
        assertEquals(2, preview.duplicateCount)
        assertEquals(1, preview.duplicateMatches.size)
        assertEquals(1, preview.repeatedInFileCount)
    }

    @Test fun changedTranslationIsReportedAsConflictNotOverwrite() {
        val card = ImportCard("L’ENDROIT", "location", "m")
        val preview = ImportPlanner.plan(FDeck("Changed", listOf(card)), emptyList(), setOf("l’endroit"))
        assertEquals(1, preview.newCards.size)
        assertEquals(1, preview.conflictCount)
    }

    @Test fun manuallyCreatedCardIsTrimmedDeduplicatedAndImmediatelyDue() {
        val entity = CardFactory.manual(
            ImportCard("  à côté de ", " next to ", tags = listOf(" assimil ", "lesson-15", "assimil")),
            "  My phrases ", 42L
        )
        assertEquals("à côté de", entity.front)
        assertEquals("next to", entity.back)
        assertEquals("My phrases", entity.deckName)
        assertEquals(listOf("assimil", "lesson-15"), entity.tags.split(TAG_SEPARATOR))
        assertEquals(Fingerprints.card("À CÔTÉ DE", "NEXT TO"), entity.fingerprint)
        assertEquals(CardState.NEW, entity.state)
        assertEquals(42L, entity.dueAt)
        assertTrue(entity.lastReviewedAt == null)
    }

    @Test fun editingContentPreservesReviewScheduleAndIdentity() {
        val original = CardFactory.manual(ImportCard("église", "church"), "Lesson 1", 42L).copy(
            id = 7L, state = CardState.REVIEW, dueAt = 9_999L, stability = 4.2,
            difficulty = 3.8, repetitions = 5, lapses = 1, lastReviewedAt = 8_000L
        )
        val edited = CardFactory.updated(original, ImportCard("l’église", "the church", "f"), "My cards")
        assertEquals(7L, edited.id)
        assertEquals(CardState.REVIEW, edited.state)
        assertEquals(9_999L, edited.dueAt)
        assertEquals(5, edited.repetitions)
        assertEquals(8_000L, edited.lastReviewedAt)
        assertEquals("l’église", edited.front)
        assertEquals("f", edited.gender)
    }

    @Test fun resettingProgressPreservesContentAndReturnsCardToNewQueue() {
        val reviewed = CardFactory.manual(ImportCard("où", "where", tags = listOf("lesson-2")), "Questions", 10L).copy(
            id = 12L, state = CardState.REVIEW, dueAt = 50_000L, stability = 14.0,
            difficulty = 3.5, repetitions = 11, lapses = 2, learningStep = 1,
            scheduledDays = 14, lastReviewedAt = 40_000L
        )
        val reset = CardFactory.resetProgress(reviewed, 60_000L)
        assertEquals(12L, reset.id)
        assertEquals("où", reset.front)
        assertEquals("Questions", reset.deckName)
        assertEquals(reviewed.fingerprint, reset.fingerprint)
        assertEquals(CardState.NEW, reset.state)
        assertEquals(60_000L, reset.dueAt)
        assertEquals(0, reset.repetitions)
        assertEquals(0.0, reset.stability, 0.0)
        assertTrue(reset.lastReviewedAt == null)
    }

    @Test fun metadataResolutionOnlyFillsMissingValuesAndUnionsTags() {
        val existing = CardFactory.manual(
            ImportCard("l’endroit", "place", gender = "m", note = "Keep this", tags = listOf("mine")),
            "Existing", 1L
        )
        val incoming = ImportCard(
            "L’ENDROIT", "PLACE", gender = "f", example = "C’est l’endroit idéal.",
            exampleTranslation = "It’s the ideal place.", note = "Do not overwrite", tags = listOf("assimil", "mine")
        )
        val merged = ImportResolver.mergeMissingMetadata(existing, incoming)
        assertEquals("m", merged.gender)
        assertEquals("Keep this", merged.note)
        assertEquals("C’est l’endroit idéal.", merged.example)
        assertEquals("It’s the ideal place.", merged.exampleTranslation)
        assertEquals(listOf("mine", "assimil"), merged.tags.split(TAG_SEPARATOR))
        assertEquals(existing.state, merged.state)
        assertEquals(existing.dueAt, merged.dueAt)
    }
}
