package com.client.bluehock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.client.bluehock.gamesdk.model.AirHockeyState
import com.client.bluehock.gamesdk.model.GameConnectionState
import com.client.bluehock.gamesdk.model.GameConstants
import com.client.bluehock.gamesdk.model.GameDeviceInfo
import com.client.bluehock.gamesdk.model.GamePhase
import com.client.bluehock.viewmodel.AirHockeyViewModel

private val TableGreen = Color(0xFF0E7A3A)
private val TableMid = Color(0xFF2E9E5B)
private val TableDark = Color(0xFF09471F)
private val GoalColor = Color(0xFF05281A)
private val HostPaddle = Color(0xFFE53935)
private val ClientPaddle = Color(0xFF1E88E5)

/**
 * Full Air Hockey screen: score header, draggable board, phase overlays and
 * the connection / restart controls.
 */
@Composable
fun AirHockeyScreen(viewModel: AirHockeyViewModel) {
    val state by viewModel.state.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val myPaddle by viewModel.myPaddle.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isHost = viewModel.isHost

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        ScoreHeader(state = state, isHost = isHost)

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(GameConstants.BOARD_WIDTH / GameConstants.BOARD_HEIGHT)
            ) {
                AirHockeyBoard(
                    state = state,
                    isHost = isHost,
                    myPaddle = myPaddle,
                    onDrag = viewModel::onDrag,
                    modifier = Modifier.fillMaxSize()
                )

                PhaseOverlay(
                    state = state,
                    isHost = isHost,
                    onRestart = viewModel::restart
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ControlBar(
            connection = connection,
            scanning = scanning,
            devices = devices,
            isHost = isHost,
            state = state,
            onHost = viewModel::hostGame,
            onScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onConnect = viewModel::connect,
            onDisconnect = viewModel::disconnect
        )

        if (logs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = logs.lastOrNull() ?: "",
                color = Color.Gray,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ScoreHeader(state: AirHockeyState, isHost: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Air Hockey",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Best of ${state.totalGoals} goals",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScoreDot(color = if (isHost) HostPaddle else ClientPaddle)
                Text(
                    text = "${state.yourScore} : ${state.opponentScore}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                ScoreDot(color = if (isHost) ClientPaddle else HostPaddle)
            }
        }
    }
}

@Composable
private fun ScoreDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, RoundedCornerShape(6.dp))
    )
}

/**
 * Maps a screen-space Y to host-frame logical Y. The host plays "as is"
 * (own paddle at the bottom); the client views the same board from the
 * opposite end, so its Y axis is flipped. Each player then defends the
 * bottom goal and moves its paddle exactly under its fingertip.
 */
private fun toLogicalY(screenY: Float, isHost: Boolean): Float =
    if (isHost) screenY else GameConstants.BOARD_HEIGHT - screenY

/**
 * Interpolates between the two most recent host snapshots so the puck and
 * the opponent paddle glide smoothly at display refresh rate, even when BLE
 * notifications arrive slower or drop packets.
 */
private class BoardInterpolator {
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

@Composable
private fun AirHockeyBoard(
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

/**
 * Renders a hockey mallet with a domed radial-gradient body and two strong
 * solid borders (no glow outside the white border).
 */
private fun DrawScope.drawGlowingPaddle(center: Offset, radius: Float, color: Color) {
    // Soft light glow behind the mallet.
    // Domed body with a light source in the top-left.
    val highlight = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                lerp(color, Color.White, 0.55f),
                color,
                lerp(color, Color.Black, 0.25f)
            ),
            center = highlight,
            radius = radius * 1.4f
        ),
        radius = radius,
        center = center
    )

    // Two strong solid borders: bright outer ring + saturated inner ring.
    drawCircle(
        color = Color.White,
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.18f)
    )
    drawCircle(
        color = color,
        radius = radius * 0.74f,
        center = center,
        style = Stroke(width = radius * 0.15f)
    )

    // Specular sheen.
    drawCircle(
        color = Color.White.copy(alpha = 0.5f),
        radius = radius * 0.4f,
        center = highlight
    )
}

/**
 * Renders the puck in pure white using the same mallet design: a domed body
 * and two solid borders, with no glow outside the border.
 */
private fun DrawScope.drawGlowingWhitePuck(center: Offset, radius: Float) {
    val color = Color.White

    // Domed body with a light source in the top-left.
    val highlight = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF2F2F2),
                Color(0xFFD9D9D9)
            ),
            center = highlight,
            radius = radius * 1.4f
        ),
        radius = radius,
        center = center
    )

    // Two strong solid borders: bright outer ring + soft inner ring.
    drawCircle(
        color = Color.White,
        radius = radius,
        center = center,
        style = Stroke(width = radius * 0.18f)
    )
    drawCircle(
        color = Color(0xFFE0E0E0),
        radius = radius * 0.74f,
        center = center,
        style = Stroke(width = radius * 0.15f)
    )

    // Specular sheen.
    drawCircle(
        color = color.copy(alpha = 0.5f),
        radius = radius * 0.4f,
        center = highlight
    )
}

