package dev.rint.launcher.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import dev.rint.launcher.system.RintAccessibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

data class UiEl(val id: Int, val label: String, val kind: String, val x: Int, val y: Int, val node: AccessibilityNodeInfo)

class Observation(val jpegB64: String?, val width: Int, val height: Int, val elements: List<UiEl>, val app: String?) {
    /** Compact, model-friendly element list in screenshot coordinates. */
    fun describe(max: Int = 60): String = buildString {
        append("screen ${width}x$height")
        app?.let { append(", foreground app: $it") }
        append('\n')
        elements.take(max).forEach { append("[${it.id}] ${it.kind} \"${it.label}\" @(${it.x},${it.y})\n") }
        if (elements.size > max) append("…${elements.size - max} more elements\n")
        if (elements.isEmpty()) append("(no readable elements — rely on the screenshot)\n")
    }
}

/**
 * Everything Rin can physically do on the phone, built on the accessibility service.
 * Coordinates exchanged with the model are in screenshot space (720px wide).
 */
object PhoneControl {
    private const val SHOT_W = 720
    private var scale = 1f
    private var lastElements: List<UiEl> = emptyList()

    private val svc: RintAccessibility? get() = RintAccessibility.instance
    val available: Boolean get() = svc != null

    private fun screenSize(s: AccessibilityService): Pair<Int, Int> {
        val wm = s.getSystemService(android.view.WindowManager::class.java)
        val b = wm.currentWindowMetrics.bounds
        return b.width() to b.height()
    }

    suspend fun observe(withImage: Boolean): Observation {
        val s = svc ?: return Observation(null, 0, 0, emptyList(), null)
        val (w, h) = screenSize(s)
        scale = w.toFloat() / SHOT_W
        val img = if (withImage && Build.VERSION.SDK_INT >= 30) screenshot(s) else null
        val els = withContext(Dispatchers.Default) { elements(s) }
        lastElements = els
        val app = runCatching { s.rootInActiveWindow?.packageName?.toString() }.getOrNull()
        return Observation(img, SHOT_W, (h / scale).toInt(), els, app)
    }

    private suspend fun screenshot(s: RintAccessibility): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        s.setOverlayVisible(false)
        delay(60)
        val bmp: Bitmap? = suspendCancellableCoroutine { cont ->
            s.takeScreenshot(Display.DEFAULT_DISPLAY, s.mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                    val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    r.hardwareBuffer.close()
                    if (cont.isActive) cont.resume(soft)
                }
                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }
        s.setOverlayVisible(true)
        bmp ?: return null
        return withContext(Dispatchers.Default) {
            val scaled = Bitmap.createScaledBitmap(bmp, SHOT_W, (bmp.height * SHOT_W.toFloat() / bmp.width).toInt(), true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 62, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun elements(s: AccessibilityService): List<UiEl> {
        val out = ArrayList<UiEl>()
        val roots = runCatching { s.windows.mapNotNull { it.root } }.getOrNull().orEmpty()
            .ifEmpty { listOfNotNull(s.rootInActiveWindow) }
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 40 || out.size > 150) return
            if (!n.isVisibleToUser) return
            val text = (n.text ?: n.contentDescription ?: n.hintText)?.toString()?.trim()?.replace('\n', ' ')?.take(60)
            val actionable = n.isClickable || n.isEditable || n.isCheckable || n.isScrollable || n.isLongClickable
            if (!text.isNullOrEmpty() || (actionable && n.isEditable)) {
                n.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) {
                    val kind = when {
                        n.isEditable -> "input"
                        n.isCheckable -> if (n.isChecked) "switch(on)" else "switch(off)"
                        n.isClickable -> "button"
                        n.isScrollable -> "list"
                        else -> "text"
                    }
                    out += UiEl(out.size + 1, text ?: "", kind, (r.centerX() / scale).toInt(), (r.centerY() / scale).toInt(), n)
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        roots.forEach { walk(it, 0) }
        return out
    }

    private suspend fun gesture(path: Path, duration: Long): Boolean {
        val s = svc ?: return false
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        return suspendCancellableCoroutine { cont ->
            val ok = s.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
    }

    suspend fun tap(x: Int, y: Int, long: Boolean = false): Boolean {
        val p = Path().apply { moveTo(x * scale, y * scale) }
        return gesture(p, if (long) 700 else 60)
    }

    suspend fun tapElement(id: Int, long: Boolean = false): Boolean {
        val el = lastElements.firstOrNull { it.id == id } ?: return false
        val action = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        var n: AccessibilityNodeInfo? = el.node
        repeat(4) {
            if (n?.performAction(action) == true) return true
            n = n?.parent
        }
        return tap(el.x, el.y, long)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long = 380): Boolean {
        val p = Path().apply { moveTo(x1 * scale, y1 * scale); lineTo(x2 * scale, y2 * scale) }
        return gesture(p, ms)
    }

    suspend fun scroll(direction: String): Boolean {
        val s = svc ?: return false
        val (w, h) = screenSize(s)
        val cx = (w / scale / 2).toInt()
        val sh = (h / scale).toInt()
        return when (direction) {
            "down" -> swipe(cx, (sh * 0.72f).toInt(), cx, (sh * 0.28f).toInt())
            "up" -> swipe(cx, (sh * 0.28f).toInt(), cx, (sh * 0.72f).toInt())
            "left" -> swipe((SHOT_W * 0.85f).toInt(), sh / 2, (SHOT_W * 0.15f).toInt(), sh / 2)
            else -> swipe((SHOT_W * 0.15f).toInt(), sh / 2, (SHOT_W * 0.85f).toInt(), sh / 2)
        }
    }

    fun type(text: String, submit: Boolean): Boolean {
        val s = svc ?: return false
        val target = s.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: lastElements.firstOrNull { it.kind == "input" }?.node
            ?: return false
        val existing = if (target.isShowingHintText) "" else target.text?.toString().orEmpty()
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + text) }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (submit && Build.VERSION.SDK_INT >= 30) {
            target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
        return ok
    }

    fun key(k: String): Boolean {
        val s = svc ?: return false
        val a = when (k) {
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return s.performGlobalAction(a)
    }
}
