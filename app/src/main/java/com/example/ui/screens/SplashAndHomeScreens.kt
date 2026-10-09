package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalScoreEntity
import com.example.domain.contacts.ContactPermissionStatus
import com.example.ui.theme.CyberAccentIndigo
import com.example.ui.theme.ElectricBlueX
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonPurpleO
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppScreen
import kotlinx.coroutines.delay

/**
 * Screen 1: Splash Screen.
 */
@Composable
fun SplashScreen(
    onContinueToHome: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(1400L)
        onContinueToHome()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NavyBackground, NavySurface, NavyBackground)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(500))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.horizontalGradient(listOf(ElectricBlueX, NeonPurpleO)),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_arena_1791553991607),
                        contentDescription = "Tic-Tac-Toe Pro Cyber Arena Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "X",
                        style = MaterialTheme.typography.displayLarge,
                        color = ElectricBlueX
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "O",
                        style = MaterialTheme.typography.displayLarge,
                        color = NeonPurpleO
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "TIC-TAC-TOE PRO",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Local 2P • Unbeatable Minimax AI • Real-Time Firebase Rooms • Privacy-Preserving Contact Discovery Foundation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onContinueToHome,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(52.dp)
                        .testTag("splash_enter_button")
                ) {
                    Text(
                        text = "Enter Arena",
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyBackground
                    )
                }
            }
        }
    }
}

/**
 * Screen 2: Home Screen.
 */
@Composable
fun HomeScreen(
    isSignedIn: Boolean,
    userDisplayName: String?,
    localScores: List<LocalScoreEntity>,
    contactPermissionStatus: ContactPermissionStatus,
    onNavigate: (AppScreen) -> Unit,
    onQuickStartLocalGame: () -> Unit
) {
    val totalMatchesPlayed = localScores.sumOf { it.totalGames }
    val totalXWins = localScores.sumOf { it.xWins }
    val totalOWins = localScores.sumOf { it.oWins }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Banner Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(ElectricBlueX, NeonPurpleO)),
                    shape = RoundedCornerShape(22.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_hero_arena_1791553991607),
                    contentDescription = "Cyber-Grid Arena Hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    NavyBackground.copy(alpha = 0.25f),
                                    NavyBackground.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = ElectricBlueX.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "PRO EDITION",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricBlueX,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = if (isSignedIn) EmeraldWin.copy(alpha = 0.2f) else NeonPurpleO.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isSignedIn) "ONLINE: ${userDisplayName ?: "SIGNED IN"}" else "OFFLINE READY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSignedIn) EmeraldWin else NeonPurpleO,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tic-Tac-Toe Pro Arena",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$totalMatchesPlayed local rounds played • X: $totalXWins | O: $totalOWins",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Primary Game Modes Section
        Text(
            text = "GAME MODES",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricBlueX,
            modifier = Modifier.padding(top = 4.dp)
        )

        ModeActionCard(
            title = "Local Pass & Play",
            subtitle = "Two players on one device • Alternating X/O turns • Works 100% offline",
            badgeText = "OFFLINE 2P",
            accentColor = ElectricBlueX,
            icon = Icons.Default.Group,
            testTag = "home_local_multiplayer_card",
            onClick = { onNavigate(AppScreen.LOCAL_MULTIPLAYER) }
        )

        ModeActionCard(
            title = "Play Against Robot AI",
            subtitle = "Choose Easy (Random), Medium (Tactical Blocker), or Hard (Unbeatable Minimax)",
            badgeText = "3 AI TIERS",
            accentColor = NeonPurpleO,
            icon = Icons.Default.SmartToy,
            testTag = "home_robot_mode_card",
            onClick = { onNavigate(AppScreen.PLAY_AGAINST_ROBOT) }
        )

        ModeActionCard(
            title = "Online Multiplayer Rooms",
            subtitle = "Create or join 6-digit rooms • Real-time Firestore sync & turn validation",
            badgeText = if (isSignedIn) "LIVE SYNC" else "GOOGLE SIGN-IN",
            accentColor = CyberAccentIndigo,
            icon = Icons.Default.Cloud,
            testTag = "home_online_multiplayer_card",
            onClick = { onNavigate(AppScreen.ONLINE_MULTIPLAYER) }
        )

        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onQuickStartLocalGame,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("quick_start_local_button")
            ) {
                Text(
                    text = "Quick 2P Match",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavyBackground
                )
            }
            OutlinedButton(
                onClick = { onNavigate(AppScreen.DIFFICULTY_SELECTION) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("quick_difficulty_button")
            ) {
                Text(
                    text = "Select AI Level",
                    style = MaterialTheme.typography.labelLarge,
                    color = NeonPurpleO
                )
            }
        }

        // Foundation & Utility Section
        Text(
            text = "RECORDS, PRIVACY & CONTACT DISCOVERY FOUNDATION",
            style = MaterialTheme.typography.labelLarge,
            color = NeonPurpleO,
            modifier = Modifier.padding(top = 6.dp)
        )

        ModeActionCard(
            title = "Scoreboard & Match History",
            subtitle = "Track wins, losses, draws across all modes + cloud synced game logs",
            badgeText = "$totalMatchesPlayed GAMES",
            accentColor = EmeraldWin,
            icon = Icons.Default.EmojiEvents,
            testTag = "home_scoreboard_card",
            onClick = { onNavigate(AppScreen.SCOREBOARD) }
        )

        ModeActionCard(
            title = "Contact Discovery & Permissions",
            subtitle = "Optional consent-based Android READ_CONTACTS flow, E.164 normalizer & Keyed HMAC matching",
            badgeText = if (contactPermissionStatus == ContactPermissionStatus.GRANTED) "GRANTED" else "OPTIONAL",
            accentColor = ElectricBlueX,
            icon = Icons.Default.Contacts,
            testTag = "home_contact_discovery_card",
            onClick = { onNavigate(AppScreen.CONTACT_PERMISSION_PRIVACY) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(AppScreen.SETTINGS) }
                    .testTag("home_settings_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings and Firebase Setup",
                        tint = ElectricBlueX
                    )
                    Column {
                        Text(
                            text = "Settings & Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Firebase & APK Info",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate(AppScreen.PRIVACY_POLICY_DELETION) }
                    .testTag("home_privacy_policy_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Policy,
                        contentDescription = "Privacy Policy and Data Deletion",
                        tint = NeonPurpleO
                    )
                    Column {
                        Text(
                            text = "Privacy & Erasure",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Consent & Deletion",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ModeActionCard(
    title: String,
    subtitle: String,
    badgeText: String,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open $title",
                tint = accentColor
            )
        }
    }
}
