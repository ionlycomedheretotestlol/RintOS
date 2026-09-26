package dev.rint.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.ItemKind
import kotlin.math.ceil

object SysWidgets {
    const val HOST_ID = 0x52494E54
    const val REQ_BIND = 7101
    const val REQ_CONFIGURE = 7102
    private var hostRef: AppWidgetHost? = null
    private var pending: Pending? = null

    private data class Pending(val id: Int, val info: AppWidgetProviderInfo, val page: Int, val w: Int, val h: Int)

    fun host(ctx: Context): AppWidgetHost = hostRef ?: AppWidgetHost(ctx.applicationContext, HOST_ID).also { hostRef = it }
    fun manager(ctx: Context): AppWidgetManager = AppWidgetManager.getInstance(ctx)

    fun providers(ctx: Context): List<AppWidgetProviderInfo> =
        manager(ctx).installedProviders.sortedWith(compareBy({ it.provider.packageName }, { it.loadLabel(ctx.packageManager) }))

    fun cellsFor(info: AppWidgetProviderInfo, ctx: Context, cols: Int): Pair<Int, Int> {
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, cols) to info.targetCellHeight.coerceIn(1, 6)
        }
        val d = ctx.resources.displayMetrics.density
        val w = ceil((info.minWidth / d + 16) / 80f).toInt().coerceIn(1, cols)
        val h = ceil((info.minHeight / d + 16) / 96f).toInt().coerceIn(1, 6)
        return w to h
    }

    /** Starts the add flow: bind permission → configure → place on home. */
    fun begin(activity: Activity, info: AppWidgetProviderInfo, page: Int, cols: Int) {
        val host = host(activity)
        val id = host.allocateAppWidgetId()
        val (w, h) = cellsFor(info, activity, cols)
        pending = Pending(id, info, page, w, h)
        val ok = manager(activity).bindAppWidgetIdIfAllowed(id, info.provider)
        if (ok) configureOrPlace(activity) else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            @Suppress("DEPRECATION")
            activity.startActivityForResult(i, REQ_BIND)
        }
    }

    private fun configureOrPlace(activity: Activity) {
        val p = pending ?: return
        if (p.info.configure != null) {
            runCatching {
                host(activity).startAppWidgetConfigureActivityForResult(activity, p.id, 0, REQ_CONFIGURE, null)
            }.onFailure { place(activity) }
        } else place(activity)
    }

    private fun place(ctx: Context) {
        val p = pending ?: return
        pending = null
        val stores = RintApp.instance.stores
        val cfg = stores.config.value.home
        val layout = stores.layout.value
        val spot = layout.firstFree(p.w, p.h, cfg.columns, cfg.rows, p.page)
            ?: Triple(layout.pages, 0, 0)
        stores.layout.update {
            it.copy(
                pages = maxOf(it.pages, spot.first + 1),
                items = it.items + HomeItem(page = spot.first, x = spot.second, y = spot.third, w = p.w, h = p.h, kind = ItemKind.SYSTEM_WIDGET, sysId = p.id),
            )
        }
    }

    /** Call from Activity.onActivityResult. */
    fun onResult(activity: Activity, requestCode: Int, resultCode: Int): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        val p = pending ?: return true
        if (resultCode != Activity.RESULT_OK) {
            host(activity).deleteAppWidgetId(p.id)
            pending = null
            return true
        }
        if (requestCode == REQ_BIND) configureOrPlace(activity) else place(activity)
        return true
    }

    fun remove(ctx: Context, id: Int) {
        runCatching { host(ctx).deleteAppWidgetId(id) }
    }
}

@Composable
fun SystemWidgetView(item: HomeItem, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val info = remember(item.sysId) { SysWidgets.manager(ctx).getAppWidgetInfo(item.sysId) } ?: return
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { c ->
            SysWidgets.host(c).createView(c.applicationContext, item.sysId, info).apply {
                setPadding(0, 0, 0, 0)
            }
        },
        update = { v: AppWidgetHostView ->
            val w = width.value
            val h = height.value
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            } else {
                @Suppress("DEPRECATION")
                v.updateAppWidgetSize(null, w.toInt(), h.toInt(), w.toInt(), h.toInt())
            }
        },
    )
}
