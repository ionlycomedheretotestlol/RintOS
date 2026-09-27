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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.StartupStyle
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinParams
import dev.rint.launcher.mascot.RinRig
import dev.rint.launcher.mascot.animateRin
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

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

    const val DURATION_MS = 4600
}

/** Shows the startup screen (after a boot, or from Settings → Preview). */
object StartupPreview {
    var show by mutableStateOf(false)
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
    val out = ((p - 0.9f) / 0.1f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().background(Color.Black).graphicsLayer {
        alpha = 1f - out
        val s = 1f + 0.08f * out * out
        scaleX = s; scaleY = s
    }) {
        when (style) {
            StartupStyle.RINTOS -> PixelLogo(p * 4.6f, Modifier.fillMaxSize(), withWordmark = true)
            StartupStyle.MINIMAL -> PixelLogo(p * 4.6f * 1.25f, Modifier.fillMaxSize(), withWordmark = false)
            StartupStyle.TERMINAL -> TerminalStartup(p)
            StartupStyle.PIXEL -> PixelWalkStartup(p)
        }
    }
}

private fun clamp(v: Float) = v.coerceIn(0f, 1f)
private fun easeOut(v: Float) = 1f - (1f - clamp(v)).let { it * it * it }
private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; val p = clamp(x) - 1f; return 1f + c3 * p * p * p + c1 * p * p }

/** Rin's head as pixels, precomputed for the reveal: colour, and where each pixel sits. */
private class HeadPixels(accent: Int) {
    val w = RinRig.HEAD_W
    val h = RinRig.HEAD_H
    val color = IntArray(w * h)
    val outline = BooleanArray(w * h)
    val order = FloatArray(w * h)      // 0..1: when this pixel appears (outline: angle; fill: distance)
    init {
        val rig = RinRig().also { it.accent = accent }
        val p = RinParams()
        animateRin(Pose.HEAD, 0.3f, 0f, p)
        p.eyeOpen = 1f
        rig.render(p)
        var maxD = 1f
        val cx = w / 2f; val cy = h * 0.55f
        for (y in 0 until h) for (x in 0 until w) {
            val c = rig.pixels[(y + RinRig.HEAD_Y) * RinRig.W + x + RinRig.HEAD_X]
            val i = y * w + x
            color[i] = c
            if ((c ushr 24) == 0) continue
            // an outline pixel is one that touches empty space
            outline[i] = listOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1).any { (xx, yy) ->
                xx !in 0 until w || yy !in 0 until h || (rig.pixels[(yy + RinRig.HEAD_Y) * RinRig.W + xx + RinRig.HEAD_X] ushr 24) == 0
            }
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            if (d > maxD) maxD = d
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if ((color[i] ushr 24) == 0) continue
            if (outline[i]) {
                // clockwise from 12 o'clock
                var a = atan2(x - cx, -(y - cy)) / (2 * Math.PI.toFloat())
                if (a < 0) a += 1f
                order[i] = a
            } else {
                order[i] = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / maxD
            }
        }
    }
}

private fun mix(a: Int, b: Int, k: Float): Int {
    val t = clamp(k)
    fun ch(s: Int) = ((((a shr s) and 0xff) * (1 - t) + ((b shr s) and 0xff) * t).toInt() and 0xff) shl s
    return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
}

/**
 * The RintOS logo reveal, at [s] seconds: a blinking pixel, Rin's head drawn as a glowing pixel
 * outline, filled by a ripple, a blink, the wordmark dropping in letter by letter, a loading bar.
 */
