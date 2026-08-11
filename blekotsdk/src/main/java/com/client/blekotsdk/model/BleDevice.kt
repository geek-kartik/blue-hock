package com.client.blekotsdk.model

/**
 * A BLE device discovered while scanning.
 */
data class BleDevice(
    val name: String?,
    val address: String
)
