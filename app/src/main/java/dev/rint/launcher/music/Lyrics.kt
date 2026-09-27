package dev.rint.launcher.music

import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class LyricLine(val timeMs: Long, val text: String)

@Serializable
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: String,
    val instrumental: Boolean = false,
) {
    fun indexAt(posMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= posMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    /** True while nobody is singing: intro, long instrumental breaks, blank/♪ lines. */
    fun isBreak(posMs: Long): Boolean {
        if (instrumental) return true
        if (!synced || lines.isEmpty()) return false
        val i = indexAt(posMs)
        if (i < 0) return lines.first().timeMs - posMs > 1500
        val cur = lines[i]
        if (cur.text.isBlank() || cur.text.trim().all { it == '♪' || it == '♫' || it.isWhitespace() }) return true
        val next = lines.getOrNull(i + 1) ?: return posMs - cur.timeMs > 6000
        return next.timeMs - cur.timeMs > 9000 && posMs - cur.timeMs > 4500
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun get(url: String, timeout: Int = 7000): String? = runCatching {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = timeout
    c.readTimeout = timeout
    c.setRequestProperty("User-Agent", "RintOS/0.1 (https://github.com/ionlycomedheretotestlol/new-product-maybe)")
    c.setRequestProperty("Accept", "application/json")
    try {
        if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else null
    } finally {
        c.disconnect()
    }
}.getOrNull()

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

object LyricsService {
    // widget, notch, lock screen and player all ask at once: share one lookup per song
    private val memory = java.util.Collections.synchronizedMap(HashMap<String, Lyrics?>())
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Lyrics?>>()

    fun parseLrc(text: String): List<LyricLine> {
        val tag = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val out = ArrayList<LyricLine>()
        text.lineSequence().forEach { raw ->
            val stamps = tag.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val body = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                out += LyricLine(min * 60_000 + sec * 1000 + ms, body)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    private fun plain(text: String, source: String) = Lyrics(
        lines = text.lines().map { LyricLine(0, it.trim()) }.dropWhile { it.text.isBlank() },
        synced = false,
        source = source,
    )

    private fun fromLrclib(o: JsonObject): Lyrics? {
        val instrumental = o["instrumental"]?.jsonPrimitive?.content == "true"
        val synced = o["syncedLyrics"]?.jsonPrimitive?.takeIf { it.isString }?.content
        val plainText = o["plainLyrics"]?.jsonPrimitive?.takeIf { it.isString }?.content
        return when {
            !synced.isNullOrBlank() -> Lyrics(parseLrc(synced), true, "LRCLIB")
            instrumental -> Lyrics(emptyList(), true, "LRCLIB", instrumental = true)
            !plainText.isNullOrBlank() -> plain(plainText, "LRCLIB")
            else -> null
        }
    }

    private fun clean(s: String) = s
        .replace(Regex("""\s*[(\[](feat|ft|with|official|lyric|video|audio|remaster)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+-\s+(Remaster|Live|Radio Edit).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    suspend fun fetch(ctx: Context, t: Track): Lyrics? {
        val key = "${clean(t.title).lowercase()}|${clean(t.artist.split(",", "&", " x ", " feat").first()).lowercase()}"
        if (memory.containsKey(key)) return memory[key]
        val job = inFlight.computeIfAbsent(key) {
            kotlinx.coroutines.GlobalScope.async(Dispatchers.IO) { runCatching { load(ctx, t, key) }.getOrNull() }
        }
        return try { job.await() } finally { inFlight.remove(key, job) }
    }

    private suspend fun load(ctx: Context, t: Track, key: String): Lyrics? = withContext(Dispatchers.IO) {
        val title = clean(t.title)
        val artist = clean(t.artist.split(",", "&", " x ", " feat").first())
        val cacheFile = File(ctx.cacheDir, "lyrics/${key.hashCode()}.json")
        runCatching {
            if (cacheFile.exists()) return@withContext json.decodeFromString(Lyrics.serializer(), cacheFile.readText())
        }

        val dur = t.durationMs / 1000
        val attempts = sequence<() -> Lyrics?> {
            // 1) exact signature match (best sync accuracy)
            yield {
                val d = if (dur > 0) "&duration=$dur" else ""
                val al = if (t.album.isNotBlank()) "&album_name=${enc(t.album)}" else ""
                get("https://lrclib.net/api/get?track_name=${enc(title)}&artist_name=${enc(artist)}$al$d")
                    ?.let { fromLrclib(json.parseToJsonElement(it).jsonObject) }
            }
            // 2) fuzzy search, prefer synced + closest duration
            yield {
                get("https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist)}")
                    ?.let { body ->
                        json.parseToJsonElement(body).jsonArray.map { it.jsonObject }
                            .sortedWith(compareBy<JsonObject>(
                                { if (it["syncedLyrics"]?.jsonPrimitive?.isString == true) 0 else 1 },
                                { kotlin.math.abs((it["duration"]?.jsonPrimitive?.longOrNull ?: dur) - dur) },
                            ))
                            .firstNotNullOfOrNull { fromLrclib(it) }
                    }
            }
            // 3) broad query search
            yield {
                get("https://lrclib.net/api/search?q=${enc("$title $artist")}")
                    ?.let { body -> json.parseToJsonElement(body).jsonArray.firstNotNullOfOrNull { fromLrclib(it.jsonObject) } }
            }
            // 4) plain-text fallback
            yield {
                get("https://api.lyrics.ovh/v1/${enc(artist)}/${enc(title)}")
                    ?.let { json.parseToJsonElement(it).jsonObject["lyrics"]?.jsonPrimitive?.content }
                    ?.takeIf { it.isNotBlank() }
                    ?.let { plain(it, "lyrics.ovh") }
            }
        }
        val result = attempts.firstNotNullOfOrNull { runCatching { it() }.getOrNull() }
        memory[key] = result
        if (result != null) runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(json.encodeToString(Lyrics.serializer(), result))
        }
        result
    }
}

object MusicSearch {
    suspend fun search(ctx: Context, q: String): List<Track> = withContext(Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        // phone files first, then the official catalog, then full-length songs from Audius
        val full = MusicSources.audiusSearch(q)
        local(ctx, q) + catalog(q) + full
    }

    private fun catalog(q: String): List<Track> = runCatching {
        val body = get("https://itunes.apple.com/search?term=${enc(q)}&entity=song&limit=25") ?: return emptyList()
        json.parseToJsonElement(body).jsonObject["results"]!!.jsonArray.mapNotNull { e ->
            val o = e.jsonObject
            val name = o["trackName"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Track(
                title = name,
                artist = o["artistName"]?.jsonPrimitive?.content ?: "",
                album = o["collectionName"]?.jsonPrimitive?.content ?: "",
                durationMs = o["trackTimeMillis"]?.jsonPrimitive?.longOrNull ?: 0,
                artUrl = o["artworkUrl100"]?.jsonPrimitive?.content?.replace("100x100bb", "1000x1000bb"),
            )
        }.distinctBy { "${it.title.lowercase()}|${it.artist.lowercase()}" }
    }.getOrDefault(emptyList())

    private fun local(ctx: Context, q: String): List<Track> {
        val perm = if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(ctx, perm) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = ArrayList<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION,
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
        runCatching {
            ctx.contentResolver.query(uri, proj, sel, arrayOf("%$q%", "%$q%"), null)?.use { c ->
                while (c.moveToNext() && out.size < 15) {
                    val id = c.getLong(0)
                    out += Track(
                        title = c.getString(1) ?: "Unknown",
                        artist = c.getString(2)?.takeUnless { it == "<unknown>" } ?: "",
                        album = c.getString(3) ?: "",
                        durationMs = c.getLong(4),
                        localUri = android.content.ContentUris.withAppendedId(uri, id),
                    )
                }
            }
        }
        return out
    }
}

