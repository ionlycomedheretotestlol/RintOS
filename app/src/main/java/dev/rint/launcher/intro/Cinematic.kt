package dev.rint.launcher.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.IconStyle
import dev.rint.launcher.core.MonoBackground
import dev.rint.launcher.core.RINT_BLUE
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.mascot.SpeechBubble
import dev.rint.launcher.settings.Schema
import dev.rint.launcher.ui.BlockFont
import dev.rint.launcher.ui.RintFonts
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val BAR = IntroSynth.BAR.toFloat()
private val BEAT = IntroSynth.BEAT.toFloat()
private val BLUE = Color(RINT_BLUE.toInt())

private fun bars(b: Int) = b * BAR
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - clamp01(x)).let { it * it * it }
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = clamp01(x) - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

/** The scripted 24-bar opening. Calls [onDone] when it ends (or on skip). */
@Composable
fun IntroCinematic(synth: IntroSynth, onDone: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                val audio = synth.seconds().toFloat()
                val wall = (now - start) / 1e9f
                t = if (audio > 0.05f || wall < 0.6f) audio else wall
            }
            if (t >= IntroSynth.TIMELINE_SECONDS) break
        }
        onDone()
    }
    val beatPhase = (t / BEAT) - floor(t / BEAT)
    val kick = exp(-beatPhase * 7f)

    Box(Modifier.fillMaxSize().background(Color(0xFF03050B))) {
        when {
            t < bars(2) -> BootScene(t)
            t < bars(4) -> WordmarkScene(t - bars(2), kick)
            t < bars(8) -> TaglineScene(t - bars(4), kick)
            t < bars(16) -> MontageScene(t - bars(8), kick)
            t < bars(20) -> BarrageScene(t - bars(16), kick)
            else -> RinScene(t - bars(20))
        }
        // flash into the breakdown
        val flash = clamp01(1f - (t - bars(20)) / 0.7f).takeIf { t >= bars(20) } ?: 0f
        if (flash > 0f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash)))
        // film progress hairline
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(clamp01(t / IntroSynth.TIMELINE_SECONDS.toFloat())).height(2.dp).background(BLUE.copy(alpha = 0.6f)))
    }
}

@Composable
internal fun BootScene(t: Float) {
    val lines = listOf(0.3f to "> rint --boot", 2.0f to "  loading pixels…", 2.7f to "  loading personality…", 3.5f to "  ok.")
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        lines.forEach { (start, text) ->
            if (t >= start) {
                val typed = ((t - start) / 0.045f).toInt().coerceAtMost(text.length)
                val cursor = if (typed < text.length || (t * 2).toInt() % 2 == 0) "█" else " "
                val last = lines.last { t >= it.first }.second == text
                Text(text.take(typed) + if (last) cursor else "", fontFamily = RintFonts.Terminal, fontSize = 26.sp,
                    color = if (text.trim() == "ok.") BLUE else Color(0xFFB8C4FF))
            }
        }
        if (t < 0.3f && (t * 3).toInt() % 2 == 0) Text("█", fontFamily = RintFonts.Terminal, fontSize = 26.sp, color = Color(0xFFB8C4FF))
    }
}

private class Particle(val cx: Int, val cy: Int, val accent: Boolean, val sx: Float, val sy: Float, val rot: Float, val delay: Float)

