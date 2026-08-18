package com.client.bluehock.game

import com.client.bluehock.game.engine.AirHockeyEngine
import com.client.bluehock.game.model.AirHockeyState
import com.client.bluehock.game.model.GameConstants
import com.client.bluehock.game.model.GamePhase
import com.client.bluehock.game.protocol.GameProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class GameProtocolTest {

    @Test
    fun `state round trips through encode and decode`() {
        val state = AirHockeyState(
            phase = GamePhase.PLAYING,
            countdown = 0,
            score1 = 3,
            score2 = 4,
            winner = 0,
            paddle1X = 42.5f,
            paddle1Y = 131.2f,
            paddle2X = 58.1f,
            paddle2Y = 18.9f,
            puckX = 49.9f,
            puckY = 75.0f,
            puckVx = -12.3f,
            puckVy = 8.7f,
            totalGoals = GameConstants.WINNING_SCORE
        )

        val decoded = GameProtocol.decodeState(GameProtocol.encodeState(state))

        assertEquals(GamePhase.PLAYING, decoded.phase)
        assertEquals(3, decoded.score1)
        assertEquals(4, decoded.score2)
        assertEquals(0, decoded.winner)
        assertEquals(42.5f, decoded.paddle1X, 0.05f)
        assertEquals(131.2f, decoded.paddle1Y, 0.05f)
        assertEquals(58.1f, decoded.paddle2X, 0.05f)
        assertEquals(18.9f, decoded.paddle2Y, 0.05f)
        assertEquals(49.9f, decoded.puckX, 0.05f)
        assertEquals(75.0f, decoded.puckY, 0.05f)
        assertEquals(-12.3f, decoded.puckVx, 0.05f)
        assertEquals(8.7f, decoded.puckVy, 0.05f)
        assertEquals(GameConstants.WINNING_SCORE, decoded.totalGoals)
    }

    @Test
    fun `input round trips through encode and decode`() {
        val input = GameProtocol.decodeInput(GameProtocol.encodeInput(23.4f, 118.9f, 0.87f))
        assertEquals(23.4f, input.first, 0.05f)
        assertEquals(118.9f, input.second, 0.05f)
        assertEquals(0.87f, input.third, 0.01f)
    }
}

class AirHockeyEngineTest {

    @Test
    fun `puck into top goal scores for player 1`() {
        val engine = AirHockeyEngine()
        engine.placePuck(GameConstants.GOAL_CENTER_X, 10f, 0f, -40f)

        var scorer = 0
        for (i in 0 until 240) {
            scorer = engine.step(1f / 120f)
            if (scorer != 0) break
        }

        assertEquals(1, scorer)
        assertEquals(1, engine.score1)
        assertEquals(0, engine.score2)
    }

    @Test
    fun `puck into bottom goal scores for player 2`() {
        val engine = AirHockeyEngine()
        engine.placePuck(GameConstants.GOAL_CENTER_X, GameConstants.BOARD_HEIGHT - 10f, 0f, 40f)

        var scorer = 0
        for (i in 0 until 240) {
            scorer = engine.step(1f / 120f)
            if (scorer != 0) break
        }

        assertEquals(2, scorer)
        assertEquals(0, engine.score1)
        assertEquals(1, engine.score2)
    }

    @Test
    fun `puck stays in bounds when bounced off the side wall`() {
        val engine = AirHockeyEngine()
        engine.placePuck(GameConstants.BOARD_WIDTH - 2f, GameConstants.BOARD_HEIGHT / 2f, 40f, 0f)

        repeat(240) { engine.step(1f / 120f) }

        assertTrue(engine.puckX >= 0f)
        assertTrue(engine.puckX <= GameConstants.BOARD_WIDTH)
        assertTrue(engine.puckY >= 0f)
        assertTrue(engine.puckY <= GameConstants.BOARD_HEIGHT)
    }

    @Test
    fun `high touch pressure launches the puck faster`() {
        val homeX = GameConstants.BOARD_WIDTH / 2f
        val homeY = GameConstants.BOARD_HEIGHT - 18f

        val hard = AirHockeyEngine()
        hard.placePuck(homeX, homeY + 3f, 0f, 0f)
        hard.setHostTarget(homeX, homeY + 1f, pressure = 1f)
        repeat(2) { hard.step(1f / 120f) }
        val hardSpeed = sqrt(hard.puckVx * hard.puckVx + hard.puckVy * hard.puckVy)

        val soft = AirHockeyEngine()
        soft.placePuck(homeX, homeY + 3f, 0f, 0f)
        soft.setHostTarget(homeX, homeY + 1f, pressure = 0f)
        repeat(2) { soft.step(1f / 120f) }
        val softSpeed = sqrt(soft.puckVx * soft.puckVx + soft.puckVy * soft.puckVy)

        assertTrue("Pressure should launch the puck faster ($hardSpeed vs $softSpeed)", hardSpeed > softSpeed)
    }
}
