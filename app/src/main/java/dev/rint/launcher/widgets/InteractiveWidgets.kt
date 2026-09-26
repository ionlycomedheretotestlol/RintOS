package dev.rint.launcher.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.search.Calc
import dev.rint.launcher.system.SystemActions
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val look = LocalRint.current
    val v = rememberHaptic()
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) look.colors.accent else look.colors.text.copy(alpha = 0.09f))
            .pressable(PressEffect.BOUNCE) { Haptics.tap(v); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (filled) look.colors.onAccent else look.colors.text, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun WidgetTitle(text: String, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    Text(text.uppercase(), modifier = modifier, fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext, letterSpacing = 1.sp)
}

// ─────────────────────────── Rin pet ───────────────────────────

@Composable
fun PetWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val scope = rememberCoroutineScope()
    val love = ctx.state("love", "0").toIntOrNull() ?: 0
    val fed = ctx.state("fed", System.currentTimeMillis().toString()).toLongOrNull() ?: 0L
    val hoursHungry = (System.currentTimeMillis() - fed) / 3_600_000f
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    var reaction by remember { mutableStateOf<Pose?>(null) }
    val hearts = remember { mutableStateListOf<Pair<Long, Float>>() }
    val bounce = remember { Animatable(0f) }
    val base = when {
        hour >= 23 || hour < 6 -> Pose.SLEEP
        hoursHungry > 10 -> Pose.MEH
        else -> Pose.SIT
    }
    val mood = when (base) {
        Pose.SLEEP -> "sleeping"
        Pose.MEH -> "hungry..."
        else -> if (love > 50) "adores you" else if (love > 10) "happy" else "curious"
    }
    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(remember { MutableInteractionSource() }, null) {
                    Haptics.tap(v)
                    ctx.set("love", (love + 1).toString())
                    hearts += System.nanoTime() to Random.nextFloat()
                    scope.launch {
                        reaction = if (love % 3 == 2) Pose.CHEER else Pose.JUMP
                        bounce.snapTo(1f)
                        bounce.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
                        delay(900)
                        reaction = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            RinSprite(
                reaction ?: base,
                size = if (ctx.h >= 2) 92.dp else 56.dp,
                modifier = Modifier.graphicsLayer {
                    translationY = -bounce.value * 26f
                    rotationZ = bounce.value * 6f
                },
            )
            hearts.toList().forEach { (id, r) ->
                key(id) {
                    val a = remember { Animatable(0f) }
                    LaunchedEffect(Unit) {
                        a.animateTo(1f, tween(900))
                        hearts.removeAll { it.first == id }
                    }
                    Text(
                        "♥", color = look.colors.accent, fontSize = 18.sp,
                        modifier = Modifier.graphicsLayer {
                            translationY = -a.value * 120f
                            translationX = (r - 0.5f) * 90f
                            alpha = 1f - a.value
                            scaleX = 0.6f + a.value
                            scaleY = 0.6f + a.value
                        },
                    )
                }
            }
        }
        if (ctx.w >= 4 || ctx.h >= 2) {
            Column(Modifier.padding(start = 6.dp), horizontalAlignment = Alignment.End) {
                WidgetTitle(look.cfg.mascot.name)
                Text(mood, fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = look.colors.text)
                Spacer(Modifier.height(4.dp))
                Text("♥ $love", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = look.colors.accent)
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(look.colors.accent)
                        .pressable(PressEffect.BOUNCE) {
                            Haptics.confirm(v)
                            ctx.set("fed", System.currentTimeMillis().toString())
                            scope.launch {
                                reaction = Pose.CHEER
                                bounce.snapTo(1f)
                                bounce.animateTo(0f, spring(dampingRatio = 0.35f))
                                delay(700)
                                reaction = null
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("feed", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = look.colors.onAccent)
                }
            }
        }
    }
}

// ─────────────────────────── Notes ───────────────────────────

@Composable
fun NotesWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val saved = ctx.state("text", "")
    var text by remember { mutableStateOf(saved) }
    LaunchedEffect(text) {
        delay(400)
        if (text != saved) ctx.set("text", text)
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(look.colors.accent, CircleShape))
            Spacer(Modifier.width(6.dp))
            WidgetTitle("note")
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxSize()) {
            if (text.isEmpty()) Text("write something…", color = look.colors.subtext, fontSize = 15.sp, fontFamily = look.font)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = look.colors.text, fontSize = 15.sp, fontFamily = look.font, lineHeight = 20.sp),
                cursorBrush = SolidColor(look.colors.accent),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────── Checklist ───────────────────────────

