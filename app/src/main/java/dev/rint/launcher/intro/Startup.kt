package dev.rint.launcher.intro

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.StartupStyle
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * RintOS's startup screen: plays once right after the phone boots, before home appears.
 * It never touches the system's real boot animation (that needs root and can brick a phone),
 * so it can't break anything: it's just the launcher's first few seconds.
 */
object Startup {
    private const val PREFS = "rint_startup"

    /** Android's boot counter changes every restart; if it's new, it's the first launch since boot. */
    fun isFirstSinceBoot(ctx: Context): Boolean {
        val boot = runCatching { Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull() ?: return false
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = p.getInt("boot", -1)
        if (seen == boot) return false
        p.edit().putInt("boot", boot).apply()
        return seen != -1   // the very first install shows the intro instead
    }

    const val DURATION_MS = 3600
}

/** Shows the startup screen (after a boot, or from Settings → Preview). */
object StartupPreview {
    var show by androidx.compose.runtime.mutableStateOf(false)
}

@Composable
fun StartupScreen(style: StartupStyle, onDone: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(Startup.DURATION_MS, easing = LinearEasing))
        onDone()
    }
    StartupFrame(style, t.value, Modifier.clickable(remember { MutableInteractionSource() }, null) { onDone() })
}

/** One frame of the startup screen at progress [p] (0..1). */
@Composable
fun StartupFrame(style: StartupStyle, p: Float, modifier: Modifier = Modifier) {
    val fadeOut = ((p - 0.88f) / 0.12f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().background(Color.Black).graphicsLayer { alpha = 1f - fadeOut }) {
        when (style) {
            StartupStyle.RINTOS -> RintOsStartup(p)
            StartupStyle.MINIMAL -> MinimalStartup(p)
            StartupStyle.TERMINAL -> TerminalStartup(p)
            StartupStyle.PIXEL -> PixelStartup(p)
        }
    }
}

private fun clamp(v: Float) = v.coerceIn(0f, 1f)
private fun easeOut(v: Float) = 1f - (1f - clamp(v)).let { it * it * it }

/** The default: sparks of your accent color swirl in, form a ring, Rin appears, the wordmark shines. */
@Composable
private fun RintOsStartup(p: Float) {
    val look = LocalRint.current
    val accent = look.colors.accent
    val sparks = remember { val r = Random(5); List(90) { Triple(r.nextFloat() * 2f * PI.toFloat(), 0.6f + r.nextFloat() * 0.9f, r.nextFloat()) } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.44f)
            val ringR = size.minDimension * 0.23f
            // background glow breathes up
            val g = easeOut(p / 0.5f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f * g), Color.Transparent), c, size.maxDimension * 0.6f), size.maxDimension * 0.6f, c)
            // sparks spiral inward and land on the ring
            val gather = clamp(p / 0.42f)
            sparks.forEach { (a0, dist, delay) ->
                val k = easeOut((gather - delay * 0.35f) / 0.65f)
                val ang = a0 + (1f - k) * 3.2f + p * 0.8f
                val r = ringR * (1f + (dist * 3f) * (1f - k))
                val pos = c + Offset(cos(ang) * r, sin(ang) * r)
                drawCircle(accent.copy(alpha = (0.25f + 0.75f * k) * (1f - clamp((p - 0.55f) / 0.15f) * 0.6f)), 3.5f + 3f * (1f - k), pos)
            }
            // the ring draws itself around Rin
            val ring = clamp((p - 0.35f) / 0.25f)
            if (ring > 0f) drawArc(
                accent, -90f, 360f * easeOut(ring), false, c - Offset(ringR, ringR), Size(ringR * 2, ringR * 2),
                style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
            )
            // a pulse ring when it completes
            val pulse = clamp((p - 0.6f) / 0.25f)
            if (pulse in 0.001f..0.999f) drawCircle(accent.copy(alpha = 1f - pulse), ringR * (1f + pulse * 0.8f), c, style = Stroke(3.dp.toPx()))
        }
        val rin = easeOut((p - 0.45f) / 0.2f)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(200.dp).graphicsLayer { alpha = rin; scaleX = 0.6f + 0.4f * rin; scaleY = scaleX }, contentAlignment = Alignment.Center) {
                RinSprite(if (p > 0.72f) Pose.HAPPY else Pose.HEAD, 120.dp)
            }
            Spacer(Modifier.height(56.dp))
            val word = easeOut((p - 0.6f) / 0.2f)
            Box(Modifier.graphicsLayer { alpha = word; translationY = (1f - word) * 30f }) {
                Text("RintOS", fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = RintFonts.Inter)
                // a shine sweeps across the wordmark
                val sweep = clamp((p - 0.72f) / 0.18f)
                if (sweep in 0.001f..0.999f) Text(
                    "RintOS", fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = RintFonts.Inter,
                    style = TextStyle(brush = Brush.linearGradient(
                        listOf(Color.Transparent, accent, Color.White, accent, Color.Transparent),
                        start = Offset(-200f + sweep * 800f, 0f), end = Offset(sweep * 800f, 60f),
                    )),
                )
            }
            Text("1.4", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = accent, modifier = Modifier.graphicsLayer { alpha = word })
        }
    }
}

