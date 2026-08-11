package com.client.bluehock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.client.blekotsdk.game.model.AirHockeyState
import com.client.blekotsdk.game.model.GameConnectionState
import com.client.blekotsdk.game.model.GameDeviceInfo

/**
 * Connection controls: host / find / join / disconnect plus the discovered
 * device list shown while scanning.
 */
@Composable
internal fun ControlBar(
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
                            DeviceRow(device)
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

@Composable
private fun DeviceRow(device: GameDeviceInfo) {
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
}
