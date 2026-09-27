package dev.rint.launcher.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.R
import dev.rint.launcher.core.LockBg
import dev.rint.launcher.core.LockStyle
import dev.rint.launcher.core.RINT_BLUE
import dev.rint.launcher.core.StartupStyle
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.home.LiveWallpaper
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinParams
import dev.rint.launcher.mascot.RinRig
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.mascot.animateRin
import dev.rint.launcher.ui.I18n
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import dev.rint.launcher.widgets.WidgetCtx
import dev.rint.launcher.widgets.WidgetRegistry
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private val BAR = Score.BAR.toFloat()
private val BEAT = Score.BEAT.toFloat()
private var ACCENT = Color(RINT_BLUE.toInt())
private val INK = Color(0xFF03040A)
private val PINK = Color(0xFFFF6FB5)
private val GOLD = Color(0xFFFFD60A)

private fun bars(b: Int) = b * BAR
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float) = 1f - (1f - clamp01(x)).let { it * it * it }
private fun easeIn(x: Float) = clamp01(x).let { it * it * it }
private fun easeInOut(x: Float): Float { val c = clamp01(x); return if (c < .5f) 4 * c * c * c else 1 - (-2 * c + 2).let { it * it * it } / 2 }
private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = clamp01(x) - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

private fun barOf(t: Float) = floor(t / BAR).toInt()
private fun inBar(t: Float) = t / BAR - floor(t / BAR)
private fun beatPhase(t: Float) = t / BEAT - floor(t / BEAT)
private fun kickOf(t: Float) = if (Score.kick(barOf(t))) exp(-beatPhase(t) * 7f) else 0f
private fun sinceImpact(t: Float): Float {
    var best = 99f
    for (b in Score.impacts) { val d = t - bars(b); if (d >= 0f && d < best) best = d }
    return best
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
                clock.floatValue = if (audio > 0.05f || wall < 0.6f) audio else wall
            }
        }
        onDone()
    }
    Film(time = { clock.floatValue })
}

/** The RintOS 1.4 film, "Rin's Upgrade Day", driven by [time]. */
@Composable
internal fun Film(time: () -> Float) {
    dev.rint.launcher.ui.LocalRintOrNull.current?.let { ACCENT = it.colors.accent }
    val bar by remember { derivedStateOf { barOf(time()) } }
    val density = LocalDensity.current.density
    Box(Modifier.fillMaxSize().background(INK)) {
        Native3D(time, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val t = time()
                val s = (16f * exp(-sinceImpact(t) * 5f) + 2f * kickOf(t)) * density
                translationX = sin(t * 97f) * s; translationY = cos(t * 71f) * s * 0.8f
                val punch = 1f + 0.012f * kickOf(t) + 0.05f * exp(-sinceImpact(t) * 6f)
                scaleX = punch; scaleY = punch
            }
        ) {
            when (Score.part(bar)) {
                Score.Part.WAKE, Score.Part.ADVENTURE, Score.Part.POWERUP, Score.Part.UPGRADE -> GameScene(time)
                Score.Part.HD -> HdScene(time)
                Score.Part.STARS -> StarsScene(time)
                Score.Part.SHOW -> ShowScene(time)
                Score.Part.FINALE -> FinaleScene(time)
                Score.Part.CREDITS, Score.Part.AFTER -> CreditsScene(time)
            }
        }
        FilmFx(time)
    }
}

@Composable
private fun FilmFx(time: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val t = time()
        val bar = barOf(t)
        val pb = inBar(t)
        val part = Score.part(bar)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center, size.maxDimension * 0.78f))
        if (part == Score.Part.HD || part == Score.Part.SHOW || part == Score.Part.FINALE) {
            val k = kickOf(t)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, ACCENT.copy(alpha = 0.32f * k)), center, size.maxDimension * 0.72f))
        }
        val si = sinceImpact(t)
        if (si < 0.6f) drawRect(Color.White.copy(alpha = exp(-si * 8f)))
        // the power-up and the grab flash white-gold
        val sg = t - Score.GRAB.toFloat()
        if (sg in 0f..0.4f) drawRect(GOLD.copy(alpha = 0.6f * exp(-sg * 10f)))
        // collapse to a line before the drops
        if ((bar == Score.HD - 1 || bar == Score.SHOW - 1) && pb > 0.8f) {
            val k = (pb - 0.8f) * 5f
            drawRect(INK)
            val w = size.width * (1f - easeIn(k))
            drawRect(Color.White, Offset((size.width - w) / 2, size.height / 2 - 2f), Size(w, 4f))
        }
        drawRect(ACCENT.copy(alpha = 0.7f), Offset(0f, size.height - 3f), Size(size.width * clamp01(t / Score.TIMELINE_SECONDS.toFloat()), 3f))
    }
}

// ═══════════════════════════════ ACT I: the 8-bit world ═══════════════════════════════

private val blockLabels = listOf("home screen", "music + lyrics", "Rin, your AI", "10 lock screens", "200+ settings", "português")

