package dev.rint.launcher.assistant

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import dev.rint.launcher.R

/**
 * A small pill pinned near the top of the screen while Rin operates other apps: Rin's head
 * (bobbing while talking), what he's doing, a stop button, and yes/no when he needs approval.
 * Uses an accessibility overlay window, so no "draw over apps" permission is needed.
 */
class AgentBubble(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val dp = service.resources.displayMetrics.density
    private val head = ImageView(service)
    private val label = TextView(service)
    private val actions = LinearLayout(service)
    private val root = LinearLayout(service)
    private var attached = false
    private var talk: ValueAnimator? = null
    var onStop: (() -> Unit)? = null

    init {
        val font = ResourcesCompat.getFont(service, R.font.inter_600)
        root.orientation = LinearLayout.HORIZONTAL
        root.gravity = Gravity.CENTER_VERTICAL
        root.setPadding((10 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
        root.background = GradientDrawable().apply {
            cornerRadius = 40 * dp
            setColor(Color.parseColor("#EE0C1230"))
            setStroke((1 * dp).toInt(), Color.parseColor("#553B7CFF"))
        }
        root.elevation = 12 * dp
        head.setImageResource(R.drawable.rin_head)
        root.addView(head, LinearLayout.LayoutParams((38 * dp).toInt(), (38 * dp).toInt()))
        label.setTextColor(Color.WHITE)
        label.textSize = 13f
        label.typeface = font ?: Typeface.DEFAULT_BOLD
        label.maxLines = 2
        label.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        root.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.orientation = LinearLayout.HORIZONTAL
        root.addView(actions)
    }

    private fun chip(text: String, accent: Boolean, onClick: () -> Unit): TextView = TextView(service).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 12f
        typeface = ResourcesCompat.getFont(service, R.font.inter_700)
        setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
        background = GradientDrawable().apply {
            cornerRadius = 20 * dp
            setColor(if (accent) Color.parseColor("#3B7CFF") else Color.parseColor("#33FFFFFF"))
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = (6 * dp).toInt() }
    }

    private fun attach() {
        if (attached) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = (36 * dp).toInt()
            horizontalMargin = 0.03f
        }
        runCatching { wm.addView(root, lp); attached = true }
    }

    fun status(text: String, busy: Boolean = true) = main.post {
        attach()
        label.text = text
        actions.removeAllViews()
        if (busy) actions.addView(chip("stop", false) { onStop?.invoke() })
    }

    fun ask(question: String, onAnswer: (Boolean) -> Unit) = main.post {
        attach()
        label.text = question
        actions.removeAllViews()
        actions.addView(chip("no", false) { onAnswer(false) })
        actions.addView(chip("yes", true) { onAnswer(true) })
    }

    /** Talking animation: bob + squash + frame swap, driven by [level] 0..1. */
    fun talking(level: Float) = main.post {
        if (level > 0.04f) {
            if (talk == null) talk = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 220; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
                addUpdateListener {
                    val f = it.animatedValue as Float
                    head.translationY = -f * 4 * dp
                    head.scaleY = 1f - 0.06f * f
                    head.rotation = (f - 0.5f) * 8f
                    head.setImageResource(if (f > 0.5f) R.drawable.rin_happy else R.drawable.rin_head)
                }
                start()
            }
        } else {
            talk?.cancel(); talk = null
            head.translationY = 0f; head.scaleY = 1f; head.rotation = 0f
            head.setImageResource(R.drawable.rin_head)
        }
    }

    fun setVisible(v: Boolean) = main.post { root.visibility = if (v) View.VISIBLE else View.INVISIBLE }

    fun remove() = main.post {
        talk?.cancel(); talk = null
        if (attached) runCatching { wm.removeView(root) }
        attached = false
    }
}
