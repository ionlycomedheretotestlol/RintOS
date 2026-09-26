package dev.rint.launcher.music

import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.Align
import dev.rint.launcher.core.LyricsFont
import dev.rint.launcher.core.PlayVia
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.mascot.BobbingHead
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberHaptic
import dev.rint.launcher.ui.lighten
import dev.rint.launcher.widgets.WidgetCtx
import kotlinx.coroutines.delay

object MusicOverlay {
    var open by mutableStateOf(false)
    var searchFirst by mutableStateOf(false)
    fun show(search: Boolean = false) {
        searchFirst = search
        open = true
    }
}

@Composable
fun rememberLyrics(track: Track?): Pair<Lyrics?, Boolean> {
    val ctx = LocalContext.current
    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(track?.title, track?.artist) {
        lyrics = null
        if (track == null) return@LaunchedEffect
        loading = true
        lyrics = LyricsService.fetch(ctx, track)
        loading = false
    }
    return lyrics to loading
}

/** Smooth, frame-accurate song position (MediaSession only reports occasionally). */
@Composable
fun rememberPosition(now: NowPlaying?, offsetMs: Long): Long {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(now) {
        if (now == null) return@LaunchedEffect
        pos = now.position() + offsetMs
        if (!now.playing) return@LaunchedEffect
        while (true) withFrameMillis { pos = now.position() + offsetMs }
    }
    return pos
}

fun lyricFont(f: LyricsFont, fallback: FontFamily) = when (f) {
    LyricsFont.TERMINAL -> RintFonts.Terminal
    LyricsFont.PIXEL -> RintFonts.Pixel
    LyricsFont.INTER -> fallback
}

@Composable
fun Artwork(np: NowPlaying?, modifier: Modifier = Modifier, placeholderIcon: Boolean = true) {
    val look = LocalRint.current
    when {
        np?.art != null -> Image(np.art.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
        np?.track?.artUrl != null -> AsyncImage(np.track.artUrl, null, modifier, contentScale = ContentScale.Crop)
        else -> Box(modifier.background(look.colors.accent), contentAlignment = Alignment.Center) {
            if (placeholderIcon) Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.fillMaxSize(0.4f))
        }
    }
}

