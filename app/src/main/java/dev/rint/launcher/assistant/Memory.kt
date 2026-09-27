package dev.rint.launcher.assistant

import dev.rint.launcher.RintApp
import dev.rint.launcher.core.JsonStore
import kotlinx.serialization.Serializable
import java.io.File
import java.util.Calendar

@Serializable
data class MemoryFact(val text: String, val at: Long)

@Serializable
data class AppHabit(val label: String, val hours: List<Int> = List(24) { 0 })

@Serializable
data class RinMemoryData(
    val facts: List<MemoryFact> = emptyList(),
    val habits: Map<String, AppHabit> = emptyMap(),
    val lastChat: Long = 0,
)

/**
 * What Rin knows about you. Lives only on this phone (never exported, never backed up):
 * facts you told him, which apps you open at which hours, and when you last talked.
 */
object RinMemory {
    const val MAX_FACTS = 80

    val store: JsonStore<RinMemoryData> by lazy {
        val app = RintApp.instance
        JsonStore(File(app.filesDir, "rin_memory.json"), RinMemoryData.serializer(), { RinMemoryData() }, app.scope)
    }

    private val enabled get() = runCatching { RintApp.instance.stores.config.value.ai.memory }.getOrDefault(false)

    fun remember(text: String): String {
        val t = text.trim().take(300)
        if (t.isEmpty()) return "Nothing to remember."
        store.update { d ->
            val kept = d.facts.filterNot { it.text.equals(t, true) }
            d.copy(facts = (kept + MemoryFact(t, System.currentTimeMillis())).takeLast(MAX_FACTS))
        }
        return "Remembered: $t"
    }

    /** Forgets every fact that contains [about] (case-insensitive). */
    fun forget(about: String): String {
        val q = about.trim()
        if (q.isEmpty()) return "Say what to forget."
        val before = store.value.facts.size
        store.update { d -> d.copy(facts = d.facts.filterNot { it.text.contains(q, true) }) }
        val gone = before - store.value.facts.size
        return if (gone == 0) "Nothing matched \"$q\"." else "Forgot $gone thing(s)."
    }

    fun delete(fact: MemoryFact) = store.update { d -> d.copy(facts = d.facts - fact) }

    fun clearAll() = store.update { RinMemoryData() }

    fun recordLaunch(key: String, label: String) {
        if (!enabled) return
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        store.update { d ->
            val cur = d.habits[key] ?: AppHabit(label)
            val hours = cur.hours.toMutableList().also { if (it.size == 24) it[h] = it[h] + 1 }
            d.copy(habits = d.habits + (key to cur.copy(label = label, hours = hours)))
        }
    }

    fun touchChat() = store.update { it.copy(lastChat = System.currentTimeMillis()) }

    /** The part of the system prompt that makes Rin "know" you. Empty when memory is off. */
    fun promptBlock(): String {
        if (!enabled) return ""
        val d = store.value
        return buildString {
            append("MEMORY: you have a long-term memory on this phone. When the user tells you something lasting about themselves ")
            append("(name, likes, plans, people, routines) or asks you to remember something, call the remember tool; when they ask you to forget, call forget. ")
            append("Use what you remember naturally, without listing it back unless asked.\n")
            if (d.facts.isNotEmpty()) {
                append("Things you remember about the user:\n")
                d.facts.takeLast(40).forEach { append("- ").append(it.text).append('\n') }
            }
            val top = d.habits.values.filter { it.hours.sum() >= 3 }.sortedByDescending { it.hours.sum() }.take(6)
            if (top.isNotEmpty()) {
                append("Their app habits (from launches in RintOS): ")
                append(top.joinToString("; ") { h -> "${h.label} (mostly around ${h.hours.indices.maxBy { h.hours[it] }}h)" })
                append(". Mention these only when it's genuinely useful.\n")
            }
            if (d.lastChat > 0) {
                val days = (System.currentTimeMillis() - d.lastChat) / 86_400_000L
                if (days >= 3) append("You haven't talked in $days days; you can say you missed them.\n")
            }
        }
    }
}
