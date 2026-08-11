package com.client.blekotsdk.model

/**
 * Structured SDK errors that can occur during BLE scan, connection or GATT
 * operations. Extends [Exception] so they can be thrown and collected as-is.
 */
sealed class BleSdkError : Exception() {

    /** Bluetooth radio on the host device is disabled. */
    object BluetoothDisabled : BleSdkError() {
        override val message: String = "Bluetooth is disabled on the host device."
    }

    /** Mandatory Bluetooth permissions are not granted by the user. */
    object PermissionDenied : BleSdkError() {
        override val message: String = "Required Bluetooth permissions were denied."
    }

    /** The required GATT service was not found on the device. */
    object ServiceNotFound : BleSdkError() {
        override val message: String = "The required GATT service was not found on the device."
    }

    /** A required characteristic is missing from the peripheral. */
    object CharacteristicMissing : BleSdkError() {
        override val message: String = "A required GATT characteristic was missing from the peripheral."
    }

    /** Failed to subscribe to characteristic notifications. */
    object NotificationFailed : BleSdkError() {
        override val message: String = "Failed to enable notifications for the characteristic."
    }

    /** A connection attempt timed out. */
    object ConnectionTimeout : BleSdkError() {
        override val message: String = "The connection attempt timed out."
    }

    /** The peripheral disconnected unexpectedly during an operation. */
    data class UnexpectedDisconnect(val details: String?) : BleSdkError() {
        override val message: String = "The device disconnected unexpectedly: ${details ?: "No additional information"}"
    }

    /** Generic Bluetooth GATT error with status code and description. */
    data class GenericBleError(val errorCode: Int, val description: String) : BleSdkError() {
        override val message: String = "Bluetooth GATT Error (code=$errorCode): $description"
    }
}
