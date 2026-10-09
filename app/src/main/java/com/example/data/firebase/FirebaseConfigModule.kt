package com.example.data.firebase

import android.content.Context
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Dedicated Firebase Configuration & Validation Module.
 *
 * Supports all standard Firebase fields (apiKey, authDomain, projectId, storageBucket,
 * messagingSenderId, appId, databaseURL, and enterprise firestoreDatabaseId) loaded from
 * the provisioned `app/google-services.json` and `firebase_applet_config.xml` or custom overrides.
 *
 * Ensures Firebase is initialized exactly once and surfaces transparent diagnostics if
 * configuration is missing or incomplete.
 */
data class FirebaseConfigStatus(
    val isConfigured: Boolean,
    val projectId: String,
    val applicationId: String,
    val apiKeyMasked: String,
    val storageBucket: String,
    val messagingSenderId: String,
    val authDomain: String,
    val databaseId: String,
    val validationErrors: List<String>
)

object FirebaseConfigModule {

    @Volatile
    private var initialized = false

    /**
     * Initializes Firebase exactly once and validates the configuration values.
     */
    fun ensureInitializedAndValidate(context: Context): FirebaseConfigStatus {
        val appContext = context.applicationContext
        val errors = mutableListOf<String>()

        val databaseId = try {
            appContext.getString(R.string.firestore_database_id)
        } catch (e: Exception) {
            errors.add("Missing R.string.firestore_database_id in firebase_applet_config.xml")
            ""
        }

        val firebaseApp: FirebaseApp? = synchronized(this) {
            try {
                val existingApps = FirebaseApp.getApps(appContext)
                if (existingApps.isNotEmpty()) {
                    initialized = true
                    FirebaseApp.getInstance()
                } else {
                    val initializedApp = FirebaseApp.initializeApp(appContext)
                    if (initializedApp != null) {
                        initialized = true
                    } else {
                        errors.add("FirebaseApp.initializeApp() returned null. Check app/google-services.json.")
                    }
                    initializedApp
                }
            } catch (e: Exception) {
                errors.add("Firebase initialization exception: ${e.localizedMessage}")
                null
            }
        }

        val options = firebaseApp?.options
        val projectId = options?.projectId.orEmpty()
        val appId = options?.applicationId.orEmpty()
        val apiKey = options?.apiKey.orEmpty()
        val storageBucket = options?.storageBucket.orEmpty()
        val senderId = options?.gcmSenderId.orEmpty()
        val authDomain = if (projectId.isNotBlank()) "$projectId.firebaseapp.com" else ""

        if (projectId.isBlank() || projectId == "remixed-project-id") {
            errors.add("Invalid or placeholder Firebase projectId ('$projectId').")
        }
        if (appId.isBlank()) {
            errors.add("Missing Firebase applicationId (mobilesdk_app_id).")
        }
        if (apiKey.isBlank()) {
            errors.add("Missing Firebase apiKey.")
        }
        if (databaseId.isBlank()) {
            errors.add("Missing Firestore named database ID.")
        }

        val maskedKey = if (apiKey.length > 8) {
            "${apiKey.take(6)}...${apiKey.takeLast(4)}"
        } else if (apiKey.isNotEmpty()) {
            "***"
        } else {
            "NOT_SET"
        }

        return FirebaseConfigStatus(
            isConfigured = errors.isEmpty(),
            projectId = projectId.ifBlank { "UNCONFIGURED" },
            applicationId = appId.ifBlank { "UNCONFIGURED" },
            apiKeyMasked = maskedKey,
            storageBucket = storageBucket.ifBlank { "N/A" },
            messagingSenderId = senderId.ifBlank { "N/A" },
            authDomain = authDomain.ifBlank { "N/A" },
            databaseId = databaseId.ifBlank { "UNCONFIGURED" },
            validationErrors = errors
        )
    }

    /**
     * Always resolves the provisioned named Firestore database instance using
     * `R.string.firestore_database_id`. Never falls back to the unprovisioned `(default)` database.
     */
    fun getFirestoreInstance(context: Context): FirebaseFirestore {
        val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
        return FirebaseFirestore.getInstance(databaseId)
    }
}
