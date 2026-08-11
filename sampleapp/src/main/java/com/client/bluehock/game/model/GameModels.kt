package com.client.bluehock.game.model

/**
 * Role of this device in the current BLE game session.
 */
enum class GameRole {
    /** Hosts the GATT server and runs the authoritative simulation. */
    HOST,

    /** Connects to the host as a client. */
    CLIENT
}

/**
 * High level connection state surfaced to the UI.
 */
enum class GameConnectionState {
    DISCONNECTED,
    HOSTING,
    CONNECTING,
    CONNECTED
}

/**
 * Phase of the current match.
 */
enum class GamePhase {
    WAITING_FOR_PLAYER,
    COUNTDOWN,
    PLAYING,
    GOAL_PAUSE,
    GAME_OVER
}

/**
 * A BLE device discovered while scanning for game hosts.
 */
data class GameDeviceInfo(
    val name: String?,
    val address: String
)

/**
 * Immutable snapshot of the whole match, streamed from host to client.
 *
 * Player 1 is always the host (bottom paddle), Player 2 the client (top paddle).
 */
data class AirHockeyState(
    val phase: GamePhase = GamePhase.WAITING_FOR_PLAYER,
    val countdown: Int = 0,
    val score1: Int = 0,
    val score2: Int = 0,
    val winner: Int = 0,
    val paddle1X: Float = GameConstants.GOAL_CENTER_X,
    val paddle1Y: Float = GameConstants.BOARD_HEIGHT - 18f,
    val paddle2X: Float = GameConstants.GOAL_CENTER_X,
    val paddle2Y: Float = 18f,
    val puckX: Float = GameConstants.GOAL_CENTER_X,
    val puckY: Float = GameConstants.BOARD_HEIGHT / 2f,
    val puckVx: Float = 0f,
    val puckVy: Float = 0f,
    val totalGoals: Int = GameConstants.TOTAL_GOALS,
    val isHost: Boolean = false
) {
    /** 1 = host wins, 2 = client wins, 0 = no winner yet. */
    val yourScore: Int
        get() = if (isHost) score1 else score2

    val opponentScore: Int
        get() = if (isHost) score2 else score1
}
