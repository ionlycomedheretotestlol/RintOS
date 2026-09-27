package dev.rint.launcher.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.mascot.SpeechBubble
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import dev.rint.launcher.widgets.WidgetCtx
import dev.rint.launcher.widgets.WidgetRegistry
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

private val BAR = Score.BAR.toFloat()
private val BEAT = Score.BEAT.toFloat()
private var ACCENT = Color(RINT_BLUE.toInt())
private val INK = Color(0xFF03040A)

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
                // no audio device (or it hasn't started): fall back to the wall clock
                clock.floatValue = if (audio > 0.05f || wall < 0.6f) audio else wall
            }
        }
        onDone()
    }
    Film(time = { clock.floatValue })
}

/**
 * The RintOS 1.4 film, driven by [time]. Only the scene switch recomposes on bar changes; shake,
 * flashes and the letterbox are read in the draw/layer phase.
 */
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
                val s = (18f * exp(-sinceImpact(t) * 5f) + 2.5f * kickOf(t)) * density
                translationX = sin(t * 97f) * s; translationY = cos(t * 71f) * s * 0.8f
                val punch = 1f + 0.015f * kickOf(t) + 0.06f * exp(-sinceImpact(t) * 6f)
                scaleX = punch; scaleY = punch
            }
        ) {
            when (Score.part(bar)) {
                Score.Part.COLD -> ColdScene(time)
                Score.Part.CHIP -> ChipScene(time)
                Score.Part.UPGRADE -> UpgradeScene(time)
                Score.Part.NEW -> NewScene(time)
                Score.Part.BREAK -> BreakScene(time)
                Score.Part.DROP2 -> ParadeScene(time)
                Score.Part.OUTRO, Score.Part.AFTER -> OutroScene(time)
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
        // the 8-bit section wears a pixel grid, like an old handheld screen
        if (part == Score.Part.CHIP || part == Score.Part.COLD) {
            val a = 0.10f
            var x = 0f
            while (x < size.width) { drawRect(Color.Black.copy(alpha = a), Offset(x, 0f), Size(1.5f, size.height)); x += 6f }
            var y = 0f
            while (y < size.height) { drawRect(Color.Black.copy(alpha = a), Offset(0f, y), Size(size.width, 1.5f)); y += 6f }
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.75f))
        // the drops breathe with the kick
        if (part == Score.Part.NEW || part == Score.Part.DROP2) {
            val k = kickOf(t)
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, ACCENT.copy(alpha = 0.35f * k)), center, size.maxDimension * 0.72f))
        }
        // flash on every impact
        val si = sinceImpact(t)
        if (si < 0.6f) drawRect(Color.White.copy(alpha = exp(-si * 8f)))
        // collapse to a line before each drop
        if ((bar == Score.NEW - 1 || bar == Score.DROP2 - 1) && pb > 0.8f) {
            val k = (pb - 0.8f) * 5f
            drawRect(INK)
            val w = size.width * (1f - easeIn(k))
            drawRect(Color.White, Offset((size.width - w) / 2, size.height / 2 - 2f), Size(w, 4f))
        }
        drawRect(ACCENT.copy(alpha = 0.7f), Offset(0f, size.height - 3f), Size(size.width * clamp01(t / Score.TIMELINE_SECONDS.toFloat()), 3f))
    }
}

// ───────────────────────────── helpers ─────────────────────────────

/**
 * A phone frame. With [real], the content lays out as a true 390×811dp phone screen and is scaled
 * down, exactly like a screenshot; otherwise it lays out at the frame's own size.
 */
