package com.client.bluehock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConnectionState

/**
 * Connection controls: host / find / disconnect. The discovered device list is
 * shown in a [DeviceListDialog] while scanning.
 */
@Composable
internal fun ControlBar(
    connection: GameConnectionState,
    isHost: Boolean,
    state: AirHockeyState,
    onHost: () -> Unit,
    onScan: () -> Unit,
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
                        onClick = onScan,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Find Game")
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
