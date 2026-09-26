package dev.rint.launcher.lock

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.rint.launcher.MainActivity
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.Binding
import dev.rint.launcher.core.GestureAction
import dev.rint.launcher.core.RintJson
import dev.rint.launcher.system.SystemActions
import dev.rint.launcher.ui.RintTheme

/**
 * The RintOS lock screen. It sits on top of the system keyguard (it never replaces its
 * security): swiping up asks the system to dismiss the keyguard, which still requires your
 * PIN / fingerprint / face if you have one set.
 */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        val stores = RintApp.instance.stores
        setContent {
            val cfg by stores.config.state.collectAsState()
            BackHandler { }
            RintTheme(cfg) {
                LockScreen(onUnlock = { unlock(null) }, onShortcut = ::runShortcut)
            }
        }
    }

    private fun runShortcut(b: Binding) {
        when (b.action) {
            GestureAction.FLASHLIGHT -> SystemActions.toggleFlashlight(this)
            GestureAction.CAMERA -> runCatching {
                startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            GestureAction.MUSIC -> RintApp.instance.music.toggle()
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(this)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(this)
            GestureAction.LOCK -> { SystemActions.lock(); finish() }
            GestureAction.NONE -> Unit
            else -> unlock(b)
        }
    }

    /** Asks the system to drop the keyguard, then (optionally) runs [then] in the launcher. */
    private fun unlock(then: Binding?) {
        val km = getSystemService(KeyguardManager::class.java)
        val proceed = {
            if (then != null) {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_ACTION, RintJson.encodeToString(Binding.serializer(), then))
                )
            }
            finish()
        }
        if (!km.isKeyguardLocked) { proceed(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = proceed()
            override fun onDismissError() = proceed()
        })
    }

    companion object {
        private var receiver: BroadcastReceiver? = null

        /** Shows the lock screen whenever the display turns off, so it's ready when you wake the phone. */
        fun install(ctx: Context) {
            if (receiver != null) return
            val rx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (!RintApp.instance.stores.config.value.lock.enabled) return
                    show(c)
                }
            }
            receiver = rx
            ctx.registerReceiver(rx, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        fun show(c: Context) {
            runCatching {
                c.startActivity(
                    Intent(c, LockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                )
            }
        }
    }
}
