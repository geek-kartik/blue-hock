package com.client.bluehock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
internal fun ScoreHeader(state: AirHockeyState, isHost: Boolean) {
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
