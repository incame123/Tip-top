package com.example

import com.example.domain.contacts.ConsentState
import com.example.domain.contacts.NormalizedLocalContact
import com.example.domain.contacts.PhoneNormalizationModule
import com.example.domain.contacts.SecureContactMatchingModule
import com.example.domain.game.BoardState
import com.example.domain.game.GameResult
import com.example.domain.game.PlayerSymbol
import com.example.domain.game.RobotDifficulty
import com.example.domain.game.TicTacToeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

class ExampleUnitTest {

    @Before
    fun setUp() {
        SecureContactMatchingModule.resetRateLimiterForTesting()
    }

    @Test
    fun allEightWinningCombinations_areDetectedForXAndO() {
        for (line in TicTacToeEngine.WINNING_LINES) {
            val xBoard = MutableList(9) { "" }
            line.forEach { idx -> xBoard[idx] = "X" }
            val (xResult, xWinLine) = TicTacToeEngine.evaluateBoard(xBoard)
            assertEquals("Expected X_WON for line $line", GameResult.X_WON, xResult)
            assertEquals(line, xWinLine)

            val oBoard = MutableList(9) { "" }
            line.forEach { idx -> oBoard[idx] = "O" }
            val (oResult, oWinLine) = TicTacToeEngine.evaluateBoard(oBoard)
            assertEquals("Expected O_WON for line $line", GameResult.O_WON, oResult)
            assertEquals(line, oWinLine)
        }
    }

    @Test
    fun drawDetection_detectsFullBoardWithNoWinner() {
        // X O X
        // X O O
        // O X X
        val drawnBoard = listOf(
            "X", "O", "X",
            "X", "O", "O",
            "O", "X", "X"
        )
        val (result, winningLine) = TicTacToeEngine.evaluateBoard(drawnBoard)
        assertEquals(GameResult.DRAW, result)
        assertTrue(winningLine.isEmpty())
    }

    @Test
    fun invalidAndRepeatedMoves_areRejected() {
        var state = BoardState()
        // Out-of-bounds moves return null
        assertNull(TicTacToeEngine.makeMove(state, -1))
        assertNull(TicTacToeEngine.makeMove(state, 9))

        // Valid move at 4 (X)
        state = checkNotNull(TicTacToeEngine.makeMove(state, 4))
        assertEquals("X", state.cells[4])
        assertEquals(PlayerSymbol.O, state.currentTurn)

        // Repeated move at occupied cell 4 returns null
        assertNull(TicTacToeEngine.makeMove(state, 4))

        // Complete a winning game for X: X at 0, 1, 2
        val finishedState = BoardState(
            cells = listOf("X", "X", "X", "O", "O", "", "", "", ""),
            currentTurn = PlayerSymbol.O,
            result = GameResult.X_WON,
            winningCells = listOf(0, 1, 2)
        )
        // Move after game ended returns null
        assertNull(TicTacToeEngine.makeMove(finishedState, 5))
        // Robot never plays after game ended
        assertEquals(-1, TicTacToeEngine.chooseRobotMove(finishedState, RobotDifficulty.EASY))
        assertEquals(-1, TicTacToeEngine.chooseRobotMove(finishedState, RobotDifficulty.MEDIUM))
        assertEquals(-1, TicTacToeEngine.chooseRobotMove(finishedState, RobotDifficulty.HARD))
    }

    @Test
    fun mediumRobot_prioritizesWinningMove_andBlocksOpponentThreat() {
        // 1. Robot (O) has two in a row at 0 and 1 -> must pick 2 to win immediately
        val winOpportunity = BoardState(
            cells = listOf(
                "O", "O", "",
                "X", "X", "",
                "", "", ""
            ),
            currentTurn = PlayerSymbol.O
        )
        val winningMove = TicTacToeEngine.chooseRobotMove(
            state = winOpportunity,
            difficulty = RobotDifficulty.MEDIUM,
            robotSymbol = PlayerSymbol.O
        )
        assertEquals(2, winningMove)

        // 2. Human (X) threatens 0 and 1 -> Robot (O) must block at 2
        val blockOpportunity = BoardState(
            cells = listOf(
                "X", "X", "",
                "O", "", "",
                "", "", ""
            ),
            currentTurn = PlayerSymbol.O
        )
        val blockingMove = TicTacToeEngine.chooseRobotMove(
            state = blockOpportunity,
            difficulty = RobotDifficulty.MEDIUM,
            robotSymbol = PlayerSymbol.O
        )
        assertEquals(2, blockingMove)
    }

