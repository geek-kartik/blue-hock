package com.client.bluehock.game.ble

import com.client.bluehock.game.model.GameConstants
import com.client.blekotsdk.model.GattCharacteristic
import com.client.blekotsdk.model.GattServiceProfile
import java.util.UUID

/**
 * GATT wire profile for the Air Hockey service. Describes the service UUID
 * and its characteristics so the generic [com.client.blekotsdk.ble.BleGattServer]
 * and [com.client.blekotsdk.ble.BleConnection] can host / connect without
 * any game-specific logic.
 */
internal object GameGattProfile {

    /**
     * Custom Air Hockey GATT Service (0xA011).
     */
    val GAME_SERVICE_UUID: UUID = UUID.fromString("0000a011-0000-1000-8000-00805f9b34fb")

    /**
     * Client -> Host paddle input characteristic (Write No Response).
     */
    val INPUT_CHAR_UUID: UUID = UUID.fromString("0000a012-0000-1000-8000-00805f9b34fb")

    /**
     * Host -> Client state snapshot characteristic (Notify).
     */
    val STATE_CHAR_UUID: UUID = UUID.fromString("0000a013-0000-1000-8000-00805f9b34fb")

    /**
     * Client -> Host control messages (Ready / Restart), Notify enabled.
     */
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("0000a014-0000-1000-8000-00805f9b34fb")

    /**
     * The full service definition used by the GATT server.
     */
    val profile: GattServiceProfile = GattServiceProfile(
        serviceUuid = GAME_SERVICE_UUID,
        serviceName = GameConstants.SERVICE_NAME,
        characteristics = listOf(
            GattCharacteristic.writeNoResponse(INPUT_CHAR_UUID),
            GattCharacteristic.writeAndNotify(CONTROL_CHAR_UUID),
            GattCharacteristic.notifyOnly(STATE_CHAR_UUID)
        )
    )
}
