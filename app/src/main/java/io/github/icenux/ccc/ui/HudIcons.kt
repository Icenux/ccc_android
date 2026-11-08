package io.github.icenux.ccc.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The HUD's thin outline icons (the Material core set has none of these). 24 × 24, tinted by the Icon. */
object HudIcons {
    private fun icon(name: String, width: Float = 1.8f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.box(l: Float, t: Float, r: Float, b: Float) {
        moveTo(l, t); lineTo(r, t); lineTo(r, b); lineTo(l, b); close()
    }

    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y); lineTo(x + 0.01f, y)
    }

    val Dashboard = icon("Dashboard") {
        box(4f, 4f, 10f, 10f); box(14f, 4f, 20f, 10f); box(4f, 14f, 10f, 20f); box(14f, 14f, 20f, 20f)
    }

    val Chats = icon("Chats") {
        moveTo(4f, 5f); lineTo(16f, 5f); lineTo(16f, 13f); lineTo(9f, 13f); lineTo(6f, 16f); lineTo(6f, 13f); lineTo(4f, 13f); close()
        moveTo(19f, 9f); lineTo(20f, 9f); lineTo(20f, 17f); lineTo(18f, 17f); lineTo(18f, 20f); lineTo(15f, 17f); lineTo(11f, 17f); lineTo(11f, 15f)
    }

    val Tasks = icon("Tasks") {
        moveTo(3.5f, 7f); lineTo(5f, 8.5f); lineTo(8f, 5.5f)
        moveTo(11f, 7f); lineTo(21f, 7f)
        moveTo(3.5f, 16f); lineTo(5f, 17.5f); lineTo(8f, 14.5f)
        moveTo(11f, 16f); lineTo(21f, 16f)
    }

    val More = icon("More", width = 2.6f) { dot(5f, 12f); dot(12f, 12f); dot(19f, 12f) }

    val Shield = icon("Shield") {
        moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11f)
        curveTo(19f, 16f, 16f, 19.5f, 12f, 21f)
        curveTo(8f, 19.5f, 5f, 16f, 5f, 11f)
        lineTo(5f, 6f); close()
        moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
    }

    val Routines = icon("Routines") {
        box(4f, 6f, 20f, 20f)
        moveTo(4f, 10f); lineTo(20f, 10f)
        moveTo(8f, 3f); lineTo(8f, 7f)
        moveTo(16f, 3f); lineTo(16f, 7f)
        moveTo(9f, 15f); lineTo(11f, 17f); lineTo(15f, 13f)
    }

    /** A helper: a small head with an antenna (the Claude Code agents). */
    val Helper = icon("Helper") {
        box(5f, 8f, 19f, 20f)
        moveTo(12f, 8f); lineTo(12f, 4f)
        dot(12f, 3.5f)
        dot(9f, 13f); dot(15f, 13f)
        moveTo(9f, 17f); lineTo(15f, 17f)
    }

    val Live = icon("Live") {
        dot(12f, 12f)
        moveTo(8.5f, 8.5f); curveTo(6.5f, 10.5f, 6.5f, 13.5f, 8.5f, 15.5f)
        moveTo(15.5f, 8.5f); curveTo(17.5f, 10.5f, 17.5f, 13.5f, 15.5f, 15.5f)
        moveTo(5.5f, 5.5f); curveTo(2f, 9f, 2f, 15f, 5.5f, 18.5f)
        moveTo(18.5f, 5.5f); curveTo(22f, 9f, 22f, 15f, 18.5f, 18.5f)
    }

    val Analytics = icon("Analytics", width = 3f) {
        moveTo(6f, 20f); lineTo(6f, 13f)
        moveTo(12f, 20f); lineTo(12f, 5f)
        moveTo(18f, 20f); lineTo(18f, 10f)
    }

    val Sliders = icon("Sliders") {
        moveTo(4f, 7f); lineTo(13f, 7f); moveTo(17f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(7f, 17f); moveTo(11f, 17f); lineTo(20f, 17f)
        box(13f, 5f, 17f, 9f); box(7f, 15f, 11f, 19f)
    }

    val Archive = icon("Archive") {
        box(3f, 4f, 21f, 9f)
        moveTo(5f, 9f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 9f)
        moveTo(10f, 13f); lineTo(14f, 13f)
    }

    val Clock = icon("Clock") {
        moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); close()
        moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
    }

    val Check = icon("Check", width = 2.2f) { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }

    val Spinner = icon("Spinner", width = 2.2f) {
        moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f)
        moveTo(21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        moveTo(12f, 21f); curveTo(7f, 21f, 3f, 17f, 3f, 12f)
    }

    val Stop = icon("Stop") {
        moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); close()
        box(9f, 9f, 15f, 15f)
    }

    val Handle = icon("Handle", width = 2f) {
        moveTo(7f, 8f); lineTo(11f, 12f); lineTo(7f, 16f)
        moveTo(13f, 8f); lineTo(17f, 12f); lineTo(13f, 16f)
    }
}
