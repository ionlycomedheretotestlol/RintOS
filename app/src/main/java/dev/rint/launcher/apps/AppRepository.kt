package dev.rint.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppIcon(
    val full: ImageBitmap,
    val background: ImageBitmap?,
    val foreground: ImageBitmap?,
    val mono: ImageBitmap?,
    val fromPack: Boolean,
    val dominant: Int,
    val fgCoverage: Float = 1f,
)

data class AppEntry(
    val key: String,
    val label: String,
    val originalLabel: String,
    val packageName: String,
    val component: ComponentName,
    val installTime: Long,
    val user: UserHandle,
)

class AppRepository(private val context: Context, private val scope: CoroutineScope) {
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion
    private val icons = ConcurrentHashMap<String, AppIcon>()
    private val infos = ConcurrentHashMap<String, LauncherActivityInfo>()
    val launchCounts = ConcurrentHashMap<String, Int>()
    var iconPack: IconPack? = null
        private set
    private var renames: Map<String, String> = emptyMap()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(p: String, u: UserHandle) = refresh()
        override fun onPackageAdded(p: String, u: UserHandle) = refresh()
        override fun onPackageChanged(p: String, u: UserHandle) {
            icons.keys.filter { it.startsWith("$p/") }.forEach { icons.remove(it) }
            refresh()
        }
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, r: Boolean) = refresh()
    }

    init {
        launcherApps?.registerCallback(callback)
        refresh()
    }

    fun setRenames(map: Map<String, String>) {
        if (map == renames) return
        renames = map
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val user = Process.myUserHandle()
            val list = (launcherApps ?: return@launch).getActivityList(null, user)
                .filter { it.componentName.packageName != context.packageName }
                .map { info ->
                    val key = info.componentName.flattenToShortString()
                    infos[key] = info
                    val original = info.label.toString()
                    AppEntry(
                        key = key,
                        label = renames[key] ?: original,
                        originalLabel = original,
                        packageName = info.componentName.packageName,
                        component = info.componentName,
                        installTime = runCatching { info.firstInstallTime }.getOrDefault(0L),
                        user = info.user,
                    )
                }
                .sortedBy { it.label.lowercase() }
            _apps.value = list
        }
    }

    fun find(key: String?): AppEntry? = key?.let { k -> _apps.value.firstOrNull { it.key == k } }

    fun setIconPack(pkg: String?) {
        if (pkg == iconPack?.packageName) return
        scope.launch(Dispatchers.IO) {
            iconPack = pkg?.let { runCatching { IconPack.load(context, it) }.getOrNull() }
            icons.clear()
            _iconVersion.value++
        }
    }

    fun cachedIcon(key: String): AppIcon? = icons[key]

    /** Lets screenshot tests render without a real package manager. */
    @androidx.annotation.VisibleForTesting
    internal fun seedForPreview(entries: List<AppEntry>, iconMap: Map<String, AppIcon>) {
        icons.putAll(iconMap)
        _apps.value = entries
    }

    suspend fun icon(key: String): AppIcon? = icons[key] ?: withContext(Dispatchers.IO) {
        val info = infos[key] ?: return@withContext null
        runCatching { buildIcon(info) }.getOrNull()?.also { icons[key] = it }
    }

    private fun buildIcon(info: LauncherActivityInfo): AppIcon {
        val size = 192
        iconPack?.drawableFor(info.componentName)?.let { d ->
            val bmp = render(d, size)
            return AppIcon(bmp.asImageBitmap(), null, null, null, true, dominant(bmp))
        }
        val d = info.getIcon(context.resources.displayMetrics.densityDpi)
        val full = render(d, size)
        if (d is AdaptiveIconDrawable) {
            val bg = d.background?.let { renderLayer(it, size) }
            val fg = d.foreground?.let { renderLayer(it, size) }
            val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome?.let { renderLayer(it, size) } else null
            return AppIcon(full.asImageBitmap(), bg?.asImageBitmap(), fg?.asImageBitmap(), mono?.asImageBitmap(), false, dominant(full), fg?.let { coverage(it) } ?: 1f)
        }
        return AppIcon(full.asImageBitmap(), null, null, null, false, dominant(full))
    }

    private fun render(d: Drawable, size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    // Adaptive layers are 108dp with the visible 72dp in the middle; render full-bleed.
    private fun renderLayer(d: Drawable, size: Int): Bitmap {
        val bmp = createBitmap(size, size)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    private fun coverage(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 18, 18, true)
        var n = 0
        var t = 0
        for (x in 3 until 15) for (y in 3 until 15) {
            t++
            if ((s.getPixel(x, y) ushr 24) > 128) n++
        }
        return n.toFloat() / t
    }

    private fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 8, 8, true)
        var r = 0L; var g = 0L; var bl = 0L; var n = 0L
        for (x in 0 until 8) for (y in 0 until 8) {
            val p = s.getPixel(x, y)
            if ((p ushr 24) < 128) continue
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF; n++
        }
        if (n == 0L) return 0xFF808080.toInt()
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (bl / n).toInt()
    }

    fun launch(entry: AppEntry, bounds: android.graphics.Rect? = null, opts: android.os.Bundle? = null) {
        launchCounts.merge(entry.key, 1, Int::plus)
        runCatching { launcherApps?.startMainActivity(entry.component, entry.user, bounds, opts) }
    }

    fun appInfo(entry: AppEntry) {
        runCatching { launcherApps?.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    fun uninstall(entry: AppEntry) {
        val i = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${entry.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun shortcuts(entry: AppEntry): List<android.content.pm.ShortcutInfo> = runCatching {
        val la = launcherApps ?: return emptyList()
        if (!la.hasShortcutHostPermission()) return emptyList()
        val q = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        la.getShortcuts(q, entry.user).orEmpty().take(4)
    }.getOrDefault(emptyList())

    fun startShortcut(s: android.content.pm.ShortcutInfo) {
        runCatching { launcherApps?.startShortcut(s, null, null) }
    }

    fun defaultDock(): List<String> {
        val pm = context.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val all = _apps.value
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, 0)?.activityInfo?.packageName ?: return@mapNotNull null
            all.firstOrNull { it.packageName == pkg }?.key
        }.distinct()
    }
}
