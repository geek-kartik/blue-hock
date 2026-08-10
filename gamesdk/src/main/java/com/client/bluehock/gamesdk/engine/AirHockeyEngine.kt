package com.client.bluehock.gamesdk.engine

import com.client.bluehock.gamesdk.model.GameConstants
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Deterministic Air Hockey simulation. Runs authoritatively on the host; the
 * client renders the host's snapshots and only contributes its paddle input.
 *
 * Board is 100 x 150 logical units. Player 1 (host) is the bottom paddle,
 * Player 2 (client) the top paddle. The top goal belongs to player 2, the
 * bottom goal to player 1.
 */
class AirHockeyEngine {

    companion object {
        fun clampHost(x: Float, y: Float): Pair<Float, Float> {
            val cx = clamp(x, GameConstants.PADDLE_RADIUS, GameConstants.BOARD_WIDTH - GameConstants.PADDLE_RADIUS)
            val cy = clamp(y, GameConstants.BOARD_HEIGHT * 0.5f + 4f, GameConstants.BOARD_HEIGHT - GameConstants.PADDLE_RADIUS)
            return cx to cy
        }

        fun clampClient(x: Float, y: Float): Pair<Float, Float> {
            val cx = clamp(x, GameConstants.PADDLE_RADIUS, GameConstants.BOARD_WIDTH - GameConstants.PADDLE_RADIUS)
            val cy = clamp(y, GameConstants.PADDLE_RADIUS, GameConstants.BOARD_HEIGHT * 0.5f - 4f)
            return cx to cy
        }

        private fun clamp(value: Float, low: Float, high: Float): Float =
            if (value < low) low else if (value > high) high else value
    }

    private val width = GameConstants.BOARD_WIDTH
    private val height = GameConstants.BOARD_HEIGHT
    private val paddleRadius = GameConstants.PADDLE_RADIUS
    private val puckRadius = GameConstants.PUCK_RADIUS

    // Puck.
    var puckX: Float = width / 2f
        private set
    var puckY: Float = height / 2f
        private set
    var puckVx: Float = 0f
        private set
    var puckVy: Float = 0f
        private set

    // Paddle positions (player 1 = host / bottom, player 2 = client / top).
    var paddle1X: Float = width / 2f
        private set
    var paddle1Y: Float = height - 18f
        private set
    var paddle2X: Float = width / 2f
        private set
    var paddle2Y: Float = 18f
        private set

    private var target1X: Float = paddle1X
    private var target1Y: Float = paddle1Y
    private var target2X: Float = paddle2X
    private var target2Y: Float = paddle2Y

    private var pressure1: Float = 0f
    private var pressure2: Float = 0f

    private var prev1X: Float = paddle1X
    private var prev1Y: Float = paddle1Y
    private var prev2X: Float = paddle2X
    private var prev2Y: Float = paddle2Y

    var score1: Int = 0
        private set
    var score2: Int = 0
        private set

    fun setHostTarget(x: Float, y: Float, pressure: Float = 0f) {
        val clamped = clampHost(x, y)
        target1X = clamped.first
        target1Y = clamped.second
        pressure1 = pressure.coerceIn(0f, 1f)
    }

    fun setClientTarget(x: Float, y: Float, pressure: Float = 0f) {
        val clamped = clampClient(x, y)
        target2X = clamped.first
        target2Y = clamped.second
        pressure2 = pressure.coerceIn(0f, 1f)
    }

    /**
     * Places the puck directly, used by tests (and advanced tuning).
     */
    internal fun placePuck(x: Float, y: Float, vx: Float, vy: Float) {
        puckX = x
        puckY = y
        puckVx = vx
        puckVy = vy
    }

    /**
     * Resets puck to centre, paddle home positions and targets.
     */
    fun serve() {
        puckX = width / 2f
        puckY = height / 2f
        puckVx = 0f
        puckVy = 0f
        resetTargets()
    }

    /**
     * Full match reset: score, positions and puck.
     */
    fun reset() {
        score1 = 0
        score2 = 0
        paddle1X = width / 2f
        paddle1Y = height - 18f
        paddle2X = width / 2f
        paddle2Y = 18f
        serve()
    }