@Composable
fun MusicWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val engine = RintApp.instance.music
    val np by engine.now.collectAsState()
    val (lyrics, _) = rememberLyrics(np?.track)
    val pos = rememberPosition(np, look.cfg.music.offsetMs)
    val v = rememberHaptic()
    LaunchedEffect(Unit) { engine.start() }

    if (ctx.w < 3) {
        // compact 2x2 card: artwork, title, the live lyric line, one big play button
        Column(
            Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { MusicOverlay.show(search = np == null) }.padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { Artwork(np, Modifier.fillMaxSize()) }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f))
                        .pressable(PressEffect.BOUNCE) { Haptics.tap(v); if (np == null) MusicOverlay.show(search = true) else engine.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (np?.playing == true) Icons.Rounded.Pause else if (np == null) Icons.Rounded.Search else Icons.Rounded.PlayArrow, null,
                        tint = Color.Black, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            val cur = np
            if (cur == null) {
                Text("Rint Music", color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("search any song", color = Color.White.copy(alpha = 0.7f), fontFamily = look.font, fontSize = 12.sp)
            } else {
                val line = lyrics?.let { l -> l.lines.getOrNull(l.indexAt(pos))?.text }
                Text(line ?: cur.track.title, color = if (line != null) look.colors.accent.lighten() else Color.White,
                    fontFamily = if (line != null) lyricFont(look.cfg.music.lyricsFont, look.font) else look.font,
                    fontWeight = FontWeight.Bold, fontSize = if (line != null) 17.sp else 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Text(cur.track.artist, color = Color.White.copy(alpha = 0.7f), fontFamily = look.font, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }

    Row(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, null) { MusicOverlay.show(search = np == null) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ctx.w >= 3) {
            Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(16.dp))) {
                Artwork(np, Modifier.fillMaxSize())
                if (np?.waiting == true) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    RinSprite(Pose.WALK, 44.dp)
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            if (np == null) {
                Text("RINT MUSIC", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.accent)
                Text("search any song.\nlyrics come alive.", fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = look.colors.text, lineHeight = 19.sp)
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(look.colors.accent).padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Search, null, tint = look.colors.onAccent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("find a song", color = look.colors.onAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = look.font)
                }
                return@Column
            }
            Column {
                Text(np!!.track.title, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = look.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np!!.track.artist, fontFamily = look.font, fontSize = 12.sp, color = look.colors.subtext, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val line = lyrics?.let { l -> l.lines.getOrNull(l.indexAt(pos))?.text }
            val brk = lyrics?.isBreak(pos) ?: false
            Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.CenterStart) {
                if (brk && look.cfg.mascot.inMusic && np!!.playing) {
                    BobbingHead(true, 28.dp)
                } else AnimatedContent(line ?: if (np!!.waiting) "loading…" else "♪", label = "l", transitionSpec = {
                    (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                }) { t ->
                    Text(t, fontFamily = lyricFont(look.cfg.music.lyricsFont, look.font), fontSize = 19.sp, color = look.colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val dur = np!!.track.durationMs.coerceAtLeast(1)
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(look.colors.text.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth((pos.toFloat() / dur).coerceIn(0f, 1f)).background(look.colors.accent))
                }
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.SkipPrevious, null, tint = look.colors.text, modifier = Modifier.size(24.dp).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.previous() })
                Icon(if (np!!.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = look.colors.text,
                    modifier = Modifier.size(30.dp).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.toggle() })
                Icon(Icons.Rounded.SkipNext, null, tint = look.colors.text, modifier = Modifier.size(24.dp).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.next() })
            }
        }
    }
}

@Composable
fun MusicPlayerScreen(onClose: () -> Unit) {
    val look = LocalRint.current
    val m = look.cfg.music
    val engine = RintApp.instance.music
    val np by engine.now.collectAsState()
    val (lyrics, loadingLyrics) = rememberLyrics(np?.track)
    val pos = rememberPosition(np, m.offsetMs)
    var searching by remember { mutableStateOf(MusicOverlay.searchFirst || np == null) }
    val v = rememberHaptic()
    LaunchedEffect(Unit) { engine.start() }
    BackHandler { if (searching && np != null) searching = false else onClose() }

    val kb = rememberInfiniteTransition(label = "kb")
    val drift by kb.animateFloat(0f, 1f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse), label = "d")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // background: the song's artwork, huge, blurred and slowly drifting
        Artwork(
            np,
            placeholderIcon = false,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val z = if (m.kenBurns) 1.25f + 0.12f * drift else 1.2f
                    scaleX = z; scaleY = z
                    if (m.kenBurns) { translationX = (drift - 0.5f) * 80f; translationY = (0.5f - drift) * 50f }
                }
                .blur(m.bgBlur.dp),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = m.bgDim * 0.6f), Color.Black.copy(alpha = m.bgDim), Color.Black.copy(alpha = (m.bgDim + 0.3f).coerceAtMost(0.95f)),
        ))))

        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.KeyboardArrowDown, "close", tint = Color.White, modifier = Modifier.size(34.dp).pressable(PressEffect.BOUNCE) { onClose() })
                Spacer(Modifier.weight(1f))
                val src = np?.let {
                    when (it.source) {
                        Source.LOCAL -> "on this phone"
                        Source.APP -> it.appPackage?.let { p -> appLabel(p) } ?: "your music app"
                        Source.NONE -> null
                    }
                }
                if (src != null) Text("via $src", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Search, "search", tint = Color.White, modifier = Modifier.size(28.dp).pressable(PressEffect.BOUNCE) { searching = true })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LyricsStage(np, lyrics, loadingLyrics, pos, Modifier.fillMaxSize())
            }

            if (np != null) NowPlayingBar(np!!, pos, onSeek = { engine.seekTo(it) })
            Spacer(Modifier.height(16.dp))
        }

        AnimatedVisibility(searching, enter = fadeIn() + slideInVertically { it / 6 }, exit = fadeOut() + slideOutVertically { it / 6 }) {
            MusicSearchSheet(
                onPick = { t ->
                    Haptics.confirm(v)
                    searching = false
                    engine.play(t, if (m.playVia == PlayVia.LOCAL) null else m.preferredApp)
                },
                onClose = { if (np != null) searching = false else onClose() },
            )
        }
    }
}

