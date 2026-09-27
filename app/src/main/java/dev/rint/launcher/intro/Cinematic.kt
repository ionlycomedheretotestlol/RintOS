package dev.rint.launcher.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import dev.rint.launcher.ui.blockWidth
import dev.rint.launcher.ui.drawBlocks
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val BAR = Score.BAR.toFloat()
private val BEAT = Score.BEAT.toFloat()
private var BLUE = Color(RINT_BLUE.toInt())
private val INK = Color(0xFF020308)
private val RED_SPLIT = Color(0xFFFF2D55)
private val CYAN_SPLIT = Color(0xFF00E5FF)

private fun bars(b: Int) = b * BAR
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - clamp01(x)).let { it * it * it }
private fun easeIn(x: Float) = clamp01(x).let { it * it * it }
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = clamp01(x) - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

private fun barOf(t: Float) = floor(t / BAR).toInt()
private fun inBar(t: Float) = t / BAR - floor(t / BAR)
private fun beatPhase(t: Float) = t / BEAT - floor(t / BEAT)

/** 1 on every kick, decaying through the beat; 0 where the score has no kick. */
private fun kickOf(t: Float) = if (Score.kick(barOf(t))) exp(-beatPhase(t) * 7f) else 0f

/** Seconds since the last big hit (BRAAM + impact), or a large number. */
private fun sinceImpact(t: Float): Float {
    var best = 99f
    for (b in Score.impacts) { val d = t - bars(b); if (d >= 0f && d < best) best = d }
    return best
}

/** The heartbeat envelope (lub-dub every two beats), matching the synth. */
private fun heartbeat(t: Float): Float {
    val c = (t / (2 * BEAT) - floor(t / (2 * BEAT))) * 2 * BEAT
    return exp(-c * 14f) + (if (c > 0.17f) 0.7f * exp(-(c - 0.17f) * 16f) else 0f)
}

/** How hard the camera shakes at [t]: impacts, snare rolls, risers and kicks. */
private fun shakeOf(t: Float): Float {
    val bar = barOf(t)
    val pb = inBar(t)
    var s = 30f * exp(-sinceImpact(t) * 4.5f)
    if (bar == Score.DROP - 1 || bar == Score.BREAK - 1 || bar == Score.SILENCE - 1) s += 10f * pb * pb
    if (bar in Score.TUNNEL + 2 until Score.LIFT) s += 5f * ((t - bars(Score.TUNNEL + 2)) / bars(2))
    if (bar in Score.LIFT + 2 until Score.BREAK) s += 4f * ((t - bars(Score.LIFT + 2)) / bars(2))
    s += 3f * kickOf(t)
    return s
}

/** The scripted opening. Its clock follows the audio playback head so picture and music stay locked. */
@Composable
fun IntroCinematic(synth: IntroSynth, onDone: () -> Unit) {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (clock.floatValue < Score.TIMELINE_SECONDS) {
            withFrameNanos { now ->
                val audio = synth.seconds().toFloat()
                val wall = (now - start) / 1e9f
                // no audio device (or it hasn't started): fall back to the wall clock
                clock.floatValue = if (audio > 0.05f || wall < 0.6f) audio else wall
            }
        }
        onDone()
    }
    Film(time = { clock.floatValue })
}

/**
 * The film itself, driven by [time]. Only the scene switch recomposes on bar changes; shake,
 * flashes and letterboxing are read in the draw/layer phase so they never trigger recomposition.
 */
@Composable
internal fun Film(time: () -> Float) {
    dev.rint.launcher.ui.LocalRintOrNull.current?.let { BLUE = it.colors.accent }
    val bar by remember { derivedStateOf { barOf(time()) } }
    val density = LocalDensity.current.density
    val stage = rememberStage3D()
    val three = stage.ok
    Box(Modifier.fillMaxSize().background(INK)) {
        // three.js lives underneath; it only draws during its own sections
        Stage3D(stage, time, Modifier.fillMaxSize().graphicsLayer {
            val t = time()
            val s = shakeOf(t) * density * 0.6f
            translationX = sin(t * 97f) * s; translationY = cos(t * 71f) * s * 0.8f
        })
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = time()
                val s = shakeOf(t) * density
                translationX = sin(t * 97f) * s
                translationY = cos(t * 71f) * s * 0.8f
                rotationZ = sin(t * 53f) * s * 0.012f
                val punch = 1f + 0.018f * kickOf(t) + 0.08f * exp(-sinceImpact(t) * 6f)
                scaleX = punch; scaleY = punch
            }
        ) {
            when (Score.part(bar)) {
                Score.Part.HEART -> HeartScene(time)
                Score.Part.BOOT -> BootScene(time)
                Score.Part.RISE -> if (three) RiseOverlay(time) else WordmarkScene(time)
                Score.Part.BUILD -> TaglineScene(time)
                Score.Part.DROP -> MontageScene(time)
                Score.Part.TUNNEL -> if (three) TunnelOverlay(time) else BarrageScene(time, Score.TUNNEL, Score.TUNNEL, tunnelFeatures + liftFeatures)
                Score.Part.LIFT -> if (bar < Score.LIFT + 2) EvolutionScene(time) else BarrageScene(time, Score.LIFT, Score.LIFT + 2, liftFeatures)
                Score.Part.BREAK -> BreakScene(time)
                Score.Part.SILENCE -> SilenceScene(time)
                Score.Part.FINAL -> RinScene(time, three)
                Score.Part.OUTRO, Score.Part.AFTER -> CreditsScene(time, three)
            }
        }
        FilmFx(time)
    }
}

