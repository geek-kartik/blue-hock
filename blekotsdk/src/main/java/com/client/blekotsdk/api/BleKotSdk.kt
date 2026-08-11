package com.client.blekotsdk.api

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.client.blekotsdk.ble.BleAdapter
import com.client.blekotsdk.ble.BleConnection
import com.client.blekotsdk.ble.BleGattServer
import com.client.blekotsdk.ble.BleScanner
import com.client.blekotsdk.permissions.BlePermissions
import com.client.blekotsdk.model.BleDevice
import com.client.blekotsdk.model.BleSdkError
import com.client.blekotsdk.model.ConnectionState
import com.client.blekotsdk.model.GattServiceProfile
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Public entry point for the app-agnostic BleKot SDK.
 *
 * Bootstraps the library, provides scanning, and factory methods for the
 * generic BLE primitives:
 *  - [newConnection] — GATT client for talking to a remote peripheral.
 *  - [newServer] — GATT server + advertising to host your own service.
 *  - [startScan] — discover peripherals, optionally filtered by service UUID.
 *
 * Domain SDKs (e.g. games, glucose meters) build on top of these primitives
 * with their own [GattServiceProfile] and protocol layer.
 *
 * Usage:
 *  - Call [initialize] once, e.g. in Application.onCreate.
 *  - Host a service: [newServer] with your [GattServiceProfile], then notify.
 *  - Connect as central: [startScan] -> [newConnection] -> connect -> read/write/notify.
 */
object BleKotSdk {

    @Volatile
    private var appContext: Context? = null

    /**
     * Bootstraps the SDK. Call once, e.g. in Application.onCreate or before
     * any other operation.
     */
    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("BleKotSdk.initialize(context) must be called first.")

    /**
     * Scans for devices advertising [serviceUuid], or all devices when null.
     */
    fun startScan(serviceUuid: UUID? = null): Flow<BleDevice> =
        BleScanner(requireContext()).startScan(serviceUuid)

    /**
     * Scans for devices advertising [serviceUuid], or all devices when null,
     * and emits the cumulative list of unique devices discovered so far.
     */
    fun startScanCollecting(serviceUuid: UUID? = null): Flow<List<BleDevice>> =
        BleScanner(requireContext()).scanDevices(serviceUuid)

    /**
     * Creates a GATT client connection for [onConnectionChanged],
     * [onNotification] and [onError] callbacks.
     */
    fun newConnection(
        onConnectionChanged: (ConnectionState) -> Unit = {},
        onNotification: (characteristicUuid: UUID, value: ByteArray) -> Unit = { _, _ -> },
        onError: (BleSdkError) -> Unit = {}
    ): BleConnection =
        BleConnection(requireContext(), onConnectionChanged, onNotification, onError)

    /**
     * Creates a GATT server that hosts [profile] and advertises it.
     */
    fun newServer(
        profile: GattServiceProfile,
        onWrite: (characteristicUuid: UUID, value: ByteArray) -> Unit,
        onConnectionChanged: (Boolean) -> Unit,
        onSubscribe: (characteristicUuid: UUID, subscribed: Boolean) -> Unit,
        onError: (BleSdkError) -> Unit
    ): BleGattServer =
        BleGattServer(requireContext(), profile, onWrite, onConnectionChanged, onSubscribe, onError)

    /**
     * Runtime permissions required for BLE scan, connect and advertise on the
     * current API level.
     */
    fun requiredBluetoothPermissions(): Array<String> = BlePermissions.requiredPermissions()

    /**
     * Whether all [requiredBluetoothPermissions] are already granted.
     */
    fun hasBluetoothPermissions(): Boolean = BlePermissions.hasPermissions(requireContext())

    /**
     * Launches the system runtime permission dialog for
     * [requiredBluetoothPermissions].
     */
    fun requestBluetoothPermissions(launcher: ActivityResultLauncher<Array<String>>) {
        launcher.launch(requiredBluetoothPermissions())
    }

    /**
     * Whether this device has a Bluetooth radio.
     */
    fun isBluetoothSupported(): Boolean = BleAdapter(requireContext()).isSupported

    /**
     * Whether Bluetooth is currently enabled on the host device.
     */
    fun isBluetoothEnabled(): Boolean = BleAdapter(requireContext()).isEnabled

    /**
     * Shows the system "allow app to turn on Bluetooth?" consent dialog.
     *
     * This is the only way to enable Bluetooth from an app on Android 13+;
     * the legacy [android.bluetooth.BluetoothAdapter.enable] is silently
     * blocked there. Requires BLUETOOTH_CONNECT on Android 12+.
     */
    fun requestEnableBluetooth(launcher: ActivityResultLauncher<Intent>) {
        launcher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    /**
     * Turns Bluetooth off directly. Best effort — restricted on Android 13+.
     */
    fun disableBluetooth(): Boolean = BleAdapter(requireContext()).disable()
}