private fun decodeTodos(s: String): List<Pair<Boolean, String>> =
    s.split('\n').filter { it.length >= 2 }.map { (it[0] == '1') to it.substring(2) }

private fun encodeTodos(l: List<Pair<Boolean, String>>) = l.joinToString("\n") { (if (it.first) "1" else "0") + "|" + it.second.replace('\n', ' ') }

@Composable
fun ChecklistWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val todos = decodeTodos(ctx.state("items", "0|drink water\n0|make RintOS mine\n1|install RintOS"))
    var draft by remember { mutableStateOf("") }
    val done = todos.count { it.first }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetTitle("to do", Modifier.weight(1f))
            Text("$done/${todos.size}", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.accent)
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(todos, key = { i, _ -> i }) { i, (checked, label) ->
                val p by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.5f), label = "c")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            Haptics.tick(v)
                            ctx.set("items", encodeTodos(todos.mapIndexed { j, t -> if (j == i) (!t.first) to t.second else t }))
                        }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(look.colors.accent.copy(alpha = 0.15f + 0.85f * p)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = look.colors.onAccent, modifier = Modifier.size(14.dp).graphicsLayer { scaleX = p; scaleY = p })
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        label, modifier = Modifier.weight(1f), fontFamily = look.font, fontSize = 14.sp,
                        color = look.colors.text.copy(alpha = 1f - 0.5f * p), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (checked) TextDecoration.LineThrough else null,
                    )
                    Icon(
                        Icons.Rounded.Close, null, tint = look.colors.subtext,
                        modifier = Modifier.size(16.dp).clickable {
                            ctx.set("items", encodeTodos(todos.filterIndexed { j, _ -> j != i }))
                        },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(look.colors.text.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) Text("add…", color = look.colors.subtext, fontSize = 13.sp, fontFamily = look.font)
                BasicTextField(
                    draft, { draft = it },
                    singleLine = true,
                    textStyle = TextStyle(color = look.colors.text, fontSize = 13.sp, fontFamily = look.font),
                    cursorBrush = SolidColor(look.colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (draft.isNotBlank()) { ctx.set("items", encodeTodos(todos + (false to draft.trim()))); draft = "" }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Icon(Icons.Rounded.Add, null, tint = look.colors.accent, modifier = Modifier.size(18.dp).clickable {
                if (draft.isNotBlank()) { ctx.set("items", encodeTodos(todos + (false to draft.trim()))); draft = "" }
            })
        }
    }
}

// ─────────────────────────── Focus timer ───────────────────────────