/** Letterbox, flashes, vignette, scanlines and the film's progress hairline. Draw-phase only. */
@Composable
private fun FilmFx(time: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val bar = barOf(t)
        val pb = inBar(t)
        val part = Score.part(bar)
        // letterbox: closes in on the build-ups, snaps open on the drops
        val box = when {
            part == Score.Part.HEART -> 0.12f
            part == Score.Part.BOOT -> 0.12f * (1f - easeOut((t - bars(Score.RISE - 1)) / 1.5f))
            bar == Score.DROP - 1 -> 0.2f * easeIn(pb) + if (pb > 0.75f) 0.3f * easeIn((pb - 0.75f) * 4f) else 0f
            bar in Score.LIFT + 2 until Score.BREAK -> 0.26f * easeIn((t - bars(Score.LIFT + 2)) / bars(2))
            part == Score.Part.BREAK -> 0.13f
            part == Score.Part.OUTRO -> 0.12f * easeOut((t - bars(Score.OUTRO)) / 1.5f)
            else -> 0f
        }
        if (box > 0f) {
            val h = size.height * box
            drawRect(INK, Offset.Zero, Size(size.width, h))
            drawRect(INK, Offset(0f, size.height - h), Size(size.width, h))
        }
        // vignette
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center, size.maxDimension * 0.75f))
        // scanlines while the machine is waking up
        if (part == Score.Part.HEART || part == Score.Part.BOOT) {
            val a = if (part == Score.Part.HEART) 0.07f else 0.05f
            var y = (t * 40f) % 6f
            while (y < size.height) { drawRect(Color.Black.copy(alpha = a), Offset(0f, y), Size(size.width, 2f)); y += 6f }
        }
        // in the drops, the whole frame breathes with the kick and flickers on every clap
        if (part == Score.Part.DROP || part == Score.Part.TUNNEL || part == Score.Part.LIFT || part == Score.Part.FINAL ||
            (part == Score.Part.BUILD && bar >= Score.BUILD)) {
            val k = kickOf(t)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, BLUE.copy(alpha = 0.45f * k)), center, size.maxDimension * 0.72f))
            val beatInBar = floor(t / BEAT).toInt() % 4
            if (beatInBar == 1 || beatInBar == 3) drawRect(Color.White.copy(alpha = 0.09f * exp(-beatPhase(t) * 12f)))
        }
        // white-hot flash on every impact, with a chromatic fringe
        val si = sinceImpact(t)
        if (si < 0.7f) {
            val f = exp(-si * 7f)
            drawRect(Color.White.copy(alpha = f))
            drawRect(RED_SPLIT.copy(alpha = 0.25f * f), Offset(-12f * f, 0f), size)
            drawRect(CYAN_SPLIT.copy(alpha = 0.18f * f), Offset(12f * f, 0f), size)
        }
        // the last beat before each drop collapses to an old-TV line
        val collapse = (bar == Score.DROP - 1 || bar == Score.BREAK - 1 || bar == Score.SILENCE - 1) && pb > 0.75f
        if (collapse) {
            val k = (pb - 0.75f) * 4f
            drawRect(INK)
            val w = size.width * (1f - easeIn(k))
            drawRect(Color.White, Offset((size.width - w) / 2, size.height / 2 - 2f), Size(w, 4f))
        }
        // film progress hairline
        drawRect(BLUE.copy(alpha = 0.7f), Offset(0f, size.height - 3f), Size(size.width * clamp01(t / Score.TIMELINE_SECONDS.toFloat()), 3f))
    }
}

/** Text with an RGB split that spikes on impacts. */
@Composable
private fun GlitchText(text: String, style: TextStyle, split: () -> Float, modifier: Modifier = Modifier, color: Color = Color.White) {
    Box(modifier) {
        Text(text, style = style, color = RED_SPLIT.copy(alpha = 0.8f), modifier = Modifier.graphicsLayer { translationX = -split(); alpha = clamp01(split() / 4f) })
        Text(text, style = style, color = CYAN_SPLIT.copy(alpha = 0.8f), modifier = Modifier.graphicsLayer { translationX = split(); alpha = clamp01(split() / 4f) })
        Text(text, style = style, color = color)
    }
}

// ───────────────────────────── bars 0–1: heartbeat ─────────────────────────────

