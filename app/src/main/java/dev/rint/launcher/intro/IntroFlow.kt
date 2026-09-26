package dev.rint.launcher.intro

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.IconStyle
import dev.rint.launcher.core.MonoBackground
import dev.rint.launcher.core.ThemeMode
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.settings.Schema
import dev.rint.launcher.settings.pretty
import dev.rint.launcher.system.SystemActions
import dev.rint.launcher.ui.BlockFont
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.color
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberHaptic
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private enum class Stage { CINEMATIC, PERSONALIZE, FINALE, LOAD_APPS, PERMISSIONS, LOAD_HOME }

@Composable
fun IntroFlow(onFinished: () -> Unit) {
    var stage by rememberSaveable { mutableStateOf(Stage.CINEMATIC) }
    val synth = remember { IntroSynth() }
    var userMuted by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(Unit) {
        runCatching { synth.start() }
        onDispose { synth.release() }
    }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_STOP -> synth.muted = true
                Lifecycle.Event.ON_START -> synth.muted = userMuted
                else -> Unit
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF03050B))) {
        AnimatedContent(stage, label = "intro", transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) }) { s ->
            when (s) {
                Stage.CINEMATIC -> IntroCinematic(synth) { synth.switch(IntroSynth.Mode.CHILL); stage = Stage.PERSONALIZE }
                Stage.PERSONALIZE -> Personalize { stage = Stage.FINALE }
                Stage.FINALE -> Finale(synth) { stage = Stage.LOAD_APPS }
                Stage.LOAD_APPS -> LoadingScreen(
                    listOf("waking up pixels", "finding your apps", "polishing icons", "teaching Rin your name"),
                ) { report ->
                    val repo = RintApp.instance.apps
                    report(0.1f)
                    var waited = 0
                    while (repo.apps.value.isEmpty() && waited < 40) { delay(50); waited++ }
                    report(0.35f)
                    val list = repo.apps.value.take(48)
                    list.forEachIndexed { i, e -> repo.icon(e.key); report(0.35f + 0.6f * (i + 1) / list.size.coerceAtLeast(1)) }
                    report(1f)
                    delay(350)
                    stage = Stage.PERMISSIONS
                }
                Stage.PERMISSIONS -> Permissions { stage = Stage.LOAD_HOME }
                Stage.LOAD_HOME -> LoadingScreen(listOf("building your home", "placing widgets", "fluffing the dock", "done!")) { report ->
                    dev.rint.launcher.home.seedLayoutIfNeeded()
                    for (i in 1..20) { report(i / 20f); delay(45) }
                    delay(300)
                    onFinished()
                }
            }
        }
        if (stage == Stage.CINEMATIC || stage == Stage.PERSONALIZE) {
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (userMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "sound",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { userMuted = !userMuted; synth.muted = userMuted }.padding(6.dp),
                )
                if (stage == Stage.CINEMATIC) Text(
                    "skip ›", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { synth.switch(IntroSynth.Mode.CHILL); stage = Stage.PERSONALIZE }.padding(10.dp),
                )
            }
        }
    }
}

// ─────────────────────────── Personalize ───────────────────────────

