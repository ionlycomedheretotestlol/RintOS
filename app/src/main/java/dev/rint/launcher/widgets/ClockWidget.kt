package dev.rint.launcher.widgets

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.rint.launcher.ui.lighten
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.Align
import dev.rint.launcher.core.ClockCfg
import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.blockWidth
import dev.rint.launcher.ui.drawBlocks
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun rememberNow(everyMs: Long = 1000): Calendar {
    // Only tick while visible; resuming refreshes immediately.
    val active = dev.rint.launcher.ui.rememberForeground() && !dev.rint.launcher.ui.LocalCovered.current
    val now by produceState(Calendar.getInstance(), everyMs, active) {
        value = Calendar.getInstance()
        while (active) {
            delay(everyMs - System.currentTimeMillis() % everyMs)
            value = Calendar.getInstance()
        }
    }
    return now
}

private val numberWords = listOf(
    "twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
)

/** User-typed patterns can be half-finished ("EEE d Q") — never let them take the home screen down. */
fun safeDateFormat(pattern: String): SimpleDateFormat =
    runCatching { SimpleDateFormat(pattern, dev.rint.launcher.ui.I18n.locale).also { it.format(java.util.Date()) } }
        .getOrElse { SimpleDateFormat("EEEE, d MMMM", dev.rint.launcher.ui.I18n.locale) }

private val ptHours = listOf("meio-dia", "uma", "duas", "três", "quatro", "cinco", "seis", "sete", "oito", "nove", "dez", "onze")

fun timeInWords(h24: Int, m: Int): String {
    if (dev.rint.launcher.ui.I18n.pt) {
        val r = ((m + 2) / 5) * 5
        val next = r > 30
        val h = ((if (next) h24 + 1 else h24) % 24).let { if (it == 0) "meia-noite" else ptHours[it % 12] }
        val pra = if (h == "uma" || h == "meia-noite" || h == "meio-dia") "pra" else "pras"
        return when (r % 60) {
            0 -> if (h.startsWith("mei")) h else "$h em ponto"
            5 -> "$h e cinco"; 10 -> "$h e dez"; 15 -> "$h e quinze"; 20 -> "$h e vinte"; 25 -> "$h e vinte e cinco"; 30 -> "$h e meia"
            35 -> "vinte e cinco $pra $h"; 40 -> "vinte $pra $h"; 45 -> "quinze $pra $h"; 50 -> "dez $pra $h"; else -> "cinco $pra $h"
        }
    }
    val rounded = ((m + 2) / 5) * 5
    val hour = if (rounded > 30) (h24 + 1) % 12 else h24 % 12
    val hw = numberWords[hour]
    return when (rounded % 60) {
        0 -> "$hw o'clock"
        5 -> "five past $hw"
        10 -> "ten past $hw"
        15 -> "quarter past $hw"
        20 -> "twenty past $hw"
        25 -> "twenty-five past $hw"
        30 -> "half past $hw"
        35 -> "twenty-five to $hw"
        40 -> "twenty to $hw"
        45 -> "quarter to $hw"
        50 -> "ten to $hw"
        else -> "five to $hw"
    }
}

