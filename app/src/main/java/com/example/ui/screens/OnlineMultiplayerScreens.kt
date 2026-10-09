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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.OnlineRoomModel
import com.example.domain.game.PlayerSymbol
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyberAccentIndigo
import com.example.ui.theme.ElectricBlueX
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonPurpleO
import com.example.ui.theme.RoseError
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.OnlineMultiplayerViewModel
import com.google.firebase.auth.FirebaseUser

/**
 * Reusable Google Sign-In Gate Card using Jetpack Credential Manager (`GetSignInWithGoogleOption`).
 */
@Composable
fun GoogleSignInGateCard(
    title: String = "Sign in with Google for Online Multiplayer",
    subtitle: String = "Firebase Authentication with Google Sign-In protects online rooms, enforces turn ownership in Firestore security rules, and prevents unauthorized database access.",
    onAuthSuccess: (FirebaseUser) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricBlueX.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Google Sign-In Required",
                tint = ElectricBlueX,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (errorMessage != null) {
                Surface(
                    color = RoseError.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RoseError,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    GoogleAuthManager.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = { user ->
                            isLoading = false
                            onAuthSuccess(user)
                        },
                        onAuthError = { err ->
                            isLoading = false
                            errorMessage = err
                        },
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NavyBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Login,
                        contentDescription = "Sign in with Google",
                        tint = NavyBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyBackground
                    )
                }
            }
        }
    }
}

/**
 * Screens 4 & 11: Online Multiplayer Lobby, Room Creation/Joining, and Live Synchronized Match Board.
 */
