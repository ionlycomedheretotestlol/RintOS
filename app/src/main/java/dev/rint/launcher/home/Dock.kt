package dev.rint.launcher.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.DockStyle
import dev.rint.launcher.ui.AppTile
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.ItemGestureCallbacks
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.itemGestures
import dev.rint.launcher.ui.glass
import kotlin.math.abs

@Composable
fun Dock(state: LauncherState, dockKeys: List<String>, modifier: Modifier = Modifier) {
    val look = LocalRint.current
    val d = look.cfg.dock
    if (!d.enabled || d.count <= 0) return
    val v = LocalView.current
    val ctx = LocalContext.current
    val apps by RintApp.instance.apps.apps.collectAsState()
    val entries = dockKeys.mapNotNull { k -> apps.firstOrNull { it.key == k } }.take(d.count)
    var touchX by remember { mutableFloatStateOf(-1f) }
    var width by remember { mutableFloatStateOf(1f) }
    val shape = RoundedCornerShape(d.corner.dp)
    val bg = when (d.style) {
        DockStyle.GLASS -> Modifier.glass(shape, tint = d.opacity / 0.55f)
        DockStyle.SOLID -> Modifier.clip(shape).background(look.colors.panelStrong.copy(alpha = (d.opacity + 0.3f).coerceAtMost(1f)))
        DockStyle.FLOATING -> Modifier.clip(shape).background(Color.Black.copy(alpha = d.opacity * 0.6f))
        DockStyle.LINE -> Modifier.border(0.dp, Color.Transparent)
        DockStyle.NONE -> Modifier
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = d.margin.dp)
            .height(d.height.dp)
            .onGloballyPositioned { state.dockRect = it.boundsInRoot(); width = it.size.width.toFloat() }
            .then(bg)
            .pointerInput(d.magnify, look.cfg.gestures.dockSwipeUp) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touchX = down.position.x
                    var dy = 0f
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        dy += ch.position.y - ch.previousPosition.y
                        touchX = ch.position.x
                    }
                    touchX = -1f
                    if (dy < -80f) state.run(ctx, look.cfg.gestures.dockSwipeUp)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (d.style == DockStyle.LINE) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.8f).height(1.dp).background(look.colors.stroke.copy(alpha = 0.4f)))
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { i, e ->
                val slot = width / entries.size.coerceAtLeast(1)
                val cx = slot * (i + 0.5f)
                val target = if (d.magnify && touchX >= 0f) {
                    val dist = abs(touchX - cx) / (slot * 1.4f)
                    1f + 0.42f * (1f - dist).coerceIn(0f, 1f)
                } else 1f
                val scale by animateFloatAsState(target, dev.rint.launcher.ui.RintSprings.pop(), label = "mag")
                var bounds by remember { mutableStateOf(Rect.Zero) }
                Box(
                    Modifier
                        .weight(1f)
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .graphicsLayer {
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .itemGestures(e.key, consumeTap = true) {
                            object : ItemGestureCallbacks {
                                override fun onTap() = launchApp(v, e, bounds)
                                override fun onLongPress(at: Offset) {
                                    Haptics.heavy(v)
                                    state.appMenu = AppMenuReq(e.key, null, bounds, fromDock = true)
                                }
                                override fun onDragStart(at: Offset) {
                                    state.appMenu = null
                                    state.drag = DragState(null, e.key, true, bounds.topLeft, IntSize(bounds.width.toInt(), bounds.height.toInt()))
                                }
                                override fun onDrag(delta: Offset) {
                                    state.drag = state.drag?.let { it.copy(pos = it.pos + delta) }
                                }
                                override fun onDragEnd() = state.finishDrag()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppTile(e, iconSize = (look.cfg.icons.size * d.iconScale).dp, showLabel = d.labels)
                }
            }
            if (entries.isEmpty()) Box(Modifier.width(1.dp))
        }
    }
}