@Composable
private fun appLabel(pkg: String): String {
    val ctx = LocalContext.current
    return remember(pkg) {
        runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
}

@Composable
internal fun LyricsStage(np: NowPlaying?, lyrics: Lyrics?, loading: Boolean, pos: Long, modifier: Modifier) {
    val look = LocalRint.current
    val m = look.cfg.music
    val font = lyricFont(m.lyricsFont, look.font)
    val align = when (m.align) { Align.START -> TextAlign.Start; Align.CENTER -> TextAlign.Center; Align.END -> TextAlign.End }
    val hAlign = when (m.align) { Align.START -> Alignment.Start; Align.CENTER -> Alignment.CenterHorizontally; Align.END -> Alignment.End }
    val highlight = Color(m.highlight.toInt())

    Box(modifier.padding(horizontal = 26.dp), contentAlignment = Alignment.CenterStart) {
        when {
            np == null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RinSprite(Pose.SIT, 120.dp)
                Spacer(Modifier.height(12.dp))
                Text("pick a song, any song", fontFamily = RintFonts.Pixel, color = Color.White, fontSize = 14.sp)
            }
            np.waiting -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WalkingRin()
                Spacer(Modifier.height(12.dp))
                Text("loading ${np.track.title.lowercase()}…", fontFamily = font, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
                Text("waiting for it to start playing", fontFamily = RintFonts.Pixel, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
            }
            lyrics == null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BobbingHead(np.playing, 110.dp)
                Spacer(Modifier.height(16.dp))
                Text(if (loading) "finding lyrics…" else "no lyrics for this one.\njust vibe.", fontFamily = font, color = Color.White, fontSize = 22.sp, textAlign = TextAlign.Center)
            }
            !lyrics.synced -> PlainLyrics(lyrics, np, pos, font, align)
            else -> {
                val idx = lyrics.indexAt(pos)
                val brk = lyrics.isBreak(pos)
                Column(Modifier.fillMaxWidth(), horizontalAlignment = hAlign) {
                    val prev = lyrics.lines.getOrNull(idx - 1)?.text
                    if (!brk && prev != null) Text(prev, fontFamily = font, fontSize = (m.lyricsSize * 0.6f).sp, color = Color.White.copy(alpha = 0.35f), textAlign = align, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    AnimatedContent(
                        targetState = if (brk) -2 else idx,
                        label = "line",
                        transitionSpec = {
                            (slideInVertically(spring(dampingRatio = 0.8f)) { it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutVertically(tween(220)) { -it / 2 } + fadeOut(tween(160)))
                        },
                    ) { i ->
                        if (i == -2) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (look.cfg.mascot.inMusic) BobbingHead(np.playing, 96.dp)
                                Text("♪ ♪ ♪", fontFamily = font, color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp)
                            }
                        } else {
                            val line = lyrics.lines.getOrNull(i)
                            val next = lyrics.lines.getOrNull(i + 1)
                            val p = if (line != null && next != null) ((pos - line.timeMs).toFloat() / (next.timeMs - line.timeMs).coerceAtLeast(1)).coerceIn(0f, 1f) else 1f
                            KaraokeLine(line?.text ?: (lyrics.lines.firstOrNull()?.text ?: ""), p, font, m.lyricsSize, highlight, align, dimmed = i < 0)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (!brk) for (k in 1..m.upcoming) {
                        lyrics.lines.getOrNull(idx + k)?.text?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontFamily = font, fontSize = (m.lyricsSize * 0.62f).sp, color = Color.White.copy(alpha = 0.42f / k), textAlign = align,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), maxLines = 2)
                        }
                    }
                }
            }
        }
        Text(lyrics?.source?.let { "lyrics: $it" } ?: "", fontFamily = RintFonts.Pixel, fontSize = 8.sp, color = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun KaraokeLine(text: String, progress: Float, font: FontFamily, size: Float, highlight: Color, align: TextAlign, dimmed: Boolean) {
    Box(Modifier.fillMaxWidth()) {
        val st = TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = (size * 1.08f).sp, textAlign = align)
        Text(text, style = st.copy(color = Color.White.copy(alpha = if (dimmed) 0.35f else 0.4f)), modifier = Modifier.fillMaxWidth())
        if (!dimmed) Text(
            text,
            style = st.copy(color = highlight),
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                clip = true
                shape = object : androidx.compose.ui.graphics.Shape {
                    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                        androidx.compose.ui.graphics.Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, size.width * (0.06f + progress * 0.94f), size.height))
                }
            },
        )
    }
}

