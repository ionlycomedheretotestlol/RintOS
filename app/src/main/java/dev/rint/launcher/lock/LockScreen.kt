package dev.rint.launcher.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.Align
import dev.rint.launcher.core.Binding
import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.core.GestureAction
import dev.rint.launcher.core.LockStyle
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.core.UnlockAnim
import dev.rint.launcher.home.Wallpaper
import dev.rint.launcher.mascot.BobbingHead
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.mascot.SpeechBubble
import dev.rint.launcher.music.Artwork
import dev.rint.launcher.music.lyricFont
import dev.rint.launcher.music.rememberLyrics
import dev.rint.launcher.music.rememberPosition
import dev.rint.launcher.system.Badges
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberAmbientClock
import dev.rint.launcher.ui.rememberHaptic
import dev.rint.launcher.ui.glass
import dev.rint.launcher.widgets.ClockFace
import dev.rint.launcher.widgets.rememberBattery
import dev.rint.launcher.widgets.rememberNow
import dev.rint.launcher.widgets.timeInWords
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private fun fmt(p: String, c: Calendar) = SimpleDateFormat(p, dev.rint.launcher.ui.I18n.locale).format(c.time)

@Composable
fun LockScreen(onUnlock: () -> Unit, onShortcut: (Binding) -> Unit) {
    val look = LocalRint.current
    val lc = look.cfg.lock
    val scope = rememberCoroutineScope()
    val v = rememberHaptic()
    val p = remember { Animatable(0f) }
    fun release(fling: Boolean) {
        scope.launch {
            if (fling || p.value > 0.35f) {
                Haptics.confirm(v)
                p.animateTo(1f, tween(320))
                onUnlock()
                p.snapTo(0f)
            } else p.animateTo(0f, dev.rint.launcher.ui.RintSprings.pop())
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                var velocity = 0f
                detectVerticalDragGestures(
                    onDragEnd = { release(velocity < -18f) },
                    onDragCancel = { release(false) },
                ) { ch, dy ->
                    ch.consume()
                    velocity = dy
                    scope.launch { p.snapTo((p.value - dy / (size.height * 0.45f)).coerceIn(0f, 1f)) }
                }
            }
    ) {
        val progress = p.value
        val content: @Composable BoxScope.() -> Unit = {
            Wallpaper()
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = lc.dim)))
            if (lc.style == LockStyle.TERMINAL) Box(Modifier.fillMaxSize().background(Color.Black))
            LockContent(onShortcut, progress)
        }
        when (lc.unlockAnim) {
            UnlockAnim.SPLIT -> {
                Box(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = HalfShape(true); translationY = -progress * size.height * 0.5f }) { content() }
                Box(Modifier.fillMaxSize().graphicsLayer { clip = true; shape = HalfShape(false); translationY = progress * size.height * 0.5f }) { content() }
            }
            else -> Box(Modifier.fillMaxSize().graphicsLayer {
                when (lc.unlockAnim) {
                    UnlockAnim.SLIDE_UP -> { translationY = -progress * size.height; alpha = 1f - progress * 0.4f }
                    UnlockAnim.FADE -> alpha = 1f - progress
                    UnlockAnim.ZOOM -> { val s = 1f + progress * 0.5f; scaleX = s; scaleY = s; alpha = 1f - progress }
                    UnlockAnim.PIXELS -> alpha = 1f - progress * progress
                    UnlockAnim.SPLIT -> Unit
                }
            }) { content() }
        }
        if (lc.unlockAnim == UnlockAnim.PIXELS && progress > 0f) PixelDissolve(progress)
    }
}

private class HalfShape(val top: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Rectangle(if (top) Rect(0f, 0f, size.width, size.height / 2) else Rect(0f, size.height / 2, size.width, size.height))
}

