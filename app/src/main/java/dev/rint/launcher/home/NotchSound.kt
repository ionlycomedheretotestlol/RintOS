package dev.rint.launcher.home

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import dev.rint.launcher.R

/**
 * The notch's open/close sounds. Stays quiet while any music is playing, and when the phone is
 * on silent or vibrate.
 */
object NotchSound {
    private var pool: SoundPool? = null
    private var open = 0
    private var close = 0

    private fun ensure(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        open = p.load(ctx.applicationContext, R.raw.notch_open, 1)
        close = p.load(ctx.applicationContext, R.raw.notch_close, 1)
        pool = p
        return p
    }

    /** Loads the sounds ahead of time so the first tap isn't silent. */
    fun warm(ctx: Context) { runCatching { ensure(ctx) } }

    fun play(ctx: Context, opening: Boolean) {
        runCatching {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (am.isMusicActive || am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val p = ensure(ctx)
            p.play(if (opening) open else close, 0.8f, 0.8f, 1, 0, 1f)
        }
    }
}