@Composable
private fun Phone(modifier: Modifier = Modifier, width: Dp = 230.dp, real: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.size(width, width * 2.08f).clip(RoundedCornerShape(width * 0.13f))
            .background(Color.Black).border(3.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(width * 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        if (real) Box(
            Modifier.requiredSize(390.dp, 811.dp).graphicsLayer { val k = width / 390.dp; scaleX = k; scaleY = k },
            content = content,
        ) else content()
    }
}

@Composable
private fun Caption(text: String, pixel: Boolean, modifier: Modifier = Modifier, big: Boolean = false) {
    Text(
        text, modifier = modifier, textAlign = TextAlign.Center, color = Color.White,
        fontFamily = if (pixel) RintFonts.Pixel else RintFonts.Inter,
        fontWeight = if (pixel) FontWeight.Normal else FontWeight.Black,
        fontSize = if (big) 34.sp else if (pixel) 15.sp else 26.sp, lineHeight = if (big) 36.sp else 28.sp,
    )
}

@Composable
private fun NewTag(modifier: Modifier = Modifier) {
    Text(
        "NEW", modifier = modifier.clip(RoundedCornerShape(50)).background(ACCENT).padding(horizontal = 12.dp, vertical = 4.dp),
        color = Color.White, fontFamily = RintFonts.Pixel, fontSize = 12.sp,
    )
}

/** Local time inside the current bar, in seconds, and the bar's appear curve. */
private fun local(t: Float) = t - floor(t / BAR) * BAR

// ───────────────────────────── bars 0–1: cold open ─────────────────────────────

@Composable
private fun ColdScene(time: () -> Float) {
    val t = time()
    // the startup screen itself, played as the film's first frames
    StartupFrame(StartupStyle.RINTOS, (t / (Score.CHIP * BAR)) * 0.86f)
}

// ───────────────────────────── bars 2–5: 8-bit, everything you love ─────────────────────────────

private val chipCaptions = listOf("your home", "music + live lyrics", "Rin, your AI", "10 lock screens")

@Composable
private fun ChipScene(time: () -> Float) {
    val t = time()
    val j = (barOf(t) - Score.CHIP).coerceIn(0, 3)
    val lt = local(t)
    val look = LocalRint.current
    Column(Modifier.fillMaxSize().padding(top = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val head = dev.rint.launcher.ui.I18n.t("still everything you love")
        val typed = ((t - bars(Score.CHIP)) / 0.05f).toInt().coerceIn(0, head.length)
        androidx.compose.material3.Text(head.take(typed), fontFamily = RintFonts.Pixel, fontSize = 13.sp, color = ACCENT)
        Spacer(Modifier.height(18.dp))
        val a = easeOutBack(lt / 0.35f)
        Phone(Modifier.graphicsLayer { scaleX = 0.8f + 0.2f * a; scaleY = scaleX; alpha = clamp01(lt / 0.15f); rotationZ = (1f - clamp01(a)) * (if (j % 2 == 0) -6f else 6f) }) {
            when (j) {
                0 -> MockPhone(look.cfg, width = 230.dp)
                1 -> MockPhone(look.cfg, width = 230.dp, lyric = "your phone, your rules ♪")
                2 -> AssistantMock(lt)
                else -> Box(Modifier.fillMaxSize()) { RealScreen { dev.rint.launcher.lock.LockScreen({}, {}) } }
            }
        }
        Spacer(Modifier.height(22.dp))
        Caption(chipCaptions[j], pixel = true, modifier = Modifier.graphicsLayer { alpha = clamp01((lt - 0.2f) / 0.2f) })
    }
}

@Composable
private fun AssistantMock(lt: Float) {
    Column(Modifier.fillMaxSize().background(Color(0xFF14151C)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(30.dp))
        RinSprite(if (lt > 1f) Pose.TALK else Pose.LISTEN, 90.dp, talk = if (lt > 1f) 0.7f else 0f)
        Spacer(Modifier.height(16.dp))
        if (lt > 0.3f) Bubble("play something chill", user = true)
        if (lt > 1.0f) Bubble("on it. lofi, coming up.", user = false)
    }
}

@Composable
private fun Bubble(text: String, user: Boolean) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (user) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text, color = Color.White, fontFamily = RintFonts.Inter, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (user) ACCENT else Color(0xFF2A2C38)).padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

// ───────────────────────────── bars 6–7: the upgrade ─────────────────────────────

@Composable
private fun UpgradeScene(time: () -> Float) {
    val t = time()
    val p = clamp01((t - bars(Score.UPGRADE)) / bars(2))
    val cells = remember { val r = Random(9); List(28 * 56) { r.nextFloat() } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // the 8-bit world breaks into blocks that fall away, revealing a smooth gradient underneath
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0E22), ACCENT.copy(alpha = 0.6f), Color(0xFF0B0E22))), alpha = p)
            val cw = size.width / 28
            for (i in cells.indices) {
                val cx = i % 28; val cy = i / 28
                val d = ((p - cells[i] * 0.8f) / 0.2f).coerceIn(0f, 1f)
                if (d >= 1f) continue
                val y = cy * cw + d * d * 600f
                val shade = if ((cx + cy) % 2 == 0) Color(0xFF1B2250) else Color(0xFF141A3E)
                drawRect(shade, Offset(cx * cw, y), Size(cw - 1f, cw - 1f), alpha = 1f - d)
            }
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Caption("UPGRADING", pixel = true)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(220.dp).height(14.dp).border(2.dp, Color.White)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(p).padding(2.dp).background(ACCENT))
            }
            Spacer(Modifier.height(8.dp))
            Text("${(p * 100).toInt()}%", fontFamily = RintFonts.Pixel, fontSize = 13.sp, color = Color.White)
            Spacer(Modifier.height(24.dp))
            Caption("1.3 → 1.4", pixel = false, modifier = Modifier.graphicsLayer { alpha = clamp01((p - 0.4f) / 0.2f) })
        }
    }
}

