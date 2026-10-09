package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.game.GameMode
import com.example.domain.game.GameResult
import com.example.domain.game.PlayerSymbol
import com.example.domain.game.RobotDifficulty
import com.example.ui.theme.AmberDraw
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlueX
import com.example.ui.theme.EmeraldWin
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.NavySurfaceElevated
import com.example.ui.theme.NeonPurpleO
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.ActiveGameSessionState

/**
 * Screen 3: Local Multiplayer Setup Screen.
 */
@Composable
fun LocalMultiplayerSetupScreen(
    onStartLocalGame: (playerXName: String, playerOName: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var playerX by remember { mutableStateOf("Player 1 (X)") }
    var playerO by remember { mutableStateOf("Player 2 (O)") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ElectricBlueX.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Local 2-Player Pass & Play",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ElectricBlueX
                )
                Text(
                    text = "Play head-to-head on a single device. Alternating X and O turns with automatic win/draw detection and persistent local scores. Works completely offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = playerX,
                    onValueChange = { playerX = it.take(30) },
                    label = { Text("Player X Name (Blue)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_player_x_input")
                )

                OutlinedTextField(
                    value = playerO,
                    onValueChange = { playerO = it.take(30) },
                    label = { Text("Player O Name (Purple)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_player_o_input")
                )

                Button(
                    onClick = { onStartLocalGame(playerX, playerO) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlueX),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_local_match_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Local Match",
                        tint = NavyBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Local Match",
                        style = MaterialTheme.typography.labelLarge,
                        color = NavyBackground
                    )
                }
            }
        }
    }
}

/**
 * Screens 5 & 6: Play Against Robot & Difficulty Selection Screen.
 */
@Composable
fun RobotAndDifficultyScreen(
    initialDifficulty: RobotDifficulty = RobotDifficulty.HARD,
    onStartRobotGame: (difficulty: RobotDifficulty, humanFirstAsX: Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedDifficulty by remember { mutableStateOf(initialDifficulty) }
    var humanPlaysFirst by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Select Robot AI Difficulty",
            style = MaterialTheme.typography.headlineMedium,
            color = NeonPurpleO
        )
        Text(
            text = "All three AI engines strictly validate board boundaries and never move after the round ends.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        RobotDifficulty.entries.forEach { difficulty ->
            val isSelected = selectedDifficulty == difficulty
            val accent = when (difficulty) {
                RobotDifficulty.EASY -> EmeraldWin
                RobotDifficulty.MEDIUM -> ElectricBlueX
                RobotDifficulty.HARD -> NeonPurpleO
            }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) NavySurfaceElevated else NavySurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else BorderSubtle,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { selectedDifficulty = difficulty }
                    .testTag("difficulty_card_${difficulty.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = difficulty.title,
                        tint = accent,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = difficulty.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = difficulty.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = difficulty.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Turn Order",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = humanPlaysFirst,
                        onClick = { humanPlaysFirst = true },
                        label = { Text("You Play First (X)") },
                        modifier = Modifier.testTag("chip_human_first")
                    )
                    FilterChip(
                        selected = !humanPlaysFirst,
                        onClick = { humanPlaysFirst = false },
                        label = { Text("Robot Plays First (X)") },
                        modifier = Modifier.testTag("chip_robot_first")
                    )
                }
            }
        }

        Button(
            onClick = { onStartRobotGame(selectedDifficulty, humanPlaysFirst) },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NeonPurpleO),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("launch_robot_game_button")
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Launch Robot Game",
                tint = NavyBackground
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Start vs ${selectedDifficulty.title}",
                style = MaterialTheme.typography.labelLarge,
                color = NavyBackground
            )
        }
    }
}

/**
 * Screen 7: Complete 3x3 Interactive Game Board Screen for Local & Robot Modes.
 */