@Composable
private fun PlainLyrics(lyrics: Lyrics, np: NowPlaying, pos: Long, font: FontFamily, align: TextAlign) {
    val state = rememberLazyListState()
    val frac = if (np.track.durationMs > 0) pos.toFloat() / np.track.durationMs else 0f
    LaunchedEffect((frac * lyrics.lines.size).toInt()) {
        state.animateScrollToItem(((frac * lyrics.lines.size).toInt() - 2).coerceAtLeast(0))
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
        item {
            Text("unsynced lyrics", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 12.dp))
        }
        items(lyrics.lines) {
            Text(it.text, fontFamily = font, fontSize = 22.sp, color = Color.White.copy(alpha = 0.85f), textAlign = align,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
        }
    }
}

@Composable
private fun WalkingRin() {
    val t = rememberInfiniteTransition(label = "w")
    val x by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "x")
    val hop by t.animateFloat(0f, 1f, infiniteRepeatable(tween(320, easing = LinearEasing)), label = "h")
    RinSprite(Pose.WALK, 90.dp, flip = false, modifier = Modifier.graphicsLayer {
        translationX = x * 120f
        translationY = -kotlin.math.abs(kotlin.math.sin(hop * Math.PI.toFloat())) * 10f
        scaleX = if (x < 0f) 1f else -1f
    })
}

@Composable
private fun NowPlayingBar(np: NowPlaying, pos: Long, onSeek: (Long) -> Unit) {
    val look = LocalRint.current
    val engine = RintApp.instance.music
    val v = rememberHaptic()
    var scrub by remember { mutableFloatStateOf(-1f) }
    val dur = np.track.durationMs.coerceAtLeast(1)
    val shown = if (scrub >= 0) scrub else (pos.toFloat() / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))) { Artwork(np, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(np.track.title, color = Color.White, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(np.track.artist, color = Color.White.copy(alpha = 0.7f), fontFamily = look.font, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OffsetNudger()
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(dur) {
                    detectTapGestures { onSeek((it.x / size.width * dur).toLong()) }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onSeek((scrub * dur).toLong()); scrub = -1f },
                        onDragCancel = { scrub = -1f },
                    ) { ch, _ -> scrub = (ch.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.fillMaxWidth().height(if (scrub >= 0) 10.dp else 6.dp)) {
                drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
                drawRoundRect(Color.White, size = size.copy(width = size.width * shown), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt((shown * dur).toLong()), color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Terminal, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(fmt(dur), color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Terminal, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(44.dp).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.previous() })
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.White).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (np.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(40.dp))
            }
            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(44.dp).pressable(PressEffect.BOUNCE) { Haptics.tap(v); engine.next() })
        }
    }
}

