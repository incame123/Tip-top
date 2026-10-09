package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseConfigStatus
import com.example.data.local.LocalMatchHistoryEntity
import com.example.data.local.LocalScoreEntity
import com.example.data.local.LocalScoreRepository
import com.example.domain.contacts.ConsentManagementModule
import com.example.domain.contacts.ConsentState
import com.example.domain.contacts.ContactDiscoverySettingsModule
import com.example.domain.contacts.ContactDiscoveryUiState
import com.example.domain.contacts.ContactPermissionStatus
import com.example.domain.game.BoardState
import com.example.domain.game.GameMode
import com.example.domain.game.GameResult
import com.example.domain.game.PlayerSymbol
import com.example.domain.game.RobotDifficulty
import com.example.domain.game.TicTacToeEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * All 12 required application screens with full backstack navigation support.
 */
enum class AppScreen(val title: String) {
    SPLASH("Welcome"),
    HOME("Tic-Tac-Toe Pro"),
    LOCAL_MULTIPLAYER("Local Multiplayer"),
    PLAY_AGAINST_ROBOT("Play vs. Robot"),
    DIFFICULTY_SELECTION("Select Difficulty"),
    GAME_BOARD("Game Arena"),
    ONLINE_MULTIPLAYER("Online Multiplayer"),
    ONLINE_ROOM_LOBBY("Online Room Lobby"),
    SCOREBOARD("Scoreboard & History"),
    SETTINGS("Settings & Setup"),
    CONTACT_PERMISSION_PRIVACY("Contact Discovery & Permissions"),
    PRIVACY_POLICY_DELETION("Privacy Policy & Data Deletion")
}

data class ActiveGameSessionState(
    val mode: GameMode = GameMode.LOCAL,
    val robotDifficulty: RobotDifficulty = RobotDifficulty.HARD,
    val humanSymbol: PlayerSymbol = PlayerSymbol.X,
    val playerXName: String = "Player X",
    val playerOName: String = "Player O",
    val boardState: BoardState = BoardState(),
    val xScore: Int = 0,
    val oScore: Int = 0,
    val draws: Int = 0,
    val roundNumber: Int = 1,
    val isRobotThinking: Boolean = false,
    val statusBannerText: String = "Player X's Turn"
)

