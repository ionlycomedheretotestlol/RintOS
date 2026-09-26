package dev.rint.launcher

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.asImageBitmap
import dev.rint.launcher.apps.AppEntry
import dev.rint.launcher.apps.AppIcon
import dev.rint.launcher.apps.AppRepository
import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.HomeLayout
import dev.rint.launcher.core.ItemKind
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.Stores
import dev.rint.launcher.music.MusicEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.nio.file.Files

/** Builds a RintApp singleton backed by fake apps so Paparazzi can render real screens. */
object TestEnv {
    private val labels = listOf(
        "Phone" to 0xFF34C759, "Messages" to 0xFF30D158, "Browser" to 0xFF0A84FF, "Camera" to 0xFF8E8E93,
        "Music" to 0xFFFF375F, "Maps" to 0xFF32ADE6, "Photos" to 0xFFFF9F0A, "Mail" to 0xFF5E5CE6,
        "Notes" to 0xFFFFD60A, "Clock" to 0xFF1C1C1E, "Weather" to 0xFF64D2FF, "Files" to 0xFF0A84FF,
        "Settings" to 0xFF636366, "Store" to 0xFF30B0C7, "Chat" to 0xFFBF5AF2, "Games" to 0xFFFF453A,
    )

    fun install(base: Context, cfg: RintConfig = RintConfig(onboarded = true, guideSeen = true)): RintApp {
        val ctx = object : ContextWrapper(base) {
            override fun getSystemService(name: String): Any? = runCatching { super.getSystemService(name) }.getOrNull()
        }
        val app = RintApp()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply { isAccessible = true }.invoke(app, ctx)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dir = Files.createTempDirectory("rint").toFile()
        val stores = Stores(dir, scope)
        stores.config.replace(cfg)
        val repo = AppRepository(app, scope)
        val entries = labels.mapIndexed { i, (name, _) ->
            AppEntry("fake.$i/.Main", name, name, "fake.$i", ComponentName("fake.$i", "fake.$i.Main"), i.toLong(), android.os.Process.myUserHandle())
        }
        repo.seedForPreview(entries, entries.mapIndexed { i, e -> e.key to fakeIcon(labels[i].first, labels[i].second.toInt()) }.toMap())
        stores.layout.replace(
            HomeLayout(
                pages = 2, seeded = true, dock = entries.take(4).map { it.key },
                items = listOf(
                    HomeItem(page = 0, x = 0, y = 0, w = 4, h = 2, kind = ItemKind.WIDGET, widget = "clock"),
                    HomeItem(page = 0, x = 0, y = 2, w = 4, h = 2, kind = ItemKind.WIDGET, widget = "music"),
                ) + entries.drop(4).take(4).mapIndexed { i, e -> HomeItem(page = 0, x = i, y = 5, kind = ItemKind.APP, app = e.key) },
            )
        )
        set(app, "stores", stores)
        set(app, "apps", repo)
        set(app, "music", MusicEngine(app, scope))
        RintApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        return app
    }

    private fun set(o: Any, field: String, v: Any) {
        RintApp::class.java.getDeclaredField(field).apply { isAccessible = true }.set(o, v)
    }

    private fun fakeIcon(label: String, color: Int): AppIcon {
        val n = 192
        val bg = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val fg = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Canvas(fg).drawText(label.take(1), n / 2f, n / 2f + 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE; textSize = 76f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        })
        val full = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Canvas(full).apply {
            drawRoundRect(0f, 0f, n.toFloat(), n.toFloat(), 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            drawBitmap(Bitmap.createScaledBitmap(fg, n, n, true), 0f, 0f, null)
        }
        return AppIcon(full.asImageBitmap(), bg.asImageBitmap(), fg.asImageBitmap(), fg.asImageBitmap(), false, color, 0.2f)
    }
}
