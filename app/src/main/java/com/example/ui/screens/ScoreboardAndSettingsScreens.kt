package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.firebase.FirebaseConfigStatus
import com.example.data.firebase.SyncedGameRecordModel
import com.example.data.local.LocalMatchHistoryEntity
import com.example.data.local.LocalScoreEntity
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlueX
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonPurpleO
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.UiState

/**
 * Screen 8: Scoreboard & Match History Screen.
 */
@Composable
fun ScoreboardScreen(
    localScores: List<LocalScoreEntity>,
    recentLocalMatches: List<LocalMatchHistoryEntity>,
    cloudGamesState: UiState<List<SyncedGameRecordModel>>?,
    onClearLocalScores: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scoreboard & Records",
                style = MaterialTheme.typography.headlineMedium,
                color = ElectricBlueX
            )
            if (localScores.isNotEmpty() || recentLocalMatches.isNotEmpty()) {
                OutlinedButton(
                    onClick = onClearLocalScores,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("clear_scoreboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Local Scores"
                    )
                    Text("Reset Local", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        // Local Scoreboard by Mode
        Text(
            text = "OFFLINE & LOCAL SCOREBOARD BY MODE",
            style = MaterialTheme.typography.labelLarge,
            color = NeonPurpleO
        )

        if (localScores.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "No scores yet",
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No completed rounds recorded yet. Play Local 2P or vs. Robot AI to populate the scoreboard!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            localScores.forEach { score ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = score.modeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${score.totalGames} Rounds",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricBlueX
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("X Wins: ${score.xWins}", color = ElectricBlueX)
                            Text("Draws: ${score.draws}", color = AmberDraw)
                            Text("O Wins: ${score.oWins}", color = NeonPurpleO)
                        }
                    }
                }
            }
        }

        // Cloud Synced Matches (when signed in)
        Text(
            text = "CLOUD SYNCED MATCHES (FIRESTORE /games)",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricBlueX
        )

        when (cloudGamesState) {
            null -> {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sign in with Google on the Online tab to automatically sync and view your cloud match history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is UiState.Loading -> {
                Text("Loading cloud games...", color = TextSecondary)
            }
            is UiState.Error -> {
                Text("Cloud sync notice: ${cloudGamesState.message}", color = RoseError)
            }
            is UiState.Success -> {
                if (cloudGamesState.data.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No cloud games logged yet for this account.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    cloudGamesState.data.take(10).forEach { game ->
                        Surface(
                            color = NavySurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${game.mode} • ${game.result}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ID: ${game.gameId.takeLast(6)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 9: Settings, Firebase Configuration Diagnostics & Setup Guide Screen.
 */
@Composable
fun SettingsScreen(
    soundEnabled: Boolean,
    hapticsEnabled: Boolean,
    animationsEnabled: Boolean,
    firebaseConfigStatus: FirebaseConfigStatus,
    onToggleSound: (Boolean) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onToggleAnimations: (Boolean) -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Settings & Firebase Configuration",
            style = MaterialTheme.typography.headlineMedium,
            color = ElectricBlueX
        )

        // Game Experience Toggles
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Gameplay Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                SettingToggleRow(
                    title = "Board & Win Line Animations",
                    subtitle = "Smooth laser win-line and symbol transitions",
                    checked = animationsEnabled,
                    testTag = "toggle_animations_switch",
                    onCheckedChange = onToggleAnimations
                )
                SettingToggleRow(
                    title = "Sound Effects",
                    subtitle = "Audio feedback on moves and round completion",
                    checked = soundEnabled,
                    testTag = "toggle_sound_switch",
                    onCheckedChange = onToggleSound
                )
                SettingToggleRow(
                    title = "Tactile Haptics",
                    subtitle = "Subtle vibration feedback on cell taps",
                    checked = hapticsEnabled,
                    testTag = "toggle_haptics_switch",
                    onCheckedChange = onToggleHaptics
                )
            }
        }

        // Firebase Configuration Validator & Diagnostics Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (firebaseConfigStatus.isConfigured) EmeraldWin.copy(alpha = 0.5f) else RoseError,
                    shape = RoundedCornerShape(18.dp)
                )
                .testTag("firebase_config_status_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (firebaseConfigStatus.isConfigured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = "Firebase Status",
                        tint = if (firebaseConfigStatus.isConfigured) EmeraldWin else RoseError
                    )
                    Text(
                        text = if (firebaseConfigStatus.isConfigured) {
                            "Firebase Initialized & Validated (Single Instance)"
                        } else {
                            "Firebase Configuration Warning"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (firebaseConfigStatus.isConfigured) EmeraldWin else RoseError
                    )
                }

                Text(
                    text = "projectId: ${firebaseConfigStatus.projectId}\n" +
                        "applicationId: ${firebaseConfigStatus.applicationId}\n" +
                        "authDomain: ${firebaseConfigStatus.authDomain}\n" +
                        "storageBucket: ${firebaseConfigStatus.storageBucket}\n" +
                        "messagingSenderId: ${firebaseConfigStatus.messagingSenderId}\n" +
                        "apiKey (masked): ${firebaseConfigStatus.apiKeyMasked}\n" +
                        "firestoreDatabaseId: ${firebaseConfigStatus.databaseId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )

                if (firebaseConfigStatus.validationErrors.isNotEmpty()) {
                    firebaseConfigStatus.validationErrors.forEach { err ->
                        Text(
                            text = "• $err",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RoseError
                        )
                    }
                }
            }
        }

        // Quick Links to Contact Permission & Privacy Policy
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onNavigate(AppScreen.CONTACT_PERMISSION_PRIVACY) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_to_contacts_button")
            ) {
                Text("Contact Permissions")
            }
            OutlinedButton(
                onClick = { onNavigate(AppScreen.PRIVACY_POLICY_DELETION) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_to_privacy_button")
            ) {
                Text("Privacy & Deletion")
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
