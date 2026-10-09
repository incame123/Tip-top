package com.example.domain.game

import kotlin.random.Random

/**
 * Pure, deterministic-capable Tic-Tac-Toe rules engine and AI move selector.
 * Supports Local Multiplayer, Online validation, and Easy / Medium / Hard (Minimax) Robot AI.
 */
object TicTacToeEngine {

    val WINNING_LINES: List<List<Int>> = listOf(
        listOf(0, 1, 2), // Top row
        listOf(3, 4, 5), // Middle row
        listOf(6, 7, 8), // Bottom row
        listOf(0, 3, 6), // Left column
        listOf(1, 4, 7), // Center column
        listOf(2, 5, 8), // Right column
        listOf(0, 4, 8), // Main diagonal
        listOf(2, 4, 6)  // Anti-diagonal
    )

    private val CORNERS = listOf(0, 2, 6, 8)
    private const val CENTER = 4

    /**
     * Evaluates a 9-cell board and returns a pair of (GameResult, winningLineIndices).
     */
    fun evaluateBoard(cells: List<String>): Pair<GameResult, List<Int>> {
        require(cells.size == 9) { "Board must contain exactly 9 cells." }

        for (line in WINNING_LINES) {
            val a = cells[line[0]]
            val b = cells[line[1]]
            val c = cells[line[2]]
            if (a.isNotEmpty() && a == b && b == c) {
                val result = if (a == PlayerSymbol.X.label) GameResult.X_WON else GameResult.O_WON
                return result to line
            }
        }

        if (cells.none { it.isEmpty() }) {
            return GameResult.DRAW to emptyList()
        }

        return GameResult.IN_PROGRESS to emptyList()
    }

    /**
     * Attempts to apply a move at [cellIndex] for [state.currentTurn].
     * Returns null if the move is invalid (out of bounds, cell occupied, or game already finished).
     */
    fun makeMove(state: BoardState, cellIndex: Int): BoardState? {
        if (state.isTerminal) return null
        if (cellIndex !in 0..8) return null
        if (state.cells[cellIndex].isNotEmpty()) return null

        val updatedCells = state.cells.toMutableList().apply {
            this[cellIndex] = state.currentTurn.label
        }
        val (result, winningLine) = evaluateBoard(updatedCells)
        val nextTurn = if (result.isGameOver) state.currentTurn else state.currentTurn.opponent()

        return state.copy(
            cells = updatedCells,
            currentTurn = nextTurn,
            result = result,
            winningCells = winningLine,
            moveHistory = state.moveHistory + cellIndex
        )
    }

    /**
     * Validates whether a proposed board transition from [previousBoard] to [newBoard]
     * by [playerSymbol] is a single legal move.
     */
    fun isValidTransition(
        previousBoard: List<String>,
        newBoard: List<String>,
        playerSymbol: PlayerSymbol
    ): Boolean {
        if (previousBoard.size != 9 || newBoard.size != 9) return false
        val (prevResult, _) = evaluateBoard(previousBoard)
        if (prevResult.isGameOver) return false

        var diffCount = 0
        for (i in 0..8) {
            if (previousBoard[i] != newBoard[i]) {
                if (previousBoard[i].isNotEmpty()) return false
                if (newBoard[i] != playerSymbol.label) return false
                diffCount++
            }
        }
        return diffCount == 1
    }

    /**
     * Computes the Robot AI's next cell index (0..8) or returns -1 if no valid move exists
     * (e.g. game already finished or board full).
     */
    fun chooseRobotMove(
        state: BoardState,
        difficulty: RobotDifficulty,
        robotSymbol: PlayerSymbol = state.currentTurn,
        random: Random = Random.Default
    ): Int {
        if (state.isTerminal) return -1
        val (evalResult, _) = evaluateBoard(state.cells)
        if (evalResult.isGameOver) return -1

        val emptyCells = state.cells.mapIndexedNotNull { index, value ->
            if (value.isEmpty()) index else null
        }
        if (emptyCells.isEmpty()) return -1

        return when (difficulty) {
            RobotDifficulty.EASY -> emptyCells.random(random)
            RobotDifficulty.MEDIUM -> chooseMediumMove(state.cells, emptyCells, robotSymbol, random)
            RobotDifficulty.HARD -> chooseHardMinimaxMove(state.cells, emptyCells, robotSymbol)
        }
    }

