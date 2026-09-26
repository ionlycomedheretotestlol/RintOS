package dev.rint.launcher.search

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dev.rint.launcher.apps.AppEntry
import dev.rint.launcher.core.SearchCfg
import dev.rint.launcher.core.SearchEngine

sealed interface Hit {
    data class App(val entry: AppEntry, val score: Int) : Hit
    data class Math(val expr: String, val result: String) : Hit
    data class Convert(val text: String) : Hit
    data class Web(val query: String) : Hit
    data class Music(val query: String) : Hit
    data class Ask(val query: String) : Hit
    data class Setting(val section: String, val title: String) : Hit
    data class Contact(val name: String, val uri: Uri, val number: String?) : Hit
}

object Fuzzy {
    /** Higher is better; 0 means no match. Handles prefixes, word starts, initials and subsequences. */
    fun score(query: String, target: String): Int {
        val q = query.lowercase().trim()
        val t = target.lowercase()
        if (q.isEmpty()) return 1
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - t.length
        val words = t.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 700 - t.length
        val initials = words.joinToString("") { it.take(1) }
        if (initials.startsWith(q)) return 650
        // CamelCase initials: "YouTube" → "yt"
        val caps = target.filter { it.isUpperCase() }.lowercase()
        if (caps.length >= 2 && caps.startsWith(q)) return 640
        val idx = t.indexOf(q)
        if (idx >= 0) return 500 - idx
        var ti = 0
        var gaps = 0
        for (c in q) {
            val found = t.indexOf(c, ti)
            if (found < 0) return 0
            gaps += found - ti
            ti = found + 1
        }
        return (300 - gaps * 8).coerceAtLeast(1)
    }
}

object SettingsIndex {
    /** (section id, searchable title) — keep in sync with SettingsSchema sections. */
    val entries = listOf(
        "look" to "theme colors accent wallpaper font dark light amoled blur",
        "icons" to "icon shape icon pack style badges squircle circle",
        "home" to "grid columns rows page transition indicator search bar",
        "dock" to "dock magnify glass",
        "drawer" to "app drawer list grid sort hidden apps",
        "gestures" to "gestures swipe double tap lock",
        "notch" to "notch island dynamic",
        "clock" to "clock time tty blocky date",
        "mascot" to "mascot rin pet",
        "music" to "music lyrics player",
        "motion" to "animation speed bounce motion",
        "lock" to "lock screen unlock shortcuts",
        "ai" to "ai assistant rin gemini groq claude openrouter voice automation api key",
        "search" to "search engine calculator",
        "backup" to "backup restore export import reset",
    )
}

object Searcher {
    fun run(ctx: Context, q: String, apps: List<AppEntry>, hidden: Set<String>, cfg: SearchCfg): List<Hit> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        if (cfg.calculator && Calc.looksLikeMath(query)) {
            Calc.eval(query)?.let { out += Hit.Math(query, Calc.format(it)) }
        }
        if (cfg.unitConverter) Units.convert(query)?.let { out += Hit.Convert(it) }
        val appHits = apps.asSequence()
            .filter { it.key !in hidden || query.length >= 3 }
            .map { it to maxOf(Fuzzy.score(query, it.label), if (cfg.fuzzy) Fuzzy.score(query, it.packageName.substringAfterLast('.')) / 3 else 0) }
            .filter { it.second > (if (cfg.fuzzy) 0 else 400) }
            .sortedByDescending { it.second }
            .take(24)
            .map { Hit.App(it.first, it.second) }
            .toList()
        out += appHits
        if (cfg.contacts) out += contacts(ctx, query)
        if (cfg.settings) SettingsIndex.entries
            .filter { (_, words) -> words.split(' ').any { it.startsWith(query.lowercase()) } }
            .take(2)
            .forEach { (id, _) -> out += Hit.Setting(id, "Rint settings · $id") }
        if (query.length > 3) out += Hit.Ask(query)
        out += Hit.Music(query)
        if (cfg.webFallback) out += Hit.Web(query)
        return out
    }

    private fun contacts(ctx: Context, q: String): List<Hit> {
        if (q.length < 2) return emptyList()
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = ArrayList<Hit>()
        runCatching {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$q%"),
                null,
            )?.use { c ->
                val seen = HashSet<Long>()
                while (c.moveToNext() && out.size < 3) {
                    val id = c.getLong(0)
                    if (!seen.add(id)) continue
                    out += Hit.Contact(c.getString(1) ?: "", ContactsContract.Contacts.getLookupUri(id, c.getString(3)), c.getString(2))
                }
            }
        }
        return out
    }

    fun webUrl(cfg: SearchCfg, q: String): String {
        val e = Uri.encode(q)
        return when (cfg.engine) {
            SearchEngine.GOOGLE -> "https://www.google.com/search?q=$e"
            SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/?q=$e"
            SearchEngine.BRAVE -> "https://search.brave.com/search?q=$e"
            SearchEngine.BING -> "https://www.bing.com/search?q=$e"
            SearchEngine.STARTPAGE -> "https://www.startpage.com/do/search?q=$e"
            SearchEngine.CUSTOM -> cfg.customUrl.replace("%s", e)
        }
    }

    fun openWeb(ctx: Context, cfg: SearchCfg, q: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(cfg, q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
