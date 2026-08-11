package com.client.blekotsdk.game.api

import android.content.Context
import com.client.blekotsdk.game.model.AirHockeyState
import com.client.blekotsdk.game.model.GameConnectionState
import com.client.blekotsdk.game.model.GameDeviceInfo
import com.client.blekotsdk.game.session.GameSession
import kotlinx.coroutines.flow.Flow

/**
 * Public entry point for the Air Hockey over BLE SDK.
 *
 * Two devices play against each other: one hosts the game (runs the
 * authoritative simulation and GATT server), the other joins by scanning,
 * connecting and streaming state snapshots.
 *
 * Game rule: play until [com.client.blekotsdk.game.model.GameConstants.TOTAL_GOALS]
 * goals are scored in total. The player with the most goals wins and the
 * match can be restarted.
 *
 * Usage:
 *  - Host: [hostGame], then listen on [observeState].
 *  - Client: [startScan] -> [connect] -> [sendPaddle].
 */
object GameSdk {

    private var session: GameSession? = null

    /**
     * Bootstraps the SDK. Call once, e.g. in Application.onCreate or before
     * hosting/joining.
     */
    fun initialize(context: Context) {
        if (session == null) {
            session = GameSession(context.applicationContext)
        }
    }

    private fun requireSession(): GameSession =
        session ?: throw IllegalStateException("GameSdk.initialize(context) must be called first.")

    /**
     * Whether this device is the host (Player 1, bottom paddle).
     */
    fun isHost(): Boolean = session?.isHost ?: false

    /**
     * Starts hosting: advertises the game service and begins the simulation.
     */
    fun hostGame() {
        requireSession().hostGame()
    }

    /**
     * Scans for devices advertising the Air Hockey service.
     */
    fun startScan(): Flow<GameDeviceInfo> = requireSession().startScan()

    /**
     * Stops the active scan.
     */
    fun stopScan() {
        session?.stopScan()
    }

    /**
     * Connects to a discovered host and signals readiness.
     */
    suspend fun connect(device: GameDeviceInfo) {
        requireSession().connect(device)
    }

    /**
     * Sends the local paddle target and touch pressure to the game.
     */
    fun sendPaddle(x: Float, y: Float, pressure: Float = 0f) {
        requireSession().sendPaddle(x, y, pressure)
    }

    /**
     * Clamps paddle coordinates to the local player's half of the board.
     */
    fun clampOwnPaddle(x: Float, y: Float): Pair<Float, Float> =
        requireSession().clampOwnPaddle(x, y)

    /**
     * Requests a restart of the match.
     */
    fun restart() {
        requireSession().restart()
    }

    /**
     * Disconnects and stops hosting/scanning.
     */
    suspend fun disconnect() {
        session?.disconnect()
    }

    /**
     * Observes the live match state.
     */
    fun observeState(): Flow<AirHockeyState> = requireSession().state

    /**
     * Observes connection state changes.
     */
    fun observeConnection(): Flow<GameConnectionState> = requireSession().connection

    /**
     * Observes SDK errors surfaced to the UI.
     */
    fun observeErrors(): Flow<String> = requireSession().errors
}