// ───────────────────────────── bars 8–15: eight new things ─────────────────────────────

private val newTitles = listOf(
    "the notch, in every app", "Rin remembers you", "Saver Home", "live wallpapers",
    "new wallpapers", "5 new widgets", "your lock screen, your way", "startup screens · português",
)

@Composable
private fun NewScene(time: () -> Float) {
    val t = time()
    val j = (barOf(t) - Score.NEW).coerceIn(0, 7)
    val lt = local(t)
    val look = LocalRint.current
    Column(Modifier.fillMaxSize().padding(top = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NewTag(Modifier.graphicsLayer { val s = easeOutBack(lt / 0.25f); scaleX = s; scaleY = s })
        Spacer(Modifier.height(10.dp))
        Caption(newTitles[j], pixel = false, modifier = Modifier.padding(horizontal = 20.dp).graphicsLayer {
            alpha = clamp01(lt / 0.15f); translationY = (1f - easeOut(lt / 0.3f)) * 40f
        })
        Spacer(Modifier.height(18.dp))
        Box(Modifier.graphicsLayer { val a = easeOutBack((lt - 0.08f) / 0.4f); scaleX = 0.85f + 0.15f * a; scaleY = scaleX; alpha = clamp01((lt - 0.05f) / 0.2f) }) {
            when (j) {
                0 -> NotchEverywhere(lt)
                1 -> MemoryDemo(lt)
                2 -> SaverHomeDemo(lt, look.cfg)
                3 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(WallpaperMode.STARFIELD, WallpaperMode.RAIN, WallpaperMode.WAVES).forEachIndexed { i, m ->
                        Phone(Modifier.graphicsLayer { translationY = (1f - easeOut((lt - i * 0.12f) / 0.4f)) * 300f }, width = 110.dp) { LiveWallpaper(m, ACCENT) }
                    }
                }
                4 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.drawable.wp_sunset, R.drawable.wp_synth, R.drawable.wp_peaks).forEachIndexed { i, res ->
                        Phone(Modifier.graphicsLayer { translationY = (1f - easeOut((lt - i * 0.12f) / 0.4f)) * 300f }, width = 110.dp) {
                            Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
                5 -> WidgetsDemo(lt)
                6 -> {
                    val lockLook = look.copy(cfg = look.cfg.copy(lock = look.cfg.lock.copy(style = LockStyle.NEON, background = LockBg.GLOW, greeting = true)))
                    Phone(real = true) { CompositionLocalProvider(LocalRint provides lockLook) { dev.rint.launcher.lock.LockScreen({}, {}) } }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Phone(width = 150.dp, real = true) { StartupFrame(StartupStyle.RINTOS, (0.3f + lt / BAR * 0.55f).coerceAtMost(0.86f)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RinSprite(Pose.WAVE, 90.dp)
                        androidx.compose.material3.Text("Olá! 🇧🇷", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                        androidx.compose.material3.Text("Hello! 🇺🇸", fontFamily = RintFonts.Inter, fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotchEverywhere(lt: Float) {
    val grow = easeOut((lt - 0.5f) / 0.5f)
    Phone {
        // a pretend "other app" with the RintOS notch floating over it
        Column(Modifier.fillMaxSize().background(Color(0xFFF2F3F7))) {
            Box(Modifier.fillMaxWidth().height(90.dp).background(Color(0xFF3A7BFF)))
            repeat(7) { i ->
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Color(0xFFCBD2E6)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Box(Modifier.width((90 + i * 13 % 50).dp).height(9.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF9AA3BC)))
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.width((140 - i * 9 % 40).dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFD5DAE8)))
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(70.dp + 110.dp * grow).height(22.dp + 70.dp * grow)
                .clip(RoundedCornerShape(if (grow > 0.5f) 22.dp else 11.dp)).background(Color.Black),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (grow > 0.6f) Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(ACCENT, Color(0xFFFF6FB5)))))
                Spacer(Modifier.width(8.dp))
                Column {
                    androidx.compose.material3.Text("Rin's Anthem", color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    androidx.compose.material3.Text("RintOS", color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Inter, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MemoryDemo(lt: Float) {
    Column(Modifier.width(290.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RinSprite(if (lt > 1.1f) Pose.NOD else Pose.LISTEN, 96.dp)
        Spacer(Modifier.height(8.dp))
        if (lt > 0.25f) Bubble("remember my exam is on friday", user = true)
        if (lt > 0.8f) Bubble("got it. good luck, you've got this.", user = false)
        if (lt > 1.2f) Row(
            Modifier.padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, ACCENT, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
                .graphicsLayer { val s = easeOutBack((lt - 1.2f) / 0.3f); scaleX = s; scaleY = s },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MEMORY", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = ACCENT)
            Spacer(Modifier.width(10.dp))
            Text("exam on friday", fontFamily = RintFonts.Inter, fontSize = 13.sp, color = Color.White)
        }
    }
}

@Composable
private fun SaverHomeDemo(lt: Float, cfg: dev.rint.launcher.core.RintConfig) {
    // widgets float away one after another, apps stay
    Phone {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF16204A), Color(0xFF0A0E1E)))))
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) { i ->
                    val f = easeIn((lt - 0.3f - i * 0.15f) / 0.6f)
                    Box(Modifier.weight(1f).height(90.dp).graphicsLayer {
                        translationY = -f * 500f; rotationZ = f * (if (i == 0) -20f else 20f); alpha = 1f - f; scaleX = 1f - 0.4f * f; scaleY = scaleX
                    }.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)))
                }
            }
            Spacer(Modifier.height(18.dp))
            repeat(4) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(4) { k -> Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(listOf(ACCENT, Color(0xFFFF6FB5), Color(0xFF3DDC84), Color(0xFFFFB020))[k])) }
                }
            }
        }
    }
}