    /**
     * Advances the simulation by [dt] seconds.
     *
     * @return the player who scored (1 or 2) during this step, or 0.
     */
    fun step(dt: Float): Int {
        // Move paddles toward their targets.
        prev1X = paddle1X
        prev1Y = paddle1Y
        prev2X = paddle2X
        prev2Y = paddle2Y
        val k = min(1f, dt * GameConstants.PADDLE_SMOOTHING)
        paddle1X += (target1X - paddle1X) * k
        paddle1Y += (target1Y - paddle1Y) * k
        paddle2X += (target2X - paddle2X) * k
        paddle2Y += (target2Y - paddle2Y) * k

        // Integrate puck.
        puckX += puckVx * dt
        puckY += puckVy * dt
        val friction = 1f - 0.08f * dt
        puckVx *= friction
        puckVy *= friction

        // Side walls.
        if (puckX < puckRadius) {
            puckX = puckRadius
            puckVx = abs(puckVx) * 0.85f
        } else if (puckX > width - puckRadius) {
            puckX = width - puckRadius
            puckVx = -abs(puckVx) * 0.85f
        }

        val inGoal = puckX >= GameConstants.GOAL_CENTER_X - GameConstants.GOAL_HALF_WIDTH &&
                puckX <= GameConstants.GOAL_CENTER_X + GameConstants.GOAL_HALF_WIDTH

        // Top goal -> player 1 scores.
        if (inGoal && puckY - puckRadius <= 0f) {
            score1++
            serve()
            return 1
        }
        // Bottom goal -> player 2 scores.
        if (inGoal && puckY + puckRadius >= height) {
            score2++
            serve()
            return 2
        }

        // Top/bottom walls outside the goal mouth.
        if (!inGoal && puckY - puckRadius <= 0f) {
            puckY = puckRadius
            puckVy = abs(puckVy) * 0.85f
        } else if (!inGoal && puckY + puckRadius >= height) {
            puckY = height - puckRadius
            puckVy = -abs(puckVy) * 0.85f
        }

        // Paddle collisions.
        collidePaddle(paddle1X, paddle1Y, (paddle1X - prev1X) / dt, (paddle1Y - prev1Y) / dt, pressure1)
        collidePaddle(paddle2X, paddle2Y, (paddle2X - prev2X) / dt, (paddle2Y - prev2Y) / dt, pressure2)

        // Cap puck speed.
        val speed = sqrt(puckVx * puckVx + puckVy * puckVy)
        if (speed > GameConstants.PUCK_MAX_SPEED) {
            val scale = GameConstants.PUCK_MAX_SPEED / speed
            puckVx *= scale
            puckVy *= scale
        }

        return 0
    }

    private fun collidePaddle(padX: Float, padY: Float, padVx: Float, padVy: Float, pressure: Float) {
        val dx = puckX - padX
        val dy = puckY - padY
        val distance = sqrt(dx * dx + dy * dy)
        val minDistance = puckRadius + paddleRadius
        if (distance == 0f || distance >= minDistance) return

        val nx = dx / distance
        val ny = dy / distance

        // Push the puck out of the paddle.
        puckX = padX + nx * minDistance
        puckY = padY + ny * minDistance

        // Only a real strike bounces the ball: reflect the relative velocity
        // about the surface normal and transfer the paddle's momentum. While
        // the ball simply rests on the face (dot >= 0) nothing is imparted, so
        // the ball is hit by the paddle's surface instead of being carried.
        val relVx = puckVx - padVx
        val relVy = puckVy - padVy
        val dot = relVx * nx + relVy * ny
        if (dot < 0f) {
            puckVx -= 2f * dot * nx
            puckVy -= 2f * dot * ny

            // Transfer paddle momentum to the puck.
            puckVx += padVx * GameConstants.PADDLE_HIT_FACTOR
            puckVy += padVy * GameConstants.PADDLE_HIT_FACTOR

            // Swing speed + touch pressure add extra launch speed along the
            // normal, so fast/hard strikes react instantly.
            val padSpeed = sqrt(padVx * padVx + padVy * padVy)
            val power = ((padSpeed / GameConstants.PADDLE_POWER_SPEED).coerceIn(0f, 1f) * 0.6f + pressure * 0.4f)
                .coerceIn(0f, 1f)
            val impulse = power * GameConstants.PRESSURE_IMPULSE
            puckVx += nx * impulse
            puckVy += ny * impulse
        }
    }

    private fun resetTargets() {
        target1X = paddle1X
        target1Y = paddle1Y
        target2X = paddle2X
        target2Y = paddle2Y
    }
}