@Composable
internal fun WordmarkScene(t: Float, kick: Float) {
    val particles = remember {
        val (a, aw) = BlockFont.cells("RINT")
        val (b, _) = BlockFont.cells("OS")
        val rnd = Random(9)
        (a.map { Triple(it.first, it.second, false) } + b.map { Triple(it.first + aw + 2, it.second, true) })
            .map { (x, y, acc) -> Particle(x, y, acc, rnd.nextFloat() * 2 - 0.5f, rnd.nextFloat() * 2 - 0.5f, rnd.nextFloat() * 720 - 360, rnd.nextFloat() * 0.5f) }
    }
    val totalW = BlockFont.cells("RINT").second + 2 + BlockFont.cells("OS").second
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width * 0.84f / totalW
            val ox = (size.width - totalW * cell) / 2
            val oy = size.height * 0.42f - cell * 2.5f
            val pulse = 1f + 0.05f * kick * clamp01((t - 2.2f) * 2)
            // glow
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.35f * clamp01(t - 1f)), Color.Transparent), Offset(size.width / 2, oy + cell * 2.5f), size.width * 0.8f), size.width * 0.8f, Offset(size.width / 2, oy + cell * 2.5f))
            particles.forEach { p ->
                val k = easeOutBack((t - p.delay) / 1.7f)
                val tx = ox + p.cx * cell
                val ty = oy + p.cy * cell
                val x = p.sx * size.width + (tx - p.sx * size.width) * k
                val y = p.sy * size.height + (ty - p.sy * size.height) * k
                val cx = size.width / 2
                val cy = oy + cell * 2.5f
                val fx = cx + (x - cx) * pulse
                val fy = cy + (y - cy) * pulse
                val s = cell * (0.88f - 0.3f * (1 - clamp01(k)))
                rotateBlock(fx, fy, s, p.rot * (1 - clamp01(k)), if (p.accent) BLUE else Color.White)
            }
        }
        val sub = clamp01((t - 2.1f) / 0.6f)
        Text(
            "a launcher for android. and for you.",
            fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f * sub),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(top = 150.dp).graphicsLayer { translationY = (1 - sub) * 30f },
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateBlock(x: Float, y: Float, s: Float, deg: Float, c: Color) {
    rotate(deg, Offset(x + s / 2, y + s / 2)) {
        drawRoundRect(c, Offset(x, y), Size(s, s), CornerRadius(s * 0.14f))
    }
}

@Composable
internal fun TaglineScene(t: Float, kick: Float) {
    val phrases = listOf(
        listOf("this", "is", "your" to true, "phone."),
        listOf("make", "it", "look", "like" , "you." to true),
        listOf("every.", "single.", "pixel." to true),
        listOf("truly" to true, "yours."),
    )
    val idx = (t / (BAR * 2)).toInt().coerceIn(0, phrases.lastIndex)
    val local = t - idx * BAR * 2
    val words = phrases[idx].map { if (it is Pair<*, *>) (it.first as String) to (it.second as Boolean) else (it as String) to false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * (0.55f + 0.08f * kick)
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.25f + 0.15f * kick), Color.Transparent), center, r), r, center)
        }
        val out = clamp01((local - BAR * 2 + 0.35f) / 0.35f)
        Text(
            buildAnnotatedString {
                words.forEachIndexed { i, (w, acc) ->
                    val appear = clamp01((local - i * BEAT) / 0.25f)
                    withStyle(SpanStyle(color = (if (acc) BLUE else Color.White).copy(alpha = appear))) { append(w) }
                    if (i < words.lastIndex) append(" ")
                }
            },
            fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 46.sp, lineHeight = 50.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(28.dp).graphicsLayer {
                val s = 0.92f + 0.08f * easeOut(local / 0.6f) + 0.02f * kick
                scaleX = s * (1 - out * 0.3f); scaleY = s * (1 - out * 0.3f)
                alpha = 1 - out
            },
        )
    }
}

private val montageCaptions = listOf("every shape.", "every color.", "every clock.", "every pixel.")

