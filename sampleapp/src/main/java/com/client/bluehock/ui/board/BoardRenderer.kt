package com.client.bluehock.ui.board

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import com.client.bluehock.game.model.GameConstants

/**
 * Renders a hockey mallet with a domed radial-gradient body and two strong
 * solid borders (no glow outside the white border).
 */
internal fun DrawScope.drawGlowingPaddle(center: Offset, radius: Float, color: Color) {
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
internal fun DrawScope.drawGlowingWhitePuck(center: Offset, radius: Float) {
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

internal fun DrawScope.drawBoardBackground(scale: Float, boardHeight: Float) {
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
internal fun DrawScope.drawGoalNet(centerX: Float, edgeY: Float, halfWidth: Float, depth: Float, scale: Float) {
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