@Composable
private fun GameScene(time: () -> Float) {
    val look = LocalRint.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val vw = 150
        val vh = (vw * (maxHeight / maxWidth)).toInt().coerceAtLeast(180)
        val world = remember(vh, look.colors.accent) { PixelWorld(vw, vh, look.colors.accent.toArgb()) }
        val bmp = remember(world) { android.graphics.Bitmap.createBitmap(vw, vh, android.graphics.Bitmap.Config.ARGB_8888) }
        val img = remember(bmp) { bmp.asImageBitmap() }
        val shards = remember { val r = Random(21); List(14 * 28) { floatArrayOf(r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1, 0.4f + r.nextFloat()) } }
        val t = time()
        val shatter = Score.SHATTER.toFloat()
        val grab = Score.GRAB.toFloat()
        // HD Rin flickers in during the power-up, then takes over
        val hdRin = when {
            t < grab -> false
            t < grab + 1.4f -> ((t - grab) / (BEAT / (2f + 4f * (t - grab) / 1.4f))).toInt() % 2 == 0
            else -> true
        }
        Canvas(Modifier.fillMaxSize()) {
            val frameT = if (t >= shatter) shatter else t
            world.draw(frameT, hideRin = hdRin)
            bmp.setPixels(world.pixels, 0, vw, 0, 0, vw, vh)
            val full = IntSize(size.width.toInt(), size.height.toInt())
            when {
                t < bars(Score.UPGRADE) -> drawImage(img, dstSize = full, filterQuality = FilterQuality.None)
                t < shatter -> {
                    // the game glitches: slices tear sideways, colours split
                    val k = (t - bars(Score.UPGRADE)) / (shatter - bars(Score.UPGRADE))
                    val slice = floor(t / (BEAT / 4)).toInt()
                    drawImage(img, dstSize = full, filterQuality = FilterQuality.None)
                    val rnd = Random(slice)
                    repeat((4 + k * 10).toInt()) {
                        val sy = rnd.nextInt(vh)
                        val sh = 2 + rnd.nextInt(8)
                        val dx = ((rnd.nextFloat() - .5f) * 40 * (0.3f + k)).toInt()
                        drawImage(
                            img, srcOffset = IntOffset(0, sy), srcSize = IntSize(vw, sh.coerceAtMost(vh - sy)),
                            dstOffset = IntOffset((dx * size.width / vw).toInt(), (sy * size.height / vh).toInt()),
                            dstSize = IntSize(size.width.toInt(), (sh.coerceAtMost(vh - sy) * size.height / vh).toInt()),
                            filterQuality = FilterQuality.None,
                        )
                    }
                    val split = (6 + 20 * k) * (if (slice % 3 == 0) 1f else 0.4f)
                    drawImage(img, dstOffset = IntOffset(-split.toInt(), 0), dstSize = full, filterQuality = FilterQuality.None, alpha = 0.35f,
                        colorFilter = ColorFilter.tint(Color(0xFFFF2D55), BlendMode.Modulate))
                    drawImage(img, dstOffset = IntOffset(split.toInt(), 0), dstSize = full, filterQuality = FilterQuality.None, alpha = 0.35f,
                        colorFilter = ColorFilter.tint(Color(0xFF00E5FF), BlendMode.Modulate))
                    if (slice % 5 == 0) drawRect(Color.White, alpha = 0.12f)
                }
                t < shatter + 1.8f -> {
                    // the screen shatters toward you, revealing the 3D world behind it
                    val u = (t - shatter) / 1.8f
                    val cols = 14; val rows = 28
                    val tw = vw / cols.toFloat(); val th = vh / rows.toFloat()
                    val sx = size.width / vw; val sy = size.height / vh
                    for (r in 0 until rows) for (c in 0 until cols) {
                        val sd = shards[r * cols + c]
                        val delay = (abs(c - cols / 2f) / cols + abs(r - rows / 2f) / rows) * 0.25f
                        val k = easeIn(((u - delay) / (1f - delay)).coerceIn(0f, 1f))
                        if (k >= 1f) continue
                        val cx = (c + .5f) * tw * sx; val cy = (r + .5f) * th * sy
                        val dirX = (cx - size.width / 2) / size.width + sd[0] * 0.3f
                        val dirY = (cy - size.height / 2) / size.height + sd[1] * 0.3f
                        val z = 1f + k * 3f * sd[3]
                        translate(cx + dirX * size.width * k * 1.6f, cy + dirY * size.height * k * 1.6f + k * k * 300f) {
                            rotate(sd[2] * 220f * k, Offset.Zero) {
                                scale(z, z, Offset.Zero) {
                                    drawImage(
                                        img, srcOffset = IntOffset((c * tw).toInt(), (r * th).toInt()), srcSize = IntSize(tw.toInt() + 1, th.toInt() + 1),
                                        dstOffset = IntOffset((-tw * sx / 2).toInt(), (-th * sy / 2).toInt()), dstSize = IntSize((tw * sx).toInt() + 1, (th * sy).toInt() + 1),
                                        filterQuality = FilterQuality.None, alpha = 1f - k,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // the HD Rin: flickering in during the power-up, then leaping up as the world breaks
        if (hdRin && t < shatter + 2.6f) {
            val scale = maxWidth / vw.toFloat()
            val baseX = (world.rinX(t.coerceAtMost(shatter)) - world.camX(t.coerceAtMost(shatter)))
            val sprite = scale * RinRig.H.toFloat()
            val fly = easeInOut((t - shatter) / 1.2f)
            val x = scale * baseX + (maxWidth / 2 - scale * baseX) * fly - sprite * 0.43f
            val feet = scale * (world.ground + world.panY(t)).toFloat()
            val y = feet - sprite + (maxHeight * 0.3f - feet + sprite * 0.5f) * fly
            val gone = clamp01((t - shatter - 1.8f) / 0.8f)
            RinSprite(
                if (t < shatter) Pose.CHEER else Pose.CELEBRATE, sprite, forcePixel = true,
                modifier = Modifier.offset(x, y).graphicsLayer { alpha = 1f - gone; scaleX = 1f - 0.6f * gone; scaleY = scaleX; translationY = -gone * 400f },
            )
        }
        if (t < shatter) GameHud(t, maxWidth, maxHeight, world)
    }
}

@Composable
private fun GameHud(t: Float, w: Dp, h: Dp, world: PixelWorld) {
    val shadow = TextStyle(shadow = Shadow(Color.Black, Offset(3f, 3f), 0f))
    val coins = Score.hits.count { t >= it.toFloat() }
    val grabbed = t >= Score.GRAB.toFloat()
    Box(Modifier.fillMaxSize()) {
        // top HUD, like a real game
        Row(Modifier.fillMaxWidth().padding(top = 44.dp, start = 22.dp, end = 22.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            HudCell("RIN", "×03", shadow)
            HudCell("COINS", "%02d".format(coins), shadow)
            HudCell("WORLD", if (grabbed) "1-4" else "1-3", shadow, highlight = grabbed)
            HudCell("TIME", "%03d".format((400 - t * 4).toInt().coerceAtLeast(0)), shadow)
        }
        // title card over the night sky
        val title = clamp01(t / 0.6f) * (1f - clamp01((t - 2.4f) / 0.5f))
        if (title > 0f) Column(Modifier.align(Alignment.Center).padding(bottom = 120.dp).graphicsLayer { alpha = title }, horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("RINTOS", fontFamily = RintFonts.Pixel, fontSize = 46.sp, color = Color.White, style = shadow)
            Spacer(Modifier.height(8.dp))
            Text("a game about an upgrade", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = GOLD, style = shadow)
        }
        // each ? block pops a feature: a big game-style callout in the sky
        val scale = w / world.vw.toFloat()
        for (i in 0 until 6) {
            val d = t - Score.hits[i].toFloat()
            if (d !in 0f..1.95f) continue
            val a = clamp01(d / 0.08f) * (1f - clamp01((d - 1.6f) / 0.3f))
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 150.dp).graphicsLayer {
                    alpha = a; val s = easeOutBack(d / 0.28f); scaleX = s; scaleY = s; translationY = -d * 14f
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.material3.Text("+1", fontFamily = RintFonts.Pixel, fontSize = 18.sp, color = GOLD, style = shadow)
                Spacer(Modifier.height(6.dp))
                Text(blockLabels[i].uppercase(), fontFamily = RintFonts.Pixel, fontSize = 22.sp, color = Color.White, style = shadow, textAlign = TextAlign.Center,
                    modifier = Modifier.background(Color(0xCC0B0F24)).border(3.dp, Color.White).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        // the big one
        val g = t - Score.GRAB.toFloat()
        if (g in 0f..2.2f) Column(Modifier.align(Alignment.Center).padding(bottom = 180.dp).graphicsLayer {
            val s = easeOutBack(g / 0.3f); scaleX = s; scaleY = s; alpha = 1f - clamp01((g - 1.7f) / 0.4f)
        }, horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("POWER UP!", fontFamily = RintFonts.Pixel, fontSize = 36.sp, color = GOLD, style = shadow)
            Text("rintos 1.4 unlocked", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White, style = shadow)
        }
        val big = t - Score.hits[6].toFloat()
        if (big in -2f..0f) Text("?!", fontFamily = RintFonts.Pixel, fontSize = 20.sp, color = GOLD, style = shadow,
            modifier = Modifier.offset(scale * (PixelWorld.blockX(6) - world.camX(t)) - 12.dp, scale * (world.blockTop(6) + world.panY(t)).toFloat() - 34.dp)
                .graphicsLayer { alpha = clamp01((big + 2f) / 0.3f); translationY = sin(t * 10f) * 6f })
        if (t in bars(Score.UPGRADE)..Score.SHATTER.toFloat()) Text(
            if ((t * 8).toInt() % 2 == 0) "ERROR: TOO MUCH UPGRADE" else "ERR0R: T00 MUCH UPGR4DE", fontFamily = RintFonts.Pixel, fontSize = 13.sp,
            color = Color(0xFFFF4F6A), style = shadow, modifier = Modifier.align(Alignment.Center).padding(bottom = 260.dp),
        )
    }
}

@Composable
private fun HudCell(label: String, value: String, style: TextStyle, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.Text(label, fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = Color.White, style = style)
        androidx.compose.material3.Text(value, fontFamily = RintFonts.Pixel, fontSize = 17.sp, color = if (highlight) GOLD else Color.White, style = style)
    }
}

// ═══════════════════════════════ ACT II: HD ═══════════════════════════════

private class Feature(val word: String, val title: String, val sub: String)

private val features = listOf(
    Feature("EVERYWHERE", "the notch, in every app", "floats over your apps, music and all"),
    Feature("REMEMBERS", "Rin remembers you", "tell him things. he keeps them. on your phone only"),
    Feature("LIGHTER", "Saver Home", "a lighter home for weak phones"),
    Feature("WALLPAPERS", "six new wallpapers", "three of them are alive"),
    Feature("LOCK SCREEN", "your lock screen, your way", "neon, fonts, sizes, backgrounds"),
    Feature("WIDGETS", "five new widgets", "countdown, world clock, rin's thought, stopwatch…"),
    Feature("STARTUP", "startup screens", "a new hello after every restart"),
    Feature("YOUR COLOR", "your color, everywhere", "even Rin wears it"),
)

@Composable
private fun HdScene(time: () -> Float) {
    val t = time()
    val f = ((barOf(t) - Score.HD) / 2).coerceIn(0, 7)
    val lt = t - bars(Score.HD + f * 2)
    val feat = features[f]
    Box(Modifier.fillMaxSize()) {
        Stage(t, tint = if (f == 7) accentCycle(lt) else ACCENT)
        // the giant outlined word drifting behind everything
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val word = I18n.t(feat.word)
            // sized so the whole word spans the screen, stacked twice (filled ghost + outline)
            val fs = (maxWidth.value / (word.length * 0.68f)).coerceIn(34f, 120f).sp
            Column(Modifier.align(Alignment.Center).padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (row in 0 until 3) {
                    androidx.compose.material3.Text(
                        word, maxLines = 1, softWrap = false,
                        style = TextStyle(fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = fs, lineHeight = fs * 0.95f,
                            drawStyle = Stroke(width = 3f), color = ACCENT.copy(alpha = if (row == 1) 0.55f else 0.2f)),
                        modifier = Modifier.wrapContentSize(unbounded = true).graphicsLayer {
                            val dir = if (row % 2 == 0) 1f else -1f
                            translationX = dir * (lt - 2f) * 18f + (1f - easeOut(lt / 0.5f)) * dir * 300f
                            alpha = clamp01(lt / 0.3f) * (1f - clamp01((lt - 3.6f) / 0.4f))
                        },
                    )
                }
            }
        }
        when (f) {
            3 -> WallpaperMontage(lt)
            5 -> WidgetRain(lt)
            else -> Phone3D(lt, Modifier.align(Alignment.Center).padding(top = 90.dp)) {
                when (f) {
                    0 -> NotchDemo(lt)
                    1 -> MemoryDemo(lt)
                    2 -> SaverDemo(lt)
                    4 -> LockFlipbook(lt)
                    6 -> StartupFrame(StartupStyle.RINTOS, (lt / 4f * 1.05f).coerceAtMost(0.88f))
                    else -> ColorDemo(lt)
                }
            }
        }
        FeatureTitle(feat, lt, Modifier.align(Alignment.TopCenter).padding(top = 60.dp))
    }
}

private val swatches = listOf(0xFF3B7CFF, 0xFFFF6FB5, 0xFF3DDC84, 0xFFFFB020, 0xFF8E7CFF, 0xFFFF4F4F, 0xFF00C8FF, 0xFFFFD60A).map { Color(it) }
private fun accentCycle(lt: Float) = swatches[(lt / BEAT).toInt().coerceIn(0, 7)]

@Composable
private fun FeatureTitle(feat: Feature, lt: Float, modifier: Modifier) {
    val out = clamp01((lt - 3.65f) / 0.3f)
    Column(modifier.padding(horizontal = 20.dp).graphicsLayer { alpha = 1f - out; translationY = -out * 40f }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NEW", color = Color.White, fontFamily = RintFonts.Pixel, fontSize = 11.sp,
            modifier = Modifier.graphicsLayer { val s = easeOutBack(lt / 0.25f); scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(50)).background(ACCENT).padding(horizontal = 12.dp, vertical = 4.dp))
        Spacer(Modifier.height(10.dp))
        // the title slides up word by word
        val words = I18n.t(feat.title).split(" ")
        Row {
            words.forEachIndexed { i, w ->
                val k = easeOut((lt - 0.08f - i * 0.07f) / 0.3f)
                androidx.compose.material3.Text(
                    "$w ", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 27.sp, color = Color.White,
                    modifier = Modifier.graphicsLayer { alpha = k; translationY = (1f - k) * 50f },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(feat.sub, fontFamily = RintFonts.Inter, fontSize = 13.sp, color = Color.White.copy(alpha = 0.65f), textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = clamp01((lt - 0.4f) / 0.3f) })
    }
}

/** A lit stage: god rays from above, a perspective floor grid rushing at you, drifting dust. */
@Composable
private fun Stage(t: Float, tint: Color) {
    val dust = remember { val r = Random(4); List(70) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat(), r.nextFloat() * 6f) } }
    Canvas(Modifier.fillMaxSize()) {
        val k = kickOf(t)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF04050D), lerp(Color(0xFF070A1E), tint, 0.22f), Color(0xFF05060F))))
        // god rays
        val top = Offset(size.width / 2, -size.height * 0.1f)
        for (i in 0 until 7) {
            val a = -0.9f + i * 0.3f + sin(t * 0.4f + i) * 0.08f
            val len = size.height * 1.3f
            val w = 0.07f
            val p = Path().apply {
                moveTo(top.x, top.y)
                lineTo(top.x + sin(a - w) * len, top.y + cos(a - w) * len)
                lineTo(top.x + sin(a + w) * len, top.y + cos(a + w) * len)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(tint.copy(alpha = 0.16f + 0.1f * k), Color.Transparent), top, len))
        }
        // floor grid in perspective
        val horizon = size.height * 0.64f
        val lineC = tint.copy(alpha = 0.35f)
        for (i in -12..12) {
            drawLine(lineC, Offset(size.width / 2 + i * 14f, horizon), Offset(size.width / 2 + i * size.width * 0.28f, size.height), 1.5f)
        }
        val scroll = (t * 1.6f) % 1f
        for (j in 0 until 14) {
            val z = (j + 1 - scroll) / 14f
            val y = horizon + (size.height - horizon) * z * z
            drawLine(lineC.copy(alpha = 0.35f * z), Offset(0f, y), Offset(size.width, y), 1.5f)
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF04050D), Color.Transparent), horizon - 40f, horizon + 120f), Offset(0f, horizon - 40f), Size(size.width, 160f))
        // dust drifting up
        dust.forEach { d ->
            val y = ((d[1] - t * 0.03f * d[2]) % 1f + 1f) % 1f
            drawCircle(Color.White.copy(alpha = 0.25f * d[2] * (0.5f + 0.5f * sin(t * 2f + d[3]))), 1.5f + d[2] * 2f, Offset(d[0] * size.width, y * size.height))
        }
    }
}