@Composable
internal fun HeartScene(time: () -> Float) {
    val ecg = remember { Path() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                // the inhale at the end of bar 1 pulls the camera into the core
                val t = time()
                val z = if (barOf(t) == 1) easeIn(inBar(t)).pow(1.6f) else 0f
                scaleX = 1f + 5f * z; scaleY = scaleX
            }
        ) {
            val t = time()
            val beat = heartbeat(t)
            val c = center
            // the core: one pixel of RintOS, throbbing
            val core = size.minDimension * (0.035f + 0.02f * beat)
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.55f * (0.3f + beat)), Color.Transparent), c, core * 7f), core * 7f, c)
            drawRoundRect(BLUE, Offset(c.x - core / 2, c.y - core / 2), Size(core, core), CornerRadius(core * 0.18f))
            drawRoundRect(Color.White.copy(alpha = beat * 0.8f), Offset(c.x - core / 4, c.y - core / 4), Size(core / 2, core / 2))
            // ECG trace scrolling through the core
            val window = 3f
            val y0 = c.y
            ecg.reset()
            val steps = 180
            var started = false
            for (i in 0..steps) {
                val x = size.width * i / steps
                val tt = t - (1f - i / steps.toFloat()) * window
                if (tt < 0f) continue
                val cyc = (tt / (2 * BEAT) - floor(tt / (2 * BEAT))) * 2 * BEAT
                fun g(mu: Float, s: Float) = exp(-((cyc - mu) * (cyc - mu)) / (2 * s * s))
                val v = g(0.0f, 0.012f) - 0.35f * g(0.03f, 0.012f) + 0.5f * g(0.17f, 0.018f) + 0.12f * g(0.36f, 0.05f)
                val y = y0 - v * size.height * 0.12f
                if (!started) { ecg.moveTo(x, y); started = true } else ecg.lineTo(x, y)
            }
            drawPath(ecg, BLUE.copy(alpha = 0.25f), style = Stroke(14f, cap = StrokeCap.Round))
            drawPath(ecg, BLUE, style = Stroke(4f, cap = StrokeCap.Round))
            // the write head
            drawCircle(Color.White, 6f, Offset(size.width, y0))
        }
        val t = time()
        val line = if (t < BAR) "signal detected" else "it's waking up."
        val start = if (t < BAR) 0.4f else BAR + 0.2f
        val typed = ((t - start) / 0.05f).toInt().coerceIn(0, line.length)
        Text(
            line.take(typed) + if ((t * 3).toInt() % 2 == 0) "█" else " ",
            fontFamily = RintFonts.Terminal, fontSize = 22.sp, color = Color(0xFFB8C4FF),
            modifier = Modifier.align(Alignment.Center).padding(top = 170.dp).graphicsLayer { alpha = 1f - easeIn(inBar(time()) * (if (barOf(time()) == 1) 1.2f else 0f)) },
        )
    }
}

// ───────────────────────────── bars 2–3: BRAAM, boot ─────────────────────────────

@Composable
internal fun BootScene(time: () -> Float) {
    val t = time() - bars(Score.BOOT)
    val story = listOf(
        "> rintos --the-story-so-far",
        "  names: FishinOS, QuotOS, ThatOS, TrulyOS…",
        "  [ ok ] settled on: RintOS",
        "  [ !! ] build 1: crashed",
        "  [ !! ] build 2: crashed harder",
        "  [ ok ] Rin: 48 pixels → a whole cat",
        "  [ !! ] music widget: \"suffering\"",
        "  [ ok ] music widget: fixed",
        "  [ ok ] v1.0 shipped",
        "  [ !! ] saver mode: worked once",
        "  [ ok ] saver mode: works every time",
        "  [ ok ] ${Schema.optionCount} settings",
        "  [ ok ] no YouTube. real music.",
        "  [ ok ] tested. and tested. and tested.",
        "  compiling 1.3…",
        "  ready.",
    )
    val lines = story.mapIndexed { i, l -> (if (i == 0) 0.05f else i * BEAT) to l }.let { all ->
        // keep the last 11 visible, like a real terminal scrolling
        val shown = all.count { t >= it.first }
        all.drop((shown - 11).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize()) {
        // shockwave from the BRAAM
        Canvas(Modifier.fillMaxSize()) {
            val tt = time() - bars(Score.BOOT)
            for (k in 0 until 3) {
                val p = (tt - k * 0.12f) / 1.6f
                if (p in 0f..1f) drawCircle(BLUE.copy(alpha = (1f - p) * 0.8f), size.maxDimension * easeOut(p), center, style = Stroke(26f * (1f - p) + 2f))
            }
        }
        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
            lines.forEachIndexed { i, (start, text) ->
                if (t >= start) {
                    val typed = ((t - start) / 0.018f).toInt().coerceAtMost(text.length)
                    val last = i == lines.indexOfLast { t >= it.first }
                    val cursor = if (last && (typed < text.length || (t * 3).toInt() % 2 == 0)) "█" else ""
                    val bad = "!!" in text
                    val jolt = if (t - start < 0.08f) 10f else 0f
                    Text(
                        text.take(typed) + cursor, fontFamily = RintFonts.Terminal, fontSize = 23.sp,
                        color = when { bad -> Color(0xFFFF6B6B); "[ ok ]" in text -> Color(0xFF9CF6C1); else -> Color(0xFFB8C4FF) },
                        modifier = Modifier.graphicsLayer { translationX = jolt },
                    )
                }
            }
        }
    }
}

private class Particle(val cx: Int, val cy: Int, val accent: Boolean, val sx: Float, val sy: Float, val rot: Float, val delay: Float)

// ───────────────────────────── bars 4–5: the wordmark assembles ─────────────────────────────

