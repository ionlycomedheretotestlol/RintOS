package dev.rint.launcher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.GestureAction
import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.ItemKind
import dev.rint.launcher.core.PageIndicator
import dev.rint.launcher.core.PageTransition
import dev.rint.launcher.core.SearchBarPos
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinDirector
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.music.MusicOverlay
import dev.rint.launcher.music.MusicPlayerScreen
import dev.rint.launcher.settings.SettingsScreen
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.LocalCovered
import dev.rint.launcher.ui.LocalBackdrop
import dev.rint.launcher.ui.rememberAmbientClock
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.glass
import androidx.compose.material.icons.rounded.Search
import dev.rint.launcher.ui.color
import dev.rint.launcher.widgets.WidgetRegistry
import dev.rint.launcher.widgets.rememberBattery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Applies [wallpaperFx] to the window: blur + zoom of the system wallpaper while overlays are open. */
class WindowFx(val apply: (Float) -> Unit)

val LocalWindowFx = androidx.compose.runtime.staticCompositionLocalOf { WindowFx {} }

fun seedLayoutIfNeeded() {
    val stores = RintApp.instance.stores
    val repo = RintApp.instance.apps
    if (stores.layout.value.seeded || repo.apps.value.isEmpty()) return
    val dock = repo.defaultDock()
    val cols = stores.config.value.home.columns
    val apps = repo.defaultHomeApps(dock, cols * 4).mapIndexed { i, key ->
        HomeItem(page = 0, x = i % cols, y = 2 + i / cols, kind = ItemKind.APP, app = key)
    }
    stores.layout.replace(
        dev.rint.launcher.core.HomeLayout(
            pages = 2,
            seeded = true,
            dock = dock,
            items = listOf(
                HomeItem(page = 0, x = 0, y = 0, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "clock"),
                HomeItem(page = 0, x = 2, y = 0, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "music"),
            ) + apps + listOf(
                HomeItem(page = 1, x = 0, y = 0, w = 4, h = 1, kind = ItemKind.WIDGET, widget = "ask"),
                HomeItem(page = 1, x = 0, y = 1, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "pet"),
                HomeItem(page = 1, x = 2, y = 1, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "notes"),
                HomeItem(page = 1, x = 0, y = 3, w = 4, h = 3, kind = ItemKind.WIDGET, widget = "todo"),
            ),
        )
    )
}