/** The phone swings in on the downbeat, floats, and swings out; its content is a real screen. */
@Composable
private fun Phone3D(lt: Float, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current.density
    val inK = easeOutBack(lt / 0.45f)
    val outK = easeIn((lt - 3.6f) / 0.4f)
    Box(modifier, contentAlignment = Alignment.Center) {
        // soft floor shadow
        Canvas(Modifier.size(260.dp, 40.dp).offset(y = 250.dp).graphicsLayer { alpha = 0.6f * (1f - outK) }) {
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)), size = size)
        }
        Box(
            Modifier.size(230.dp, 478.dp).graphicsLayer {
                cameraDistance = 14f * density
                rotationY = (1f - inK) * -75f + outK * 80f + sin(lt * 1.2f) * 7f
                rotationX = 6f + cos(lt * 0.9f) * 3f
                translationY = sin(lt * 1.6f) * 8f
                val s = 0.9f + 0.1f * clamp01(inK)
                scaleX = s; scaleY = s
                alpha = clamp01(lt / 0.12f) * (1f - outK)
            }.clip(RoundedCornerShape(34.dp)).background(Color.Black).border(3.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(34.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.requiredSize(390.dp, 811.dp).graphicsLayer { val k = 230f / 390f; scaleX = k; scaleY = k }, content = content)
            // glass reflection
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Transparent), Offset(0f, 0f), Offset(size.width, size.height * 0.6f)))
            }
        }
    }
}

