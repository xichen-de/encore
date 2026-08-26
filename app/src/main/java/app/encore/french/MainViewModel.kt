package app.encore.french

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.encore.french.data.CardEntity
import app.encore.french.data.CardState
import app.encore.french.data.DeckCount
import app.encore.french.data.DeckParseException
import app.encore.french.data.DuplicateImportAction
import app.encore.french.data.EncoreRepository
import app.encore.french.data.FDeckCodec
import app.encore.french.data.Grade
import app.encore.french.data.ImportPreview
import app.encore.french.data.ImportOutcome
import app.encore.french.data.ImportCard
import app.encore.french.data.TodayCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportState {
    data object Idle : ImportState
    data class Loading(val message: String = "Reading deck…") : ImportState
    data class Preview(val value: ImportPreview) : ImportState
    data class Error(val message: String) : ImportState
    data class Complete(val outcome: ImportOutcome) : ImportState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: EncoreRepository = (application as EncoreApplication).repository
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }
    val selectedDeck = MutableStateFlow<String?>(null)
    val deckNames = repository.observeDeckNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deckCounts = repository.observeDeckCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today = combine(clock, selectedDeck) { now, deck -> now to deck }
        .flatMapLatest { (now, deck) -> repository.observeToday(now, deck) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayCounts(0, 0))
    private val query = MutableStateFlow("")
    val cards = combine(query, selectedDeck) { text, deck -> text to deck }
        .flatMapLatest { (text, deck) -> repository.observeCards(text, deck) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importTargetDeck = MutableStateFlow("")
    val importDuplicateAction = MutableStateFlow(DuplicateImportAction.SKIP)
    val reviewCards = MutableStateFlow<List<CardEntity>>(emptyList())
    val nextLearningDueAt = MutableStateFlow<Long?>(null)
    private val pendingLearning = mutableMapOf<Long, CardEntity>()
    private val learningJobs = mutableMapOf<Long, Job>()
    private var reviewSession = 0
    private var reviewActive = false

    fun search(value: String) { query.value = value }

    fun readDeck(uri: Uri) {
        importState.value = ImportState.Loading()
        viewModelScope.launch {
            importState.value = try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw DeckParseException.Invalid("Android could not open this file.")
                }
                val deck = FDeckCodec.parse(json)
                importTargetDeck.value = deck.name
                importDuplicateAction.value = DuplicateImportAction.SKIP
                ImportState.Preview(repository.preview(deck))
            } catch (error: Exception) {
                ImportState.Error(error.message ?: "This file could not be imported.")
            }
        }
    }

    fun importDeck() {
        val preview = (importState.value as? ImportState.Preview)?.value ?: return
        importState.value = ImportState.Loading("Saving cards…")
        viewModelScope.launch {
            runCatching { repository.import(preview, System.currentTimeMillis(), importTargetDeck.value, importDuplicateAction.value) }
                .onSuccess { importState.value = ImportState.Complete(it) }
                .onFailure { importState.value = ImportState.Error(it.message ?: "The cards could not be saved.") }
        }
    }

    fun clearImport() { importState.value = ImportState.Idle; importTargetDeck.value = ""; importDuplicateAction.value = DuplicateImportAction.SKIP }
    fun setImportTargetDeck(value: String) { importTargetDeck.value = value }
    fun setImportDuplicateAction(value: DuplicateImportAction) { importDuplicateAction.value = value }
    fun selectDeck(value: String?) { selectedDeck.value = value }

    fun renameDeck(oldName: String, newName: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(runCatching { repository.renameDeck(oldName, newName) }) }
    }

    fun deleteDeck(deckName: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onComplete(runCatching { repository.deleteDeck(deckName) }) }
    }

    fun startReview() {
        reviewSession += 1
        reviewActive = true
        learningJobs.values.forEach(Job::cancel)
        learningJobs.clear()
        pendingLearning.clear()
        nextLearningDueAt.value = null
        viewModelScope.launch { reviewCards.value = repository.queue(System.currentTimeMillis(), selectedDeck.value) }
    }

    fun endReview() {
        reviewActive = false
        learningJobs.values.forEach(Job::cancel)
        learningJobs.clear()
        pendingLearning.clear()
        nextLearningDueAt.value = null
    }

    fun grade(card: CardEntity, grade: Grade) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val updated = repository.grade(card, grade, now)
            reviewCards.value = reviewCards.value.drop(1)
            if (updated.state == CardState.LEARNING || updated.state == CardState.RELEARNING) {
                scheduleLearningCard(updated)
            }
        }
    }

    private fun scheduleLearningCard(card: CardEntity) {
        pendingLearning[card.id] = card
        nextLearningDueAt.value = pendingLearning.values.minOfOrNull(CardEntity::dueAt)
        val session = reviewSession
        learningJobs[card.id]?.cancel()
        learningJobs[card.id] = viewModelScope.launch {
            delay((card.dueAt - System.currentTimeMillis()).coerceAtLeast(0))
            if (reviewActive && session == reviewSession) {
                pendingLearning.remove(card.id)
                learningJobs.remove(card.id)
                nextLearningDueAt.value = pendingLearning.values.minOfOrNull(CardEntity::dueAt)
                reviewCards.update { queue -> if (queue.any { it.id == card.id }) queue else queue + card }
            }
        }
    }

    fun delete(card: CardEntity) { viewModelScope.launch { repository.delete(card) } }

    fun deleteCards(ids: List<Long>, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onComplete(runCatching { repository.deleteCards(ids) }) }
    }

    fun resetCards(ids: List<Long>, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onComplete(runCatching { repository.resetProgress(ids, System.currentTimeMillis()) }) }
    }

    fun createCard(card: ImportCard, deckName: String, onComplete: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onComplete(runCatching { repository.addCard(card, deckName, System.currentTimeMillis()) })
        }
    }

    fun updateCard(existing: CardEntity, card: ImportCard, deckName: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onComplete(runCatching { repository.updateCard(existing, card, deckName) })
        }
    }

    fun export(uri: Uri, selected: List<CardEntity>, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val json = FDeckCodec.encode("Encore export", selected)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
                        ?: error("Android could not create the file.")
                }
            }
            onComplete(result.fold({ "Exported ${selected.size} cards" }, { it.message ?: "Export failed" }))
        }
    }

}
