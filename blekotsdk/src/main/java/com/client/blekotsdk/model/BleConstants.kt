package com.client.blekotsdk.model

import java.util.UUID

/**
 * Standard Bluetooth base constants shared by every GATT profile.
 */
object BleConstants {

    /**
     * Client Characteristic Configuration Descriptor — controls notification
     * and indication subscriptions.
     */
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