// ─── demos (each lays out as a real 390×811 screen) ───

@Composable
private fun FakeApp() {
    Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F7))) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(listOf(Color(0xFF4A7BFF), Color(0xFF3A63D8)))))
        repeat(9) { i ->
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(listOf(Color(0xFFFFB3C7), Color(0xFFB3D4FF), Color(0xFFC9F2C7))[i % 3]))
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(Modifier.width((150 + (i * 37) % 80).dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF9AA3BC)))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width((220 - (i * 23) % 90).dp).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFD5DAE8)))
                }
            }
        }
    }
}

@Composable
private fun NotchDemo(lt: Float) {
    // pill → live music → expanded → pill, on the beat
    val stage = (lt / BEAT).toInt()
    val live = easeOutBack(((lt - BEAT * 2) / 0.35f))
    val open = easeOutBack(((lt - BEAT * 4) / 0.4f)) * (1f - easeInOut((lt - BEAT * 7) / 0.35f))
    val w = 130f + 110f * clamp01(live) + 110f * open
    val h = 38f + 170f * open
    Box(Modifier.fillMaxSize()) {
        FakeApp()
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp).size(w.dp, h.dp)
                .clip(RoundedCornerShape((19f + 17f * open).dp)).background(Color.Black),
        ) {
            if (open > 0.5f) Column(Modifier.padding(18.dp).graphicsLayer { alpha = clamp01((open - 0.5f) * 2) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(ACCENT, PINK))))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        androidx.compose.material3.Text("Upgrade", color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        androidx.compose.material3.Text("Rin", color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Inter, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                androidx.compose.material3.Text("♪ we're on, we're on ♪", color = Color.White, fontFamily = RintFonts.Terminal, fontSize = 26.sp)
            } else if (live > 0.5f) Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(ACCENT, PINK))))
                Spacer(Modifier.weight(1f))
                Row(Modifier.height(18.dp), verticalAlignment = Alignment.Bottom) {
                    for (i in 0 until 4) {
                        val v = 0.3f + 0.7f * abs(sin(lt * (7f + i * 1.7f) + i))
                        Box(Modifier.padding(horizontal = 2.dp).width(4.dp).fillMaxHeight(v).clip(RoundedCornerShape(2.dp)).background(ACCENT))
                    }
                }
            } else Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("9:41", color = Color.White, fontFamily = RintFonts.Terminal, fontSize = 20.sp)
            }
        }
        if (stage >= 1) Text(
            "over any app", color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).clip(RoundedCornerShape(50)).background(ACCENT).padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