@Composable
fun Launcher(state: LauncherState) {
    val look = LocalRint.current
    val cfg = look.cfg
    val stores = RintApp.instance.stores
    val layout by stores.layout.state.collectAsState()
    val apps by RintApp.instance.apps.apps.collectAsState()
    val fx = LocalWindowFx.current
    val battery = rememberBattery()

    LaunchedEffect(apps.isNotEmpty()) { seedLayoutIfNeeded() }
    LaunchedEffect(cfg.home.columns, cfg.home.rows) { stores.layout.update { it.reflow(cfg.home.columns, cfg.home.rows) } }
    LaunchedEffect(Unit) { RintApp.instance.music.start() }

    val pager = rememberPagerState { layout.pages.coerceAtLeast(1) }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { state.currentPage = it } }
    LaunchedEffect(state.pendingPage) {
        state.pendingPage?.let { p ->
            delay(60)
            pager.animateScrollToPage(p.coerceIn(0, (layout.pages - 1).coerceAtLeast(0)))
            state.pendingPage = null
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { state.drawer.value to (state.settingsOpen || MusicOverlay.open || state.widgetPicker) }
            .collect { (d, other) -> fx.apply(maxOf(d, if (other) 1f else 0f)) }
    }

    // edge-paging while dragging an item
    LaunchedEffect(state.drag != null) {
        while (state.drag != null) {
            val d = state.drag ?: break
            val cx = d.pos.x + d.size.width / 2f
            val w = state.gridRect.width
            if (w > 0f) {
                if (cx < state.gridRect.left + 30f && pager.currentPage > 0) {
                    pager.animateScrollToPage(pager.currentPage - 1); delay(450)
                } else if (cx > state.gridRect.right - 30f) {
                    if (pager.currentPage == layout.pages - 1) stores.layout.update { it.copy(pages = it.pages + 1) }
                    delay(40)
                    pager.animateScrollToPage(pager.currentPage + 1); delay(450)
                }
            }
            delay(120)
        }
        // drop empty trailing pages
        stores.layout.update { l ->
            val last = (l.items.maxOfOrNull { it.page } ?: 0)
            if (l.pages > last + 1 && l.pages > 1) l.copy(pages = maxOf(last + 1, 1)) else l
        }
    }

    // Opaque overlays (settings, music, assistant) fully hide home: stop composing it so nothing
    // behind them animates or recomposes. A fully open drawer only pauses ambient motion.
    val opaque = state.settingsOpen || MusicOverlay.open || dev.rint.launcher.assistant.AssistantOverlay.open
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(opaque) { if (opaque) { delay(450); hidden = true } else hidden = false }
    val covered = hidden || (state.drawer.value >= 0.999f && state.drawer.targetValue >= 1f)

    Box(Modifier.fillMaxSize()) {
      CompositionLocalProvider(LocalCovered provides covered) {
       if (!hidden) {
        Wallpaper()

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = maxOf(state.drawer.value, if (state.settingsOpen || MusicOverlay.open) 1f else 0f)
                    val s = 1f - 0.06f * p
                    scaleX = s; scaleY = s
                    alpha = 1f - 0.9f * p
                }
                .homeGestures(state)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val notchSpace = if (cfg.notch.enabled) (cfg.notch.height + cfg.notch.offsetY).dp else 0.dp
                Spacer(Modifier.height(cfg.home.topMargin.dp + notchSpace))
                if (cfg.home.searchBar == SearchBarPos.TOP) HomeSearchBar(state)
                HorizontalPager(
                    state = pager,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = cfg.home.sideMargin.dp)
                        .onGloballyPositioned { state.gridRect = it.boundsInRoot() },
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    HomePage(page, layout, state, Modifier.pageTransition(pager, page, cfg.home.transition))
                }
                PageDots(pager, layout.pages)
                if (cfg.home.searchBar == SearchBarPos.BOTTOM) HomeSearchBar(state)
                Dock(state, layout.dock)
                Spacer(Modifier.height(8.dp))
            }
            RinDirector(
                homeVisits = state.homeVisits,
                charging = battery.charging,
                bottomInset = with(LocalDensity.current) { (WindowInsets.navigationBars.getBottom(this) / density).dp } + cfg.dock.height.dp + 18.dp,
            )
        }
       }
      }

        // Only compose the drawer while it's visible (or an app is being dragged out of it),
        // otherwise its invisible layer would swallow every touch on the home screen.
        if (state.drawer.value > 0f || state.drawer.targetValue > 0f || state.drag != null) AppDrawer(state)

        RintNotch(state)

        DragGhost(state)
        HomeMenu(state)
        AppMenu(state)
        WidgetMenu(state)
        WidgetPicker(state)
        RenameDialog(state)

        AnimatedVisibility(state.settingsOpen, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.94f)) {
            SettingsScreen(state)
        }
        AnimatedVisibility(MusicOverlay.open, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
            MusicPlayerScreen(onClose = { MusicOverlay.open = false })
        }
        AnimatedVisibility(dev.rint.launcher.assistant.AssistantOverlay.open, enter = fadeIn() + slideInVertically { it / 4 }, exit = fadeOut() + slideOutVertically { it / 4 }) {
            dev.rint.launcher.assistant.AssistantScreen(state)
        }
        dev.rint.launcher.mascot.GuitarShowOverlay()
        CrashReport(state)
        Toast(state)
        if (state.guideStep >= 0) dev.rint.launcher.intro.GuideOverlay(state)
    }
}

@Composable
private fun HomeSearchBar(state: LauncherState) {
    val look = LocalRint.current
    val cfg = look.cfg
    if (cfg.home.searchStyle == dev.rint.launcher.core.SearchBarStyle.COMPACT) {
        // iOS-style: a small centered "Search" pill, with Rin one tap away beside it
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(34.dp).glass(RoundedCornerShape(50)).pressable(dev.rint.launcher.core.PressEffect.SHRINK) { state.openDrawer(search = true) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Search, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(cfg.home.searchHint, color = Color.White, fontFamily = look.font, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(34.dp).glass(CircleShape).pressable(dev.rint.launcher.core.PressEffect.BOUNCE) { dev.rint.launcher.assistant.AssistantOverlay.show() },
                contentAlignment = Alignment.Center,
            ) { RinSprite(Pose.HEAD, 26.dp) }
        }
        return
    }
    SearchField(
        query = "",
        onQuery = {},
        focus = null,
        onGo = {},
        readOnlyClick = { state.openDrawer(search = true) },
        modifier = Modifier.padding(horizontal = (cfg.home.sideMargin + 4).dp, vertical = 8.dp),
        trailing = {
            RinSprite(Pose.HEAD, 34.dp, Modifier.pressable(dev.rint.launcher.core.PressEffect.BOUNCE) {
                dev.rint.launcher.assistant.AssistantOverlay.show()
            })
        },
    )
}

