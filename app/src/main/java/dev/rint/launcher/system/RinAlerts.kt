package dev.rint.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import dev.rint.launcher.R
import dev.rint.launcher.RintApp
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rin's serious-only popups: very low battery and emergency alerts (tornado, amber, etc.).
 * Nothing else ever uses this. Shows over other apps when allowed, and says it out loud.
 */
object RinAlerts {
    private val main = Handler(Looper.getMainLooper())
    private var current: LinearLayout? = null

    fun show(ctx: Context, title: String, body: String, danger: Boolean = false, speak: Boolean = true) = main.post {
        dismiss(ctx)
        val app = ctx.applicationContext
        val type = when {
            RintAccessibility.instance != null -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            Build.VERSION.SDK_INT >= 26 && Settings.canDrawOverlays(app) -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> null
        }
        if (speak) runCatching { RintApp.instance.assistant.speak("$title. $body") }
        if (type == null) return@post
        val host: Context = RintAccessibility.instance ?: app
        val dp = host.resources.displayMetrics.density
        val accent = if (danger) Color.parseColor("#FF3B30") else runCatching { RintApp.instance.stores.config.value.look.accent.toInt() }.getOrDefault(Color.parseColor("#3B7CFF"))
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 26 * dp; setColor(Color.parseColor("#F5121318")); setStroke((2 * dp).toInt(), accent) }
            elevation = 16 * dp
        }
        val head = RinView(host).apply { pose = if (danger) Pose.SHOCK else Pose.MEH; this.accent = accent }
        card.addView(head, LinearLayout.LayoutParams((52 * dp).toInt(), (50 * dp).toInt()))
        val texts = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding((12 * dp).toInt(), 0, 0, 0) }
        texts.addView(TextView(host).apply {
            text = title; setTextColor(accent); textSize = 15f
            typeface = ResourcesCompat.getFont(host, R.font.inter_800) ?: Typeface.DEFAULT_BOLD
        })
        texts.addView(TextView(host).apply { text = body; setTextColor(Color.WHITE); textSize = 13.5f; maxLines = 4 })
        texts.addView(TextView(host).apply { text = "tap to dismiss"; setTextColor(Color.parseColor("#80FFFFFF")); textSize = 11f })
        card.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP; y = (40 * dp).toInt(); horizontalMargin = 0.03f }
        card.setOnClickListener { dismiss(host) }
        card.translationY = -300 * dp
        runCatching {
            host.getSystemService(WindowManager::class.java).addView(card, lp)
            current = card
            card.animate().translationY(0f).setDuration(420).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
            main.postDelayed({ if (current === card) dismiss(host) }, if (danger) 30_000 else 14_000)
        }
    }

    fun dismiss(ctx: Context) {
        val c = current ?: return
        current = null
        runCatching { c.context.getSystemService(WindowManager::class.java).removeView(c) }
    }

    /** Emergency broadcasts arrive as notifications from the cell-broadcast app. */
    fun onNotification(ctx: Context, sbn: StatusBarNotification) {
        if (!RintApp.instance.stores.config.value.battery.emergencyAlerts) return
        if (!sbn.packageName.contains("cellbroadcast", ignoreCase = true)) return
        val ex = sbn.notification.extras
        val title = ex.getCharSequence("android.title")?.toString() ?: "Emergency alert"
        val text = ex.getCharSequence("android.bigText")?.toString() ?: ex.getCharSequence("android.text")?.toString() ?: ""
        show(ctx, title, text, danger = true)
    }
}

/** Watches the battery for low-power warnings and the battery-saver home. */
object BatteryWatch {
    data class State(val level: Int = 100, val charging: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    /** True while the launcher is collapsed into the battery-saver dot. */
    val saver = MutableStateFlow(false)
    private val warned = HashSet<Int>()

    fun install(ctx: Context) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                if (level < 0) return
                val pct = level * 100 / scale
                // "plugged" is the truth: status can stay CHARGING after a charger is pulled
                val charging = plugged != 0
                update(c, State(pct, charging))
            }
        }
        runCatching { ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    private fun update(ctx: Context, s: State) {
        _state.value = s
        val cfg = RintApp.instance.stores.config.value.battery
        // re-arm every warning once the battery is back above it, so they fire every time
        warned.removeAll { s.charging || s.level > it }
        // leave saver when charging, or whenever the battery is comfortably back up
        if (saver.value && (s.level >= cfg.saverAt + 5 && (s.charging || s.level >= 50))) saver.value = false
        if (s.charging) return
        if (cfg.saver && !saver.value && s.level <= cfg.saverAt) {
            saver.value = true
            if (cfg.alerts) RinAlerts.show(ctx, "battery's at ${s.level}%", "I folded your home screen into saver mode so it lasts longer. It comes back when you charge.")
        }
        if (!cfg.alerts) return
        for (th in intArrayOf(5, 1)) {
            if (s.level <= th && warned.add(th)) {
                RinAlerts.show(ctx, if (th == 1) "1% left!!" else "only ${s.level}% left", if (th == 1) "your phone is about to turn off. plug it in now." else "find a charger soon — I'm keeping things light.", danger = th == 1)
            }
        }
    }
}
