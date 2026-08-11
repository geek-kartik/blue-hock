package com.client.blekotsdk.game.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.client.blekotsdk.api.BleKotSdk
import com.client.blekotsdk.game.model.AirHockeyState
import com.client.blekotsdk.game.protocol.GameProtocol
import com.client.blekotsdk.model.ConnectionState
import java.util.UUID

/**
 * GATT client that connects to a game host, streams state snapshots via
 * notifications and sends paddle input / control messages upstream.
 */
class GameBleClient(
    private val onState: (AirHockeyState) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {

    private val connection = BleKotSdk.newConnection(
        onConnectionChanged = { state ->
            onConnectionChange(state == ConnectionState.CONNECTED)
        },
        onNotification = { uuid, value ->
            if (uuid == GameGattProfile.STATE_CHAR_UUID) {
                try {
                    onState(GameProtocol.decodeState(value))
                } catch (e: Exception) {
                    onError("Failed to parse game state: ${e.message}")
                }
            }
        },
        onError = { error -> onError(error.message ?: "BLE error") }
    )

    private var lastInputSentAt = 0L
    private val inputIntervalMs = 33L

    /**
     * Connects, discovers services and requests a larger MTU.
     */
    suspend fun connect(address: String) {
        connection.connect(address)
    }

    /**
     * Enables notifications on [charUuid] by writing to its CCCD.
     */
    suspend fun enableNotifications(charUuid: UUID) {
        connection.enableNotifications(GameGattProfile.GAME_SERVICE_UUID, charUuid)
    }

    /**
     * Sends a control message (Ready / Restart) to the host.
     */
    suspend fun sendControl(opcode: Byte) {
        connection.write(
            GameGattProfile.GAME_SERVICE_UUID,
            GameGattProfile.CONTROL_CHAR_UUID,
            byteArrayOf(opcode)
        )
    }

    /**
     * Sends paddle input (position + touch pressure), throttled, without
     * awaiting a write response.
     */
    fun sendInput(x: Float, y: Float, pressure: Float) {
        val now = System.currentTimeMillis()
        if (now - lastInputSentAt < inputIntervalMs) return
        lastInputSentAt = now
        connection.write(
            GameGattProfile.GAME_SERVICE_UUID,
            GameGattProfile.INPUT_CHAR_UUID,
            GameProtocol.encodeInput(x, y, pressure),
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        )
    }

    suspend fun disconnect() {
        connection.disconnect()
    }
}