private fun Modifier.homeGestures(state: LauncherState): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop * 1.5f
    awaitEachGesture {
        val cfg = RintApp.instance.stores.config.value
        val down = awaitFirstDown(requireUnconsumed = false)
        val vt = VelocityTracker()
        vt.addPosition(down.uptimeMillis, down.position)
        var dx = 0f
        var dy = 0f
        var maxPointers = 1
        var dir = 0 // -1 up, 1 down
        var draggingDrawer = false
        while (true) {
            val ev = awaitPointerEvent()
            maxPointers = maxOf(maxPointers, ev.changes.count { it.pressed })
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (!ch.pressed) break
            if (dir == 0 && ch.isConsumed) return@awaitEachGesture
            val d = ch.positionChange()
            dx += d.x; dy += d.y
            vt.addPosition(ch.uptimeMillis, ch.position)
            if (dir == 0 && abs(dy) > slop && abs(dy) > abs(dx) * 1.4f) {
                dir = if (dy < 0) -1 else 1
                draggingDrawer = dir == -1 && maxPointers == 1 && cfg.gestures.swipeUp.action == GestureAction.DRAWER
            }
            if (dir != 0) {
                ch.consume()
                if (draggingDrawer) {
                    val h = size.height.toFloat()
                    state.scope.launch { state.drawer.snapTo((state.drawer.value - d.y / (h * 0.55f)).coerceIn(0f, 1f)) }
                }
            }
        }
        if (dir == 0) return@awaitEachGesture
        val vel = vt.calculateVelocity().y
        when {
            draggingDrawer -> if (vel < -300f || state.drawer.value > 0.3f) state.openDrawer() else state.closeDrawer()
            dir == 1 && maxPointers >= 2 -> state.run(RintApp.instance, cfg.gestures.twoFingerDown)
            dir == 1 -> state.run(RintApp.instance, cfg.gestures.swipeDown)
            dir == -1 -> state.run(RintApp.instance, cfg.gestures.swipeUp)
        }
    }
}

private fun Modifier.pageTransition(pager: PagerState, page: Int, t: PageTransition): Modifier = graphicsLayer {
    val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val a = o.absoluteValue.coerceIn(0f, 1f)
    when (t) {
        PageTransition.SLIDE -> Unit
        PageTransition.CUBE -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (o < 0) 0f else 1f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
        }
        PageTransition.STACK -> if (o > 0) {
            translationX = size.width * o
            val s = 1f - 0.18f * a
            scaleX = s; scaleY = s
            alpha = 1f - a
        }
        PageTransition.ZOOM -> {
            val s = 1f - 0.3f * a
            scaleX = s; scaleY = s
            alpha = 1f - 0.8f * a
        }
        PageTransition.FLIP -> {
            cameraDistance = 16f * density
            translationX = size.width * o
            rotationY = 180f * o.coerceIn(-1f, 1f)
            alpha = if (a > 0.5f) 0f else 1f
        }
        PageTransition.FADE -> {
            translationX = size.width * o
            alpha = 1f - a
        }
        PageTransition.CAROUSEL -> {
            cameraDistance = 12f * density
            val s = 1f - 0.15f * a
            scaleX = s; scaleY = s
            rotationY = 28f * o.coerceIn(-1f, 1f)
        }
        PageTransition.TILT -> {
            transformOrigin = TransformOrigin(0.5f, 1.2f)
            rotationZ = -14f * o.coerceIn(-1f, 1f)
            alpha = 1f - 0.5f * a
        }
    }
}

@Composable
private fun PageDots(pager: PagerState, pages: Int) {
    val look = LocalRint.current
    if (pages <= 1 || look.cfg.home.indicator == PageIndicator.NONE) {
        Spacer(Modifier.height(8.dp)); return
    }
    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        when (look.cfg.home.indicator) {
            PageIndicator.DOTS -> repeat(pages) { i ->
                val active = (1f - ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue).coerceIn(0f, 1f)
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width((6 + 14 * active).dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.35f + 0.65f * active))
                )
            }
            PageIndicator.LINE -> Box(Modifier.width(120.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                val f = (pager.currentPage + pager.currentPageOffsetFraction) / (pages - 1).coerceAtLeast(1)
                Box(Modifier.offset(x = (120 - 120 / pages).dp * f).width((120 / pages).dp).height(3.dp).clip(CircleShape).background(Color.White))
            }
            PageIndicator.NUMBERS -> Text("${pager.currentPage + 1} / $pages", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = Color.White)
            PageIndicator.PAW -> repeat(pages) { i ->
                if (i == pager.currentPage) RinSprite(Pose.HEAD, 18.dp, Modifier.padding(horizontal = 2.dp))
                else Box(Modifier.padding(horizontal = 5.dp).size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
            }
            PageIndicator.NONE -> Unit
        }
    }
}

