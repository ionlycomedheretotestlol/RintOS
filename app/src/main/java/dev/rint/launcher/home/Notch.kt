package dev.rint.launcher.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.NotchContent
import dev.rint.launcher.core.NotchShape
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.mascot.BobbingHead
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.music.Artwork
import dev.rint.launcher.music.MusicOverlay
import dev.rint.launcher.music.lyricFont
import dev.rint.launcher.music.rememberLyrics
import dev.rint.launcher.music.rememberPosition
import dev.rint.launcher.system.Badges
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.color
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.widgets.rememberBattery
import dev.rint.launcher.widgets.rememberNow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun RintNotch(state: LauncherState, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    val n = look.cfg.notch
    if (!n.enabled) return
    val v = LocalView.current
    val ctx = LocalContext.current
    val np by RintApp.instance.music.now.collectAsState()
    val live = n.liveActivity && np?.playing == true
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val expanded = state.notchExpanded

    val baseW = when (n.shape) {
        NotchShape.DOT -> n.height.dp
        NotchShape.WIDE -> n.width.dp * 1.6f
        else -> n.width.dp
    }
    val w by animateDpAsState(
        when {
            expanded -> screenW - 20.dp
            live -> baseW + 96.dp
            else -> baseW
        }, RintSprings.pop(), label = "w",
    )
    val h by animateDpAsState(if (expanded) 200.dp else n.height.dp, RintSprings.pop(), label = "h")
    val attached = n.shape == NotchShape.TEARDROP || n.shape == NotchShape.TAB
    val radius: Dp = when {
        expanded -> 36.dp
        n.shape == NotchShape.TAB -> 14.dp
        else -> n.height.dp / 2
    }
    val shape = if (attached && !expanded) RoundedCornerShape(0.dp, 0.dp, radius, radius) else RoundedCornerShape(radius)
    val top by animateDpAsState(if (attached && !expanded) 0.dp else n.offsetY.dp, RintSprings.pop(), label = "t")
    val bg = n.color.color()

    Box(modifier.fillMaxWidth().padding(top = top), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .width(w)
                .height(if (attached && !expanded) h + 6.dp else h)
                .then(if (n.glow) Modifier.shadow(if (expanded) 24.dp else 10.dp, shape, ambientColor = look.colors.accent, spotColor = look.colors.accent) else Modifier)
                .clip(shape)
                .background(bg)
                .pointerInput(n.expandOnTap, look.cfg.gestures.notchLongPress) {
                    detectTapGestures(
                        onTap = { if (n.expandOnTap) { Haptics.tap(v); state.notchExpanded = !state.notchExpanded } },
                        onLongPress = { Haptics.heavy(v); state.run(ctx, look.cfg.gestures.notchLongPress) },
                    )
                },
        ) {
            AnimatedContent(expanded, label = "notch", transitionSpec = { fadeIn(tween(220, 90)) togetherWith fadeOut(tween(90)) }) { ex ->
                if (ex) ExpandedNotch(state) else CollapsedNotch(live, n.left, n.right)
            }
        }
    }
}

@Composable
private fun CollapsedNotch(live: Boolean, left: NotchContent, right: NotchContent) {
    val look = LocalRint.current
    if (look.cfg.notch.shape == NotchShape.DOT && !live) return
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (live) {
            val np by RintApp.instance.music.now.collectAsState()
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))) { Artwork(np, Modifier.fillMaxSize()) }
            Spacer(Modifier.weight(1f))
            Equalizer(look.colors.accent)
        } else {
            NotchSlot(left)
            Spacer(Modifier.weight(1f))
            NotchSlot(right)
        }
    }
}

