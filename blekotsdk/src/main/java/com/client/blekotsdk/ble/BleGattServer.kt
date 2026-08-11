package com.client.blekotsdk.ble

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
import com.client.blekotsdk.logging.SdkLog
import com.client.blekotsdk.model.BleConstants
import com.client.blekotsdk.model.BleSdkError
import com.client.blekotsdk.model.GattServiceProfile
import java.util.UUID

/**
 * App-agnostic GATT peripheral. Hosts a [GattServiceProfile], advertises it so
 * clients can connect, and pushes notifications to subscribed centrals.
 *
 * Domain logic (protocol parsing) lives outside this class: incoming writes
 * are forwarded raw via [onWrite] and subscriptions via [onSubscribe].
 */
class BleGattServer(
    private val context: Context,
    private val profile: GattServiceProfile,
    private val onWrite: (characteristicUuid: UUID, value: ByteArray) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onSubscribe: (characteristicUuid: UUID, subscribed: Boolean) -> Unit,
    private val onError: (BleSdkError) -> Unit
) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertising = false

    private var connectedDevice: BluetoothDevice? = null
    private val subscribedCharacteristics = mutableSetOf<UUID>()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            advertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            advertising = false
            onError(BleSdkError.GenericBleError(errorCode, "Advertising failed with code $errorCode"))
        }
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice = device
                onConnectionChanged(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (connectedDevice?.address == device.address) {
                    connectedDevice = null
                    subscribedCharacteristics.clear()
                    onConnectionChanged(false)
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
                sendResponse(device, requestId, offset)
            }
            onWrite(characteristic.uuid, value)
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
            if (descriptor.uuid == BleConstants.CCCD_DESCRIPTOR_UUID) {
                val characteristicUuid = descriptor.characteristic.uuid
                val subscribed = value.isNotEmpty() && value[0].toInt() != 0
                if (subscribed) {
                    subscribedCharacteristics.add(characteristicUuid)
                } else {
                    subscribedCharacteristics.remove(characteristicUuid)
                }
                onSubscribe(characteristicUuid, subscribed)
            }

            if (responseNeeded) {
                sendResponse(device, requestId, offset)
            }
        }
    }

    private fun sendResponse(device: BluetoothDevice, requestId: Int, offset: Int) {
        try {
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        } catch (_: SecurityException) {
            // Ignore.
        }
    }

    /**
     * Opens the GATT server, registers the profile's service and starts
     * advertising it.
     */
    fun start() {
        gattServer = try {
            bluetoothManager.openGattServer(context, callback)
        } catch (e: SecurityException) {
            onError(BleSdkError.PermissionDenied)
            null
        } ?: run {
            onError(BleSdkError.GenericBleError(-1, "Failed to open GATT server. Bluetooth may be off."))
            null
        }

        val server = gattServer
        if (server != null) {
            val service = BluetoothGattService(
                profile.serviceUuid,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            profile.characteristics.forEach { definition ->
                val characteristic = BluetoothGattCharacteristic(
                    definition.uuid,
                    definition.properties,
                    definition.permission
                )
                if (definition.withCccd) {
                    characteristic.addDescriptor(cccdDescriptor())
                }
                service.addCharacteristic(characteristic)
            }

            try {
                server.addService(service)
            } catch (e: SecurityException) {
                onError(BleSdkError.PermissionDenied)
            }
        }

        startAdvertising()
    }

    private fun cccdDescriptor(): BluetoothGattDescriptor =
        BluetoothGattDescriptor(
            BleConstants.CCCD_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )

    /**
     * Pushes [data] to the connected central if it subscribed to
     * [characteristicUuid].
     */
    fun notify(characteristicUuid: UUID, data: ByteArray) {
        val device = connectedDevice ?: return
        if (characteristicUuid !in subscribedCharacteristics) return
        val server = gattServer ?: return
        val characteristic = server.getService(profile.serviceUuid)
            ?.getCharacteristic(characteristicUuid) ?: return

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
            onError(BleSdkError.PermissionDenied)
        }
    }

    private fun startAdvertising() {
        val adapter = bluetoothManager.adapter ?: return
        if (adapter.isMultipleAdvertisementSupported == false) {
            onError(BleSdkError.GenericBleError(-1, "BLE advertising is not supported on this device."))
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
            .addServiceUuid(ParcelUuid(profile.serviceUuid))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            onError(BleSdkError.PermissionDenied)
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

    /**
     * Stops advertising and closes the GATT server.
     */
    fun stop() {
        stopAdvertising()
        try {
            gattServer?.close()
        } catch (_: SecurityException) {
            // Ignore.
        }
        gattServer = null
        connectedDevice = null
        subscribedCharacteristics.clear()
    }

    /**
     * Stops advertising without closing the server.
     */
    fun stopAdvertisingOnly() {
        stopAdvertising()
    }
}