@Composable
internal fun Personalize(onNext: () -> Unit) {
    val look = LocalRint.current
    val cfg = look.cfg
    val store = RintApp.instance.stores.config
    val v = rememberHaptic()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("first, make it yours.", fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Color.White)
            Text("tap around. this is just the start — there are ${Schema.optionCount} more knobs inside.", fontFamily = RintFonts.Inter, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            MockPhone(cfg, width = 190.dp)
        }
        Column(
            Modifier.fillMaxWidth().weight(0.9f).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(Color(0xFF0C1122))
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ChoiceStrip("accent") {
                listOf(0xFF2F6BFFL, 0xFF00B3FFL, 0xFF36D399L, 0xFFFFD60AL, 0xFFFF9F1CL, 0xFFFF5A6AL, 0xFFFF6FB5L, 0xFF9B8CFFL).forEach { c ->
                    val sel = cfg.look.accent == c
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(c.color()).border(if (sel) 3.dp else 0.dp, Color.White, CircleShape)
                            .pressable(dev.rint.launcher.core.PressEffect.BOUNCE) {
                                Haptics.tick(v)
                                store.update { it.copy(look = it.look.copy(accent = c), icons = it.icons.copy(monoFg = if (it.icons.monoBg == MonoBackground.ACCENT) it.icons.monoFg else c, badgeColor = c)) }
                            }
                    )
                }
            }
            ChoiceStrip("icon shape") {
                listOf(IconShape.SQUIRCLE, IconShape.CIRCLE, IconShape.HEXAGON, IconShape.PEBBLE, IconShape.TEARDROP, IconShape.CLOVER, IconShape.DIAMOND).forEach { s ->
                    Pill(pretty(s.name), selected = cfg.icons.shape == s) { store.update { it.copy(icons = it.icons.copy(shape = s)) } }
                }
            }
            ChoiceStrip("icon look") {
                listOf(
                    "rint white" to { c: dev.rint.launcher.core.Icons -> c.copy(style = IconStyle.RINT, monoBg = MonoBackground.WHITE) },
                    "rint black" to { c: dev.rint.launcher.core.Icons -> c.copy(style = IconStyle.RINT, monoBg = MonoBackground.BLACK) },
                    "glass" to { c: dev.rint.launcher.core.Icons -> c.copy(style = IconStyle.RINT, monoBg = MonoBackground.GLASS) },
                    "original" to { c: dev.rint.launcher.core.Icons -> c.copy(style = IconStyle.ORIGINAL) },
                    "outline" to { c: dev.rint.launcher.core.Icons -> c.copy(style = IconStyle.OUTLINE) },
                ).forEach { (name, f) ->
                    val applied = f(cfg.icons)
                    Pill(name, selected = applied.style == cfg.icons.style && applied.monoBg == cfg.icons.monoBg) { store.update { it.copy(icons = f(it.icons)) } }
                }
            }
            ChoiceStrip("clock") {
                listOf(ClockStyle.BLOCKS, ClockStyle.THIN, ClockStyle.STACKED, ClockStyle.PIXEL, ClockStyle.WORDS, ClockStyle.ANALOG).forEach { s ->
                    Pill(pretty(s.name), selected = cfg.clock.style == s) { store.update { it.copy(clock = it.clock.copy(style = s)) } }
                }
            }
            ChoiceStrip("theme") {
                listOf(ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.LIGHT, ThemeMode.AUTO).forEach { m ->
                    Pill(pretty(m.name), selected = cfg.look.theme == m) { store.update { it.copy(look = it.look.copy(theme = m)) } }
                }
            }
            ChoiceStrip("wallpaper") {
                listOf(WallpaperMode.SYSTEM, WallpaperMode.MESH, WallpaperMode.GRADIENT, WallpaperMode.SOLID).forEach { m ->
                    Pill(pretty(m.name), selected = cfg.look.wallpaper == m) { store.update { it.copy(look = it.look.copy(wallpaper = m)) } }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp)).background(look.colors.accent)
                    .pressable(dev.rint.launcher.core.PressEffect.BOUNCE) { Haptics.confirm(v); onNext() },
                contentAlignment = Alignment.Center,
            ) {
                Text("looks good →", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = look.colors.onAccent)
            }
        }
    }
}

