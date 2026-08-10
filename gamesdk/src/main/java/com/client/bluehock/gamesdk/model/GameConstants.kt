package com.client.bluehock.gamesdk.model

import java.util.UUID

/**
 * Constants for the Air Hockey BLE GATT service, protocol and gameplay rules.
 */
object GameConstants {
    /**
     * Custom Air Hockey GATT Service (0xA011).
     */
    val GAME_SERVICE_UUID: UUID = UUID.fromString("0000a011-0000-1000-8000-00805f9b34fb")

    /**
     * Client -> Host paddle input characteristic (Write No Response).
     */
    val INPUT_CHAR_UUID: UUID = UUID.fromString("0000a012-0000-1000-8000-00805f9b34fb")

    /**
     * Host -> Client state snapshot characteristic (Notify).
     */
    val STATE_CHAR_UUID: UUID = UUID.fromString("0000a013-0000-1000-8000-00805f9b34fb")

    /**
     * Client -> Host control messages (Ready / Restart), Notify enabled.
     */
    val CONTROL_CHAR_UUID: UUID = UUID.fromString("0000a014-0000-1000-8000-00805f9b34fb")

    /**
     * Client Characteristic Configuration Descriptor.
     */
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Board geometry in logical units (scaled to any screen).
    const val BOARD_WIDTH = 80f
    const val BOARD_HEIGHT = 160f
    const val PUCK_RADIUS = 3.5f
    const val PADDLE_RADIUS = 9f
    const val GOAL_CENTER_X = 40f
    const val GOAL_HALF_WIDTH = 24f

    // How deep the goal net pocket extends into the board.
    const val GOAL_DEPTH = 6f

    // Game rules.
    const val TOTAL_GOALS = 7
    const val COUNTDOWN_SECONDS = 3

    // Physics tuning.
    const val PUCK_MAX_SPEED = 170f
    const val PADDLE_SMOOTHING = 18f

    // How much paddle momentum is passed to the puck on a hit.
    const val PADDLE_HIT_FACTOR = 0.8f

    // Paddle speed (units/sec) that saturates the swing-power term.
    const val PADDLE_POWER_SPEED = 160f

    // Max extra launch speed (units/sec) imparted by swing speed + touch
    // pressure, so fast/hard hits feel like a real ball.
    const val PRESSURE_IMPULSE = 110f

    const val SERVICE_NAME = "AirHockey"

    // Control channel opcodes.
    const val OP_READY: Byte = 0x01
    const val OP_RESTART: Byte = 0x02
}