@Composable
internal fun WordmarkScene(time: () -> Float) {
    val particles = remember {
        val (a, aw) = BlockFont.cells("RINT")
        val (b, _) = BlockFont.cells("OS")
        val rnd = Random(9)
        (a.map { Triple(it.first, it.second, false) } + b.map { Triple(it.first + aw + 2, it.second, true) })
            .map { (x, y, acc) -> Particle(x, y, acc, rnd.nextFloat() * 3 - 1f, rnd.nextFloat() * 3 - 1f, rnd.nextFloat() * 1080 - 540, rnd.nextFloat() * 0.6f) }
    }
    val totalW = BlockFont.cells("RINT").second + 2 + BlockFont.cells("OS").second
    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                // at the end of bar 5 the wordmark rushes the camera
                val t = time() - bars(Score.RISE) - BAR * 2
                val rush = easeIn((t - BAR * 1.75f) / (BAR * 0.25f))
                scaleX = 1f + 3f * rush; scaleY = scaleX; alpha = 1f - rush
            }
        ) {
            val t = time() - bars(Score.RISE) - BAR
            val cell = size.width * 0.84f / totalW
            val ox = (size.width - totalW * cell) / 2
            val oy = size.height * 0.42f - cell * 2.5f
            val landed = t - 1.9f
            val split = if (landed in 0f..0.5f) (1f - landed / 0.5f) * cell * 0.35f else 0f
            val cx = size.width / 2
            val cy = oy + cell * 2.5f
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.4f * clamp01(t - 1f)), Color.Transparent), Offset(cx, cy), size.width * 0.8f), size.width * 0.8f, Offset(cx, cy))
            val pulse = 1f + 0.06f * heartbeat(t) * clamp01(landed * 2)
            for (pass in 0 until (if (split > 0f) 3 else 1)) {
                val dx = when (pass) { 1 -> -split; 2 -> split; else -> 0f }
                particles.forEach { p ->
                    val k = easeOutBack((t - p.delay) / 1.3f)
                    val tx = ox + p.cx * cell
                    val ty = oy + p.cy * cell
                    val x = p.sx * size.width + (tx - p.sx * size.width) * k
                    val y = p.sy * size.height + (ty - p.sy * size.height) * k
                    val fx = cx + (x - cx) * pulse + dx
                    val fy = cy + (y - cy) * pulse
                    val s = cell * (0.88f - 0.3f * (1 - clamp01(k)))
                    val col = when (pass) { 1 -> RED_SPLIT.copy(alpha = 0.6f); 2 -> CYAN_SPLIT.copy(alpha = 0.6f); else -> if (p.accent) BLUE else Color.White }
                    rotateBlock(fx, fy, s, p.rot * (1 - clamp01(k)), col)
                }
            }
        }
        val t = time() - bars(Score.RISE) - BAR
        val sub = clamp01((t - 2.1f) / 0.5f)
        Text(
            "version 1.3 — the real one.",
            fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f * sub * (1f - easeIn((t - BAR * 1.75f) * 4))),
            textAlign = TextAlign.Center, letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.Center).padding(top = 150.dp).graphicsLayer { translationY = (1 - sub) * 30f },
        )
    }
}

private fun DrawScope.rotateBlock(x: Float, y: Float, s: Float, deg: Float, c: Color) {
    rotate(deg, Offset(x + s / 2, y + s / 2)) {
        drawRoundRect(c, Offset(x, y), Size(s, s), CornerRadius(s * 0.14f))
    }
}

// ───────────────────────────── bars 6–9: words slam in, countdown ─────────────────────────────

private val phrases = listOf(
    listOf("it" to false, "took" to false, "a" to false, "while." to true),
    listOf("so" to false, "many" to false, "crashes." to true, "fixed." to false),
    listOf("this" to false, "is" to false, "RintOS" to false, "1.3" to true),
)

@Composable
internal fun TaglineScene(time: () -> Float) {
    val t = time() - bars(Score.BUILD)
    val bar = (t / BAR).toInt().coerceIn(0, 3)
    val local = t - bar * BAR
    val beat = (local / BEAT).toInt().coerceIn(0, 3)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val tt = time()
            val k = kickOf(tt)
            val r = size.minDimension * (0.5f + 0.12f * k)
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.18f + 0.3f * k), Color.Transparent), center, r), r, center)
            // speed streaks once the beat is in
            if (barOf(tt) >= Score.BUILD + 2) {
                val rnd = Random(3)
                val speed = (tt - bars(Score.BUILD + 2)) / bars(2)
                repeat(36) {
                    val y = rnd.nextFloat() * size.height
                    val len = 80f + 400f * speed * rnd.nextFloat()
                    val x = ((rnd.nextFloat() + tt * (0.6f + 2.5f * speed)) % 1.4f - 0.2f) * size.width
                    drawRect(Color.White.copy(alpha = 0.05f + 0.12f * speed), Offset(x, y), Size(len, 2f))
                }
            }
        }
        if (bar < 3) {
            // one word per beat, each one slamming down from huge
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                phrases[bar].forEachIndexed { i, (w, acc) ->
                    if (beat >= i) {
                        val since = local - i * BEAT
                        Text(
                            w, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 54.sp, lineHeight = 56.sp,
                            color = if (acc) BLUE else Color.White, textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer {
                                val s = 1f + 1.6f * (1f - easeOut(since / 0.14f))
                                scaleX = s; scaleY = s
                                alpha = clamp01(since / 0.06f)
                                translationY = -20f * (1f - easeOut(since / 0.14f))
                            },
                        )
                    }
                }
            }
        } else {
            // the countdown rides the snare roll: 3 · 2 · 1 · (collapse)
            if (beat < 3) {
                val n = (3 - beat).toString()
                val since = local - beat * BEAT
                Canvas(
                    Modifier.size(180.dp).graphicsLayer {
                        val s = 2.4f - 1.4f * easeOut(since / 0.16f) + 0.25f * (since / BEAT)
                        scaleX = s; scaleY = s
                        rotationZ = (if (beat % 2 == 0) -1 else 1) * 6f * (1f - easeOut(since / 0.2f))
                    }
                ) {
                    val cell = size.height / 5f
                    val w = blockWidth(n) * cell
                    drawBlocks(n, RED_SPLIT.copy(alpha = 0.6f), cell, origin = Offset((size.width - w) / 2 - 8f, 0f))
                    drawBlocks(n, CYAN_SPLIT.copy(alpha = 0.6f), cell, origin = Offset((size.width - w) / 2 + 8f, 0f))
                    drawBlocks(n, if (beat == 2) BLUE else Color.White, cell, origin = Offset((size.width - w) / 2, 0f))
                }
            }
        }
    }
}