@Composable
private fun ChoiceStrip(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

// ─────────────────────────── Finale ───────────────────────────

private class Mote(val sx: Float, val sy: Float, val color: Color, val tx: Float, val ty: Float, val hasTarget: Boolean, val spin: Float)

/**
 * The whole preview gets swallowed by a spinning black hole on the riser, then on the drop the
 * pixels burst back out and snap into a giant blocky START button that pulses to the beat
 * with Rin head-banging on top. Pressing it tape-stops the music.
 */
@Composable
private fun Finale(synth: IntroSynth, onStart: () -> Unit) {
    val look = LocalRint.current
    val accent = look.colors.accent
    val v = rememberHaptic()
    var clock by remember { mutableFloatStateOf(0f) }
    var pressedAt by remember { mutableStateOf<Offset?>(null) }
    var pressT by remember { mutableFloatStateOf(0f) }
    var modeNow by remember { mutableStateOf(synth.mode) }
    var modeTime by remember { mutableFloatStateOf(0f) }
    var beatPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        synth.switch(IntroSynth.Mode.RISER)
        val start = System.nanoTime()
        while (true) {
            withFrameNanos { now ->
                clock = (now - start) / 1e9f
                val s = synth.seconds()
                if (s <= 0.0 && clock > 1f) {
                    // no audio output: drive the show from the wall clock instead
                    val ft = clock - 1f
                    val riserLen = (IntroSynth.BAR * 2).toFloat()
                    modeNow = if (ft < riserLen) IntroSynth.Mode.RISER else IntroSynth.Mode.DROP
                    modeTime = if (ft < riserLen) ft else ft - riserLen
                    beatPhase = ((ft / IntroSynth.BEAT) - floor(ft / IntroSynth.BEAT)).toFloat()
                } else {
                    modeNow = synth.mode
                    modeTime = synth.secondsInMode().toFloat()
                    beatPhase = ((s / IntroSynth.BEAT) - floor(s / IntroSynth.BEAT)).toFloat()
                    if (modeNow == IntroSynth.Mode.RISER && modeTime >= IntroSynth.BAR * 2 - 0.5) synth.switch(IntroSynth.Mode.DROP)
                }
                if (pressedAt != null) pressT += 1f / 60f
            }
            if (pressedAt != null && pressT > 1.1f) break
        }
        onStart()
    }
    val kick = exp(-beatPhase * 7f)
    val riserP = when (modeNow) {
        IntroSynth.Mode.RISER -> (modeTime / (IntroSynth.BAR * 2).toFloat()).coerceIn(0f, 1f)
        IntroSynth.Mode.DROP, IntroSynth.Mode.STOP -> 1f
        else -> 0f
    }
    val dropT = if (modeNow == IntroSynth.Mode.DROP || modeNow == IntroSynth.Mode.STOP) modeTime else -1f

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(dropT >= 0.6f) {
            if (dropT < 0.6f) return@pointerInput
            detectTapGestures { p ->
                if (pressedAt == null) { pressedAt = p; Haptics.heavy(v); synth.tapeStop() }
            }
        }
    ) {
        val (cells, cw) = remember { BlockFont.cells("START") }
        val motes = remember {
            val rnd = Random(21)
            val palette = listOf(Color.White, Color(0xFF0A0E1E), accent, accent.copy(alpha = 0.7f))
            val list = ArrayList<Mote>()
            cells.forEach { (x, y) -> list += Mote(0.3f + rnd.nextFloat() * 0.4f, 0.22f + rnd.nextFloat() * 0.56f, if (rnd.nextFloat() < 0.8f) accent else Color.White, x.toFloat(), y.toFloat(), true, rnd.nextFloat() * 4 - 2) }
            repeat(90) { list += Mote(0.3f + rnd.nextFloat() * 0.4f, 0.22f + rnd.nextFloat() * 0.56f, palette[rnd.nextInt(palette.size)], 0f, 0f, false, rnd.nextFloat() * 4 - 2) }
            list
        }
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            // accretion glow
            if (riserP > 0f && dropT < 0f) {
                val r = size.minDimension * (0.15f + 0.5f * riserP)
                drawCircle(Brush.radialGradient(listOf(Color.Black, accent.copy(alpha = 0.5f * riserP), Color.Transparent), c, r), r, c)
            }
            if (dropT in 0f..0.5f) drawRect(Color.White.copy(alpha = (1f - dropT / 0.5f)))
            val cell = size.width * 0.84f / cw
            val ox = (size.width - cw * cell) / 2
            val oy = size.height * 0.45f - cell * 2.5f
            val bump = 1f + 0.08f * kick
            motes.forEachIndexed { i, m ->
                val sx = m.sx * size.width
                val sy = m.sy * size.height
                var x: Float
                var y: Float
                var s = cell * 0.5f
                var alpha = 1f
                if (dropT < 0f) {
                    // spiral in
                    val p = riserP * riserP
                    val dx = sx - c.x
                    val dy = sy - c.y
                    val r0 = hypot(dx, dy)
                    val a0 = kotlin.math.atan2(dy, dx)
                    val r = r0 * (1f - p)
                    val a = a0 + p * 10f * PI.toFloat() * (0.6f + 0.4f * (i % 5) / 5f)
                    x = c.x + cos(a) * r
                    y = c.y + sin(a) * r
                    s = cell * 0.5f * (1f - 0.7f * p)
                } else {
                    val k = easeOutBackF(dropT / 0.9f)
                    if (m.hasTarget) {
                        val tx = ox + m.tx * cell
                        val ty = oy + m.ty * cell
                        x = c.x + (tx - c.x) * k
                        y = c.y + (ty - c.y) * k
                        s = cell * 0.9f
                        x = c.x + (x - c.x) * bump
                        y = c.y + (y - c.y) * bump
                    } else {
                        val ang = (i * 137.5f) * PI.toFloat() / 180f
                        val dist = size.maxDimension * (0.2f + 0.5f * ((i * 31) % 17) / 17f) * (dropT * 1.6f).coerceAtMost(1.6f)
                        x = c.x + cos(ang) * dist
                        y = c.y + sin(ang) * dist
                        alpha = (1f - dropT / 1.4f).coerceIn(0f, 1f)
                    }
                    pressedAt?.let { p ->
                        val dx = x - p.x
                        val dy = y - p.y
                        val d = hypot(dx, dy).coerceAtLeast(1f)
                        val push = pressT * pressT * size.maxDimension * 1.2f
                        x += dx / d * push
                        y += dy / d * push
                        alpha *= (1f - pressT).coerceIn(0f, 1f)
                    }
                }
                rotate(m.spin * 90f * (if (dropT < 0f) riserP else 0f), Offset(x + s / 2, y + s / 2)) {
                    drawRoundRect(m.color.copy(alpha = m.color.alpha * alpha), Offset(x, y), Size(s, s), CornerRadius(s * 0.15f))
                }
            }
            pressedAt?.let { p ->
                val r = pressT * size.maxDimension * 1.3f
                drawCircle(Color.White.copy(alpha = (1f - pressT).coerceIn(0f, 1f)), r, p, style = Stroke(18f * (1f - pressT).coerceIn(0.1f, 1f)))
            }
        }
        // before the riser kicks in: the preview phone shrinks & tilts
        if (riserP == 0f && dropT < 0f) {
            Box(Modifier.align(Alignment.Center).graphicsLayer {
                val s = (1f - clock * 0.25f).coerceIn(0.5f, 1f)
                scaleX = s; scaleY = s; rotationZ = clock * 12f
            }) { MockPhone(look.cfg, width = 190.dp) }
        }
        // Rin gets sucked in… then rides the button
        if (dropT < 0f && riserP > 0.35f) {
            val p = ((riserP - 0.35f) / 0.65f).coerceIn(0f, 1f)
            RinSprite(Pose.JUMP, 110.dp, Modifier.align(Alignment.Center).graphicsLayer {
                rotationZ = p * 900f; scaleX = 1f - p; scaleY = 1f - p
                translationX = (1f - p) * -240f; translationY = (1f - p) * 300f
            })
        }
        if (dropT >= 0.4f && pressedAt == null) {
            val appear = ((dropT - 0.4f) / 0.5f).coerceIn(0f, 1f)
            val cellDp = maxWidth * 0.84f / cw
            Column(
                Modifier.align(Alignment.Center).padding(bottom = cellDp * 5 + 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RinSprite(Pose.HAPPY, 84.dp, Modifier.graphicsLayer {
                    alpha = appear
                    val bob = sin(beatPhase * 2 * PI.toFloat())
                    rotationZ = bob * 12f
                    translationY = -kick * 16f + (1f - appear) * -200f
                })
            }
            Text(
                "tap START", fontFamily = RintFonts.Pixel, fontSize = 13.sp, color = Color.White.copy(alpha = appear * (0.5f + 0.5f * kick)),
                modifier = Modifier.align(Alignment.Center).padding(top = cellDp * 5 + 70.dp),
            )
        }
        if (pressedAt != null) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (pressT - 0.4f).coerceIn(0f, 1f))))
    }
}

