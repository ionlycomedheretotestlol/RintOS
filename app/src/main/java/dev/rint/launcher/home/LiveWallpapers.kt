package dev.rint.launcher.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.ui.rememberAmbientClock
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Built-in live wallpapers, drawn in code in your accent color. They run on the ambient clock,
 * so they freeze whenever home is covered (apps, drawer, settings) and cost nothing then.
 */
@Composable
fun LiveWallpaper(mode: WallpaperMode, accent: Color, running: Boolean = true) {
    val t = rememberAmbientClock(running)
    when (mode) {
        WallpaperMode.STARFIELD -> Starfield(t, accent)
        WallpaperMode.RAIN -> Rain(t, accent)
        else -> Waves(t, accent)
    }
}

private class Star(val x: Float, val y: Float, val z: Float, val tw: Float)

@Composable
private fun Starfield(t: Float, accent: Color) {
    val stars = remember { val r = Random(7); List(220) { Star(r.nextFloat(), r.nextFloat(), 0.2f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02030A), lerp(Color(0xFF070B22), accent, 0.18f), Color(0xFF0A0616))))
        // a faint nebula in the accent color
        val c = Offset(size.width * (0.5f + 0.1f * sin(t * 0.05f)), size.height * 0.35f)
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), c, size.maxDimension * 0.55f), size.maxDimension * 0.55f, c)
        stars.forEach { s ->
            val y = ((s.y + t * 0.004f * s.z) % 1f) * size.height
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * (1.2f + s.z) + s.tw))) * s.z
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), 1.2f + s.z * 2.2f, Offset(s.x * size.width, y))
        }
        // a shooting star every ~7 seconds
        val cyc = t / 7f
        val ph = cyc - kotlin.math.floor(cyc)
        if (ph < 0.12f) {
            val k = ph / 0.12f
            val r = Random(kotlin.math.floor(cyc).toInt())
            val sx = size.width * (0.2f + 0.7f * r.nextFloat()); val sy = size.height * 0.1f + size.height * 0.3f * r.nextFloat()
            val head = Offset(sx - 700f * k, sy + 380f * k)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White), head + Offset(260f, -140f), head), head + Offset(260f, -140f), head, 3f, StrokeCap.Round, alpha = 1f - k)
        }
    }
}

private class Drop(val x: Float, val y: Float, val speed: Float, val len: Float)

@Composable
private fun Rain(t: Float, accent: Color) {
    val drops = remember { val r = Random(3); List(170) { Drop(r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 0.6f, 30f + r.nextFloat() * 70f) } }
    val beads = remember { val r = Random(11); List(60) { Offset(r.nextFloat(), r.nextFloat()) to (4f + r.nextFloat() * 10f) } }
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B1220), lerp(Color(0xFF14203A), accent, 0.2f), Color(0xFF070A12))))
        // blurry city lights behind the glass
        for (i in 0 until 14) {
            val r = Random(i * 31)
            val c = Offset(size.width * r.nextFloat(), size.height * (0.55f + 0.4f * r.nextFloat()))
            val col = if (i % 3 == 0) accent else Color(0xFFFFC46B)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.28f), Color.Transparent), c, 90f + 60f * r.nextFloat()), 150f, c)
        }
        drops.forEach { d ->
            val y = ((d.y + t * d.speed) % 1.1f) * size.height - d.len
            val x = d.x * size.width + y * 0.06f
            drawLine(Color.White.copy(alpha = 0.10f + 0.18f * d.speed), Offset(x, y), Offset(x + d.len * 0.06f, y + d.len), 1.6f, StrokeCap.Round)
        }
        // water beads sliding slowly down the glass
        beads.forEach { (p, r) ->
            val y = ((p.y + t * 0.004f * r / 8f) % 1f) * size.height
            drawCircle(Color.White.copy(alpha = 0.10f), r, Offset(p.x * size.width, y))
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.3f, Offset(p.x * size.width - r * 0.3f, y - r * 0.3f))
        }
    }
}

@Composable
private fun Waves(t: Float, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF060914), lerp(Color(0xFF0C1330), accent, 0.12f))))
        for (layer in 0 until 5) {
            val base = size.height * (0.45f + layer * 0.11f)
            val amp = 60f - layer * 7f
            val speed = 0.25f + layer * 0.12f
            val path = Path().apply {
                moveTo(0f, size.height)
                var x = 0f
                while (x <= size.width + 20f) {
                    val y = base + amp * sin(x / size.width * 2f * PI.toFloat() * (1.2f + layer * 0.3f) + t * speed + layer) +
                        amp * 0.4f * sin(x / size.width * 7f + t * speed * 1.7f)
                    lineTo(x, y); x += 16f
                }
                lineTo(size.width, size.height); close()
            }
            val col = lerp(accent, Color(0xFF0A0E1E), 0.35f + layer * 0.14f)
            drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.85f), col.copy(alpha = 1f)), startY = base - amp, endY = size.height))
        }
    }
}