@Composable
private fun NotchSlot(c: NotchContent) {
    val look = LocalRint.current
    val fg = Color.White
    when (c) {
        NotchContent.TIME -> {
            val now = rememberNow(15_000)
            Text(SimpleDateFormat(if (look.cfg.clock.use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(now.time),
                fontFamily = RintFonts.Terminal, fontSize = 17.sp, color = fg)
        }
        NotchContent.DATE -> {
            val now = rememberNow(60_000)
            Text(SimpleDateFormat("d MMM", Locale.getDefault()).format(now.time), fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = fg)
        }
        NotchContent.BATTERY -> {
            val b = rememberBattery()
            BatteryGlyph(b.level, b.charging)
        }
        NotchContent.NOTIFICATIONS -> {
            val counts by Badges.counts.collectAsState()
            val total = counts.values.sum()
            if (total > 0) Box(Modifier.size(18.dp).clip(CircleShape).background(look.colors.accent), contentAlignment = Alignment.Center) {
                Text("$total", fontSize = 10.sp, color = look.colors.onAccent, fontFamily = RintFonts.Pixel)
            }
        }
        NotchContent.MASCOT -> RinSprite(Pose.HEAD, 24.dp)
        NotchContent.NOW_PLAYING -> {
            val np by RintApp.instance.music.now.collectAsState()
            np?.let { Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))) { Artwork(it, Modifier.fillMaxSize()) } }
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int, charging: Boolean) {
    val look = LocalRint.current
    val col = when {
        charging -> Color(0xFF3DDC84)
        level <= 15 -> look.colors.danger
        else -> Color.White
    }
    Canvas(Modifier.size(26.dp, 13.dp)) {
        val body = Size(size.width - 3.dp.toPx(), size.height)
        drawRoundRect(Color.White.copy(alpha = 0.4f), size = body, cornerRadius = CornerRadius(4.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()))
        val pad = 2.dp.toPx()
        drawRoundRect(col, Offset(pad, pad), Size((body.width - pad * 2) * level / 100f, body.height - pad * 2), CornerRadius(2.dp.toPx()))
        drawRoundRect(Color.White.copy(alpha = 0.4f), Offset(body.width + 0.5.dp.toPx(), size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f), CornerRadius(1.dp.toPx()))
    }
}

@Composable
private fun Equalizer(color: Color) {
    val t = dev.rint.launcher.ui.rememberAmbientClock()
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 4) {
            val v = 0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * (4.1f + i * 1.3f) + i))
            Box(Modifier.padding(horizontal = 1.5.dp).width(3.dp).fillMaxHeight(v).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun ExpandedNotch(state: LauncherState) {
    val look = LocalRint.current
    val engine = RintApp.instance.music
    val np by engine.now.collectAsState()
    Box(Modifier.fillMaxSize().padding(18.dp)) {
        val cur = np
        if (cur == null) {
            val now = rememberNow(1000)
            val b = rememberBattery()
            val counts by Badges.counts.collectAsState()
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat(if (look.cfg.clock.use24h) "HH:mm" else "h:mm", Locale.getDefault()).format(now.time), fontFamily = RintFonts.Terminal, fontSize = 64.sp, color = Color.White)
                    Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now.time), fontFamily = look.font, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Text("${b.level}% ${if (b.charging) "· charging" else ""}  ·  ${counts.values.sum()} notifications", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.accent)
                }
                val hour = now.get(Calendar.HOUR_OF_DAY)
                RinSprite(if (hour >= 23 || hour < 6) Pose.SLEEP else Pose.SIT, 96.dp)
            }
        } else {
            val (lyrics, _) = rememberLyrics(cur.track)
            val pos = rememberPosition(cur, look.cfg.music.offsetMs)
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).pressable(PressEffect.SHRINK) { state.notchExpanded = false; MusicOverlay.show() }) {
                        Artwork(cur, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cur.track.title, color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(cur.track.artist, color = Color.White.copy(alpha = 0.65f), fontFamily = look.font, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Equalizer(if (cur.playing) look.colors.accent else Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                val brk = lyrics?.isBreak(pos) == true
                if (brk && look.cfg.mascot.inMusic) BobbingHead(cur.playing, 34.dp)
                else Text(
                    lyrics?.let { it.lines.getOrNull(it.indexAt(pos))?.text } ?: "♪",
                    color = Color.White, fontFamily = lyricFont(look.cfg.music.lyricsFont, look.font), fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val dur = cur.track.durationMs.coerceAtLeast(1)
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).background(Color.White))
                    }
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(28.dp).pressable(PressEffect.BOUNCE) { engine.previous() })
                    Icon(if (cur.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp).pressable(PressEffect.BOUNCE) { engine.toggle() })
                    Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(28.dp).pressable(PressEffect.BOUNCE) { engine.next() })
                }
            }
        }
    }
}
