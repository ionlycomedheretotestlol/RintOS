package dev.rint.launcher.music

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import dev.rint.launcher.system.RintNotificationListener
import dev.rint.launcher.system.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Track(
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val artUrl: String? = null,
    val localUri: Uri? = null,
    /** A direct full-length stream (e.g. an Audius search result): plays with no source hunting. */
    val streamUrl: String? = null,
)

enum class Source { NONE, LOCAL, APP, STREAM }

data class NowPlaying(
    val track: Track,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val positionStamp: Long = SystemClock.elapsedRealtime(),
    val speed: Float = 1f,
    val source: Source = Source.NONE,
    val appPackage: String? = null,
    val waiting: Boolean = false,
    /** Which source is playing (or what's being tried), shown in the UI. */
    val via: String? = null,
    /** True when only a short preview is available from free sources. */
    val preview: Boolean = false,
) {
    fun position(now: Long = SystemClock.elapsedRealtime()): Long =
        if (playing) positionMs + ((now - positionStamp) * speed).toLong() else positionMs
}

/**
 * One source of truth for "what's playing". It mirrors whichever media app is active
 * (Spotify, YT Music, anything with a MediaSession) or drives RintOS's own local player.
 */
class MusicEngine(private val context: Context, private val scope: CoroutineScope) {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private var player: MediaPlayer? = null
    private var pendingSearch: Track? = null
    private var waitJob: Job? = null
    private val listenerComponent = ComponentName(context, RintNotificationListener::class.java)

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = syncFromController()
        override fun onPlaybackStateChanged(state: PlaybackState?) = syncFromController()
        override fun onSessionDestroyed() {
            controller = null
            if (_now.value?.source == Source.APP) _now.value = null
            pickController()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pickController(it) }
    private var listening = false

