package dev.rint.launcher.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PlusOne
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.rint.launcher.RintApp
import dev.rint.launcher.music.MusicWidget

class WidgetCtx(val id: String, val w: Int, val h: Int, val preview: Boolean = false) {
    private val store get() = RintApp.instance.stores
    fun get(key: String): String? = store.widgetState("$id.$key")
    fun set(key: String, v: String) = store.setWidgetState("$id.$key", v)
}

@Composable
fun WidgetCtx.state(key: String, default: String): String {
    val all by RintApp.instance.stores.widgets.state.collectAsState()
    return all["$id.$key"] ?: default
}

class WidgetSpec(
    val type: String,
    val name: String,
    val blurb: String,
    val icon: ImageVector,
    val sizes: List<Pair<Int, Int>>,
    val content: @Composable (WidgetCtx) -> Unit,
) {
    val default get() = sizes.first()
}

object WidgetRegistry {
    val all: List<WidgetSpec> = listOf(
        WidgetSpec("clock", "Clock", "Blocky tty clock and 6 other faces", Icons.Rounded.Schedule, listOf(4 to 2, 4 to 1, 2 to 2, 4 to 3)) { ClockWidget(it) },
        WidgetSpec("music", "Rint Music", "A real player with live synced lyrics", Icons.Rounded.MusicNote, listOf(4 to 2, 4 to 3, 2 to 2)) { MusicWidget(it) },
        WidgetSpec("pet", "Rin", "Pet him. Feed him. He remembers.", Icons.Rounded.Pets, listOf(2 to 2, 4 to 2)) { PetWidget(it) },
        WidgetSpec("notes", "Sticky note", "Type right on your home screen", Icons.Rounded.StickyNote2, listOf(2 to 2, 4 to 2, 2 to 3, 4 to 3)) { NotesWidget(it) },
        WidgetSpec("todo", "Checklist", "Tick things off without opening an app", Icons.Rounded.Checklist, listOf(2 to 3, 4 to 3, 2 to 2, 4 to 4)) { ChecklistWidget(it) },
        WidgetSpec("timer", "Focus timer", "Pomodoro with a ring you can spin", Icons.Rounded.Timer, listOf(2 to 2, 4 to 2)) { TimerWidget(it) },
        WidgetSpec("calc", "Calculator", "A whole calculator. On your home screen.", Icons.Rounded.Calculate, listOf(4 to 3, 4 to 4)) { CalculatorWidget(it) },
        WidgetSpec("counter", "Tally", "Count anything: water, reps, days", Icons.Rounded.PlusOne, listOf(2 to 1, 2 to 2)) { CounterWidget(it) },
        WidgetSpec("dice", "Dice & coin", "Settle arguments fast", Icons.Rounded.Casino, listOf(1 to 1, 2 to 1, 2 to 2)) { DiceWidget(it) },
        WidgetSpec("torch", "Flashlight", "One tap torch", Icons.Rounded.FlashlightOn, listOf(1 to 1, 2 to 1)) { TorchWidget(it) },
        WidgetSpec("battery", "Battery", "Charge ring with time estimate", Icons.Rounded.Battery5Bar, listOf(2 to 1, 2 to 2, 1 to 1)) { BatteryWidget(it) },
        WidgetSpec("calendar", "Month", "This month at a glance", Icons.Rounded.CalendarMonth, listOf(4 to 3, 2 to 2, 4 to 2)) { CalendarWidget(it) },
        WidgetSpec("weather", "Weather", "Open-Meteo, no account needed", Icons.Rounded.Cloud, listOf(4 to 2, 2 to 2, 4 to 1)) { WeatherWidget(it) },
    )

    fun find(type: String?) = all.firstOrNull { it.type == type }
}