@Composable
fun TimerWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val minutes = ctx.state("min", "25").toIntOrNull() ?: 25
    val endAt = ctx.state("end", "0").toLongOrNull() ?: 0L
    val pausedLeft = ctx.state("left", "0").toLongOrNull() ?: 0L
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) { while (endAt > 0) { now = System.currentTimeMillis(); delay(250) } }
    val running = endAt > now
    val total = minutes * 60_000L
    val left = when {
        running -> endAt - now
        pausedLeft > 0 -> pausedLeft
        endAt in 1..now -> 0L
        else -> total
    }
    LaunchedEffect(running, endAt) {
        if (endAt in 1..System.currentTimeMillis() && !running) Haptics.heavy(v)
    }
    val progress = if (total > 0) 1f - left.toFloat() / total else 0f
    var dragMin by remember { mutableFloatStateOf(-1f) }
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .weight(1f)
                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                .pointerInput(running) {
                    if (running) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            if (dragMin > 0) ctx.set("min", dragMin.roundToInt().coerceIn(1, 120).toString())
                            ctx.set("left", "0"); ctx.set("end", "0")
                            dragMin = -1f
                        },
                    ) { change, _ ->
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val p = change.position - c
                        var ang = Math.toDegrees(atan2(p.x.toDouble(), -p.y.toDouble())).toFloat()
                        if (ang < 0) ang += 360f
                        val m = (ang / 360f * 60f).coerceAtLeast(1f)
                        if (dragMin < 0 || (m.roundToInt() != dragMin.roundToInt())) Haptics.tick(v)
                        dragMin = m
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val shown = if (dragMin > 0) dragMin / 60f else if (running || pausedLeft > 0) progress else minutes.coerceAtMost(60) / 60f
            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                val stroke = size.minDimension * 0.1f
                drawArc(look.colors.text.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(stroke))
                drawArc(look.colors.accent, -90f, 360f * shown, false, style = Stroke(stroke, cap = StrokeCap.Round))
                val a = Math.toRadians((360.0 * shown) - 90.0)
                val r = size.minDimension / 2
                drawCircle(Color.White, stroke * 0.7f, Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()))
            }
            val secs = (if (dragMin > 0) dragMin.roundToInt() * 60_000L else left) / 1000
            Text("%02d:%02d".format(secs / 60, secs % 60), fontFamily = RintFonts.Terminal, fontSize = 26.sp, color = look.colors.text)
        }
        Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundButton(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, filled = true) {
                if (running) {
                    ctx.set("left", (endAt - System.currentTimeMillis()).toString()); ctx.set("end", "0")
                } else {
                    val dur = if (pausedLeft > 0) pausedLeft else total
                    ctx.set("end", (System.currentTimeMillis() + dur).toString()); ctx.set("left", "0")
                }
            }
            RoundButton(Icons.Rounded.Refresh) { ctx.set("end", "0"); ctx.set("left", "0") }
        }
    }
}

// ─────────────────────────── Calculator ───────────────────────────

@Composable
fun CalculatorWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val v = rememberHaptic()
    var expr by remember { mutableStateOf(ctx.get("expr") ?: "") }
    var shownResult by remember { mutableStateOf(false) }
    val live = if (expr.isNotBlank()) Calc.eval(expr)?.let { Calc.format(it) } else null
    val keys = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "-"),
        listOf("1", "2", "3", "+"),
        listOf("0", ".", "⌫", "="),
    )
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Column(Modifier.fillMaxWidth().weight(0.9f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Bottom) {
            Text(expr.ifEmpty { "0" }, fontFamily = RintFonts.Terminal, fontSize = if (shownResult) 34.sp else 22.sp,
                color = if (shownResult) look.colors.text else look.colors.subtext, maxLines = 1)
            AnimatedContent(live.takeIf { !shownResult } ?: "", label = "r", transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
            }) { r -> Text(r, fontFamily = RintFonts.Terminal, fontSize = 30.sp, color = look.colors.accent, maxLines = 1) }
        }
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    val op = k in listOf("÷", "×", "-", "+", "=")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (k == "=") look.colors.accent else if (op) look.colors.accent.copy(alpha = 0.18f) else look.colors.text.copy(alpha = 0.07f))
                            .pressable(PressEffect.SHRINK) {
                                Haptics.tick(v)
                                when (k) {
                                    "C" -> { expr = ""; shownResult = false }
                                    "⌫" -> { expr = expr.dropLast(1); shownResult = false }
                                    "=" -> { live?.let { expr = it.replace(",", ""); shownResult = true } }
                                    else -> {
                                        if (shownResult && k.first().isDigit()) expr = ""
                                        expr += k; shownResult = false
                                    }
                                }
                                ctx.set("expr", expr)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(k, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                            color = if (k == "=") look.colors.onAccent else if (op) look.colors.accent else look.colors.text)
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Tally ───────────────────────────

@Composable
fun CounterWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val n = ctx.state("n", "0").toIntOrNull() ?: 0
    val label = ctx.state("label", "tally")
    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Rounded.Remove, size = 32.dp) { ctx.set("n", (n - 1).coerceAtLeast(0).toString()) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(n, label = "n", transitionSpec = {
                if (targetState > initialState) (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                else (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut())
            }) { Text("$it", fontFamily = RintFonts.Terminal, fontSize = 36.sp, color = look.colors.text) }
            if (ctx.h >= 2) WidgetTitle(label)
        }
        RoundButton(Icons.Rounded.Add, size = 32.dp, filled = true) { ctx.set("n", (n + 1).toString()) }
    }
}

