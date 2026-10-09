package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.firebase.DiscoveryBatchModel
import com.example.domain.contacts.AndroidContactPermissionModule
import com.example.domain.contacts.ContactDiscoveryUiState
import com.example.domain.contacts.ContactPermissionStatus
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlueX
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonPurpleO
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextSecondary

/**
 * Screen 10: Contact Permission & Privacy-Preserving Contact Discovery Foundation Screen.
 */
@Composable
fun ContactDiscoveryScreen(
    uiState: ContactDiscoveryUiState,
    authenticatedUserId: String?,
    submittedBatches: List<DiscoveryBatchModel>,
    onRefreshPermission: () -> Unit,
    onRequestEnableDiscovery: () -> Unit,
    onDismissPrePermissionDialog: () -> Unit,
    onConfirmPrePermissionAndLaunchOsDialog: (() -> Unit) -> Unit,
    onOsPermissionResult: (Boolean) -> Unit,
    onPromptServerMatchingConsent: () -> Unit,
    onDismissServerMatchingDialog: () -> Unit,
    onSetServerMatchingConsent: (Boolean) -> Unit,
    onRunContactDiscovery: () -> Unit,
    onWithdrawConsentAndDelete: () -> Unit,
    onOpenPrivacyPolicyScreen: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Real Android OS READ_CONTACTS runtime permission launcher
    val osPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onOsPermissionResult(isGranted)
    }

    // Re-check OS permission state when returning from Android App Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 1. Pre-Permission Explanation Dialog (Shown BEFORE triggering OS runtime dialog)
    if (uiState.showPrePermissionDisclosureDialog) {
        AlertDialog(
            onDismissRequest = onDismissPrePermissionDialog,
            title = {
                Text(
                    text = "Why Contact Access is Requested",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "1. Purpose: Optional Contact Discovery helps identify friends who have verified their own phone number in Tic-Tac-Toe Pro & future Caller ID features.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "2. What is Accessed: Only display names and phone numbers from your address book when you explicitly tap 'Scan & Normalize'. No SMS, Call Logs, or Accessibility data is ever requested.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "3. Does Data Leave the Device?: By default, phone numbers are normalized to E.164 strictly on-device. Nothing leaves your device unless you grant a separate Stage-2 Server Matching consent.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "4. Decline or Revoke Anytime: If you decline, all Tic-Tac-Toe game modes remain 100% playable. You can revoke access or delete data anytime.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmPrePermissionAndLaunchOsDialog {
                            osPermissionLauncher.launch(AndroidContactPermissionModule.READ_CONTACTS_PERMISSION)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                    modifier = Modifier.testTag("confirm_pre_permission_button")
                ) {
                    Text("Continue to Android Permission", color = NavyBackground)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissPrePermissionDialog,
                    modifier = Modifier.testTag("decline_pre_permission_button")
                ) {
                    Text("Not Now")
                }
            }
        )
    }

    // 2. Separate Stage-2 Informed Consent Dialog for Server-Side Keyed HMAC Matching
    if (uiState.showServerConsentDialog) {
        AlertDialog(
            onDismissRequest = onDismissServerMatchingDialog,
            title = {
                Text(
                    text = "Separate Consent: Server-Side Matching",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Plain unsalted SHA-256 hashes are NEVER used because phone numbers have a small, guessable search space (~10^10).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Instead, normalized E.164 numbers are tokenized using Keyed HMAC-SHA256 with rate limiting (max 50 numbers/batch) and a 30-day retention TTL.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Anti-Spoofing Guarantee: Someone having a number in their address book never proves ownership, and unverified contact labels are never shown as authoritative caller identity.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSetServerMatchingConsent(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleO),
                    modifier = Modifier.testTag("grant_server_consent_button")
                ) {
                    Text("I Consent to Keyed Matching", color = NavyBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissServerMatchingDialog) {
                    Text("Keep Local Only")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricBlueX.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Contacts,
                        contentDescription = "Contact Discovery Foundation",
                        tint = ElectricBlueX,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Optional Contact Discovery Foundation",
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                Text(
                    text = "Designed for future Caller ID & friend matching with strict zero-trust privacy. The game works 100% if you decline contact access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Status / Error Feedback
        if (uiState.statusMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldWin.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_status_banner")
            ) {
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldWin,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (uiState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_error_banner")
            ) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoseError,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Step 1: Android OS READ_CONTACTS Permission Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Android READ_CONTACTS Permission",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val badgeColor = when (uiState.permissionStatus) {
                        ContactPermissionStatus.GRANTED -> EmeraldWin
                        ContactPermissionStatus.NOT_REQUESTED_YET -> ElectricBlueX
                        ContactPermissionStatus.DENIED_CAN_RETRY -> AmberDraw
                        ContactPermissionStatus.PERMANENTLY_DENIED_OR_REVOKED -> RoseError
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = uiState.permissionStatus.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = "Uses Android's real runtime permission dialog (`android.permission.READ_CONTACTS`). Never requests SMS, Call Logs, or Accessibility permissions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.permissionStatus != ContactPermissionStatus.GRANTED) {
                        Button(
                            onClick = onRequestEnableDiscovery,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("enable_contact_discovery_button")
                        ) {
                            Text(
                                text = "Enable Contact Discovery",
                                color = NavyBackground,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { AndroidContactPermissionModule.openAndroidAppSettings(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_app_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Android App Settings",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Android Settings", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Step 2: Separate Server-Side Matching Consent Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "2. Server-Side Keyed HMAC Matching Consent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Separate informed opt-in required before any tokenized digest leaves your device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = uiState.consentState.serverMatchingConsentGranted,
                        onCheckedChange = { checked ->
                            if (checked) {
                                onPromptServerMatchingConsent()
                            } else {
                                onSetServerMatchingConsent(false)
                            }
                        },
                        modifier = Modifier.testTag("server_matching_consent_switch")
                    )
                }
            }
        }

        // Step 3: On-Demand Contact Normalizer & Keyed HMAC Tokenizer
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3. On-Demand E.164 Normalizer & HMAC Tokenizer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Contacts are NEVER read in the background. Tap below to run an on-demand scan after granting permission and consent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Button(
                    onClick = onRunContactDiscovery,
                    enabled = !uiState.isProcessing,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleO),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_contact_discovery_button")
                ) {
                    if (uiState.isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = NavyBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Scan and Normalize Contacts",
                            tint = NavyBackground
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan & Normalize Contacts Now",
                            color = NavyBackground,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                if (uiState.matchCandidates.isNotEmpty()) {
                    Text(
                        text = "Processed Contacts (${uiState.matchCandidates.size} E.164 records):",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricBlueX
                    )
                    uiState.matchCandidates.take(8).forEach { candidate ->
                        Surface(
                            color = NavySurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "E.164 Masked: ${candidate.maskedE164}",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Unverified Label Isolated",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldWin
                                    )
                                }
                                Text(
                                    text = "Digest: ${candidate.keyedTokenDigest}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonPurpleO
                                )
                                Text(
                                    text = candidate.verificationNote,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step 4: Consent Withdrawal & Data Deletion
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RoseError.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "4. Withdraw Consent & Delete Discovery Data",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoseError
                )
                Text(
                    text = "Submitted Cloud Batches (${submittedBatches.size} active, 30-day auto-expiry). Withdraw consent at any time to immediately disable discovery and erase all submitted token batches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onWithdrawConsentAndDelete,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("withdraw_consent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Withdraw Consent and Delete",
                            tint = RoseError
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Withdraw & Erase", color = RoseError)
                    }

                    OutlinedButton(
                        onClick = onOpenPrivacyPolicyScreen,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_privacy_policy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Policy,
                            contentDescription = "Privacy Architecture",
                            tint = ElectricBlueX
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Privacy Policy")
                    }
                }
            }
        }
    }
}

/**
 * Screen 12: Privacy Policy, Cryptographic Matching Architecture & Account/Data Deletion.
 */
@Composable
fun PrivacyPolicyAndDeletionScreen(
    isSignedIn: Boolean,
    userEmail: String?,
    onDeleteContactDiscoveryData: () -> Unit,
    onClearLocalScores: () -> Unit,
    onDeleteAllCloudAccountData: ((String) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var deletionFeedback by remember { mutableStateOf<String?>(null) }
    var showConfirmCloudDeleteDialog by remember { mutableStateOf(false) }

    if (showConfirmCloudDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCloudDeleteDialog = false },
            title = { Text("Delete All Cloud Account Data?") },
            text = {
                Text(
                    "This permanently deletes your Firestore User Profile (/users/{uid}), Consent Record (/consents/{uid}), Synced Game History (/games), and all Contact Discovery Batches (/users/{uid}/discoveryBatches)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCloudDeleteDialog = false
                        onDeleteAllCloudAccountData { msg -> deletionFeedback = msg }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                    modifier = Modifier.testTag("confirm_cloud_delete_button")
                ) {
                    Text("Permanently Delete", color = NavyBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCloudDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Privacy Policy & Data Deletion",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricBlueX
        )

        if (deletionFeedback != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldWin.copy(alpha = 0.16f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("deletion_feedback_banner")
            ) {
                Text(
                    text = deletionFeedback!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldWin,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        PolicySectionCard(
            title = "1. Why Unsalted SHA-256 is Prohibited for Phone Numbers",
            body = "Global E.164 phone numbers span roughly 10^10 combinations. An attacker with a modern GPU can compute unsalted SHA-256 hashes for every valid phone number in minutes and reverse hashes using a rainbow table. Therefore, Tic-Tac-Toe Pro never uses plain SHA-256 hashes of phone numbers."
        )

        PolicySectionCard(
            title = "2. Server-Keyed HMAC-SHA256 & Rate-Limited Matching",
            body = "Contact matching uses Keyed HMAC-SHA256 where the secret Pepper resides exclusively in server-side secret management (Google Cloud Secret Manager via Cloud Functions), never in the client APK. Requests are strictly rate-limited (max 50 numbers per batch, cooldown enforced) to prevent bulk enumeration."
        )

        PolicySectionCard(
            title = "3. Caller Identity Anti-Spoofing Principle",
            body = "Possession of a phone number in someone's address book is NEVER treated as proof that the number belongs to that person. Unverified address-book names are never uploaded or displayed as authoritative caller identity. Account phone numbers are stored separately and require Firebase Auth Phone Verification."
        )

        PolicySectionCard(
            title = "4. Retention Limits & Immediate Erasure Controls",
            body = "Submitted discovery token batches carry a strict 30-day retention expiration (`expiresAt`) and can be deleted immediately using the controls below."
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, RoseError.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Account & Data Deletion Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RoseError
                )

                Button(
                    onClick = {
                        onDeleteContactDiscoveryData()
                        deletionFeedback = "Contact Discovery consent withdrawn and all discovery batches deleted."
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_discovery_data_button")
                ) {
                    Text("Delete Contact Discovery Data & Withdraw Consent", color = RoseError)
                }

                Button(
                    onClick = {
                        onClearLocalScores()
                        deletionFeedback = "All local offline scoreboard records and match history cleared."
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NavySurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_local_scores_button")
                ) {
                    Text("Clear All Local Offline Scores & History", color = AmberDraw)
                }

                if (isSignedIn) {
                    Button(
                        onClick = { showConfirmCloudDeleteDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_all_cloud_data_button")
                    ) {
                        Text(
                            text = "Delete All Cloud Account Data (${userEmail ?: "Signed In"})",
                            color = NavyBackground,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicySectionCard(
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = NeonPurpleO
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
