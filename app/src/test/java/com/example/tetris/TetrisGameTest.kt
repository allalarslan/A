package com.example.tetris

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TetrisGameTest {

    private fun newGame() = TetrisGame(random = Random(42))

    @Test
    fun pieceSpawnsAtTopCenter() {
        val game = newGame()
        assertEquals(0, game.current.y)
        assertTrue(game.fits(game.current))
        assertFalse(game.isGameOver)
    }

    @Test
    fun sevenBagContainsEveryPieceOnce() {
        val game = newGame()
        val seen = mutableListOf(game.current.type)
        repeat(6) {
            game.hardDrop()
            seen += game.current.type
        }
        assertEquals(Tetromino.entries.toSet(), seen.toSet())
    }

    @Test
    fun cannotMovePastWalls() {
        val game = newGame()
        repeat(20) { game.moveLeft() }
        assertEquals(0, game.current.cells().minOf { it.first })
        repeat(20) { game.moveRight() }
        assertEquals(game.width - 1, game.current.cells().maxOf { it.first })
    }

    @Test
    fun hardDropLocksPieceOnFloorAndScores() {
        val game = newGame()
        val landingCells = game.current.cells().map { (x, y) -> x to y + (game.ghostY() - game.current.y) }
        val distance = game.ghostY() - game.current.y
        game.hardDrop()
        for ((x, y) in landingCells) assertNotEquals(0, game.board[y][x])
        assertEquals(distance * 2, game.score)
    }

    @Test
    fun rotationKeepsFourCellsAndFourRotationsReturnToStart() {
        val game = newGame()
        repeat(3) { game.moveLeft(); game.softDrop() }
        val start = game.current.cells().toSet()
        repeat(4) {
            assertTrue(game.rotate() || game.current.type == Tetromino.O)
            assertEquals(4, game.current.cells().size)
        }
        assertEquals(start, game.current.cells().toSet())
    }

    @Test
    fun fullLineIsClearedAndScored() {
        val game = newGame()
        val bottom = game.height - 1
        // Fill the bottom row except the column(s) the current piece will fill.
        val landing = game.current.cells().map { (x, y) -> x to y + (game.ghostY() - game.current.y) }
        val filledOnBottom = landing.filter { it.second == bottom }.map { it.first }.toSet()
        for (x in 0 until game.width) {
            if (x !in filledOnBottom) game.board[bottom][x] = 1
        }
        game.hardDrop()
        assertEquals(1, game.lines)
        assertTrue(game.score >= 100)
    }

    @Test
    fun clearingTenLinesRaisesLevel() {
        val game = newGame()
        repeat(10) {
            // A pre-filled bottom row is cleared when the next piece locks.
            val bottom = game.height - 1
            for (x in 0 until game.width) game.board[bottom][x] = 1
            game.hardDrop()
        }
        assertEquals(10, game.lines)
        assertEquals(2, game.level)
        assertTrue(game.gravityDelayMs < 800L)
    }

    @Test
    fun holdSwapsOnlyOncePerPiece() {
        val game = newGame()
        val first = game.current.type
        assertTrue(game.hold())
        assertEquals(first, game.held)
        assertFalse(game.hold())
        game.hardDrop()
        val beforeSwap = game.current.type
        assertTrue(game.hold())
        assertEquals(first, game.current.type)
        assertEquals(beforeSwap, game.held)
    }

    @Test
    fun stackingToTheTopEndsTheGame() {
        val game = newGame()
        repeat(200) { if (!game.isGameOver) game.hardDrop() }
        assertTrue(game.isGameOver)
        game.reset()
        assertFalse(game.isGameOver)
        assertEquals(0, game.score)
    }
}
