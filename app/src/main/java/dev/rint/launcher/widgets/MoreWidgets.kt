package dev.rint.launcher.widgets

import android.app.ActivityManager
import android.app.DatePickerDialog
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.I18n
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RawText
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    Text(text.uppercase(), modifier = modifier, fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext, letterSpacing = 1.sp)
}

// ─────────────────────────── Countdown ───────────────────────────

/** Days until something: tap the number to pick the date, type the name. */
@Composable
fun CountdownWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val context = LocalContext.current
    val title = ctx.state("title", "")
    val target = ctx.state("date", "").toLongOrNull()
    val now = rememberNow(60_000)
    val days = target?.let {
        val t = Calendar.getInstance().apply { timeInMillis = it; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val today = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        ((t.timeInMillis - today.timeInMillis) / 86_400_000L).toInt()
    }
    fun pick() {
        val c = Calendar.getInstance().apply { target?.let { timeInMillis = it } }
        runCatching {
            DatePickerDialog(context, { _, y, m, d ->
                ctx.set("date", Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis.toString())
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Label("countdown")
        Column(Modifier.clickable(remember { MutableInteractionSource() }, null) { if (!ctx.preview) pick() }) {
            Text(
                when {
                    days == null -> "tap to set a date"
                    days == 0 -> "today!"
                    days < 0 -> "${-days} days ago"
                    else -> "$days"
                },
                fontFamily = if (days != null && days > 0) RintFonts.Terminal else look.font,
                fontSize = if (days != null && days > 0) 52.sp else 18.sp, color = look.colors.accent, maxLines = 1,
            )
            if (days != null && days > 0) Text(if (days == 1) "day left" else "days left", fontFamily = look.font, fontSize = 12.sp, color = look.colors.subtext)
        }
        Box {
            if (title.isEmpty()) Text("what's coming up?", color = look.colors.subtext, fontFamily = look.font, fontSize = 14.sp)
            if (!ctx.preview) BasicTextField(
                title, { ctx.set("title", it.take(40)) }, singleLine = true,
                textStyle = TextStyle(color = look.colors.text, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                cursorBrush = SolidColor(look.colors.accent), modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── World clock ───────────────────────────

private val cities = listOf(
    "Tokyo" to "Asia/Tokyo", "London" to "Europe/London", "New York" to "America/New_York", "São Paulo" to "America/Sao_Paulo",
    "Los Angeles" to "America/Los_Angeles", "Paris" to "Europe/Paris", "Dubai" to "Asia/Dubai", "Sydney" to "Australia/Sydney",
    "Seoul" to "Asia/Seoul", "Lisbon" to "Europe/Lisbon", "Mexico City" to "America/Mexico_City", "Mumbai" to "Asia/Kolkata",
)

/** A second time zone. Tap to cycle cities. */
@Composable
fun WorldClockWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val idx = ctx.state("city", "0").toIntOrNull()?.mod(cities.size) ?: 0
    val (name, zone) = cities[idx]
    val now = rememberNow(15_000)
    val tz = TimeZone.getTimeZone(zone)
    val fmt = remember(zone, look.cfg.clock.use24h) {
        SimpleDateFormat(if (look.cfg.clock.use24h) "HH:mm" else "h:mm a", I18n.locale).apply { timeZone = tz }
    }
    val diffH = (tz.getOffset(now.timeInMillis) - TimeZone.getDefault().getOffset(now.timeInMillis)) / 3_600_000f
    val there = Calendar.getInstance(tz).apply { timeInMillis = now.timeInMillis }.get(Calendar.HOUR_OF_DAY)
    val night = there >= 19 || there < 6
    Column(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { if (!ctx.preview) ctx.set("city", "${idx + 1}") }.padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(if (night) "night" else "day")
            Spacer(Modifier.weight(1f))
            Text(
                (if (diffH >= 0) "+" else "") + (if (diffH % 1f == 0f) "${diffH.toInt()}" else "%.1f".format(diffH)) + "h",
                fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.accent,
            )
        }
        Text(fmt.format(now.time), fontFamily = RintFonts.Terminal, fontSize = if (ctx.w >= 4) 56.sp else 42.sp, color = look.colors.text, maxLines = 1)
        RawText(name, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = look.colors.subtext)
    }
}

// ─────────────────────────── Rin's thought ───────────────────────────

private val thoughtsEn = listOf(
    "drink some water. I'll wait." to Pose.NOD, "you're doing better than you think." to Pose.HAPPY,
    "today feels like a guitar solo kind of day." to Pose.GUITAR, "I reorganized nothing. you're welcome." to Pose.PROUD,
    "take a break. even launchers sleep." to Pose.YAWN, "if it's hard, it counts." to Pose.CHEER,
    "I counted your apps. there are a lot." to Pose.CONFUSED, "small steps are still steps." to Pose.WALK,
    "go outside for five minutes. I'll hold the fort." to Pose.WAVE, "you look nice today. probably." to Pose.WINK,
    "snack break? snack break." to Pose.EAT, "one thing at a time." to Pose.THINK,
    "your phone, your rules. except bedtime." to Pose.SLEEP, "sing something. nobody's listening. (I am.)" to Pose.SING,
)
private val thoughtsPt = listOf(
    "bebe uma água. eu espero." to Pose.NOD, "você tá indo melhor do que pensa." to Pose.HAPPY,
    "hoje tá com cara de solo de guitarra." to Pose.GUITAR, "não organizei nada. de nada." to Pose.PROUD,
    "faz uma pausa. até launcher dorme." to Pose.YAWN, "se é difícil, conta." to Pose.CHEER,
    "contei seus apps. são muitos." to Pose.CONFUSED, "passinho também é passo." to Pose.WALK,
    "sai lá fora cinco minutinhos. eu seguro as pontas." to Pose.WAVE, "você tá bonito(a) hoje. provavelmente." to Pose.WINK,
    "hora do lanche? hora do lanche." to Pose.EAT, "uma coisa de cada vez." to Pose.THINK,
    "seu celular, suas regras. menos a hora de dormir." to Pose.SLEEP, "canta alguma coisa. ninguém tá ouvindo. (eu tô.)" to Pose.SING,
)

/** One new thought from Rin every day, with a matching animation. Tap for another. */
@Composable
fun RinThoughtWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val now = rememberNow(60_000)
    val extra = ctx.state("extra", "0").toIntOrNull() ?: 0
    val list = if (I18n.pt) thoughtsPt else thoughtsEn
    val day = now.get(Calendar.YEAR) * 400 + now.get(Calendar.DAY_OF_YEAR)
    val (text, pose) = list[(day * 7 + extra).mod(list.size)]
    Row(
        Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { if (!ctx.preview) ctx.set("extra", "${extra + 1}") }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RinSprite(pose, if (ctx.h >= 2) 84.dp else 44.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Label("rin says")
            Spacer(Modifier.height(4.dp))
            RawText(text, color = look.colors.text, fontFamily = look.font, fontSize = if (ctx.h >= 2) 16.sp else 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─────────────────────────── Device ───────────────────────────

private data class DeviceStats(val storageUsed: Float, val storageFreeGb: Float, val ramUsed: Float, val uptimeH: Long)

private fun readStats(c: Context): DeviceStats {
    val st = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val total = runCatching { st?.totalBytes }.getOrNull() ?: 1L
    val free = runCatching { st?.availableBytes }.getOrNull() ?: 0L
    val mi = ActivityManager.MemoryInfo()
    runCatching { (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi) }
    val ramUsed = if (mi.totalMem > 0) 1f - mi.availMem / mi.totalMem.toFloat() else 0f
    return DeviceStats(1f - free / max(total, 1L).toFloat(), free / 1e9f, ramUsed, SystemClock.elapsedRealtime() / 3_600_000L)
}

/** Storage, memory and uptime at a glance. */
@Composable
fun DeviceWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val context = LocalContext.current
    var stats by remember { mutableStateOf(readStats(context)) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); stats = readStats(context) } }
    @Composable
    fun Bar(label: String, v: Float, detail: String) {
        Column(Modifier.fillMaxWidth()) {
            Row { Label(label); Spacer(Modifier.weight(1f)); Text(detail, fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext) }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(look.colors.text.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceIn(0f, 1f)).clip(RoundedCornerShape(4.dp)).background(if (v > 0.9f) look.colors.danger else look.colors.accent))
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        Bar("storage", stats.storageUsed, "%.1f GB free".format(stats.storageFreeGb))
        Bar("memory", stats.ramUsed, "${(stats.ramUsed * 100).toInt()}%")
        if (ctx.h >= 2) Text(
            if (stats.uptimeH >= 24) "up ${stats.uptimeH / 24}d ${stats.uptimeH % 24}h" else "up ${stats.uptimeH}h",
            fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = look.colors.subtext,
        )
    }
}

// ─────────────────────────── Stopwatch ───────────────────────────

/** Start, pause, lap. Keeps running while you're in other apps. */
@Composable
fun StopwatchWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val startedAt = ctx.state("start", "0").toLongOrNull() ?: 0L     // elapsedRealtime when running, 0 when paused
    val banked = ctx.state("banked", "0").toLongOrNull() ?: 0L
    val lap = ctx.state("lap", "").toLongOrNull()
    val running = startedAt > 0
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) { while (running) { tick = SystemClock.elapsedRealtime(); delay(47) } }
    val elapsed = banked + if (running) tick - startedAt else 0L
    fun fmt(ms: Long) = "%02d:%02d.%d".format(ms / 60_000, (ms / 1000) % 60, (ms / 100) % 10)
    Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Label(if (running) "running" else "stopwatch")
            Text(fmt(elapsed), fontFamily = RintFonts.Terminal, fontSize = if (ctx.w >= 4) 46.sp else 34.sp, color = look.colors.text, maxLines = 1)
            if (lap != null && ctx.h >= 2) Text("lap ${fmt(lap)}", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.accent)
        }
        @Composable
        fun Btn(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (filled) look.colors.accent else look.colors.text.copy(alpha = 0.1f))
                    .clickable { if (!ctx.preview) onClick() },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (filled) look.colors.onAccent else look.colors.text, modifier = Modifier.size(22.dp)) }
        }
        if (running) Btn(Icons.Rounded.Flag, false) { ctx.set("lap", "$elapsed") }
        else Btn(Icons.Rounded.Refresh, false) { ctx.set("banked", "0"); ctx.set("lap", "") }
        Spacer(Modifier.width(8.dp))
        Btn(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, true) {
            if (running) { ctx.set("banked", "$elapsed"); ctx.set("start", "0") }
            else ctx.set("start", "${SystemClock.elapsedRealtime()}")
        }
    }
}
