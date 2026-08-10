package com.client.bluehock.gamesdk.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.client.bluehock.gamesdk.model.AirHockeyState
import com.client.bluehock.gamesdk.model.GameConstants
import com.client.bluehock.gamesdk.protocol.GameProtocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GATT client that connects to a game host, streams state snapshots via
 * notifications and sends paddle input / control messages upstream.
 */
class GameGattClient(
    private val context: Context,
    private val onState: (AirHockeyState) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null

    private var servicesDiscovered = CompletableDeferred<Boolean>()
    private var writeCompleted = CompletableDeferred<Boolean>()
    private val connected = AtomicBoolean(false)

    private var lastInputSentAt = 0L
    private val inputIntervalMs = 33L

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        onError("GATT connect failed with status $status")
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
                    onConnectionChange(false)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            servicesDiscovered.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.uuid, characteristic.value ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeCompleted.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            writeCompleted.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        when (uuid) {
            GameConstants.STATE_CHAR_UUID -> {
                try {
                    onState(GameProtocol.decodeState(value))
                } catch (e: Exception) {
                    onError("Failed to parse game state: ${e.message}")
                }
            }
            else -> { /* Control messages not required from host. */ }
        }
    }

    /**
     * Connects, discovers services and requests a larger MTU.
     */
    suspend fun connect(address: String) {
        servicesDiscovered = CompletableDeferred()
        val device = adapter?.getRemoteDevice(address)
            ?: throw IllegalStateException("Unknown remote device $address")

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

        withTimeout(10000) {
            if (!servicesDiscovered.await()) {
                throw IllegalStateException("Service discovery failed.")
            }
        }

        try {
            g.requestMtu(512)
        } catch (_: SecurityException) {
            // Non-fatal.
        }
        onConnectionChange(true)
    }

    /**
     * Enables notifications on [charUuid] by writing to its CCCD.
     */
    suspend fun enableNotifications(charUuid: UUID) {
        val g = gatt ?: throw IllegalStateException("Not connected.")
        val service = g.getService(GameConstants.GAME_SERVICE_UUID)
            ?: throw IllegalStateException("Game service not found on host.")
        val characteristic = service.getCharacteristic(charUuid)
            ?: throw IllegalStateException("Characteristic $charUuid not found on host.")
        val descriptor = characteristic.getDescriptor(GameConstants.CCCD_DESCRIPTOR_UUID)
            ?: throw IllegalStateException("CCCD descriptor not found on host.")

        try {
            g.setCharacteristicNotification(characteristic, true)
        } catch (e: SecurityException) {
            onError("SecurityException enabling notifications.")
            return
        }

        writeCompleted = CompletableDeferred()
        try {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            if (!g.writeDescriptor(descriptor)) {
                throw IllegalStateException("Failed to write CCCD descriptor.")
            }
        } catch (e: SecurityException) {
            onError("SecurityException writing CCCD descriptor.")
            return
        }
        withTimeout(5000) {
            if (!writeCompleted.await()) {
                throw IllegalStateException("CCCD write failed.")
            }
        }
    }

    /**
     * Sends the "ready" control message once the link is up.
     */
    suspend fun sendControl(opcode: Byte) {
        writeMessage(GameConstants.CONTROL_CHAR_UUID, byteArrayOf(opcode), awaitResponse = true)
    }

    /**
     * Sends paddle input (position + touch pressure), throttled, without
     * awaiting a write response.
     */
    fun sendInput(x: Float, y: Float, pressure: Float) {
        val now = System.currentTimeMillis()
        if (now - lastInputSentAt < inputIntervalMs) return
        lastInputSentAt = now
        writeMessage(GameConstants.INPUT_CHAR_UUID, GameProtocol.encodeInput(x, y, pressure), awaitResponse = false)
    }

    private fun writeMessage(charUuid: UUID, data: ByteArray, awaitResponse: Boolean) {
        val g = gatt ?: return
        val characteristic = g.getService(GameConstants.GAME_SERVICE_UUID)?.getCharacteristic(charUuid) ?: return
        val writeType = if (awaitResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

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
            onError("SecurityException writing characteristic.")
        }
    }

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
    }
}