@Composable
private fun PixelDissolve(p: Float) {
    val accent = LocalRint.current.colors.accent
    val cells = remember { List(180) { Random.nextFloat() } }
    Canvas(Modifier.fillMaxSize()) {
        val cols = 12
        val cw = size.width / cols
        val rows = (size.height / cw).toInt() + 1
        for (i in 0 until cols * rows) {
            val r = cells[i % cells.size]
            val local = ((p - r * 0.6f) / 0.4f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val s = cw * (1f - local)
            drawRect(accent.copy(alpha = (1f - local) * 0.8f), Offset((i % cols) * cw + (cw - s) / 2, (i / cols) * cw + (cw - s) / 2), Size(s, s))
        }
    }
}

@Composable
private fun LockContent(onShortcut: (Binding) -> Unit, progress: Float) {
    val look = LocalRint.current
    val lc = look.cfg.lock
    val now = rememberNow(1000)
    val battery = rememberBattery()
    val np by RintApp.instance.music.now.collectAsState()
    val clockColor = if (lc.accentClock) look.colors.accent else Color.White
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (lc.style) {
                LockStyle.CLASSIC -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fmt("EEEE, d MMMM", now), color = Color.White, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    ClockFace(look.cfg.clock.copy(style = ClockStyle.THIN, align = Align.CENTER, size = 1.35f, seconds = false), Modifier.fillMaxWidth(), tint = clockColor)
                }
                LockStyle.BLOCKS -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ClockFace(look.cfg.clock.copy(style = ClockStyle.BLOCKS, align = Align.CENTER, seconds = false), Modifier.fillMaxWidth().height(130.dp), tint = if (lc.accentClock) clockColor else null)
                    Spacer(Modifier.height(10.dp))
                    Text(fmt("EEE d MMM", now).uppercase(), color = Color.White, fontFamily = RintFonts.Pixel, fontSize = 13.sp, letterSpacing = 2.sp)
                }
                LockStyle.STACKED -> Column(Modifier.fillMaxWidth()) {
                    val big = androidx.compose.ui.text.TextStyle(fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 118.sp, lineHeight = 108.sp)
                    Text(fmt(if (look.cfg.clock.use24h) "HH" else "hh", now), style = big.copy(color = clockColor))
                    Text(fmt("mm", now), style = big.copy(color = look.colors.accent))
                    Text(fmt("EEEE d MMMM", now), color = Color.White.copy(alpha = 0.85f), fontFamily = look.font, fontSize = 16.sp)
                }
                LockStyle.WORDS -> Column(Modifier.fillMaxWidth()) {
                    Text(timeInWords(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), color = clockColor, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 54.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(fmt("EEEE, d MMMM", now), color = Color.White.copy(alpha = 0.8f), fontFamily = look.font, fontSize = 16.sp)
                }
                LockStyle.ANALOG -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    ClockFace(look.cfg.clock.copy(style = ClockStyle.ANALOG, seconds = true), Modifier.size(240.dp), tint = clockColor)
                    Spacer(Modifier.height(12.dp))
                    Text(fmt("EEEE d MMMM", now), color = Color.White, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                LockStyle.TERMINAL -> TerminalLock(now, battery.level, battery.charging)
                LockStyle.MINIMAL -> Column(Modifier.align(Alignment.BottomStart).padding(bottom = 30.dp)) {
                    Text(fmt(if (look.cfg.clock.use24h) "HH:mm" else "h:mm", now), color = clockColor, fontFamily = look.font, fontSize = 44.sp, fontWeight = FontWeight.Light)
                    Text(fmt("EEE d MMM", now), color = Color.White.copy(alpha = 0.6f), fontFamily = look.font, fontSize = 14.sp)
                }
                LockStyle.POSTER -> Column(Modifier.fillMaxWidth()) {
                    Text(fmt("EEEE", now).uppercase(), color = look.colors.accent, fontFamily = RintFonts.Pixel, fontSize = 16.sp, letterSpacing = 3.sp)
                    Text(fmt("d", now), color = clockColor, fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 190.sp, lineHeight = 170.sp)
                    Text(fmt("MMMM", now), color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 38.sp)
                    Text(fmt(if (look.cfg.clock.use24h) "HH:mm" else "h:mm a", now), color = Color.White.copy(alpha = 0.7f), fontFamily = RintFonts.Terminal, fontSize = 30.sp)
                }
                LockStyle.MUSIC -> MusicLock(now)
                LockStyle.RIN -> RinLock(now)
            }
        }
        if (lc.message.isNotBlank()) Text(lc.message, color = Color.White.copy(alpha = 0.75f), fontFamily = look.font, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
        if (lc.music && np != null && lc.style != LockStyle.MUSIC) MiniPlayer()
        if (lc.notifications) NotificationRow()
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (lc.battery) Text(
                "${battery.level}%" + if (battery.charging) " · charging" else "",
                color = if (battery.charging) Color(0xFF3DDC84) else Color.White.copy(alpha = 0.7f), fontFamily = RintFonts.Pixel, fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            if (lc.rin && lc.style != LockStyle.RIN) {
                val hour = now.get(Calendar.HOUR_OF_DAY)
                RinSprite(if (hour >= 23 || hour < 6) Pose.SLEEP else Pose.DROWSY, 44.dp)
            }
        }
        Shortcuts(lc.shortcuts.take(10), onShortcut)
        UnlockHint(progress)
    }
}

