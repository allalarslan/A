package com.example.tetris

import kotlin.random.Random

/** The seven tetrominoes, each defined by a square matrix in its spawn orientation. */
enum class Tetromino(val shape: Array<IntArray>) {
    I(arrayOf(intArrayOf(0, 0, 0, 0), intArrayOf(1, 1, 1, 1), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))),
    O(arrayOf(intArrayOf(1, 1), intArrayOf(1, 1))),
    T(arrayOf(intArrayOf(0, 1, 0), intArrayOf(1, 1, 1), intArrayOf(0, 0, 0))),
    S(arrayOf(intArrayOf(0, 1, 1), intArrayOf(1, 1, 0), intArrayOf(0, 0, 0))),
    Z(arrayOf(intArrayOf(1, 1, 0), intArrayOf(0, 1, 1), intArrayOf(0, 0, 0))),
    J(arrayOf(intArrayOf(1, 0, 0), intArrayOf(1, 1, 1), intArrayOf(0, 0, 0))),
    L(arrayOf(intArrayOf(0, 0, 1), intArrayOf(1, 1, 1), intArrayOf(0, 0, 0)));

    /** Value stored in the board for cells occupied by this piece (0 means empty). */
    val cellValue: Int get() = ordinal + 1
}

/** A falling piece: a tetromino in a given orientation at a position on the board. */
data class Piece(val type: Tetromino, val matrix: Array<IntArray>, val x: Int, val y: Int) {

    val size: Int get() = matrix.size

    /** Absolute board coordinates (x, y) of every block of this piece. */
    fun cells(): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>(4)
        for (r in matrix.indices) {
            for (c in matrix[r].indices) {
                if (matrix[r][c] != 0) result.add(Pair(x + c, y + r))
            }
        }
        return result
    }

    fun moved(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)

    fun rotatedClockwise(): Piece {
        val n = size
        val rotated = Array(n) { r -> IntArray(n) { c -> matrix[n - 1 - c][r] } }
        return copy(matrix = rotated)
    }

    companion object {
        fun spawn(type: Tetromino, boardWidth: Int): Piece {
            val matrix = Array(type.shape.size) { type.shape[it].copyOf() }
            return Piece(type, matrix, (boardWidth - matrix.size) / 2, 0)
        }
    }
}

/**
 * Pure game logic for Tetris, independent of Android so it can be unit tested.
 *
 * Coordinates: x grows to the right, y grows downward, (0, 0) is the top-left cell.
 */
class TetrisGame(
    val width: Int = WIDTH,
    val height: Int = HEIGHT,
    private val random: Random = Random.Default,
) {
    /** board[y][x] is 0 when empty, otherwise [Tetromino.cellValue] of the locked block. */
    val board: Array<IntArray> = Array(height) { IntArray(width) }

    lateinit var current: Piece
        private set
    lateinit var next: Tetromino
        private set
    var held: Tetromino? = null
        private set
    var canHold = true
        private set

    var score = 0
        private set
    var lines = 0
        private set
    var level = 1
        private set
    var isGameOver = false
        private set

    private val bag = ArrayDeque<Tetromino>()

    init {
        reset()
    }

    fun reset() {
        board.forEach { it.fill(0) }
        bag.clear()
        held = null
        canHold = true
        score = 0
        lines = 0
        level = 1
        isGameOver = false
        next = drawFromBag()
        spawnNext()
    }

    /** Time between two gravity steps, in milliseconds, for the current level. */
    val gravityDelayMs: Long
        get() = maxOf(80L, 800L - (level - 1) * 70L)

    fun moveLeft(): Boolean = tryMove(-1, 0)

    fun moveRight(): Boolean = tryMove(1, 0)

    /** Moves the piece one row down. Awards 1 point per row when [manual]. */
    fun softDrop(manual: Boolean = true): Boolean {
        val moved = tryMove(0, 1)
        if (moved && manual) score += 1
        return moved
    }

    /** Drops the piece to the bottom and locks it immediately. */
    fun hardDrop() {
        if (isGameOver) return
        var distance = 0
        while (tryMove(0, 1)) distance++
        score += distance * 2
        lockPiece()
    }

    /** Rotates clockwise, trying a few horizontal/vertical offsets ("wall kicks"). */
    fun rotate(): Boolean {
        if (isGameOver || current.type == Tetromino.O) return false
        val rotated = current.rotatedClockwise()
        for ((dx, dy) in KICKS) {
            val candidate = rotated.moved(dx, dy)
            if (fits(candidate)) {
                current = candidate
                return true
            }
        }
        return false
    }

    /** Swaps the current piece with the held one (once per piece). */
    fun hold(): Boolean {
        if (isGameOver || !canHold) return false
        val previous = held
        held = current.type
        if (previous == null) {
            spawnNext()
        } else {
            spawn(previous)
        }
        canHold = false
        return true
    }

    /** One gravity step: moves the piece down, or locks it if it cannot move. */
    fun tick() {
        if (isGameOver) return
        if (!softDrop(manual = false)) lockPiece()
    }

    /** Row where the current piece would land if hard-dropped. */
    fun ghostY(): Int {
        var ghost = current
        while (fits(ghost.moved(0, 1))) ghost = ghost.moved(0, 1)
        return ghost.y
    }

    fun fits(piece: Piece): Boolean = piece.cells().all { (x, y) ->
        x in 0 until width && y in 0 until height && board[y][x] == 0
    }

    private fun tryMove(dx: Int, dy: Int): Boolean {
        if (isGameOver) return false
        val candidate = current.moved(dx, dy)
        if (!fits(candidate)) return false
        current = candidate
        return true
    }

    private fun lockPiece() {
        for ((x, y) in current.cells()) {
            board[y][x] = current.type.cellValue
        }
        val cleared = clearLines()
        if (cleared > 0) {
            score += LINE_SCORES[cleared] * level
            lines += cleared
            level = lines / 10 + 1
        }
        canHold = true
        spawnNext()
    }

    /** Removes full rows and returns how many were removed. */
    private fun clearLines(): Int {
        val remaining = board.filter { row -> row.any { it == 0 } }
        val cleared = height - remaining.size
        if (cleared == 0) return 0
        for (y in 0 until height) {
            if (y < cleared) {
                board[y] = IntArray(width)
            } else {
                board[y] = remaining[y - cleared]
            }
        }
        return cleared
    }

    private fun spawnNext() {
        val type = next
        next = drawFromBag()
        spawn(type)
    }

    private fun spawn(type: Tetromino) {
        current = Piece.spawn(type, width)
        if (!fits(current)) isGameOver = true
    }

    /** "7-bag" randomizer: every piece appears once in each group of seven. */
    private fun drawFromBag(): Tetromino {
        if (bag.isEmpty()) bag.addAll(Tetromino.entries.shuffled(random))
        return bag.removeFirst()
    }

    companion object {
        const val WIDTH = 10
        const val HEIGHT = 20

        private val LINE_SCORES = intArrayOf(0, 100, 300, 500, 800)

        private val KICKS = listOf(
            0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1,
        )
    }
}