private fun montageConfig(edit: Int): RintConfig {
    val b = RintConfig()
    return when (edit) {
        0 -> b.copy(icons = b.icons.copy(shape = IconShape.CIRCLE))
        1 -> b.copy(icons = b.icons.copy(shape = IconShape.HEXAGON))
        2 -> b.copy(icons = b.icons.copy(shape = IconShape.PEBBLE))
        3 -> b.copy(icons = b.icons.copy(shape = IconShape.CLOVER))
        4 -> b.copy(look = b.look.copy(accent = 0xFFFF6FB5L), icons = b.icons.copy(monoFg = 0xFFFF6FB5L))
        5 -> b.copy(look = b.look.copy(accent = 0xFF39FF88L), icons = b.icons.copy(monoFg = 0xFF39FF88L, monoBg = MonoBackground.BLACK))
        6 -> b.copy(look = b.look.copy(accent = 0xFFFF9F1CL), icons = b.icons.copy(monoFg = 0xFFFF9F1CL, shape = IconShape.HEXAGON, monoBg = MonoBackground.BLACK))
        7 -> b.copy(icons = b.icons.copy(monoBg = MonoBackground.ACCENT))
        8 -> b.copy(clock = b.clock.copy(style = ClockStyle.STACKED))
        9 -> b.copy(clock = b.clock.copy(style = ClockStyle.PIXEL, useAccent = true))
        10 -> b.copy(clock = b.clock.copy(style = ClockStyle.WORDS))
        11 -> b.copy(clock = b.clock.copy(style = ClockStyle.ANALOG, seconds = true))
        12 -> b
        13 -> b
        14 -> b.copy(look = b.look.copy(wallpaper = WallpaperMode.MESH), icons = b.icons.copy(style = IconStyle.OUTLINE))
        else -> b.copy(icons = b.icons.copy(style = IconStyle.ORIGINAL))
    }
}

