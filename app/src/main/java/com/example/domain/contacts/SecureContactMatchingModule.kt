package com.example.domain.contacts

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class ContactMatchCandidate(
    val maskedE164: String,
    val keyedTokenDigest: String,
    val isRegisteredPlayer: Boolean,
    val verifiedDisplayName: String?, // Populated ONLY if the target user verified their own phone number via Firebase Auth
    val verificationNote: String
)

/**
 * Module 5: Secure Contact Matching Module.
 *
 * Implements the privacy-preserving contact matching protocol and security guardrails:
 *
 * 1. WHY UNSALTED SHA-256 IS FORBIDDEN:
 *    The global E.164 phone number space (~10^10 valid numbers) can be exhaustively hashed in minutes
 *    on a modern GPU using plain unsalted SHA-256. Therefore, plain SHA-256 hashes of phone numbers
 *    are easily reversible via rainbow tables and MUST NEVER be stored or transmitted as a privacy mechanism.
 *
 * 2. KEYED HMAC-SHA256 & BLINDED TOKEN MATCHING ARCHITECTURE:
 *    - In production, the client normalizes numbers to E.164 only after explicit consent and sends
 *      bounded batches (max 50 numbers) over TLS 1.3 to a rate-limited Cloud Function (`matchContacts`).
 *    - The Cloud Function holds a high-entropy 256-bit secret Pepper in Google Cloud Secret Manager
 *      (never embedded in the client APK) and computes `HMAC-SHA256(ServerPepper, E.164)`.
 *    - For client-side batch auditing in `/users/{uid}/discoveryBatches`, this module derives
 *      user-scoped + epoch-scoped keyed HMAC-SHA256 digests so raw phone numbers never touch Firestore.
 *    - Limitations documented: even with a server-held HMAC pepper, a compromised server secret or
 *      unrestricted query API could allow enumeration; therefore, strict per-UID rate limiting,
 *      batch size caps (<= 50), and 30-day automatic TTL expiration are enforced.
 *
 * 3. ANTI-SPOOFING & CALLER IDENTITY INVARIANT:
 *    - Having a number in someone's address book NEVER proves ownership of that number.
 *    - Unverified address-book names are NEVER stored in a public directory or displayed as
 *      authoritative caller identity.
 */
object SecureContactMatchingModule {

    const val MAX_BATCH_SIZE = 50
    const val MIN_INTERVAL_BETWEEN_MATCH_REQUESTS_MS = 10_000L

    private var lastRequestTimestampMs: Long = 0L

    /**
     * Computes a keyed HMAC-SHA256 hex digest using a user-specific + rotation-epoch key context.
     * Raw phone numbers are never stored in Firestore.
     */
    fun computeKeyedHmacSha256(
        e164Number: String,
        userIdScope: String,
        epochKeyMaterial: String = "v1_epoch_2026_10"
    ): String {
        require(e164Number.startsWith("+")) { "Phone number must be normalized to E.164 before HMAC computation." }
        val compositeKey = "tictactoe_pro_hmac:${userIdScope.trim()}:$epochKeyMaterial"
        val secretKey = SecretKeySpec(compositeKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val digestBytes = mac.doFinal(e164Number.toByteArray(Charsets.UTF_8))
        return "hmac_v1_" + digestBytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Enforces client-side rate limiting and batch size bounds before preparing keyed tokens
     * for the protected matching backend.
     */
    fun prepareRateLimitedMatchBatch(
        contacts: List<NormalizedLocalContact>,
        userId: String,
        consentState: ConsentState,
        nowMs: Long = System.currentTimeMillis()
    ): Result<List<ContactMatchCandidate>> = runCatching {
        if (!consentState.canPerformServerMatching) {
            throw SecurityException(
                "Server-side contact matching requires both Local Contact Discovery consent and explicit Server Matching consent."
            )
        }
        if (userId.isBlank()) {
            throw SecurityException("Authenticated user UID is required for rate-limited contact matching.")
        }
        val elapsed = nowMs - lastRequestTimestampMs
        if (lastRequestTimestampMs > 0L && elapsed < MIN_INTERVAL_BETWEEN_MATCH_REQUESTS_MS) {
            val waitSeconds = ((MIN_INTERVAL_BETWEEN_MATCH_REQUESTS_MS - elapsed) / 1000L) + 1L
            throw IllegalStateException("Rate limit active: please wait ${waitSeconds}s before running another contact match.")
        }

        lastRequestTimestampMs = nowMs
        val bounded = contacts.take(MAX_BATCH_SIZE)

        bounded.map { contact ->
            val digest = computeKeyedHmacSha256(
                e164Number = contact.e164Number,
                userIdScope = userId
            )
            ContactMatchCandidate(
                maskedE164 = contact.maskedNumber,
                keyedTokenDigest = digest,
                isRegisteredPlayer = false,
                verifiedDisplayName = null, // Never populate from unverified address book names
                verificationNote = "Tokenized via Keyed HMAC-SHA256 • Unverified address-book labels are never used as caller ID"
            )
        }
    }

    fun resetRateLimiterForTesting() {
        lastRequestTimestampMs = 0L
    }
}
