package app.encore.french.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.encore.french.ImportState
import app.encore.french.MainViewModel
import app.encore.french.REVIEW_LIMIT_OPTIONS
import app.encore.french.TtsController
import app.encore.french.data.CardEntity
import app.encore.french.data.CardState
import app.encore.french.data.DeckCount
import app.encore.french.data.Grade
import app.encore.french.data.DuplicateImportAction
import app.encore.french.data.ImportOutcome
import app.encore.french.data.ImportPreview
import app.encore.french.data.ImportCard
import app.encore.french.data.TAG_SEPARATOR
import app.encore.french.data.Scheduler
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Tab { TODAY, LIBRARY }

@Composable
fun EncoreApp(vm: MainViewModel, initialIntent: Intent?) {
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var reviewing by remember { mutableStateOf(false) }
    val importState by vm.importState.collectAsState()
    val deckNames by vm.deckNames.collectAsState()
    val importTargetDeck by vm.importTargetDeck.collectAsState()
    val importDuplicateAction by vm.importDuplicateAction.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val tts = remember { TtsController(context) { Toast.makeText(context, "A French text-to-speech voice is unavailable.", Toast.LENGTH_SHORT).show() } }
    DisposableEffect(Unit) { onDispose(tts::close) }
    LaunchedEffect(initialIntent) {
        if (initialIntent?.action == Intent.ACTION_VIEW) initialIntent.data?.let(vm::readDeck)
    }
    val selectedDeck by vm.selectedDeck.collectAsState()
    LaunchedEffect(deckNames, selectedDeck) {
        if (selectedDeck != null && selectedDeck !in deckNames) vm.selectDeck(null)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readDeck) }

    BackHandler(enabled = reviewing || importState !is ImportState.Idle) {
        when {
            reviewing -> { vm.endReview(); reviewing = false }
            importState !is ImportState.Idle -> vm.clearImport()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        when {
            reviewing -> ReviewScreen(vm, tts, onBack = { vm.endReview(); reviewing = false })
            importState !is ImportState.Idle -> ImportScreen(importState, importTargetDeck, deckNames, importDuplicateAction, vm::setImportTargetDeck, vm::setImportDuplicateAction, vm::importDeck, vm::clearImport,
                onChooseAgain = { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
            else -> Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                        NavigationBarItem(tab == Tab.TODAY, { tab = Tab.TODAY }, { Icon(Icons.Rounded.Home, null) }, label = { Text("Today") })
                        NavigationBarItem(tab == Tab.LIBRARY, { tab = Tab.LIBRARY }, { Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null) }, label = { Text("Library") })
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { when (tab) {
                    Tab.TODAY -> TodayScreen(vm, onReview = { vm.startReview(); reviewing = true })
                    Tab.LIBRARY -> CardsScreen(vm, snackbar, tts, onImport = { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) })
                } }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

@Composable
private fun TodayScreen(vm: MainViewModel, onReview: () -> Unit) {
    val counts by vm.today.collectAsState()
    val deckNames by vm.deckNames.collectAsState()
    val selectedDeck by vm.selectedDeck.collectAsState()
    val reviewLimit by vm.reviewLimit.collectAsState()
    val available = counts.due + counts.new
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        PageHeader("Today", selectedDeck ?: "All decks")
        DeckFilter(selectedDeck, deckNames, vm::selectDeck)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TodayMetric("Due", counts.due, Modifier.weight(1f))
                TodayMetric("New", counts.new, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Text("Cards this session", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REVIEW_LIMIT_OPTIONS.forEach { limit ->
                    FilterChip(
                        selected = reviewLimit == limit,
                        onClick = { vm.setReviewLimit(limit) },
                        label = { Text(limit.toString()) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onReview, enabled = available > 0, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (available > 0) "Start ${minOf(available, reviewLimit)}-card review" else "No cards due")
            }
        }
    }
}

@Composable
private fun TodayMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("$value", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportScreen(state: ImportState, targetDeck: String, deckNames: List<String>, duplicateAction: DuplicateImportAction, onTargetDeckChange: (String) -> Unit, onDuplicateActionChange: (DuplicateImportAction) -> Unit, onImport: () -> Unit, onClose: () -> Unit, onChooseAgain: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClose) { Icon(Icons.Rounded.Close, "Close") } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { when (state) {
            ImportState.Idle -> Unit
            is ImportState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(16.dp)); Text(state.message) }
            is ImportState.Error -> StatusPanel(Icons.Rounded.FileOpen, "Import failed", state.message, "Choose file", onChooseAgain)
            is ImportState.Complete -> StatusPanel(Icons.Rounded.Check, importOutcomeMessage(state.outcome), "", "Done", onClose)
            is ImportState.Preview -> ImportPreviewPanel(state.value, targetDeck, deckNames, duplicateAction, onTargetDeckChange, onDuplicateActionChange, onImport)
        } }
    }
}

@Composable
private fun ImportPreviewPanel(preview: ImportPreview, targetDeck: String, deckNames: List<String>, duplicateAction: DuplicateImportAction, onTargetDeckChange: (String) -> Unit, onDuplicateActionChange: (DuplicateImportAction) -> Unit, onImport: () -> Unit) {
    var showDuplicateDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FileOpen, null, Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(24.dp)); Text(preview.deck.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp)); Text("${preview.totalCount} cards", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SummaryRow("New cards", preview.newCards.size)
                SummaryRow("Already present", preview.duplicateCount)
                if (preview.conflictCount > 0) {
                    HorizontalDivider(); Text("${preview.conflictCount} ${if (preview.conflictCount == 1) "card has" else "cards have"} the same French front but a different translation. They will be imported as separate cards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        DeckChooser(targetDeck, deckNames, onTargetDeckChange, label = "Import into deck")
        if (preview.duplicateMatches.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton({ showDuplicateDialog = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Identical cards: ${duplicateAction.label}")
            }
        }
        val actionableDuplicates = preview.duplicateMatches.isNotEmpty() && duplicateAction != DuplicateImportAction.SKIP
        Spacer(Modifier.height(24.dp)); Button(onImport, enabled = targetDeck.isNotBlank() && (preview.newCards.isNotEmpty() || actionableDuplicates), modifier = Modifier.fillMaxWidth().height(56.dp).testTag("import_button")) {
            Text(if (preview.newCards.isEmpty() && !actionableDuplicates) "Nothing new to import" else "Import cards")
        }
    }
    if (showDuplicateDialog) DuplicateResolutionDialog(
        count = preview.duplicateMatches.size,
        selected = duplicateAction,
        onSelect = { onDuplicateActionChange(it); showDuplicateDialog = false },
        onDismiss = { showDuplicateDialog = false }
    )
}

private val DuplicateImportAction.label: String get() = when (this) {
    DuplicateImportAction.SKIP -> "Skip"
    DuplicateImportAction.UPDATE_MISSING -> "Fill missing details"
    DuplicateImportAction.MOVE_TO_DECK -> "Move existing"
    DuplicateImportAction.KEEP_COPY -> "Keep another copy"
}

@Composable
private fun DuplicateResolutionDialog(count: Int, selected: DuplicateImportAction, onSelect: (DuplicateImportAction) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        DuplicateImportAction.SKIP to "Leave existing cards unchanged.",
        DuplicateImportAction.UPDATE_MISSING to "Add only missing gender, examples, notes, and tags. Existing values win.",
        DuplicateImportAction.MOVE_TO_DECK to "Move each existing card into the deck selected for this import.",
        DuplicateImportAction.KEEP_COPY to "Create another independent card in the selected deck with fresh progress."
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve $count identical ${if (count == 1) "card" else "cards"}") },
        text = { Column { options.forEach { (action, description) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(action) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == action, onClick = { onSelect(action) })
                Column(Modifier.padding(start = 8.dp)) { Text(action.label, fontWeight = FontWeight.Medium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } } },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

private fun importOutcomeMessage(outcome: ImportOutcome): String = buildList {
    if (outcome.added > 0) add("${outcome.added} imported")
    if (outcome.updated > 0) add("${outcome.updated} updated")
    if (outcome.moved > 0) add("${outcome.moved} moved")
}.joinToString(" · ").ifEmpty { "No changes" }

@Composable private fun SummaryRow(label: String, number: Int) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text("$number", fontWeight = FontWeight.SemiBold) }

@Composable
private fun DeckFilter(selected: String?, deckNames: List<String>, onSelect: (String?) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { FilterChip(selected == null, { onSelect(null) }, label = { Text("All decks") }) }
        items(deckNames.size) { index ->
            val deck = deckNames[index]
            FilterChip(selected == deck, { onSelect(deck) }, label = { Text(deck) })
        }
    }
}

@Composable
private fun DeckChooser(value: String, deckNames: List<String>, onValueChange: (String) -> Unit, label: String = "Deck") {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (deckNames.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(deckNames.size) { index ->
                    val deck = deckNames[index]
                    FilterChip(value == deck, { onValueChange(deck) }, label = { Text(deck) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value, onValueChange, label = { Text("Deck name") }, supportingText = { Text("Select or create a deck") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StatusPanel(icon: ImageVector, title: String, message: String, action: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (message.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(24.dp))
        Button(onAction) { Text(action) }
    }
}

@Composable
private fun ReviewScreen(vm: MainViewModel, tts: TtsController, onBack: () -> Unit) {
    val queue by vm.reviewCards.collectAsState()
    val nextLearningDueAt by vm.nextLearningDueAt.collectAsState()
    var revealed by remember(queue.firstOrNull()?.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f)); Text(if (queue.isEmpty()) "" else "${queue.size} remaining", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
        }
        if (queue.isEmpty()) {
            val pendingMinutes = nextLearningDueAt?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000 }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pendingMinutes != null) StatusPanel(Icons.Rounded.MoreHoriz, "Next card in $pendingMinutes min", "", "Finish", onBack)
                else StatusPanel(Icons.Rounded.Check, "Review complete", "", "Done", onBack)
            }
        } else {
            val card = queue.first()
            LaunchedEffect(card.id) { tts.speak(card.front) }
            val gradePreviews = remember(card.id, card.dueAt, card.repetitions) { Scheduler.preview(card, System.currentTimeMillis()) }
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.weight(1f).fillMaxWidth().testTag("review_card"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().weight(0.27f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(card.front, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                                card.gender?.let { Text("  (${it}.)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
                            }
                            Spacer(Modifier.height(12.dp))
                            FilledIconButton({ tts.speak(card.front) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Pronounce French") }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(0.16f), contentAlignment = Alignment.Center) {
                        card.example?.let { sentence ->
                            Surface(
                                onClick = { tts.speak(sentence) },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Read example sentence", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(sentence, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().weight(0.57f), contentAlignment = Alignment.Center) {
                        if (revealed) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                HorizontalDivider(Modifier.width(64.dp)); Spacer(Modifier.height(28.dp))
                                Text(card.back, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.testTag("answer"))
                                card.exampleTranslation?.let { Spacer(Modifier.height(16.dp)); Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                if (!revealed) Button({
                    revealed = true
                    card.example?.let(tts::speak)
                }, Modifier.fillMaxWidth().height(56.dp)) { Text("Show answer") }
                else GradeBar(gradePreviews.mapValues { it.value.intervalDays }) { vm.grade(card, it) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun GradeBar(intervals: Map<Grade, Double>, onGrade: (Grade) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("grade_bar"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Grade.AGAIN to "Again", Grade.HARD to "Hard", Grade.GOOD to "Good", Grade.EASY to "Easy").forEach { (grade, label) ->
            OutlinedButton(
                onClick = { onGrade(grade) },
                modifier = Modifier.weight(1f).height(64.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label)
                    Text(formatInterval(intervals[grade] ?: 0.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun formatInterval(days: Double): String {
    val minutes = (days * 1440).roundToInt()
    return when {
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${(minutes / 60.0).roundToInt()}h"
        days < 30 -> "${days.roundToInt()}d"
        days < 365 -> "${(days / 30).roundToInt()}mo"
        else -> "${(days / 365).roundToInt()}y"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardsScreen(vm: MainViewModel, snackbar: SnackbarHostState, tts: TtsController, onImport: () -> Unit) {
    val cards by vm.cards.collectAsState()
    val deckNames by vm.deckNames.collectAsState()
    val deckCounts by vm.deckCounts.collectAsState()
    val selectedDeck by vm.selectedDeck.collectAsState()
    var search by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<CardEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CardEntity?>(null) }
    var viewing by remember { mutableStateOf<CardEntity?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var confirmExport by remember { mutableStateOf(false) }
    var managingDecks by remember { mutableStateOf(false) }
    var resetIds by remember { mutableStateOf<Set<Long>?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(it, cards) { message -> scope.launch { snackbar.showSnackbar(message) } } }
    }
    BackHandler(enabled = viewing != null && !(creating || editing != null)) { viewing = null }
    if (creating || editing != null) {
        CreateCardScreen(initial = editing, deckNames = deckNames, onBack = { creating = false; editing = null }) { card, deck ->
            val current = editing
            if (current == null) {
                vm.createCard(card, deck) { result ->
                    result.onSuccess { added ->
                        scope.launch { snackbar.showSnackbar(if (added) "Card added" else "Card already exists") }
                        if (added) creating = false
                    }.onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Couldn’t save the card") } }
                }
            } else {
                vm.updateCard(current, card, deck) { result ->
                    result.onSuccess {
                        editing = null
                        scope.launch { snackbar.showSnackbar("Card updated") }
                    }.onFailure { error ->
                        val message = if (error.message?.contains("UNIQUE", ignoreCase = true) == true) "Another card already has this French and English text" else error.message ?: "Couldn’t update the card"
                        scope.launch { snackbar.showSnackbar(message) }
                    }
                }
            }
        }
    } else {
        val focusManager = LocalFocusManager.current
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            ) {
            if (selectionMode) {
                val visibleIds = cards.mapTo(mutableSetOf(), CardEntity::id)
                val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedIds }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ selectionMode = false; selectedIds = emptySet() }) { Icon(Icons.Rounded.Close, "Cancel selection") }
                    Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    IconButton({ selectedIds = if (allVisibleSelected) selectedIds - visibleIds else selectedIds + visibleIds }, enabled = cards.isNotEmpty()) {
                        Icon(Icons.Rounded.SelectAll, if (allVisibleSelected) "Clear visible selection" else "Select all visible cards")
                    }
                    IconButton({ resetIds = selectedIds }, enabled = selectedIds.isNotEmpty()) { Icon(Icons.Rounded.RestartAlt, "Reset progress") }
                    IconButton({ confirmBulkDelete = true }, enabled = selectedIds.isNotEmpty()) { Icon(Icons.Rounded.DeleteOutline, "Delete") }
                }
            } else {
                PageHeader("Library", "${cards.size} cards") {
                    IconButton({ managingDecks = true }, enabled = deckNames.isNotEmpty()) { Icon(Icons.Rounded.Folder, "Manage decks") }
                    IconButton({ selectionMode = true }, enabled = cards.isNotEmpty()) { Icon(Icons.Rounded.Checklist, "Select") }
                    IconButton({ confirmExport = true }, enabled = cards.isNotEmpty()) { Icon(Icons.Rounded.Share, "Export") }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onImport, Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Import deck")
                    }
                    Button({ creating = true }, Modifier.weight(1f).height(52.dp).testTag("add_card")) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add card")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            DeckFilter(selectedDeck, deckNames, vm::selectDeck)
            SearchBar(inputField = { androidx.compose.material3.SearchBarDefaults.InputField(query = search, onQueryChange = { search = it; vm.search(it) }, onSearch = { focusManager.clearFocus() }, expanded = false, onExpandedChange = {}, placeholder = { Text("Search cards") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton({ search = ""; vm.search("") }) { Icon(Icons.Rounded.Close, "Clear search") } }) }, expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), content = {})
            if (cards.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (search.isBlank()) "No cards" else "No results", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else {
                val letterIndex = remember(cards) {
                    val map = LinkedHashMap<Char, Int>()
                    cards.forEachIndexed { i, card -> map.getOrPut(card.indexLetter()) { i } }
                    map
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(start = 20.dp, end = if (letterIndex.size > 1) 36.dp else 20.dp, top = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(cards, key = { it.id }) { card ->
                            LibraryCard(
                                card = card,
                                selected = card.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                                    else viewing = card
                                },
                                onSelectionChange = { checked -> selectedIds = if (checked) selectedIds + card.id else selectedIds - card.id },
                                onPronounce = { tts.speak(card.front) }
                            )
                        }
                    }
                    if (letterIndex.size > 1) AlphabetIndexBar(
                        letterIndex = letterIndex,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(0.9f).width(20.dp)
                    ) { index -> scope.launch { listState.scrollToItem(index) } }
                }
            }
            }
            viewing?.let { card ->
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CardDetailScreen(
                        card = card,
                        tts = tts,
                        onBack = { viewing = null },
                        onEdit = { viewing = null; editing = card },
                        onDelete = { deleteTarget = card },
                        onReset = { resetIds = setOf(card.id) }
                    )
                }
            }
        }
    }
    deleteTarget?.let { card -> AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("Delete card?") }, text = { Text("“${card.front}” and its review history will be removed.") }, confirmButton = { TextButton(onClick = { vm.delete(card); if (viewing?.id == card.id) viewing = null; deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } }, dismissButton = { Button({ deleteTarget = null }) { Text("Cancel") } }) }
    if (confirmBulkDelete) AlertDialog(
        onDismissRequest = { confirmBulkDelete = false },
        title = { Text("Delete ${selectedIds.size} cards?") },
        text = { Text("The selected cards and their review histories will be permanently removed.") },
        confirmButton = { TextButton(
            onClick = {
                val ids = selectedIds.toList()
                vm.deleteCards(ids) { result ->
                    result.onSuccess {
                        selectedIds = emptySet(); selectionMode = false; confirmBulkDelete = false
                        scope.launch { snackbar.showSnackbar("Deleted ${ids.size} cards") }
                    }.onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Couldn’t delete cards") } }
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Delete") } },
        dismissButton = { Button({ confirmBulkDelete = false }) { Text("Cancel") } }
    )
    resetIds?.let { ids -> AlertDialog(
        onDismissRequest = { resetIds = null },
        title = { Text(if (ids.size == 1) "Reset this card’s progress?" else "Reset ${ids.size} cards?") },
        text = { Text("Review history and FSRS memory state will be cleared. ${if (ids.size == 1) "The card" else "These cards"} will return to the new-card queue.") },
        confirmButton = { TextButton(
            onClick = {
                vm.resetCards(ids.toList()) { result ->
                    result.onSuccess {
                        resetIds = null; selectedIds = emptySet(); selectionMode = false
                        if (viewing?.id?.let { it in ids } == true) viewing = null
                        scope.launch { snackbar.showSnackbar(if (ids.size == 1) "Card progress reset" else "Reset ${ids.size} cards") }
                    }.onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Couldn’t reset progress") } }
                }
            },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) { Text("Reset") } },
        dismissButton = { Button({ resetIds = null }) { Text("Cancel") } }
    ) }
    if (confirmExport) AlertDialog(
        onDismissRequest = { confirmExport = false },
        title = { Text("Export ${cards.size} cards?") },
        text = { Text(buildString {
            append(if (selectedDeck == null) "All decks" else "Deck “$selectedDeck”")
            if (search.isNotBlank()) append(", matching “$search”")
            append(".")
        }) },
        confirmButton = { TextButton({ confirmExport = false; exporter.launch("encore-export.fdeck") }) { Text("Export") } },
        dismissButton = { TextButton({ confirmExport = false }) { Text("Cancel") } }
    )
    if (managingDecks) ManageDecksDialog(
        deckCounts = deckCounts,
        onRename = { old, new -> vm.renameDeck(old, new) { result -> result.onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Couldn’t rename deck") } } } },
        onDelete = { deck ->
            vm.deleteDeck(deck) { result ->
                result.onSuccess { scope.launch { snackbar.showSnackbar("Deck “$deck” deleted") } }
                    .onFailure { error -> scope.launch { snackbar.showSnackbar(error.message ?: "Couldn’t delete deck") } }
            }
        },
        onDismiss = { managingDecks = false }
    )
}

@Composable
private fun ManageDecksDialog(deckCounts: List<DeckCount>, onRename: (String, String) -> Unit, onDelete: (String) -> Unit, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DeckCount?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage decks") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                deckCounts.forEachIndexed { index, deck ->
                    if (index > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(deck.deckName, fontWeight = FontWeight.Medium)
                            Text("${deck.count} ${if (deck.count == 1) "card" else "cards"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ renaming = deck.deckName }) { Icon(Icons.Rounded.Edit, "Rename “${deck.deckName}”") }
                        IconButton({ deleting = deck }) { Icon(Icons.Rounded.DeleteOutline, "Delete “${deck.deckName}”") }
                    }
                }
                if (deckCounts.isEmpty()) Text("No decks yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Done") } }
    )
    renaming?.let { deck ->
        var name by remember(deck) { mutableStateOf(deck) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename deck") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Deck name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank() && name != deck) onRename(deck, name); renaming = null }, enabled = name.isNotBlank()) { Text("Rename") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete deck “${deck.deckName}”?") },
            text = { Text("${deck.count} ${if (deck.count == 1) "card" else "cards"} in this deck and their review history will be permanently removed.") },
            confirmButton = { TextButton(onClick = { onDelete(deck.deckName); deleting = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { Button({ deleting = null }) { Text("Cancel") } }
        )
    }
}

private fun CardEntity.indexLetter(): Char {
    val base = front.trim().firstOrNull()?.uppercaseChar() ?: return '#'
    val folded = java.text.Normalizer.normalize(base.toString(), java.text.Normalizer.Form.NFD)
        .firstOrNull { it.category != CharCategory.NON_SPACING_MARK } ?: base
    return if (folded in 'A'..'Z') folded else '#'
}

@Composable
private fun AlphabetIndexBar(letterIndex: Map<Char, Int>, modifier: Modifier = Modifier, onLetterSelected: (Int) -> Unit) {
    val letters = remember { ('A'..'Z').toList() + '#' }
    var height by remember { mutableStateOf(0) }
    fun jumpTo(letter: Char) {
        val target = letters.dropWhile { it != letter }.firstNotNullOfOrNull { letterIndex[it] }
            ?: letters.takeWhile { it != letter }.asReversed().firstNotNullOfOrNull { letterIndex[it] }
        target?.let(onLetterSelected)
    }
    fun selectForY(y: Float) {
        if (height <= 0) return
        val rowHeight = height / letters.size.toFloat()
        jumpTo(letters[(y / rowHeight).toInt().coerceIn(0, letters.size - 1)])
    }
    Column(
        modifier
            .onSizeChanged { height = it.height }
            .pointerInput(letterIndex) {
                detectDragGestures { change, _ -> selectForY(change.position.y); change.consume() }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            val available = letter in letterIndex
            Text(
                if (letter == '#') "#" else letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(onClickLabel = "Jump to ${if (letter == '#') "other" else letter.toString()}") { jumpTo(letter) }
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun LibraryCard(
    card: CardEntity,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    onPronounce: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(selected, onSelectionChange)
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.front + (card.gender?.let { " ($it.)" } ?: ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(card.back, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardPill(card.deckName, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
                    val isDue = card.state == CardState.REVIEW && card.dueAt <= System.currentTimeMillis()
                    CardPill(
                        cardStatus(card),
                        container = if (isDue) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        content = if (isDue) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!selectionMode) {
                Spacer(Modifier.width(4.dp))
                IconButton(onPronounce) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Pronounce “${card.front}”", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun CardPill(label: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = container) {
        Text(label, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = content)
    }
}

private fun cardStatus(card: CardEntity): String = when (card.state) {
    CardState.NEW -> "New"
    CardState.LEARNING, CardState.RELEARNING -> "Learning"
    CardState.REVIEW -> if (card.dueAt <= System.currentTimeMillis()) "Due" else "Scheduled"
}

@Composable
private fun CardDetailScreen(card: CardEntity, tts: TtsController, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Card", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            IconButton(onEdit) { Icon(Icons.Rounded.Edit, "Edit card") }
            IconButton(onDelete) { Icon(Icons.Rounded.DeleteOutline, "Delete card") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(card.front, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        card.gender?.let { Text("($it.)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(16.dp))
                        FilledIconButton({ tts.speak(card.front) }) { Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Read French word or phrase") }
                        Spacer(Modifier.height(24.dp)); HorizontalDivider(Modifier.width(64.dp)); Spacer(Modifier.height(24.dp))
                        Text(card.back, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            if (card.example != null || card.exampleTranslation != null) item {
                DetailSection("Example") {
                    card.example?.let { sentence ->
                        Surface(
                            onClick = { tts.speak(sentence) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, "Read French example sentence", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(sentence, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    card.exampleTranslation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            card.note?.let { value -> item { DetailSection("Note") { Text(value) } } }
            item {
                DetailSection("Collection") { Text(card.deckName) }
            }
            if (card.tags.isNotBlank()) item {
                DetailSection("Tags") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        card.tags.split(TAG_SEPARATOR).forEach { tag -> Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium) } }
                    }
                }
            }
            item {
                DetailSection("Review") {
                    Text(if (card.state == CardState.NEW) "New · ready to study" else "${card.repetitions} reviews · ${card.lapses} lapses", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (card.state != CardState.NEW || card.repetitions > 0) {
                        OutlinedButton(onReset) { Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text("Reset progress") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun CreateCardScreen(initial: CardEntity? = null, deckNames: List<String>, onBack: () -> Unit, onSave: (ImportCard, String) -> Unit) {
    var front by remember(initial?.id) { mutableStateOf(initial?.front.orEmpty()) }
    var back by remember(initial?.id) { mutableStateOf(initial?.back.orEmpty()) }
    var gender by remember(initial?.id) { mutableStateOf(initial?.gender) }
    var example by remember(initial?.id) { mutableStateOf(initial?.example.orEmpty()) }
    var exampleTranslation by remember(initial?.id) { mutableStateOf(initial?.exampleTranslation.orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var tags by remember(initial?.id) { mutableStateOf(initial?.tags?.split(TAG_SEPARATOR)?.joinToString(", ").orEmpty()) }
    var deck by remember(initial?.id) { mutableStateOf(initial?.deckName ?: "My cards") }
    var showErrors by remember(initial?.id) { mutableStateOf(false) }
    var confirmDiscard by remember(initial?.id) { mutableStateOf(false) }
    val canSave = front.isNotBlank() && back.isNotBlank() && deck.isNotBlank()
    val dirty = front != initial?.front.orEmpty() ||
        back != initial?.back.orEmpty() ||
        gender != initial?.gender ||
        example != initial?.example.orEmpty() ||
        exampleTranslation != initial?.exampleTranslation.orEmpty() ||
        note != initial?.note.orEmpty() ||
        tags != initial?.tags?.split(TAG_SEPARATOR)?.joinToString(", ").orEmpty() ||
        deck != (initial?.deckName ?: "My cards")
    val save = {
        showErrors = true
        if (canSave) onSave(ImportCard(
            front = front,
            back = back,
            gender = gender,
            example = example.takeIf(String::isNotBlank),
            exampleTranslation = exampleTranslation.takeIf(String::isNotBlank),
            note = note.takeIf(String::isNotBlank),
            tags = tags.split(',').map(String::trim).filter(String::isNotEmpty)
        ), deck)
    }
    val requestBack = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = requestBack)

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(requestBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(if (initial == null) "New card" else "Edit card", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = save, colors = ButtonDefaults.textButtonColors(contentColor = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)) { Text("Save") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                OutlinedTextField(front, { front = it }, label = { Text("French") }, placeholder = { Text("à côté de") }, singleLine = false, isError = showErrors && front.isBlank(), supportingText = if (showErrors && front.isBlank()) ({ Text("French text is required") }) else null, modifier = Modifier.fillMaxWidth().testTag("card_front"))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(back, { back = it }, label = { Text("English") }, placeholder = { Text("next to / beside") }, singleLine = false, isError = showErrors && back.isBlank(), supportingText = if (showErrors && back.isBlank()) ({ Text("English translation is required") }) else null, modifier = Modifier.fillMaxWidth().testTag("card_back"))
            }
            item {
                Text("Gender", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(gender == "m", { gender = if (gender == "m") null else "m" }, label = { Text("masculine · m.") })
                    FilterChip(gender == "f", { gender = if (gender == "f") null else "f" }, label = { Text("feminine · f.") })
                }
            }
            item {
                Text("Examples", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(example, { example = it }, label = { Text("French example") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(exampleTranslation, { exampleTranslation = it }, label = { Text("Example translation") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("Deck and tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                DeckChooser(deck, deckNames, { deck = it })
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, supportingText = { Text("Separate tags with commas") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Button(onClick = save, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("save_card")) { Text(if (initial == null) "Add card" else "Save") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard changes?") },
        text = { Text(if (initial == null) "This new card hasn’t been saved." else "Your edits to this card haven’t been saved.") },
        confirmButton = { TextButton({ confirmDiscard = false; onBack() }) { Text("Discard") } },
        dismissButton = { TextButton({ confirmDiscard = false }) { Text("Keep editing") } }
    )
}