@Composable
private fun WidgetsDemo(lt: Float) {
    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("thought" to 110.dp, "worldclock" to 110.dp, "stopwatch" to 80.dp).forEachIndexed { i, (type, h) ->
            val spec = WidgetRegistry.find(type) ?: return@forEachIndexed
            Box(
                Modifier.fillMaxWidth().height(h).graphicsLayer { translationX = (1f - easeOut((lt - i * 0.15f) / 0.45f)) * (if (i % 2 == 0) -600f else 600f) }
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xFF1B1E29)),
            ) { spec.content(WidgetCtx("film_$type", 4, if (type == "stopwatch") 1 else 2, preview = true)) }
        }
    }
}

// ───────────────────────────── bars 16–19: the 3D 1.4 ─────────────────────────────

private val breakLines = listOf("rebuilt.", "again.", "for you.", "")

@Composable
private fun BreakScene(time: () -> Float) {
    val t = time()
    val j = (barOf(t) - Score.BREAK).coerceIn(0, 3)
    val lt = local(t)
    Box(Modifier.fillMaxSize()) {
        if (breakLines[j].isNotEmpty()) Caption(
            breakLines[j], pixel = false, big = true,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp).graphicsLayer {
                alpha = clamp01(lt / 0.4f) * (1f - clamp01((lt - 1.6f) / 0.4f)); translationY = (1f - easeOut(lt / 0.6f)) * 30f
            },
        )
    }
}

