package com.client.bluehock.game.protocol

import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConstants
import com.client.bluehock.game.model.GamePhase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Binary (de)serialization for the Air Hockey BLE protocol.
 *
 * All messages are little-endian.
 */
internal object GameProtocol {

    /**
     * Paddle input message: 3 x short (x/y scaled by 10, pressure scaled by
     * 100), total 6 bytes.
     */
    fun encodeInput(x: Float, y: Float, pressure: Float): ByteArray {
        return ByteBuffer.allocate(6)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(encodeFloat(x))
            .putShort(encodeFloat(y))
            .putShort((pressure.coerceIn(0f, 1f) * 100f).roundToInt().toShort())
            .array()
    }

    fun decodeInput(data: ByteArray): Triple<Float, Float, Float> {
        val buffer = wrap(data)
        val x = decodeFloat(buffer.short)
        val y = decodeFloat(buffer.short)
        val pressure = if (buffer.remaining() >= 2) buffer.short.toFloat() / 100f else 0f
        return Triple(x, y, pressure.coerceIn(0f, 1f))
    }

    /**
     * Control message: single opcode byte.
     */
    fun encodeControl(opcode: Byte): ByteArray = byteArrayOf(opcode)

    fun decodeControl(data: ByteArray): Byte? {
        return data.firstOrNull()
    }

    /**
     * State snapshot (22 bytes):
     * [0] phase ordinal, [1] countdown, [2] score1, [3] score2, [4] winner,
     * [5..6] paddle1 x/y, [7..8] paddle2 x/y, [9..10] puck x/y, [11..12] puck vx/vy,
     * [13] totalGoals.
     */
    fun encodeState(state: AirHockeyState): ByteArray {
        return ByteBuffer.allocate(22)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(state.phase.ordinal.toByte())
            .put(state.countdown.toByte())
            .put(state.score1.toByte())
            .put(state.score2.toByte())
            .put(state.winner.toByte())
            .putShort(encodeFloat(state.paddle1X))
            .putShort(encodeFloat(state.paddle1Y))
            .putShort(encodeFloat(state.paddle2X))
            .putShort(encodeFloat(state.paddle2Y))
            .putShort(encodeFloat(state.puckX))
            .putShort(encodeFloat(state.puckY))
            .putShort(encodeFloat(state.puckVx))
            .putShort(encodeFloat(state.puckVy))
            .put(state.totalGoals.toByte())
            .array()
    }

    fun decodeState(data: ByteArray): AirHockeyState {
        val buffer = wrap(data)
        val phase = GamePhase.entries.getOrElse(buffer.get().toInt() and 0xFF) { GamePhase.WAITING_FOR_PLAYER }
        val countdown = buffer.get().toInt()
        val score1 = buffer.get().toInt() and 0xFF
        val score2 = buffer.get().toInt() and 0xFF
        val winner = buffer.get().toInt() and 0xFF
        val p1x = decodeFloat(buffer.short)
        val p1y = decodeFloat(buffer.short)
        val p2x = decodeFloat(buffer.short)
        val p2y = decodeFloat(buffer.short)
        val px = decodeFloat(buffer.short)
        val py = decodeFloat(buffer.short)
        val pvx = decodeFloat(buffer.short)
        val pvy = decodeFloat(buffer.short)
        val totalGoals = buffer.get().toInt() and 0xFF
        return AirHockeyState(
            phase = phase,
            countdown = countdown,
            score1 = score1,
            score2 = score2,
            winner = winner,
            paddle1X = p1x,
            paddle1Y = p1y,
            paddle2X = p2x,
            paddle2Y = p2y,
            puckX = px,
            puckY = py,
            puckVx = pvx,
            puckVy = pvy,
            totalGoals = if (totalGoals == 0) GameConstants.TOTAL_GOALS else totalGoals,
            isHost = false
        )
    }

    private fun wrap(data: ByteArray): ByteBuffer =
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    private fun encodeFloat(value: Float): Short =
        (value * 10f).roundToInt().toShort()

    private fun decodeFloat(value: Short): Float =
        value.toFloat() / 10f
}