// ───────────────────────────── bars 10–17: THE DROP. the montage ─────────────────────────────

private val montageCaptions = listOf("SHAPE", "COLOR", "CLOCK", "PIXEL")

private fun montageConfig(edit: Int): RintConfig {
    val b = RintConfig()
    return when (edit) {
        0 -> b.copy(icons = b.icons.copy(shape = IconShape.CIRCLE))
        1 -> b.copy(icons = b.icons.copy(shape = IconShape.HEXAGON))
        2 -> b.copy(icons = b.icons.copy(shape = IconShape.PEBBLE))
        3 -> b.copy(icons = b.icons.copy(shape = IconShape.CLOVER))
        4 -> b.copy(look = b.look.copy(accent = 0xFFFF6FB5L), icons = b.icons.copy(monoFg = 0xFFFF6FB5L, style = IconStyle.RINT))
        5 -> b.copy(look = b.look.copy(accent = 0xFF39FF88L), icons = b.icons.copy(monoFg = 0xFF39FF88L, monoBg = MonoBackground.BLACK, style = IconStyle.RINT))
        6 -> b.copy(look = b.look.copy(accent = 0xFFFF9F1CL), icons = b.icons.copy(monoFg = 0xFFFF9F1CL, shape = IconShape.HEXAGON, monoBg = MonoBackground.BLACK, style = IconStyle.RINT))
        7 -> b.copy(icons = b.icons.copy(monoBg = MonoBackground.ACCENT, style = IconStyle.RINT))
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
internal fun MontageScene(time: () -> Float) {
    val editLen = BEAT * 2
    val edit by remember { derivedStateOf { ((time() - bars(Score.DROP)) / editLen).toInt().coerceIn(0, 15) } }
    val cfg = remember(edit) { montageConfig(edit) }
    val density = LocalDensity.current
    fun local() = time() - bars(Score.DROP)
    fun inEdit() = local() - edit * editLen
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val phoneW = minOf(maxWidth * 0.6f, 260.dp)
        // giant outlined word sliding behind the phone
        val cap = montageCaptions[edit / 4]
        Text(
            cap, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 150.sp, color = Color.White.copy(alpha = 0.07f),
            softWrap = false, maxLines = 1,
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                val p = (local() - (edit / 4) * editLen * 4) / (editLen * 4)
                translationX = (0.6f - 1.2f * p) * size.width * 0.5f
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            val k = kickOf(time())
            val r = size.minDimension * (0.55f + 0.1f * k)
            drawCircle(Brush.radialGradient(listOf(Color(cfg.look.accent.toInt()).copy(alpha = 0.22f + 0.2f * k), Color.Transparent), center, r), r, center)
        }
        GlitchText(
            "every ${cap.lowercase()}.", TextStyle(fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp),
            split = { 14f * exp(-((local() % (editLen * 4)) * 6f)) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp),
        )
        Box(
            Modifier.align(Alignment.Center).padding(top = 60.dp).graphicsLayer {
                val enter = easeOutBack(local() / 0.7f)
                val whip = (1f - easeOut(inEdit() / 0.28f)) * (if (edit % 2 == 0) 28f else -28f)
                scaleX = (0.2f + 0.8f * enter) * (1f + 0.02f * kickOf(time())); scaleY = scaleX
                rotationY = sin(local() * 0.9f) * 8f + whip
                rotationZ = (1 - clamp01(enter)) * -25f
                cameraDistance = 14f * density.density
            }
        ) {
            val wave = if (edit == 12) (inEdit() / editLen) * 4.5f - 0.5f else -1f
            MockPhone(cfg, width = phoneW, dockWave = wave, notchOpen = edit == 13, lyric = if (edit == 13) "your phone, your rules ♪" else null)
            // the finger of someone customizing
            val target = when (edit / 4) {
                0 -> Offset(0.3f + 0.13f * (edit % 4), 0.72f)
                1 -> Offset(0.5f, 0.35f)
                2 -> Offset(0.45f, 0.2f)
                else -> when (edit) { 12 -> Offset((wave.coerceIn(0f, 3f) + 0.5f) / 4f, 0.93f); 13 -> Offset(0.5f, 0.03f); else -> Offset(0.6f, 0.5f) }
            }
            val fx = with(density) { phoneW.toPx() } * target.x
            val fy = with(density) { (phoneW * 2.05f).toPx() } * target.y
            Box(
                Modifier.offset { IntOffset(fx.roundToInt() - 40, fy.roundToInt() - 40) }.size(28.dp)
                    .graphicsLayer { val press = clamp01(1f - inEdit() / 0.35f); scaleX = 1f - 0.25f * press; scaleY = scaleX }
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.55f))
            )
            Box(
                Modifier.offset { IntOffset(fx.roundToInt() - 40, fy.roundToInt() - 40) }.size(28.dp)
                    .graphicsLayer { val press = clamp01(1f - inEdit() / 0.35f); val s = 1f + (1 - press) * 2.2f; scaleX = s; scaleY = s; alpha = press }
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

// ───────────────────────────── bars 18–21: feature barrage, warp ─────────────────────────────

@Composable
internal fun BarrageScene(time: () -> Float, from: Int, cardsFrom: Int, features: List<String>) {
    val beatIdx by remember { derivedStateOf { ((time() - bars(cardsFrom)) / BEAT).toInt() } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time() - bars(from)
            val warp = clamp01((t - bars(2)) / bars(2))
            val speed = 0.4f + clamp01(t / bars(2)) + 3f * warp * warp
            val rnd = Random(4)
            val c = center
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.15f + 0.45f * warp), Color.Transparent), c, size.minDimension * (0.3f + 0.4f * warp)), size.minDimension * (0.3f + 0.4f * warp), c)
            repeat(70) {
                val ang = rnd.nextFloat() * 2 * PI.toFloat()
                val base = (rnd.nextFloat() + t * speed * 0.5f) % 1f
                val r0 = base * base * size.maxDimension * 0.8f
                val r1 = r0 + 20f + (60f + 500f * warp) * base
                drawLine(
                    (if (it % 7 == 0) BLUE else Color.White).copy(alpha = 0.1f + 0.5f * base),
                    Offset(c.x + cos(ang) * r0, c.y + sin(ang) * r0), Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1), 2f + 4f * base,
                )
            }
        }
        // only the cards near the current beat are composed at all
        features.forEachIndexed { i, f ->
            if (i in beatIdx - 3..beatIdx) {
                val side = if (i % 2 == 0) -1f else 1f
                Box(
                    Modifier.align(Alignment.Center).graphicsLayer {
                        val beat = (time() - bars(cardsFrom)) / BEAT
                        val q = (beat - i) / 2.6f
                        val z = easeOut(q)
                        val s = 0.3f + 1.9f * z
                        scaleX = s; scaleY = s
                        rotationX = 50f * (1 - z) - 8f
                        rotationZ = side * 5f * (1 - z)
                        translationX = side * 60f * z
                        translationY = (i % 3 - 1) * 90f * (1 - z)
                        alpha = clamp01(q * 6) * clamp01((1 - q) * 3)
                        cameraDistance = 10f * density
                    }
                ) {
                    Text(
                        f, fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (i == features.lastIndex) Color.White.copy(alpha = 0.14f) else BLUE)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
        Text(
            "RINTOS", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp).graphicsLayer { alpha = 0.3f + 0.6f * kickOf(time()) },
        )
    }
}

