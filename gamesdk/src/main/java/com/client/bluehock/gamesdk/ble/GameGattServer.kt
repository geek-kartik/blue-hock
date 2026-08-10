package com.client.bluehock.gamesdk.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.client.bluehock.gamesdk.model.GameConstants
import com.client.bluehock.gamesdk.protocol.GameProtocol
import java.util.UUID

/**
 * Hosts the Air Hockey GATT service and advertises it so clients can join.
 *
 * Exposes:
 *  - INPUT char: client paddle input (write, no response)
 *  - CONTROL char: client control messages (write + notify)
 *  - STATE char: authoritative state snapshots (notify)
 */
class GameGattServer(
    private val context: Context,
    private val onInput: (Float, Float, Float) -> Unit,
    private val onControl: (Byte) -> Unit,
    private val onConnectionChange: (Boolean) -> Unit,
    private val onClientReady: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false

    private var connectedDevice: BluetoothDevice? = null
    private var stateSubscribed = false

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            onError("Advertising failed with code $errorCode")
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                onConnectionChange(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedDevice?.address == device.address) {
                    connectedDevice = null
                    stateSubscribed = false
                    onConnectionChange(false)
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } catch (_: SecurityException) {
                    // Ignore.
                }
            }

            when (characteristic.uuid) {
                GameConstants.INPUT_CHAR_UUID -> {
                    try {
                        val input = GameProtocol.decodeInput(value)
                        onInput(input.first, input.second, input.third)
                    } catch (_: Exception) {
                        onError("Malformed paddle input received.")
                    }
                }
                GameConstants.CONTROL_CHAR_UUID -> {
                    GameProtocol.decodeControl(value)?.let { onControl(it) }
                }
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            if (descriptor.uuid == GameConstants.CCCD_DESCRIPTOR_UUID &&
                descriptor.characteristic.uuid == GameConstants.STATE_CHAR_UUID
            ) {
                val subscribed = value.isNotEmpty() && value[0].toInt() != 0
                stateSubscribed = subscribed
                if (subscribed) {
                    onClientReady()
                }
            }

            if (responseNeeded) {
                try {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                } catch (_: SecurityException) {
                    // Ignore.
                }
            }
        }
    }

    /**
     * Opens the GATT server, registers the game service and starts advertising.
     */
    fun start() {
        gattServer = try {
            bluetoothManager.openGattServer(context, callback)
        } catch (e: SecurityException) {
            onError("SecurityException opening GATT server.")
            null
        } ?: run {
            onError("Failed to open GATT server. Bluetooth may be off.")
            null
        }

        val server = gattServer
        if (server != null) {
            val service = BluetoothGattService(
                GameConstants.GAME_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            val inputChar = BluetoothGattCharacteristic(
                GameConstants.INPUT_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            val controlChar = BluetoothGattCharacteristic(
                GameConstants.CONTROL_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            controlChar.addDescriptor(cccdDescriptor())

            val stateChar = BluetoothGattCharacteristic(
                GameConstants.STATE_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0
            )
            stateChar.addDescriptor(cccdDescriptor())

            service.addCharacteristic(inputChar)
            service.addCharacteristic(controlChar)
            service.addCharacteristic(stateChar)

            try {
                server.addService(service)
            } catch (e: SecurityException) {
                onError("SecurityException adding game service.")
            }
        }

        startAdvertising()
    }

    private fun cccdDescriptor(): BluetoothGattDescriptor =
        BluetoothGattDescriptor(
            GameConstants.CCCD_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )

    /**
     * Pushes a state snapshot to the connected client if notifications are on.
     */
    fun notifyState(data: ByteArray) {
        val device = connectedDevice ?: return
        if (!stateSubscribed) return
        val server = gattServer ?: return
        val characteristic = server.getService(GameConstants.GAME_SERVICE_UUID)
            ?.getCharacteristic(GameConstants.STATE_CHAR_UUID) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, data)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
        } catch (e: SecurityException) {
            onError("SecurityException notifying state.")
        }
    }

    private fun startAdvertising() {
        val adapter = bluetoothManager.adapter ?: return
        if (adapter.isMultipleAdvertisementSupported == false) {
            onError("BLE advertising is not supported on this device.")
            return
        }
        advertiser = adapter.bluetoothLeAdvertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(GameConstants.GAME_SERVICE_UUID))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            onError("SecurityException starting advertising.")
        }
    }

    private fun stopAdvertising() {
        if (!advertising) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: SecurityException) {
            // Ignore.
        }
        advertising = false
        advertiser = null
    }

    fun stop() {
        stopAdvertising()
        try {
            gattServer?.close()
        } catch (_: SecurityException) {
            // Ignore.
        }
        gattServer = null
        connectedDevice = null
        stateSubscribed = false
    }

    fun stopAdvertisingOnly() {
        stopAdvertising()
    }
}