@Composable
fun PixelLogo(s: Float, modifier: Modifier = Modifier, withWordmark: Boolean = true, finished: Boolean = false) {
    val look = LocalRint.current
    val accent = look.colors.accent
    val head = remember(accent) { HeadPixels(accent.toArgb()) }
    val live = remember { RinRig() }
    val liveParams = remember { RinParams() }
    val bmp = remember { android.graphics.Bitmap.createBitmap(head.w, head.h, android.graphics.Bitmap.Config.ARGB_8888) }
    val image = remember(bmp) { bmp.asImageBitmap() }
    val buf = remember { IntArray(head.w * head.h) }
    val t = if (finished) 99f else s
    val white = 0xFFFFFFFF.toInt()
    val acc = accent.toArgb()

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.42f)
            // dot grid with a shockwave when the face fills in
            val step = 22.dp.toPx()
            val wave = (t - 1.55f) * size.maxDimension * 0.9f
            var y = (c.y % step)
            while (y < size.height) {
                var x = (c.x % step)
                while (x < size.width) {
                    val d = sqrt((x - c.x) * (x - c.x) + (y - c.y) * (y - c.y))
                    val ring = if (t > 1.55f) exp(-((d - wave) / 60f).let { it * it }) else 0f
                    val a = (0.05f + 0.5f * ring) * clamp(t / 0.6f)
                    drawCircle(if (ring > 0.2f) accent else Color.White, 1.3.dp.toPx() + ring * 1.5.dp.toPx(), Offset(x, y), alpha = a)
                    x += step
                }
                y += step
            }
            // a glow that breathes in behind Rin
            val g = easeOut((t - 0.4f) / 1.4f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f * g), Color.Transparent), c, size.minDimension * 0.55f), size.minDimension * 0.55f, c)

            // ── the head
            val px = (size.minDimension * 0.46f) / head.w
            val ox = c.x - head.w * px / 2; val oy = c.y - head.h * px / 2
            if (t < 0.45f) {
                // a single cursor pixel, blinking
                if ((t * 6).toInt() % 2 == 0) drawRect(accent, Offset(c.x - px / 2, c.y - px / 2), Size(px, px))
            } else if (t < 2.2f && !finished) {
                val outlineP = (t - 0.45f) / 0.7f           // outline sweep
                val fillP = (t - 1.1f) / 0.55f              // fill ripple
                for (yy in 0 until head.h) for (xx in 0 until head.w) {
                    val i = yy * head.w + xx
                    val col = head.color[i]
                    if ((col ushr 24) == 0) continue
                    val o = head.order[i]
                    val shownAt: Float; val k: Float
                    if (head.outline[i]) { shownAt = o; k = outlineP } else { shownAt = o; k = fillP }
                    if (k < shownAt) continue
                    val age = (k - shownAt) * 4f
                    val finalCol = if (head.outline[i] && t < 1.6f) acc else col
                    val hot = mix(white, acc, clamp(age * 1.5f))
                    val cc = if (age < 1f) mix(hot, finalCol, age) else finalCol
                    val pop = if (age < 0.5f) 1f + 0.5f * (1f - age * 2f) else 1f
                    val sz = px * pop
                    drawRect(Color(cc), Offset(ox + xx * px + (px - sz) / 2, oy + yy * px + (px - sz) / 2), Size(sz + 0.5f, sz + 0.5f))
                }
            }
        }
        // after the reveal: the live Rin head (he blinks awake, then winks)
        if (t >= 2.2f || finished) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height * 0.42f)
                val px = (size.minDimension * 0.46f) / head.w
                val lt = t - 2.2f
                val pose = if (finished || lt < 1.4f) Pose.HEAD else Pose.WINK
                animateRin(pose, 0.3f + lt, 0f, liveParams)
                // eyes open from a blink as he "wakes"
                if (!finished && lt < 0.35f) liveParams.eyeOpen = (lt / 0.35f)
                liveParams.stance = dev.rint.launcher.mascot.Stance.HEAD; liveParams.headY = 0f
                live.accent = acc
                live.render(liveParams)
                for (yy in 0 until head.h) for (xx in 0 until head.w) buf[yy * head.w + xx] = live.pixels[(yy + RinRig.HEAD_Y) * RinRig.W + xx + RinRig.HEAD_X]
                bmp.setPixels(buf, 0, head.w, 0, 0, head.w, head.h)
                val w = (head.w * px).toInt(); val h = (head.h * px).toInt()
                drawImage(image, dstOffset = IntOffset((c.x - w / 2).toInt(), (c.y - h / 2).toInt()), dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
            }
        }
        if (withWordmark) Wordmark(t, finished, Modifier.align(Alignment.Center).padding(top = 300.dp))
    }
}