// ─────────────────────────── Dice & coin ───────────────────────────

@Composable
fun DiceWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val scope = rememberCoroutineScope()
    var face by remember { mutableIntStateOf(ctx.get("d")?.toIntOrNull() ?: 5) }
    var coin by remember { mutableStateOf(ctx.get("c") ?: "heads") }
    val spin = remember { Animatable(0f) }
    val flip = remember { Animatable(0f) }
    Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .fillMaxHeight(0.8f)
                .aspectRatio(1f)
                .graphicsLayer { rotationZ = spin.value * 720f; scaleX = 1f - 0.2f * sin(spin.value * Math.PI).toFloat(); scaleY = scaleX }
                .clickable(remember { MutableInteractionSource() }, null) {
                    scope.launch {
                        repeat(6) { face = Random.nextInt(1, 7); Haptics.tick(v); delay(60) }
                        spin.snapTo(0f); spin.animateTo(1f, tween(500))
                        face = Random.nextInt(1, 7); ctx.set("d", "$face"); Haptics.confirm(v)
                    }
                }
        ) {
            val s = size.minDimension
            drawRoundRect(Color.White, size = Size(s, s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.22f))
            val pip = s * 0.09f
            val pos = mapOf(
                1 to listOf(0.5f to 0.5f),
                2 to listOf(0.28f to 0.28f, 0.72f to 0.72f),
                3 to listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.75f),
                4 to listOf(0.28f to 0.28f, 0.72f to 0.28f, 0.28f to 0.72f, 0.72f to 0.72f),
                5 to listOf(0.26f to 0.26f, 0.74f to 0.26f, 0.5f to 0.5f, 0.26f to 0.74f, 0.74f to 0.74f),
                6 to listOf(0.28f to 0.24f, 0.72f to 0.24f, 0.28f to 0.5f, 0.72f to 0.5f, 0.28f to 0.76f, 0.72f to 0.76f),
            )
            pos[face]!!.forEach { (x, y) -> drawCircle(if (face == 1) look.colors.accent else Color(0xFF0A0E1E), pip, Offset(x * s, y * s)) }
        }
        if (ctx.w >= 2) {
            Box(
                Modifier
                    .fillMaxHeight(0.8f)
                    .aspectRatio(1f)
                    .graphicsLayer { rotationY = flip.value * 1080f; cameraDistance = 12f * density }
                    .clip(CircleShape)
                    .background(look.colors.accent)
                    .clickable {
                        scope.launch {
                            flip.snapTo(0f); flip.animateTo(1f, tween(700))
                            coin = if (Random.nextBoolean()) "heads" else "tails"; ctx.set("c", coin); Haptics.confirm(v)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) { Text(coin, fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = look.colors.onAccent) }
        }
    }
}

// ─────────────────────────── Torch ───────────────────────────

@Composable
fun TorchWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val context = LocalContext.current
    val v = rememberHaptic()
    var on by remember { mutableStateOf(false) }
    val glow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f), label = "g")
    Box(
        Modifier
            .fillMaxSize()
            .background(look.colors.accent.copy(alpha = 0.85f * glow))
            .clickable { Haptics.confirm(v); on = SystemActions.toggleFlashlight(context) },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, null,
                tint = if (on) look.colors.onAccent else look.colors.text, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = -12f * glow })
            if (ctx.w >= 2) {
                Spacer(Modifier.width(8.dp))
                Text(if (on) "on" else "off", fontFamily = RintFonts.Pixel, fontSize = 12.sp, color = if (on) look.colors.onAccent else look.colors.text)
            }
        }
    }
}

// ─────────────────────────── Battery ───────────────────────────

data class BatteryState(val level: Int, val charging: Boolean)

