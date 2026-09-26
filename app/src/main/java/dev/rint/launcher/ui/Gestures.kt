package dev.rint.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull

interface ItemGestureCallbacks {
    fun onTap() {}
    fun onLongPress(at: Offset) {}
    fun onDragStart(at: Offset) {}
    fun onDrag(delta: Offset) {}
    fun onDragEnd() {}
}

/**
 * Tap, long-press and drag-after-long-press on one element. Runs in the Initial pass so it can
 * steal the gesture from interactive children (widgets) once a long-press is recognised.
 * Plain taps pass through to children when [consumeTap] is false.
 */
@Composable
fun Modifier.itemGestures(
    key: Any?,
    consumeTap: Boolean,
    enabled: Boolean = true,
    cb: () -> ItemGestureCallbacks,
): Modifier {
    val latest = rememberUpdatedState(cb)
    if (!enabled) return this
    return pointerInput(key, consumeTap) {
    val slop = viewConfiguration.touchSlop
    val dragStart = 10.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var released = false
        var cancelled = false
        val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) { released = true; break }
                total += ch.positionChange()
                if (total.getDistance() > slop) { cancelled = true; break }
                if (ev.changes.any { it.isConsumed && it.id == down.id }) { cancelled = true; break }
            }
        } == null
        val callbacks = latest.value()
        if (!timedOut) {
            if (released && !cancelled && consumeTap) callbacks.onTap()
            return@awaitEachGesture
        }
        // long press recognised: own the rest of the gesture
        callbacks.onLongPress(down.position)
        var dragging = false
        var moved = Offset.Zero
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            ev.changes.forEach { it.consume() }
            if (!ch.pressed) break
            val d = ch.positionChange()
            moved += d
            if (!dragging && moved.getDistance() > dragStart) {
                dragging = true
                callbacks.onDragStart(ch.position)
            }
            if (dragging) callbacks.onDrag(d)
        }
        if (dragging) callbacks.onDragEnd()
    }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
