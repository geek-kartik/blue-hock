package com.client.bluehock.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build

/**
 * Thin wrapper around the platform Bluetooth adapter so the UI can read the
 * current state and enable/disable Bluetooth from inside the app, without
 * redirecting the user to the system Bluetooth settings.
 *
 * On Android 12+ a system consent dialog may appear the first time the app
 * turns Bluetooth on; that is not a settings redirect.
 */
class BluetoothController(context: Context) {

    private val adapter: BluetoothAdapter? = adapter(context)

    val isSupported: Boolean
        get() = adapter != null

    val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    /**
     * Turns Bluetooth on/off from the app. Returns false when the request
     * could not be issued (unsupported hardware or missing permission).
     */
    fun setEnabled(enabled: Boolean): Boolean {
        val adapter = adapter ?: return false
        return if (enabled) adapter.enable() else adapter.disable()
    }

    private fun adapter(context: Context): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }
}