// ───────────────────────────── bars 20–25: Rin's new moves ─────────────────────────────

private val parade = listOf(
    Pose.CELEBRATE to "celebrate", Pose.SING to "sing", Pose.GUITAR_SOLO to "shred", Pose.SPIN to "spin",
    Pose.EAT to "snack", Pose.PURR to "purr", Pose.WINK to "wink", Pose.LAUGH to "laugh",
    Pose.PROUD to "proud", Pose.DANCE to "dance", Pose.SCARED to "scared", Pose.CHEER to "cheer",
)

@Composable
private fun ParadeScene(time: () -> Float) {
    val t = time()
    val beat = ((t - bars(Score.DROP2)) / (BEAT * 2)).toInt().coerceIn(0, parade.size - 1)
    val (pose, label) = parade[beat]
    val lt = (t - bars(Score.DROP2)) - beat * BEAT * 2
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Caption("40+ animations", pixel = true)
        Spacer(Modifier.height(20.dp))
        Box(contentAlignment = Alignment.Center) {
            // a dark halo keeps Rin readable over the warp tunnel
            Canvas(Modifier.size(320.dp)) { drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)), size.minDimension / 2) }
            Box(Modifier.graphicsLayer { val s = easeOutBack(lt / 0.25f); scaleX = 0.7f + 0.3f * s; scaleY = scaleX }) {
                RinSprite(pose, 190.dp, timeOffset = lt)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(label, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 30.sp, color = ACCENT)
    }
}

// ───────────────────────────── bars 26–29: finale ─────────────────────────────

@Composable
private fun OutroScene(time: () -> Float) {
    val t = time()
    val lt = t - bars(Score.OUTRO)
    val credits = listOf("made by Carrot", "starring Rin", "music: synthesized live · 0 audio files")
    Column(Modifier.fillMaxSize().padding(bottom = 90.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("RintOS 1.4", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 34.sp, color = Color.White,
            modifier = Modifier.graphicsLayer { alpha = clamp01((lt - 1.5f) / 0.6f) })
        Spacer(Modifier.height(10.dp))
        credits.forEachIndexed { i, c ->
            Text(c, fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(vertical = 3.dp).graphicsLayer { alpha = clamp01((lt - 3f - i * 0.5f) / 0.5f) })
        }
    }
}

/** Lays [content] out as a full 390×811dp screen, scaled to fill whatever box it's in. */
@Composable
private fun RealScreen(content: @Composable BoxScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val k = maxWidth / 390.dp
        Box(Modifier.requiredSize(390.dp, 811.dp).graphicsLayer { scaleX = k; scaleY = k }, content = content)
    }
}
