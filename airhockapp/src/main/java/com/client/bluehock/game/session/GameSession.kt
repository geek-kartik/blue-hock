package com.client.bluehock.game.session

import android.content.Context
import com.client.blekotsdk.api.BleKotSdk
import com.client.bluehock.game.ble.GameBleClient
import com.client.bluehock.game.ble.GameBleServer
import com.client.bluehock.game.ble.GameGattProfile
import com.client.bluehock.game.ble.GameScanner
import com.client.bluehock.game.engine.AirHockeyEngine
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConnectionState
import com.client.bluehock.game.model.GameConstants
import com.client.bluehock.game.model.GameDeviceInfo
import com.client.bluehock.game.model.GamePhase
import com.client.bluehock.game.protocol.GameProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.jvm.Volatile
import kotlin.math.ceil

/**
 * Owns a single game session: hosts the authoritative simulation or joins a
 * remote host, streams [AirHockeyState] snapshots to the UI and translates
 * paddle input into GATT writes.
 */
internal class GameSession(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = AirHockeyEngine()

    private var server: GameBleServer? = null
    private var client: GameBleClient? = null
    private var hostLoopJob: Job? = null
    private var scanFlowJob: Job? = null

    private val _state = MutableStateFlow(AirHockeyState(isHost = false))
    val state: Flow<AirHockeyState> = _state.asStateFlow()

    private val _connection = MutableStateFlow(GameConnectionState.DISCONNECTED)
    val connection: Flow<GameConnectionState> = _connection.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: Flow<String> = _errors.asSharedFlow()

    // Written by the BLE server binder thread (ready/connect callbacks) and
    // read by the host simulation loop thread, so they must be @Volatile.
    @Volatile private var phase = GamePhase.WAITING_FOR_PLAYER
    @Volatile private var countdownRemaining = 0f
    @Volatile private var goalPauseRemaining = 0f
    @Volatile private var winner = 0
    private var lastBroadcastAt = 0L

    var isHost: Boolean = false
        private set

    init {
        BleKotSdk.initialize(context)
    }

    // ----- Host mode ------------------------------------------------------

    /**
     * Starts the GATT server, advertises the game and runs the simulation.
     */
    fun hostGame() {
        if (isHost) return
        isHost = true
        phase = GamePhase.WAITING_FOR_PLAYER
        winner = 0
        engine.reset()

        server = GameBleServer(
            onInput = { x, y, pressure -> engine.setClientTarget(x, y, pressure) },
            onControl = { op -> handleClientControl(op) },
            onConnectionChange = { connected ->
                _connection.value = if (connected) GameConnectionState.CONNECTED else GameConnectionState.DISCONNECTED
                if (connected) {
                    onOpponentJoined()
                } else {
                    onOpponentLeft()
                }
            },
            onClientReady = { onOpponentJoined() },
            onError = { emitError(it) }
        ).also { it.start() }

        _connection.value = GameConnectionState.HOSTING
        broadcastState()

        hostLoopJob?.cancel()
        hostLoopJob = scope.launch { hostLoop() }
    }

    private suspend fun CoroutineScope.hostLoop() {
        val stepDt = 1f / 120f
        var last = System.nanoTime()
        var accumulator = 0f
        while (isActive) {
            val now = System.nanoTime()
            var dt = (now - last) / 1_000_000_000f
            last = now
            if (dt > 0.1f) dt = 0.1f
            accumulator += dt

            if (phase == GamePhase.PLAYING) {
                // Fixed 120Hz simulation steps keep the physics deterministic.
                while (accumulator >= stepDt) {
                    val scorer = engine.step(stepDt)
                    if (scorer != 0) handleGoal(scorer)
                    accumulator -= stepDt
                }
            } else {
                accumulator = 0f
            }

            updateTimers(dt)
            maybeBroadcast()
            delay(4)
        }
    }

    private fun updateTimers(dt: Float) {
        when (phase) {
            GamePhase.COUNTDOWN -> {
                countdownRemaining -= dt
                if (countdownRemaining <= 0f) {
                    phase = GamePhase.PLAYING
                    broadcastState()
                }
            }
            GamePhase.GOAL_PAUSE -> {
                goalPauseRemaining -= dt
                if (goalPauseRemaining <= 0f) {
                    if (engine.score1 + engine.score2 >= GameConstants.TOTAL_GOALS) {
                        phase = GamePhase.GAME_OVER
                        winner = if (engine.score1 > engine.score2) 1 else 2
                    } else {
                        engine.serve()
                        phase = GamePhase.PLAYING
                    }
                    broadcastState()
                }
            }
            else -> { /* WAITING / PLAYING / GAME_OVER need no per-tick work. */ }
        }
    }

    private fun handleGoal(scorer: Int) {
        winner = 0
        phase = GamePhase.GOAL_PAUSE
        goalPauseRemaining = 1.5f
        broadcastState()
    }

    private fun handleClientControl(opcode: Byte) {
        when (opcode) {
            GameConstants.OP_READY -> {
                if (phase == GamePhase.WAITING_FOR_PLAYER) startCountdown()
            }
            GameConstants.OP_RESTART -> restartGame()
        }
    }

    /**
     * The opponent connected (or subscribed, or signalled ready): start the
     * match automatically. Guarded so the first trigger wins.
     */
    private fun onOpponentJoined() {
        if (phase == GamePhase.WAITING_FOR_PLAYER || phase == GamePhase.COUNTDOWN) {
            startCountdown()
        }
    }

    /**
     * The opponent left: return to waiting so a new player can join.
     */
    private fun onOpponentLeft() {
        if (phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN || phase == GamePhase.GOAL_PAUSE) {
            phase = GamePhase.WAITING_FOR_PLAYER
            winner = 0
            broadcastState()
        }
    }

    private fun startCountdown() {
        phase = GamePhase.COUNTDOWN
        countdownRemaining = GameConstants.COUNTDOWN_SECONDS.toFloat()
        broadcastState()
    }

    /**
     * Resets scores, puck and restarts the match with a fresh countdown.
     */
    fun restartGame() {
        engine.reset()
        winner = 0
        startCountdown()
    }

    private fun maybeBroadcast() {
        val now = System.currentTimeMillis()
        if (now - lastBroadcastAt >= 8) {
            broadcastState()
        }
    }

    private fun broadcastState() {
        val snapshot = buildSnapshot()
        _state.value = snapshot
        lastBroadcastAt = System.currentTimeMillis()
        server?.notifyState(GameProtocol.encodeState(snapshot))
    }

    private fun buildSnapshot(): AirHockeyState {
        return AirHockeyState(
            phase = phase,
            countdown = ceil(countdownRemaining).toInt().coerceAtLeast(0),
            score1 = engine.score1,
            score2 = engine.score2,
            winner = winner,
            paddle1X = engine.paddle1X,
            paddle1Y = engine.paddle1Y,
            paddle2X = engine.paddle2X,
            paddle2Y = engine.paddle2Y,
            puckX = engine.puckX,
            puckY = engine.puckY,
            puckVx = engine.puckVx,
            puckVy = engine.puckVy,
            totalGoals = GameConstants.TOTAL_GOALS,
            isHost = true
        )
    }

    // ----- Client mode -----------------------------------------------------

    fun startScan(): Flow<List<GameDeviceInfo>> {
        val scanner = GameScanner()
        return scanner.startScan()
    }

    fun stopScan() {
        scanFlowJob?.cancel()
    }

    /**
     * Connects to a discovered host, subscribes to state snapshots and signals
     * readiness so the host starts the countdown.
     */
    suspend fun connect(device: GameDeviceInfo) {
        if (isHost) return
        _connection.value = GameConnectionState.CONNECTING

        val c = GameBleClient(
            onState = { snapshot -> _state.value = snapshot },
            onConnectionChange = { connected ->
                _connection.value = if (connected) GameConnectionState.CONNECTED else GameConnectionState.DISCONNECTED
            },
            onError = { emitError(it) }
        )
        client = c

        try {
            c.connect(device.address)
            c.enableNotifications(GameGattProfile.STATE_CHAR_UUID)
            c.enableNotifications(GameGattProfile.CONTROL_CHAR_UUID)
            _connection.value = GameConnectionState.CONNECTED
            c.sendControl(GameConstants.OP_READY)
        } catch (e: Exception) {
            emitError("Connection failed: ${e.message}")
            _connection.value = GameConnectionState.DISCONNECTED
            client = null
        }
    }

    // ----- Shared ----------------------------------------------------------

    /**
     * Sends the local paddle target and touch pressure. Host applies it to
     * the engine directly; client writes it over the air.
     */
    fun sendPaddle(x: Float, y: Float, pressure: Float = 0f) {
        if (isHost) {
            engine.setHostTarget(x, y, pressure)
        } else {
            client?.sendInput(x, y, pressure)
        }
    }

    /**
     * Clamps paddle coordinates to the local player's half of the board.
     */
    fun clampOwnPaddle(x: Float, y: Float): Pair<Float, Float> =
        if (isHost) AirHockeyEngine.clampHost(x, y) else AirHockeyEngine.clampClient(x, y)

    /**
     * Requests a match restart from the current side.
     */
    fun restart() {
        if (isHost) {
            restartGame()
        } else {
            scope.launch {
                try {
                    client?.sendControl(GameConstants.OP_RESTART)
                } catch (_: Exception) {
                    // Ignore.
                }
            }
        }
    }

    suspend fun disconnect() {
        hostLoopJob?.cancel()
        stopScan()
        server?.stop()
        server = null
        client?.disconnect()
        client = null
        isHost = false
        phase = GamePhase.WAITING_FOR_PLAYER
        winner = 0
        _connection.value = GameConnectionState.DISCONNECTED
        _state.value = AirHockeyState(isHost = false)
    }

    private fun emitError(message: String) {
        scope.launch {
            _errors.emit(message)
        }
    }
}
