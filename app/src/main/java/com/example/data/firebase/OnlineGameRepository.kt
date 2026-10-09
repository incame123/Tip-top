package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.domain.game.GameResult
import com.example.domain.game.PlayerSymbol
import com.example.domain.game.TicTacToeEngine
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for Firebase Firestore Online Multiplayer Rooms, Synced Games, User Profile,
 * and Privacy Consent records.
 *
 * CRITICAL: Always uses the provisioned named database ID (`R.string.firestore_database_id`)
 * and requires authenticated Google Sign-In users.
 */
class OnlineGameRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        FirebaseAuth.getInstance()
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // -------------------------------------------------------------------
    // 1. User Profile Management (/users/{uid})
    // -------------------------------------------------------------------
    suspend fun ensureUserProfile(
        displayName: String,
        verifiedPhoneNumber: String? = null
    ): Result<UserProfileModel> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfileModel::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfileModel(userId = uid, displayName = displayName)
                existing
            } else {
                val profile = UserProfileModel(
                    userId = uid,
                    displayName = displayName.ifBlank { "Player" }.take(50),
                    verifiedPhoneNumber = verifiedPhoneNumber,
                    contactDiscoveryEnabled = false,
                    serverMatchingConsent = false
                )
                docRef.set(profile.toCreateMap()).await()
                profile
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    suspend fun getUserProfile(uid: String = requireUserId()): Result<UserProfileModel?> = runCatching {
        val path = "users/$uid"
        try {
            val snap = db.collection("users").document(uid).get().await()
            if (!snap.exists()) null
            else snap.toObject(
                UserProfileModel::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            throw e
        }
    }

    suspend fun updatePrivacySettingsInProfile(
        contactDiscoveryEnabled: Boolean,
        serverMatchingConsent: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val displayName = auth.currentUser?.displayName?.take(50).orEmpty().ifBlank { "Player" }
                val createMap = UserProfileModel(
                    userId = uid,
                    displayName = displayName,
                    contactDiscoveryEnabled = contactDiscoveryEnabled,
                    serverMatchingConsent = serverMatchingConsent
                ).toCreateMap()
                docRef.set(createMap).await()
            } else {
                docRef.update(
                    mapOf(
                        "contactDiscoveryEnabled" to contactDiscoveryEnabled,
                        "serverMatchingConsent" to serverMatchingConsent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    // -------------------------------------------------------------------
    // 2. Online Multiplayer Rooms (/rooms/{roomId})
    // -------------------------------------------------------------------
    fun generateRoomCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun createRoom(
        customRoomCode: String? = null,
        hostDisplayName: String? = null
    ): Result<OnlineRoomModel> = runCatching {
        val uid = requireUserId()
        val roomCode = (customRoomCode?.uppercase()?.trim().takeUnless { it.isNullOrBlank() }
            ?: generateRoomCode())
        val hostName = (hostDisplayName ?: auth.currentUser?.displayName ?: "Player X")
            .trim()
            .ifBlank { "Player X" }
            .take(50)

        val roomModel = OnlineRoomModel(
            roomId = roomCode,
            hostUid = uid,
            hostName = hostName,
            guestUid = "",
            guestName = "",
            board = List(9) { "" },
            currentTurn = "X",
            status = "waiting",
            winner = "",
            winningCells = emptyList(),
            hostScore = 0,
            guestScore = 0,
            draws = 0,
            roundNumber = 1,
            hostConnected = true,
            guestConnected = false,
            expiresAt = System.currentTimeMillis() + 3_600_000L
        )

        val path = "rooms/$roomCode"
        try {
            db.collection("rooms").document(roomCode).set(roomModel.toCreateMap()).await()
            roomModel
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun joinRoom(
        rawRoomCode: String,
        guestDisplayName: String? = null
    ): Result<OnlineRoomModel> = runCatching {
        val uid = requireUserId()
        val roomCode = rawRoomCode.trim().uppercase()
        require(roomCode.length in 4..32 && roomCode.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
            "Invalid room code format. Room codes must be 4-32 alphanumeric characters."
        }

        val path = "rooms/$roomCode"
        val docRef = db.collection("rooms").document(roomCode)
        try {
            val snap = docRef.get().await()
            val room = OnlineRoomModel.fromSnapshot(snap)
                ?: throw IllegalArgumentException("Room '$roomCode' was not found. Check the code and try again.")

            // Rejoin if caller is already host or guest
            if (room.hostUid == uid) {
                docRef.update(
                    mapOf(
                        "hostConnected" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return@runCatching room.copy(hostConnected = true)
            }
            if (room.guestUid == uid) {
                docRef.update(
                    mapOf(
                        "guestConnected" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return@runCatching room.copy(guestConnected = true)
            }

            if (room.status != "waiting" || room.guestUid.isNotEmpty()) {
                throw IllegalStateException("Room '$roomCode' is already full or no longer accepting players.")
            }

            val guestName = (guestDisplayName ?: auth.currentUser?.displayName ?: "Player O")
                .trim()
                .ifBlank { "Player O" }
                .take(50)

            docRef.update(
                mapOf(
                    "guestUid" to uid,
                    "guestName" to guestName,
                    "guestConnected" to true,
                    "status" to "playing",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            room.copy(
                guestUid = uid,
                guestName = guestName,
                guestConnected = true,
                status = "playing"
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    fun observeRoom(roomCode: String): Flow<OnlineRoomModel?> {
        val normalized = roomCode.trim().uppercase()
        val path = "rooms/$normalized"
        return db.collection("rooms").document(normalized)
            .snapshots()
            .map { snap -> OnlineRoomModel.fromSnapshot(snap) }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun submitOnlineMove(
        room: OnlineRoomModel,
        cellIndex: Int
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(room.status == "playing") { "Game is not currently active." }
        require(cellIndex in 0..8) { "Cell index out of bounds." }
        require(room.board[cellIndex].isEmpty()) { "Selected cell is already occupied." }

        val mySymbol = when (uid) {
            room.hostUid -> PlayerSymbol.X
            room.guestUid -> PlayerSymbol.O
            else -> throw IllegalStateException("You are not a participant in this room.")
        }
        require(room.currentTurn == mySymbol.label) { "It is currently Player ${room.currentTurn}'s turn." }

        val newBoard = room.board.toMutableList().apply {
            this[cellIndex] = mySymbol.label
        }

        // Validate transition via authoritative engine
        require(TicTacToeEngine.isValidTransition(room.board, newBoard, mySymbol)) {
            "Invalid board state transition."
        }

        val (evalResult, winningLine) = TicTacToeEngine.evaluateBoard(newBoard)
        val nextTurn = if (evalResult.isGameOver) room.currentTurn else mySymbol.opponent().label
        val newStatus = if (evalResult.isGameOver) "finished" else "playing"
        val winnerStr = when (evalResult) {
            GameResult.X_WON -> "X"
            GameResult.O_WON -> "O"
            GameResult.DRAW -> "DRAW"
            GameResult.IN_PROGRESS -> ""
        }

        val newHostScore = room.hostScore + if (evalResult == GameResult.X_WON) 1 else 0
        val newGuestScore = room.guestScore + if (evalResult == GameResult.O_WON) 1 else 0
        val newDraws = room.draws + if (evalResult == GameResult.DRAW) 1 else 0

        val path = "rooms/${room.roomId}"
        try {
            db.collection("rooms").document(room.roomId).update(
                mapOf(
                    "board" to newBoard,
                    "currentTurn" to nextTurn,
                    "status" to newStatus,
                    "winner" to winnerStr,
                    "winningCells" to winningLine,
                    "hostScore" to newHostScore,
                    "guestScore" to newGuestScore,
                    "draws" to newDraws,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun requestRematch(room: OnlineRoomModel): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid == room.hostUid || uid == room.guestUid) {
            "Only room participants can start a rematch."
        }
        require(room.status == "finished") {
            "Rematch is only available after the current round finishes."
        }

        val nextRound = room.roundNumber + 1
        // Alternate starting symbol on each rematch for fairness
        val startingTurn = if (nextRound % 2 == 1) "X" else "O"
        val path = "rooms/${room.roomId}"

        try {
            db.collection("rooms").document(room.roomId).update(
                mapOf(
                    "board" to List(9) { "" },
                    "currentTurn" to startingTurn,
                    "status" to "playing",
                    "winner" to "",
                    "winningCells" to emptyList<Int>(),
                    "roundNumber" to nextRound,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    suspend fun updateConnectionPresence(
        room: OnlineRoomModel,
        connected: Boolean,
        abandonIfLeaving: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val isHost = uid == room.hostUid
        val isGuest = uid == room.guestUid
        if (!isHost && !isGuest) return@runCatching

        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (isHost) {
            updates["hostConnected"] = connected
        } else {
            updates["guestConnected"] = connected
        }
        if (abandonIfLeaving && room.status != "finished") {
            updates["status"] = "abandoned"
        }

        val path = "rooms/${room.roomId}"
        try {
            db.collection("rooms").document(room.roomId).update(updates).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            throw e
        }
    }

    // -------------------------------------------------------------------
    // 3. Synced Game Records (/games/{gameId})
    // -------------------------------------------------------------------
    suspend fun logCompletedGame(
        board: List<String>,
        result: String,
        mode: String,
        opponentUid: String? = null
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val gameId = "game_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val players = if (opponentUid.isNullOrBlank()) listOf(uid) else listOf(uid, opponentUid)
        val model = SyncedGameRecordModel(
            gameId = gameId,
            userId = uid,
            playerUids = players,
            board = if (board.size == 9) board else List(9) { "" },
            currentTurn = "X",
            status = "finished",
            result = result,
            mode = mode
        )
        val path = "games/$gameId"
        try {
            db.collection("games").document(gameId).set(model.toCreateMap()).await()
            gameId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    fun observeUserGames(userId: String): Flow<List<SyncedGameRecordModel>> {
        val path = "games"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { SyncedGameRecordModel.fromSnapshot(it) }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun getUserGames(): Result<List<SyncedGameRecordModel>> = runCatching {
        val uid = requireUserId()
        val path = "games"
        try {
            val snap = db.collection(path)
                .whereEqualTo("userId", uid)
                .get()
                .await()
            snap.documents.mapNotNull { SyncedGameRecordModel.fromSnapshot(it) }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    // -------------------------------------------------------------------
    // 4. Privacy Consents & Discovery Batch Management (/consents/{uid})
    // -------------------------------------------------------------------
    suspend fun saveConsentRecord(
        localContactsGranted: Boolean,
        serverMatchingGranted: Boolean,
        policyVersion: String = "1.0.0",
        isRevocation: Boolean = false
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val path = "consents/$uid"
        val docRef = db.collection("consents").document(uid)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val createPayload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "consentPurpose" to "contact_discovery_matching",
                    "policyVersion" to policyVersion,
                    "localContactsGranted" to localContactsGranted,
                    "serverMatchingGranted" to serverMatchingGranted,
                    "grantedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isRevocation) {
                    createPayload["revokedAt"] = FieldValue.serverTimestamp()
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "consentPurpose" to "contact_discovery_matching",
                    "policyVersion" to policyVersion,
                    "localContactsGranted" to localContactsGranted,
                    "serverMatchingGranted" to serverMatchingGranted,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isRevocation) {
                    updatePayload["revokedAt"] = FieldValue.serverTimestamp()
                }
                docRef.update(updatePayload).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            throw e
        }
    }

    suspend fun submitDiscoveryBatch(
        tokenDigests: List<String>,
        policyVersion: String = "1.0.0",
        retentionDays: Int = 30
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val boundedDigests = tokenDigests.take(50)
        val batchId = "batch_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val expiresAt = System.currentTimeMillis() + (retentionDays * 86_400_000L)
        val path = "users/$uid/discoveryBatches/$batchId"

        val payload = mapOf(
            "batchId" to batchId,
            "userId" to uid,
            "tokenCount" to boundedDigests.size,
            "tokenDigests" to boundedDigests,
            "policyVersion" to policyVersion,
            "expiresAt" to expiresAt,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            db.collection("users").document(uid)
                .collection("discoveryBatches").document(batchId)
                .set(payload)
                .await()
            batchId
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    fun observeDiscoveryBatches(userId: String): Flow<List<DiscoveryBatchModel>> {
        val path = "users/$userId/discoveryBatches"
        return db.collection("users").document(userId)
            .collection("discoveryBatches")
            .snapshots()
            .map { snap ->
                snap.documents.map { doc ->
                    val digests = (doc.get("tokenDigests") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    DiscoveryBatchModel(
                        batchId = doc.getString("batchId") ?: doc.id,
                        userId = doc.getString("userId").orEmpty(),
                        tokenCount = (doc.getLong("tokenCount") ?: 0L).toInt(),
                        tokenDigests = digests,
                        policyVersion = doc.getString("policyVersion") ?: "1.0.0",
                        expiresAt = doc.getLong("expiresAt") ?: 0L,
                        createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    )
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                throw error
            }
    }

    /**
     * Deletes all user-submitted contact discovery batches, revokes consent, and optionally
     * deletes all synced cloud games and user profile document.
     */
    suspend fun deleteContactDiscoveryData(): Result<Int> = runCatching {
        val uid = requireUserId()
        val batchesPath = "users/$uid/discoveryBatches"
        var deletedCount = 0
        try {
            val batches = db.collection("users").document(uid)
                .collection("discoveryBatches")
                .get()
                .await()
            for (doc in batches.documents) {
                doc.reference.delete().await()
                deletedCount++
            }
            saveConsentRecord(
                localContactsGranted = false,
                serverMatchingGranted = false,
                isRevocation = true
            ).getOrThrow()
            updatePrivacySettingsInProfile(
                contactDiscoveryEnabled = false,
                serverMatchingConsent = false
            ).getOrThrow()
            deletedCount
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, batchesPath)
            throw e
        }
    }

    suspend fun deleteAllUserCloudData(): Result<Unit> = runCatching {
        val uid = requireUserId()
        try {
            // 1. Delete discovery batches
            val batches = db.collection("users").document(uid)
                .collection("discoveryBatches")
                .get()
                .await()
            for (doc in batches.documents) {
                doc.reference.delete().await()
            }

            // 2. Delete user games
            val games = db.collection("games")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            for (doc in games.documents) {
                doc.reference.delete().await()
            }

            // 3. Delete consent record
            val consentRef = db.collection("consents").document(uid)
            if (consentRef.get().await().exists()) {
                consentRef.delete().await()
            }

            // 4. Delete user profile
            val userRef = db.collection("users").document(uid)
            if (userRef.get().await().exists()) {
                userRef.delete().await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            throw e
        }
    }
}
