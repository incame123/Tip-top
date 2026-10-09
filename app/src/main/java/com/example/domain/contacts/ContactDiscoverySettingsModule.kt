package com.example.domain.contacts

import android.content.Context
import com.example.data.firebase.DiscoveryBatchModel
import com.example.data.firebase.OnlineGameRepository
import kotlinx.coroutines.flow.Flow

data class ContactDiscoveryUiState(
    val permissionStatus: ContactPermissionStatus = ContactPermissionStatus.NOT_REQUESTED_YET,
    val consentState: ConsentState = ConsentState(),
    val normalizedLocalContactsCount: Int = 0,
    val matchCandidates: List<ContactMatchCandidate> = emptyList(),
    val submittedBatches: List<DiscoveryBatchModel> = emptyList(),
    val isProcessing: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val showPrePermissionDisclosureDialog: Boolean = false,
    val showServerConsentDialog: Boolean = false
)

/**
 * Module 6: Contact Discovery Settings & Orchestration Module.
 *
 * Coordinates:
 * - Pre-permission transparency disclosure
 * - Android READ_CONTACTS runtime permission status
 * - Two-stage informed consent (local reading vs. server-side keyed matching)
 * - On-demand contact normalization & rate-limited keyed HMAC batch submission
 * - Full consent withdrawal and server data deletion
 */
class ContactDiscoverySettingsModule(
    private val context: Context,
    private val consentModule: ConsentManagementModule,
    private val onlineRepository: OnlineGameRepository?
) {
    val consentFlow: Flow<ConsentState> = consentModule.consentStateFlow

    fun currentPermissionStatus(hasRequestedBefore: Boolean): ContactPermissionStatus {
        return AndroidContactPermissionModule.evaluatePermissionStatus(
            context = context,
            hasRequestedBefore = hasRequestedBefore
        )
    }

    suspend fun enableLocalDiscoveryAndRecordConsent(): Result<Unit> = runCatching {
        consentModule.grantLocalDiscoveryConsent()
        onlineRepository?.saveConsentRecord(
            localContactsGranted = true,
            serverMatchingGranted = false,
            policyVersion = ConsentManagementModule.CURRENT_POLICY_VERSION
        )
        onlineRepository?.updatePrivacySettingsInProfile(
            contactDiscoveryEnabled = true,
            serverMatchingConsent = false
        )
    }

    suspend fun updateServerMatchingOptIn(granted: Boolean): Result<Unit> = runCatching {
        consentModule.setServerMatchingConsent(granted)
        onlineRepository?.saveConsentRecord(
            localContactsGranted = true,
            serverMatchingGranted = granted,
            policyVersion = ConsentManagementModule.CURRENT_POLICY_VERSION
        )
        onlineRepository?.updatePrivacySettingsInProfile(
            contactDiscoveryEnabled = true,
            serverMatchingConsent = granted
        )
    }

    suspend fun runPrivacyPreservingDiscovery(
        userId: String,
        consentState: ConsentState
    ): Result<List<ContactMatchCandidate>> = runCatching {
        val localContacts = LocalContactReaderModule.readAndNormalizeContacts(
            context = context,
            localConsentGranted = consentState.canReadLocalContacts
        ).getOrThrow()

        if (!consentState.canPerformServerMatching) {
            // Return local-only preview with masked numbers and zero server transmission
            return@runCatching localContacts.map { contact ->
                ContactMatchCandidate(
                    maskedE164 = contact.maskedNumber,
                    keyedTokenDigest = "LOCAL_ONLY (Server Matching Consent Not Granted)",
                    isRegisteredPlayer = false,
                    verifiedDisplayName = null,
                    verificationNote = "Normalized on-device only • No data left the device"
                )
            }
        }

        val candidates = SecureContactMatchingModule.prepareRateLimitedMatchBatch(
            contacts = localContacts,
            userId = userId,
            consentState = consentState
        ).getOrThrow()

        if (candidates.isNotEmpty() && onlineRepository != null) {
            onlineRepository.submitDiscoveryBatch(
                tokenDigests = candidates.map { it.keyedTokenDigest },
                policyVersion = consentState.policyVersion,
                retentionDays = ConsentManagementModule.RETENTION_LIMIT_DAYS
            ).getOrThrow()
        }

        candidates
    }

    suspend fun withdrawConsentAndDeleteSubmittedData(): Result<Int> = runCatching {
        consentModule.withdrawAllConsent()
        val deletedBatches = onlineRepository?.deleteContactDiscoveryData()?.getOrDefault(0) ?: 0
        deletedBatches
    }
}