private fun easeOutBackF(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = x.coerceIn(0f, 1f) - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

// ─────────────────────────── Loading ───────────────────────────

@Composable
fun LoadingScreen(steps: List<String>, work: suspend (report: (Float) -> Unit) -> Unit) {
    val look = LocalRint.current
    val progress = remember { Animatable(0f) }
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { work { target = it } }
    LaunchedEffect(target) { progress.animateTo(target, tween(260, easing = LinearEasing)) }
    val stepIdx = (progress.value * steps.size).toInt().coerceIn(0, steps.lastIndex)
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(120.dp)) {
            val blocks = 20
            val filled = (progress.value * blocks).toInt()
            Canvas(Modifier.fillMaxWidth().height(22.dp).align(Alignment.BottomStart)) {
                val bw = size.width / blocks
                for (i in 0 until blocks) {
                    drawRoundRect(
                        if (i < filled) look.colors.accent else Color.White.copy(alpha = 0.08f),
                        Offset(i * bw + 2, 0f), Size(bw - 4, size.height), CornerRadius(4f),
                    )
                }
            }
            val step = (System.nanoTime() / 150_000_000L) % 2
            RinSprite(
                Pose.WALK, 64.dp,
                Modifier.align(Alignment.BottomStart).padding(bottom = 22.dp)
                    .graphicsLayer { translationX = (maxWidth.toPx() - 64.dp.toPx()) * progress.value; translationY = if (step == 0L) 0f else -4f },
            )
        }
        Spacer(Modifier.height(18.dp))
        AnimatedContent(steps[stepIdx], label = "s", transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }) {
            Text("> $it", fontFamily = RintFonts.Terminal, fontSize = 24.sp, color = Color.White)
        }
        Text("${(progress.value * 100).toInt()}%", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = look.colors.accent)
    }
}

