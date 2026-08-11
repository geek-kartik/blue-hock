package com.client.blekotsdk.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.client.blekotsdk.logging.SdkLog
import com.client.blekotsdk.model.BleConstants
import com.client.blekotsdk.model.BleSdkError
import com.client.blekotsdk.model.ConnectionState
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * App-agnostic GATT client. Connects to a remote peripheral, discovers
 * services, requests MTU and serializes read / write / notify-subscription
 * operations for reliability.
 *
 * Domain logic (protocol parsing, service profiles) lives outside this class:
 * raw characteristic notifications are forwarded via [onNotification].
 */
class BleConnection(
    private val context: Context,
    private val onConnectionChanged: (ConnectionState) -> Unit = {},
    private val onNotification: (characteristicUuid: UUID, value: ByteArray) -> Unit = { _, _ -> },
    private val onError: (BleSdkError) -> Unit = {}
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null

    private val operationMutex = Mutex()
    private var pendingOperation: CompletableDeferred<Any>? = null

    private var servicesDiscovered = CompletableDeferred<Boolean>()
    private val connected = AtomicBoolean(false)

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        SdkLog.e("BleConnection", "GATT connect failed with status $status")
                        onError(BleSdkError.GenericBleError(status, "GATT connect failed with status $status"))
                        servicesDiscovered.complete(false)
                        return
                    }
                    connected.set(true)
                    try {
                        gatt.discoverServices()
                    } catch (_: SecurityException) {
                        // Ignore.
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connected.set(false)
                    onConnectionChanged(ConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(true)
                onConnectionChanged(ConnectionState.CONNECTED)
            } else {
                servicesDiscovered.complete(false)
                onError(BleSdkError.ServiceNotFound)
            }
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onNotification(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            onNotification(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val value = characteristic.value ?: byteArrayOf()
            completeRead(status, value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            completeRead(status, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            completeOperation(status, "Write characteristic failed")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            completeOperation(status, "Descriptor write failed")
        }
    }

    private fun completeRead(status: Int, value: ByteArray) {
        val operation = pendingOperation
        if (operation != null && status == BluetoothGatt.GATT_SUCCESS) {
            operation.complete(value)
        } else if (operation != null) {
            operation.completeExceptionally(BleSdkError.GenericBleError(status, "Read characteristic failed"))
        }
    }

    private fun completeOperation(status: Int, message: String) {
        val operation = pendingOperation
        if (operation != null) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                operation.complete(Unit)
            } else {
                operation.completeExceptionally(BleSdkError.GenericBleError(status, message))
            }
        }
    }

    /**
     * Connects to [address], discovers services and requests a larger MTU.
     */
    suspend fun connect(address: String) {
        if (adapter == null) {
            onError(BleSdkError.BluetoothDisabled)
            return
        }

        servicesDiscovered = CompletableDeferred()
        val device = adapter.getRemoteDevice(address)

        val g = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Bluetooth permission denied.", e)
        }
        gatt = g ?: throw IllegalStateException("Failed to open GATT connection.")

        withTimeout(CONNECT_TIMEOUT_MS) {
            if (!servicesDiscovered.await()) {
                throw BleSdkError.ServiceNotFound
            }
        }

        try {
            g.requestMtu(MTU_BYTES)
        } catch (_: SecurityException) {
            // Non-fatal.
        }
    }

    /**
     * Reads a characteristic value. Suspends until the read completes.
     */
    suspend fun read(serviceUuid: UUID, charUuid: UUID): ByteArray = operationMutex.withLock {
        val g = gatt ?: throw IllegalStateException("Not connected.")
        val characteristic = g.getService(serviceUuid)?.getCharacteristic(charUuid)
            ?: throw BleSdkError.CharacteristicMissing

        val deferred = CompletableDeferred<ByteArray>()
        pendingOperation = deferred as CompletableDeferred<Any>

        try {
            val success = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.readCharacteristic(characteristic)
                } else {
                    @Suppress("DEPRECATION")
                    g.readCharacteristic(characteristic)
                }
            } catch (e: SecurityException) {
                throw BleSdkError.PermissionDenied
            }
            if (!success) {
                throw BleSdkError.GenericBleError(-1, "Failed to initiate read characteristic")
            }
            withTimeout(OPERATION_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingOperation = null
        }
    }

    /**
     * Writes [data] to a characteristic. Fire-and-forget: never awaits a write
     * response (works with both write types).
     */
    fun write(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ) {
        val g = gatt ?: return
        val characteristic = g.getService(serviceUuid)?.getCharacteristic(charUuid) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, data, writeType)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                characteristic.writeType = writeType
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            onError(BleSdkError.PermissionDenied)
        }
    }

    /**
     * Enables notifications/indications on [charUuid] by writing to its CCCD.
     * Suspends until the descriptor write completes.
     */
    suspend fun enableNotifications(serviceUuid: UUID, charUuid: UUID) = operationMutex.withLock {
        val g = gatt ?: throw IllegalStateException("Not connected.")
        val characteristic = g.getService(serviceUuid)?.getCharacteristic(charUuid)
            ?: throw BleSdkError.CharacteristicMissing
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_DESCRIPTOR_UUID)
            ?: throw BleSdkError.NotificationFailed

        val notificationEnabled = try {
            g.setCharacteristicNotification(characteristic, true)
        } catch (e: SecurityException) {
            throw BleSdkError.PermissionDenied
        }
        if (!notificationEnabled) {
            throw BleSdkError.NotificationFailed
        }

        val enableValue = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        val deferred = CompletableDeferred<Unit>()
        pendingOperation = deferred as CompletableDeferred<Any>

        try {
            val success = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(descriptor, enableValue) >= 0
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = enableValue
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            } catch (_: SecurityException) {
                throw BleSdkError.PermissionDenied
            }
            if (!success) {
                throw BleSdkError.NotificationFailed
            }
            withTimeout(OPERATION_TIMEOUT_MS) { deferred.await() }
        } finally {
            pendingOperation = null
        }
    }

    /**
     * Disconnects and closes the GATT link.
     */
    suspend fun disconnect() {
        try {
            gatt?.disconnect()
        } catch (_: SecurityException) {
            // Ignore.
        }
        try {
            gatt?.close()
        } catch (_: SecurityException) {
            // Ignore.
        }
        gatt = null
        connected.set(false)
        onConnectionChanged(ConnectionState.DISCONNECTED)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val OPERATION_TIMEOUT_MS = 5_000L
        const val MTU_BYTES = 512
    }
}
