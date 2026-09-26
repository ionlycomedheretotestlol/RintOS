package dev.rint.launcher.home

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.system.SystemActions
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Panel
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberHaptic
import dev.rint.launcher.widgets.SysWidgets
import dev.rint.launcher.widgets.WidgetCtx
import dev.rint.launcher.widgets.WidgetRegistry
import kotlin.math.roundToInt

private data class MenuAction(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)

@Composable
private fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
    ) { content() }
}

@Composable
private fun PopCard(anchorX: Float, anchorY: Float, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, RintSprings.pop()) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = with(density) { 250.dp.toPx() }
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val x = (anchorX - w / 2).coerceIn(24f, maxW - w - 24f)
        val below = anchorY < maxH * 0.55f
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), if (below) anchorY.roundToInt() else 0) }
                .width(250.dp)
                .then(if (!below) Modifier.offset { IntOffset(0, 0) } else Modifier)
                .graphicsLayer {
                    scaleX = 0.8f + 0.2f * a.value; scaleY = scaleX; alpha = a.value.coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (below) 0f else 1f)
                    if (!below) translationY = anchorY - size.height
                }
                .clickable(remember { MutableInteractionSource() }, null) {}
        ) { content() }
    }
}

@Composable
private fun ActionList(actions: List<MenuAction>, header: (@Composable () -> Unit)? = null) {
    val look = LocalRint.current
    val v = rememberHaptic()
    Panel(strong = true, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            header?.invoke()
            actions.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().pressable(PressEffect.SHRINK) { Haptics.tick(v); a.run() }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(a.icon, null, tint = if (a.danger) look.colors.danger else look.colors.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(a.label, color = if (a.danger) look.colors.danger else look.colors.text, fontFamily = look.font, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun HomeMenu(state: LauncherState) {
    val at = state.homeMenu ?: return
    val ctx = LocalContext.current
    val stores = RintApp.instance.stores
    val locked = LocalRint.current.cfg.home.lockLayout
    fun close() { state.homeMenu = null }
    Scrim(::close) {
        PopCard(at.x + state.gridRect.left, at.y + state.gridRect.top) {
            ActionList(listOf(
                MenuAction(Icons.Rounded.Widgets, "Widgets") { close(); state.widgetPicker = true },
                MenuAction(Icons.Rounded.Wallpaper, "Wallpaper") { close(); SystemActions.openWallpaperPicker(ctx) },
                MenuAction(Icons.Rounded.Palette, "Customize look") { close(); state.settingsSection = "look"; state.settingsOpen = true },
                MenuAction(Icons.Rounded.Tune, "RintOS settings") { close(); state.settingsSection = null; state.settingsOpen = true },
                MenuAction(Icons.Rounded.AddBox, "Add a page") { close(); stores.layout.update { it.copy(pages = it.pages + 1) }; state.pendingPage = stores.layout.value.pages - 1 },
                MenuAction(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (locked) "Unlock layout" else "Lock layout") {
                    close(); stores.config.update { it.copy(home = it.home.copy(lockLayout = !locked)) }
                },
            ))
        }
    }
}

@Composable
fun AppMenu(state: LauncherState) {
    val req = state.appMenu ?: return
    val repo = RintApp.instance.apps
    val stores = RintApp.instance.stores
    val entry = repo.find(req.key) ?: return
    val look = LocalRint.current
    val shortcuts = remember(req.key) { repo.shortcuts(entry) }
    fun close() { state.appMenu = null }
    val layout = stores.layout.value
    val onHome = req.itemId != null
    val inDock = req.key in layout.dock
    val hidden = req.key in look.cfg.hiddenApps
    val actions = buildList {
        shortcuts.forEach { s ->
            add(MenuAction(Icons.AutoMirrored.Rounded.OpenInNew, (s.shortLabel ?: s.longLabel ?: "").toString()) { close(); repo.startShortcut(s) })
        }
        add(MenuAction(Icons.Rounded.DriveFileRenameOutline, "Rename") { close(); state.renaming = req.key })
        if (!onHome) add(MenuAction(Icons.Rounded.Home, "Add to home") { close(); state.addAppToHome(req.key, state.currentPage) })
        if (!inDock) add(MenuAction(Icons.Rounded.Dock, "Add to dock") {
            close(); stores.layout.update { it.copy(dock = (it.dock + req.key).distinct()) }
        })
        if (req.fromDock) add(MenuAction(Icons.Rounded.RemoveCircleOutline, "Remove from dock") {
            close(); stores.layout.update { it.copy(dock = it.dock - req.key) }
        })
        if (onHome) add(MenuAction(Icons.Rounded.RemoveCircleOutline, "Remove from home") {
            close(); stores.layout.update { l -> l.copy(items = l.items.filterNot { it.id == req.itemId }) }
        })
        add(MenuAction(Icons.Rounded.VisibilityOff, if (hidden) "Unhide app" else "Hide from drawer") {
            close(); stores.config.update { it.copy(hiddenApps = if (hidden) it.hiddenApps - req.key else it.hiddenApps + req.key) }
            state.say(if (hidden) "back in the drawer" else "hidden — search still finds it")
        })
        add(MenuAction(Icons.Rounded.Info, "App info") { close(); repo.appInfo(entry) })
        add(MenuAction(Icons.Rounded.Delete, "Uninstall", danger = true) { close(); repo.uninstall(entry) })
    }
    Scrim(::close) {
        PopCard(req.anchor.center.x, req.anchor.bottom + 8f) {
            ActionList(actions, header = {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconView(entry.key, 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(entry.label, color = look.colors.text, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            })
        }
    }
}

@Composable
fun WidgetMenu(state: LauncherState) {
    val item = state.widgetMenu ?: return
    val stores = RintApp.instance.stores
    val ctx = LocalContext.current
    val look = LocalRint.current
    val spec = WidgetRegistry.find(item.widget)
    fun close() { state.widgetMenu = null }
    Scrim(::close) {
        Box(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            Panel(strong = true, shape = RoundedCornerShape(28.dp), modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {}) {
                Column(Modifier.padding(20.dp)) {
                    Text(spec?.name ?: "Widget", fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = look.colors.text)
                    spec?.let {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AspectRatio, null, tint = look.colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SIZE", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = look.colors.subtext)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            it.sizes.forEach { (w, h) ->
                                Pill("${w}×$h", selected = item.w == w && item.h == h) {
                                    val cols = look.cfg.home.columns
                                    val rows = look.cfg.home.rows
                                    val l = stores.layout.value
                                    if (l.fits(item.page, item.x.coerceAtMost(cols - w), item.y.coerceAtMost(rows - h), w, h, cols, rows, skipId = item.id)) {
                                        stores.layout.update { lay -> lay.copy(items = lay.items.map { i -> if (i.id == item.id) i.copy(w = w, h = h, x = i.x.coerceAtMost(cols - w), y = i.y.coerceAtMost(rows - h)) else i }) }
                                        state.widgetMenu = item.copy(w = w, h = h)
                                    } else state.say("not enough room — move things first")
                                }
                            }
                        }
                    }
                    if (item.widget == "clock") {
                        Spacer(Modifier.height(12.dp))
                        Pill("clock styles →") { close(); state.settingsSection = "clock"; state.settingsOpen = true }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(look.colors.danger.copy(alpha = 0.12f))
                            .clickable {
                                close()
                                if (item.sysId >= 0) SysWidgets.remove(ctx, item.sysId)
                                stores.layout.update { l -> l.copy(items = l.items.filterNot { it.id == item.id }) }
                            }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = look.colors.danger)
                        Spacer(Modifier.width(10.dp))
                        Text("Remove widget", color = look.colors.danger, fontWeight = FontWeight.SemiBold, fontFamily = look.font)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(state: LauncherState) {
    val key = state.renaming ?: return
    val look = LocalRint.current
    val stores = RintApp.instance.stores
    val entry = RintApp.instance.apps.find(key) ?: return
    var text by remember(key) { mutableStateOf(entry.label) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun save() {
        stores.config.update { c ->
            val t = text.trim()
            c.copy(renamedApps = if (t.isEmpty() || t == entry.originalLabel) c.renamedApps - key else c.renamedApps + (key to t))
        }
        state.renaming = null
    }
    Scrim({ state.renaming = null }) {
        Box(Modifier.fillMaxSize().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Panel(strong = true, shape = RoundedCornerShape(26.dp), modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {}) {
                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIconView(key, 64.dp)
                    Spacer(Modifier.height(14.dp))
                    BasicTextField(
                        text, { text = it }, singleLine = true,
                        textStyle = TextStyle(color = look.colors.text, fontSize = 20.sp, fontFamily = look.font, fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        cursorBrush = SolidColor(look.colors.accent),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { save() }),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(look.colors.text.copy(alpha = 0.06f)).padding(14.dp).focusRequester(focus),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("reset") { text = entry.originalLabel }
                        Pill("save", selected = true) { save() }
                    }
                }
            }
        }
    }
}

@Composable
fun WidgetPicker(state: LauncherState) {
    if (!state.widgetPicker) return
    val look = LocalRint.current
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var chosen by remember { mutableStateOf<String?>(null) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, RintSprings.sheet()) }
    Scrim({ state.widgetPicker = false }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer { translationY = (1f - a.value) * size.height }
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(look.colors.panelStrong)
                    .border(1.dp, look.colors.stroke, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .navigationBarsPadding()
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(look.colors.subtext.copy(alpha = 0.4f)))
                }
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Widgets", fontFamily = look.font, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = look.colors.text, modifier = Modifier.weight(1f))
                    Pill("RintOS", selected = tab == 0) { tab = 0 }
                    Spacer(Modifier.width(8.dp))
                    Pill("Apps", selected = tab == 1) { tab = 1 }
                }
                if (tab == 0) {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(WidgetRegistry.all, key = { it.type }) { spec ->
                            Panel(Modifier.fillMaxWidth().clickable { chosen = if (chosen == spec.type) null else spec.type }, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(spec.icon, null, tint = look.colors.accent, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(spec.name, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = look.colors.text)
                                            Text(spec.blurb, fontFamily = look.font, fontSize = 12.sp, color = look.colors.subtext)
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    val (w, h) = spec.default
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h * 1.25f).clip(RoundedCornerShape(18.dp))
                                            .background(Color(0xFF101829))
                                    ) {
                                        dev.rint.launcher.ui.RintTheme(look.cfg.copy(look = look.cfg.look.copy(theme = dev.rint.launcher.core.ThemeMode.DARK))) {
                                            spec.content(WidgetCtx("preview-${spec.type}", w, h, preview = true))
                                        }
                                    }
                                    if (chosen == spec.type) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            spec.sizes.forEach { (sw, sh) ->
                                                Pill("add ${sw}×$sh", selected = true) {
                                                    state.widgetPicker = false
                                                    state.addWidget(spec.type, sw, sh, state.currentPage)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val providers = remember { SysWidgets.providers(ctx).groupBy { it.provider.packageName } }
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                        providers.forEach { (pkg, list) ->
                            item(key = pkg) {
                                val label = remember(pkg) { runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
                                Text(label, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = look.colors.text, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(list, key = { it.provider.flattenToString() }) { info ->
                                        val preview = remember(info) {
                                            runCatching { info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0) }.getOrNull()?.toBitmap(240, 160)?.asImageBitmap()
                                        }
                                        Column(
                                            Modifier.width(150.dp).clip(RoundedCornerShape(18.dp)).background(look.colors.text.copy(alpha = 0.05f))
                                                .clickable {
                                                    state.widgetPicker = false
                                                    (ctx as? Activity)?.let { SysWidgets.begin(it, info, state.currentPage, look.cfg.home.columns) }
                                                }.padding(10.dp),
                                        ) {
                                            Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                                                preview?.let { Image(it, null, Modifier.fillMaxSize()) }
                                            }
                                            Spacer(Modifier.height(6.dp))
                                            Text(info.loadLabel(ctx.packageManager), fontSize = 12.sp, color = look.colors.text, fontFamily = look.font, maxLines = 2)
                                            val (cw, ch) = SysWidgets.cellsFor(info, ctx, look.cfg.home.columns)
                                            Text("${cw}×$ch", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = look.colors.subtext)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