private val memories = listOf("exam on friday", "loves purple", "plays guitar", "sleeps way too late")

@Composable
private fun MemoryDemo(lt: Float) {
    Column(Modifier.fillMaxSize().background(Color(0xFF101119)).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(50.dp))
        RinSprite(if ((lt / BEAT).toInt() % 2 == 0) Pose.NOD else Pose.HAPPY, 150.dp, forcePixel = true)
        Spacer(Modifier.height(20.dp))
        if (lt > 0.3f) ChatBubble("remember my exam is on friday", true, lt - 0.3f)
        if (lt > 0.9f) ChatBubble("got it. you've got this.", false, lt - 0.9f)
        Spacer(Modifier.height(26.dp))
        memories.forEachIndexed { i, m ->
            val at = 1.4f + i * BEAT
            if (lt < at) return@forEachIndexed
            val k = easeOutBack((lt - at) / 0.3f)
            Row(
                Modifier.padding(vertical = 6.dp).graphicsLayer { scaleX = k; scaleY = k; rotationZ = (if (i % 2 == 0) -2f else 2f) }
                    .clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.07f)).border(1.5.dp, ACCENT, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MEMORY", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = ACCENT)
                Spacer(Modifier.width(12.dp))
                Text(m, fontFamily = RintFonts.Inter, fontSize = 18.sp, color = Color.White)
            }
        }
    }
}

