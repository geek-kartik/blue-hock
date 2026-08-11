package com.client.blekotsdk.model

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/**
 * Describes one characteristic of a GATT service.
 *
 * @param uuid Characteristic UUID.
 * @param properties Combination of [BluetoothGattCharacteristic] PROPERTY_* flags.
 * @param permission Combination of [BluetoothGattCharacteristic] PERMISSION_* flags.
 * @param withCccd Whether the characteristic exposes a CCCD descriptor so
 *   clients can subscribe to notifications/indications.
 */
data class GattCharacteristic(
    val uuid: UUID,
    val properties: Int,
    val permission: Int = 0,
    val withCccd: Boolean = false
) {
    companion object {
        /** Client → server message (write with response). */
        fun write(uuid: UUID): GattCharacteristic =
            GattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

        /** High frequency client → server messages (write no response). */
        fun writeNoResponse(uuid: UUID): GattCharacteristic =
            GattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

        /** Bidirectional characteristic: client writes, server notifies. */
        fun writeAndNotify(uuid: UUID): GattCharacteristic =
            GattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
                withCccd = true
            )

        /** Server → client state notifications. */
        fun notifyOnly(uuid: UUID): GattCharacteristic =
            GattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0,
                withCccd = true
            )
    }
}

/**
 * A complete GATT service to be hosted by [com.client.blekotsdk.ble.BleGattServer].
 */
data class GattServiceProfile(
    val serviceUuid: UUID,
    val serviceName: String,
    val characteristics: List<GattCharacteristic>
)
