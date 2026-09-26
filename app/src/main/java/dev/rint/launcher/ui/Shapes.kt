package dev.rint.launcher.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import dev.rint.launcher.core.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

object Shapes {
    val Squircle: Shape = GenericShape { size, _ ->
        val n = 4.2
        val a = size.width / 2f
        val b = size.height / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2 / n)
            val y = b + b * sign(s) * abs(s).pow(2 / n)
            if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
        }
        close()
    }

    val Hexagon: Shape = GenericShape { size, _ ->
        val r = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rr = r * 0.16f
        val pts = (0 until 6).map {
            val ang = PI / 3 * it - PI / 2
            Offset(cx + r * cos(ang).toFloat(), cy + r * sin(ang).toFloat())
        }
        for (i in pts.indices) {
            val p = pts[i]
            val prev = pts[(i + 5) % 6]
            val next = pts[(i + 1) % 6]
            val a = p + (prev - p) * (rr / r)
            val b = p + (next - p) * (rr / r)
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(p.x, p.y, b.x, b.y)
        }
        close()
    }

    val Pebble: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        moveTo(w * 0.52f, 0f)
        cubicTo(w * 0.86f, 0f, w, h * 0.16f, w, h * 0.48f)
        cubicTo(w, h * 0.82f, w * 0.84f, h, w * 0.5f, h)
        cubicTo(w * 0.16f, h, 0f, h * 0.84f, 0f, h * 0.54f)
        cubicTo(0f, h * 0.18f, w * 0.2f, 0f, w * 0.52f, 0f)
        close()
    }

    val Clover: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val r = w * 0.29f
        listOf(Offset(w * 0.3f, h * 0.3f), Offset(w * 0.7f, h * 0.3f), Offset(w * 0.3f, h * 0.7f), Offset(w * 0.7f, h * 0.7f))
            .forEach { addOval(Rect(it, r)) }
        addRect(Rect(w * 0.2f, h * 0.2f, w * 0.8f, h * 0.8f))
    }

    val Diamond: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val k = 0.14f
        moveTo(w / 2 - w * k, h * k)
        quadraticTo(w / 2, 0f, w / 2 + w * k, h * k)
        lineTo(w - w * k, h / 2 - h * k)
        quadraticTo(w, h / 2, w - w * k, h / 2 + h * k)
        lineTo(w / 2 + w * k, h - h * k)
        quadraticTo(w / 2, h, w / 2 - w * k, h - h * k)
        lineTo(w * k, h / 2 + h * k)
        quadraticTo(0f, h / 2, w * k, h / 2 - h * k)
        close()
    }

    fun icon(s: IconShape): Shape = when (s) {
        IconShape.SQUIRCLE -> Squircle
        IconShape.CIRCLE -> CircleShape
        IconShape.ROUNDED -> RoundedCornerShape(24)
        IconShape.SQUARE -> RoundedCornerShape(8)
        IconShape.TEARDROP -> RoundedCornerShape(50, 50, 16, 50)
        IconShape.HEXAGON -> Hexagon
        IconShape.PEBBLE -> Pebble
        IconShape.CLOVER -> Clover
        IconShape.DIAMOND -> Diamond
        IconShape.SYSTEM -> Squircle
    }
}
