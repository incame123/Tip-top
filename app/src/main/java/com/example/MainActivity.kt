package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.FirebaseConfigModule
import com.example.data.firebase.GoogleAuthManager
import com.example.data.firebase.OnlineGameRepository
import com.example.data.local.AppDatabase
import com.example.data.local.LocalScoreRepository
import com.example.domain.contacts.ConsentManagementModule
import com.example.domain.contacts.ContactDiscoverySettingsModule
import com.example.ui.screens.ContactDiscoveryScreen
import com.example.ui.screens.GameBoardScreen
import com.example.ui.screens.GoogleSignInGateCard
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LocalMultiplayerSetupScreen
import com.example.ui.screens.OnlineMultiplayerScreen
import com.example.ui.screens.PrivacyPolicyAndDeletionScreen
import com.example.ui.screens.RobotAndDifficultyScreen
import com.example.ui.screens.ScoreboardScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TicTacToeProTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.OnlineMultiplayerViewModel
import com.example.ui.viewmodel.TicTacToeViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TicTacToeProTheme {
                TicTacToeProApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicTacToeProApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    // Validate & initialize Firebase once
    val firebaseConfigStatus = remember(context) {
        FirebaseConfigModule.ensureInitializedAndValidate(context)
    }

    // Observe Firebase Auth state reactively
    val currentUser by GoogleAuthManager.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = null)

    // Attempt silent auto-sign-in on startup (single GetGoogleIdOption request)
    LaunchedEffect(Unit) {
        GoogleAuthManager.attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {},
            onUnauthenticated = {}
        )
    }

    // Core offline-ready ViewModel (Local 2P, Robot AI, Room Scoreboard, Contact Discovery)
    val mainViewModel: TicTacToeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = AppDatabase.getInstance(app)
                val localRepo = LocalScoreRepository(db.localScoreDao())
                val consentMod = ConsentManagementModule(app)
                val onlineRepo = if (firebaseConfigStatus.isConfigured) {
                    OnlineGameRepository(app)
                } else null
                val discoverySettings = ContactDiscoverySettingsModule(
                    context = app,
                    consentModule = consentMod,
                    onlineRepository = onlineRepo
                )
                TicTacToeViewModel(
                    localScoreRepository = localRepo,
                    consentModule = consentMod,
                    contactDiscoverySettingsModule = discoverySettings,
                    firebaseConfigStatus = firebaseConfigStatus
                )
            }
        }
    )

    // Auth-gated OnlineMultiplayerViewModel: ONLY instantiated when currentUser != null
    val authenticatedUser = currentUser
    val onlineViewModel: OnlineMultiplayerViewModel? = if (authenticatedUser != null && firebaseConfigStatus.isConfigured) {
        viewModel(
            key = authenticatedUser.uid,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val databaseId = app.getString(R.string.firestore_database_id)
                    val firestore = FirebaseFirestore.getInstance(databaseId)
                    OnlineMultiplayerViewModel(
                        repository = OnlineGameRepository(firestore),
                        currentUserId = authenticatedUser.uid,
                        initialDisplayName = authenticatedUser.displayName ?: "Player"
                    )
                }
            }
        )
    } else {
        null
    }

    val currentScreen by mainViewModel.currentScreen.collectAsStateWithLifecycle()
    val gameSession by mainViewModel.gameSession.collectAsStateWithLifecycle()
    val localScores by mainViewModel.localScores.collectAsStateWithLifecycle()
    val recentLocalMatches by mainViewModel.recentLocalMatches.collectAsStateWithLifecycle()
    val contactUiState by mainViewModel.contactUiState.collectAsStateWithLifecycle()
    val soundEnabled by mainViewModel.soundEffectsEnabled.collectAsStateWithLifecycle()
    val hapticsEnabled by mainViewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val animationsEnabled by mainViewModel.animationsEnabled.collectAsStateWithLifecycle()

    val cloudGamesState = onlineViewModel?.syncedGamesState?.collectAsStateWithLifecycle()?.value
    val discoveryBatchesState = onlineViewModel?.discoveryBatchesState?.collectAsStateWithLifecycle()?.value
    val submittedBatches = (discoveryBatchesState as? UiState.Success)?.data ?: emptyList()

    if (currentScreen == AppScreen.SPLASH) {
        SplashScreen(onContinueToHome = mainViewModel::completeSplashToHome)
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = NavyBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentScreen.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (currentScreen != AppScreen.HOME) {
                        IconButton(
                            onClick = { mainViewModel.navigateBack() },
                            modifier = Modifier.testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavySurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = NavySurface) {
                NavigationBarItem(
                    selected = currentScreen == AppScreen.HOME ||
                        currentScreen == AppScreen.LOCAL_MULTIPLAYER ||
                        currentScreen == AppScreen.PLAY_AGAINST_ROBOT ||
                        currentScreen == AppScreen.DIFFICULTY_SELECTION ||
                        currentScreen == AppScreen.GAME_BOARD,
                    onClick = { mainViewModel.navigateToBottomTab(AppScreen.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Arena Home") },
                    label = { Text("Arena") },
                    modifier = Modifier.testTag("nav_tab_home")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.ONLINE_MULTIPLAYER ||
                        currentScreen == AppScreen.ONLINE_ROOM_LOBBY,
                    onClick = { mainViewModel.navigateToBottomTab(AppScreen.ONLINE_MULTIPLAYER) },
                    icon = { Icon(Icons.Default.Cloud, contentDescription = "Online Multiplayer") },
                    label = { Text("Online") },
                    modifier = Modifier.testTag("nav_tab_online")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SCOREBOARD,
                    onClick = { mainViewModel.navigateToBottomTab(AppScreen.SCOREBOARD) },
                    icon = { Icon(Icons.Default.EmojiEvents, contentDescription = "Scoreboard") },
                    label = { Text("Scores") },
                    modifier = Modifier.testTag("nav_tab_scores")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.CONTACT_PERMISSION_PRIVACY ||
                        currentScreen == AppScreen.PRIVACY_POLICY_DELETION,
                    onClick = { mainViewModel.navigateToBottomTab(AppScreen.CONTACT_PERMISSION_PRIVACY) },
                    icon = { Icon(Icons.Default.Contacts, contentDescription = "Contact Discovery") },
                    label = { Text("Discovery") },
                    modifier = Modifier.testTag("nav_tab_discovery")
                )
                NavigationBarItem(
                    selected = currentScreen == AppScreen.SETTINGS,
                    onClick = { mainViewModel.navigateToBottomTab(AppScreen.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.SPLASH -> Unit
                AppScreen.HOME -> {
                    HomeScreen(
                        isSignedIn = authenticatedUser != null,
                        userDisplayName = authenticatedUser?.displayName,
                        localScores = localScores,
                        contactPermissionStatus = contactUiState.permissionStatus,
                        onNavigate = mainViewModel::navigateTo,
                        onQuickStartLocalGame = { mainViewModel.startLocalMultiplayerGame() }
                    )
                }
                AppScreen.LOCAL_MULTIPLAYER -> {
                    LocalMultiplayerSetupScreen(
                        onStartLocalGame = { pX, pO -> mainViewModel.startLocalMultiplayerGame(pX, pO) },
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.PLAY_AGAINST_ROBOT,
                AppScreen.DIFFICULTY_SELECTION -> {
                    RobotAndDifficultyScreen(
                        initialDifficulty = gameSession.robotDifficulty,
                        onStartRobotGame = { diff, humanFirst ->
                            mainViewModel.startRobotGame(diff, humanFirst)
                        },
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.GAME_BOARD -> {
                    GameBoardScreen(
                        session = gameSession,
                        animationsEnabled = animationsEnabled,
                        onCellClick = { index ->
                            mainViewModel.onCellClicked(index) { board, result, mode ->
                                onlineViewModel?.logOfflineOrRobotGameToCloud(board, result, mode)
                            }
                        },
                        onNewRound = mainViewModel::startNewRound,
                        onResetScores = mainViewModel::resetSessionScoresAndBoard,
                        onChangeDifficulty = { mainViewModel.navigateTo(AppScreen.DIFFICULTY_SELECTION) },
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.ONLINE_MULTIPLAYER,
                AppScreen.ONLINE_ROOM_LOBBY -> {
                    if (authenticatedUser == null || onlineViewModel == null) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            GoogleSignInGateCard(
                                onAuthSuccess = {}
                            )
                        }
                    } else {
                        OnlineMultiplayerScreen(
                            onlineViewModel = onlineViewModel,
                            userEmail = authenticatedUser.email,
                            onSignOut = {
                                onlineViewModel.leaveOnlineRoom()
                                GoogleAuthManager.signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = {}
                                )
                            },
                            onBack = { mainViewModel.navigateBack() }
                        )
                    }
                }
                AppScreen.SCOREBOARD -> {
                    ScoreboardScreen(
                        localScores = localScores,
                        recentLocalMatches = recentLocalMatches,
                        cloudGamesState = cloudGamesState,
                        onClearLocalScores = mainViewModel::clearAllLocalScores,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.CONTACT_PERMISSION_PRIVACY -> {
                    ContactDiscoveryScreen(
                        uiState = contactUiState,
                        authenticatedUserId = authenticatedUser?.uid,
                        submittedBatches = submittedBatches,
                        onRefreshPermission = mainViewModel::refreshPermissionStatus,
                        onRequestEnableDiscovery = mainViewModel::requestEnableContactDiscoveryFlow,
                        onDismissPrePermissionDialog = mainViewModel::dismissPrePermissionDisclosureDialog,
                        onConfirmPrePermissionAndLaunchOsDialog = mainViewModel::confirmPrePermissionDisclosureAndPrepareOsPrompt,
                        onOsPermissionResult = mainViewModel::onOsContactPermissionResult,
                        onPromptServerMatchingConsent = mainViewModel::promptServerMatchingConsentDialog,
                        onDismissServerMatchingDialog = mainViewModel::dismissServerMatchingConsentDialog,
                        onSetServerMatchingConsent = mainViewModel::setServerMatchingConsent,
                        onRunContactDiscovery = { mainViewModel.runOnDemandContactDiscovery(authenticatedUser?.uid) },
                        onWithdrawConsentAndDelete = mainViewModel::withdrawConsentAndDeleteDiscoveryData,
                        onOpenPrivacyPolicyScreen = { mainViewModel.navigateTo(AppScreen.PRIVACY_POLICY_DELETION) },
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.PRIVACY_POLICY_DELETION -> {
                    PrivacyPolicyAndDeletionScreen(
                        isSignedIn = authenticatedUser != null,
                        userEmail = authenticatedUser?.email,
                        onDeleteContactDiscoveryData = mainViewModel::withdrawConsentAndDeleteDiscoveryData,
                        onClearLocalScores = mainViewModel::clearAllLocalScores,
                        onDeleteAllCloudAccountData = { callback ->
                            if (onlineViewModel != null) {
                                onlineViewModel.deleteAllCloudAccountData(callback)
                            } else {
                                callback("Not signed in to cloud.")
                            }
                        },
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
                AppScreen.SETTINGS -> {
                    SettingsScreen(
                        soundEnabled = soundEnabled,
                        hapticsEnabled = hapticsEnabled,
                        animationsEnabled = animationsEnabled,
                        firebaseConfigStatus = firebaseConfigStatus,
                        onToggleSound = mainViewModel::toggleSoundEffects,
                        onToggleHaptics = mainViewModel::toggleHaptics,
                        onToggleAnimations = mainViewModel::toggleAnimations,
                        onNavigate = mainViewModel::navigateTo,
                        onBack = { mainViewModel.navigateBack() }
                    )
                }
            }
        }
    }
}
