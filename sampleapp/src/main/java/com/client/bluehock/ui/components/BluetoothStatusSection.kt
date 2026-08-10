package com.client.bluehock.ui.components

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.client.bluehock.bluetooth.BluetoothController

/**
 * Compact status strip that surfaces the Bluetooth state and lets the user
 * turn Bluetooth on from inside the app (no settings redirect) or open the
 * Bluetooth tethering guide.
 */
@Composable
internal fun BluetoothStatusSection() {
    val context = LocalContext.current
    val controller = remember { BluetoothController(context) }
    var btOn by remember { mutableStateOf(controller.isEnabled) }
    var showTetheringDialog by remember { mutableStateOf(false) }
    var turnOnFailed by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                btOn = controller.isEnabled
                turnOnFailed = false
            }
        }
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    if (!controller.isSupported) {
        StatusBar(
            message = "Bluetooth is not supported on this device",
            color = Color(0xFFFF5252)
        )
        return
    }

    val statusColor = if (btOn) Color(0xFF00C853) else Color(0xFFFF8F00)
    val background = if (btOn) Color(0x1A00C853) else Color(0x1AFF8F00)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (btOn) "Bluetooth is on" else "Bluetooth is off",
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        if (btOn) {
            OutlinedButton(
                onClick = { showTetheringDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Bluetooth Tethering", fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = { turnOnFailed = !controller.setEnabled(true) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text("Turn On", fontSize = 13.sp)
            }
        }
    }

    if (turnOnFailed) {
        StatusBar(
            message = "Could not turn on Bluetooth. Please check that Bluetooth permissions are granted.",
            color = Color(0xFFFF5252)
        )
    }

    if (showTetheringDialog) {
        BluetoothTetheringDialog(onDismiss = { showTetheringDialog = false })
    }
}

@Composable
private fun StatusBar(message: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = message,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun BluetoothTetheringDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bluetooth Tethering") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Make the connection between the two devices more stable by sharing " +
                        "the match over Bluetooth tethering.",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "1. Host device: this app turns Bluetooth on for you.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "2. Host device: open Settings > Hotspot & tethering > " +
                        "Bluetooth tethering and switch it ON.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "3. Other device: tap Find Game and join the host.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "The match still plays over Bluetooth Low Energy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}