// ───────────────────────────── bar 22: silence ─────────────────────────────

@Composable
internal fun SilenceScene(time: () -> Float) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = time()
            val pb = inBar(t)
            if (pb < 0.6f) {
                val b = heartbeat(t)
                val s = 10f + 8f * b
                drawRoundRect(BLUE.copy(alpha = 0.3f + 0.7f * b), Offset(center.x - s / 2, center.y - s / 2), Size(s, s), CornerRadius(2f))
            }
            if (pb > 0.7f) {
                // the inhale: a line of light stretching out before the hit
                val k = easeIn((pb - 0.7f) / 0.3f)
                val w = size.width * k
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), center.x - w / 2, center.x + w / 2), Offset(center.x - w / 2, center.y - 1.5f - 6f * k), Size(w, 3f + 12f * k))
            }
        }
        Text(
            "wait.", fontFamily = RintFonts.Terminal, fontSize = 22.sp, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.Center).padding(top = 90.dp).graphicsLayer {
                val pb = inBar(time())
                alpha = clamp01((pb - 0.12f) / 0.1f) * clamp01((0.62f - pb) / 0.1f)
            },
        )
    }
}

// ───────────────────────────── bars 23–26: BRAAM. Rin lands. ─────────────────────────────

@Composable
internal fun RinScene(time: () -> Float, three: Boolean = false) {
    val t = time() - bars(Score.FINAL)
    val fall = 0.38f
    val pose = when {
        t < fall -> Pose.JUMP
        t < fall + 0.5f -> Pose.CROUCH
        t < 2.6f -> Pose.CHEER
        t < 6.8f -> Pose.WAVE
        t < 9.2f -> Pose.LAUGH
        else -> Pose.DANCE
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val tt = time() - bars(Score.FINAL)
            val k = kickOf(time())
            val ground = size.height * 0.7f
            // wordmark towering behind him, breathing with the kick
            val word = "1.3"
            val cell = size.width * 0.9f / blockWidth(word)
            val w = blockWidth(word) * cell
            val rise = easeOutBack((tt - 0.3f) / 0.8f)
            val oy = ground - cell * 5f - size.height * 0.2f + (1f - rise) * size.height * 0.2f
            if (!three) {
                drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.35f + 0.25f * k), Color.Transparent), Offset(size.width / 2, oy + cell * 2.5f), size.width * 0.9f), size.width * 0.9f, Offset(size.width / 2, oy + cell * 2.5f))
                drawBlocks(word, BLUE.copy(alpha = 0.22f * clamp01(rise) + 0.1f * k), cell * (1f + 0.03f * k), origin = Offset((size.width - w) / 2, oy))
            }
            // landing shockwave + dust
            val land = tt - fall
            if (land in 0f..1.4f) {
                val p = land / 1.4f
                drawOval(Color.White.copy(alpha = (1f - p) * 0.9f), Offset(size.width / 2 - size.width * easeOut(p), ground - 10f * (1f + p)), Size(size.width * 2 * easeOut(p), 20f * (1f + p)), style = Stroke(8f * (1f - p) + 1f))
                val rnd = Random(5)
                repeat(26) {
                    val dir = rnd.nextFloat() * 2 - 1
                    val v = 200f + rnd.nextFloat() * 500f
                    val x = size.width / 2 + dir * v * land
                    val y = ground - (250f * rnd.nextFloat() * land - 500f * land * land).coerceAtLeast(0f)
                    val s = 6f + 10f * rnd.nextFloat()
                    drawRect(Color.White.copy(alpha = (1f - p) * 0.8f), Offset(x, y), Size(s, s))
                }
            }
            // confetti rain after the landing (the 3D stage has its own)
            if (land > 0f && !three) {
                val rnd = Random(8)
                val cols = listOf(BLUE, Color.White, Color(0xFFFFD60A), Color(0xFFFF6FB5), Color(0xFF39FF88))
                repeat(70) {
                    val x = rnd.nextFloat() * size.width + sin(land * 2f + it) * 20f
                    val speed = 180f + rnd.nextFloat() * 240f
                    val y = -40f + (land * speed + rnd.nextFloat() * size.height * 0.3f) % (size.height + 40f)
                    val s = 8f + rnd.nextFloat() * 8f
                    rotate(land * 200f * (rnd.nextFloat() - 0.5f), Offset(x, y)) {
                        drawRect(cols[it % cols.size].copy(alpha = clamp01(land * 2) * 0.9f), Offset(x - s / 2, y - s / 4), Size(s, s / 2))
                    }
                }
            }
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, ground), Offset(size.width, ground), 3f)
        }
        val rinSize = 170.dp
        Column(
            Modifier.align(Alignment.TopCenter).offset(y = h * 0.7f - rinSize - 52.dp).graphicsLayer {
                val tt = time() - bars(Score.FINAL)
                translationY = if (tt < fall) -(1f - easeIn(tt / fall)) * size.height * 4f else 0f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val bubble = when {
                t in 2.7f..4.6f -> "it's finally here."
                t in 4.6f..6.8f -> "RintOS 1.3. the real one."
                t in 6.8f..9.2f -> "you made this happen."
                t >= 9.2f -> "let's make it yours →"
                else -> null
            }
            Box(Modifier.height(44.dp)) { bubble?.let { SpeechBubble(it) } }
            Spacer(Modifier.height(8.dp))
            RinSprite(pose, rinSize)
        }
    }
}

