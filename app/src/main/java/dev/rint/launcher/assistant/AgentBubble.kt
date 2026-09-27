package dev.rint.launcher.assistant

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import dev.rint.launcher.R
import dev.rint.launcher.RintApp

/**
 * Rin's floating panel while he works in other apps. Expanded: his head (talking, thinking),
 * what he's doing, what he said, an input bar, and collapse / stop / close. Collapsed: just his
 * head in a little circle you can drag and tap to reopen. Uses an accessibility overlay window
 * when the service is running (no extra permission), or a normal "display over other apps"
 * window otherwise.
 */
class AgentBubble(private val context: Context, private val windowType: Int = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val dp = context.resources.displayMetrics.density
    private val head = dev.rint.launcher.mascot.RinView(context)
    private val label = TextView(context)
    private val said = TextView(context)
    private val actions = LinearLayout(context)
    private val input = EditText(context)
    private val card = LinearLayout(context)
    private val root = LinearLayout(context)
    private var attached = false
    private var collapsed = false
    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START; y = (36 * dp).toInt() }

    var onStop: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null
    var onSend: ((String) -> Unit)? = null

    private fun accent(): Int = runCatching { RintApp.instance.stores.config.value.let { it.mascot.color ?: it.look.accent }.toInt() }.getOrDefault(Color.parseColor("#3B7CFF"))

    private val font = ResourcesCompat.getFont(context, R.font.inter_600)

    init {
        head.accent = accent()
        root.orientation = LinearLayout.VERTICAL
        root.setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        card.background = GradientDrawable().apply {
            cornerRadius = 26 * dp
            setColor(Color.parseColor("#F2111218"))
            setStroke((1 * dp).toInt(), (accent() and 0x00FFFFFF) or 0x66000000)
        }
        card.elevation = 14 * dp

        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(head, LinearLayout.LayoutParams((44 * dp).toInt(), (42 * dp).toInt()))
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((10 * dp).toInt(), 0, (6 * dp).toInt(), 0) }
        label.setTextColor(Color.WHITE); label.textSize = 14f; label.typeface = font ?: Typeface.DEFAULT_BOLD; label.maxLines = 2
        said.setTextColor(Color.parseColor("#B8BDD0")); said.textSize = 12.5f; said.maxLines = 3; said.visibility = View.GONE
        texts.addView(label); texts.addView(said)
        top.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(icon("▾") { collapse(true) })
        top.addView(icon("✕") { onClose?.invoke() })
        card.addView(top)

        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.END
        card.addView(actions, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * dp).toInt() })

        input.hint = "tell Rin something…"
        input.setHintTextColor(Color.parseColor("#80FFFFFF"))
        input.setTextColor(Color.WHITE)
        input.textSize = 14f
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
        input.background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(Color.parseColor("#22FFFFFF")) }
        input.setOnFocusChangeListener { _, has -> setFocusable(has) }
        input.setOnTouchListener { v, e -> if (e.action == MotionEvent.ACTION_DOWN) { setFocusable(true); v.requestFocus() }; false }
        input.setOnEditorActionListener { _, id, ev ->
            if (id == EditorInfo.IME_ACTION_SEND || ev?.keyCode == KeyEvent.KEYCODE_ENTER) {
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) { input.setText(""); onSend?.invoke(t) }
                setFocusable(false)
                true
            } else false
        }
        card.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * dp).toInt() })
        root.addView(card)

        // tapping the collapsed head expands it again
        head.setOnClickListener { if (collapsed) collapse(false) }
    }

    private fun icon(text: String, onClick: () -> Unit) = TextView(context).apply {
        this.text = dev.rint.launcher.ui.I18n.t(text)
        setTextColor(Color.parseColor("#C8CCDA"))
        textSize = 16f
        setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        setOnClickListener { onClick() }
    }

    private fun chip(text: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = dev.rint.launcher.ui.I18n.t(text)
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = ResourcesCompat.getFont(context, R.font.inter_700)
        setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
        background = GradientDrawable().apply { cornerRadius = 20 * dp; setColor(if (primary) accent() else Color.parseColor("#33FFFFFF")) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = (6 * dp).toInt() }
    }

    private fun setFocusable(f: Boolean) {
        if (!attached) return
        lp.flags = if (f) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        runCatching { wm.updateViewLayout(root, lp) }
        if (!f) context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun collapse(c: Boolean) {
        collapsed = c
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            if (i == 0) {
                val row = child as LinearLayout
                for (j in 1 until row.childCount) row.getChildAt(j).visibility = if (c) View.GONE else View.VISIBLE
            } else child.visibility = if (c) View.GONE else View.VISIBLE
        }
        if (!c) said.visibility = if (said.text.isNullOrBlank()) View.GONE else View.VISIBLE
        lp.width = if (c) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT
        (card.background as? GradientDrawable)?.cornerRadius = (if (c) 40 else 26) * dp
        if (attached) runCatching { wm.updateViewLayout(root, lp) }
    }

    private fun attach() {
        if (attached) return
        runCatching { wm.addView(root, lp); attached = true }
    }

    fun status(text: String, busy: Boolean = true) = main.post {
        attach()
        label.text = dev.rint.launcher.ui.I18n.t(text)
        actions.removeAllViews()
        if (busy) actions.addView(chip("stop", false) { onStop?.invoke() })
    }

    /** Shows what Rin said (under the status line). */
    fun say(text: String) = main.post {
        attach()
        said.text = text
        if (!collapsed) said.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    fun ask(question: String, onAnswer: (Boolean) -> Unit) = main.post {
        attach()
        if (collapsed) collapse(false)
        label.text = dev.rint.launcher.ui.I18n.t(question)
        actions.removeAllViews()
        actions.addView(chip("no", false) { onAnswer(false) })
        actions.addView(chip("yes", true) { onAnswer(true) })
    }

    /** Lip-sync: Rin's mouth follows the voice level (0..1). */
    fun talking(level: Float) = main.post { head.talk = level }

    /** Rin's expression while working: thinking, happy when done, etc. */
    fun mood(pose: dev.rint.launcher.mascot.Pose) = main.post { head.pose = pose }

    fun setVisible(v: Boolean) = main.post { root.visibility = if (v) View.VISIBLE else View.INVISIBLE }

    fun remove() = main.post {
        if (attached) runCatching { wm.removeView(root) }
        attached = false
    }
}