@Composable
internal fun MontageScene(t: Float, kick: Float) {
    val editLen = BEAT * 2
    val edit = (t / editLen).toInt().coerceIn(0, 15)
    val local = t - edit * editLen
    val cfg = remember(edit) { montageConfig(edit) }
    val enter = easeOutBack(t / 0.9f)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phoneW = minOf(maxWidth * 0.62f, 260.dp)
        // caption
        val cap = montageCaptions[edit / 4]
        val capIn = clamp01(((t - (edit / 4) * editLen * 4)) / 0.3f)
        Text(
            cap, fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp).graphicsLayer { alpha = capIn; translationY = (1 - capIn) * -30f },
        )
        Box(
            Modifier.align(Alignment.Center).padding(top = 60.dp).graphicsLayer {
                scaleX = 0.2f + 0.8f * enter; scaleY = scaleX
                rotationY = sin(t * 0.9f) * 9f
                rotationZ = (1 - clamp01(enter)) * -20f
                cameraDistance = 14f * density.density
                val bump = 1f + 0.015f * kick
                scaleX *= bump; scaleY *= bump
            }
        ) {
            val wave = if (edit == 12) (local / editLen) * 4.5f - 0.5f else -1f
            MockPhone(cfg, width = phoneW, dockWave = wave, notchOpen = edit == 13, lyric = if (edit == 13) "your phone, your rules ♪" else null)
            // the "someone customizing" finger
            val target = when (edit / 4) {
                0 -> Offset(0.3f + 0.13f * (edit % 4), 0.72f)
                1 -> Offset(0.5f, 0.35f)
                2 -> Offset(0.45f, 0.2f)
                else -> when (edit) { 12 -> Offset((wave.coerceIn(0f, 3f) + 0.5f) / 4f, 0.93f); 13 -> Offset(0.5f, 0.03f); else -> Offset(0.6f, 0.5f) }
            }
            val press = clamp01(1f - local / 0.35f)
            val fx = with(density) { phoneW.toPx() } * target.x
            val fy = with(density) { (phoneW * 2.05f).toPx() } * target.y
            Box(
                Modifier.offset { IntOffset(fx.roundToInt() - 40, fy.roundToInt() - 40) }.size(28.dp)
                    .graphicsLayer { scaleX = 1f - 0.25f * press; scaleY = scaleX }
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.55f))
            )
            if (press > 0f) Box(
                Modifier.offset { IntOffset(fx.roundToInt() - 40, fy.roundToInt() - 40) }.size(28.dp)
                    .graphicsLayer { val s = 1f + (1 - press) * 2.2f; scaleX = s; scaleY = s; alpha = press }
                    .clip(CircleShape).background(BLUE.copy(alpha = 0.6f))
            )
        }
        Text(
            listOf("icons", "icons", "icons", "icons", "accent", "accent", "accent", "accent", "clock", "clock", "clock", "clock", "dock", "notch", "wallpaper", "style")[edit],
            fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = BLUE,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
internal fun BarrageScene(t: Float, kick: Float) {
    val features = listOf(
        "live synced lyrics", "widgets you can touch", "a notch that's yours", "${Schema.optionCount}+ settings",
        "icon packs + 10 shapes", "8 page transitions", "gestures for everything", "a real music player",
        "a calculator. on your home.", "fuzzy search", "one-tap presets", "backup & restore",
        "no ads. no tracking.", "blocky tty clocks", "a pet that remembers", "…and one more thing",
    )
    val beat = t / BEAT
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val speed = clamp01(t / (BAR * 4))
            val rnd = Random(4)
            repeat(60) {
                val ang = rnd.nextFloat() * 2 * PI.toFloat()
                val base = (rnd.nextFloat() + t * (0.4f + 1.4f * speed)) % 1f
                val r0 = base * size.maxDimension * 0.7f
                val r1 = r0 + 30f + 140f * speed * base
                val c = center
                drawLine(
                    Color.White.copy(alpha = 0.08f + 0.25f * base * speed),
                    Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), 2f + 3f * base,
                )
            }
        }
        features.forEachIndexed { i, f ->
            val q = (beat - i) / 2.6f
            if (q in 0f..1f) {
                val z = easeOut(q)
                val side = if (i % 2 == 0) -1f else 1f
                Box(
                    Modifier.align(Alignment.Center).graphicsLayer {
                        val s = 0.3f + 1.9f * z
                        scaleX = s; scaleY = s
                        rotationX = 50f * (1 - z) - 8f
                        rotationZ = side * 4f * (1 - z)
                        translationX = side * 60f * z
                        translationY = (i % 3 - 1) * 90f * (1 - z)
                        alpha = clamp01(q * 6) * clamp01((1 - q) * 3)
                        cameraDistance = 10f * density
                    }
                ) {
                    Text(
                        f, fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(BLUE, BLUE.copy(alpha = 0.6f))))
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
        Text(
            "RINTOS", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f + 0.4f * kick),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}

@Composable
internal fun RinScene(t: Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.22f), Color.Transparent), Offset(size.width / 2, size.height * 0.62f), size.width), size.width, Offset(size.width / 2, size.height * 0.62f))
            val rnd = Random(12)
            repeat(40) {
                val x = rnd.nextFloat() * size.width
                val y = (rnd.nextFloat() * size.height - t * 18f * (0.4f + rnd.nextFloat())).mod(size.height)
                drawCircle(Color.White.copy(alpha = 0.15f + 0.25f * rnd.nextFloat()), 1.5f + rnd.nextFloat() * 2f, Offset(x, y))
            }
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height * 0.66f), Offset(size.width, size.height * 0.66f), 2f)
        }
        val walkEnd = 3.6f
        val walking = t < walkEnd
        val xFrac = if (walking) -0.25f + 0.75f * easeOut(t / walkEnd) else 0.5f
        val hop = if (walking) abs(sin(t * PI.toFloat() * 3.2f)) * 10f else 0f
        val pose = when {
            walking -> Pose.WALK
            t < 4.2f -> Pose.SHOCK
            else -> Pose.FRONT
        }
        val size = 150.dp
        Column(
            Modifier.offset(x = w * xFrac - size / 2).align(Alignment.CenterStart).padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bubble = when {
                t in 4.1f..5.4f -> "oh— hi!"
                t in 5.4f..6.5f -> "I'm Rin."
                t in 6.5f..7.6f -> "I live here now."
                t >= 7.6f -> "let's make it yours →"
                else -> null
            }
            Box(Modifier.height(40.dp)) { bubble?.let { SpeechBubble(it) } }
            Spacer(Modifier.height(6.dp))
            RinSprite(pose, size, Modifier.graphicsLayer { translationY = -hop })
        }
    }
}
