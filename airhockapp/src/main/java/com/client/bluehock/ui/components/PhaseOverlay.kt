package com.client.bluehock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GamePhase

/**
 * Overlays contextual UI on top of the board depending on the game phase:
 * waiting, countdown, goal pause or the winner card.
 */
@Composable
internal fun PhaseOverlay(
    state: AirHockeyState,
    isHost: Boolean,
    playerName: String,
    opponentName: String?,
    lastScorer: Int,
    onRestart: () -> Unit
) {
    val myName = playerName.ifBlank { "You" }
    val opponent = opponentName?.trim()?.ifBlank { null } ?: "Opponent"

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
            val scorerIsHost = lastScorer == 1
            val scorer = if (scorerIsHost == isHost) myName else opponent
            WaitingText("Goal! $scorer scores")
        }
        GamePhase.GAME_OVER -> {
            WinnerCard(
                state = state,
                isHost = isHost,
                myName = myName,
                opponentName = opponent,
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
    myName: String,
    opponentName: String,
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
                    text = "$myName ${state.yourScore} - ${state.opponentScore} $opponentName",
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
