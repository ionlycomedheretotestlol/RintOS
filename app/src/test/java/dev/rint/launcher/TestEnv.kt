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
    // Stand-ins for installed apps (the test machine has none). On a phone RintOS draws the real icons.
    private val labels = listOf(
        Triple("Phone", 0xFF34C759, android.R.drawable.ic_menu_call), Triple("Messages", 0xFF30D158, android.R.drawable.ic_menu_send),
        Triple("Browser", 0xFF0A84FF, android.R.drawable.ic_menu_search), Triple("Camera", 0xFF3A3A3C, android.R.drawable.ic_menu_camera),
        Triple("Calendar", 0xFFFF3B30, android.R.drawable.ic_menu_my_calendar), Triple("Photos", 0xFFFF9F0A, android.R.drawable.ic_menu_gallery),
        Triple("Maps", 0xFF32ADE6, android.R.drawable.ic_menu_mapmode), Triple("Mail", 0xFF0A84FF, android.R.drawable.ic_dialog_email),
        Triple("Notes", 0xFFFFCC00, android.R.drawable.ic_menu_edit), Triple("Clock", 0xFF1C1C1E, android.R.drawable.ic_lock_idle_alarm),
        Triple("Music", 0xFFFF375F, android.R.drawable.ic_media_play), Triple("Files", 0xFF5E5CE6, android.R.drawable.ic_menu_save),
        Triple("Settings", 0xFF636366, android.R.drawable.ic_menu_manage), Triple("Store", 0xFF30B0C7, android.R.drawable.ic_menu_upload),
        Triple("Chat", 0xFFBF5AF2, android.R.drawable.ic_menu_share), Triple("Games", 0xFFFF453A, android.R.drawable.ic_menu_view),
        Triple("Wallet", 0xFF1C1C1E, android.R.drawable.ic_menu_agenda), Triple("Health", 0xFFFF2D55, android.R.drawable.ic_menu_compass),
        Triple("Podcasts", 0xFF9B51E0, android.R.drawable.ic_btn_speak_now), Triple("Books", 0xFFFF9500, android.R.drawable.ic_menu_sort_alphabetically),
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
        val entries = labels.mapIndexed { i, (name, _, _) ->
            AppEntry("fake.$i/.Main", name, name, "fake.$i", ComponentName("fake.$i", "fake.$i.Main"), i.toLong(), android.os.Process.myUserHandle())
        }
        repo.seedForPreview(entries, entries.mapIndexed { i, e -> e.key to fakeIcon(ctx, labels[i].third, labels[i].second.toInt()) }.toMap())
        stores.layout.replace(
            HomeLayout(
                pages = 2, seeded = true, dock = entries.take(4).map { it.key },
                items = listOf(
                    HomeItem(page = 0, x = 0, y = 0, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "clock"),
                    HomeItem(page = 0, x = 2, y = 0, w = 2, h = 2, kind = ItemKind.WIDGET, widget = "music"),
                ) + entries.drop(4).take(16).mapIndexed { i, e -> HomeItem(page = 0, x = i % 4, y = 2 + i / 4, kind = ItemKind.APP, app = e.key) },
            )
        )
        set(app, "stores", stores)
        set(app, "apps", repo)
        set(app, "music", MusicEngine(app, scope))
        set(app, "assistant", dev.rint.launcher.assistant.AgentEngine(app, scope))
        RintApp::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, app)
        return app
    }

    private fun set(o: Any, field: String, v: Any) {
        RintApp::class.java.getDeclaredField(field).apply { isAccessible = true }.set(o, v)
    }

    private fun fakeIcon(ctx: Context, glyph: Int, color: Int): AppIcon {
        val n = 192
        val bg = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        val fg = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        ctx.getDrawable(glyph)?.let { d ->
            d.setTint(android.graphics.Color.WHITE)
            val g = (n * 0.46f).toInt()
            d.setBounds((n - g) / 2, (n - g) / 2, (n + g) / 2, (n + g) / 2)
            d.draw(Canvas(fg))
        }
        val full = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        Canvas(full).apply {
            drawRoundRect(0f, 0f, n.toFloat(), n.toFloat(), 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            drawBitmap(Bitmap.createScaledBitmap(fg, n, n, true), 0f, 0f, null)
        }
        return AppIcon(full.asImageBitmap(), bg.asImageBitmap(), fg.asImageBitmap(), fg.asImageBitmap(), false, color, 0.2f)
    }
}
