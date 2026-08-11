package com.client.blekotsdk.game.ble

import com.client.blekotsdk.api.BleKotSdk
import com.client.blekotsdk.game.model.GameDeviceInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Scans for devices advertising the Air Hockey GATT service.
 */
class GameScanner {

    /**
     * Emits discovered game hosts. Throws [IllegalStateException] when
     * Bluetooth permissions are missing or Bluetooth is disabled.
     */
    fun startScan(): Flow<GameDeviceInfo> =
        BleKotSdk.startScan(GameGattProfile.GAME_SERVICE_UUID)
            .map { device -> GameDeviceInfo(device.name, device.address) }
}
