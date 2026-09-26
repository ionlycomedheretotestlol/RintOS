package dev.rint.launcher.music

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Plays songs through YouTube's own mobile site in a visible WebView.
 *
 * Flow: a popup opens on the search results and picks the top video. If an ad runs, the popup
 * simply waits. Once the real song is playing, the popup shrinks into a small floating
 * mini-player (YouTube's rules require the video to stay visible, so it never hides completely).
 * With "keep playing over other apps" on, the mini-player moves into a floating window when
 * you leave RintOS.
 */
object WebPlayer {
    enum class Stage { HIDDEN, POPUP, MINI }

    var stage by mutableStateOf(Stage.HIDDEN)
    var status by mutableStateOf("")
    /** Bumped whenever the view is taken back from a floating window, so Compose re-hosts it. */
    var hostGeneration by mutableIntStateOf(0)

    private var web: WebView? = null
    private var holder: FrameLayout? = null
    private val main = Handler(Looper.getMainLooper())
    private var polling = false
    private var floating = false
    private var sawAd = false

    // Hides the "page is hidden" signal so the video isn't paused by the page itself while
    // the (still visible) floating window is up.
    private const val KEEP_VISIBLE_JS = """
        (function(){try{Object.defineProperty(document,'hidden',{get:function(){return false}});
        Object.defineProperty(document,'visibilityState',{get:function(){return 'visible'}});
        document.addEventListener('visibilitychange',function(e){e.stopImmediatePropagation()},true);}catch(e){}})();"""

    private const val PICK_FIRST_JS = """
        (function(){var a=document.querySelector('a[href^="/watch"]'); if(a){a.click(); return 'ok'} return 'none'})();"""

    private const val STATE_JS = """
        (function(){var v=document.querySelector('video'); if(!v) return JSON.stringify({v:0});
        var p=document.querySelector('#movie_player,.html5-video-player');
        var ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')))||!!document.querySelector('.ytp-ad-player-overlay,.ad-interrupting');
        if(v.paused&&!v.ended&&!ad&&v.currentTime<1){try{v.play()}catch(e){}}
        var t=document.querySelector('h2.slim-video-metadata-title, .slim-video-information-title, title');
        return JSON.stringify({v:1,t:v.currentTime,d:v.duration||0,p:!v.paused&&!v.ended,ad:ad,title:t?t.textContent:''});})();"""

    /** The host view (a rounded container with the WebView inside). */
    @SuppressLint("SetJavaScriptEnabled")
    fun view(context: Context): FrameLayout {
        holder?.let { h -> (h.parent as? ViewGroup)?.removeView(h); return h }
        val w = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host ?: return true
                    // stay inside YouTube; never jump out to other sites or apps
                    return !(host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("gstatic.com"))
                }
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(KEEP_VISIBLE_JS, null)
                    if (url.contains("/results")) pickFirst(0)
                }
            }
        }
        web = w
        val h = FrameLayout(context.applicationContext).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, 28f * view.resources.displayMetrics.density / 2)
            }
            addView(w, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        holder = h
        return h
    }

    private fun pickFirst(attempt: Int) {
        if (attempt > 12 || stage == Stage.HIDDEN) return
        main.postDelayed({
            web?.evaluateJavascript(PICK_FIRST_JS) { r -> if (r?.contains("ok") != true) pickFirst(attempt + 1) }
        }, 600)
    }

    fun play(context: Context, track: Track) {
        view(context)
        sawAd = false
        status = "finding \"${track.title}\"…"
        stage = Stage.POPUP
        val q = URLEncoder.encode("${track.title} ${track.artist} audio", "UTF-8")
        web?.loadUrl("https://m.youtube.com/results?search_query=$q")
        startPolling()
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        val tick = object : Runnable {
            override fun run() {
                if (!polling) return
                web?.evaluateJavascript(STATE_JS) { raw -> handleState(raw) }
                main.postDelayed(this, 500)
            }
        }
        main.post(tick)
    }

    private fun handleState(raw: String?) {
        val json = runCatching { JSONObject(JSONObject("{\"x\":$raw}").getString("x")) }.getOrNull() ?: return
        if (json.optInt("v") == 0) return
        val t = json.optDouble("t", 0.0)
        val d = json.optDouble("d", 0.0)
        val playing = json.optBoolean("p")
        val ad = json.optBoolean("ad")
        if (ad) { sawAd = true; status = "an ad is playing. Rin is waiting with you…" }
        else if (!playing && t < 1) status = if (sawAd) "almost there…" else "tap play if it doesn't start"
        if (!ad) {
            dev.rint.launcher.RintApp.instance.music.onWebState(playing, (t * 1000).toLong(), if (d.isFinite()) (d * 1000).toLong() else 0L)
            if (playing && t > 0.8 && stage == Stage.POPUP) stage = Stage.MINI
        }
    }

    fun js(code: String) = web?.evaluateJavascript(code, null)
    fun toggle(play: Boolean) = js("(function(){var v=document.querySelector('video'); if(v){ ${if (play) "v.play()" else "v.pause()"} }})()")
    fun seek(ms: Long) = js("(function(){var v=document.querySelector('video'); if(v){v.currentTime=${ms / 1000.0}}})()")

    fun stop() {
        polling = false
        toggle(false)
        web?.loadUrl("about:blank")
        stage = Stage.HIDDEN
        leaveFloating()
    }

    // ── floating over other apps ──────────────────────────
    fun onAppStopped(context: Context, keepPlaying: Boolean) {
        if (stage != Stage.MINI) return
        if (!keepPlaying || !Settings.canDrawOverlays(context)) { toggle(false); return }
        val h = holder ?: return
        (h.parent as? ViewGroup)?.removeView(h)
        val wm = context.getSystemService(WindowManager::class.java)
        val d = context.resources.displayMetrics.density
        val lp = WindowManager.LayoutParams(
            (220 * d).toInt(), (124 * d).toInt(),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = (12 * d).toInt(); y = (120 * d).toInt() }
        runCatching { wm.addView(h, lp); floating = true }
    }

    fun onAppStarted() = leaveFloating()

    private fun leaveFloating() {
        val h = holder ?: return
        if (!floating) return
        runCatching { h.context.getSystemService(WindowManager::class.java).removeView(h) }
        floating = false
        hostGeneration++
    }
}
