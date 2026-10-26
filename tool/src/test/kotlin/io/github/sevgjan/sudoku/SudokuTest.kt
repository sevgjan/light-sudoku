package io.github.sevgjan.sudoku

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SudokuTest {
    @Test
    fun generatedPuzzleHasUniqueValidSolution() {
        val puzzle = Sudoku.generate(Random(1), clues = 30)
        assertTrue(Sudoku.isValidSolution(puzzle.solution))
        assertEquals(1, Sudoku.countSolutions(puzzle.givens.copyOf(), limit = 2))
        assertTrue(puzzle.givens.count { it != 0 } >= 30)
        puzzle.givens.forEachIndexed { i, v -> if (v != 0) assertEquals(puzzle.solution[i], v) }
    }

    @Test
    fun dailyPuzzleIsDeterministic() {
        val a = Sudoku.daily(epochDay = 20_752, dayOfWeek = 1)
        val b = Sudoku.daily(epochDay = 20_752, dayOfWeek = 1)
        val c = Sudoku.daily(epochDay = 20_753, dayOfWeek = 2)
        assertContentEquals(a.givens, b.givens)
        assertFalse(a.givens.contentEquals(c.givens))
    }

    @Test
    fun sundayIsHarderThanMonday() {
        assertTrue(Sudoku.cluesFor(7) < Sudoku.cluesFor(1))
    }

    @Test
    fun solvingCompletesTheGame() {
        val puzzle = Sudoku.generate(Random(2), clues = 40)
        var game = Game.new(day = 1, puzzle = puzzle)
        val empty = (0 until Sudoku.SIZE).filter { !game.isGiven(it) }
        empty.dropLast(1).forEach { game = game.enter(it, puzzle.solution[it], asNote = false) }
        assertFalse(game.solved)
        game = game.enter(empty.last(), puzzle.solution[empty.last()], asNote = false)
        assertTrue(game.solved)
    }

    @Test
    fun givensCannotBeChanged() {
        val puzzle = Sudoku.generate(Random(3), clues = 40)
        val game = Game.new(day = 1, puzzle = puzzle)
        val given = (0 until Sudoku.SIZE).first { game.isGiven(it) }
        assertEquals(game, game.enter(given, 1, asNote = false))
        assertEquals(game, game.erase(given))
    }

    @Test
    fun notesToggleAndClearFromPeers() {
        val puzzle = Sudoku.generate(Random(4), clues = 30)
        var game = Game.new(day = 1, puzzle = puzzle)
        val (a, b) = (0 until Sudoku.SIZE).filter { !game.isGiven(it) }.let { cells ->
            val first = cells.first()
            first to cells.first { it != first && it in Sudoku.peers[first] }
        }
        game = game.enter(b, 5, asNote = true)
        assertTrue(game.hasNote(b, 5))
        game = game.enter(a, 5, asNote = false)
        assertFalse(game.hasNote(b, 5))
        game = game.enter(a, 5, asNote = false)
        assertEquals(0, game.entries[a])
    }

    @Test
    fun streak() {
        var stats = Stats()
        stats = stats.solved(day = 10, seconds = 300)
        stats = stats.solved(day = 11, seconds = 200)
        assertEquals(2, stats.current(today = 11))
        assertEquals(2, stats.current(today = 12))
        assertEquals(0, stats.current(today = 13))
        assertEquals(200, stats.bestSeconds)
        assertEquals(1, stats.solved(day = 14, seconds = 100).streak)
    }

    @Test
    fun durationFormat() {
        assertEquals("4:05", formatDuration(245))
        assertEquals("1:00:01", formatDuration(3601))
    }

    @Test
    fun restartedGameIsBlankWithFullUndos() {
        val puzzle = Sudoku.generate(Random(3), clues = 40)
        val cell = (0 until Sudoku.SIZE).first { puzzle.givens[it] == 0 }
        val played = Game.new(day = 1, puzzle = puzzle)
            .enter(cell, puzzle.solution[cell], asNote = false)
            .copy(elapsedSeconds = 90, undosLeft = 0)
        val replay = played.restarted(Mode.REPLAY)
        assertEquals(0, replay.entries[cell])
        assertEquals(0, replay.elapsedSeconds)
        assertEquals(Game.MAX_UNDOS, replay.undosLeft)
        assertEquals(Mode.REPLAY, replay.mode)
        assertContentEquals(puzzle.givens.toList(), replay.givens)
    }
}