@Composable
private fun MinimalStartup(p: Float) {
    val look = LocalRint.current
    val a = easeOut(p / 0.35f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Box(Modifier.graphicsLayer { alpha = a; scaleX = 0.9f + 0.1f * a; scaleY = scaleX }) { RinSprite(Pose.HEAD, 96.dp) }
        Spacer(Modifier.height(28.dp))
        Canvas(Modifier.size(140.dp, 4.dp)) {
            drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
            drawRoundRect(look.colors.accent, size = Size(size.width * easeOut(p / 0.85f), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        }
    }
}

private val bootLines = listOf(
    "[ ok ] kernel says hi", "[ ok ] waking up Rin", "[ ok ] loading your wallpaper", "[ ok ] icons: polished",
    "[ ok ] widgets: stretching", "[ ok ] notch: in position", "[ ok ] music: ready when you are", "[ ok ] welcome back.",
)

@Composable
private fun TerminalStartup(p: Float) {
    val look = LocalRint.current
    val shown = (p / 0.8f * bootLines.size).toInt().coerceIn(0, bootLines.size)
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text("rintos 1.4 · boot", fontFamily = RintFonts.Terminal, fontSize = 26.sp, color = look.colors.accent)
        Spacer(Modifier.height(16.dp))
        bootLines.take(shown).forEach { Text(it, fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = Color(0xFFB8FFCC)) }
        if (shown < bootLines.size || (p * 6).toInt() % 2 == 0) Text("█", fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = look.colors.accent)
    }
}

@Composable
private fun PixelStartup(p: Float) {
    val look = LocalRint.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // pixel ground and a loading bar made of blocks
            val block = 12.dp.toPx()
            val y = size.height * 0.62f
            var x = 0f
            while (x < size.width) { drawRect(Color.White.copy(alpha = 0.1f), Offset(x, y), Size(block - 2f, block - 2f)); x += block }
            val n = 14
            val bw = n * block
            val bx = (size.width - bw) / 2
            for (i in 0 until n) {
                val on = i < (p / 0.85f * n).toInt()
                drawRect(if (on) look.colors.accent else Color.White.copy(alpha = 0.12f), Offset(bx + i * block, y + 60.dp.toPx()), Size(block - 3f, block - 3f))
            }
        }
        // Rin walks across the ground, then turns and waves
        Box(Modifier.fillMaxSize()) {
            val walk = clamp(p / 0.7f)
            RinSprite(
                if (p < 0.7f) Pose.WALK else Pose.WAVE, 90.dp,
                Modifier.align(Alignment.TopStart).graphicsLayer {
                    translationX = -250f + (size.width + 500f) * 0f + walk * 900f
                    translationY = 0f
                }.padding(top = 440.dp),
                forcePixel = true,
            )
        }
        Text("RINTOS", fontFamily = RintFonts.Pixel, fontSize = 22.sp, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(bottom = 260.dp))
    }
}
