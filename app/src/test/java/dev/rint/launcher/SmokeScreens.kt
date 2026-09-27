package dev.rint.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.home.AppMenuReq
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.settings.Schema
import dev.rint.launcher.ui.RintTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/** Renders every screen/menu once so composition-time crashes fail the build. */
class SmokeScreens {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.NoActionBar", maxPercentDifference = 100.0)

    private val registry = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) = Unit
        }
    }

    private fun frame(name: String, cfg: RintConfig = RintConfig(onboarded = true, guideSeen = true), content: @Composable () -> Unit) {
        TestEnv.install(paparazzi.context, cfg)
        dev.rint.launcher.ui.RintSprings.reduce = true
        paparazzi.snapshot(name) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides registry) {
                RintTheme(RintApp.instance.stores.config.value) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF03050B))) { content() }
                }
            }
        }
    }

    private fun st() = LauncherState(CoroutineScope(Dispatchers.Unconfined))

    @Test fun everySettingsSection() {
        Schema.sections.forEach { s ->
            frame("section_${s.id}") { dev.rint.launcher.settings.SettingsScreen(remember { st().apply { settingsOpen = true; settingsSection = s.id } }) }
        }
    }

    @Test fun jokes() {
        dev.rint.launcher.mascot.Joke.entries.forEach { j ->
            frame("joke_" + j.name.lowercase()) {
                dev.rint.launcher.mascot.Chaos.joke = j
                dev.rint.launcher.mascot.JokeOverlay()
            }
        }
        dev.rint.launcher.mascot.Chaos.joke = null
    }

    @Test fun menusAndSheets() {
        frame("home_menu") { val x = remember { st().apply { homeMenu = Offset(300f, 600f) } }; dev.rint.launcher.home.HomeMenu(x) }
        frame("app_menu") {
            val x = remember { st().apply { appMenu = AppMenuReq(RintApp.instance.apps.apps.value[5].key, null, Rect(100f, 400f, 250f, 550f)) } }
            dev.rint.launcher.home.AppMenu(x)
        }
        frame("widget_menu") {
            val item = RintApp.instance.stores.layout.value.items.first()
            dev.rint.launcher.home.WidgetMenu(remember { st().apply { widgetMenu = item } })
        }
        frame("widget_picker") { dev.rint.launcher.home.WidgetPicker(remember { st().apply { widgetPicker = true } }) }
        frame("rename") { dev.rint.launcher.home.RenameDialog(remember { st().apply { renaming = RintApp.instance.apps.apps.value[2].key } }) }
        frame("guide") { dev.rint.launcher.intro.GuideOverlay(remember { st().apply { guideStep = 2 } }) }
        frame("crash_report") {
            dev.rint.launcher.CrashLog.record(paparazzi.context, IllegalStateException("smoke"), fatal = true)
            dev.rint.launcher.home.CrashReport(remember { st() })
        }
    }

    @Test fun drawerOpen() = frame("drawer") {
        val s = remember { st() }
        LaunchedEffect(Unit) { s.drawer.snapTo(1f) }
        dev.rint.launcher.home.AppDrawer(s)
    }

    @Test fun musicSearch() = frame("music_search") {
        dev.rint.launcher.music.MusicOverlay.searchFirst = true
        dev.rint.launcher.music.MusicPlayerScreen(onClose = {})
    }

    @Test fun onboardingScreens() {
        frame("loading") { dev.rint.launcher.intro.LoadingScreen(listOf("a", "b")) { it(0.4f) } }
        frame("permissions") { dev.rint.launcher.intro.Permissions {} }
    }

    /** Everything again with the Terminal preset, which has crashed on a real phone. */
    @Test fun terminalPresetEverywhere() {
        val cfg = dev.rint.launcher.settings.Presets.all.first { it.name == "Terminal" }.apply(RintConfig(onboarded = true, guideSeen = true))
        frame("t_home", cfg) { dev.rint.launcher.home.Launcher(remember { st() }) }
        frame("t_drawer", cfg) { val s = remember { st() }; LaunchedEffect(Unit) { s.drawer.snapTo(1f) }; dev.rint.launcher.home.AppDrawer(s) }
        Schema.sections.forEach { sec -> frame("t_section_${sec.id}", cfg) { dev.rint.launcher.settings.SettingsScreen(remember { st().apply { settingsOpen = true; settingsSection = sec.id } }) } }
        frame("t_settings", cfg) { dev.rint.launcher.settings.SettingsScreen(remember { st().apply { settingsOpen = true } }) }
        frame("t_notch", cfg) { dev.rint.launcher.home.RintNotch(remember { st() }) }
        frame("t_saver", cfg) { dev.rint.launcher.home.SaverHome() }
        frame("t_assistant", cfg) { dev.rint.launcher.assistant.AssistantScreen(remember { st() }) }
        dev.rint.launcher.core.LockStyle.entries.forEach { ls ->
            frame("t_lock_${ls.name}", cfg.copy(lock = cfg.lock.copy(style = ls))) { dev.rint.launcher.lock.LockScreen({}, {}) }
        }
        dev.rint.launcher.widgets.WidgetRegistry.all.forEach { w ->
            w.sizes.forEach { (x, y) ->
                frame("t_widget_${w.type}_${x}x$y", cfg) { w.content(dev.rint.launcher.widgets.WidgetCtx("t_${w.type}", x, y)) }
            }
        }
    }
}
