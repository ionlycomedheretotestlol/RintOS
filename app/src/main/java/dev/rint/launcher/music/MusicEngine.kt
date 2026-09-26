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
)

enum class Source { NONE, LOCAL, APP }

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
        if (_now.value?.source == Source.LOCAL && player?.isPlaying == true) return
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

    private fun syncFromController() {
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

    fun toggle() {
        val n = _now.value ?: return
        when (n.source) {
            Source.LOCAL -> player?.let { if (it.isPlaying) it.pause() else it.start(); publishLocal() }
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
            Source.LOCAL -> { player?.seekTo(0); publishLocal() }
            else -> Unit
        }
    }

    fun seekTo(ms: Long) {
        when (_now.value?.source) {
            Source.APP -> controller?.transportControls?.seekTo(ms)
            Source.LOCAL -> { player?.seekTo(ms.toInt()); publishLocal() }
            else -> Unit
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
        stopLocal()
        controller?.transportControls?.pause()
        val p = MediaPlayer()
        player = p
        _now.value = NowPlaying(track = track, source = Source.LOCAL, waiting = true)
        p.setOnPreparedListener { it.start(); publishLocal() }
        p.setOnCompletionListener { publishLocal() }
        p.setOnErrorListener { _, _, _ -> stopLocal(); _now.value = null; true }
        val ok = runCatching { p.setDataSource(context, track.localUri!!); p.prepareAsync() }.isSuccess
        if (!ok) { stopLocal(); _now.value = null; return }
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
