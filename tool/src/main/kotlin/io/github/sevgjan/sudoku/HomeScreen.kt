package io.github.sevgjan.sudoku

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class PuzzleViewModel(private val store: PuzzleStore) : LightViewModel<Unit>() {
    /** The game on screen: today's daily, or a replay/practice round on top of it. */
    val game = MutableStateFlow<Game?>(null)
    val stats = MutableStateFlow(Stats())
    val selected = MutableStateFlow<Int?>(null)
    val notesMode = MutableStateFlow(false)
    /** Earlier states of the game on screen, for undo; cleared whenever a different game is shown. */
    val history = MutableStateFlow<List<Game>>(emptyList())
    private var daily: Game? = null
    private var ticker: Job? = null

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        viewModelScope.launch {
            val today = LocalDate.now()
            if (daily?.day != today.toEpochDay()) {
                daily = store.loadGame(today.toEpochDay()) ?: withContext(Dispatchers.Default) {
                    Game.new(today.toEpochDay(), Sudoku.daily(today.toEpochDay(), today.dayOfWeek.value))
                }.also { store.saveGame(it) }
                show(store.loadExtra(today.toEpochDay()) ?: daily)
            }
            stats.value = store.loadStats()
            startTicker()
        }
    }

    override fun onScreenHide(screen: SimpleLightScreen<Unit>) = pause()

    override fun onAppPause() = pause()

    private fun pause() {
        ticker?.cancel()
        game.value?.let { viewModelScope.launch { store.save(it) } }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1.seconds)
                val current = game.value ?: continue
                if (!current.solved) set(current.copy(elapsedSeconds = current.elapsedSeconds + 1))
            }
        }
    }

    private fun set(next: Game) {
        game.value = next
        if (next.mode == Mode.DAILY) daily = next
    }

    private fun show(next: Game?) {
        selected.value = null
        history.value = emptyList()
        game.value = next
    }

    fun select(cell: Int) {
        selected.value = cell
    }

    fun toggleNotes() {
        notesMode.value = !notesMode.value
    }

    fun input(digit: Int) = update { game, cell -> game.enter(cell, digit, notesMode.value) }

    fun erase() = update { game, cell -> game.erase(cell) }

    /** Steps back one move. Each game allows [Game.MAX_UNDOS]. */
    fun undo() {
        val current = game.value ?: return
        val previous = history.value.lastOrNull() ?: return
        if (current.undosLeft <= 0 || current.solved) return
        history.value = history.value.dropLast(1)
        commit(previous.copy(elapsedSeconds = current.elapsedSeconds, undosLeft = current.undosLeft - 1))
    }

    /** Not reachable from the UI for now; kept for when REPLAY comes back. Plays the puzzle on screen again from a blank board, without touching the daily result. */
    fun replay() {
        val current = game.value ?: return
        startExtra(current.restarted(Mode.REPLAY))
    }

    /** A fresh random puzzle at today's difficulty. */
    fun practice() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val puzzle = withContext(Dispatchers.Default) {
                Sudoku.generate(Random.Default, Sudoku.cluesFor(today.dayOfWeek.value))
            }
            startExtra(Game.new(today.toEpochDay(), puzzle, Mode.PRACTICE))
        }
    }

    private val volumeDownPresses = ArrayDeque<Long>()

    /** Hidden bypass: three volume-down presses within [TRIPLE_PRESS_MS] on a solved screen start a practice round. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isPracticeKey(keyCode)) return super.onKeyDown(keyCode, event)
        if (event.repeatCount > 0) return true
        volumeDownPresses.removeAll { event.eventTime - it > TRIPLE_PRESS_MS }
        volumeDownPresses.addLast(event.eventTime)
        if (volumeDownPresses.size >= 3) {
            volumeDownPresses.clear()
            practice()
        }
        return true
    }

    // Consumed too, so LightOS doesn't open its volume overlay over the solved screen.
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        isPracticeKey(keyCode) || super.onKeyUp(keyCode, event)

    private fun isPracticeKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && game.value?.solved == true

    private fun startExtra(extra: Game) {
        show(extra)
        viewModelScope.launch { store.saveExtra(extra) }
        startTicker()
    }

    /** Leaves a replay/practice round for the daily, or returns false when already on the daily. */
    fun close(): Boolean {
        if (game.value?.mode == Mode.DAILY) return false
        show(daily)
        viewModelScope.launch { store.clearExtra() }
        return true
    }

    private fun update(change: (Game, Int) -> Game) {
        val current = game.value ?: return
        val cell = selected.value ?: return
        val next = change(current, cell)
        if (next == current) return
        history.value = history.value + current
        commit(next)
    }

    private fun commit(next: Game) {
        set(next)
        viewModelScope.launch {
            store.save(next)
            if (next.solved) {
                ticker?.cancel()
                if (next.mode == Mode.DAILY) {
                    stats.value = stats.value.solved(next.day, next.elapsedSeconds).also { store.saveStats(it) }
                }
            }
        }
    }
}