@Composable
fun GameBoardScreen(
    session: ActiveGameSessionState,
    animationsEnabled: Boolean,
    onCellClick: (Int) -> Unit,
    onNewRound: () -> Unit,
    onResetScores: () -> Unit,
    onChangeDifficulty: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val board = session.boardState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Mode & Round Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = NavySurfaceElevated,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${session.mode.displayName} • Round ${session.roundNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricBlueX,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (session.mode != GameMode.LOCAL) {
                OutlinedButton(
                    onClick = onChangeDifficulty,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("change_difficulty_button")
                ) {
                    Text(
                        text = session.robotDifficulty.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonPurpleO
                    )
                }
            }
        }

        // Live Scoreboard Strip
        ScoreboardStrip(
            playerXLabel = session.playerXName,
            xWins = session.xScore,
            draws = session.draws,
            playerOLabel = session.playerOName,
            oWins = session.oScore,
            activeTurn = if (board.isTerminal) null else board.currentTurn
        )

        // Turn / Result Banner
        val bannerColor = when (board.result) {
            GameResult.X_WON -> ElectricBlueX
            GameResult.O_WON -> NeonPurpleO
            GameResult.DRAW -> AmberDraw
            GameResult.IN_PROGRESS -> if (board.currentTurn == PlayerSymbol.X) ElectricBlueX else NeonPurpleO
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.14f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .border(1.dp, bannerColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .testTag("game_status_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (session.isRobotThinking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = bannerColor,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = session.statusBannerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = bannerColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 3x3 Interactive Cyber-Grid Board
        CyberGridBoard(
            cells = board.cells,
            winningCells = board.winningCells,
            isInteractive = !board.isTerminal && !session.isRobotThinking,
            animationsEnabled = animationsEnabled,
            onCellTap = onCellClick
        )

        // Action Controls: New Round & Reset Scores
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onNewRound,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (board.isTerminal) EmeraldWin else ElectricBlueX
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("new_round_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "New Round",
                    tint = NavyBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Round",
                    style = MaterialTheme.typography.labelLarge,
                    color = NavyBackground
                )
            }

            OutlinedButton(
                onClick = onResetScores,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("restart_game_button")
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Reset Match Scores",
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset Scores",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun ScoreboardStrip(
    playerXLabel: String,
    xWins: Int,
    draws: Int,
    playerOLabel: String,
    oWins: Int,
    activeTurn: PlayerSymbol?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScorePillCard(
            title = playerXLabel,
            symbol = "X",
            score = xWins,
            accentColor = ElectricBlueX,
            isActive = activeTurn == PlayerSymbol.X,
            modifier = Modifier
                .weight(1f)
                .testTag("score_card_x")
        )
        ScorePillCard(
            title = "Draws",
            symbol = "=",
            score = draws,
            accentColor = AmberDraw,
            isActive = false,
            modifier = Modifier
                .weight(0.8f)
                .testTag("score_card_draws")
        )
        ScorePillCard(
            title = playerOLabel,
            symbol = "O",
            score = oWins,
            accentColor = NeonPurpleO,
            isActive = activeTurn == PlayerSymbol.O,
            modifier = Modifier
                .weight(1f)
                .testTag("score_card_o")
        )
    }
}

@Composable
private fun ScorePillCard(
    title: String,
    symbol: String,
    score: Int,
    accentColor: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) accentColor.copy(alpha = 0.18f) else NavySurface
        ),
        modifier = modifier.border(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) accentColor else BorderSubtle,
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$symbol • $title",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Reusable 3x3 Cyber-Grid Board with custom Canvas X and O symbols and winning combination highlight.
 */
@Composable
fun CyberGridBoard(
    cells: List<String>,
    winningCells: List<Int>,
    isInteractive: Boolean,
    animationsEnabled: Boolean = true,
    onCellTap: (Int) -> Unit
) {
    val winLineProgress by animateFloatAsState(
        targetValue = if (winningCells.size == 3) 1f else 0f,
        animationSpec = tween(durationMillis = if (animationsEnabled) 350 else 0),
        label = "winLineProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 380.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(NavySurface)
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(listOf(ElectricBlueX, NeonPurpleO)),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(14.dp)
            .testTag("tic_tac_toe_board")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0..2) {
                        val cellIndex = row * 3 + col
                        val symbol = cells.getOrElse(cellIndex) { "" }
                        val isWinningCell = cellIndex in winningCells

                        val cellBorderColor = when {
                            isWinningCell -> EmeraldWin
                            symbol == "X" -> ElectricBlueX.copy(alpha = 0.55f)
                            symbol == "O" -> NeonPurpleO.copy(alpha = 0.55f)
                            else -> BorderSubtle
                        }
                        val cellBgColor = when {
                            isWinningCell -> EmeraldWin.copy(alpha = 0.20f)
                            symbol.isNotEmpty() -> NavySurfaceElevated
                            else -> NavyBackground.copy(alpha = 0.75f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cellBgColor)
                                .border(
                                    width = if (isWinningCell) 2.5.dp else 1.dp,
                                    color = cellBorderColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(
                                    enabled = isInteractive && symbol.isEmpty(),
                                    onClick = { onCellTap(cellIndex) }
                                )
                                .semantics {
                                    contentDescription = "Board cell $cellIndex ${if (symbol.isEmpty()) "empty" else symbol}"
                                }
                                .testTag("board_cell_$cellIndex"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (symbol.isNotEmpty()) {
                                SymbolCanvas(
                                    symbol = symbol,
                                    isWinningCell = isWinningCell,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Draw animated glowing winning line across the 3 winning cells
        if (winningCells.size == 3 && winLineProgress > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val startIdx = winningCells.first()
                val endIdx = winningCells.last()

                val startRow = startIdx / 3
                val startCol = startIdx % 3
                val endRow = endIdx / 3
                val endCol = endIdx % 3

                val cellW = size.width / 3f
                val cellH = size.height / 3f

                val startOffset = Offset(
                    x = (startCol + 0.5f) * cellW,
                    y = (startRow + 0.5f) * cellH
                )
                val targetEndOffset = Offset(
                    x = (endCol + 0.5f) * cellW,
                    y = (endRow + 0.5f) * cellH
                )
                val currentEndOffset = Offset(
                    x = startOffset.x + (targetEndOffset.x - startOffset.x) * winLineProgress,
                    y = startOffset.y + (targetEndOffset.y - startOffset.y) * winLineProgress
                )

                drawLine(
                    color = EmeraldWin.copy(alpha = 0.45f),
                    start = startOffset,
                    end = currentEndOffset,
                    strokeWidth = 22f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = EmeraldWin,
                    start = startOffset,
                    end = currentEndOffset,
                    strokeWidth = 9f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun SymbolCanvas(
    symbol: String,
    isWinningCell: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.16f
        if (symbol == "X") {
            val color = if (isWinningCell) EmeraldWin else ElectricBlueX
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(size.width, 0f),
                end = Offset(0f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        } else if (symbol == "O") {
            val color = if (isWinningCell) EmeraldWin else NeonPurpleO
            drawCircle(
                color = color,
                radius = (size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}