    private fun chooseMediumMove(
        cells: List<String>,
        emptyCells: List<Int>,
        robotSymbol: PlayerSymbol,
        random: Random
    ): Int {
        val humanSymbol = robotSymbol.opponent()

        // 1. Prioritize an immediate winning move
        findImmediateWinningCell(cells, emptyCells, robotSymbol)?.let { return it }

        // 2. Block opponent's immediate winning move
        findImmediateWinningCell(cells, emptyCells, humanSymbol)?.let { return it }

        // 3. Controlled randomness: 75% tactical preference (center -> corners), 25% random valid cell
        if (random.nextFloat() < 0.75f) {
            if (CENTER in emptyCells) return CENTER
            val availableCorners = CORNERS.filter { it in emptyCells }
            if (availableCorners.isNotEmpty()) {
                return availableCorners.random(random)
            }
        }

        return emptyCells.random(random)
    }

    private fun findImmediateWinningCell(
        cells: List<String>,
        emptyCells: List<Int>,
        symbol: PlayerSymbol
    ): Int? {
        for (idx in emptyCells) {
            val testBoard = cells.toMutableList().apply { this[idx] = symbol.label }
            val (result, _) = evaluateBoard(testBoard)
            if ((symbol == PlayerSymbol.X && result == GameResult.X_WON) ||
                (symbol == PlayerSymbol.O && result == GameResult.O_WON)
            ) {
                return idx
            }
        }
        return null
    }

    /**
     * Unbeatable Minimax algorithm with alpha-beta pruning and depth penalty so the AI
     * wins as quickly as possible and never loses against optimal play.
     */
    private fun chooseHardMinimaxMove(
        cells: List<String>,
        emptyCells: List<Int>,
        robotSymbol: PlayerSymbol
    ): Int {
        // Opening optimization: if board is completely empty, pick center (4) immediately
        if (emptyCells.size == 9) return CENTER

        val board = cells.toMutableList()
        var bestScore = Int.MIN_VALUE
        var bestMove = emptyCells.first()

        for (cellIndex in emptyCells) {
            board[cellIndex] = robotSymbol.label
            val score = minimax(
                board = board,
                depth = 0,
                isMaximizing = false,
                robotSymbol = robotSymbol,
                alpha = Int.MIN_VALUE,
                beta = Int.MAX_VALUE
            )
            board[cellIndex] = ""

            if (score > bestScore) {
                bestScore = score
                bestMove = cellIndex
            }
        }
        return bestMove
    }

    private fun minimax(
        board: MutableList<String>,
        depth: Int,
        isMaximizing: Boolean,
        robotSymbol: PlayerSymbol,
        alpha: Int,
        beta: Int
    ): Int {
        val (result, _) = evaluateBoard(board)
        val humanSymbol = robotSymbol.opponent()

        when (result) {
            GameResult.X_WON -> return if (robotSymbol == PlayerSymbol.X) 100 - depth else depth - 100
            GameResult.O_WON -> return if (robotSymbol == PlayerSymbol.O) 100 - depth else depth - 100
            GameResult.DRAW -> return 0
            GameResult.IN_PROGRESS -> Unit
        }

        var currentAlpha = alpha
        var currentBeta = beta

        if (isMaximizing) {
            var maxEval = Int.MIN_VALUE
            for (i in 0..8) {
                if (board[i].isEmpty()) {
                    board[i] = robotSymbol.label
                    val eval = minimax(board, depth + 1, false, robotSymbol, currentAlpha, currentBeta)
                    board[i] = ""
                    if (eval > maxEval) maxEval = eval
                    if (eval > currentAlpha) currentAlpha = eval
                    if (currentBeta <= currentAlpha) break
                }
            }
            return maxEval
        } else {
            var minEval = Int.MAX_VALUE
            for (i in 0..8) {
                if (board[i].isEmpty()) {
                    board[i] = humanSymbol.label
                    val eval = minimax(board, depth + 1, true, robotSymbol, currentAlpha, currentBeta)
                    board[i] = ""
                    if (eval < minEval) minEval = eval
                    if (eval < currentBeta) currentBeta = eval
                    if (currentBeta <= currentAlpha) break
                }
            }
            return minEval
        }
    }
}
