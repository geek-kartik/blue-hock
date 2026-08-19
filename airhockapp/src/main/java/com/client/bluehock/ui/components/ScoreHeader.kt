package com.client.bluehock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.ui.board.ClientPaddle
import com.client.bluehock.ui.board.HostPaddle

@Composable
internal fun ScoreHeader(
    state: AirHockeyState,
    isHost: Boolean,
    playerName: String,
    opponentName: String?,
    onEditPlayerName: () -> Unit
) {
    val opponent = opponentName?.trim()?.ifBlank { null } ?: "Opponent"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = playerName.ifBlank { "Air Hockey" },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit player name",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onEditPlayerName),
                    tint = Color.Gray
                )
            }
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreDot(color = if (isHost) HostPaddle else ClientPaddle)
                Text(
                    text = playerName.ifBlank { "You" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHost) HostPaddle else ClientPaddle,
                    maxLines = 1
                )
                Text(
                    text = "${state.yourScore} : ${state.opponentScore}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = opponent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isHost) ClientPaddle else HostPaddle,
                    maxLines = 1
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
