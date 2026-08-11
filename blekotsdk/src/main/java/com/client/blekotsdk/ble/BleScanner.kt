package com.client.blekotsdk.ble

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
import com.client.blekotsdk.logging.SdkLog
import com.client.blekotsdk.model.BleDevice
import com.client.blekotsdk.model.BleSdkError
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Scans for BLE peripherals, optionally filtered by an advertised service UUID.
 */
class BleScanner(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** Checks whether the Bluetooth permissions required for scanning are granted. */
    fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** Checks whether Bluetooth is currently enabled on the device. */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Emits discovered devices matching [serviceUuid], or all devices when
     * [serviceUuid] is null.
     *
     * Throws [IllegalStateException] when permissions are missing, Bluetooth is
     * off, or no LE scanner is available. The flow closes with a [BleSdkError]
     * when scanning fails mid-scan.
     */
    fun startScan(serviceUuid: UUID? = null): Flow<BleDevice> = callbackFlow {
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
                trySend(BleDevice(name, address))
            }

            override fun onScanFailed(errorCode: Int) {
                SdkLog.e("BleScanner", "Scan failed: $errorCode")
                close(BleSdkError.GenericBleError(errorCode, "Scan failed with code $errorCode"))
            }
        }

        val filterBuilder = ScanFilter.Builder()
        if (serviceUuid != null) {
            filterBuilder.setServiceUuid(ParcelUuid(serviceUuid))
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        SdkLog.i("BleScanner", "Starting BLE scan${serviceUuid?.let { " for $it" } ?: ""}...")
        try {
            scanner.startScan(listOf(filterBuilder.build()), settings, scanCallback)
        } catch (e: SecurityException) {
            throw IllegalStateException("Bluetooth permissions are not granted.", e)
        }

        awaitClose {
            SdkLog.i("BleScanner", "Stopping BLE scan due to flow closure.")
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
