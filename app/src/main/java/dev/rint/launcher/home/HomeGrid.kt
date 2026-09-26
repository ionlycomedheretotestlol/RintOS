package dev.rint.launcher.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.HomeLayout
import dev.rint.launcher.core.ItemKind
import dev.rint.launcher.ui.AppTile
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.ItemGestureCallbacks
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Panel
import dev.rint.launcher.ui.itemGestures
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.widgets.SystemWidgetView
import dev.rint.launcher.widgets.WidgetCtx
import dev.rint.launcher.widgets.WidgetRegistry

@Composable
fun HomePage(page: Int, layout: HomeLayout, state: LauncherState, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    val cfg = look.cfg
    val ctx = LocalContext.current
    val v = LocalView.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(cfg.gestures.doubleTap, layout, page, cfg.home.columns, cfg.home.rows) {
                fun onItem(o: Offset): Boolean {
                    val cx = (o.x / (size.width / cfg.home.columns)).toInt()
                    val cy = (o.y / (size.height / cfg.home.rows)).toInt()
                    return cx in 0..31 && cy in 0..31 && layout.occupied(page)[cx][cy]
                }
                detectTapGestures(
                    onDoubleTap = { if (!onItem(it)) { Haptics.tap(v); state.run(ctx, cfg.gestures.doubleTap) } },
                    onLongPress = { if (!onItem(it)) { Haptics.heavy(v); state.homeMenu = it } },
                )
            }
    ) {
        val cellW = maxWidth / cfg.home.columns
        val cellH = maxHeight / cfg.home.rows
        layout.items.filter { it.page == page }.forEach { item ->
            val dragging = state.drag?.item?.id == item.id
            androidx.compose.runtime.key(item.id) {
                Box(
                    Modifier
                        .offset(cellW * item.x, cellH * item.y)
                        .size(cellW * item.w, cellH * item.h)
                        .graphicsLayer { alpha = if (dragging) 0f else 1f }
                ) {
                    when (item.kind) {
                        ItemKind.APP -> HomeApp(item, state, cellW, cellH)
                        ItemKind.WIDGET, ItemKind.SYSTEM_WIDGET -> HomeWidget(item, state, cellW, cellH)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeApp(item: HomeItem, state: LauncherState, cellW: Dp, cellH: Dp) {
    val look = LocalRint.current
    val v = LocalView.current
    val apps by RintApp.instance.apps.apps.collectAsState()
    val entry = apps.firstOrNull { it.key == item.app } ?: return
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .itemGestures(item.id, consumeTap = true, enabled = true) {
                object : ItemGestureCallbacks {
                    override fun onTap() {
                        launchApp(v, entry, bounds)
                    }
                    override fun onLongPress(at: Offset) {
                        Haptics.heavy(v)
                        state.appMenu = AppMenuReq(entry.key, item.id, bounds)
                    }
                    override fun onDragStart(at: Offset) {
                        if (look.cfg.home.lockLayout) return state.say("layout is locked (settings → home)")
                        state.appMenu = null
                        state.drag = DragState(item, entry.key, false, bounds.topLeft, IntSize(bounds.width.toInt(), bounds.height.toInt()))
                    }
                    override fun onDrag(delta: Offset) {
                        state.drag = state.drag?.let { it.copy(pos = it.pos + delta) }
                    }
                    override fun onDragEnd() = state.finishDrag()
                }
            }
            .pressable(look.cfg.icons.press),
        contentAlignment = Alignment.Center,
    ) {
        AppTile(entry)
    }
}

@Composable
private fun HomeWidget(item: HomeItem, state: LauncherState, cellW: Dp, cellH: Dp) {
    val look = LocalRint.current
    val v = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val spec = WidgetRegistry.find(item.widget)
    val framed = item.kind == ItemKind.SYSTEM_WIDGET || spec?.type != "clock" || item.w <= 2
    Box(
        Modifier
            .fillMaxSize()
            .padding(5.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .itemGestures(item.id, consumeTap = false) {
                object : ItemGestureCallbacks {
                    override fun onLongPress(at: Offset) {
                        Haptics.heavy(v)
                        state.widgetMenu = item
                    }
                    override fun onDragStart(at: Offset) {
                        if (look.cfg.home.lockLayout) return
                        state.widgetMenu = null
                        state.drag = DragState(item, null, false, bounds.topLeft, IntSize(bounds.width.toInt(), bounds.height.toInt()))
                    }
                    override fun onDrag(delta: Offset) {
                        state.drag = state.drag?.let { it.copy(pos = it.pos + delta) }
                    }
                    override fun onDragEnd() = state.finishDrag()
                }
            }
    ) {
        val content: @Composable () -> Unit = {
            if (item.kind == ItemKind.SYSTEM_WIDGET) SystemWidgetView(item, cellW * item.w, cellH * item.h)
            else spec?.content?.invoke(WidgetCtx(item.id, item.w, item.h))
        }
        if (framed) Panel(Modifier.fillMaxSize(), shape = RoundedCornerShape((look.cfg.look.corner).dp)) { content() }
        else content()
    }
}

/** Resolves the drop target for the current drag, updating layout/dock. */
fun LauncherState.finishDrag() {
    val d = drag ?: return
    drag = null
    val stores = RintApp.instance.stores
    val cfg = stores.config.value
    val cols = cfg.home.columns
    val rows = cfg.home.rows
    val center = d.pos + Offset(d.size.width / 2f, d.size.height / 2f)
    val page = currentPage

    // dropped on the dock
    if (d.appKey != null && dockRect.contains(center) && cfg.dock.enabled) {
        stores.layout.update { l ->
            val without = l.dock.filterNot { it == d.appKey }
            val slot = (((center.x - dockRect.left) / dockRect.width) * (without.size + 1)).toInt().coerceIn(0, without.size)
            val dock = (without.take(slot) + d.appKey + without.drop(slot)).take(cfg.dock.count.coerceAtLeast(1) + 2)
            l.copy(dock = dock, items = if (d.item != null) l.items.filterNot { it.id == d.item.id } else l.items)
        }
        return
    }
    if (gridRect == Rect.Zero) return
    val cw = gridRect.width / cols
    val ch = gridRect.height / rows
    val tx = ((d.pos.x - gridRect.left + cw / 2) / cw).toInt().coerceIn(0, cols - d.w)
    val ty = ((d.pos.y - gridRect.top + ch / 2) / ch).toInt().coerceIn(0, rows - d.h)
    stores.layout.update { l ->
        val base = if (d.fromDock) l.copy(dock = l.dock.filterNot { it == d.appKey }) else l
        when {
            d.item != null && base.fits(page, tx, ty, d.w, d.h, cols, rows, skipId = d.item.id) ->
                base.copy(items = base.items.map { if (it.id == d.item.id) it.copy(page = page, x = tx, y = ty) else it })
            d.item != null -> {
                // swap with a same-size neighbour when the cell is taken
                val other = base.items.firstOrNull { it.page == page && it.x == tx && it.y == ty && it.w == d.w && it.h == d.h && it.id != d.item.id }
                if (other != null) base.copy(items = base.items.map {
                    when (it.id) {
                        d.item.id -> it.copy(page = page, x = tx, y = ty)
                        other.id -> it.copy(page = d.item.page, x = d.item.x, y = d.item.y)
                        else -> it
                    }
                }) else base
            }
            d.appKey != null && base.fits(page, tx, ty, 1, 1, cols, rows) ->
                base.copy(items = base.items + HomeItem(page = page, x = tx, y = ty, kind = ItemKind.APP, app = d.appKey))
            d.appKey != null -> base.firstFree(1, 1, cols, rows, page)?.let { (p, x, y) ->
                base.copy(pages = maxOf(base.pages, p + 1), items = base.items + HomeItem(page = p, x = x, y = y, kind = ItemKind.APP, app = d.appKey))
            } ?: base
            else -> base
        }
    }
}