// ───────────────────────────── bars 18–19: how far he's come ─────────────────────────────

@Composable
internal fun EvolutionScene(time: () -> Float) {
    val t = time() - bars(Score.LIFT)
    val beat = (t / BEAT).toInt()
    val stages = listOf(
        Triple("v0.1", "48 × 56 pixels", Pose.FRONT),
        Triple("v1.0", "survived the crash era", Pose.SHOCK),
        Triple("v1.3", "him.", Pose.CHEER),
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val k = kickOf(time())
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.2f + 0.2f * k), Color.Transparent), center, size.maxDimension * 0.6f), size.maxDimension * 0.6f, center)
        }
        Text(
            if (beat < 6) "the evolution of Rin" else "look how far he's come.", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 30.sp,
            color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp, start = 20.dp, end = 20.dp),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom,
        ) {
            stages.forEachIndexed { i, (ver, cap, pose) ->
                val appear = beat >= i * 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        val since = time() - bars(Score.LIFT) - i * 2 * BEAT
                        val e = easeOutBack(since / 0.35f)
                        alpha = if (appear) clamp01(since / 0.1f) else 0f
                        scaleX = 0.4f + 0.6f * e; scaleY = scaleX
                        translationY = (1f - clamp01(e)) * 80f - (if (beat >= 6) abs(sin(time() * 9f + i)) * 18f else 0f)
                    },
                ) {
                    Text(ver, fontFamily = RintFonts.Pixel, fontSize = 14.sp, color = if (i == 2) BLUE else Color.White)
                    Spacer(Modifier.height(6.dp))
                    RinSprite(pose, if (i == 2) 120.dp else 92.dp, forcePixel = i == 0)
                    Spacer(Modifier.height(6.dp))
                    Text(cap, fontFamily = RintFonts.Inter, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private val tunnelFeatures = listOf(
    "faster.", "every widget, alive", "a notch that's yours", "10 lock screens",
    "photos, your wallpapers", "custom colors. all of them", "gestures for everything", "…and it's free",
)
private val liftFeatures = listOf(
    "Rin: hold home, just talk", "songs play right in the widget", "${Schema.optionCount} settings", "battery saver dot mode",
    "serious alerts only", "no ads. no tracking.", "made by one person", "…one more thing",
)

// ───────────────────────────── 3D overlays: captions on top of three.js ─────────────────────────────

@Composable
internal fun RiseOverlay(time: () -> Float) {
    val t = (time() - bars(Score.RISE)) * (2f / BAR)
    Box(Modifier.fillMaxSize()) {
        Text(
            "INTRODUCING", fontFamily = RintFonts.Pixel, fontSize = 14.sp, color = Color.White, letterSpacing = 6.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp).graphicsLayer {
                alpha = clamp01((t - 0.6f) / 0.6f) * (1f - clamp01((t - 6.5f) / 0.5f))
                translationY = (1f - easeOut((t - 0.6f) / 0.8f)) * -40f
            },
        )
        Text(
            "the real one.", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 22.sp, color = BLUE,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 130.dp).graphicsLayer {
                alpha = clamp01((t - 4.4f) / 0.3f) * (1f - clamp01((t - 7.3f) / 0.4f))
                val s = 1f + 0.5f * (1f - easeOut((t - 4.4f) / 0.25f)); scaleX = s; scaleY = s
            },
        )
    }
}

