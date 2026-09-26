package dev.rint.launcher.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.Binding
import dev.rint.launcher.core.GestureAction
import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.ItemKind
import dev.rint.launcher.music.MusicOverlay
import dev.rint.launcher.system.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class DragState(
    val item: HomeItem?,
    val appKey: String?,
    val fromDock: Boolean,
    val pos: Offset,
    val size: IntSize,
    val w: Int = item?.w ?: 1,
    val h: Int = item?.h ?: 1,
)

data class AppMenuReq(val key: String, val itemId: String?, val anchor: Rect, val fromDock: Boolean = false)

class LauncherState(val scope: CoroutineScope) {
    val drawer = Animatable(0f)
    var searchFocus by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var settingsSection by mutableStateOf<String?>(null)
    var widgetPicker by mutableStateOf(false)
    var homeMenu by mutableStateOf<Offset?>(null)
    var appMenu by mutableStateOf<AppMenuReq?>(null)
    var widgetMenu by mutableStateOf<HomeItem?>(null)
    var renaming by mutableStateOf<String?>(null)
    var drag by mutableStateOf<DragState?>(null)
    var toast by mutableStateOf<String?>(null)
    var homeVisits by mutableIntStateOf(0)
    var notchExpanded by mutableStateOf(false)
    var gridRect by mutableStateOf(Rect.Zero)
    var dockRect by mutableStateOf(Rect.Zero)
    var pendingPage by mutableStateOf<Int?>(null)
    var guideStep by mutableIntStateOf(-1)
    var currentPage by mutableIntStateOf(0)

    val anyOverlay: Boolean
        get() = drawer.value > 0.01f || settingsOpen || widgetPicker || homeMenu != null || appMenu != null ||
            widgetMenu != null || MusicOverlay.open || notchExpanded || renaming != null

    fun openDrawer(search: Boolean = false) {
        searchFocus = search
        scope.launch { drawer.animateTo(1f, dev.rint.launcher.ui.RintSprings.sheet()) }
    }

    fun closeDrawer() {
        searchFocus = false
        scope.launch { drawer.animateTo(0f, dev.rint.launcher.ui.RintSprings.sheet()) }
    }

    /** Back / home: peel off the top-most layer. Returns true when something was closed. */
    fun dismissTop(): Boolean {
        when {
            renaming != null -> renaming = null
            appMenu != null -> appMenu = null
            widgetMenu != null -> widgetMenu = null
            homeMenu != null -> homeMenu = null
            MusicOverlay.open -> MusicOverlay.open = false
            widgetPicker -> widgetPicker = false
            settingsOpen && settingsSection != null -> settingsSection = null
            settingsOpen -> settingsOpen = false
            notchExpanded -> notchExpanded = false
            drawer.value > 0.01f || drawer.targetValue > 0f -> closeDrawer()
            else -> return false
        }
        return true
    }

    fun say(msg: String) {
        toast = msg
    }

    fun run(ctx: Context, b: Binding) {
        when (b.action) {
            GestureAction.NONE -> Unit
            GestureAction.DRAWER -> openDrawer()
            GestureAction.SEARCH -> openDrawer(search = true)
            GestureAction.NOTIFICATIONS -> SystemActions.expandNotifications(ctx)
            GestureAction.QUICK_SETTINGS -> SystemActions.expandQuickSettings(ctx)
            GestureAction.LOCK -> if (!SystemActions.lock()) {
                say("turn on RintOS gestures in Accessibility to lock with a gesture")
                SystemActions.openAccessibilitySettings(ctx)
            }
            GestureAction.RECENTS -> if (!SystemActions.recents()) say("needs RintOS gestures (Accessibility)")
            GestureAction.SETTINGS -> settingsOpen = true
            GestureAction.FLASHLIGHT -> say(if (SystemActions.toggleFlashlight(ctx)) "flashlight on" else "flashlight off")
            GestureAction.MUSIC -> MusicOverlay.show()
            GestureAction.NOTCH -> notchExpanded = !notchExpanded
            GestureAction.WIDGETS -> widgetPicker = true
            GestureAction.FIRST_PAGE -> pendingPage = 0
            GestureAction.MASCOT -> say("${RintApp.instance.stores.config.value.mascot.name} says hi :3")
            GestureAction.LAUNCH_APP -> RintApp.instance.apps.find(b.app)?.let { RintApp.instance.apps.launch(it) }
                ?: say("pick an app for this gesture in settings")
        }
    }

    fun addWidget(type: String, w: Int, h: Int, page: Int) {
        val stores = RintApp.instance.stores
        val cfg = stores.config.value.home
        val layout = stores.layout.value
        val spot = layout.firstFree(w, h, cfg.columns, cfg.rows, page) ?: layout.firstFree(w, h, cfg.columns, cfg.rows, 0)
        if (spot == null) {
            stores.layout.update { it.copy(pages = it.pages + 1, items = it.items + HomeItem(page = it.pages, x = 0, y = 0, w = w, h = h, kind = ItemKind.WIDGET, widget = type)) }
            pendingPage = layout.pages
        } else {
            val (p, x, y) = spot
            stores.layout.update {
                it.copy(pages = maxOf(it.pages, p + 1), items = it.items + HomeItem(page = p, x = x, y = y, w = w, h = h, kind = ItemKind.WIDGET, widget = type))
            }
            pendingPage = p
        }
        say("widget added")
    }

    fun addAppToHome(key: String, page: Int) {
        val stores = RintApp.instance.stores
        val cfg = stores.config.value.home
        val spot = stores.layout.value.firstFree(1, 1, cfg.columns, cfg.rows, page) ?: return say("no room on this page")
        stores.layout.update { it.copy(pages = maxOf(it.pages, spot.first + 1), items = it.items + HomeItem(page = spot.first, x = spot.second, y = spot.third, kind = ItemKind.APP, app = key)) }
        say("added to home")
    }
}

object SystemSettingsLinks {
    fun defaultHome(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
