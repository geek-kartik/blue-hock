package com.client.bluehock.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConstants

/**
 * Maps a screen-space Y to host-frame logical Y. The host plays "as is"
 * (own paddle at the bottom); the client views the same board from the
 * opposite end, so its Y axis is flipped. Each player then defends the
 * bottom goal and moves its paddle exactly under its fingertip.
 */
internal fun toLogicalY(screenY: Float, isHost: Boolean): Float =
    if (isHost) screenY else GameConstants.BOARD_HEIGHT - screenY

/**
 * Interactive board that captures drag input, maps it to logical board
 * coordinates and draws the table, puck and both paddles every frame.
 */
@Composable
internal fun AirHockeyBoard(
    state: AirHockeyState,
    isHost: Boolean,
    myPaddle: Pair<Float, Float>?,
    onDrag: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val interpolator = remember { BoardInterpolator() }
    val renderState = remember { mutableStateOf(state) }

    LaunchedEffect(state) {
        interpolator.onNewSnapshot(state)
        renderState.value = state
    }

    // Redraw on every display frame, gliding between host snapshots.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTimeNanos ->
                renderState.value = interpolator.renderStateAt(frameTimeNanos)
            }
        }
    }

    val viewState = renderState.value

    Canvas(
        modifier = modifier.pointerInput(isHost) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val scale = this.size.width.toFloat() / GameConstants.BOARD_WIDTH
                onDrag(
                    down.position.x / scale,
                    toLogicalY(down.position.y / scale, isHost),
                    down.pressure
                )
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    onDrag(
                        change.position.x / scale,
                        toLogicalY(change.position.y / scale, isHost),
                        change.pressure
                    )
                    event.changes.forEach { it.consume() }
                }
            }
        }
    ) {
        val scale = size.width / GameConstants.BOARD_WIDTH
        val boardHeight = GameConstants.BOARD_HEIGHT * scale
        val viewY: (Float) -> Float = { y -> if (isHost) y else GameConstants.BOARD_HEIGHT - y }

        drawBoardBackground(scale, boardHeight)

        // Puck: a solid glowing white ball with no border.
        val puckCenter = Offset(viewState.puckX * scale, viewY(viewState.puckY) * scale)
        drawGlowingWhitePuck(puckCenter, GameConstants.PUCK_RADIUS * scale)

        // Opponent paddle from authoritative state.
        val oppX = if (isHost) viewState.paddle2X else viewState.paddle1X
        val oppY = if (isHost) viewState.paddle2Y else viewState.paddle1Y
        drawGlowingPaddle(
            center = Offset(oppX * scale, viewY(oppY) * scale),
            radius = GameConstants.PADDLE_RADIUS * scale,
            color = if (isHost) ClientPaddle else HostPaddle
        )

        // Own paddle rendered locally for instant feedback.
        val ownX = myPaddle?.first ?: if (isHost) viewState.paddle1X else viewState.paddle2X
        val ownY = myPaddle?.second ?: if (isHost) viewState.paddle1Y else viewState.paddle2Y
        drawGlowingPaddle(
            center = Offset(ownX * scale, viewY(ownY) * scale),
            radius = GameConstants.PADDLE_RADIUS * scale,
            color = if (isHost) HostPaddle else ClientPaddle
        )
    }
}