@Composable
private fun TerminalLock(now: Calendar, level: Int, charging: Boolean) {
    val green = Color(0xFF39FF88)
    val t = rememberAmbientClock()
    Column(Modifier.fillMaxWidth()) {
        val lines = listOf(
            "rin@rintos:~$ date",
            fmt("EEE dd MMM yyyy", now),
            "rin@rintos:~$ time",
            fmt("HH:mm:ss", now),
            "rin@rintos:~$ battery",
            "$level% ${if (charging) "[charging]" else "[discharging]"}",
            "rin@rintos:~$ unlock --swipe-up" + if ((t * 2).toInt() % 2 == 0) "█" else "",
        )
        lines.forEachIndexed { i, l ->
            Text(l, color = if (i % 2 == 0) green.copy(alpha = 0.6f) else green, fontFamily = RintFonts.Terminal, fontSize = if (i == 3) 64.sp else 24.sp)
        }
    }
}

@Composable
private fun MusicLock(now: Calendar) {
    val look = LocalRint.current
    val np by RintApp.instance.music.now.collectAsState()
    val (lyrics, _) = rememberLyrics(np?.track)
    val pos = rememberPosition(np, look.cfg.music.offsetMs)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(fmt(if (look.cfg.clock.use24h) "HH:mm" else "h:mm", now), color = Color.White, fontFamily = RintFonts.Terminal, fontSize = 48.sp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(250.dp).clip(RoundedCornerShape(30.dp))) { Artwork(np, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(16.dp))
        Text(np?.track?.title ?: "nothing playing", color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(np?.track?.artist ?: "play something from the music widget", color = Color.White.copy(alpha = 0.65f), fontFamily = look.font, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        val line = lyrics?.let { it.lines.getOrNull(it.indexAt(pos))?.text }
        if (lyrics?.isBreak(pos) == true && np?.playing == true) BobbingHead(true, 48.dp)
        else Text(line ?: "♪", color = look.colors.accent, fontFamily = lyricFont(look.cfg.music.lyricsFont, look.font), fontSize = 24.sp, textAlign = TextAlign.Center)
        if (np != null) PlayerButtons(np!!.playing)
    }
}

@Composable
private fun RinLock(now: Calendar) {
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val night = hour >= 23 || hour < 6
    val t = rememberAmbientClock()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        SpeechBubble(fmt("HH:mm", now) + " · " + fmt("EEE d MMM", now).lowercase())
        Spacer(Modifier.height(10.dp))
        RinSprite(if (night) Pose.SLEEP else Pose.SIT, 180.dp, Modifier.graphicsLayer {
            val b = sin(t * 2.2f)
            translationY = if (night) 0f else -abs(b) * 8f
            scaleY = if (night) 1f + 0.02f * b else 1f
        })
        if (night) Text("z z z", fontFamily = RintFonts.Pixel, color = LocalRint.current.colors.accent, fontSize = 14.sp)
    }
}

@Composable
private fun PlayerButtons(playing: Boolean) {
    val engine = RintApp.instance.music
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp).pressable(PressEffect.BOUNCE) { engine.previous() })
        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(horizontal = 18.dp).size(52.dp).pressable(PressEffect.BOUNCE) { engine.toggle() })
        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp).pressable(PressEffect.BOUNCE) { engine.next() })
    }
}

