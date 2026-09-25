package com.example.tetris

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets

/**
 * Draws the game and handles touch input.
 *
 * Layout (portrait): the board on the left, an info panel (score, next, hold) on
 * the right, and a row of control buttons along the bottom.
 */
class TetrisView(context: Context) : View(context) {

    private val game = TetrisGame()
    private val prefs = context.getSharedPreferences("tetris", Context.MODE_PRIVATE)
    private var highScore = prefs.getInt(KEY_HIGH_SCORE, 0)

    private var paused = false
    private var running = false
    private var lastFrameTime = 0L
    private var gravityAccumulator = 0L

    private val density = resources.displayMetrics.density

    // Layout, computed in onSizeChanged / onApplyWindowInsets.
    private var insetTop = 0
    private var insetBottom = 0
    private var cell = 0f
    private val boardRect = RectF()
    private val panelRect = RectF()
    private val nextRect = RectF()
    private val holdRect = RectF()
    private val pauseRect = RectF()
    private val buttons = mutableListOf<Button>()

    // Touch state: which button each pointer is pressing, for auto-repeat.
    private val pressed = mutableMapOf<Int, Button>()

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 255, 255, 255) }
    private val gridPaint = Paint().apply { color = Color.rgb(28, 30, 44); strokeWidth = 1f }
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 96, 130)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val boardBgPaint = Paint().apply { color = Color.rgb(16, 17, 26) }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(44, 48, 70) }
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(86, 94, 140) }
    private val overlayPaint = Paint().apply { color = Color.argb(190, 0, 0, 0) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(150, 156, 190)
        textSize = 13f * density
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 20f * density
        isFakeBoldText = true
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val frameCallback = object : Runnable {
        override fun run() {
            if (!running) return
            update()
            invalidate()
            postOnAnimation(this)
        }
    }

    init {
        setBackgroundColor(Color.rgb(10, 11, 18))
        isFocusable = true
    }

    // ---------------------------------------------------------------- lifecycle

    fun resume() {
        if (running) return
        running = true
        lastFrameTime = SystemClock.uptimeMillis()
        postOnAnimation(frameCallback)
    }

    fun pause() {
        running = false
        removeCallbacks(frameCallback)
        pressed.clear()
        if (!game.isGameOver) paused = true
        saveHighScore()
        invalidate()
    }

    private fun update() {
        val now = SystemClock.uptimeMillis()
        val delta = (now - lastFrameTime).coerceAtMost(250L)
        lastFrameTime = now
        if (paused || game.isGameOver) return

        for (button in pressed.values) {
            if (button.repeats && now >= button.nextRepeat) {
                button.action()
                button.nextRepeat = now + REPEAT_INTERVAL_MS
            }
        }

        gravityAccumulator += delta
        while (gravityAccumulator >= game.gravityDelayMs) {
            gravityAccumulator -= game.gravityDelayMs
            game.tick()
        }
        if (game.isGameOver) onGameOver()
    }

    private fun onGameOver() {
        pressed.clear()
        saveHighScore()
    }

    private fun saveHighScore() {
        if (game.score > highScore) {
            highScore = game.score
            prefs.edit().putInt(KEY_HIGH_SCORE, highScore).apply()
        }
    }

    private fun restart() {
        game.reset()
        gravityAccumulator = 0L
        paused = false
    }

    // ------------------------------------------------------------------ layout

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        @Suppress("DEPRECATION")
        insetTop = insets.systemWindowInsetTop
        @Suppress("DEPRECATION")
        insetBottom = insets.systemWindowInsetBottom
        computeLayout(width, height)
        return insets
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeLayout(w, h)
    }

    private fun computeLayout(w: Int, h: Int) {
        if (w == 0 || h == 0) return
        val margin = 12f * density
        val top = insetTop + margin
        val bottom = h - insetBottom - margin

        val buttonHeight = 64f * density
        val buttonsTop = bottom - buttonHeight
        val areaBottom = buttonsTop - margin

        // The board takes ~70% of the width; the panel the rest.
        val panelWidth = w * 0.28f
        val maxBoardWidth = w - panelWidth - margin * 3
        val maxBoardHeight = areaBottom - top
        cell = minOf(maxBoardWidth / game.width, maxBoardHeight / game.height)
        val boardWidth = cell * game.width
        val boardHeight = cell * game.height
        val contentWidth = boardWidth + margin + panelWidth
        val left = (w - contentWidth) / 2f
        boardRect.set(left, top, left + boardWidth, top + boardHeight)
        panelRect.set(boardRect.right + margin, top, boardRect.right + margin + panelWidth, top + boardHeight)

        val box = panelWidth
        val labelSpace = 20f * density
        nextRect.set(panelRect.left, panelRect.top + labelSpace, panelRect.right, panelRect.top + labelSpace + box * 0.8f)
        holdRect.set(panelRect.left, nextRect.bottom + labelSpace * 1.6f, panelRect.right, nextRect.bottom + labelSpace * 1.6f + box * 0.8f)
        pauseRect.set(panelRect.left, panelRect.bottom - 48f * density, panelRect.right, panelRect.bottom)

        buildButtons(margin, buttonsTop, w - margin, bottom)
    }

    private fun buildButtons(left: Float, top: Float, right: Float, bottom: Float) {
        buttons.clear()
        buttons += Button("HOLD", repeats = false) { game.hold() }
        buttons += Button("◀", repeats = true) { game.moveLeft() }
        buttons += Button("▼", repeats = true) { game.softDrop() }
        buttons += Button("▶", repeats = true) { game.moveRight() }
        buttons += Button("⟳", repeats = false) { game.rotate() }
        buttons += Button("⤓", repeats = false) { game.hardDrop() }

        val gap = 8f * density
        val buttonWidth = (right - left - gap * (buttons.size - 1)) / buttons.size
        buttons.forEachIndexed { i, button ->
            val x = left + i * (buttonWidth + gap)
            button.rect.set(x, top, x + buttonWidth, bottom)
        }
    }

    // ------------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val id = event.getPointerId(index)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                onPress(id, event.getX(index), event.getY(index))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> pressed.remove(id)
            MotionEvent.ACTION_CANCEL -> pressed.clear()
        }
        invalidate()
        return true
    }

    private fun onPress(pointerId: Int, x: Float, y: Float) {
        if (game.isGameOver) {
            restart()
            return
        }
        if (pauseRect.contains(x, y)) {
            paused = !paused
            return
        }
        if (paused) {
            paused = false
            return
        }
        if (holdRect.contains(x, y)) {
            game.hold()
            return
        }
        if (boardRect.contains(x, y)) {
            game.rotate()
            return
        }
        val button = buttons.firstOrNull { it.rect.contains(x, y) } ?: return
        button.action()
        button.nextRepeat = SystemClock.uptimeMillis() + REPEAT_DELAY_MS
        pressed[pointerId] = button
        if (game.isGameOver) onGameOver()
    }

    // ----------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        if (cell <= 0f) return
        drawBoard(canvas)
        drawPanel(canvas)
        drawButtons(canvas)
        if (game.isGameOver) {
            drawOverlay(canvas, "GAME OVER", "Touchez pour rejouer")
        } else if (paused) {
            drawOverlay(canvas, "PAUSE", "Touchez pour reprendre")
        }
    }

    private fun drawBoard(canvas: Canvas) {
        canvas.drawRect(boardRect, boardBgPaint)
        for (x in 1 until game.width) {
            val gx = boardRect.left + x * cell
            canvas.drawLine(gx, boardRect.top, gx, boardRect.bottom, gridPaint)
        }
        for (y in 1 until game.height) {
            val gy = boardRect.top + y * cell
            canvas.drawLine(boardRect.left, gy, boardRect.right, gy, gridPaint)
        }

        for (y in 0 until game.height) {
            for (x in 0 until game.width) {
                val value = game.board[y][x]
                if (value != 0) drawBlock(canvas, boardRect.left + x * cell, boardRect.top + y * cell, cell, COLORS[value - 1])
            }
        }

        if (!game.isGameOver) {
            val piece = game.current
            val ghostOffset = game.ghostY() - piece.y
            ghostPaint.color = COLORS[piece.type.ordinal]
            ghostPaint.alpha = 110
            val inset = 3f * density
            for ((x, y) in piece.cells()) {
                val gx = boardRect.left + x * cell
                val gy = boardRect.top + (y + ghostOffset) * cell
                canvas.drawRect(gx + inset, gy + inset, gx + cell - inset, gy + cell - inset, ghostPaint)
            }
            for ((x, y) in piece.cells()) {
                drawBlock(canvas, boardRect.left + x * cell, boardRect.top + y * cell, cell, COLORS[piece.type.ordinal])
            }
        }

        canvas.drawRect(boardRect, framePaint)
    }

    private fun drawBlock(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val gap = maxOf(1f, size * 0.06f)
        blockPaint.color = color
        canvas.drawRoundRect(x + gap, y + gap, x + size - gap, y + size - gap, size * 0.15f, size * 0.15f, blockPaint)
        canvas.drawRect(x + gap * 2, y + gap * 2, x + size - gap * 2, y + size * 0.32f, highlightPaint)
    }

    private fun drawPanel(canvas: Canvas) {
        canvas.drawText("SUIVANT", nextRect.left, nextRect.top - 6f * density, labelPaint)
        canvas.drawRoundRect(nextRect, 8f * density, 8f * density, boardBgPaint)
        drawPreview(canvas, game.next, nextRect, 1f)

        canvas.drawText("RÉSERVE", holdRect.left, holdRect.top - 6f * density, labelPaint)
        canvas.drawRoundRect(holdRect, 8f * density, 8f * density, boardBgPaint)
        game.held?.let { drawPreview(canvas, it, holdRect, if (game.canHold) 1f else 0.35f) }

        var y = holdRect.bottom + 34f * density
        val stats = listOf(
            "SCORE" to game.score,
            "LIGNES" to game.lines,
            "NIVEAU" to game.level,
            "RECORD" to maxOf(highScore, game.score),
        )
        for ((label, value) in stats) {
            if (y + 24f * density > pauseRect.top) break
            canvas.drawText(label, panelRect.left, y, labelPaint)
            canvas.drawText(value.toString(), panelRect.left, y + 24f * density, valuePaint)
            y += 54f * density
        }

        canvas.drawRoundRect(pauseRect, 10f * density, 10f * density, buttonPaint)
        centerTextPaint.textSize = 16f * density
        drawCenteredText(canvas, if (paused) "▶" else "II", pauseRect)
    }

    private fun drawPreview(canvas: Canvas, type: Tetromino, area: RectF, alpha: Float) {
        val shape = type.shape
        val rows = shape.indices.filter { r -> shape[r].any { it != 0 } }
        val cols = shape[0].indices.filter { c -> shape.any { it[c] != 0 } }
        val size = minOf(area.width() / 5f, area.height() / 3.5f)
        val left = area.centerX() - cols.size * size / 2f
        val top = area.centerY() - rows.size * size / 2f
        val color = COLORS[type.ordinal]
        val faded = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        for ((ri, r) in rows.withIndex()) {
            for ((ci, c) in cols.withIndex()) {
                if (shape[r][c] != 0) drawBlock(canvas, left + ci * size, top + ri * size, size, faded)
            }
        }
    }

    private fun drawButtons(canvas: Canvas) {
        val pressedButtons = pressed.values.toSet()
        for (button in buttons) {
            val paint = if (button in pressedButtons) buttonPressedPaint else buttonPaint
            canvas.drawRoundRect(button.rect, 12f * density, 12f * density, paint)
            centerTextPaint.textSize = if (button.label.length > 1) 14f * density else 26f * density
            drawCenteredText(canvas, button.label, button.rect)
        }
    }

    private fun drawOverlay(canvas: Canvas, title: String, subtitle: String) {
        canvas.drawRect(boardRect, overlayPaint)
        centerTextPaint.textSize = 30f * density
        canvas.drawText(title, boardRect.centerX(), boardRect.centerY(), centerTextPaint)
        centerTextPaint.textSize = 14f * density
        canvas.drawText(subtitle, boardRect.centerX(), boardRect.centerY() + 32f * density, centerTextPaint)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, rect: RectF) {
        val baseline = rect.centerY() - (centerTextPaint.descent() + centerTextPaint.ascent()) / 2f
        canvas.drawText(text, rect.centerX(), baseline, centerTextPaint)
    }

    private class Button(
        val label: String,
        val repeats: Boolean,
        private val onPress: () -> Any,
    ) {
        val rect = RectF()
        var nextRepeat = 0L

        fun action() {
            onPress()
        }
    }

    companion object {
        private const val KEY_HIGH_SCORE = "high_score"
        private const val REPEAT_DELAY_MS = 180L
        private const val REPEAT_INTERVAL_MS = 55L

        /** Colors indexed by [Tetromino.ordinal]: I, O, T, S, Z, J, L. */
        private val COLORS = intArrayOf(
            Color.rgb(0, 214, 230),
            Color.rgb(245, 208, 0),
            Color.rgb(170, 70, 230),
            Color.rgb(60, 205, 80),
            Color.rgb(235, 60, 60),
            Color.rgb(50, 100, 235),
            Color.rgb(245, 140, 20),
        )
    }
}
