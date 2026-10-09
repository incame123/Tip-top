package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.DiscoveryBatchModel
import com.example.data.firebase.OnlineGameRepository
import com.example.data.firebase.OnlineRoomModel
import com.example.data.firebase.SyncedGameRecordModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class OnlineLobbyUiState(
    val activeRoom: OnlineRoomModel? = null,
    val roomCodeInput: String = "",
    val playerDisplayName: String = "",
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

/**
 * Authenticated ViewModel for Online Multiplayer Rooms, Cloud Game Sync, and Cloud Data Deletion.
 *
 * CRITICAL: Only instantiated when `currentUserId` is non-null (gated at navigation level)
 * and keyed by `currentUserId` via `viewModelFactory`.
 */
class OnlineMultiplayerViewModel(
    private val repository: OnlineGameRepository,
    val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    private val _lobbyState = MutableStateFlow(
        OnlineLobbyUiState(playerDisplayName = initialDisplayName.ifBlank { "Player" })
    )
    val lobbyState: StateFlow<OnlineLobbyUiState> = _lobbyState.asStateFlow()

    // Two-tier Flow error handling for user's synced games
    val syncedGamesState: StateFlow<UiState<List<SyncedGameRecordModel>>> =
        repository.observeUserGames(currentUserId)
            .map<List<SyncedGameRecordModel>, UiState<List<SyncedGameRecordModel>>> { UiState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing synced games", error)
                emit(UiState.Error(error.localizedMessage ?: "Failed to load cloud games"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    // Two-tier Flow error handling for user's submitted contact discovery batches
    val discoveryBatchesState: StateFlow<UiState<List<DiscoveryBatchModel>>> =
        repository.observeDiscoveryBatches(currentUserId)
            .map<List<DiscoveryBatchModel>, UiState<List<DiscoveryBatchModel>>> { UiState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing discovery batches", error)
                emit(UiState.Error(error.localizedMessage ?: "Failed to load discovery batches"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private var roomObserverJob: Job? = null

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(displayName = initialDisplayName.ifBlank { "Player" })
        }
    }

    fun updateRoomCodeInput(code: String) {
        val sanitized = code.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(12)
        _lobbyState.update { it.copy(roomCodeInput = sanitized, errorMessage = null) }
    }

    fun updateDisplayNameInput(name: String) {
        _lobbyState.update { it.copy(playerDisplayName = name.take(40)) }
    }

    fun clearMessages() {
        _lobbyState.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun createNewOnlineRoom(onRoomReady: () -> Unit) {
        viewModelScope.launch {
            _lobbyState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
            val displayName = _lobbyState.value.playerDisplayName.ifBlank { "Host X" }
            val result = repository.createRoom(hostDisplayName = displayName)
            result.fold(
                onSuccess = { room ->
                    _lobbyState.update {
                        it.copy(
                            isBusy = false,
                            activeRoom = room,
                            roomCodeInput = room.roomId,
                            infoMessage = "Room ${room.roomId} created! Share this code with your opponent."
                        )
                    }
                    startObservingRoom(room.roomId)
                    onRoomReady()
                },
                onFailure = { err ->
                    _lobbyState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "Failed to create room."
                        )
                    }
                }
            )
        }
    }

    fun joinExistingOnlineRoom(onRoomReady: () -> Unit) {
        val code = _lobbyState.value.roomCodeInput.trim().uppercase()
        if (code.length < 4) {
            _lobbyState.update { it.copy(errorMessage = "Please enter a valid 6-character room code.") }
            return
        }
        viewModelScope.launch {
            _lobbyState.update { it.copy(isBusy = true, errorMessage = null, infoMessage = null) }
            val displayName = _lobbyState.value.playerDisplayName.ifBlank { "Guest O" }
            val result = repository.joinRoom(rawRoomCode = code, guestDisplayName = displayName)
            result.fold(
                onSuccess = { room ->
                    _lobbyState.update {
                        it.copy(
                            isBusy = false,
                            activeRoom = room,
                            infoMessage = "Joined room ${room.roomId}!"
                        )
                    }
                    startObservingRoom(room.roomId)
                    onRoomReady()
                },
                onFailure = { err ->
                    _lobbyState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "Could not join room '$code'."
                        )
                    }
                }
            )
        }
    }

    private fun startObservingRoom(roomId: String) {
        roomObserverJob?.cancel()
        roomObserverJob = viewModelScope.launch {
            repository.observeRoom(roomId)
                .catch { e ->
                    Log.w(TAG, "Error observing room $roomId", e)
                    _lobbyState.update {
                        it.copy(errorMessage = e.localizedMessage ?: "Lost connection to room $roomId")
                    }
                }
                .collect { updatedRoom ->
                    if (updatedRoom != null) {
                        val prevRoom = _lobbyState.value.activeRoom
                        _lobbyState.update { it.copy(activeRoom = updatedRoom) }

                        // Log completed online match once when transitioning to 'finished'
                        if (prevRoom?.status != "finished" && updatedRoom.status == "finished") {
                            val resultStr = when (updatedRoom.winner) {
                                "X" -> "X_WON"
                                "O" -> "O_WON"
                                else -> "DRAW"
                            }
                            val opponentId = if (currentUserId == updatedRoom.hostUid) {
                                updatedRoom.guestUid
                            } else {
                                updatedRoom.hostUid
                            }
                            repository.logCompletedGame(
                                board = updatedRoom.board,
                                result = resultStr,
                                mode = "ONLINE",
                                opponentUid = opponentId
                            )
                        }
                    }
                }
        }
    }

    fun makeOnlineMove(cellIndex: Int) {
        val room = _lobbyState.value.activeRoom ?: return
        viewModelScope.launch {
            val result = repository.submitOnlineMove(room, cellIndex)
            result.onFailure { err ->
                _lobbyState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Invalid move")
                }
            }
        }
    }

    fun requestOnlineRematch() {
        val room = _lobbyState.value.activeRoom ?: return
        viewModelScope.launch {
            val result = repository.requestRematch(room)
            result.onFailure { err ->
                _lobbyState.update {
                    it.copy(errorMessage = err.localizedMessage ?: "Could not start rematch")
                }
            }
        }
    }

    fun toggleSimulateConnectionState() {
        val room = _lobbyState.value.activeRoom ?: return
        val isHost = currentUserId == room.hostUid
        val currentlyConnected = if (isHost) room.hostConnected else room.guestConnected
        viewModelScope.launch {
            repository.updateConnectionPresence(
                room = room,
                connected = !currentlyConnected,
                abandonIfLeaving = false
            )
        }
    }

    fun leaveOnlineRoom() {
        val room = _lobbyState.value.activeRoom
        roomObserverJob?.cancel()
        roomObserverJob = null
        if (room != null) {
            viewModelScope.launch {
                repository.updateConnectionPresence(
                    room = room,
                    connected = false,
                    abandonIfLeaving = true
                )
            }
        }
        _lobbyState.update { it.copy(activeRoom = null, infoMessage = "Left online room.") }
    }

    fun logOfflineOrRobotGameToCloud(board: List<String>, result: String, mode: String) {
        viewModelScope.launch {
            repository.logCompletedGame(board = board, result = result, mode = mode)
        }
    }

    fun deleteAllCloudAccountData(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _lobbyState.update { it.copy(isBusy = true, errorMessage = null) }
            val res = repository.deleteAllUserCloudData()
            res.fold(
                onSuccess = {
                    _lobbyState.update { it.copy(isBusy = false, activeRoom = null) }
                    onComplete("All cloud profile, consent, discovery batches, and game records deleted.")
                },
                onFailure = { err ->
                    _lobbyState.update { it.copy(isBusy = false) }
                    onComplete("Error deleting cloud data: ${err.localizedMessage}")
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomObserverJob?.cancel()
    }

    private companion object {
        const val TAG = "OnlineMultiplayerVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
