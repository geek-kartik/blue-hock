package com.client.bluehock.gamesdk.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.client.bluehock.gamesdk.model.GameConstants
import com.client.bluehock.gamesdk.model.GameDeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Scans for devices advertising the Air Hockey GATT service.
 */
class GameScanner(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun startScan(): Flow<GameDeviceInfo> = callbackFlow {
        if (!hasPermissions()) {
            throw IllegalStateException("Bluetooth permissions are not granted.")
        }
        if (!isBluetoothEnabled()) {
            throw IllegalStateException("Bluetooth is disabled.")
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
            ?: throw IllegalStateException("BluetoothLeScanner is not available.")

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name: String? = try {
                    result.device.name
                } catch (_: SecurityException) {
                    null
                }
                val address: String = try {
                    result.device.address
                } catch (_: SecurityException) {
                    return@onScanResult
                }
                trySend(GameDeviceInfo(name, address))
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(GameConstants.GAME_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            throw IllegalStateException("Bluetooth permissions are not granted.", e)
        }

        awaitClose {
            try {
                if (hasPermissions() && isBluetoothEnabled()) {
                    scanner.stopScan(scanCallback)
                }
            } catch (_: SecurityException) {
                // Ignore cleanup failures.
            }
        }
    }
}
