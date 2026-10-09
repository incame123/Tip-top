package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class OnlineGameRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndJoinRoom_andSynchronizeMoves_succeedsForParticipants() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = OnlineGameRepository(firestore, auth)
        val roomCode = "RM" + UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

        val createdRoom = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createRoom(customRoomCode = roomCode, hostDisplayName = "Alice").getOrThrow()
        }
        assertEquals(roomCode, createdRoom.roomId)
        assertEquals(aliceUid, createdRoom.hostUid)
        assertEquals("waiting", createdRoom.status)

        // Bob signs in and joins Alice's room
        val bobUid = signInTestUser(BOB_EMAIL)
        val joinedRoom = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.joinRoom(roomCode, guestDisplayName = "Bob").getOrThrow()
        }
        assertEquals(bobUid, joinedRoom.guestUid)
        assertEquals("playing", joinedRoom.status)

        // Alice (Host, X) makes first move at cell 0
        signInTestUser(ALICE_EMAIL)
        val moveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitOnlineMove(joinedRoom, 0)
        }
        assertTrue(moveResult.isSuccess)

        // Observe updated room state
        val updatedRoom = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeRoom(roomCode).first { it != null && it.board[0] == "X" }
        }
        assertNotNull(updatedRoom)
        assertEquals("O", updatedRoom!!.currentTurn)
    }

    @Test
    fun logCompletedGame_andQueryOwnGames_enforcesUserIsolation() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = OnlineGameRepository(firestore, auth)

        val gameId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.logCompletedGame(
                board = listOf("X", "X", "X", "O", "O", "", "", "", ""),
                result = "X_WON",
                mode = "ROBOT_HARD"
            ).getOrThrow()
        }
        assertTrue(gameId.isNotEmpty())

        val aliceGames = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getUserGames().getOrThrow()
        }
        assertTrue(aliceGames.any { it.gameId == gameId && it.userId == aliceUid })

        // Cross-user read of Alice's user profile by Bob must fail with PERMISSION_DENIED
        signInTestUser(BOB_EMAIL)
        val crossProfileResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getUserProfile(aliceUid)
        }
        assertTrue(crossProfileResult.isFailure)
        val ex = crossProfileResult.exceptionOrNull() as? FirebaseFirestoreException
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
    }

    @Test
    fun unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = OnlineGameRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserGames("unauth_uid").first()
            }
            fail("Expected FirebaseFirestoreException.Code.PERMISSION_DENIED")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }

    @Test
    fun consentAndDiscoveryBatch_saveAndDelete_succeedsForOwner() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = OnlineGameRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice").getOrThrow()
            repo.saveConsentRecord(
                localContactsGranted = true,
                serverMatchingGranted = true,
                policyVersion = "1.0.0"
            ).getOrThrow()
        }

        val batchId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitDiscoveryBatch(
                tokenDigests = listOf("hmac_v1_a1b2c3d4", "hmac_v1_e5f6g7h8")
            ).getOrThrow()
        }
        assertTrue(batchId.startsWith("batch_"))

        val batches = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeDiscoveryBatches(aliceUid).first { it.isNotEmpty() }
        }
        assertTrue(batches.any { it.batchId == batchId })

        val deletedCount = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.deleteContactDiscoveryData().getOrThrow()
        }
        assertTrue(deletedCount >= 1)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_rule_test@test.com"
        const val BOB_EMAIL = "bob_rule_test@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
