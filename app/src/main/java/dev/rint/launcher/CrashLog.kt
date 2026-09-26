package dev.rint.launcher

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on disk so the next launch can show it (and the user can send it).
 * Fatal crashes still let Android restart the launcher as usual.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            record(ctx, e, fatal = true, thread = t.name)
            runCatching { RintApp.instance.stores.flushAll() }
            previous?.uncaughtException(t, e)
        }
    }

    fun record(ctx: Context, e: Throwable, fatal: Boolean, thread: String = Thread.currentThread().name) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
            file(ctx).writeText(buildString {
                appendLine("RintOS $version · ${if (fatal) "crash" else "background error"} · $stamp")
                appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · thread $thread")
                appendLine()
                append(Log.getStackTraceString(e).take(12_000))
            })
        }
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}
