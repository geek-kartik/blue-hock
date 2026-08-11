package com.client.bluehock.game.ble

import com.client.blekotsdk.api.BleKotSdk
import com.client.bluehock.game.protocol.GameProtocol

/**
 * Hosts the Air Hockey GATT service and advertises it so clients can join.
 *
 * Exposes:
 *  - INPUT char: client paddle input (write, no response)
 *  - CONTROL char: client control messages (write + notify)
 *  - STATE char: authoritative state snapshots (notify)
 */
internal class GameBleServer(
    private val onInput: (Float, Float, Float) -> Unit,
    private val onControl: (Byte) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onClientReady: () -> Unit,
    private val onError: (String) -> Unit
) {

    private val server = BleKotSdk.newServer(
        profile = GameGattProfile.profile,
        onWrite = { uuid, value ->
            when (uuid) {
                GameGattProfile.INPUT_CHAR_UUID -> {
                    try {
                        val input = GameProtocol.decodeInput(value)
                        onInput(input.first, input.second, input.third)
                    } catch (_: Exception) {
                        onError("Malformed paddle input received.")
                    }
                }
                GameGattProfile.CONTROL_CHAR_UUID -> {
                    GameProtocol.decodeControl(value)?.let { onControl(it) }
                }
            }
        },
        onConnectionChanged = onConnectionChange,
        onSubscribe = { uuid, subscribed ->
            if (uuid == GameGattProfile.STATE_CHAR_UUID && subscribed) {
                onClientReady()
            }
        },
        onError = { error -> onError(error.message ?: "BLE error") }
    )

    /**
     * Opens the GATT server, registers the game service and starts advertising.
     */
    fun start() {
        server.start()
    }

    /**
     * Pushes a state snapshot to the connected client if notifications are on.
     */
    fun notifyState(data: ByteArray) {
        server.notify(GameGattProfile.STATE_CHAR_UUID, data)
    }

    fun stop() {
        server.stop()
    }
}
