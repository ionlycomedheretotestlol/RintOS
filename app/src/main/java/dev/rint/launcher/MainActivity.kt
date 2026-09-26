package dev.rint.launcher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.rint.launcher.home.Launcher
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.home.LocalWindowFx
import dev.rint.launcher.home.WindowFx
import dev.rint.launcher.intro.IntroFlow
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.RintSprings
import dev.rint.launcher.ui.RintTheme
import dev.rint.launcher.widgets.SysWidgets
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var state: LauncherState? = null
    private var resumed = false
    private var lastBlur = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = RintApp.instance
        val fx = WindowFx { applyWindowFx(it) }
        setContent {
            val cfg by app.stores.config.state.collectAsState()
            val scope = rememberCoroutineScope()
            val st = remember { LauncherState(scope).also { state = it } }

            LaunchedEffect(cfg.motion) { RintSprings.apply(cfg.motion) }
            LaunchedEffect(cfg.gestures.haptics) { Haptics.enabled = cfg.gestures.haptics }
            LaunchedEffect(cfg.renamedApps) { app.apps.setRenames(cfg.renamedApps) }
            LaunchedEffect(cfg.icons.iconPack) { app.apps.setIconPack(cfg.icons.iconPack) }
            LaunchedEffect(cfg.look.showStatusBar, cfg.look.showNavBar, cfg.onboarded) { applyBars(cfg.look.showStatusBar || !cfg.onboarded, cfg.look.showNavBar || !cfg.onboarded) }

            BackHandler { st.dismissTop() }

            RintTheme(cfg) {
                CompositionLocalProvider(LocalWindowFx provides fx) {
                    AnimatedContent(cfg.onboarded, label = "root", transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(300)) }) { onboarded ->
                        if (onboarded) Launcher(st)
                        else IntroFlow(onFinished = {
                            app.stores.config.update { it.copy(onboarded = true) }
                            if (!app.stores.config.value.guideSeen) st.guideStep = 0
                        })
                    }
                }
            }
        }
    }

    private fun applyBars(status: Boolean, nav: Boolean) {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (status) c.show(WindowInsetsCompat.Type.statusBars()) else c.hide(WindowInsetsCompat.Type.statusBars())
        if (nav) c.show(WindowInsetsCompat.Type.navigationBars()) else c.hide(WindowInsetsCompat.Type.navigationBars())
    }

    /** Blurs the system wallpaper behind overlays; [p] is 0..1. */
    private fun applyWindowFx(p: Float) {
        val cfg = RintApp.instance.stores.config.value
        val radius = (p * cfg.look.blur * resources.displayMetrics.density).roundToInt()
        val step = radius / 4 * 4
        if (step == lastBlur) return
        lastBlur = step
        if (Build.VERSION.SDK_INT >= 31 && windowManager.isCrossWindowBlurEnabled) {
            if (step > 0) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) else window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.also { it.blurBehindRadius = step }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val st = state ?: return
        val isHome = intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)
        if (!isHome) return
        if (resumed && !st.dismissTop()) {
            st.run(this, RintApp.instance.stores.config.value.gestures.homePress)
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { SysWidgets.host(this).startListening() }
        RintApp.instance.music.start()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        state?.let { it.homeVisits++ }
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        RintApp.instance.stores.flushAll()
    }

    override fun onStop() {
        super.onStop()
        runCatching { SysWidgets.host(this).stopListening() }
        state?.let { st ->
            st.notchExpanded = false
            st.appMenu = null
            st.homeMenu = null
            if (st.drawer.value > 0f) st.closeDrawer()
        }
    }

    @Deprecated("AppWidget bind/configure still report through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!SysWidgets.onResult(this, requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
