package com.client.blekotsdk.api

import android.content.Context
import com.client.blekotsdk.ble.BleConnection
import com.client.blekotsdk.ble.BleGattServer
import com.client.blekotsdk.ble.BleScanner
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
}