@Composable
internal fun TunnelOverlay(time: () -> Float) {
    val t = (time() - bars(Score.TUNNEL)) * (2f / BAR)
    Box(Modifier.fillMaxSize()) {
        GlitchText(
            "SECOND DROP", TextStyle(fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 40.sp),
            split = { 18f * exp(-(time() - bars(Score.TUNNEL)) * 3f) },
            modifier = Modifier.align(Alignment.Center).graphicsLayer {
                alpha = 1f - clamp01((t - 1.1f) / 0.3f)
                val s = 0.6f + 0.6f * easeOut(t / 0.3f); scaleX = s; scaleY = s
            },
        )
        Text(
            "warp speed →", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).graphicsLayer {
                alpha = clamp01((t - 5f) / 0.4f)
            },
        )
    }
}

// ───────────────────────────── the breakdown ─────────────────────────────

private val breakLines = listOf("you tested it.", "again.", "and again.", "and it got better.")

@Composable
internal fun BreakScene(time: () -> Float) {
    val t = time() - bars(Score.BREAK)
    val bar = (t / BAR).toInt().coerceIn(0, 3)
    val pose = when {
        t < BAR * 2 -> Pose.SIT
        t < BAR * 2 + 1.2f -> Pose.STRETCH
        t < BAR * 3.5f -> Pose.FRONT
        else -> Pose.CHEER
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val tt = time() - bars(Score.BREAK)
            // slow motes drifting up, like dust in a spotlight
            val rnd = Random(21)
            repeat(50) {
                val x = rnd.nextFloat() * size.width + sin(tt * 0.6f + it) * 18f
                val y = (rnd.nextFloat() * size.height - tt * (14f + rnd.nextFloat() * 22f)).mod(size.height)
                drawCircle(Color.White.copy(alpha = 0.08f + 0.2f * rnd.nextFloat()), 1.4f + rnd.nextFloat() * 2.6f, Offset(x, y))
            }
            val glow = 0.18f + 0.3f * clamp01((tt - BAR * 2) / (BAR * 2))
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = glow), Color.Transparent), Offset(size.width / 2, size.height * 0.62f), size.width * 0.8f),
                size.width * 0.8f, Offset(size.width / 2, size.height * 0.62f))
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 140.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            breakLines.forEachIndexed { i, line ->
                if (bar >= i) {
                    val since = t - i * BAR
                    Text(
                        line, fontFamily = RintFonts.Inter, fontWeight = if (i == 3) FontWeight.Black else FontWeight.Bold,
                        fontSize = if (i == 3) 34.sp else 26.sp, color = if (i == 3) BLUE else Color.White.copy(alpha = if (bar > i) 0.45f else 1f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = clamp01(since / 0.5f)
                            translationY = (1f - easeOut(since / 0.8f)) * 30f
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        // a build counter racing toward 1.3
        Text(
            "build " + (1 + ((t / (BAR * 4)).coerceIn(0f, 1f).let { it * it } * 1299).toInt()).toString(),
            fontFamily = RintFonts.Terminal, fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp),
        )
        RinSprite(pose, 150.dp, Modifier.align(Alignment.Center).padding(top = 200.dp))
    }
}

// ───────────────────────────── credits ─────────────────────────────

private val credits = listOf(
    "RintOS 1.3" to true,
    "made by Carrot" to false,
    "starring Rin" to false,
    "music: synthesized live · 0 audio files" to false,
    "3D: three.js" to false,
    "thanks for waiting." to true,
)

@Composable
internal fun CreditsScene(time: () -> Float, three: Boolean) {
    val t = time() - bars(Score.OUTRO)
    Box(Modifier.fillMaxSize()) {
        if (!three) Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(BLUE.copy(alpha = 0.25f), Color.Transparent), center, size.maxDimension * 0.6f), size.maxDimension * 0.6f, center)
        }
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            credits.forEachIndexed { i, (line, big) ->
                val since = t - i * 0.45f
                Text(
                    line, fontFamily = if (big) RintFonts.Inter else RintFonts.Pixel, fontWeight = if (big) FontWeight.Black else FontWeight.Normal,
                    fontSize = if (big) 30.sp else 12.sp, color = if (i == 0) Color.White else if (big) BLUE else Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = if (big) 8.dp else 4.dp).graphicsLayer {
                        alpha = clamp01(since / 0.4f)
                        translationY = (1f - easeOut(since / 0.6f)) * 24f
                    },
                )
            }
        }
    }
}
