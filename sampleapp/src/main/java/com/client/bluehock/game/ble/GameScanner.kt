package com.client.bluehock.game.ble

import com.client.blekotsdk.api.BleKotSdk
import com.client.bluehock.game.model.GameDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Scans for devices advertising the Air Hockey GATT service.
 */
internal class GameScanner {

    /**
     * Emits the cumulative list of discovered game hosts. Throws
     * [IllegalStateException] when Bluetooth permissions are missing or
     * Bluetooth is disabled.
     */
    fun startScan(): Flow<List<GameDeviceInfo>> =
        BleKotSdk.startScanCollecting(GameGattProfile.GAME_SERVICE_UUID)
            .map { devices ->
                devices.map { device -> GameDeviceInfo(device.name, device.address) }
            }
}
