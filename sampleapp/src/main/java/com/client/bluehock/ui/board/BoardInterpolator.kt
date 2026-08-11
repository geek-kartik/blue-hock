package com.client.bluehock.ui.board

import com.client.blekotsdk.game.model.AirHockeyState

/**
 * Interpolates between the two most recent host snapshots so the puck and
 * the opponent paddle glide smoothly at display refresh rate, even when BLE
 * notifications arrive slower or drop packets.
 */
internal class BoardInterpolator {
    private var prev: AirHockeyState? = null
    private var next: AirHockeyState? = null
    private var prevTimeNanos = 0L
    private var nextTimeNanos = 0L

    fun onNewSnapshot(state: AirHockeyState) {
        prev = next
        prevTimeNanos = nextTimeNanos
        next = state
        nextTimeNanos = System.nanoTime()
    }

    fun renderStateAt(nowNanos: Long): AirHockeyState {
        val from = prev
        val to = next
        if (to == null) return from ?: AirHockeyState()
        if (from == null || from.phase != to.phase || nextTimeNanos <= prevTimeNanos) return to
        val t = ((nowNanos - prevTimeNanos).toFloat() / (nextTimeNanos - prevTimeNanos))
            .coerceIn(0f, 1f)
        return to.copy(
            paddle1X = lerpFloat(from.paddle1X, to.paddle1X, t),
            paddle1Y = lerpFloat(from.paddle1Y, to.paddle1Y, t),
            paddle2X = lerpFloat(from.paddle2X, to.paddle2X, t),
            paddle2Y = lerpFloat(from.paddle2Y, to.paddle2Y, t),
            puckX = lerpFloat(from.puckX, to.puckX, t),
            puckY = lerpFloat(from.puckY, to.puckY, t)
        )
    }

    private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
