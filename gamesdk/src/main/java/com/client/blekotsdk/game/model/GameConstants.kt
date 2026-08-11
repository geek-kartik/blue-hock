package com.client.blekotsdk.game.model

/**
 * Constants for the Air Hockey gameplay rules and control protocol.
 *
 * GATT service/characteristic UUIDs live in
 * [com.client.blekotsdk.game.ble.GameGattProfile].
 */
object GameConstants {

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
