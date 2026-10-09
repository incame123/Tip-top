package com.example.domain.contacts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consentDataStore by preferencesDataStore(name = "privacy_consent_store")

data class ConsentState(
    val contactDiscoveryEnabled: Boolean = false,
    val localReadingConsentGranted: Boolean = false,
    val serverMatchingConsentGranted: Boolean = false,
    val hasRequestedSystemPermission: Boolean = false,
    val policyVersion: String = ConsentManagementModule.CURRENT_POLICY_VERSION,
    val grantedAtEpochMs: Long = 0L,
    val revokedAtEpochMs: Long = 0L
) {
    val canReadLocalContacts: Boolean
        get() = contactDiscoveryEnabled && localReadingConsentGranted

    val canPerformServerMatching: Boolean
        get() = contactDiscoveryEnabled && localReadingConsentGranted && serverMatchingConsentGranted
}

/**
 * Module 4: Consent Management Module.
 *
 * Manages separate, informed opt-in consent states for:
 * 1. Enabling optional Contact Discovery and local contact normalization.
 * 2. Submitting privacy-preserving tokens for server-side contact matching.
 * Supports immediate consent withdrawal and retention tracking.
 */
class ConsentManagementModule(private val context: Context) {

    companion object {
        const val CURRENT_POLICY_VERSION = "1.0.0"
        const val RETENTION_LIMIT_DAYS = 30

        private val KEY_DISCOVERY_ENABLED = booleanPreferencesKey("contact_discovery_enabled")
        private val KEY_LOCAL_CONSENT = booleanPreferencesKey("local_reading_consent_granted")
        private val KEY_SERVER_CONSENT = booleanPreferencesKey("server_matching_consent_granted")
        private val KEY_HAS_REQUESTED_PERM = booleanPreferencesKey("has_requested_system_permission")
        private val KEY_POLICY_VERSION = stringPreferencesKey("policy_version")
        private val KEY_GRANTED_AT = longPreferencesKey("granted_at_epoch_ms")
        private val KEY_REVOKED_AT = longPreferencesKey("revoked_at_epoch_ms")
    }

    val consentStateFlow: Flow<ConsentState> = context.applicationContext.consentDataStore.data.map { prefs ->
        ConsentState(
            contactDiscoveryEnabled = prefs[KEY_DISCOVERY_ENABLED] ?: false,
            localReadingConsentGranted = prefs[KEY_LOCAL_CONSENT] ?: false,
            serverMatchingConsentGranted = prefs[KEY_SERVER_CONSENT] ?: false,
            hasRequestedSystemPermission = prefs[KEY_HAS_REQUESTED_PERM] ?: false,
            policyVersion = prefs[KEY_POLICY_VERSION] ?: CURRENT_POLICY_VERSION,
            grantedAtEpochMs = prefs[KEY_GRANTED_AT] ?: 0L,
            revokedAtEpochMs = prefs[KEY_REVOKED_AT] ?: 0L
        )
    }

    suspend fun grantLocalDiscoveryConsent() {
        val now = System.currentTimeMillis()
        context.applicationContext.consentDataStore.edit { prefs ->
            prefs[KEY_DISCOVERY_ENABLED] = true
            prefs[KEY_LOCAL_CONSENT] = true
            prefs[KEY_POLICY_VERSION] = CURRENT_POLICY_VERSION
            prefs[KEY_GRANTED_AT] = now
            prefs[KEY_REVOKED_AT] = 0L
        }
    }

    suspend fun setServerMatchingConsent(granted: Boolean) {
        val now = System.currentTimeMillis()
        context.applicationContext.consentDataStore.edit { prefs ->
            prefs[KEY_SERVER_CONSENT] = granted
            prefs[KEY_POLICY_VERSION] = CURRENT_POLICY_VERSION
            if (granted) {
                prefs[KEY_GRANTED_AT] = now
            }
        }
    }

    suspend fun markSystemPermissionRequested() {
        context.applicationContext.consentDataStore.edit { prefs ->
            prefs[KEY_HAS_REQUESTED_PERM] = true
        }
    }

    suspend fun withdrawAllConsent() {
        val now = System.currentTimeMillis()
        context.applicationContext.consentDataStore.edit { prefs ->
            prefs[KEY_DISCOVERY_ENABLED] = false
            prefs[KEY_LOCAL_CONSENT] = false
            prefs[KEY_SERVER_CONSENT] = false
            prefs[KEY_REVOKED_AT] = now
        }
    }
}
