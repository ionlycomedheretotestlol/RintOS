package dev.rint.launcher.system

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.rint.launcher.RintApp
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.home.RintNotch
import dev.rint.launcher.ui.RintTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The notch, floating over every other app. RintOS itself draws its own notch, so this window
 * only exists while another app is in front. The window wraps the pill, so touches next to it
 * go straight through to the app underneath.
 */
object GlobalNotch {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var launcherVisible = true

    /** Called by the launcher when it comes to the front / goes to the back. */
    fun launcherVisible(ctx: Context, visible: Boolean) {
        launcherVisible = visible
        main.post { sync(ctx.applicationContext) }
    }

    /** Re-evaluate after settings or permissions change. */
    fun refresh(ctx: Context) = main.post { sync(ctx.applicationContext) }

    private fun allowed(ctx: Context): Boolean {
        val n = runCatching { RintApp.instance.stores.config.value.notch }.getOrNull() ?: return false
        if (!n.enabled || !n.everywhere) return false
        return RintAccessibility.instance != null || Settings.canDrawOverlays(ctx)
    }

    private fun sync(ctx: Context) {
        runCatching {
            if (!launcherVisible && allowed(ctx)) show(ctx) else hide(ctx)
        }.onFailure { hide(ctx) }
    }

    private fun show(ctx: Context) {
        if (view != null) return
        val host: Context = RintAccessibility.instance ?: ctx
        val type = if (RintAccessibility.instance != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val wm = host.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val o = OverlayOwner().also { it.start() }
        val state = LauncherState(CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
        val v = ComposeView(host).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                val cfg by RintApp.instance.stores.config.state.collectAsState()
                RintTheme(cfg) { RintNotch(remember { state }, fullWidth = false, overlay = true) }
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(v, lp)
        view = v; owner = o
    }

    private fun hide(ctx: Context) {
        val v = view ?: return
        view = null
        runCatching {
            val host: Context = RintAccessibility.instance ?: ctx
            (host.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v)
        }
        runCatching { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(v) }
        owner?.stop(); owner = null
    }
}

/** Minimal lifecycle so Compose can run inside a plain overlay window. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