@Composable
private fun MiniPlayer() {
    val look = LocalRint.current
    val np by RintApp.instance.music.now.collectAsState()
    val cur = np ?: return
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(RoundedCornerShape(22.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))) { Artwork(cur, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(cur.track.title, color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(cur.track.artist, color = Color.White.copy(alpha = 0.65f), fontFamily = look.font, fontSize = 12.sp, maxLines = 1)
        }
        Icon(if (cur.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White,
            modifier = Modifier.size(34.dp).pressable(PressEffect.BOUNCE) { RintApp.instance.music.toggle() })
    }
}

@Composable
private fun NotificationRow() {
    val counts by Badges.counts.collectAsState()
    if (counts.isEmpty()) return
    val apps by RintApp.instance.apps.apps.collectAsState()
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        counts.entries.sortedByDescending { it.value }.take(6).forEach { (pkg, n) ->
            val key = apps.firstOrNull { it.packageName == pkg }?.key ?: return@forEach
            Box {
                AppIconView(key, 34.dp, showBadge = false)
                Box(
                    Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(LocalRint.current.colors.accent),
                    contentAlignment = Alignment.Center,
                ) { Text("$n", fontSize = 9.sp, color = Color.White, fontFamily = RintFonts.Pixel) }
            }
        }
    }
}

fun actionIcon(a: GestureAction): ImageVector = when (a) {
    GestureAction.FLASHLIGHT -> Icons.Rounded.FlashlightOn
    GestureAction.CAMERA -> Icons.Rounded.PhotoCamera
    GestureAction.MUSIC -> Icons.Rounded.MusicNote
    GestureAction.NOTIFICATIONS -> Icons.Rounded.Notifications
    GestureAction.QUICK_SETTINGS -> Icons.Rounded.Tune
    GestureAction.LOCK -> Icons.Rounded.Lock
    GestureAction.SEARCH -> Icons.Rounded.Search
    GestureAction.SETTINGS -> Icons.Rounded.Settings
    GestureAction.DRAWER -> Icons.Rounded.Apps
    else -> Icons.Rounded.Bolt
}

@Composable
private fun Shortcuts(list: List<Binding>, onShortcut: (Binding) -> Unit) {
    if (list.isEmpty()) return
    val v = rememberHaptic()
    list.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = if (row.size <= 2) Arrangement.SpaceBetween else Arrangement.SpaceEvenly) {
            row.forEach { b ->
                Box(
                    Modifier.size(54.dp).glass(CircleShape)
                        .pressable(PressEffect.BOUNCE) { Haptics.confirm(v); onShortcut(b) },
                    contentAlignment = Alignment.Center,
                ) {
                    when (b.action) {
                        GestureAction.ASSISTANT -> RinSprite(Pose.HEAD, 34.dp)
                        GestureAction.LAUNCH_APP -> b.app?.let { AppIconView(it, 34.dp, showBadge = false) }
                        else -> Icon(actionIcon(b.action), null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UnlockHint(progress: Float) {
    val t = rememberAmbientClock()
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.graphicsLayer { translationY = -abs(sin(t * 2.5f)) * 10f })
        Text("swipe up to unlock", color = Color.White.copy(alpha = 0.7f * (1f - progress)), fontFamily = RintFonts.Pixel, fontSize = 10.sp)
    }
}
