package com.passwall.tv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun PlayIcon(size: Dp = 36.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val path = Path().apply {
            moveTo(size.toPx() * 0.28f, size.toPx() * 0.18f)
            lineTo(size.toPx() * 0.82f, size.toPx() * 0.50f)
            lineTo(size.toPx() * 0.28f, size.toPx() * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun StopIcon(size: Dp = 32.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val pad = size.toPx() * 0.22f
        drawRoundRect(
            color = color,
            topLeft = Offset(pad, pad),
            size = Size(size.toPx() - pad * 2, size.toPx() - pad * 2),
            cornerRadius = CornerRadius(4f, 4f),
        )
    }
}

@Composable
fun GearIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * 0.22f
        drawCircle(color, r, c, style = Stroke(width = this.size.minDimension * 0.12f))
        val outer = this.size.minDimension * 0.42f
        for (i in 0 until 6) {
            val a = Math.toRadians((i * 60).toDouble())
            drawLine(
                color,
                Offset(c.x + (r * 1.1f * Math.cos(a)).toFloat(), c.y + (r * 1.1f * Math.sin(a)).toFloat()),
                Offset(c.x + (outer * Math.cos(a)).toFloat(), c.y + (outer * Math.sin(a)).toFloat()),
                strokeWidth = this.size.minDimension * 0.14f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun BackArrowIcon(size: Dp = 26.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.62f, h * 0.18f)
            lineTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.82f)
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

@Composable
fun PencilIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.72f)
            lineTo(this@Canvas.size.width * 0.70f, this@Canvas.size.height * 0.24f)
            lineTo(this@Canvas.size.width * 0.80f, this@Canvas.size.height * 0.34f)
            lineTo(this@Canvas.size.width * 0.32f, this@Canvas.size.height * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun GlobeIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val r = this.size.minDimension * 0.38f
        drawCircle(color, r, c, style = Stroke(width = 3.4f))
        drawOval(
            color,
            topLeft = Offset(c.x - r * 0.45f, c.y - r),
            size = Size(r * 0.9f, r * 2),
            style = Stroke(width = 3f),
        )
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 3f)
    }
}

@Composable
fun CheckIcon(size: Dp = 22.dp, color: Color = Color(0xFF3B82F6)) {
    Canvas(Modifier.size(size)) {
        val path = Path().apply {
            moveTo(this@Canvas.size.width * 0.18f, this@Canvas.size.height * 0.52f)
            lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.76f)
            lineTo(this@Canvas.size.width * 0.84f, this@Canvas.size.height * 0.24f)
        }
        drawPath(path, color, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
    }
}