@Composable
fun ClockWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val c = look.cfg.clock
    val context = LocalContext.current
    if (ctx.w <= 2 && ctx.h >= 2) {
        // compact card: weekday, the time, the date — the iOS widget rhythm
        val now = rememberNow(60_000)
        Column(
            Modifier.fillMaxSize()
                .clickable(remember { MutableInteractionSource() }, null) {
                    runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                .padding(16.dp),
        ) {
            Text(SimpleDateFormat("EEEE", dev.rint.launcher.ui.I18n.locale).format(now.time).uppercase(), color = look.colors.accent.lighten(),
                fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            val fits = if (c.style == ClockStyle.STACKED || c.style == ClockStyle.WORDS) ClockStyle.THIN else c.style
            ClockFace(c.copy(style = fits, align = Align.START, seconds = false), Modifier.fillMaxWidth().height(52.dp), compact = true)
            Spacer(Modifier.height(4.dp))
            Text(SimpleDateFormat("d MMMM", dev.rint.launcher.ui.I18n.locale).format(now.time), color = Color.White.copy(alpha = 0.75f),
                fontFamily = look.font, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null) {
                runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = when (c.align) {
            Align.START -> Alignment.Start
            Align.CENTER -> Alignment.CenterHorizontally
            Align.END -> Alignment.End
        },
    ) {
        val now = rememberNow(60_000)
        if (c.greeting && ctx.h >= 2) {
            val hour = now.get(Calendar.HOUR_OF_DAY)
            Text(
                when (hour) { in 5..11 -> "good morning"; in 12..17 -> "good afternoon"; in 18..22 -> "good evening"; else -> "late night mode" }.uppercase(),
                fontFamily = RintFonts.Pixel, fontSize = 10.sp, letterSpacing = 2.sp,
                color = look.colors.accent.lighten(),
                style = TextStyle(shadow = Shadow(look.colors.accent.copy(alpha = 0.8f), Offset.Zero, 16f)),
            )
            Spacer(Modifier.height(4.dp))
        }
        ClockFace(c, Modifier.weight(1f).fillMaxWidth(), compact = ctx.h <= 1)
        if (c.showDate && ctx.h >= 2) {
            Spacer(Modifier.height(8.dp))
            Text(
                safeDateFormat(c.dateFormat).format(now.time),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                style = TextStyle(
                    fontFamily = if (c.style == ClockStyle.PIXEL) RintFonts.Pixel else look.font,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                ),
            )
        }
    }
}

@Composable
fun ClockFace(c: ClockCfg, modifier: Modifier = Modifier, compact: Boolean = false, tint: Color? = null) {
    val look = LocalRint.current
    val now = rememberNow(if (c.seconds || c.blinkColon || c.style == ClockStyle.ANALOG) 1000 else 15_000)
    val color = tint ?: if (c.useAccent) look.colors.accent else Color.White
    val h = now.get(Calendar.HOUR_OF_DAY).let { if (c.use24h) it else (it % 12).let { x -> if (x == 0) 12 else x } }
    val m = now.get(Calendar.MINUTE)
    val s = now.get(Calendar.SECOND)
    val hh = if (c.use24h) "%02d".format(h) else "$h"
    val text = "$hh:%02d".format(m) + if (c.seconds) ":%02d".format(s) else ""
    val colon by animateFloatAsState(if (!c.blinkColon || s % 2 == 0) 1f else 0.25f, tween(400), label = "colon")
    val align = when (c.align) {
        Align.START -> TextAlign.Start
        Align.CENTER -> TextAlign.Center
        Align.END -> TextAlign.End
    }
    val shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 3f), 14f)
    when (c.style) {
        ClockStyle.BLOCKS -> Canvas(modifier) {
            val units = blockWidth(text)
            val cell = minOf(size.width * 0.84f / units, size.height / 5f) * c.size.coerceIn(0.4f, 1.2f)
            val w = units * cell
            val x = when (c.align) {
                Align.START -> 0f
                Align.CENTER -> (size.width - w) / 2
                Align.END -> size.width - w
            }
            val y = (size.height - cell * 5) / 2
            if (c.glow) {
                val center = Offset(x + w / 2, y + cell * 2.5f)
                val r = w * 0.62f
                drawOval(
                    Brush.radialGradient(listOf(look.colors.accent.copy(alpha = 0.45f), Color.Transparent), center, r),
                    topLeft = Offset(center.x - r, center.y - r * 0.55f),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 1.1f),
                )
            }
            drawBlocks(
                text, color, cell, origin = Offset(x, y), colonAlpha = colon,
                shadow = Color.Black.copy(alpha = 0.22f),
                bottomColor = if (c.glow && tint == null && !c.useAccent) look.colors.accent.lighten() else null,
            )
        }
        ClockStyle.THIN, ClockStyle.PIXEL -> Text(
            text,
            modifier = modifier,
            textAlign = align,
            style = TextStyle(
                fontFamily = if (c.style == ClockStyle.PIXEL) RintFonts.Pixel else look.font,
                fontSize = ((if (compact) 42 else 76) * c.size).sp,
            fontWeight = FontWeight.SemiBold,
                letterSpacing = (if (c.style == ClockStyle.THIN) (-2).sp else 0.sp),
                color = color,
                shadow = shadow,
            ),
        )
        ClockStyle.STACKED -> Column(modifier, verticalArrangement = Arrangement.Center) {
            val st = TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = ((if (compact) 28 else 58) * c.size).sp,
                lineHeight = ((if (compact) 28 else 56) * c.size).sp, color = color, shadow = shadow, textAlign = align)
            Text(hh, style = st, modifier = Modifier.fillMaxWidth())
            Text("%02d".format(m), style = st.copy(color = look.colors.accent.takeIf { !c.useAccent } ?: Color.White), modifier = Modifier.fillMaxWidth())
        }
        ClockStyle.WORDS -> Text(
            timeInWords(now.get(Calendar.HOUR_OF_DAY), m),
            modifier = modifier,
            textAlign = align,
            style = TextStyle(fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = ((if (compact) 22 else 36) * c.size).sp,
                lineHeight = ((if (compact) 24 else 38) * c.size).sp, color = color, shadow = shadow),
        )
        ClockStyle.ANALOG -> Canvas(modifier.fillMaxHeight().aspectRatio(1f)) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Color.Black.copy(alpha = 0.25f), r)
            drawCircle(color.copy(alpha = 0.35f), r, style = Stroke(r * 0.03f))
            for (i in 0 until 12) rotate(i * 30f, center) {
                drawLine(color.copy(alpha = if (i % 3 == 0) 0.9f else 0.4f), Offset(center.x, center.y - r * 0.9f), Offset(center.x, center.y - r * (if (i % 3 == 0) 0.74f else 0.8f)), r * 0.035f, StrokeCap.Round)
            }
            val hour = now.get(Calendar.HOUR) + m / 60f
            rotate(hour * 30f, center) { drawLine(color, center, Offset(center.x, center.y - r * 0.5f), r * 0.07f, StrokeCap.Round) }
            rotate(m * 6f + s / 10f, center) { drawLine(color, center, Offset(center.x, center.y - r * 0.74f), r * 0.045f, StrokeCap.Round) }
            if (c.seconds) rotate(s * 6f, center) { drawLine(look.colors.accent, center, Offset(center.x, center.y - r * 0.82f), r * 0.02f, StrokeCap.Round) }
            drawCircle(look.colors.accent, r * 0.05f, center)
        }
        ClockStyle.NONE -> Unit
    }
}
