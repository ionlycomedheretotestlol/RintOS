package dev.rint.launcher.intro

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import dev.rint.launcher.settings.Schema
import dev.rint.launcher.ui.BlockFont
import org.json.JSONArray
import org.json.JSONObject

/**
 * The three.js half of the intro: a transparent WebView behind the Compose film, running
 * assets/intro/stage.html. It reads the film's clock every frame through a tiny bridge, so the 3D
 * cubes land on the same beats as the music. If WebGL isn't available, [ok] stays false and
 * the film uses its 2D versions of those scenes instead.
 */
class Stage3DState {
    var ok by mutableStateOf(false)
        internal set
    var failed by mutableStateOf(false)
        internal set
    @Volatile internal var clock = 0f
}

private class Bridge(private val state: Stage3DState, private val config: String) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun time(): Float = state.clock
    @JavascriptInterface fun config(): String = config
    @JavascriptInterface fun ready() { main.post { state.ok = true } }
    @JavascriptInterface fun fail(msg: String) { main.post { state.failed = true; state.ok = false } }
}

/** Voxel layouts from the launcher's own block font: [[x, y, accent]]. */
private fun spec(lines: List<String>, accentLines: Set<Int>): JSONObject {
    val cells = JSONArray()
    val w = lines.maxOf { BlockFont.cells(it).second }
    var y0 = 0
    lines.forEachIndexed { i, l ->
        val (c, lw) = BlockFont.cells(l)
        val off = (w - lw) / 2
        c.forEach { (x, y) -> cells.put(JSONArray().put(x + off).put(y + y0).put(if (i in accentLines) 1 else 0)) }
        y0 += 6
    }
    return JSONObject().put("cells", cells).put("w", w).put("h", y0 - 1)
}

private fun spec1(text: String, accentFrom: Int): JSONObject {
    val (c, w) = BlockFont.cells(text)
    val cells = JSONArray()
    c.forEach { (x, y) -> cells.put(JSONArray().put(x).put(y).put(if (x >= accentFrom) 1 else 0)) }
    return JSONObject().put("cells", cells).put("w", w).put("h", 5)
}

internal fun stageConfig(accent: Int): String {
    val kick = JSONArray()
    for (b in 0..Score.END + 2) if (Score.kick(b)) kick.put(b)
    val hex = String.format("#%06X", accent and 0xFFFFFF)
    return JSONObject()
        .put("bar", Score.BAR).put("beat", Score.BEAT).put("accent", hex).put("kickBars", kick)
        .put("words", JSONObject()
            .put("STACK", spec(listOf("RINT", "OS"), setOf(1)))
            .put("VERSION", spec1("1.3", 4))
            .put("BIG", spec1("1.3", 4))
            .put("LINE", spec1("RINTOS", 18)))
        .put("features", JSONArray(listOf(
            "Rin, your AI", "songs play right here", "${Schema.optionCount} settings", "10 lock screens",
            "your photo & live wallpapers", "battery saver dot", "no ads. no tracking.", "…and it's free",
        )))
        .put("sections", JSONObject()
            .put("rise", JSONArray(listOf(Score.RISE, Score.BUILD)))
            .put("tunnel", JSONArray(listOf(Score.TUNNEL, Score.LIFT)))
            .put("final", JSONArray(listOf(Score.FINAL, Score.END))))
        .put("outro", Score.OUTRO)
        .toString()
}

@Composable
fun rememberStage3D(): Stage3DState = remember { Stage3DState() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun Stage3D(state: Stage3DState, time: () -> Float, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) return
    val accent = dev.rint.launcher.ui.LocalRintOrNull.current?.colors?.accent?.toArgb() ?: 0xFF3B7CFF.toInt()
    val config = remember(accent) { stageConfig(accent) }
    LaunchedEffect(Unit) { while (true) withFrameNanos { state.clock = time() } }
    var web by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { web?.let { runCatching { it.stopLoading(); it.destroy() } } } }
    AndroidView(
        factory = { ctx ->
            // no WebView on this device (or in tests)? then the 2D film carries on alone
            try { WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                // the page never goes anywhere: no links, no network
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
                }
                addJavascriptInterface(Bridge(state, config), "RintBridge")
                loadUrl("file:///android_asset/intro/stage.html")
                web = this
            } } catch (t: Throwable) {
                state.failed = true
                View(ctx)
            }
        },
        modifier = modifier,
    )
}
