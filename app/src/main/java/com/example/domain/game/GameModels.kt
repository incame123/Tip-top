package com.example.domain.game

enum class PlayerSymbol(val label: String) {
    X("X"),
    O("O");

    fun opponent(): PlayerSymbol = if (this == X) O else X
}

enum class RobotDifficulty(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    EASY(
        title = "Easy Mode",
        subtitle = "Selects randomly among valid empty cells. Great for quick warm-ups.",
        badge = "RANDOM"
    ),
    MEDIUM(
        title = "Medium Mode",
        subtitle = "Prioritizes winning moves, blocks threats, and favors center/corners with tactical variety.",
        badge = "TACTICAL"
    ),
    HARD(
        title = "Hard Mode (Minimax)",
        subtitle = "Optimal game-tree search with depth scoring. Unbeatable against any strategy.",
        badge = "UNBEATABLE"
    )
}

enum class GameMode(val id: String, val displayName: String) {
    LOCAL("LOCAL", "Local 2-Player"),
    ROBOT_EASY("ROBOT_EASY", "vs Robot (Easy)"),
    ROBOT_MEDIUM("ROBOT_MEDIUM", "vs Robot (Medium)"),
    ROBOT_HARD("ROBOT_HARD", "vs Robot (Hard)"),
    ONLINE("ONLINE", "Online Multiplayer")
}

enum class GameResult {
    IN_PROGRESS,
    X_WON,
    O_WON,
    DRAW;

    val isGameOver: Boolean
        get() = this != IN_PROGRESS
}

data class BoardState(
    val cells: List<String> = List(9) { "" },
    val currentTurn: PlayerSymbol = PlayerSymbol.X,
    val result: GameResult = GameResult.IN_PROGRESS,
    val winningCells: List<Int> = emptyList(),
    val moveHistory: List<Int> = emptyList()
) {
    val isTerminal: Boolean
        get() = result.isGameOver
}
