package io.github.sevgjan.sudoku

import kotlin.random.Random

/** A 9x9 grid stored row-major, 0 = empty. */
class Puzzle(val givens: IntArray, val solution: IntArray)

object Sudoku {
    const val SIZE = 81

    fun row(index: Int) = index / 9
    fun col(index: Int) = index % 9
    fun box(index: Int) = row(index) / 3 * 3 + col(index) / 3

    /** Cells sharing a row, column or box with [index], excluding itself. */
    val peers: Array<IntArray> = Array(SIZE) { i ->
        (0 until SIZE).filter { j -> j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i)) }.toIntArray()
    }

    /** Clue count by ISO day of week (1 = Monday): easy at the start of the week, hardest on Sunday. */
    fun cluesFor(dayOfWeek: Int): Int = intArrayOf(40, 38, 36, 34, 32, 30, 28)[dayOfWeek - 1]

    /** Same day, same puzzle, on every phone. */
    fun daily(epochDay: Long, dayOfWeek: Int): Puzzle = generate(Random(epochDay * 7919 + 17), cluesFor(dayOfWeek))

    fun generate(random: Random, clues: Int): Puzzle {
        val solution = IntArray(SIZE)
        fill(solution, random)
        val givens = solution.copyOf()
        var remaining = SIZE
        for (cell in (0 until SIZE).shuffled(random)) {
            if (remaining <= clues) break
            val kept = givens[cell]
            givens[cell] = 0
            if (countSolutions(givens.copyOf(), limit = 2) == 1) remaining-- else givens[cell] = kept
        }
        return Puzzle(givens, solution)
    }

    private fun fill(grid: IntArray, random: Random): Boolean {
        val cell = grid.indexOfFirst { it == 0 }
        if (cell < 0) return true
        for (digit in (1..9).shuffled(random)) {
            if (canPlace(grid, cell, digit)) {
                grid[cell] = digit
                if (fill(grid, random)) return true
            }
        }
        grid[cell] = 0
        return false
    }

    fun canPlace(grid: IntArray, cell: Int, digit: Int): Boolean = peers[cell].none { grid[it] == digit }

    /** Counts solutions up to [limit], filling the most constrained cell first. */
    fun countSolutions(grid: IntArray, limit: Int): Int {
        var bestCell = -1
        var bestOptions = 10
        for (cell in 0 until SIZE) {
            if (grid[cell] != 0) continue
            val options = (1..9).count { canPlace(grid, cell, it) }
            if (options == 0) return 0
            if (options < bestOptions) {
                bestOptions = options
                bestCell = cell
            }
        }
        if (bestCell < 0) return 1
        var count = 0
        for (digit in 1..9) {
            if (!canPlace(grid, bestCell, digit)) continue
            grid[bestCell] = digit
            count += countSolutions(grid, limit - count)
            grid[bestCell] = 0
            if (count >= limit) break
        }
        return count
    }

    fun isValidSolution(grid: IntArray): Boolean =
        grid.all { it in 1..9 } && (0 until SIZE).all { cell -> canPlace(grid, cell, grid[cell]) }
}
