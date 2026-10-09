package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Type-safe Kotlin data models with default values for every property (synthetic no-arg constructor)
 * and safe numeric/timestamp deserialization helpers.
 */
data class UserProfileModel(
    val userId: String = "",
    val displayName: String = "Player",
    val verifiedPhoneNumber: String? = null,
    val contactDiscoveryEnabled: Boolean = false,
    val serverMatchingConsent: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "userId" to userId,
            "displayName" to displayName.take(50).ifBlank { "Player" },
            "verifiedPhoneNumber" to verifiedPhoneNumber?.take(20),
            "contactDiscoveryEnabled" to contactDiscoveryEnabled,
            "serverMatchingConsent" to serverMatchingConsent,
            "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null } as Map<String, Any>
    }
}

data class OnlineRoomModel(
    val roomId: String = "",
    val hostUid: String = "",
    val hostName: String = "Host",
    val guestUid: String = "",
    val guestName: String = "",
    val board: List<String> = List(9) { "" },
    val currentTurn: String = "X",
    val status: String = "waiting", // waiting, playing, finished, abandoned
    val winner: String = "", // "", "X", "O", "DRAW"
    val winningCells: List<Int> = emptyList(),
    val hostScore: Int = 0,
    val guestScore: Int = 0,
    val draws: Int = 0,
    val roundNumber: Int = 1,
    val hostConnected: Boolean = true,
    val guestConnected: Boolean = false,
    val expiresAt: Long = System.currentTimeMillis() + 3_600_000L,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "roomId" to roomId,
            "hostUid" to hostUid,
            "hostName" to hostName.take(50).ifBlank { "Host" },
            "guestUid" to guestUid,
            "guestName" to guestName,
            "board" to if (board.size == 9) board else List(9) { "" },
            "currentTurn" to currentTurn,
            "status" to status,
            "winner" to winner,
            "winningCells" to winningCells,
            "hostScore" to hostScore,
            "guestScore" to guestScore,
            "draws" to draws,
            "roundNumber" to roundNumber,
            "hostConnected" to hostConnected,
            "guestConnected" to guestConnected,
            "expiresAt" to expiresAt,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): OnlineRoomModel? {
            if (!doc.exists()) return null
            val rawBoard = (doc.get("board") as? List<*>)?.map { (it as? String).orEmpty() }
            val safeBoard = if (rawBoard != null && rawBoard.size == 9) rawBoard else List(9) { "" }
            val rawWinning = (doc.get("winningCells") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()

            return OnlineRoomModel(
                roomId = doc.getString("roomId") ?: doc.id,
                hostUid = doc.getString("hostUid").orEmpty(),
                hostName = doc.getString("hostName") ?: "Host",
                guestUid = doc.getString("guestUid").orEmpty(),
                guestName = doc.getString("guestName").orEmpty(),
                board = safeBoard,
                currentTurn = doc.getString("currentTurn") ?: "X",
                status = doc.getString("status") ?: "waiting",
                winner = doc.getString("winner").orEmpty(),
                winningCells = rawWinning,
                hostScore = (doc.getLong("hostScore") ?: 0L).toInt(),
                guestScore = (doc.getLong("guestScore") ?: 0L).toInt(),
                draws = (doc.getLong("draws") ?: 0L).toInt(),
                roundNumber = (doc.getLong("roundNumber") ?: 1L).toInt(),
                hostConnected = doc.getBoolean("hostConnected") ?: true,
                guestConnected = doc.getBoolean("guestConnected") ?: false,
                expiresAt = doc.getLong("expiresAt") ?: 0L,
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class SyncedGameRecordModel(
    val gameId: String = "",
    val userId: String = "",
    val playerUids: List<String> = emptyList(),
    val board: List<String> = List(9) { "" },
    val currentTurn: String = "X",
    val status: String = "finished",
    val result: String = "DRAW",
    val mode: String = "LOCAL",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        return mapOf(
            "gameId" to gameId,
            "userId" to userId,
            "playerUids" to playerUids.take(2),
            "board" to if (board.size == 9) board else List(9) { "" },
            "currentTurn" to currentTurn,
            "status" to status,
            "result" to result,
            "mode" to mode,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): SyncedGameRecordModel? {
            if (!doc.exists()) return null
            val rawBoard = (doc.get("board") as? List<*>)?.map { (it as? String).orEmpty() } ?: List(9) { "" }
            val rawPlayers = (doc.get("playerUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            return SyncedGameRecordModel(
                gameId = doc.getString("gameId") ?: doc.id,
                userId = doc.getString("userId").orEmpty(),
                playerUids = rawPlayers,
                board = if (rawBoard.size == 9) rawBoard else List(9) { "" },
                currentTurn = doc.getString("currentTurn") ?: "X",
                status = doc.getString("status") ?: "finished",
                result = doc.getString("result") ?: "DRAW",
                mode = doc.getString("mode") ?: "LOCAL",
                createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

data class ConsentRecordModel(
    val userId: String = "",
    val consentPurpose: String = "contact_discovery_matching",
    val policyVersion: String = "1.0.0",
    val localContactsGranted: Boolean = false,
    val serverMatchingGranted: Boolean = false,
    val grantedAt: Timestamp? = null,
    val revokedAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

data class DiscoveryBatchModel(
    val batchId: String = "",
    val userId: String = "",
    val tokenCount: Int = 0,
    val tokenDigests: List<String> = emptyList(),
    val policyVersion: String = "1.0.0",
    val expiresAt: Long = 0L,
    val createdAt: Timestamp? = null
)
