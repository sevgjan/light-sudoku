package io.github.sevgjan.sudoku

import kotlinx.serialization.Serializable

/** DAILY counts toward stats; REPLAY and PRACTICE are extra rounds that don't. */
enum class Mode { DAILY, REPLAY, PRACTICE }

/** One game's progress. [notes] holds a 9-bit candidate mask per cell. */
@Serializable
data class Game(
    val day: Long,
    val givens: List<Int>,
    val solution: List<Int>,
    val entries: List<Int> = List(Sudoku.SIZE) { 0 },
    val notes: List<Int> = List(Sudoku.SIZE) { 0 },
    val elapsedSeconds: Long = 0,
    val solved: Boolean = false,
    val mode: Mode = Mode.DAILY,
    val undosLeft: Int = MAX_UNDOS,
) {
    fun valueAt(cell: Int): Int = givens[cell].takeIf { it != 0 } ?: entries[cell]

    fun isGiven(cell: Int): Boolean = givens[cell] != 0

    fun enter(cell: Int, digit: Int, asNote: Boolean): Game {
        if (isGiven(cell) || solved) return this
        if (asNote) {
            if (entries[cell] != 0) return this
            val toggled = notes[cell] xor (1 shl (digit - 1))
            return copy(notes = notes.replaced(cell, toggled))
        }
        val value = if (entries[cell] == digit) 0 else digit
        // Placing a digit clears it from the notes of every peer.
        val cleared = if (value == 0) notes else notes.mapIndexed { i, mask ->
            if (i == cell) 0 else if (i in Sudoku.peers[cell]) mask and (1 shl (digit - 1)).inv() else mask
        }
        val next = copy(entries = entries.replaced(cell, value), notes = cleared)
        return next.copy(solved = next.isComplete())
    }

    fun erase(cell: Int): Game =
        if (isGiven(cell) || solved) this else copy(entries = entries.replaced(cell, 0), notes = notes.replaced(cell, 0))

    fun isComplete(): Boolean = (0 until Sudoku.SIZE).all { valueAt(it) == solution[it] }

    fun hasNote(cell: Int, digit: Int): Boolean = notes[cell] and (1 shl (digit - 1)) != 0

    /** Same puzzle from a blank board. */
    fun restarted(mode: Mode): Game = Game(day, givens, solution, mode = mode)

    companion object {
        const val MAX_UNDOS = 3

        fun new(day: Long, puzzle: Puzzle, mode: Mode = Mode.DAILY) =
            Game(day, puzzle.givens.toList(), puzzle.solution.toList(), mode = mode)
    }
}

/** Streak counts consecutive days solved; solving yesterday's streak + today keeps it going. */
@Serializable
data class Stats(val streak: Int = 0, val lastSolvedDay: Long = Long.MIN_VALUE, val bestSeconds: Long? = null) {
    fun current(today: Long): Int = if (lastSolvedDay == today || lastSolvedDay == today - 1) streak else 0

    fun solved(day: Long, seconds: Long): Stats {
        if (lastSolvedDay == day) return this
        val nextStreak = if (lastSolvedDay == day - 1) streak + 1 else 1
        return Stats(nextStreak, day, bestSeconds?.coerceAtMost(seconds) ?: seconds)
    }
}

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun List<Int>.replaced(index: Int, value: Int): List<Int> = toMutableList().also { it[index] = value }