@Composable
private fun DragGhost(state: LauncherState) {
    val d = state.drag ?: return
    val look = LocalRint.current
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(d.pos.x.roundToInt(), d.pos.y.roundToInt()) }
            .size(with(density) { d.size.width.toDp() }, with(density) { d.size.height.toDp() })
            .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f; alpha = 0.92f; rotationZ = -2f }
            .shadow(18.dp, RoundedCornerShape(look.cfg.look.corner.dp), clip = false),
        contentAlignment = Alignment.Center,
    ) {
        when {
            d.appKey != null -> AppIconView(d.appKey, look.cfg.icons.size.dp)
            d.item?.kind == ItemKind.WIDGET -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(look.cfg.look.corner.dp)).background(look.colors.panel),
                contentAlignment = Alignment.Center,
            ) {
                WidgetRegistry.find(d.item.widget)?.let { androidx.compose.material3.Icon(it.icon, null, tint = look.colors.accent, modifier = Modifier.size(36.dp)) }
            }
            else -> Box(Modifier.fillMaxSize().clip(RoundedCornerShape(look.cfg.look.corner.dp)).background(look.colors.panel))
        }
    }
}

@Composable
private fun Toast(state: LauncherState) {
    val look = LocalRint.current
    val msg = state.toast
    LaunchedEffect(msg) {
        if (msg != null) { delay(2400); if (state.toast == msg) state.toast = null }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(msg != null, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(look.colors.panelStrong).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RinSprite(Pose.HEAD, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(state.toast ?: "", fontFamily = look.font, fontSize = 13.sp, color = look.colors.text)
            }
        }
    }
}

@Composable
fun Wallpaper() {
    val look = LocalRint.current
    val l = look.cfg.look
    Box(Modifier.fillMaxSize()) {
        when (l.wallpaper) {
            WallpaperMode.ART -> {
                val bd = LocalBackdrop.current ?: dev.rint.launcher.ui.rememberBackdrop()
                if (bd != null) androidx.compose.foundation.Image(
                    bd.full, null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            WallpaperMode.SYSTEM -> Unit
            WallpaperMode.SOLID -> Box(Modifier.fillMaxSize().background(l.solidColor.color()))
            WallpaperMode.GRADIENT -> Canvas(Modifier.fillMaxSize()) {
                val rad = Math.toRadians(l.gradientAngle.toDouble())
                val cx = size.width / 2
                val cy = size.height / 2
                val r = maxOf(size.width, size.height) / 2
                val dx = (cos(rad) * r).toFloat()
                val dy = (sin(rad) * r).toFloat()
                drawRect(Brush.linearGradient(listOf(l.gradientA.color(), l.gradientB.color()), Offset(cx - dx, cy - dy), Offset(cx + dx, cy + dy)))
            }
            WallpaperMode.MESH -> {
                // "Aurora": four soft light blobs drifting over a deep gradient. Driven by the
                // ambient clock so it stops moving whenever you can't see it.
                val p = rememberAmbientClock() * (2f * Math.PI.toFloat() / 48f)
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.verticalGradient(listOf(l.gradientA.color(), Color(0xFF050818))))
                    val blobs = listOf(
                        Triple(look.colors.accent, 0f, 0.62f),
                        Triple(l.gradientB.color(), 2.1f, 0.58f),
                        Triple(l.gradientC.color(), 4.2f, 0.45f),
                        Triple(Color(0xFFFF6FB5), 5.3f, 0.22f),
                    )
                    blobs.forEachIndexed { i, (c, ph, a) ->
                        val center = Offset(
                            size.width * (0.5f + 0.38f * cos(p * (1f + i * 0.13f) + ph)),
                            size.height * (0.42f + 0.32f * sin(p * (0.7f + i * 0.09f) + ph)),
                        )
                        val r = size.maxDimension * (0.5f + 0.06f * sin(p * 1.3f + ph))
                        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = a), Color.Transparent), center, r), r, center)
                    }
                    // gentle vignette so icons and text stay readable
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.35f))))
                }
            }
        }
        if (l.wallpaperDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = l.wallpaperDim)))
    }
}
