package dev.rint.launcher.home

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.rint.launcher.RintApp
import dev.rint.launcher.apps.AppEntry
import dev.rint.launcher.core.DrawerSort
import dev.rint.launcher.core.DrawerStyle
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.core.SearchBarStyle
import dev.rint.launcher.music.MusicOverlay
import dev.rint.launcher.search.Hit
import dev.rint.launcher.search.Searcher
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.AppTile
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.ItemGestureCallbacks
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.itemGestures
import dev.rint.launcher.ui.pressable
import kotlinx.coroutines.launch

@Composable
fun AppDrawer(state: LauncherState, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    val cfg = look.cfg
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by RintApp.instance.apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val p = state.drawer.value
    val open = state.drawer.targetValue > 0.5f

    LaunchedEffect(open, state.searchFocus) {
        if (open && (state.searchFocus || cfg.drawer.autoKeyboard)) runCatching { focus.requestFocus(); keyboard?.show() }
        if (!open) { query = ""; focusManager.clearFocus(); keyboard?.hide() }
    }

    val visible = remember(all, cfg.hiddenApps, cfg.drawer.sort) {
        val base = all.filter { it.key !in cfg.hiddenApps }
        when (cfg.drawer.sort) {
            DrawerSort.ALPHA -> base
            DrawerSort.INSTALL_DATE -> base.sortedByDescending { it.installTime }
            DrawerSort.MOST_USED -> base.sortedByDescending { RintApp.instance.apps.launchCounts[it.key] ?: 0 }
            DrawerSort.COLOR -> base.sortedBy { e ->
                val c = RintApp.instance.apps.cachedIcon(e.key)?.dominant ?: 0
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(c, hsv)
                if (hsv[1] < 0.18f) 400f + hsv[2] else hsv[0]
            }
        }
    }
    val hits = remember(query, all, cfg.search) { Searcher.run(ctx, query, all, cfg.hiddenApps, cfg.search) }

    val closeConnection = remember(state) {
        object : NestedScrollConnection {
            var pulling = false
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pulling && source == NestedScrollSource.UserInput) {
                    val h = state.gridRect.height.coerceAtLeast(800f) * 1.3f
                    scope.launch { state.drawer.snapTo((state.drawer.value - available.y / h).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    pulling = true
                    val h = state.gridRect.height.coerceAtLeast(800f) * 1.3f
                    scope.launch { state.drawer.snapTo((state.drawer.value - available.y / h).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!pulling) return Velocity.Zero
                pulling = false
                if (available.y > 600f || state.drawer.value < 0.7f) state.closeDrawer() else state.openDrawer()
                return available
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - p) * size.height * 0.35f
                alpha = p.coerceIn(0f, 1f)
                val s = lerp(0.94f, 1f, p)
                scaleX = s; scaleY = s
            }
            .background(look.colors.bg.copy(alpha = cfg.drawer.opacity * p))
            .nestedScroll(closeConnection)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(
                Modifier.fillMaxWidth().pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { if (state.drawer.value < 0.75f) state.closeDrawer() else state.openDrawer() },
                    ) { _, dy ->
                        scope.launch { state.drawer.snapTo((state.drawer.value - dy / (size.height * 8f)).coerceIn(0f, 1f)) }
                    }
                }
            ) {
                SearchField(
                    query = query,
                    onQuery = { query = it },
                    focus = focus,
                    onGo = {
                        val first = hits.firstOrNull()
                        when {
                            first is Hit.App && cfg.search.launchOnEnter -> { RintApp.instance.apps.launch(first.entry); state.closeDrawer() }
                            query.isNotBlank() -> Searcher.openWeb(ctx, cfg.search, query)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (query.isBlank()) {
                if (cfg.drawer.showRecents) RecentsRow(state, all)
                AppList(state, visible)
            } else {
                Results(state, hits, query)
            }
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    focus: FocusRequester?,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
    readOnlyClick: (() -> Unit)? = null,
) {
    val look = LocalRint.current
    val style = look.cfg.home.searchStyle
    val shape = when (style) {
        SearchBarStyle.PILL -> RoundedCornerShape(50)
        SearchBarStyle.TERMINAL -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape((look.cfg.look.corner * 0.8f).dp)
    }
    val bg = when (style) {
        SearchBarStyle.UNDERLINE -> Modifier
        SearchBarStyle.TERMINAL -> Modifier.clip(shape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).border(1.dp, look.colors.accent.copy(alpha = 0.6f), shape)
        else -> Modifier.clip(shape).background(look.colors.panel).border(1.dp, look.colors.stroke, shape)
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(bg)
            .then(if (readOnlyClick != null) Modifier.clickable { readOnlyClick() } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == SearchBarStyle.TERMINAL) Text(">", fontFamily = RintFonts.Terminal, fontSize = 24.sp, color = look.colors.accent)
        else Icon(Icons.Rounded.Search, null, tint = look.colors.subtext, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            val font = if (style == SearchBarStyle.TERMINAL) RintFonts.Terminal else look.font
            val size = if (style == SearchBarStyle.TERMINAL) 22.sp else 16.sp
            if (query.isEmpty()) Text(look.cfg.home.searchHint, color = look.colors.subtext, fontSize = size, fontFamily = font)
            if (readOnlyClick == null) BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = look.colors.text, fontSize = size, fontFamily = font),
                cursorBrush = SolidColor(look.colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
        if (query.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = look.colors.subtext, modifier = Modifier.size(20.dp).clickable { onQuery("") })
    }
    if (style == SearchBarStyle.UNDERLINE) Box(modifier.fillMaxWidth().height(2.dp).background(look.colors.accent))
}

@Composable
private fun RecentsRow(state: LauncherState, all: List<AppEntry>) {
    val counts = RintApp.instance.apps.launchCounts
    val top = remember(all, state.drawer.targetValue) {
        all.filter { (counts[it.key] ?: 0) > 0 }.sortedByDescending { counts[it.key] }.take(RECENTS_COUNT)
    }
    if (top.isEmpty()) return
    val look = LocalRint.current
    Text("RECENT", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext, modifier = Modifier.padding(start = 20.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        top.forEach { DrawerApp(state, it, Modifier.weight(1f)) }
    }
    Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth().height(1.dp).background(look.colors.stroke))
}

private const val RECENTS_COUNT = 5

@Composable
private fun DrawerApp(state: LauncherState, e: AppEntry, modifier: Modifier = Modifier, list: Boolean = false) {
    val look = LocalRint.current
    val v = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val mod = modifier
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .itemGestures(e.key, consumeTap = true) {
            object : ItemGestureCallbacks {
                override fun onTap() {
                    launchApp(v, e, bounds)
                    state.closeDrawer()
                }
                override fun onLongPress(at: Offset) {
                    Haptics.heavy(v)
                    state.appMenu = AppMenuReq(e.key, null, bounds)
                }
                override fun onDragStart(at: Offset) {
                    state.appMenu = null
                    state.closeDrawer()
                    state.drag = DragState(null, e.key, false, bounds.topLeft, IntSize(bounds.width.toInt(), bounds.height.toInt()))
                }
                override fun onDrag(delta: Offset) {
                    state.drag = state.drag?.let { it.copy(pos = it.pos + delta) }
                }
                override fun onDragEnd() = state.finishDrag()
            }
        }
        .pressable(look.cfg.icons.press)
    if (list) {
        Row(mod.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconView(e.key, 40.dp)
            Spacer(Modifier.width(16.dp))
            Text(e.label, color = look.colors.text, fontFamily = look.font, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Box(mod.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            AppTile(e, labelOnDark = look.colors.dark)
        }
    }
}

@Composable
private fun AppList(state: LauncherState, apps: List<AppEntry>) {
    val look = LocalRint.current
    val d = look.cfg.drawer
    when (d.style) {
        DrawerStyle.GRID -> {
            val grid = rememberLazyGridState()
            val grouped = d.sort == DrawerSort.ALPHA && d.headers
            LazyVerticalGrid(GridCells.Fixed(d.columns), state = grid, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 40.dp)) {
                if (grouped) {
                    apps.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }.forEach { (letter, group) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h$letter") {
                            Text("$letter", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = look.colors.accent, modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 2.dp))
                        }
                        items(group, key = { it.key }) { DrawerApp(state, it) }
                    }
                } else items(apps, key = { it.key }) { DrawerApp(state, it) }
            }
        }
        DrawerStyle.LIST -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            items(apps, key = { it.key }) { DrawerApp(state, it, list = true) }
        }
        DrawerStyle.ALPHABET -> AlphabetList(state, apps)
        DrawerStyle.PAGED -> {
            val per = d.columns * 5
            val pages = (apps.size + per - 1) / per
            val pager = rememberPagerState { pages.coerceAtLeast(1) }
            HorizontalPager(pager, Modifier.fillMaxSize()) { pg ->
                LazyVerticalGrid(GridCells.Fixed(d.columns), userScrollEnabled = false, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    items(apps.drop(pg * per).take(per), key = { it.key }) { DrawerApp(state, it) }
                }
            }
        }
    }
}

@Composable
private fun AlphabetList(state: LauncherState, apps: List<AppEntry>) {
    val look = LocalRint.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val v = LocalView.current
    val letters = remember(apps) { apps.map { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }.distinct() }
    var active by remember { mutableStateOf<Char?>(null) }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(bottom = 40.dp)) {
            items(apps, key = { it.key }) { DrawerApp(state, it, list = true) }
        }
        Column(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(letters) {
                    detectVerticalDragGestures(onDragEnd = { active = null }) { ch, _ ->
                        val i = (ch.position.y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        val l = letters[i]
                        if (l != active) {
                            active = l
                            Haptics.tick(v)
                            val idx = apps.indexOfFirst { (it.label.firstOrNull()?.uppercaseChar() ?: '#') == l }
                            if (idx >= 0) scope.launch { list.scrollToItem(idx) }
                        }
                    }
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                val s by animateFloatAsState(if (l == active) 1.9f else 1f, RintSprings.pop(), label = "l")
                Text("$l", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = if (l == active) look.colors.accent else look.colors.subtext,
                    modifier = Modifier.graphicsLayer { scaleX = s; scaleY = s; translationX = -(s - 1f) * 30f })
            }
        }
    }
}

@Composable
private fun Results(state: LauncherState, hits: List<Hit>, query: String) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val apps = hits.filterIsInstance<Hit.App>()
    val others = hits.filterNot { it is Hit.App }
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        others.filter { it is Hit.Math || it is Hit.Convert }.forEach { h ->
            item {
                val (icon, text) = when (h) {
                    is Hit.Math -> Icons.Rounded.Calculate to "= ${h.result}"
                    is Hit.Convert -> Icons.Rounded.Straighten to h.text
                    else -> Icons.Rounded.Search to ""
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(look.colors.accent).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = look.colors.onAccent)
                    Spacer(Modifier.width(14.dp))
                    Text(text, fontFamily = RintFonts.Terminal, fontSize = 30.sp, color = look.colors.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (apps.isNotEmpty()) item {
            val cols = look.cfg.drawer.columns
            Column(Modifier.padding(horizontal = 8.dp)) {
                apps.take(cols * 2).chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { DrawerApp(state, it.entry, Modifier.weight(1f)) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        items(others.filterNot { it is Hit.Math || it is Hit.Convert }) { h ->
            val (icon, title, sub) = when (h) {
                is Hit.Contact -> Triple(Icons.Rounded.Person, h.name, h.number ?: "contact")
                is Hit.Setting -> Triple(Icons.Rounded.Tune, h.title, "jump to settings")
                is Hit.Music -> Triple(Icons.Rounded.MusicNote, "play “$query”", "with live lyrics in Rint Music")
                is Hit.Web -> Triple(Icons.Rounded.Public, "search the web for “$query”", look.cfg.search.engine.name.lowercase())
                else -> Triple(Icons.Rounded.Search, "", "")
            }
            ResultRow(icon, title, sub) {
                when (h) {
                    is Hit.Contact -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, h.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    is Hit.Setting -> { state.settingsSection = h.section; state.settingsOpen = true }
                    is Hit.Music -> MusicOverlay.show(search = true)
                    is Hit.Web -> Searcher.openWeb(ctx, look.cfg.search, query)
                    else -> Unit
                }
                if (h !is Hit.Setting && h !is Hit.Music) state.closeDrawer()
            }
        }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val look = LocalRint.current
    Row(
        Modifier.fillMaxWidth().pressable(PressEffect.SHRINK, onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(look.colors.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = look.colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = look.colors.text, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = look.colors.subtext, fontFamily = look.font, fontSize = 12.sp, maxLines = 1)
        }
    }
}