@Composable
private fun ChatBubble(text: String, user: Boolean, age: Float) {
    val k = easeOutBack(age / 0.3f)
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text, color = Color.White, fontFamily = RintFonts.Inter, fontSize = 18.sp,
            modifier = Modifier.graphicsLayer { scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (user) 1f else 0f, 1f) }
                .clip(RoundedCornerShape(20.dp)).background(if (user) ACCENT else Color(0xFF2A2C38)).padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun SaverDemo(lt: Float) {
    val fold = easeInOut((lt - 2.2f) / 0.7f)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            val s = 1f - 0.985f * fold * fold; scaleX = s; scaleY = s
            shape = RoundedCornerShape(percent = (50 * fold).toInt()); clip = true
        }) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2A6B), Color(0xFF0A0E1E)))))
            Column(Modifier.fillMaxSize().padding(22.dp)) {
                Spacer(Modifier.height(60.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(2) { i ->
                        val f = easeIn((lt - 0.5f - i * 0.2f) / 0.7f)
                        Box(Modifier.weight(1f).height(160.dp).graphicsLayer {
                            translationY = -f * 900f; rotationZ = f * (if (i == 0) -24f else 24f); alpha = 1f - f; scaleX = 1f - 0.4f * f; scaleY = scaleX
                        }.clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.14f)))
                    }
                }
                Spacer(Modifier.height(40.dp))
                repeat(4) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(4) { k -> Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(swatches[(k + it) % swatches.size])) }
                    }
                }
            }
        }
        if (fold > 0.9f) {
            val ring = easeOut((lt - 2.9f) / 0.6f)
            Canvas(Modifier.align(Alignment.Center).size(220.dp)) {
                drawArc(ACCENT, -90f, 360f * 0.14f * ring * 7f, false, style = Stroke(10f, cap = StrokeCap.Round))
                drawCircle(ACCENT, size.minDimension * 0.28f * (1f + 0.05f * sin(lt * 5f)))
            }
            Text("saver mode · 14%", fontFamily = RintFonts.Pixel, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).padding(top = 300.dp))
        }
    }
}

@Composable
private fun WallpaperMontage(lt: Float) {
    val i = (lt / BEAT).toInt().coerceIn(0, 7)
    val inBeat = (lt - i * BEAT) / BEAT
    Box(Modifier.fillMaxSize()) {
        // previous one underneath, new one wipes in from the bottom
        Box(Modifier.fillMaxSize()) { WallpaperFrame((i - 1).coerceAtLeast(0), lt) }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val k = easeOut(inBeat / 0.35f)
            clip = true
            shape = object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(size: Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                    androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, size.height * (1f - k), size.width, size.height))
            }
        }) { WallpaperFrame(i, lt) }
        // a tiny home screen on top, so it reads as a wallpaper
        Column(Modifier.align(Alignment.Center).padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("9:41", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Light, fontSize = 88.sp, color = Color.White,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 6f), 20f)))
            Spacer(Modifier.height(170.dp))
            Row(Modifier.clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.18f)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { k -> Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(swatches[k].copy(alpha = 0.9f))) }
            }
        }
    }
}

@Composable
private fun WallpaperFrame(i: Int, lt: Float) {
    when (i % 8) {
        0 -> LiveWallpaper(WallpaperMode.STARFIELD, ACCENT)
        1 -> Image(painterResource(R.drawable.wp_sunset), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        2 -> LiveWallpaper(WallpaperMode.RAIN, ACCENT)
        3 -> Image(painterResource(R.drawable.wp_synth), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        4 -> LiveWallpaper(WallpaperMode.WAVES, ACCENT)
        5 -> Image(painterResource(R.drawable.wp_peaks), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        6 -> Image(painterResource(R.drawable.wp_pixel), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else -> LiveWallpaper(WallpaperMode.STARFIELD, PINK)
    }
}

@Composable
private fun LockFlipbook(lt: Float) {
    val look = LocalRint.current
    val i = (lt / BEAT).toInt().coerceIn(0, 7)
    val styles = listOf(LockStyle.CLASSIC, LockStyle.BLOCKS, LockStyle.POSTER, LockStyle.STACKED, LockStyle.TERMINAL, LockStyle.ANALOG, LockStyle.WORDS, LockStyle.NEON)
    val bgs = listOf(LockBg.WALLPAPER, LockBg.GLOW, LockBg.WALLPAPER, LockBg.BLURRED, LockBg.BLACK, LockBg.GLOW, LockBg.WALLPAPER, LockBg.GLOW)
    val lockLook = look.copy(cfg = look.cfg.copy(lock = look.cfg.lock.copy(style = styles[i], background = bgs[i], greeting = i == 7)))
    CompositionLocalProvider(LocalRint provides lockLook) { dev.rint.launcher.lock.LockScreen({}, {}) }
    // a camera-flash on every flip
    val f = (lt - i * BEAT)
    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f * exp(-f * 14f))))
}

@Composable
private fun WidgetRain(lt: Float) {
    val drops = listOf(
        Triple("thought", 0, 0) to Pair(4, 2), Triple("worldclock", 0, 2) to Pair(2, 2), Triple("countdown", 2, 2) to Pair(2, 2),
        Triple("stopwatch", 0, 4) to Pair(4, 1), Triple("device", 0, 5) to Pair(4, 1),
    )
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 190.dp, start = 18.dp, end = 18.dp)) {
        val cell = maxWidth / 4
        drops.forEachIndexed { i, (spec, sz) ->
            val (type, gx, gy) = spec
            val (w, h) = sz
            val at = 0.2f + i * BEAT
            if (lt < at) return@forEachIndexed
            val d = lt - at
            val fall = easeIn(d / 0.3f)
            val squash = if (d in 0.3f..0.55f) 1f - 0.12f * sin((d - 0.3f) / 0.25f * PI.toFloat()) else 1f
            val ws = WidgetRegistry.find(type) ?: return@forEachIndexed
            Box(
                Modifier.offset(cell * gx + 4.dp, cell * gy * 0.72f + 4.dp).size(cell * w - 8.dp, cell * h * 0.72f - 8.dp)
                    .graphicsLayer {
                        translationY = -(1f - fall) * 1400f
                        scaleY = squash; scaleX = 2f - squash
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        alpha = 1f - clamp01((lt - 3.6f) / 0.4f)
                    }
                    .clip(RoundedCornerShape(24.dp)).background(Color(0xFF181B28)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
            ) { ws.content(WidgetCtx("film_$type", w, if (h == 1) 1 else 2, preview = true)) }
        }
    }
}