// ─────────────────────────── Permissions ───────────────────────────

private class Perm(val title: String, val why: String, val icon: ImageVector, val essential: Boolean, val granted: () -> Boolean, val request: () -> Unit)

@Composable
internal fun Permissions(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val look = LocalRint.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val rm = ctx.getSystemService(RoleManager::class.java)
    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    val perms = listOf(
        Perm("Be your home screen", "so pressing home opens RintOS", Icons.Rounded.Home, true,
            { rm?.isRoleHeld(RoleManager.ROLE_HOME) == true },
            { rm?.createRequestRoleIntent(RoleManager.ROLE_HOME)?.let { roleLauncher.launch(it) } }),
        Perm("Notification access", "notification dots, and live lyrics for Spotify, YT Music & friends", Icons.Rounded.NotificationsActive, false,
            { SystemActions.notificationAccessGranted(ctx) }, { SystemActions.openNotificationAccess(ctx) }),
        Perm("Music on this phone", "play your own song files with lyrics", Icons.Rounded.LibraryMusic, false,
            { has(audioPerm) }, { permLauncher.launch(audioPerm) }),
        Perm("Contacts", "find people right from search", Icons.Rounded.Contacts, false,
            { has(Manifest.permission.READ_CONTACTS) }, { permLauncher.launch(Manifest.permission.READ_CONTACTS) }),
        Perm("Gesture helper", "double-tap to lock & recents gestures (accessibility). optional.", Icons.Rounded.AccessibilityNew, false,
            { SystemActions.accessibilityGranted(ctx) }, { SystemActions.openAccessibilitySettings(ctx) }),
    )
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("a few permissions", fontFamily = RintFonts.Inter, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Color.White)
                Text("each one unlocks something. skip any — you can grant them later in settings.", fontFamily = RintFonts.Inter, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
            RinSprite(Pose.MEH, 64.dp)
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            perms.forEach { p ->
                val ok = remember(tick) { p.granted() }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(if (ok) look.colors.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (ok) look.colors.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
                        .clickable(enabled = !ok) { p.request() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (ok) look.colors.accent else Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(if (ok) Icons.Rounded.Check else p.icon, null, tint = if (ok) look.colors.onAccent else Color.White)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            if (p.essential) Text("  recommended", fontFamily = RintFonts.Pixel, fontSize = 8.sp, color = look.colors.accent)
                        }
                        Text(p.why, fontFamily = RintFonts.Inter, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                    Text(if (ok) "on" else "allow", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = if (ok) look.colors.accent else Color.White)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp)).background(look.colors.accent)
                .clickable(remember { MutableInteractionSource() }, null) { onDone() },
            contentAlignment = Alignment.Center,
        ) {
            Text("continue →", fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = look.colors.onAccent, textAlign = TextAlign.Center)
        }
    }
}
