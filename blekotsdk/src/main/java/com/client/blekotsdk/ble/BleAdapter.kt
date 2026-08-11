package com.client.blekotsdk.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

/**
 * Thin wrapper around the platform Bluetooth adapter for reading the current
 * state and toggling Bluetooth in-app — never a redirect to system settings.
 */
internal class BleAdapter(context: Context) {

    private val adapter: BluetoothAdapter? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }

    val isSupported: Boolean
        get() = adapter != null

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    fun disable(): Boolean = adapter?.disable() == true
}
