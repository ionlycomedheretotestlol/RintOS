package dev.rint.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.rint.launcher.core.AiProvider
import dev.rint.launcher.core.Align
import dev.rint.launcher.core.LockStyle
import dev.rint.launcher.core.UnlockAnim
import dev.rint.launcher.core.VoiceEngine
import dev.rint.launcher.core.Binding
import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.core.DockStyle
import dev.rint.launcher.core.DrawerSort
import dev.rint.launcher.core.DrawerStyle
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.IconStyle
import dev.rint.launcher.core.LabelColor
import dev.rint.launcher.core.LyricsFont
import dev.rint.launcher.core.MascotPresence
import dev.rint.launcher.core.MonoBackground
import dev.rint.launcher.core.NotchContent
import dev.rint.launcher.core.NotchShape
import dev.rint.launcher.core.OpenAnim
import dev.rint.launcher.core.PageIndicator
import dev.rint.launcher.core.PageTransition
import dev.rint.launcher.core.PlayVia
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.SearchBarPos
import dev.rint.launcher.core.SearchBarStyle
import dev.rint.launcher.core.SearchEngine
import dev.rint.launcher.core.ThemeMode
import dev.rint.launcher.core.UiFont
import dev.rint.launcher.core.WallpaperArt
import dev.rint.launcher.core.WallpaperMode

typealias Get<T> = (RintConfig) -> T
typealias Set<T> = (RintConfig, T) -> RintConfig

sealed class Opt(val title: String, val desc: String?) {
    class Toggle(title: String, desc: String? = null, val get: Get<Boolean>, val set: Set<Boolean>) : Opt(title, desc)
    class Slider(
        title: String, desc: String? = null, val range: ClosedFloatingPointRange<Float>, val steps: Int = 0,
        val fmt: (Float) -> String = { "%.0f".format(it) }, val get: Get<Float>, val set: Set<Float>,
    ) : Opt(title, desc)
    class Choice<E : Enum<E>>(title: String, desc: String? = null, val values: List<E>, val label: (E) -> String = { pretty(it.name) }, val get: Get<E>, val set: Set<E>) : Opt(title, desc)
    class ColorPick(title: String, desc: String? = null, val get: Get<Long>, val set: Set<Long>) : Opt(title, desc)
    class TextField(title: String, desc: String? = null, val get: Get<String>, val set: Set<String>) : Opt(title, desc)
    class Gesture(title: String, desc: String? = null, val get: Get<Binding>, val set: Set<Binding>) : Opt(title, desc)
    class Action(title: String, desc: String? = null, val id: String) : Opt(title, desc)
    class GestureList(title: String, desc: String? = null, val max: Int, val get: Get<List<Binding>>, val set: Set<List<Binding>>) : Opt(title, desc)
    class Secret(title: String, desc: String? = null, val name: String) : Opt(title, desc)
    class Header(title: String) : Opt(title, null)
}

class Section(val id: String, val title: String, val blurb: String, val icon: ImageVector, val tint: Color, val opts: List<Opt>)

fun pretty(s: String) = s.lowercase().replace('_', ' ')

private fun pct(f: Float) = "${(f * 100).toInt()}%"
private fun dp(f: Float) = "${f.toInt()}dp"
private fun x(f: Float) = "%.2f×".format(f)