    @Test
    fun hardMinimaxRobot_neverLosesAgainstAnyPlay() {
        val rng = Random(42)

        // Simulate 50 games where Human (X) plays first and Hard Robot (O) responds
        repeat(50) {
            var state = BoardState()
            while (!state.isTerminal) {
                val move = if (state.currentTurn == PlayerSymbol.X) {
                    TicTacToeEngine.chooseRobotMove(state, RobotDifficulty.EASY, PlayerSymbol.X, rng)
                } else {
                    TicTacToeEngine.chooseRobotMove(state, RobotDifficulty.HARD, PlayerSymbol.O, rng)
                }
                assertTrue(move in 0..8)
                state = checkNotNull(TicTacToeEngine.makeMove(state, move))
            }
            // Hard robot (O) must NEVER lose (result is either O_WON or DRAW)
            assertNotNull(state.result)
            assertFalse("Hard Minimax O must never lose", state.result == GameResult.X_WON)
        }

        // Simulate 30 games where Hard Robot (X) plays first against Medium Robot (O)
        repeat(30) {
            var state = BoardState()
            while (!state.isTerminal) {
                val move = if (state.currentTurn == PlayerSymbol.X) {
                    TicTacToeEngine.chooseRobotMove(state, RobotDifficulty.HARD, PlayerSymbol.X, rng)
                } else {
                    TicTacToeEngine.chooseRobotMove(state, RobotDifficulty.MEDIUM, PlayerSymbol.O, rng)
                }
                assertTrue(move in 0..8)
                state = checkNotNull(TicTacToeEngine.makeMove(state, move))
            }
            assertFalse("Hard Minimax X must never lose", state.result == GameResult.O_WON)
        }
    }

    @Test
    fun phoneNormalization_normalizesValidNumbersAndRejectsInvalidInputs() {
        assertEquals("+14155552671", PhoneNormalizationModule.normalizeToE164("(415) 555-2671", "US"))
        assertEquals("+14155552671", PhoneNormalizationModule.normalizeToE164("+1 415-555-2671", "US"))
        assertEquals("+447911123456", PhoneNormalizationModule.normalizeToE164("07911 123456", "GB"))
        assertNull(PhoneNormalizationModule.normalizeToE164("12345", "US"))
        assertNull(PhoneNormalizationModule.normalizeToE164("not-a-phone", "US"))
    }

    @Test
    fun secureContactMatching_requiresExplicitConsentAndEnforcesRateLimit() {
        val sampleContacts = listOf(
            NormalizedLocalContact(
                localContactId = "1",
                localLabelHint = "Friend",
                e164Number = "+14155552671",
                maskedNumber = "+1415***2671"
            )
        )

        // Without server matching consent -> fails with SecurityException
        val unconsented = ConsentState(
            contactDiscoveryEnabled = true,
            localReadingConsentGranted = true,
            serverMatchingConsentGranted = false
        )
        val failResult = SecureContactMatchingModule.prepareRateLimitedMatchBatch(
            contacts = sampleContacts,
            userId = "user_123",
            consentState = unconsented,
            nowMs = 100_000L
        )
        assertTrue(failResult.isFailure)

        // With full two-stage consent -> succeeds and produces keyed HMAC digest
        val consented = ConsentState(
            contactDiscoveryEnabled = true,
            localReadingConsentGranted = true,
            serverMatchingConsentGranted = true
        )
        val okResult = SecureContactMatchingModule.prepareRateLimitedMatchBatch(
            contacts = sampleContacts,
            userId = "user_123",
            consentState = consented,
            nowMs = 100_000L
        )
        assertTrue(okResult.isSuccess)
        val candidates = okResult.getOrThrow()
        assertEquals(1, candidates.size)
        assertTrue(candidates.first().keyedTokenDigest.startsWith("hmac_v1_"))
        assertNull("Unverified address-book label must never be exposed as verified caller ID", candidates.first().verifiedDisplayName)

        // Immediate second request within 10s rate-limit window -> rejected
        val rateLimitedResult = SecureContactMatchingModule.prepareRateLimitedMatchBatch(
            contacts = sampleContacts,
            userId = "user_123",
            consentState = consented,
            nowMs = 102_000L
        )
        assertTrue(rateLimitedResult.isFailure)
    }
}