@Composable
private fun ColorDemo(lt: Float) {
    val look = LocalRint.current
    val c = accentCycle(lt)
    val cfg = look.cfg.copy(look = look.cfg.look.copy(accent = c.toArgb().toLong() and 0xFFFFFFFFL))
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        MockPhone(cfg, width = 390.dp)
        RinSprite(Pose.DANCE, 170.dp, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp, bottom = 120.dp), accentOverride = c.toArgb(), forcePixel = true)
    }
}

// ═══════════════════════════════ ACT III ═══════════════════════════════

private val starLines = listOf("made by one person.", "tested on one phone.", "rebuilt a hundred times.", "for you.")

@Composable
private fun StarsScene(time: () -> Float) {
    val t = time()
    val j = (barOf(t) - Score.STARS).coerceIn(0, 3)
    val lt = t - bars(Score.STARS + j)
    Box(Modifier.fillMaxSize()) {
        LiveWallpaper(WallpaperMode.STARFIELD, ACCENT)
        // a dark hill with Rin sitting on top, looking up
        Canvas(Modifier.fillMaxSize()) {
            val p = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, size.height * 0.84f)
                cubicTo(size.width * 0.3f, size.height * 0.72f, size.width * 0.7f, size.height * 0.72f, size.width, size.height * 0.82f)
                lineTo(size.width, size.height); close()
            }
            drawPath(p, Color(0xFF05060C))
        }
        RinSprite(Pose.BACK, 130.dp, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp), forcePixel = true)
        Text(
            starLines[j], fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 30.sp, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 180.dp).graphicsLayer {
                alpha = clamp01(lt / 0.5f) * (1f - clamp01((lt - 1.5f) / 0.4f)); translationY = (1f - easeOut(lt / 0.7f)) * 30f
            },
        )
    }
}

private val showPoses = listOf(
    Pose.CELEBRATE to "celebrate", Pose.SING to "sing", Pose.GUITAR_SOLO to "shred", Pose.SPIN to "spin",
    Pose.DANCE to "dance", Pose.WINK to "wink", Pose.LAUGH to "laugh", Pose.PROUD to "proud",
    Pose.EAT to "snack", Pose.PURR to "purr", Pose.SCARED to "scared", Pose.SNEEZE to "sneeze",
    Pose.CHEER to "cheer", Pose.WAVE to "wave", Pose.LOVE to "love", Pose.CELEBRATE to "40+ animations",
)

@Composable
private fun ShowScene(time: () -> Float) {
    val t = time()
    val lt = t - bars(Score.SHOW)
    val i = (lt / (BEAT * 2)).toInt().coerceIn(0, showPoses.size - 1)
    val (pose, label) = showPoses[i]
    val pt = lt - i * BEAT * 2
    val look = LocalRint.current
    val crowdHead = remember(look.colors.accent) { crowdBitmap() }
    val confetti = remember { val r = Random(8); List(160) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat(), r.nextFloat() * 6, r.nextInt(5).toFloat()) } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val k = kickOf(t)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF05040C), Color(0xFF120A26), Color(0xFF05040C))))
            // sweeping spotlights
            val cols = listOf(ACCENT, PINK, Color.White, ACCENT, GOLD)
            for (b in 0 until 5) {
                val base = Offset(size.width * (0.1f + b * 0.2f), -20f)
                val a = sin(t * (0.9f + b * 0.23f) + b * 1.7f) * 0.55f
                val len = size.height * 1.1f
                val p = Path().apply {
                    moveTo(base.x, base.y)
                    lineTo(base.x + sin(a - 0.09f) * len, base.y + cos(a - 0.09f) * len)
                    lineTo(base.x + sin(a + 0.09f) * len, base.y + cos(a + 0.09f) * len)
                    close()
                }
                drawPath(p, Brush.radialGradient(listOf(cols[b].copy(alpha = 0.3f + 0.15f * k), Color.Transparent), base, len))
            }
            // the stage floor
            val floorY = size.height * 0.68f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1433), Color(0xFF07060F)), floorY, size.height), Offset(0f, floorY), Size(size.width, size.height - floorY))
            drawCircle(Brush.radialGradient(listOf(ACCENT.copy(alpha = 0.35f + 0.25f * k), Color.Transparent), Offset(size.width / 2, floorY + 30f), size.width * 0.45f),
                size.width * 0.45f, Offset(size.width / 2, floorY + 30f))
        }
        // Rin, center stage, and his reflection on the floor
        Column(Modifier.align(Alignment.Center).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer { val s = easeOutBack(pt / 0.25f); scaleX = 0.8f + 0.2f * s; scaleY = scaleX }) {
                RinSprite(pose, 230.dp, timeOffset = pt, forcePixel = true)
            }
            Box(Modifier.graphicsLayer { scaleY = -0.5f; alpha = 0.18f; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f) }) {
                RinSprite(pose, 230.dp, timeOffset = pt, forcePixel = true)
            }
        }
        Text(label, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = if (i == showPoses.size - 1) 34.sp else 30.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 120.dp).graphicsLayer { val s = easeOutBack(pt / 0.2f); scaleX = s; scaleY = s })
        Text("RIN'S NEW MOVES", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = ACCENT,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp))
        // the crowd: rows of little Rins bouncing to the beat
        Canvas(Modifier.fillMaxSize()) {
            val ph = beatPhase(t)
            for (row in 0 until 3) {
                val n = 7 + row
                val y = size.height * (0.8f + row * 0.065f)
                val sz = 90f + row * 24f
                for (c in 0 until n) {
                    val x = size.width * (c + 0.5f + (row % 2) * 0.3f) / n - sz / 2
                    val bounce = abs(sin((ph + c * 0.37f + row * 0.21f) * PI.toFloat())) * 16f * (if (Score.kick(barOf(t))) 1f else 0.3f)
                    drawImage(crowdHead, dstOffset = IntOffset(x.toInt(), (y - bounce).toInt()), dstSize = IntSize(sz.toInt(), (sz * 0.9f).toInt()),
                        filterQuality = FilterQuality.None, colorFilter = ColorFilter.tint(lerp(Color(0xFF2A2350), ACCENT, 0.35f - 0.1f * row), BlendMode.SrcIn))
                }
            }
            // confetti from bar 4 of the show
            if (lt > bars(4)) {
                val ct = lt - bars(4)
                confetti.forEach { c ->
                    val y = ((c[1] * -1f) + ct * 0.25f * c[2]) % 1.2f
                    if (y < 0f) return@forEach
                    val x = c[0] * size.width + sin(ct * 2f + c[3]) * 30f
                    rotate(ct * 200f * c[2] + c[3] * 50f, Offset(x, y * size.height)) {
                        drawRect(listOf(ACCENT, PINK, GOLD, Color.White, Color(0xFF3DDC84))[c[4].toInt()], Offset(x - 6f, y * size.height - 3f), Size(12f, 6f))
                    }
                }
            }
        }
    }
}