object Schema {
    val sections: List<Section> = listOf(
        Section("look", "Look & feel", "theme, accent, wallpaper, fonts", Icons.Rounded.Palette, Color(0xFF2F6BFF), listOf(
            Opt.Choice("Language", "the whole app, and how Rin talks", dev.rint.launcher.ui.Lang.entries, label = { if (it == dev.rint.launcher.ui.Lang.PT) "Português (Brasil)" else "English (USA)" }, get = { dev.rint.launcher.ui.Lang.of(it.lang) ?: dev.rint.launcher.ui.Lang.EN }, set = { c, v -> c.copy(lang = v.code) }),
            Opt.Choice("Theme", values = ThemeMode.entries, get = { it.look.theme }, set = { c, v -> c.copy(look = c.look.copy(theme = v)) }),
            Opt.ColorPick("Accent color", "used everywhere: buttons, highlights, lyrics", get = { it.look.accent }, set = { c, v -> c.copy(look = c.look.copy(accent = v)) }),
            Opt.Choice("Interface font", values = UiFont.entries, get = { it.look.font }, set = { c, v -> c.copy(look = c.look.copy(font = v)) }),
            Opt.Slider("Corner roundness", range = 0f..44f, fmt = ::dp, get = { it.look.corner }, set = { c, v -> c.copy(look = c.look.copy(corner = v)) }),
            Opt.Slider("Panel opacity", "how see-through menus and widgets are", 0.1f..1f, fmt = ::pct, get = { it.look.panelOpacity }, set = { c, v -> c.copy(look = c.look.copy(panelOpacity = v)) }),
            Opt.Slider("Background blur", "blur behind drawer & overlays (Android 12+)", 0f..80f, fmt = ::dp, get = { it.look.blur }, set = { c, v -> c.copy(look = c.look.copy(blur = v)) }),
            Opt.Toggle("Label shadows", get = { it.look.textShadow }, set = { c, v -> c.copy(look = c.look.copy(textShadow = v)) }),
            Opt.Toggle("Show status bar", get = { it.look.showStatusBar }, set = { c, v -> c.copy(look = c.look.copy(showStatusBar = v)) }),
            Opt.Toggle("Show navigation bar", get = { it.look.showNavBar }, set = { c, v -> c.copy(look = c.look.copy(showNavBar = v)) }),
            Opt.Header("Your own colors"),
            Opt.Toggle("Custom colors", "override the theme's background, panels and text", get = { it.look.customColors }, set = { c, v -> c.copy(look = c.look.copy(customColors = v)) }),
            Opt.ColorPick("Background", get = { it.look.bgColor }, set = { c, v -> c.copy(look = c.look.copy(bgColor = v, customColors = true)) }),
            Opt.ColorPick("Panels & widgets", get = { it.look.panelColor }, set = { c, v -> c.copy(look = c.look.copy(panelColor = v, customColors = true)) }),
            Opt.ColorPick("Text", get = { it.look.textColor }, set = { c, v -> c.copy(look = c.look.copy(textColor = v, customColors = true)) }),
            Opt.ColorPick("Secondary text", get = { it.look.subtextColor }, set = { c, v -> c.copy(look = c.look.copy(subtextColor = v, customColors = true)) }),
            Opt.Slider("Film grain", "a subtle analog texture over the wallpaper", 0f..0.3f, fmt = ::pct, get = { it.look.grain }, set = { c, v -> c.copy(look = c.look.copy(grain = v)) }),
            Opt.ColorPick("Aurora third color", get = { it.look.gradientC }, set = { c, v -> c.copy(look = c.look.copy(gradientC = v)) }),
            Opt.Header("Wallpaper"),
            Opt.Choice("Wallpaper", "system, or one RintOS paints for you", WallpaperMode.entries, get = { it.look.wallpaper }, set = { c, v -> c.copy(look = c.look.copy(wallpaper = v)) }),
            Opt.Choice("Artwork", "RintOS's own wallpapers (used when Wallpaper = art)", WallpaperArt.entries, get = { it.look.art }, set = { c, v -> c.copy(look = c.look.copy(art = v, wallpaper = WallpaperMode.ART)) }),
            Opt.Action("Use my own photo", "pick any picture from your gallery", id = "photo"),
            Opt.Action("Choose a live wallpaper", "any live wallpaper installed on your phone", id = "live"),
            Opt.Action("Pick system wallpaper", id = "wallpaper"),
            Opt.ColorPick("Solid color", get = { it.look.solidColor }, set = { c, v -> c.copy(look = c.look.copy(solidColor = v)) }),
            Opt.ColorPick("Gradient start", get = { it.look.gradientA }, set = { c, v -> c.copy(look = c.look.copy(gradientA = v)) }),
            Opt.ColorPick("Gradient end", get = { it.look.gradientB }, set = { c, v -> c.copy(look = c.look.copy(gradientB = v)) }),
            Opt.Slider("Gradient angle", range = 0f..360f, fmt = { "${it.toInt()}°" }, get = { it.look.gradientAngle }, set = { c, v -> c.copy(look = c.look.copy(gradientAngle = v)) }),
            Opt.Slider("Wallpaper dim", range = 0f..0.85f, fmt = ::pct, get = { it.look.wallpaperDim }, set = { c, v -> c.copy(look = c.look.copy(wallpaperDim = v)) }),
        )),
        Section("icons", "Icons", "shapes, styles, packs, badges", Icons.Rounded.Interests, Color(0xFF00B3FF), listOf(
            Opt.Choice("Icon style", "Rint = the white/blue signature look", IconStyle.entries, get = { it.icons.style }, set = { c, v -> c.copy(icons = c.icons.copy(style = v)) }),
            Opt.Choice("Shape", values = IconShape.entries, get = { it.icons.shape }, set = { c, v -> c.copy(icons = c.icons.copy(shape = v)) }),
            Opt.Choice("Rint background", "behind the glyph in Rint style", MonoBackground.entries, get = { it.icons.monoBg }, set = { c, v -> c.copy(icons = c.icons.copy(monoBg = v)) }),
            Opt.ColorPick("Glyph color", get = { it.icons.monoFg }, set = { c, v -> c.copy(icons = c.icons.copy(monoFg = v)) }),
            Opt.Slider("Icon size", range = 36f..84f, fmt = ::dp, get = { it.icons.size }, set = { c, v -> c.copy(icons = c.icons.copy(size = v)) }),
            Opt.Slider("Inner padding", range = 0f..14f, fmt = ::dp, get = { it.icons.padding }, set = { c, v -> c.copy(icons = c.icons.copy(padding = v)) }),
            Opt.Toggle("Drop shadow", get = { it.icons.shadow }, set = { c, v -> c.copy(icons = c.icons.copy(shadow = v)) }),
            Opt.Slider("Outline width", range = 0f..4f, steps = 7, fmt = ::dp, get = { it.icons.outlineWidth }, set = { c, v -> c.copy(icons = c.icons.copy(outlineWidth = v)) }),
            Opt.ColorPick("Outline color", get = { it.icons.outlineColor }, set = { c, v -> c.copy(icons = c.icons.copy(outlineColor = v)) }),
            Opt.Choice("Press effect", values = PressEffect.entries, get = { it.icons.press }, set = { c, v -> c.copy(icons = c.icons.copy(press = v)) }),
            Opt.Toggle("Notification dots", "needs notification access", get = { it.icons.badges }, set = { c, v -> c.copy(icons = c.icons.copy(badges = v)) }),
            Opt.ColorPick("Dot color", get = { it.icons.badgeColor }, set = { c, v -> c.copy(icons = c.icons.copy(badgeColor = v)) }),
            Opt.Header("Icon packs"),
            Opt.Action("Choose icon pack", "any ADW / Nova compatible pack", id = "iconpack"),
            Opt.Toggle("Shape pack icons too", get = { it.icons.shapePackIcons }, set = { c, v -> c.copy(icons = c.icons.copy(shapePackIcons = v)) }),
        )),
        Section("labels", "Labels", "app names under icons", Icons.Rounded.ShortText, Color(0xFF7C5CFF), listOf(
            Opt.Toggle("Show labels", get = { it.labels.show }, set = { c, v -> c.copy(labels = c.labels.copy(show = v)) }),
            Opt.Slider("Text size", range = 8f..16f, steps = 15, fmt = { "%.1fsp".format(it) }, get = { it.labels.size }, set = { c, v -> c.copy(labels = c.labels.copy(size = v)) }),
            Opt.Choice("Color", values = LabelColor.entries, get = { it.labels.color }, set = { c, v -> c.copy(labels = c.labels.copy(color = v)) }),
            Opt.Slider("Max lines", range = 1f..2f, steps = 0, fmt = { "${it.toInt()}" }, get = { it.labels.maxLines.toFloat() }, set = { c, v -> c.copy(labels = c.labels.copy(maxLines = v.toInt())) }),
            Opt.Toggle("UPPERCASE", get = { it.labels.uppercase }, set = { c, v -> c.copy(labels = c.labels.copy(uppercase = v)) }),
            Opt.Toggle("Bold", get = { it.labels.bold }, set = { c, v -> c.copy(labels = c.labels.copy(bold = v)) }),
        )),
        Section("home", "Home screen", "grid, pages, transitions", Icons.Rounded.GridView, Color(0xFF00C48C), listOf(
            Opt.Slider("Columns", range = 3f..7f, steps = 3, fmt = { "${it.toInt()}" }, get = { it.home.columns.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(columns = v.toInt())) }),
            Opt.Slider("Rows", range = 4f..9f, steps = 4, fmt = { "${it.toInt()}" }, get = { it.home.rows.toFloat() }, set = { c, v -> c.copy(home = c.home.copy(rows = v.toInt())) }),
            Opt.Choice("Page transition", values = PageTransition.entries, get = { it.home.transition }, set = { c, v -> c.copy(home = c.home.copy(transition = v)) }),
            Opt.Choice("Page indicator", values = PageIndicator.entries, get = { it.home.indicator }, set = { c, v -> c.copy(home = c.home.copy(indicator = v)) }),
            Opt.Slider("Side margin", range = 0f..40f, fmt = ::dp, get = { it.home.sideMargin }, set = { c, v -> c.copy(home = c.home.copy(sideMargin = v)) }),
            Opt.Slider("Top margin", range = 0f..80f, fmt = ::dp, get = { it.home.topMargin }, set = { c, v -> c.copy(home = c.home.copy(topMargin = v)) }),
            Opt.Toggle("Lock layout", "stops accidental drags", get = { it.home.lockLayout }, set = { c, v -> c.copy(home = c.home.copy(lockLayout = v)) }),
            Opt.Header("Search bar"),
            Opt.Choice("Position", values = SearchBarPos.entries, get = { it.home.searchBar }, set = { c, v -> c.copy(home = c.home.copy(searchBar = v)) }),
            Opt.Choice("Style", values = SearchBarStyle.entries, get = { it.home.searchStyle }, set = { c, v -> c.copy(home = c.home.copy(searchStyle = v)) }),
            Opt.TextField("Placeholder text", get = { it.home.searchHint }, set = { c, v -> c.copy(home = c.home.copy(searchHint = v)) }),
        )),
        Section("dock", "Dock", "glass, magnify, count", Icons.Rounded.Dock, Color(0xFF1FD1C1), listOf(
            Opt.Toggle("Show dock", get = { it.dock.enabled }, set = { c, v -> c.copy(dock = c.dock.copy(enabled = v)) }),
            Opt.Slider("Icons", range = 1f..7f, steps = 5, fmt = { "${it.toInt()}" }, get = { it.dock.count.toFloat() }, set = { c, v -> c.copy(dock = c.dock.copy(count = v.toInt())) }),
            Opt.Choice("Style", values = DockStyle.entries, get = { it.dock.style }, set = { c, v -> c.copy(dock = c.dock.copy(style = v)) }),
            Opt.Toggle("Magnify on touch", "macOS-style wave when you slide across", get = { it.dock.magnify }, set = { c, v -> c.copy(dock = c.dock.copy(magnify = v)) }),
            Opt.Slider("Height", range = 56f..120f, fmt = ::dp, get = { it.dock.height }, set = { c, v -> c.copy(dock = c.dock.copy(height = v)) }),
            Opt.Slider("Corner", range = 0f..60f, fmt = ::dp, get = { it.dock.corner }, set = { c, v -> c.copy(dock = c.dock.copy(corner = v)) }),
            Opt.Slider("Side margin", range = 0f..48f, fmt = ::dp, get = { it.dock.margin }, set = { c, v -> c.copy(dock = c.dock.copy(margin = v)) }),
            Opt.Slider("Background opacity", range = 0f..1f, fmt = ::pct, get = { it.dock.opacity }, set = { c, v -> c.copy(dock = c.dock.copy(opacity = v)) }),
            Opt.Slider("Icon scale", range = 0.6f..1.4f, fmt = ::x, get = { it.dock.iconScale }, set = { c, v -> c.copy(dock = c.dock.copy(iconScale = v)) }),
            Opt.Toggle("Labels in dock", get = { it.dock.labels }, set = { c, v -> c.copy(dock = c.dock.copy(labels = v)) }),
        )),
        Section("drawer", "App drawer", "layout, sorting, hidden apps", Icons.Rounded.Apps, Color(0xFFFFB020), listOf(
            Opt.Choice("Layout", values = DrawerStyle.entries, get = { it.drawer.style }, set = { c, v -> c.copy(drawer = c.drawer.copy(style = v)) }),
            Opt.Slider("Columns", range = 3f..7f, steps = 3, fmt = { "${it.toInt()}" }, get = { it.drawer.columns.toFloat() }, set = { c, v -> c.copy(drawer = c.drawer.copy(columns = v.toInt())) }),
            Opt.Choice("Sort by", values = DrawerSort.entries, get = { it.drawer.sort }, set = { c, v -> c.copy(drawer = c.drawer.copy(sort = v)) }),
            Opt.Slider("Background opacity", range = 0.2f..1f, fmt = ::pct, get = { it.drawer.opacity }, set = { c, v -> c.copy(drawer = c.drawer.copy(opacity = v)) }),
            Opt.Toggle("Open keyboard automatically", get = { it.drawer.autoKeyboard }, set = { c, v -> c.copy(drawer = c.drawer.copy(autoKeyboard = v)) }),
            Opt.Toggle("Recent apps row", get = { it.drawer.showRecents }, set = { c, v -> c.copy(drawer = c.drawer.copy(showRecents = v)) }),
            Opt.Toggle("Letter headers", get = { it.drawer.headers }, set = { c, v -> c.copy(drawer = c.drawer.copy(headers = v)) }),
            Opt.Action("Hidden apps", "see and unhide", id = "hidden"),
        )),
        Section("search", "Search", "engine, calculator, shortcuts", Icons.Rounded.Search, Color(0xFFFF7A45), listOf(
            Opt.Choice("Web engine", values = SearchEngine.entries, get = { it.search.engine }, set = { c, v -> c.copy(search = c.search.copy(engine = v)) }),
            Opt.TextField("Custom engine URL", "use %s for the query", get = { it.search.customUrl }, set = { c, v -> c.copy(search = c.search.copy(customUrl = v)) }),
            Opt.Toggle("Calculator", "type 12*4+2 and get the answer", get = { it.search.calculator }, set = { c, v -> c.copy(search = c.search.copy(calculator = v)) }),
            Opt.Toggle("Unit converter", "“5 km to mi”, “70 f to c”", get = { it.search.unitConverter }, set = { c, v -> c.copy(search = c.search.copy(unitConverter = v)) }),
            Opt.Toggle("Contacts", get = { it.search.contacts }, set = { c, v -> c.copy(search = c.search.copy(contacts = v)) }),
            Opt.Toggle("Settings shortcuts", get = { it.search.settings }, set = { c, v -> c.copy(search = c.search.copy(settings = v)) }),
            Opt.Toggle("Fuzzy matching", "“yt” finds YouTube", get = { it.search.fuzzy }, set = { c, v -> c.copy(search = c.search.copy(fuzzy = v)) }),
            Opt.Toggle("Enter launches top app", get = { it.search.launchOnEnter }, set = { c, v -> c.copy(search = c.search.copy(launchOnEnter = v)) }),
            Opt.Toggle("Web search fallback", get = { it.search.webFallback }, set = { c, v -> c.copy(search = c.search.copy(webFallback = v)) }),
        )),
        Section("gestures", "Gestures", "swipes, taps, shortcuts", Icons.Rounded.Gesture, Color(0xFFFF4D8D), listOf(
            Opt.Gesture("Swipe up", get = { it.gestures.swipeUp }, set = { c, v -> c.copy(gestures = c.gestures.copy(swipeUp = v)) }),
            Opt.Gesture("Swipe down", get = { it.gestures.swipeDown }, set = { c, v -> c.copy(gestures = c.gestures.copy(swipeDown = v)) }),
            Opt.Gesture("Double tap", get = { it.gestures.doubleTap }, set = { c, v -> c.copy(gestures = c.gestures.copy(doubleTap = v)) }),
            Opt.Gesture("Two-finger swipe down", get = { it.gestures.twoFingerDown }, set = { c, v -> c.copy(gestures = c.gestures.copy(twoFingerDown = v)) }),
            Opt.Gesture("Home button (on home)", get = { it.gestures.homePress }, set = { c, v -> c.copy(gestures = c.gestures.copy(homePress = v)) }),
            Opt.Gesture("Swipe up on dock", get = { it.gestures.dockSwipeUp }, set = { c, v -> c.copy(gestures = c.gestures.copy(dockSwipeUp = v)) }),
            Opt.Gesture("Long-press notch", get = { it.gestures.notchLongPress }, set = { c, v -> c.copy(gestures = c.gestures.copy(notchLongPress = v)) }),
            Opt.Toggle("Haptics", get = { it.gestures.haptics }, set = { c, v -> c.copy(gestures = c.gestures.copy(haptics = v)) }),
            Opt.Action("Enable lock & recents gestures", "turns on the RintOS accessibility service", id = "a11y"),
        )),
        Section("notch", "Notch", "your own dynamic island", Icons.Rounded.ViewDay, Color(0xFF9B8CFF), listOf(
            Opt.Toggle("Show notch", get = { it.notch.enabled }, set = { c, v -> c.copy(notch = c.notch.copy(enabled = v)) }),
            Opt.Choice("Shape", values = NotchShape.entries, get = { it.notch.shape }, set = { c, v -> c.copy(notch = c.notch.copy(shape = v)) }),
            Opt.Slider("Width", range = 40f..260f, fmt = ::dp, get = { it.notch.width }, set = { c, v -> c.copy(notch = c.notch.copy(width = v)) }),
            Opt.Slider("Height", range = 16f..56f, fmt = ::dp, get = { it.notch.height }, set = { c, v -> c.copy(notch = c.notch.copy(height = v)) }),
            Opt.Slider("Distance from top", range = 0f..40f, fmt = ::dp, get = { it.notch.offsetY }, set = { c, v -> c.copy(notch = c.notch.copy(offsetY = v)) }),
            Opt.ColorPick("Color", get = { it.notch.color }, set = { c, v -> c.copy(notch = c.notch.copy(color = v)) }),
            Opt.Toggle("Accent glow", get = { it.notch.glow }, set = { c, v -> c.copy(notch = c.notch.copy(glow = v)) }),
            Opt.Choice("Left side", values = NotchContent.entries, get = { it.notch.left }, set = { c, v -> c.copy(notch = c.notch.copy(left = v)) }),
            Opt.Choice("Right side", values = NotchContent.entries, get = { it.notch.right }, set = { c, v -> c.copy(notch = c.notch.copy(right = v)) }),
            Opt.Toggle("Live activity", "grows with artwork + equalizer while music plays", get = { it.notch.liveActivity }, set = { c, v -> c.copy(notch = c.notch.copy(liveActivity = v)) }),
            Opt.Toggle("Tap to expand", get = { it.notch.expandOnTap }, set = { c, v -> c.copy(notch = c.notch.copy(expandOnTap = v)) }),
        )),
        Section("clock", "Clock", "tty blocks & 6 more faces", Icons.Rounded.Schedule, Color(0xFF4DA3FF), listOf(
            Opt.Choice("Face", values = ClockStyle.entries, get = { it.clock.style }, set = { c, v -> c.copy(clock = c.clock.copy(style = v)) }),
            Opt.Toggle("24-hour", get = { it.clock.use24h }, set = { c, v -> c.copy(clock = c.clock.copy(use24h = v)) }),
            Opt.Toggle("Seconds", get = { it.clock.seconds }, set = { c, v -> c.copy(clock = c.clock.copy(seconds = v)) }),
            Opt.Toggle("Blinking colon", get = { it.clock.blinkColon }, set = { c, v -> c.copy(clock = c.clock.copy(blinkColon = v)) }),
            Opt.Toggle("Show date", get = { it.clock.showDate }, set = { c, v -> c.copy(clock = c.clock.copy(showDate = v)) }),
            Opt.TextField("Date format", "e.g. EEE d MMM · dd/MM/yyyy", get = { it.clock.dateFormat }, set = { c, v -> c.copy(clock = c.clock.copy(dateFormat = v)) }),
            Opt.Toggle("Use accent color", get = { it.clock.useAccent }, set = { c, v -> c.copy(clock = c.clock.copy(useAccent = v)) }),
            Opt.Slider("Size", range = 0.4f..1.4f, fmt = ::x, get = { it.clock.size }, set = { c, v -> c.copy(clock = c.clock.copy(size = v)) }),
            Opt.Toggle("Greeting under the clock", get = { it.clock.greeting }, set = { c, v -> c.copy(clock = c.clock.copy(greeting = v)) }),
            Opt.Choice("Alignment", values = Align.entries, get = { it.clock.align }, set = { c, v -> c.copy(clock = c.clock.copy(align = v)) }),
        )),
        Section("mascot", "Rin", "your little roommate", Icons.Rounded.Pets, Color(0xFF6C8CFF), listOf(
            Opt.Toggle("Rin lives here", get = { it.mascot.enabled }, set = { c, v -> c.copy(mascot = c.mascot.copy(enabled = v)) }),
            Opt.Choice("Art style", "smooth vector art, or the original pixel look", dev.rint.launcher.core.MascotStyle.entries, get = { it.mascot.style }, set = { c, v -> c.copy(mascot = c.mascot.copy(style = v)) }),
            Opt.ColorPick("Ear, tail & zipper color", "defaults to your accent", get = { it.mascot.color ?: it.look.accent }, set = { c, v -> c.copy(mascot = c.mascot.copy(color = v)) }),
            Opt.TextField("Name", get = { it.mascot.name }, set = { c, v -> c.copy(mascot = c.mascot.copy(name = v.take(16))) }),
            Opt.Choice("How often he shows up", values = MascotPresence.entries, get = { it.mascot.presence }, set = { c, v -> c.copy(mascot = c.mascot.copy(presence = v)) }),
            Opt.Slider("Size", range = 0.5f..1.8f, fmt = ::x, get = { it.mascot.size }, set = { c, v -> c.copy(mascot = c.mascot.copy(size = v)) }),
            Opt.Toggle("Says hi each day", get = { it.mascot.greets }, set = { c, v -> c.copy(mascot = c.mascot.copy(greets = v)) }),
            Opt.Toggle("Sleeps at night", get = { it.mascot.sleepsAtNight }, set = { c, v -> c.copy(mascot = c.mascot.copy(sleepsAtNight = v)) }),
            Opt.Toggle("Reacts to charging", get = { it.mascot.reactsToCharging }, set = { c, v -> c.copy(mascot = c.mascot.copy(reactsToCharging = v)) }),
            Opt.Toggle("Head-bobs in music breaks", get = { it.mascot.inMusic }, set = { c, v -> c.copy(mascot = c.mascot.copy(inMusic = v)) }),
        )),
        Section("music", "Rint Music", "lyrics look & playback", Icons.Rounded.MusicNote, Color(0xFFFF5A6A), listOf(
            Opt.Choice("Lyrics font", values = LyricsFont.entries, get = { it.music.lyricsFont }, set = { c, v -> c.copy(music = c.music.copy(lyricsFont = v)) }),
            Opt.Slider("Lyrics size", range = 20f..56f, fmt = { "${it.toInt()}sp" }, get = { it.music.lyricsSize }, set = { c, v -> c.copy(music = c.music.copy(lyricsSize = v)) }),
            Opt.Choice("Alignment", values = Align.entries, get = { it.music.align }, set = { c, v -> c.copy(music = c.music.copy(align = v)) }),
            Opt.Slider("Upcoming lines", range = 0f..4f, steps = 3, fmt = { "${it.toInt()}" }, get = { it.music.upcoming.toFloat() }, set = { c, v -> c.copy(music = c.music.copy(upcoming = v.toInt())) }),
            Opt.ColorPick("Karaoke highlight", get = { it.music.highlight }, set = { c, v -> c.copy(music = c.music.copy(highlight = v)) }),
            Opt.Slider("Background blur", range = 0f..80f, fmt = ::dp, get = { it.music.bgBlur }, set = { c, v -> c.copy(music = c.music.copy(bgBlur = v)) }),
            Opt.Slider("Background dim", range = 0f..0.9f, fmt = ::pct, get = { it.music.bgDim }, set = { c, v -> c.copy(music = c.music.copy(bgDim = v)) }),
            Opt.Toggle("Slow artwork drift", get = { it.music.kenBurns }, set = { c, v -> c.copy(music = c.music.copy(kenBurns = v)) }),
            Opt.Choice("Play songs with", "right here = full songs streamed from Audius", values = PlayVia.entries, label = { when (it) { PlayVia.ASK -> "ask each time"; PlayVia.APP -> "my music app"; PlayVia.LOCAL -> "files on phone"; PlayVia.STREAM -> "right here (free music APIs)" } },
                get = { it.music.playVia }, set = { c, v -> c.copy(music = c.music.copy(playVia = v)) }),
            Opt.Action("Choose music app", id = "musicapp"),
            Opt.Toggle("Lyrics on the widget", get = { it.music.lyricsOnWidget }, set = { c, v -> c.copy(music = c.music.copy(lyricsOnWidget = v)) }),
            Opt.Action("Allow display over other apps", id = "overlay"),
            Opt.Slider("Lyrics offset", "nudge if lyrics run early/late", -3000f..3000f, steps = 23, fmt = { "%+.2fs".format(it / 1000) },
                get = { it.music.offsetMs.toFloat() }, set = { c, v -> c.copy(music = c.music.copy(offsetMs = v.toLong())) }),
        )),
        Section("lock", "Lock screen", "10 styles, 10 shortcuts, unlock effects", Icons.Rounded.Lock, Color(0xFF00B894), listOf(
            Opt.Toggle("RintOS lock screen", "shows over the system lock screen (your PIN/fingerprint still protects the phone). needs the RintOS gesture helper in Accessibility.",
                get = { it.lock.enabled }, set = { c, v -> c.copy(lock = c.lock.copy(enabled = v)) }),
            Opt.Action("Preview it", id = "lockpreview"),
            Opt.Choice("Style", values = LockStyle.entries, get = { it.lock.style }, set = { c, v -> c.copy(lock = c.lock.copy(style = v)) }),
            Opt.GestureList("Shortcuts", "up to 10. camera & flashlight work without unlocking", 10, get = { it.lock.shortcuts }, set = { c, v -> c.copy(lock = c.lock.copy(shortcuts = v)) }),
            Opt.Choice("Unlock effect", values = UnlockAnim.entries, get = { it.lock.unlockAnim }, set = { c, v -> c.copy(lock = c.lock.copy(unlockAnim = v)) }),
            Opt.Toggle("Notification icons", get = { it.lock.notifications }, set = { c, v -> c.copy(lock = c.lock.copy(notifications = v)) }),
            Opt.Toggle("Music controls", get = { it.lock.music }, set = { c, v -> c.copy(lock = c.lock.copy(music = v)) }),
            Opt.Toggle("Rin keeps you company", get = { it.lock.rin }, set = { c, v -> c.copy(lock = c.lock.copy(rin = v)) }),
            Opt.Toggle("Battery", get = { it.lock.battery }, set = { c, v -> c.copy(lock = c.lock.copy(battery = v)) }),
            Opt.Toggle("Accent-colored clock", get = { it.lock.accentClock }, set = { c, v -> c.copy(lock = c.lock.copy(accentClock = v)) }),
            Opt.Slider("Wallpaper dim", range = 0f..0.9f, fmt = ::pct, get = { it.lock.dim }, set = { c, v -> c.copy(lock = c.lock.copy(dim = v)) }),
            Opt.TextField("Message", "e.g. “if found, call 555-0100”", get = { it.lock.message }, set = { c, v -> c.copy(lock = c.lock.copy(message = v.take(80))) }),
        )),
        Section("ai", "Rin assistant", "AI that talks, sees & taps for you", Icons.Rounded.AutoAwesome, Color(0xFF8E7CFF), listOf(
            Opt.Choice("Brain", "which AI powers Rin", AiProvider.entries, label = { when (it) { AiProvider.GEMINI -> "gemini"; AiProvider.GROQ -> "groq"; AiProvider.CLAUDE -> "claude"; AiProvider.OPENROUTER -> "openrouter" } },
                get = { it.ai.provider }, set = { c, v -> c.copy(ai = c.ai.copy(provider = v)) }),
            Opt.Header("API keys (stored only on this phone)"),
            Opt.Secret("Gemini API key", "free at aistudio.google.com (new AQ. keys work) — also unlocks Rin's voice", "GEMINI"),
            Opt.Secret("Groq API key", "console.groq.com", "GROQ"),
            Opt.Secret("Claude API key", "console.anthropic.com", "CLAUDE"),
            Opt.Secret("OpenRouter API key", "openrouter.ai/keys", "OPENROUTER"),
            Opt.Header("Models (type any model name)"),
            Opt.TextField("Gemini model", get = { it.ai.model(AiProvider.GEMINI) }, set = { c, v -> c.copy(ai = c.ai.copy(models = c.ai.models + (AiProvider.GEMINI.name to v.trim()))) }),
            Opt.TextField("Groq model", "pick a vision model so Rin can see screenshots", get = { it.ai.model(AiProvider.GROQ) }, set = { c, v -> c.copy(ai = c.ai.copy(models = c.ai.models + (AiProvider.GROQ.name to v.trim()))) }),
            Opt.TextField("Claude model", get = { it.ai.model(AiProvider.CLAUDE) }, set = { c, v -> c.copy(ai = c.ai.copy(models = c.ai.models + (AiProvider.CLAUDE.name to v.trim()))) }),
            Opt.TextField("OpenRouter model", get = { it.ai.model(AiProvider.OPENROUTER) }, set = { c, v -> c.copy(ai = c.ai.copy(models = c.ai.models + (AiProvider.OPENROUTER.name to v.trim()))) }),
            Opt.Header("Voice"),
            Opt.Toggle("Rin talks out loud", "needs a Gemini API key (free tier)", get = { it.ai.voice }, set = { c, v -> c.copy(ai = c.ai.copy(voice = v)) }),
            Opt.Choice("Voice engine", "Android's built-in voice is the offline fallback", VoiceEngine.entries, get = { it.ai.voiceEngine }, set = { c, v -> c.copy(ai = c.ai.copy(voiceEngine = v)) }),
            Opt.TextField("Gemini voice", "Kore, Puck, Leda, Zephyr, Aoede, Charon…", get = { it.ai.voiceName }, set = { c, v -> c.copy(ai = c.ai.copy(voiceName = v.trim())) }),
            Opt.TextField("Gemini speech model", get = { it.ai.ttsModel }, set = { c, v -> c.copy(ai = c.ai.copy(ttsModel = v.trim())) }),
            Opt.Toggle("Hands-free conversation", "Rin listens again after answering", get = { it.ai.handsFree }, set = { c, v -> c.copy(ai = c.ai.copy(handsFree = v)) }),
            Opt.Action("Test Rin's voice", id = "voicetest"),
            Opt.Header("Phone control"),
            Opt.Toggle("Let Rin operate the phone", "screenshots, taps, typing, swipes — only while doing a task you asked for", get = { it.ai.automation }, set = { c, v -> c.copy(ai = c.ai.copy(automation = v)) }),
            Opt.Toggle("Ask before risky steps", "sending, buying, deleting, posting", get = { it.ai.confirmRisky }, set = { c, v -> c.copy(ai = c.ai.copy(confirmRisky = v)) }),
            Opt.Slider("Max steps per task", range = 5f..60f, steps = 10, fmt = { "${it.toInt()}" }, get = { it.ai.maxSteps.toFloat() }, set = { c, v -> c.copy(ai = c.ai.copy(maxSteps = v.toInt())) }),
            Opt.TextField("Extra personality", "e.g. “answer like a pirate”", get = { it.ai.personality }, set = { c, v -> c.copy(ai = c.ai.copy(personality = v.take(300))) }),
            Opt.Action("Turn on phone control (Accessibility)", id = "a11y"),
            Opt.Action("Make Rin your phone's assistant", "then hold the home button anywhere to talk to him", id = "assistant"),
        )),
        Section("motion", "Motion", "speed, bounce, app opening", Icons.Rounded.Speed, Color(0xFF36D399), listOf(
            Opt.Slider("Animation speed", range = 0.25f..3f, fmt = ::x, get = { it.motion.speed }, set = { c, v -> c.copy(motion = c.motion.copy(speed = v)) }),
            Opt.Slider("Bounciness", "lower = more jelly", 0.15f..1f, fmt = ::pct, get = { it.motion.bounce }, set = { c, v -> c.copy(motion = c.motion.copy(bounce = v)) }),
            Opt.Choice("App open animation", values = OpenAnim.entries, get = { it.motion.openAnim }, set = { c, v -> c.copy(motion = c.motion.copy(openAnim = v)) }),
            Opt.Toggle("Wallpaper parallax", get = { it.motion.parallax }, set = { c, v -> c.copy(motion = c.motion.copy(parallax = v)) }),
            Opt.Toggle("Reduce motion", get = { it.motion.reduce }, set = { c, v -> c.copy(motion = c.motion.copy(reduce = v)) }),
        )),
        Section("system", "Permissions", "default launcher & access", Icons.Rounded.Security, Color(0xFF8A94B8), listOf(
            Opt.Action("Set RintOS as default home", id = "home"),
            Opt.Action("Notification access", "dots + live lyrics from any music app", id = "notif"),
            Opt.Action("Accessibility (lock / recents gestures)", id = "a11y"),
            Opt.Action("Replay the welcome guide", id = "guide"),
            Opt.Action("Replay the intro", id = "intro"),
        )),
        Section("power", "Battery & alerts", "saver mode, serious warnings only", Icons.Rounded.BatteryAlert, Color(0xFF34C759), listOf(
            Opt.Toggle("Battery saver mode", "when battery gets low, home folds into a single dot: no widgets, almost no animation", get = { it.battery.saver }, set = { c, v -> c.copy(battery = c.battery.copy(saver = v)) }),
            Opt.Slider("Turn on at", range = 5f..40f, steps = 6, fmt = { "${it.toInt()}%" }, get = { it.battery.saverAt.toFloat() }, set = { c, v -> c.copy(battery = c.battery.copy(saverAt = v.toInt())) }),
            Opt.Toggle("Rin warns about low battery", "at saver mode, 5% and 1%", get = { it.battery.alerts }, set = { c, v -> c.copy(battery = c.battery.copy(alerts = v)) }),
            Opt.Toggle("Emergency alerts", "Rin pops up for tornado, amber and other emergency broadcasts (needs notification access)", get = { it.battery.emergencyAlerts }, set = { c, v -> c.copy(battery = c.battery.copy(emergencyAlerts = v)) }),
            Opt.Action("Allow Rin's popups over other apps", id = "overlay"),
            Opt.Action("Try saver mode now", id = "saver"),
        )),
        Section("danger", "Dangerous", "do not press. seriously.", Icons.Rounded.Warning, Color(0xFFFF3B30), listOf(
            Opt.Action("Watch Rin play the guitar", "you have been warned", id = "guitar"),
        ) + dev.rint.launcher.mascot.Joke.entries.map { Opt.Action(it.title, it.desc, id = "joke:" + it.name) }),
        Section("backup", "Backup & reset", "export, import, start over", Icons.Rounded.Backup, Color(0xFF5B6380), listOf(
            Opt.Action("Export setup", "copies your whole config + layout", id = "export"),
            Opt.Action("Import setup", "paste an exported setup", id = "import"),
            Opt.Action("Reset look to defaults", id = "resetlook"),
            Opt.Action("Reset everything", id = "resetall"),
        )),
    )

    fun find(id: String?) = sections.firstOrNull { it.id == id }
    val optionCount get() = sections.sumOf { s -> s.opts.count { it !is Opt.Header } }
}