private const val TRIPLE_PRESS_MS = 1500L

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, PuzzleViewModel>(sealedActivity) {
    override val viewModelClass: Class<PuzzleViewModel>
        get() = PuzzleViewModel::class.java

    override fun createViewModel() = PuzzleViewModel(PuzzleStore(lightContext.dataStore))

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val game by viewModel.game.collectAsState()
        val stats by viewModel.stats.collectAsState()
        val selected by viewModel.selected.collectAsState()
        val notesMode by viewModel.notesMode.collectAsState()
        val history by viewModel.history.collectAsState()
        val weekday = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val current = game
        val title = when {
            current?.mode == Mode.REPLAY -> "Sudoku · Replay"
            current?.mode == Mode.PRACTICE -> "Sudoku · Practice"
            else -> "Sudoku · $weekday"
        }
        val close = { if (!viewModel.close()) goBack() }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when {
                    current == null -> {
                        LightTopBar(center = LightTopBarCenter.Text(title))
                        Box(Modifier.weight(1f).fillMaxWidth())
                    }
                    current.solved -> {
                        LightTopBar(
                            leftButton = if (current.mode == Mode.DAILY) null else LightBarButton.LightIcon(LightIcons.CLOSE, onClick = close),
                            center = LightTopBarCenter.Text(title),
                        )
                        SolvedView(current, stats, Modifier.weight(1f))
                    }
                    else -> {
                        LightTopBar(
                            leftButton = LightBarButton.LightIcon(LightIcons.CLOSE, onClick = close),
                            center = if (notesMode) {
                                LightTopBarCenter.TwoLineDetail("Notes mode on", "Numbers are added as notes only")
                            } else {
                                LightTopBarCenter.Text(title)
                            },
                        )
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 0.5f.gridUnitsAsDp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Board(
                                game = current,
                                selected = selected,
                                onSelect = viewModel::select,
                                modifier = Modifier.size(minOf(maxWidth, maxHeight)),
                            )
                        }
                        DigitPad(notesMode = notesMode, onDigit = viewModel::input)
                        LightBottomBar(
                            listOf(
                                // Hidden once all undos are spent.
                                if (current.undosLeft > 0) {
                                    LightBarButton.Text(
                                        text = "UNDO ${current.undosLeft}",
                                        onClick = viewModel::undo.takeIf { history.isNotEmpty() },
                                    )
                                } else {
                                    null
                                },
                                LightBarButton.Text(text = "NOTES", onClick = viewModel::toggleNotes),
                                LightBarButton.Text(text = "ERASE", onClick = viewModel::erase),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Board(game: Game, selected: Int?, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LightThemeTokens.colors
    BoxWithConstraints(modifier = modifier.aspectRatio(1f)) {
        val cellSize: Dp = maxWidth / 9
        Column(
            modifier = Modifier.drawBehind {
                val cell = size.width / 9
                for (i in 0..9) {
                    val thick = i % 3 == 0
                    val stroke = if (thick) 3f else 1f
                    val color = if (thick) colors.content else colors.contentSecondary
                    drawLine(color, Offset(i * cell, 0f), Offset(i * cell, size.height), stroke)
                    drawLine(color, Offset(0f, i * cell), Offset(size.width, i * cell), stroke)
                }
            },
        ) {
            for (row in 0 until 9) {
                Row {
                    for (col in 0 until 9) {
                        val cell = row * 9 + col
                        Cell(game, cell, cell == selected, cellSize, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(game: Game, cell: Int, isSelected: Boolean, size: Dp, onSelect: (Int) -> Unit) {
    val colors = LightThemeTokens.colors
    val textColor = if (isSelected) colors.background else null
    Box(
        modifier = Modifier
            .size(size)
            .let { if (isSelected) it.background(colors.content) else it }
            .lightClickable { onSelect(cell) },
        contentAlignment = Alignment.Center,
    ) {
        val value = game.valueAt(cell)
        if (value != 0) {
            LightText(
                text = value.toString(),
                variant = LightTextVariant.Subheading,
                lighten = !game.isGiven(cell) && !isSelected,
                color = textColor,
            )
        } else if (game.notes[cell] != 0) {
            // Light's Micro (8sp) is hard to read and Superfine (16sp) overflows a full cell, so notes sit in between.
            BasicText(
                text = (1..9).filter { game.hasNote(cell, it) }.joinToString(""),
                style = LightThemeTokens.typography.micro.copy(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    textAlign = TextAlign.Center,
                    color = textColor ?: colors.contentSecondary,
                ),
            )
        }
    }
}

@Composable
private fun DigitPad(notesMode: Boolean, onDigit: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 0.5f.gridUnitsAsDp())) {
        for (digit in 1..9) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.4f.gridUnitsAsDp())
                    .lightClickable { onDigit(digit) },
                contentAlignment = Alignment.Center,
            ) {
                // Small, dimmed digits while notes mode is on, matching how notes look on the board.
                LightText(
                    text = digit.toString(),
                    variant = if (notesMode) LightTextVariant.Copy else LightTextVariant.Heading,
                    lighten = notesMode,
                )
            }
        }
    }
}

@Composable
private fun SolvedView(game: Game, stats: Stats, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30.seconds)
            now = LocalTime.now()
        }
    }
    val minutesLeft = 24 * 60 - (now.hour * 60 + now.minute)
    val streak = stats.current(game.day)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LightText(text = "Solved", variant = LightTextVariant.Title)
            LightText(
                text = "in ${formatDuration(game.elapsedSeconds)}",
                variant = LightTextVariant.Copy,
                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            )
            if (game.mode == Mode.DAILY) {
                LightText(
                    text = if (streak == 1) "1 day streak" else "$streak day streak",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp()),
                )
                stats.bestSeconds?.let {
                    LightText(text = "best ${formatDuration(it)}", variant = LightTextVariant.Copy, lighten = true)
                }
            } else {
                LightText(
                    text = "not counted toward streak",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp()),
                )
            }
            LightText(
                text = "next puzzle in ${minutesLeft / 60}h ${minutesLeft % 60}m",
                variant = LightTextVariant.Fine,
                lighten = true,
                modifier = Modifier.padding(top = 2f.gridUnitsAsDp()),
            )
        }
    }
}