private fun crowdBitmap(): androidx.compose.ui.graphics.ImageBitmap {
    val rig = RinRig()
    val p = RinParams()
    animateRin(Pose.HEAD, 0.3f, 0f, p)
    rig.render(p)
    val w = RinRig.HEAD_W; val h = RinRig.HEAD_H
    val px = IntArray(w * h) { rig.pixels[(it / w + RinRig.HEAD_Y) * RinRig.W + it % w + RinRig.HEAD_X] }
    return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ═══════════════════════════════ ACT IV ═══════════════════════════════

private class Spark(val a: Float, val v: Float, val c: Int)

@Composable
private fun FinaleScene(time: () -> Float) {
    val t = time()
    val bursts = remember { val r = Random(12); Score.fireworks.map { fw -> Triple(fw.toFloat(), Offset(0.15f + r.nextFloat() * 0.7f, 0.12f + r.nextFloat() * 0.3f), List(70) { Spark(r.nextFloat() * 6.283f, 0.5f + r.nextFloat() * 0.5f, r.nextInt(4)) }) } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val cols = listOf(ACCENT, PINK, GOLD, Color.White)
            bursts.forEach { (at, pos, sparks) ->
                val d = t - at
                // the rocket climbing
                if (d in -0.5f..0f) {
                    val k = (d + 0.5f) / 0.5f
                    val p = Offset(pos.x * size.width, size.height - (size.height - pos.y * size.height) * k)
                    drawCircle(Color.White, 4f, p)
                    drawLine(Color.White.copy(alpha = 0.4f), p, p + Offset(0f, 60f), 2f)
                }
                if (d !in 0f..1.8f) return@forEach
                val c = Offset(pos.x * size.width, pos.y * size.height)
                val r = size.width * 0.28f * easeOut(d / 0.9f)
                val fade = 1f - clamp01((d - 0.8f) / 1f)
                sparks.forEach { s ->
                    val p = c + Offset(cos(s.a) * r * s.v, sin(s.a) * r * s.v + d * d * 90f)
                    drawLine(cols[s.c].copy(alpha = fade * 0.5f), p, p - Offset(cos(s.a) * 22f, sin(s.a) * 22f - 6f), 3f, StrokeCap.Round)
                    drawCircle(cols[s.c].copy(alpha = fade), 3.5f, p)
                }
                if (d < 0.15f) drawCircle(Color.White.copy(alpha = 1f - d / 0.15f), 40f, c)
            }
        }
        val lt = t - bars(Score.FINALE)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Text("RintOS 1.4", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 40.sp, color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = clamp01((lt - 2f) / 0.6f); val s = 0.9f + 0.1f * easeOut((lt - 2f) / 0.6f); scaleX = s; scaleY = s })
            Text("the upgrade.", fontFamily = RintFonts.Pixel, fontSize = 13.sp, color = ACCENT, modifier = Modifier.graphicsLayer { alpha = clamp01((lt - 2.6f) / 0.5f) })
        }
    }
}

@Composable
private fun CreditsScene(time: () -> Float) {
    val t = time()
    val lt = t - bars(Score.CREDITS)
    Box(Modifier.fillMaxSize()) {
        PixelLogo(2.2f + lt * 1.3f, Modifier.fillMaxSize().padding(bottom = 120.dp))
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("made by Carrot", "starring Rin", "music: synthesized live · 0 audio files", "thanks for playing.").forEachIndexed { i, c ->
                Text(c, fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = if (i == 3) 1f else 0.7f),
                    modifier = Modifier.padding(vertical = 3.dp).graphicsLayer { alpha = clamp01((lt - 0.6f - i * 0.35f) / 0.4f) })
            }
        }
    }
}