/** Lets people fix lyric drift by ear: tap − / + to shift lyrics by 250 ms. */
@Composable
private fun OffsetNudger() {
    val stores = RintApp.instance.stores
    val off = LocalRint.current.cfg.music.offsetMs
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("−", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable {
            stores.config.update { it.copy(music = it.music.copy(offsetMs = it.music.offsetMs - 250)) }
        }.padding(8.dp))
        Text(if (off == 0L) "sync" else "%+.2fs".format(off / 1000f), color = Color.White.copy(alpha = 0.7f), fontFamily = RintFonts.Pixel, fontSize = 9.sp)
        Text("+", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable {
            stores.config.update { it.copy(music = it.music.copy(offsetMs = it.music.offsetMs + 250)) }
        }.padding(8.dp))
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

data class MusicApp(val pkg: String, val label: String)

@Composable
fun rememberMusicApps(): List<MusicApp> {
    val ctx = LocalContext.current
    return remember {
        val pm = ctx.packageManager
        pm.queryIntentActivities(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH), 0)
            .map { MusicApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { it.pkg != ctx.packageName }
    }
}

@Composable
private fun MusicSearchSheet(onPick: (Track) -> Unit, onClose: () -> Unit) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val stores = RintApp.instance.stores
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var choosingFor by remember { mutableStateOf<Track?>(null) }
    val focus = remember { FocusRequester() }
    val apps = rememberMusicApps()
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        delay(350)
        busy = true
        results = MusicSearch.search(ctx, q.trim())
        busy = false
    }

    fun pick(t: Track) {
        val m = stores.config.value.music
        if (t.localUri == null && m.playVia != PlayVia.LOCAL && (m.preferredApp == null || m.playVia == PlayVia.ASK) && apps.size > 1) {
            choosingFor = t
        } else {
            if (t.localUri == null && m.preferredApp == null && apps.size == 1) {
                stores.config.update { it.copy(music = it.music.copy(preferredApp = apps[0].pkg)) }
            }
            onPick(t)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("song, artist, vibe…", color = Color.White.copy(alpha = 0.45f), fontSize = 16.sp, fontFamily = look.font)
                    BasicTextField(
                        q, { q = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = look.font),
                        cursorBrush = SolidColor(look.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { pick(it) } }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.Close, "close", tint = Color.White, modifier = Modifier.size(28.dp).clickable { onClose() })
        }
        if (busy) Text("searching…", color = Color.White.copy(alpha = 0.5f), fontFamily = RintFonts.Pixel, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)) {
            items(results, key = { "${it.title}|${it.artist}|${it.localUri}" }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { pick(t) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))) {
                        Artwork(NowPlaying(t), Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = Color.White, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontFamily = look.font, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (t.localUri != null) Icon(Icons.Rounded.PhoneAndroid, "on device", tint = look.colors.accent, modifier = Modifier.size(18.dp))
                    else Icon(Icons.Rounded.GraphicEq, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    choosingFor?.let { t ->
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null) { choosingFor = null },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(look.colors.panelStrong)
                    .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding().padding(20.dp),
            ) {
                Text("play it with…", fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = look.colors.text)
                Text("RintOS starts the song in your music app and follows along with lyrics.", color = look.colors.subtext, fontSize = 13.sp, fontFamily = look.font)
                Spacer(Modifier.height(12.dp))
                apps.forEach { app ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                            stores.config.update { it.copy(music = it.music.copy(preferredApp = app.pkg, playVia = PlayVia.APP)) }
                            choosingFor = null
                            onPick(t)
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dev.rint.launcher.ui.AppIconView(RintApp.instance.apps.apps.value.firstOrNull { it.packageName == app.pkg }?.key ?: "", 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, color = look.colors.text, fontFamily = look.font, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