    fun start() {
        if (listening || !SystemActions.notificationAccessGranted(context)) {
            if (listening) pickController()
            return
        }
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, main)
            listening = true
            pickController()
        }
    }

    val hasSessionAccess: Boolean get() = listening

    private fun pickController(list: List<MediaController>? = null) {
        if (_now.value?.source.let { it == Source.LOCAL || it == Source.STREAM }) return
        val sessions = list ?: runCatching { msm.getActiveSessions(listenerComponent) }.getOrDefault(emptyList())
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.packageName == pendingAppPackage() }
            ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = best
            best?.registerCallback(controllerCallback, main)
        }
        syncFromController()
    }

    private fun pendingAppPackage(): String? = _now.value?.appPackage

    /** True while RintOS itself is playing (or starting) a song. */
    private val ownPlayback: Boolean
        get() = _now.value?.source.let { it == Source.LOCAL || it == Source.STREAM } && (player != null || streamJob?.isActive == true)

    private fun syncFromController() {
        // another app's session (e.g. the one we just paused) reports its old song: ignore it while we play ours
        if (ownPlayback) return
        val c = controller ?: return
        val md = c.metadata ?: return
        val st = c.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
        val playing = st?.state == PlaybackState.STATE_PLAYING
        val prev = _now.value
        val keepUrl = prev?.track?.takeIf { it.title.equals(title, true) }?.artUrl
        val track = Track(
            title = title,
            artist = artist,
            album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            artUrl = artUri ?: keepUrl,
        )
        val pending = pendingSearch
        val arrived = pending == null || title.contains(pending.title, true) || pending.title.contains(title, true)
        if (arrived) pendingSearch = null
        _now.value = NowPlaying(
            track = track,
            art = art,
            playing = playing,
            positionMs = st?.position ?: 0,
            positionStamp = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            source = Source.APP,
            appPackage = c.packageName,
            waiting = !arrived,
        )
    }

    /** Stops whatever is playing and returns the widget to search. */
    fun stop() {
        request++
        waitJob?.cancel()
        streamJob?.cancel()
        pendingSearch = null
        when (_now.value?.source) {
            Source.APP -> controller?.transportControls?.pause()
            else -> Unit
        }
        stopLocal()
        _now.value = null
    }

    fun toggle() {
        val n = _now.value ?: return
        when (n.source) {
            Source.LOCAL, Source.STREAM -> player?.let { if (it.isPlaying) it.pause() else it.start(); publishLocal() }
            Source.APP -> controller?.transportControls?.let {
                if (n.playing) it.pause() else it.play()
            }
            Source.NONE -> Unit
        }
    }

    fun next() {
        if (_now.value?.source == Source.APP) controller?.transportControls?.skipToNext()
    }

    fun previous() {
        when (_now.value?.source) {
            Source.APP -> controller?.transportControls?.skipToPrevious()
            Source.LOCAL, Source.STREAM -> { player?.seekTo(0); publishLocal() }
            else -> Unit
        }
    }

    fun seekTo(ms: Long) {
        when (_now.value?.source) {
            Source.APP -> controller?.transportControls?.seekTo(ms)
            Source.LOCAL, Source.STREAM -> { player?.seekTo(ms.toInt()); publishLocal() }
            else -> Unit
        }
    }

    /**
     * Plays a track the way the user chose: streamed inside RintOS through a chain of free music
     * APIs (default), local files, or by handing off to the user's music app.
     */
    fun play(track: Track, via: dev.rint.launcher.core.PlayVia, preferredApp: String?) {
        if (track.localUri != null) return playLocal(track)
        when (via) {
            dev.rint.launcher.core.PlayVia.APP -> play(track, preferredApp)
            else -> playStream(track)
        }
    }

    private var streamJob: Job? = null
    /** Bumped for every new play/stop, so an older attempt can tell it's been replaced. */
    private var request = 0

    /** Tries each source in [MusicSources] until one actually plays. */
    private fun playStream(track: Track) {
        stopLocal()
        controller?.transportControls?.pause()
        _now.value = NowPlaying(track = track, source = Source.STREAM, waiting = true, via = "finding a source…")
        val req = ++request
        streamJob?.cancel()
        streamJob = scope.launch {
            val tried = ArrayList<String>()
            // picked straight from a full-song search result: just play it
            track.streamUrl?.let { url ->
                _now.value = _now.value?.copy(via = "Audius (full song)")
                val ok = kotlinx.coroutines.CompletableDeferred<Boolean>()
                playUrl(track, Uri.parse(url), Source.STREAM, onResult = { ok.complete(it) })
                val started = kotlinx.coroutines.withTimeoutOrNull(15_000) { ok.await() } ?: false
                if (request != req) return@launch
                if (started) { _now.value = _now.value?.copy(waiting = false); return@launch }
                tried += "Audius: couldn't play"
                stopLocal()
            }
            for (src in MusicSources.all) {
                if (request != req) return@launch
                _now.value = _now.value?.copy(via = "trying ${src.name}…")
                val hit = runCatching { src.find(track) }.getOrNull()
                if (hit == null) { tried += "${src.name}: not found"; continue }
                val ok = kotlinx.coroutines.CompletableDeferred<Boolean>()
                playUrl(track, Uri.parse(hit.url), Source.STREAM, onResult = { ok.complete(it) })
                val started = kotlinx.coroutines.withTimeoutOrNull(15_000) { ok.await() } ?: false
                if (request != req) return@launch
                if (started) {
                    _now.value = _now.value?.copy(via = src.name, preview = hit.preview, waiting = false,
                        track = if (hit.durationMs > 0) track.copy(durationMs = hit.durationMs) else track)
                    return@launch
                }
                tried += "${src.name}: couldn't play"
                stopLocal()
            }
            if (request == req) _now.value = _now.value?.copy(waiting = false, playing = false,
                via = "couldn't play this one (${tried.joinToString("; ")}). check your internet, or open it in your music app.")
        }
    }

    /** Plays a track: local files in-process, catalog results through the user's music app. */
    fun play(track: Track, preferredApp: String?) {
        if (track.localUri != null) return playLocal(track)
        stopLocal()
        pendingSearch = track
        _now.value = NowPlaying(track = track, source = Source.APP, appPackage = preferredApp, waiting = true)
        val query = "${track.title} ${track.artist}"
        val extras = Bundle().apply {
            putString(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            putString(MediaStore.EXTRA_MEDIA_TITLE, track.title)
            putString(MediaStore.EXTRA_MEDIA_ARTIST, track.artist)
            if (track.album.isNotBlank()) putString(MediaStore.EXTRA_MEDIA_ALBUM, track.album)
            putString(SearchManager.QUERY, query)
        }
        // Prefer driving an already-alive session: the song starts without the app ever showing up.
        val sessions = if (listening) runCatching { msm.getActiveSessions(listenerComponent) }.getOrDefault(emptyList()) else emptyList()
        val target = sessions.firstOrNull { preferredApp == null || it.packageName == preferredApp }
        val canSearch = target?.playbackState?.actions?.let { it and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L } ?: false
        if (target != null && canSearch) {
            target.transportControls.playFromSearch(query, extras)
        } else {
            val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                putExtras(extras)
                preferredApp?.let { setPackage(it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
        waitJob?.cancel()
        waitJob = scope.launch {
            repeat(40) {
                delay(500)
                pickController()
                if (_now.value?.waiting == false) return@launch
            }
        }
    }

    private fun playLocal(track: Track) {
        request++
        streamJob?.cancel()
        stopLocal()
        _now.value = NowPlaying(track = track, source = Source.LOCAL, waiting = true, via = "this phone")
        playUrl(track, track.localUri!!, Source.LOCAL, onResult = { if (!it) _now.value = null })
    }

    private fun playUrl(track: Track, uri: Uri, source: Source, onResult: (Boolean) -> Unit) {
        stopLocal()
        controller?.transportControls?.pause()
        val p = MediaPlayer()
        player = p
        if (_now.value?.source != source) _now.value = NowPlaying(track = track, source = source, waiting = true)
        p.setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
        p.setOnPreparedListener { it.start(); publishLocal(); onResult(true) }
        p.setOnCompletionListener { publishLocal() }
        p.setOnErrorListener { _, _, _ -> if (player === p) stopLocal(); onResult(false); true }
        val ok = runCatching {
            if (uri.scheme == "http" || uri.scheme == "https") p.setDataSource(uri.toString()) else p.setDataSource(context, uri)
            p.prepareAsync()
        }.isSuccess
        if (!ok) { stopLocal(); onResult(false); return }
        waitJob?.cancel()
        waitJob = scope.launch {
            while (player === p) {
                delay(1000)
                publishLocal()
            }
        }
    }

    private fun publishLocal() {
        val p = player ?: return
        val cur = _now.value ?: return
        _now.value = cur.copy(
            playing = runCatching { p.isPlaying }.getOrDefault(false),
            positionMs = runCatching { p.currentPosition.toLong() }.getOrDefault(0),
            positionStamp = SystemClock.elapsedRealtime(),
            track = cur.track.copy(durationMs = runCatching { p.duration.toLong() }.getOrDefault(cur.track.durationMs)),
            waiting = false,
        )
    }

    private fun stopLocal() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
    }

    fun release() = stopLocal()

    @androidx.annotation.VisibleForTesting
    internal fun seedForPreview(np: NowPlaying?) {
        _now.value = np
    }
}

/** A song URL a source can stream. */
class StreamHit(val url: String, val preview: Boolean, val durationMs: Long = 0)

interface MusicSource {
    val name: String
    suspend fun find(track: Track): StreamHit?
}

/**
 * Free, keyless, legal music APIs, tried in order. Full songs come from Audius (a big indie
 * catalog); for everything else Deezer and Apple give official 30-second previews.
 */
object MusicSources {
    private fun get(url: String): String? = runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "RintOS/1.3")
        c.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun norm(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]|feat\\..*|[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    private fun same(a: String, b: String) = norm(a).let { x -> norm(b).let { y -> x.isNotEmpty() && y.isNotEmpty() && (x.contains(y) || y.contains(x)) } }

    private class AudiusSource(private val strict: Boolean) : MusicSource {
        override val name = if (strict) "Audius (full song)" else "Audius (closest match)"
        override suspend fun find(track: Track): StreamHit? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val host = get("https://api.audius.co")?.let { org.json.JSONObject(it).getJSONArray("data").getString(0) } ?: return@withContext null
            val body = get("$host/v1/tracks/search?query=${enc("${track.title} ${track.artist}")}&app_name=RintOS") ?: return@withContext null
            val arr = org.json.JSONObject(body).getJSONArray("data")
            for (i in 0 until minOf(arr.length(), 12)) {
                val o = arr.getJSONObject(i)
                val title = o.optString("title")
                val user = o.optJSONObject("user")?.optString("name").orEmpty()
                val variant = Regex("remix|mix\\b|cover|edit|bootleg|flip|mashup|sped|slowed|nightcore|instrumental|karaoke|version|rework|vip|piano|acoustic|live|lofi|type beat", RegexOption.IGNORE_CASE)
                if (strict && variant.containsMatchIn(title) && !variant.containsMatchIn(track.title)) continue
                val core = norm(title).replace(norm(track.artist), "").trim()
                val titleOk = if (strict) core == norm(track.title) else same(title, track.title)
                val artistOk = track.artist.isBlank() || same(user, track.artist) || norm(title).contains(norm(track.artist))
                if (titleOk && (artistOk || !strict)) {
                    return@withContext StreamHit("$host/v1/tracks/${o.getString("id")}/stream?app_name=RintOS", preview = false, durationMs = o.optLong("duration") * 1000)
                }
            }
            null
        }
    }

    private object Deezer : MusicSource {
        override val name = "Deezer preview"
        override suspend fun find(track: Track): StreamHit? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val q = if (track.artist.isNotBlank()) "track:\"${track.title}\" artist:\"${track.artist}\"" else track.title
            for (query in listOf(q, "${track.title} ${track.artist}")) {
                val body = get("https://api.deezer.com/search?q=${enc(query)}&limit=8") ?: continue
                val arr = org.json.JSONObject(body).optJSONArray("data") ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val url = o.optString("preview")
                    if (url.isNotBlank() && same(o.optString("title"), track.title)) return@withContext StreamHit(url, preview = true, durationMs = 30_000)
                }
            }
            null
        }
    }

    private object Apple : MusicSource {
        override val name = "Apple preview"
        override suspend fun find(track: Track): StreamHit? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val body = get("https://itunes.apple.com/search?term=${enc("${track.title} ${track.artist}")}&entity=song&limit=5") ?: return@withContext null
            val arr = org.json.JSONObject(body).optJSONArray("results") ?: return@withContext null
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("previewUrl")
                if (url.isNotBlank() && same(o.optString("trackName"), track.title)) return@withContext StreamHit(url, preview = true, durationMs = 30_000)
            }
            null
        }
    }

    val all: List<MusicSource> = listOf(AudiusSource(strict = true), Deezer, Apple, AudiusSource(strict = false))

    /** Full-length tracks from Audius for a free-text query (shown in search as "FULL"). */
    suspend fun audiusSearch(q: String, limit: Int = 6): List<Track> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (q.isBlank()) return@withContext emptyList()
        runCatching {
            val host = get("https://api.audius.co")?.let { org.json.JSONObject(it).getJSONArray("data").getString(0) } ?: return@runCatching emptyList()
            val body = get("$host/v1/tracks/search?query=${enc(q)}&app_name=RintOS") ?: return@runCatching emptyList()
            val arr = org.json.JSONObject(body).getJSONArray("data")
            (0 until minOf(arr.length(), limit)).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                if (o.has("is_streamable") && !o.optBoolean("is_streamable", true)) return@mapNotNull null
                val art = o.optJSONObject("artwork")?.let { a -> a.optString("480x480").ifBlank { a.optString("150x150") } }?.takeIf { it.isNotBlank() }
                Track(
                    title = o.optString("title"),
                    artist = o.optJSONObject("user")?.optString("name").orEmpty(),
                    album = "full song · Audius",
                    durationMs = o.optLong("duration") * 1000,
                    artUrl = art,
                    streamUrl = "$host/v1/tracks/${o.getString("id")}/stream?app_name=RintOS",
                )
            }
        }.getOrDefault(emptyList())
    }
}