class TicTacToeViewModel(
    private val localScoreRepository: LocalScoreRepository,
    private val consentModule: ConsentManagementModule,
    private val contactDiscoverySettingsModule: ContactDiscoverySettingsModule,
    val firebaseConfigStatus: FirebaseConfigStatus
) : ViewModel() {

    private val _screenStack = MutableStateFlow(listOf(AppScreen.SPLASH))
    val currentScreen: StateFlow<AppScreen> = MutableStateFlow(AppScreen.SPLASH).apply {
        viewModelScope.launch {
            _screenStack.collect { stack ->
                value = stack.lastOrNull() ?: AppScreen.HOME
            }
        }
    }

    private val _gameSession = MutableStateFlow(ActiveGameSessionState())
    val gameSession: StateFlow<ActiveGameSessionState> = _gameSession.asStateFlow()

    private val _contactUiState = MutableStateFlow(ContactDiscoveryUiState())
    val contactUiState: StateFlow<ContactDiscoveryUiState> = _contactUiState.asStateFlow()

    private val _soundEffectsEnabled = MutableStateFlow(true)
    val soundEffectsEnabled: StateFlow<Boolean> = _soundEffectsEnabled.asStateFlow()

    private val _hapticsEnabled = MutableStateFlow(true)
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(true)
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    val localScores: StateFlow<List<LocalScoreEntity>> = localScoreRepository.allScores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val recentLocalMatches: StateFlow<List<LocalMatchHistoryEntity>> = localScoreRepository.recentMatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private var robotMoveJob: Job? = null

    init {
        viewModelScope.launch {
            consentModule.consentStateFlow.collect { consent ->
                val permStatus = contactDiscoverySettingsModule.currentPermissionStatus(
                    hasRequestedBefore = consent.hasRequestedSystemPermission
                )
                _contactUiState.update {
                    it.copy(
                        consentState = consent,
                        permissionStatus = permStatus
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------
    // Navigation Management
    // -------------------------------------------------------------------
    fun completeSplashToHome() {
        _screenStack.value = listOf(AppScreen.HOME)
    }

    fun navigateTo(screen: AppScreen) {
        _screenStack.update { stack ->
            if (stack.lastOrNull() == screen) stack else stack + screen
        }
    }

    fun navigateBack(): Boolean {
        val current = _screenStack.value
        return if (current.size > 1) {
            _screenStack.value = current.dropLast(1)
            true
        } else if (current.firstOrNull() != AppScreen.HOME) {
            _screenStack.value = listOf(AppScreen.HOME)
            true
        } else {
            false
        }
    }

    fun navigateToBottomTab(tabScreen: AppScreen) {
        if (tabScreen == AppScreen.HOME) {
            _screenStack.value = listOf(AppScreen.HOME)
        } else {
            _screenStack.value = listOf(AppScreen.HOME, tabScreen)
        }
    }

    // -------------------------------------------------------------------
    // Local & Robot Game Session Management
    // -------------------------------------------------------------------
    fun startLocalMultiplayerGame(
        playerXName: String = "Player 1 (X)",
        playerOName: String = "Player 2 (O)"
    ) {
        robotMoveJob?.cancel()
        val cleanX = playerXName.trim().ifBlank { "Player 1 (X)" }
        val cleanO = playerOName.trim().ifBlank { "Player 2 (O)" }
        _gameSession.value = ActiveGameSessionState(
            mode = GameMode.LOCAL,
            playerXName = cleanX,
            playerOName = cleanO,
            boardState = BoardState(),
            statusBannerText = "$cleanX's Turn (X)"
        )
        navigateTo(AppScreen.GAME_BOARD)
    }

    fun startRobotGame(
        difficulty: RobotDifficulty,
        humanPlaysFirstAsX: Boolean = true
    ) {
        robotMoveJob?.cancel()
        val mode = when (difficulty) {
            RobotDifficulty.EASY -> GameMode.ROBOT_EASY
            RobotDifficulty.MEDIUM -> GameMode.ROBOT_MEDIUM
            RobotDifficulty.HARD -> GameMode.ROBOT_HARD
        }
        val humanSymbol = if (humanPlaysFirstAsX) PlayerSymbol.X else PlayerSymbol.O
        val xName = if (humanPlaysFirstAsX) "You (X)" else "Robot (${difficulty.badge})"
        val oName = if (humanPlaysFirstAsX) "Robot (${difficulty.badge})" else "You (O)"

        val initialSession = ActiveGameSessionState(
            mode = mode,
            robotDifficulty = difficulty,
            humanSymbol = humanSymbol,
            playerXName = xName,
            playerOName = oName,
            boardState = BoardState(),
            isRobotThinking = !humanPlaysFirstAsX,
            statusBannerText = if (humanPlaysFirstAsX) "Your Turn (X)" else "Robot (${difficulty.badge}) is thinking..."
        )
        _gameSession.value = initialSession
        navigateTo(AppScreen.GAME_BOARD)

        if (!humanPlaysFirstAsX) {
            triggerRobotMoveIfNeeded()
        }
    }

    fun onCellClicked(
        cellIndex: Int,
        onCompletedMatchForCloudSync: ((board: List<String>, result: String, mode: String) -> Unit)? = null
    ) {
        val session = _gameSession.value
        if (session.mode == GameMode.ONLINE) return
        if (session.isRobotThinking) return
        if (session.boardState.isTerminal) return

        // In Robot mode, ensure human can only tap on their own turn
        if (session.mode != GameMode.LOCAL && session.boardState.currentTurn != session.humanSymbol) {
            return
        }

        val nextBoard = TicTacToeEngine.makeMove(session.boardState, cellIndex) ?: return
        applyUpdatedBoardState(session, nextBoard, onCompletedMatchForCloudSync)

        if (!nextBoard.isTerminal && session.mode != GameMode.LOCAL) {
            triggerRobotMoveIfNeeded(onCompletedMatchForCloudSync)
        }
    }

    private fun triggerRobotMoveIfNeeded(
        onCompletedMatchForCloudSync: ((board: List<String>, result: String, mode: String) -> Unit)? = null
    ) {
        robotMoveJob?.cancel()
        robotMoveJob = viewModelScope.launch {
            val current = _gameSession.value
            val robotSymbol = current.humanSymbol.opponent()
            if (current.boardState.isTerminal || current.boardState.currentTurn != robotSymbol) {
                return@launch
            }

            _gameSession.update {
                it.copy(
                    isRobotThinking = true,
                    statusBannerText = "Robot (${it.robotDifficulty.badge}) is calculating..."
                )
            }

            delay(280L)

            val latest = _gameSession.value
            if (latest.boardState.isTerminal || latest.boardState.currentTurn != robotSymbol) {
                return@launch
            }

            val moveIndex = TicTacToeEngine.chooseRobotMove(
                state = latest.boardState,
                difficulty = latest.robotDifficulty,
                robotSymbol = robotSymbol
            )
            if (moveIndex in 0..8) {
                val updatedBoard = TicTacToeEngine.makeMove(latest.boardState, moveIndex)
                if (updatedBoard != null) {
                    applyUpdatedBoardState(
                        session = latest.copy(isRobotThinking = false),
                        nextBoard = updatedBoard,
                        onCompletedMatchForCloudSync = onCompletedMatchForCloudSync
                    )
                }
            }
        }
    }

    private fun applyUpdatedBoardState(
        session: ActiveGameSessionState,
        nextBoard: BoardState,
        onCompletedMatchForCloudSync: ((board: List<String>, result: String, mode: String) -> Unit)?
    ) {
        val newXScore = session.xScore + if (nextBoard.result == GameResult.X_WON) 1 else 0
        val newOScore = session.oScore + if (nextBoard.result == GameResult.O_WON) 1 else 0
        val newDraws = session.draws + if (nextBoard.result == GameResult.DRAW) 1 else 0

        val banner = when (nextBoard.result) {
            GameResult.X_WON -> "${session.playerXName} Wins!"
            GameResult.O_WON -> "${session.playerOName} Wins!"
            GameResult.DRAW -> "Round Drawn • Evenly Matched!"
            GameResult.IN_PROGRESS -> {
                if (nextBoard.currentTurn == PlayerSymbol.X) {
                    "${session.playerXName}'s Turn (X)"
                } else {
                    "${session.playerOName}'s Turn (O)"
                }
            }
        }

        _gameSession.value = session.copy(
            boardState = nextBoard,
            xScore = newXScore,
            oScore = newOScore,
            draws = newDraws,
            isRobotThinking = false,
            statusBannerText = banner
        )

        if (nextBoard.isTerminal) {
            val winnerCode = when (nextBoard.result) {
                GameResult.X_WON -> "X"
                GameResult.O_WON -> "O"
                GameResult.DRAW -> "DRAW"
                GameResult.IN_PROGRESS -> ""
            }
            viewModelScope.launch {
                localScoreRepository.recordRoundOutcome(
                    modeKey = session.mode.id,
                    modeLabel = session.mode.displayName,
                    winner = winnerCode,
                    movesCount = nextBoard.moveHistory.size
                )
            }
            onCompletedMatchForCloudSync?.invoke(
                nextBoard.cells,
                nextBoard.result.name,
                session.mode.id
            )
        }
    }

    fun startNewRound() {
        robotMoveJob?.cancel()
        val current = _gameSession.value
        val nextRound = current.roundNumber + 1
        // Alternate starting turn in Local mode; in Robot mode keep configured starting turn
        val startingTurn = if (current.mode == GameMode.LOCAL) {
            if (nextRound % 2 == 1) PlayerSymbol.X else PlayerSymbol.O
        } else {
            PlayerSymbol.X
        }
        val freshBoard = BoardState(currentTurn = startingTurn)
        val isRobotFirst = current.mode != GameMode.LOCAL && current.humanSymbol != startingTurn

        _gameSession.value = current.copy(
            boardState = freshBoard,
            roundNumber = nextRound,
            isRobotThinking = isRobotFirst,
            statusBannerText = if (startingTurn == PlayerSymbol.X) {
                "${current.playerXName}'s Turn (X)"
            } else {
                "${current.playerOName}'s Turn (O)"
            }
        )

        if (isRobotFirst) {
            triggerRobotMoveIfNeeded()
        }
    }

    fun resetSessionScoresAndBoard() {
        robotMoveJob?.cancel()
        val current = _gameSession.value
        viewModelScope.launch {
            localScoreRepository.resetModeScore(current.mode.id)
        }
        val isRobotFirst = current.mode != GameMode.LOCAL && current.humanSymbol != PlayerSymbol.X
        _gameSession.value = current.copy(
            boardState = BoardState(),
            xScore = 0,
            oScore = 0,
            draws = 0,
            roundNumber = 1,
            isRobotThinking = isRobotFirst,
            statusBannerText = "${current.playerXName}'s Turn (X)"
        )
        if (isRobotFirst) {
            triggerRobotMoveIfNeeded()
        }
    }

    fun clearAllLocalScores() {
        viewModelScope.launch {
            localScoreRepository.clearAllLocalData()
        }
    }

    // -------------------------------------------------------------------
    // Preferences Toggles
    // -------------------------------------------------------------------
    fun toggleSoundEffects(enabled: Boolean) {
        _soundEffectsEnabled.value = enabled
    }

    fun toggleHaptics(enabled: Boolean) {
        _hapticsEnabled.value = enabled
    }

    fun toggleAnimations(enabled: Boolean) {
        _animationsEnabled.value = enabled
    }

    // -------------------------------------------------------------------
    // Contact Permission & Privacy Foundation Flow
    // -------------------------------------------------------------------
    fun refreshPermissionStatus() {
        val hasRequested = _contactUiState.value.consentState.hasRequestedSystemPermission
        val permStatus = contactDiscoverySettingsModule.currentPermissionStatus(hasRequested)
        _contactUiState.update { it.copy(permissionStatus = permStatus) }
    }

    fun requestEnableContactDiscoveryFlow() {
        _contactUiState.update {
            it.copy(
                showPrePermissionDisclosureDialog = true,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun dismissPrePermissionDisclosureDialog() {
        _contactUiState.update { it.copy(showPrePermissionDisclosureDialog = false) }
    }

    fun confirmPrePermissionDisclosureAndPrepareOsPrompt(onLaunchOsPermissionRequest: () -> Unit) {
        viewModelScope.launch {
            consentModule.markSystemPermissionRequested()
            contactDiscoverySettingsModule.enableLocalDiscoveryAndRecordConsent()
            _contactUiState.update { it.copy(showPrePermissionDisclosureDialog = false) }
            onLaunchOsPermissionRequest()
        }
    }

    fun onOsContactPermissionResult(isGranted: Boolean) {
        viewModelScope.launch {
            val status = contactDiscoverySettingsModule.currentPermissionStatus(hasRequestedBefore = true)
            if (isGranted) {
                _contactUiState.update {
                    it.copy(
                        permissionStatus = ContactPermissionStatus.GRANTED,
                        statusMessage = "Android READ_CONTACTS permission granted. You can now normalize local contacts or opt into server matching.",
                        errorMessage = null
                    )
                }
            } else {
                _contactUiState.update {
                    it.copy(
                        permissionStatus = status,
                        statusMessage = "Contact permission declined. All Tic-Tac-Toe game modes remain 100% playable!",
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun promptServerMatchingConsentDialog() {
        _contactUiState.update { it.copy(showServerConsentDialog = true) }
    }

    fun dismissServerMatchingConsentDialog() {
        _contactUiState.update { it.copy(showServerConsentDialog = false) }
    }

    fun setServerMatchingConsent(granted: Boolean) {
        viewModelScope.launch {
            contactDiscoverySettingsModule.updateServerMatchingOptIn(granted)
            _contactUiState.update {
                it.copy(
                    showServerConsentDialog = false,
                    statusMessage = if (granted) {
                        "Informed consent for server-side keyed HMAC matching granted."
                    } else {
                        "Server-side contact matching disabled."
                    },
                    errorMessage = null
                )
            }
        }
    }

    fun runOnDemandContactDiscovery(authenticatedUserId: String?) {
        viewModelScope.launch {
            _contactUiState.update { it.copy(isProcessing = true, errorMessage = null, statusMessage = null) }
            val currentConsent: ConsentState = _contactUiState.value.consentState
            val effectiveUid = authenticatedUserId ?: "local_device_preview"

            val result = contactDiscoverySettingsModule.runPrivacyPreservingDiscovery(
                userId = effectiveUid,
                consentState = currentConsent
            )
            result.fold(
                onSuccess = { candidates ->
                    _contactUiState.update {
                        it.copy(
                            isProcessing = false,
                            normalizedLocalContactsCount = candidates.size,
                            matchCandidates = candidates,
                            statusMessage = if (candidates.isEmpty()) {
                                "No valid E.164 contacts found on device (you can add contacts in the Android Contacts app to test)."
                            } else if (currentConsent.canPerformServerMatching) {
                                "Processed ${candidates.size} contacts into rate-limited Keyed HMAC-SHA256 tokens."
                            } else {
                                "Normalized ${candidates.size} contacts locally in E.164 format (no data left device)."
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _contactUiState.update {
                        it.copy(
                            isProcessing = false,
                            errorMessage = error.localizedMessage ?: "Contact discovery failed."
                        )
                    }
                }
            )
        }
    }

    fun withdrawConsentAndDeleteDiscoveryData() {
        viewModelScope.launch {
            _contactUiState.update { it.copy(isProcessing = true, errorMessage = null) }
            val result = contactDiscoverySettingsModule.withdrawConsentAndDeleteSubmittedData()
            val deletedBatches = result.getOrDefault(0)
            _contactUiState.update {
                it.copy(
                    isProcessing = false,
                    normalizedLocalContactsCount = 0,
                    matchCandidates = emptyList(),
                    statusMessage = "Consent withdrawn & $deletedBatches server discovery batch(es) permanently deleted.",
                    errorMessage = null
                )
            }
        }
    }
}
