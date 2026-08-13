package com.client.bluehock.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.client.bluehock.game.api.AirHockeyGame
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConnectionState
import com.client.bluehock.game.model.GameDeviceInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridges the Air Hockey Compose UI to the [AirHockeyGame].
 */
class AirHockeyViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(AirHockeyState())
    val state: StateFlow<AirHockeyState> = _state.asStateFlow()

    private val _connection = MutableStateFlow(GameConnectionState.DISCONNECTED)
    val connection: StateFlow<GameConnectionState> = _connection.asStateFlow()

    private val _devices = MutableStateFlow<List<GameDeviceInfo>>(emptyList())
    val devices: StateFlow<List<GameDeviceInfo>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    /** Last locally known paddle position, rendered instantly on drag. */
    private val _myPaddle = MutableStateFlow<Pair<Float, Float>?>(null)
    val myPaddle: StateFlow<Pair<Float, Float>?> = _myPaddle.asStateFlow()

    val isHost: Boolean
        get() = AirHockeyGame.isHost()

    private var scanJob: Job? = null

    init {
        AirHockeyGame.initialize(getApplication())

        viewModelScope.launch {
            AirHockeyGame.observeState().collect { _state.value = it }
        }
        viewModelScope.launch {
            AirHockeyGame.observeConnection().collect { _connection.value = it }
        }
        viewModelScope.launch {
            AirHockeyGame.observeErrors().collect { error -> addLog("ERROR: $error") }
        }
    }

    fun hostGame() {
        addLog("Hosting Air Hockey game...")
        viewModelScope.launch { AirHockeyGame.hostGame() }
    }

    fun startScan() {
        if (_scanning.value) return
        _scanning.value = true
        _devices.value = emptyList()
        addLog("Scanning for Air Hockey hosts...")
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                AirHockeyGame.startScan().collect { devices -> _devices.value = devices }
            } catch (e: Exception) {
                addLog("Scan failed: ${e.message}")
            } finally {
                _scanning.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        AirHockeyGame.stopScan()
        _scanning.value = false
    }

    fun connect(device: GameDeviceInfo) {
        stopScan()
        addLog("Joining ${device.name ?: "host"} (${device.address})...")
        viewModelScope.launch {
            try {
                AirHockeyGame.connect(device)
            } catch (e: Exception) {
                addLog("Connect failed: ${e.message}")
            }
        }
    }

    fun disconnect() {
        _myPaddle.value = null
        viewModelScope.launch { AirHockeyGame.disconnect() }
    }

    /**
     * Called from the board drag gesture with logical board coordinates and
     * the current touch pressure.
     */
    fun onDrag(x: Float, y: Float, pressure: Float = 0f) {
        val (clampedX, clampedY) = AirHockeyGame.clampOwnPaddle(x, y)
        _myPaddle.value = clampedX to clampedY
        AirHockeyGame.sendPaddle(clampedX, clampedY, pressure)
    }

    fun restart() {
        addLog("Restarting match...")
        viewModelScope.launch { AirHockeyGame.restart() }
    }

    private fun addLog(message: String) {
        _logs.value = (_logs.value + message).takeLast(200)
    }
}