@Composable
private fun Wordmark(t: Float, finished: Boolean, modifier: Modifier) {
    val look = LocalRint.current
    val letters = "RintOS"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            letters.forEachIndexed { i, ch ->
                val k = if (finished) 1f else easeOutBack((t - 2.35f - i * 0.07f) / 0.35f)
                val a = if (finished) 1f else clamp((t - 2.35f - i * 0.07f) / 0.12f)
                androidx.compose.material3.Text(
                    ch.toString(), fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 44.sp,
                    color = if (i >= 4) look.colors.accent else Color.White,
                    modifier = Modifier.graphicsLayer { alpha = a; translationY = (1f - k) * -60f },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        // a pixel loading bar
        val barP = if (finished) 1f else clamp((t - 2.7f) / 1.3f)
        Canvas(Modifier.size(160.dp, 8.dp).graphicsLayer { alpha = if (finished) 0f else clamp((t - 2.6f) / 0.2f) }) {
            val n = 16
            val cw = size.width / n
            for (i in 0 until n) {
                val on = i < (barP * n).toInt()
                drawRect(if (on) look.colors.accent else Color.White.copy(alpha = 0.12f), Offset(i * cw + 1, 0f), Size(cw - 2, size.height))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("1.4", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.graphicsLayer { alpha = if (finished) 1f else clamp((t - 2.9f) / 0.3f) })
    }
}

// ─────────────────────────── terminal ───────────────────────────

private val bootLog = listOf(
    "[ 0.004] rintos 1.4 · kernel says hi",
    "[ 0.118] mounting /home ........ ok",
    "[ 0.241] waking up rin ......... ok",
    "[ 0.379] loading wallpaper ..... ok",
    "[ 0.512] polishing icons ....... ok",
    "[ 0.640] notch: in position .... ok",
    "[ 0.771] music: standing by .... ok",
    "[ 0.905] welcome back.",
)

@Composable
private fun TerminalStartup(p: Float) {
    val look = LocalRint.current
    val head = remember(look.colors.accent) { HeadPixels(look.colors.accent.toArgb()) }
    val green = Color(0xFF9CFFB9)
    val t = p * 4.6f
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
        Text("RINTOS BIOS v1.4  ·  (c) Carrot", fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = look.colors.accent)
        val mem = (clamp(t / 0.8f) * 8192).toInt()
        Text("memory test: $mem MB ${if (mem >= 8192) "OK" else ""}", fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = green)
        Spacer(Modifier.height(12.dp))
        // Rin's head as block characters
        if (t > 0.8f) {
            val rows = ((t - 0.8f) / 0.6f * head.h).toInt().coerceIn(0, head.h)
            for (y in 0 until rows step 2) {
                val line = buildString {
                    for (x in 0 until head.w) {
                        val top = head.color[y * head.w + x]; val bot = if (y + 1 < head.h) head.color[(y + 1) * head.w + x] else 0
                        val a = (top ushr 24) != 0 && (top and 0xffffff) > 0x404040
                        val b = (bot ushr 24) != 0 && (bot and 0xffffff) > 0x404040
                        append(if (a && b) '█' else if (a) '▀' else if (b) '▄' else ' ')
                    }
                }
                androidx.compose.material3.Text(line, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 7.sp, lineHeight = 7.sp, color = green)
            }
        }
        Spacer(Modifier.height(12.dp))
        val shown = ((t - 1.6f) / 2.2f * bootLog.size).toInt().coerceIn(0, bootLog.size)
        bootLog.take(shown).forEach { Text(it, fontFamily = RintFonts.Terminal, fontSize = 17.sp, color = green) }
        if ((t * 3).toInt() % 2 == 0) Text("█", fontFamily = RintFonts.Terminal, fontSize = 17.sp, color = look.colors.accent)
    }
}

// ─────────────────────────── pixel walk ───────────────────────────

/** Rin walks through the 8-bit world at dawn and waves. */
@Composable
private fun PixelWalkStartup(p: Float) {
    val look = LocalRint.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vw = 180
        val vh = (vw * (maxHeight / maxWidth)).toInt().coerceAtLeast(200)
        val world = remember(vh, look.colors.accent) { PixelWorld(vw, vh, look.colors.accent.toArgb()) }
        val bmp = remember(world) { android.graphics.Bitmap.createBitmap(vw, vh, android.graphics.Bitmap.Config.ARGB_8888) }
        val img = remember(bmp) { bmp.asImageBitmap() }
        // map the startup's 4.6 s onto the film's dawn + first steps
        val ft = 1.8f + p * 4.2f
        Canvas(Modifier.fillMaxSize()) {
            world.draw(ft)
            bmp.setPixels(world.pixels, 0, vw, 0, 0, vw, vh)
            drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.None)
        }
        Column(Modifier.align(Alignment.TopCenter).padding(top = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RINTOS", fontFamily = RintFonts.Pixel, fontSize = 30.sp, color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = clamp((p - 0.15f) / 0.1f) })
            Text("PRESS ANY BUTTON", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = if ((p * 12).toInt() % 2 == 0 && p > 0.35f) 1f else 0f })
        }
    }
}
