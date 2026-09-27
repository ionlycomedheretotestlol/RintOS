package dev.rint.launcher.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

enum class Lang(val code: String, val locale: Locale) {
    EN("en", Locale.US),
    PT("pt", Locale("pt", "BR"));

    companion object {
        fun of(code: String): Lang? = entries.find { it.code == code }
    }
}

/**
 * RintOS is written in English; Portuguese is applied where text is shown.
 * Exact strings are looked up in [PtBr.exact]; strings built from templates match [PtBr.patterns]
 * ("{}" is a hole whose content is kept as is; "{t}" in the Portuguese side also translates it).
 */
object I18n {
    private val hole = Regex("\\{t?\\}")
    var lang by mutableStateOf(Lang.EN)

    val locale: Locale get() = lang.locale
    val pt: Boolean get() = lang == Lang.PT

    private class Pattern(val regex: Regex, val out: String)

    private val patterns: List<Pattern> by lazy {
        PtBr.patterns.sortedByDescending { it.first.replace("{}", "").length }.map { (en, pt) ->
            val parts = en.split("{}")
            val rx = parts.joinToString("(.+?)", prefix = "^", postfix = "$") { Regex.escape(it) }
            runCatching { Pattern(Regex(rx, RegexOption.DOT_MATCHES_ALL), pt) }.getOrNull()
        }.filterNotNull()
    }
    private val lower: Map<String, String> by lazy {
        PtBr.exact.entries.associate { (k, v) -> k.lowercase() to (if (k == k.uppercase() && k != k.lowercase()) v.lowercase() else v) }
    }
    private val cache = HashMap<String, String>()

    fun t(en: String): String {
        if (lang == Lang.EN || en.isBlank()) return en
        synchronized(cache) { cache[en]?.let { return it } }
        val out = runCatching { translate(en) }.getOrDefault(en)
        synchronized(cache) {
            if (cache.size > 3000) cache.clear()
            cache[en] = out
        }
        return out
    }

    private fun exact(s: String): String? {
        PtBr.exact[s]?.let { return it }
        // Same phrase written in another case ("SETTINGS", "Settings")
        val l = lower[s.lowercase()] ?: return null
        return when {
            s.length > 1 && s == s.uppercase() && s != s.lowercase() -> l.uppercase(locale)
            s.first().isUpperCase() -> l.replaceFirstChar { it.titlecase(locale) }
            else -> l.replaceFirstChar { it.lowercase(locale) }
        }
    }

    private fun translate(en: String): String {
        exact(en)?.let { return it }
        // keep surrounding whitespace / newlines intact
        val lead = en.takeWhile { it.isWhitespace() }
        val trail = en.takeLastWhile { it.isWhitespace() }
        if (lead.isNotEmpty() || trail.isNotEmpty()) {
            val core = en.trim()
            if (core.isNotEmpty()) exact(core)?.let { return lead + it + trail }
        }
        for (p in patterns) {
            val m = p.regex.matchEntire(en) ?: continue
            var i = 0
            return hole.replace(p.out) {
                val g = m.groupValues.getOrNull(++i) ?: ""
                (if (it.value == "{t}") exact(g) ?: g else g).replace("\\", "\\\\").replace("$", "\\$")
            }
        }
        return en
    }
}

/** Shorthand for strings shown outside Compose [Text] (views, canvas, speech bubbles). */
val String.tr: String get() = I18n.t(this)

/** Drop-in for Material's Text that shows the current language. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    I18n.t(text), modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
    textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style,
)

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
    textAlign, lineHeight, overflow, softWrap, maxLines, minLines, inlineContent, onTextLayout, style,
)

/** Text that is never translated: song titles, artists, lyrics, app names, chat messages. */
@Composable
fun RawText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = androidx.compose.material3.Text(
    text, modifier, color, fontSize, fontStyle, fontWeight, fontFamily, letterSpacing, textDecoration,
    textAlign, lineHeight, overflow, softWrap, maxLines, minLines, onTextLayout, style,
)