@Composable
fun rememberBattery(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(100, false)) }
    DisposableEffect(Unit) {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                i ?: return
                val lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                state = BatteryState(
                    if (lvl >= 0) lvl * 100 / scale else 100,
                    st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, rx, IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(rx) } }
    }
    return state
}

@Composable
fun BatteryWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val b = rememberBattery()
    val p by animateFloatAsState(b.level / 100f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow), label = "b")
    val col = when {
        b.charging -> Color(0xFF3DDC84)
        b.level <= 15 -> look.colors.danger
        else -> look.colors.accent
    }
    Row(Modifier.fillMaxSize().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(2.dp)) {
                val st = size.minDimension * 0.12f
                drawArc(look.colors.text.copy(alpha = 0.1f), 0f, 360f, false, style = Stroke(st))
                drawArc(col, -90f, 360f * p, false, style = Stroke(st, cap = StrokeCap.Round))
            }
            if (ctx.w == 1) Text("${b.level}", fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = look.colors.text)
            else Text(if (b.charging) "⚡" else "", fontSize = 16.sp, color = col)
        }
        if (ctx.w >= 2) {
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${b.level}%", fontFamily = RintFonts.Terminal, fontSize = 30.sp, color = look.colors.text)
                WidgetTitle(if (b.charging) "charging" else "battery")
            }
        }
    }
}

// ─────────────────────────── Calendar ───────────────────────────

@Composable
fun CalendarWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val now = rememberNow(60_000)
    val cal = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val first = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = now.get(Calendar.DAY_OF_MONTH)
    val monthName = java.text.SimpleDateFormat("MMMM", java.util.Locale.getDefault()).format(now.time)
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(monthName, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = look.colors.text)
            Spacer(Modifier.width(6.dp))
            Text("${now.get(Calendar.YEAR)}", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = look.colors.accent)
        }
        if (ctx.h < 3 && ctx.w < 4) {
            Spacer(Modifier.weight(1f))
            Text("$today", fontFamily = RintFonts.Terminal, fontSize = 56.sp, color = look.colors.text)
            Text(java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(now.time), fontSize = 12.sp, color = look.colors.subtext, fontFamily = look.font)
            return@Column
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, Modifier.weight(1f), fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        val weeks = (first + days + 6) / 7
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                for (d in 0 until 7) {
                    val day = w * 7 + d - first + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..days) {
                            if (day == today) Box(Modifier.size(22.dp).background(look.colors.accent, CircleShape))
                            Text("$day", fontSize = 11.sp, fontFamily = look.font, fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                                color = if (day == today) look.colors.onAccent else look.colors.text.copy(alpha = if (day < today) 0.45f else 0.9f))
                        }
                    }
                }
            }
        }
    }
}


// ─────────────────────────── Ask Rin ───────────────────────────

@Composable
fun AskRinWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val speaking by dev.rint.launcher.RintApp.instance.assistant.voiceOut.speaking.collectAsState()
    val level by dev.rint.launcher.RintApp.instance.assistant.voiceOut.level.collectAsState()
    val open = { dev.rint.launcher.assistant.AssistantOverlay.show() }
    val mode = if (speaking) dev.rint.launcher.assistant.HeadMode.TALKING else dev.rint.launcher.assistant.HeadMode.IDLE
    if (ctx.h >= 2) {
        Column(Modifier.fillMaxSize().clickable { open() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            dev.rint.launcher.assistant.RinTalkingHead(mode, level, 64.dp)
            Text("ask ${look.cfg.mascot.name}", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = look.colors.text)
        }
    } else {
        Row(Modifier.fillMaxSize().clickable { open() }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RinSprite(if (speaking) Pose.HAPPY else Pose.HEAD, 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(if (ctx.w >= 4) "ask ${look.cfg.mascot.name} anything…" else "ask ${look.cfg.mascot.name}", fontFamily = look.font, fontSize = 15.sp, color = look.colors.subtext, modifier = Modifier.weight(1f), maxLines = 1)
            if (ctx.w >= 3) Box(Modifier.size(36.dp).clip(CircleShape).background(look.colors.accent), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Mic, null, tint = look.colors.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}