@Composable
fun OnlineMultiplayerScreen(
    onlineViewModel: OnlineMultiplayerViewModel,
    userEmail: String?,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val lobbyState by onlineViewModel.lobbyState.collectAsState()
    val activeRoom = lobbyState.activeRoom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Signed-in User Header
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurfaceElevated),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = "Authenticated",
                        tint = EmeraldWin
                    )
                    Column {
                        Text(
                            text = lobbyState.playerDisplayName.ifBlank { "Online Player" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = userEmail ?: "UID: ${onlineViewModel.currentUserId.take(8)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("online_sign_out_button")
                ) {
                    Text("Sign Out", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Error or Info Notifications
        if (lobbyState.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.16f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoseError, RoundedCornerShape(12.dp))
                    .testTag("online_error_banner")
            ) {
                Text(
                    text = lobbyState.errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoseError,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (lobbyState.infoMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElectricBlueX.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = lobbyState.infoMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ElectricBlueX,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (activeRoom == null) {
            // Room Creation & Joining Lobby
            OnlineRoomCreationAndJoiningCard(
                displayName = lobbyState.playerDisplayName,
                roomCodeInput = lobbyState.roomCodeInput,
                isBusy = lobbyState.isBusy,
                onDisplayNameChange = onlineViewModel::updateDisplayNameInput,
                onRoomCodeChange = onlineViewModel::updateRoomCodeInput,
                onCreateRoom = { onlineViewModel.createNewOnlineRoom {} },
                onJoinRoom = { onlineViewModel.joinExistingOnlineRoom {} }
            )
        } else {
            // Active Online Multiplayer Room Arena
            ActiveOnlineRoomArena(
                room = activeRoom,
                currentUserId = onlineViewModel.currentUserId,
                onCellTap = onlineViewModel::makeOnlineMove,
                onRequestRematch = onlineViewModel::requestOnlineRematch,
                onTogglePresence = onlineViewModel::toggleSimulateConnectionState,
                onLeaveRoom = onlineViewModel::leaveOnlineRoom
            )
        }
    }
}

@Composable
private fun OnlineRoomCreationAndJoiningCard(
    displayName: String,
    roomCodeInput: String,
    isBusy: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onRoomCodeChange: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberAccentIndigo.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Create or Join an Online Room",
                style = MaterialTheme.typography.headlineMedium,
                color = ElectricBlueX
            )
            Text(
                text = "Host plays as X (Electric Blue) and Guest plays as O (Neon Purple). Moves, turns, and room membership are strictly validated by Firestore Security Rules.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Your Arena Display Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_display_name_input")
            )

            Button(
                onClick = onCreateRoom,
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_room_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NavyBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "Create Room",
                        tint = NavyBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Create New Room (Play as X)",
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "HAVE A ROOM CODE?",
                style = MaterialTheme.typography.labelLarge,
                color = NeonPurpleO
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = onRoomCodeChange,
                    label = { Text("6-Digit Room Code") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("room_code_input")
                )

                Button(
                    onClick = onJoinRoom,
                    enabled = !isBusy && roomCodeInput.length >= 4,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleO),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("join_room_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = "Join Room",
                        tint = NavyBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Join (O)",
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveOnlineRoomArena(
    room: OnlineRoomModel,
    currentUserId: String,
    onCellTap: (Int) -> Unit,
    onRequestRematch: () -> Unit,
    onTogglePresence: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val isHost = currentUserId == room.hostUid
    val mySymbol = if (isHost) "X" else "O"
    val isMyTurn = room.status == "playing" && room.currentTurn == mySymbol

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Room Code & Connection Status Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ROOM CODE: ",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                        Text(
                            text = room.roomId,
                            style = MaterialTheme.typography.headlineMedium,
                            color = ElectricBlueX,
                            modifier = Modifier.testTag("active_room_code_text")
                        )
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(room.roomId)) },
                            modifier = Modifier.testTag("copy_room_code_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Room Code",
                                tint = ElectricBlueX
                            )
                        }
                    }

                    Surface(
                        color = NeonPurpleO.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "YOU ARE $mySymbol",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mySymbol == "X") ElectricBlueX else NeonPurpleO,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Players Connection Presence Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (room.hostConnected) Icons.Default.Wifi else Icons.Default.CloudOff,
                            contentDescription = "Host Connection",
                            tint = if (room.hostConnected) EmeraldWin else AmberDraw,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "X: ${room.hostName} ${if (room.hostConnected) "(Online)" else "(Reconnecting...)"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricBlueX
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val guestConnected = room.guestUid.isNotEmpty() && room.guestConnected
                        Icon(
                            imageVector = if (guestConnected) Icons.Default.Wifi else Icons.Default.CloudOff,
                            contentDescription = "Guest Connection",
                            tint = if (guestConnected) EmeraldWin else AmberDraw,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (room.guestUid.isEmpty()) "O: Waiting for Player..." else "O: ${room.guestName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonPurpleO
                        )
                    }
                }
            }
        }

        // Scoreboard Strip
        ScoreboardStrip(
            playerXLabel = room.hostName,
            xWins = room.hostScore,
            draws = room.draws,
            playerOLabel = room.guestName.ifBlank { "Waiting..." },
            oWins = room.guestScore,
            activeTurn = if (room.status == "playing") {
                if (room.currentTurn == "X") PlayerSymbol.X else PlayerSymbol.O
            } else null
        )

        // Status Banner
        val statusText = when (room.status) {
            "waiting" -> "Waiting for second player to join with code ${room.roomId}..."
            "playing" -> if (isMyTurn) {
                "Your Turn ($mySymbol) — Tap any empty cell!"
            } else {
                "Waiting for Opponent (${room.currentTurn}) to make a move..."
            }
            "finished" -> when (room.winner) {
                "X" -> "${room.hostName} (X) Won Round ${room.roundNumber}!"
                "O" -> "${room.guestName} (O) Won Round ${room.roundNumber}!"
                else -> "Round ${room.roundNumber} Ended in a Draw!"
            }
            "abandoned" -> "Match ended because a player left the room."
            else -> room.status
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurfaceElevated),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_room_status_banner")
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isMyTurn) EmeraldWin else ElectricBlueX,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            )
        }

        // Synchronized 3x3 Board
        CyberGridBoard(
            cells = room.board,
            winningCells = room.winningCells,
            isInteractive = isMyTurn,
            onCellTap = onCellTap
        )

        // Rematch, Presence Simulation & Leave Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (room.status == "finished") {
                Button(
                    onClick = onRequestRematch,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldWin),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("online_rematch_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rematch",
                        tint = NavyBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rematch", color = NavyBackground, style = MaterialTheme.typography.labelLarge)
                }
            }

            OutlinedButton(
                onClick = onTogglePresence,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("toggle_presence_button")
            ) {
                Text(
                    text = "Toggle Presence",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onLeaveRoom,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("leave_online_room_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Leave Room",
                    tint = RoseError
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Leave", color = RoseError, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
