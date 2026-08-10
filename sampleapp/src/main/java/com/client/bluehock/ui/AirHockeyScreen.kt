package com.client.bluehock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.client.bluehock.gamesdk.model.GameConstants
import com.client.bluehock.ui.board.AirHockeyBoard
import com.client.bluehock.ui.components.BluetoothStatusSection
import com.client.bluehock.ui.components.ControlBar
import com.client.bluehock.ui.components.PhaseOverlay
import com.client.bluehock.ui.components.ScoreHeader
import com.client.bluehock.viewmodel.AirHockeyViewModel

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
        BluetoothStatusSection()

        Spacer(modifier = Modifier.height(8.dp))

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
