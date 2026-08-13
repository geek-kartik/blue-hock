package com.client.bluehock.bluetooth

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.client.blekotsdk.api.BleKotSdk

/**
 * Thin wrapper around the SDK's Bluetooth capability so the UI can read the
 * current state and turn Bluetooth on/off from inside the app — never a
 * redirect to the system Bluetooth settings.
 *
 * Turning Bluetooth on is handled by the SDK: it requests any missing runtime
 * permissions and then shows the system "allow app to turn on Bluetooth?"
 * consent dialog (the only way to enable Bluetooth on Android 13+). The
 * [permissionLauncher] and [enableBluetoothLauncher] are created by the
 * host Activity/Composable and passed in.
 */
class BluetoothController(
    private val permissionLauncher: ActivityResultLauncher<Array<String>>,
    private val enableBluetoothLauncher: ActivityResultLauncher<Intent>
) {

    val isSupported: Boolean
        get() = BleKotSdk.isBluetoothSupported()

    val isEnabled: Boolean
        get() = BleKotSdk.isBluetoothEnabled()

    /**
     * Turns Bluetooth on or off. Enabling requests missing runtime
     * permissions first, then shows the system consent dialog. Returns true
     * when the request was issued.
     */
    fun setEnabled(enabled: Boolean): Boolean {
        if (!enabled) return BleKotSdk.disableBluetooth()
        return if (!BleKotSdk.hasBluetoothPermissions()) {
            BleKotSdk.requestBluetoothPermissions(permissionLauncher)
            true
        } else {
            BleKotSdk.requestEnableBluetooth(enableBluetoothLauncher)
            true
        }
    }
}
