package dev.rint.launcher.home

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import dev.rint.launcher.R

/**
 * The notch's open/close sounds. Plays on the media channel (so Do Not Disturb and "touch sounds
 * off" don't swallow it) and stays quiet only while music is actually playing.
 */
object NotchSound {
    private var pool: SoundPool? = null
    private var open = 0
    private var close = 0
    private val loaded = HashSet<Int>()
    private var pending = 0

    @Synchronized
    private fun ensure(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        p.setOnLoadCompleteListener { sp, id, status ->
            if (status != 0) return@setOnLoadCompleteListener
            synchronized(this) {
                loaded += id
                // a tap that arrived before the sound finished loading still gets played
                if (pending == id) { pending = 0; sp.play(id, 0.9f, 0.9f, 1, 0, 1f) }
            }
        }
        open = p.load(ctx.applicationContext, R.raw.notch_open, 1)
        close = p.load(ctx.applicationContext, R.raw.notch_close, 1)
        pool = p
        return p
    }

    /** Loads the sounds ahead of time so the first tap isn't silent. */
    fun warm(ctx: Context) { runCatching { ensure(ctx) } }

    fun play(ctx: Context, opening: Boolean) {
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am?.isMusicActive == true) return
            val p = ensure(ctx)
            val id = if (opening) open else close
            synchronized(this) {
                if (id in loaded) p.play(id, 0.9f, 0.9f, 1, 0, 1f) else pending = id
            }
        }
    }
}
