package dev.rint.launcher.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

val RintJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
    coerceInputValues = true
}

class JsonStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    default: () -> T,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(load() ?: default())
    val state: StateFlow<T> = _state.asStateFlow()
    val value: T get() = _state.value
    private var pending: Job? = null

    private fun load(): T? = runCatching {
        if (file.exists()) RintJson.decodeFromString(serializer, file.readText()) else null
    }.getOrNull()

    fun update(f: (T) -> T) {
        _state.update(f)
        pending?.cancel()
        pending = scope.launch(Dispatchers.IO) {
            delay(250)
            flush()
        }
    }

    fun replace(v: T) = update { v }

    @Synchronized
    fun flush() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RintJson.encodeToString(serializer, _state.value))
        tmp.renameTo(file)
    }

    fun export(): String = RintJson.encodeToString(serializer, _state.value)
    fun import(text: String): Boolean = runCatching {
        replace(RintJson.decodeFromString(serializer, text)); true
    }.getOrDefault(false)
}

enum class ItemKind { APP, WIDGET, SYSTEM_WIDGET }

@Serializable
data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val page: Int,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val kind: ItemKind,
    val app: String? = null,
    val widget: String? = null,
    val sysId: Int = -1,
)

@Serializable
data class HomeLayout(
    val pages: Int = 2,
    val items: List<HomeItem> = emptyList(),
    val dock: List<String> = emptyList(),
    val seeded: Boolean = false,
) {
    fun occupied(page: Int, skipId: String? = null): Array<BooleanArray> {
        val grid = Array(32) { BooleanArray(32) }
        items.filter { it.page == page && it.id != skipId }.forEach { item ->
            for (dx in 0 until item.w) for (dy in 0 until item.h) {
                val cx = item.x + dx
                val cy = item.y + dy
                if (cx in 0..31 && cy in 0..31) grid[cx][cy] = true
            }
        }
        return grid
    }

    fun fits(page: Int, x: Int, y: Int, w: Int, h: Int, cols: Int, rows: Int, skipId: String? = null): Boolean {
        if (x < 0 || y < 0 || x + w > cols || y + h > rows) return false
        val occ = occupied(page, skipId)
        for (dx in 0 until w) for (dy in 0 until h) if (occ[x + dx][y + dy]) return false
        return true
    }

    /** Keeps everything on-screen after the grid shrinks: items that no longer fit get re-placed. */
    fun reflow(cols: Int, rows: Int): HomeLayout {
        if (items.all { it.x + it.w <= cols && it.y + it.h <= rows }) return this
        var out = copy(items = items.filter { it.x + it.w <= cols && it.y + it.h <= rows })
        items.filterNot { it.x + it.w <= cols && it.y + it.h <= rows }.forEach { item ->
            val w = item.w.coerceAtMost(cols)
            val h = item.h.coerceAtMost(rows)
            val spot = out.firstFree(w, h, cols, rows, item.page) ?: out.firstFree(w, h, cols, rows, 0)
            if (spot != null) out = out.copy(
                pages = maxOf(out.pages, spot.first + 1),
                items = out.items + item.copy(page = spot.first, x = spot.second, y = spot.third, w = w, h = h),
            )
        }
        return out
    }

    fun firstFree(w: Int, h: Int, cols: Int, rows: Int, startPage: Int = 0): Triple<Int, Int, Int>? {
        for (p in startPage until pages + 1) {
            for (y in 0..rows - h) for (x in 0..cols - w) {
                if (fits(p, x, y, w, h, cols, rows)) return Triple(p, x, y)
            }
        }
        return null
    }
}

object WidgetStateSerializer : KSerializer<Map<String, String>> by MapSerializer(String.serializer(), String.serializer())

class Stores(dir: File, scope: CoroutineScope) {
    val config = JsonStore(File(dir, "rint_config.json"), RintConfig.serializer(), { RintConfig() }, scope)
    val layout = JsonStore(File(dir, "rint_layout.json"), HomeLayout.serializer(), { HomeLayout() }, scope)
    val widgets = JsonStore(File(dir, "rint_widgets.json"), WidgetStateSerializer, { emptyMap() }, scope)

    /** API keys. Never part of export/import, and the app opts out of cloud backup. */
    val secrets = JsonStore(File(dir, "rint_secrets.json"), WidgetStateSerializer, { emptyMap() }, scope)
    fun secret(name: String): String? = secrets.value[name]?.takeIf { it.isNotBlank() }
    fun setSecret(name: String, v: String) = secrets.update { if (v.isBlank()) it - name else it + (name to v.trim()) }

    fun widgetState(id: String): String? = widgets.value[id]
    fun setWidgetState(id: String, v: String) = widgets.update { it + (id to v) }

    fun flushAll() {
        config.flush(); layout.flush(); widgets.flush(); secrets.flush()
    }
}