private fun DrawScope.drawBoardBackground(scale: Float, boardHeight: Float) {
    val widthPx = size.width
    val halfWidth = GameConstants.GOAL_HALF_WIDTH * scale
    val goalCenterX = GameConstants.GOAL_CENTER_X * scale
    val goalDepth = GameConstants.GOAL_DEPTH * scale

    // Attractive green pitch with a soft radial vignette.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(TableMid, TableGreen, TableDark),
            center = Offset(widthPx / 2f, boardHeight / 2f),
            radius = boardHeight * 0.85f
        )
    )

    // Decorative goal nets, one at each edge. Orientation is symmetric, so
    // host and client render them identically.
    drawGoalNet(goalCenterX, 0f, halfWidth, goalDepth, scale)
    drawGoalNet(goalCenterX, boardHeight, halfWidth, goalDepth, scale)

    // Center line and circle.
    drawLine(
        color = Color.White.copy(alpha = 0.5f),
        start = Offset(0f, boardHeight / 2f),
        end = Offset(widthPx, boardHeight / 2f),
        strokeWidth = 2f
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.3f),
        radius = 18f * scale,
        center = Offset(goalCenterX, boardHeight / 2f),
        style = Stroke(width = 2f)
    )
}

/**
 * Draws a decorative goal net at [edgeY] (the board boundary), extending
 * [depth] into the board: a dark pocket with a diamond mesh.
 */
private fun DrawScope.drawGoalNet(centerX: Float, edgeY: Float, halfWidth: Float, depth: Float, scale: Float) {
    val intoBoard = if (edgeY <= 0f) 1f else -1f
    val left = centerX - halfWidth
    val top = if (intoBoard > 0f) edgeY else edgeY - depth
    val width = halfWidth * 2f
    val height = depth

    // Dark net pocket backing.
    drawRect(color = GoalColor, topLeft = Offset(left, top), size = Size(width, height))

    // Diamond mesh inside the pocket.
    val meshColor = Color.White.copy(alpha = 0.32f)
    val meshStroke = (1.1f * scale).coerceAtLeast(0.9f)
    val spacing = 2.6f * scale
    clipRect(left, top, left + width, top + height) {
        var offset = -height
        while (offset <= width) {
            drawLine(
                meshColor,
                Offset(left + offset, top),
                Offset(left + offset + height, top + height),
                meshStroke
            )
            drawLine(
                meshColor,
                Offset(left + offset, top + height),
                Offset(left + offset + height, top),
                meshStroke
            )
            offset += spacing
        }
    }

    // White frame on the left and right posts of the net (the opening and the
    // back stay open).
    val postColor = Color.White.copy(alpha = 0.85f)
    val postStroke = (1f * scale).coerceAtLeast(1f)
    drawLine(postColor, Offset(left, top), Offset(left, top + height), postStroke)
    drawLine(postColor, Offset(left + width, top), Offset(left + width, top + height), postStroke)
}

@Composable
private fun PhaseOverlay(
    state: AirHockeyState,
    isHost: Boolean,
    onRestart: () -> Unit
) {
    when (state.phase) {
        GamePhase.WAITING_FOR_PLAYER -> {
            WaitingText(if (isHost) "Waiting for opponent..." else "Waiting for host...")
        }
        GamePhase.COUNTDOWN -> {
            if (state.countdown > 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "${state.countdown}",
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        GamePhase.GOAL_PAUSE -> {
            val scorer = if (state.score1 > state.score2) "Player 1" else "Player 2"
            WaitingText("Goal! $scorer scores")
        }
        GamePhase.GAME_OVER -> {
            WinnerCard(
                state = state,
                isHost = isHost,
                onRestart = onRestart
            )
        }
        GamePhase.PLAYING -> Unit
    }
}

@Composable
private fun WaitingText(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WinnerCard(
    state: AirHockeyState,
    isHost: Boolean,
    onRestart: () -> Unit
) {
    val youWon = if (isHost) state.winner == 1 else state.winner == 2
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (youWon) "You Win!" else "You Lose",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (youWon) Color(0xFF00C853) else Color(0xFFFF5252)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Final score ${state.yourScore} - ${state.opponentScore}",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Play Again")
                }
            }
        }
    }
}

@Composable
private fun ControlBar(
    connection: GameConnectionState,
    scanning: Boolean,
    devices: List<GameDeviceInfo>,
    isHost: Boolean,
    state: AirHockeyState,
    onHost: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (GameDeviceInfo) -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        when (connection) {
            GameConnectionState.DISCONNECTED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onHost,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Host Game")
                    }
                    OutlinedButton(
                        onClick = if (scanning) onStopScan else onScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (scanning) "Stop Scan" else "Find Game")
                    }
                }

                if (scanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (devices.isEmpty()) {
                        Text(
                            text = "Searching for Air Hockey hosts...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    } else {
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = device.name ?: "Air Hockey Host",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = device.address,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = "JOIN",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            devices.forEach { device ->
                                Button(
                                    onClick = { onConnect(device) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Join ${device.name ?: "Host"}")
                                }
                            }
                        }
                    }
                }
            }
            GameConnectionState.HOSTING,
            GameConnectionState.CONNECTING,
            GameConnectionState.CONNECTED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (connection) {
                            GameConnectionState.HOSTING -> if (isHost) "Hosting game..." else "Connecting..."
                            GameConnectionState.CONNECTING -> "Connecting..."
                            else -> "Connected to opponent"
                        },
                        color = when (connection) {
                            GameConnectionState.CONNECTED -> Color(0xFF00C853)
                            else -> Color(0xFFFFD600)
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}
