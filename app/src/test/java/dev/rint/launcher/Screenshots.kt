package dev.rint.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.WallpaperMode
import dev.rint.launcher.home.Launcher
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.intro.IntroSynth
import dev.rint.launcher.intro.Film
import dev.rint.launcher.intro.Personalize
import dev.rint.launcher.music.MusicPlayerScreen
import dev.rint.launcher.music.NowPlaying
import dev.rint.launcher.music.Source
import dev.rint.launcher.music.Track
import dev.rint.launcher.settings.Presets
import dev.rint.launcher.settings.SettingsScreen
import dev.rint.launcher.ui.Panel
import dev.rint.launcher.ui.RintTheme
import dev.rint.launcher.widgets.WidgetCtx
import dev.rint.launcher.widgets.WidgetRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

class Screenshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private val wallpaperCfg = RintConfig(onboarded = true, guideSeen = true)

    private fun shot(cfg: RintConfig = wallpaperCfg, content: @Composable () -> Unit) {
        TestEnv.install(paparazzi.context, cfg)
        dev.rint.launcher.ui.RintSprings.reduce = true
        paparazzi.snapshot {
            androidx.compose.runtime.CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides fakeRegistry) {
                RintTheme(RintApp.instance.stores.config.value) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF03050B))) { content() }
                }
            }
        }
    }

    private val bar = IntroSynth.BAR.toFloat()

    private val fakeRegistry = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) = Unit
        }
    }

    private fun film(t: Float) = shot { Film { t } }
    @Test fun intro_00_heart() = film(1.02f)
    @Test fun intro_01_boot() = film(bar * 2 + 6.3f)
    @Test fun intro_02_rise() = film(bar * 6 + 4.6f)
    @Test fun intro_02b_rise_assembling() = film(bar * 6 + 1.2f)
    @Test fun intro_07b_tunnel_warp() = film(bar * 24 + 1.5f)
    @Test fun intro_13b_final_3d() = film(bar * 37f)
    @Test fun intro_03_tagline() = film(bar * 11 + 1.7f)
    @Test fun intro_04_countdown() = film(bar * 13 + 0.55f)
    @Test fun intro_05_montage_color() = film(bar * 14 + 5.15f)
    @Test fun intro_06_montage_notch() = film(bar * 14 + 13.6f)
    @Test fun intro_07_tunnel() = film(bar * 22 + 3f)
    @Test fun intro_08_evolution() = film(bar * 26 + 3.2f)
    @Test fun intro_09_barrage() = film(bar * 28 + 1.3f)
    @Test fun intro_10_break() = film(bar * 31 + 1.2f)
    @Test fun intro_11_silence() = film(bar * 34 + 0.5f)
    @Test fun intro_12_landing() = film(bar * 35 + 0.5f)
    @Test fun intro_13_final() = film(bar * 35 + 5f)
    @Test fun intro_14_credits() = film(bar * 41 + 3f)
    @Test fun intro_15_personalize() = shot { Personalize {} }

    @Test fun home_default() = shot(wallpaperCfg) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun home_terminal_preset() = shot(Presets.all[1].apply(wallpaperCfg)) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun home_candy_preset() = shot(Presets.all[3].apply(wallpaperCfg)) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    private fun pt(f: () -> Unit) { dev.rint.launcher.ui.I18n.lang = dev.rint.launcher.ui.Lang.PT; try { f() } finally { dev.rint.launcher.ui.I18n.lang = dev.rint.launcher.ui.Lang.EN } }
    @Test fun pt_home() = pt { shot(wallpaperCfg) { Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) }) } }
    @Test fun pt_settings() = pt { shot { SettingsScreen(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)).apply { settingsOpen = true; settingsSection = "look" } }) } }
    @Test fun pt_intro_boot() = pt { film(bar * 2 + 6.3f) }
    @Test fun pt_intro_tagline() = pt { film(bar * 11 + 1.7f) }

    @androidx.compose.runtime.Composable private fun launcher() = Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    private val base = RintConfig(onboarded = true, guideSeen = true)
    @Test fun clock_analog_4x2() = shot(base.copy(clock = base.clock.copy(style = dev.rint.launcher.core.ClockStyle.ANALOG, greeting = true))) {
        androidx.compose.foundation.layout.Box(Modifier.padding(top = 120.dp).fillMaxSize()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(200.dp)) {
                dev.rint.launcher.widgets.ClockWidget(dev.rint.launcher.widgets.WidgetCtx("c", 4, 2))
            }
        }
    }
    @Test fun readme_pink_hexes() = shot(base.copy(
        look = base.look.copy(accent = 0xFFFF6FB5, wallpaper = dev.rint.launcher.core.WallpaperMode.MESH, gradientA = 0xFF2A0B3D, gradientB = 0xFFFF6FB5, gradientC = 0xFF6A5CFF),
        icons = base.icons.copy(shape = dev.rint.launcher.core.IconShape.HEXAGON, style = dev.rint.launcher.core.IconStyle.RINT, monoBg = dev.rint.launcher.core.MonoBackground.WHITE, monoFg = 0xFFFF6FB5),
        clock = base.clock.copy(style = dev.rint.launcher.core.ClockStyle.WORDS, useAccent = true),
    )) { launcher() }
    @Test fun readme_green_terminal() = shot(base.copy(
        look = base.look.copy(accent = 0xFF3DDC84, font = dev.rint.launcher.core.UiFont.TERMINAL, wallpaper = dev.rint.launcher.core.WallpaperMode.ART, art = dev.rint.launcher.core.WallpaperArt.PIXEL_NIGHT),
        icons = base.icons.copy(shape = dev.rint.launcher.core.IconShape.SQUARE, style = dev.rint.launcher.core.IconStyle.TINTED),
        clock = base.clock.copy(style = dev.rint.launcher.core.ClockStyle.PIXEL, useAccent = true),
    )) { launcher() }
    @Test fun readme_orange_paper() = shot(base.copy(
        look = base.look.copy(accent = 0xFFFF7A45, wallpaper = dev.rint.launcher.core.WallpaperMode.ART, art = dev.rint.launcher.core.WallpaperArt.TIDE),
        icons = base.icons.copy(shape = dev.rint.launcher.core.IconShape.CLOVER),
        clock = base.clock.copy(style = dev.rint.launcher.core.ClockStyle.ANALOG),
    )) { launcher() }

    @Test fun settings_home() = shot {
        SettingsScreen(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)).apply { settingsOpen = true } })
    }

    @Test fun settings_icons() = shot {
        SettingsScreen(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)).apply { settingsOpen = true; settingsSection = "icons" } })
    }

    @Test fun music_player() = shot {
        RintApp.instance.music.seedForPreview(
            NowPlaying(Track("Pixel Heart", "Rin & The Blocks", "Launcher Nights", 214_000), playing = true, positionMs = 61_000, source = Source.APP, appPackage = "fake.4")
        )
        MusicPlayerScreen(onClose = {})
    }

    @Test fun music_lyrics() = shot {
        val np = NowPlaying(Track("Pixel Heart", "Rin & The Blocks", "", 214_000), playing = true, positionMs = 20_600, source = Source.APP)
        RintApp.instance.music.seedForPreview(np)
        val lyrics = dev.rint.launcher.music.Lyrics(
            listOf(
                dev.rint.launcher.music.LyricLine(15_000, "woke up in a grid of blue"),
                dev.rint.launcher.music.LyricLine(19_000, "every pixel points to you"),
                dev.rint.launcher.music.LyricLine(23_000, "swipe it up and make it new"),
                dev.rint.launcher.music.LyricLine(27_000, "this home is mine, it's true"),
            ),
            synced = true, source = "LRCLIB",
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2F6B), Color(0xFF05070D))))) {
            dev.rint.launcher.music.LyricsStage(np.copy(positionMs = 20_600), lyrics, false, 20_600, Modifier.fillMaxSize())
        }
    }

    @Test fun music_break_rin_bobs() = shot {
        val np = NowPlaying(Track("Pixel Heart", "Rin & The Blocks", "", 214_000), playing = true, positionMs = 3_000, source = Source.APP)
        val lyrics = dev.rint.launcher.music.Lyrics(listOf(dev.rint.launcher.music.LyricLine(15_000, "woke up in a grid of blue")), synced = true, source = "LRCLIB")
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2F6B), Color(0xFF05070D))))) {
            dev.rint.launcher.music.LyricsStage(np, lyrics, false, 3_000, Modifier.fillMaxSize())
        }
    }

    @Test fun notch_expanded() = shot {
        RintApp.instance.music.seedForPreview(
            NowPlaying(Track("Pixel Heart", "Rin & The Blocks", "", 214_000), playing = true, positionMs = 61_000, source = Source.APP)
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1226), Color(0xFF1B2F6B))))) {
            dev.rint.launcher.home.RintNotch(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)).apply { notchExpanded = true } }, Modifier.padding(top = 30.dp))
        }
    }

    private fun lock(style: dev.rint.launcher.core.LockStyle) = shot(wallpaperCfg.copy(lock = wallpaperCfg.lock.copy(style = style, message = "if found, call 555-0100"))) {
        RintApp.instance.music.seedForPreview(NowPlaying(Track("Pixel Heart", "Rin & The Blocks", "", 214_000), playing = true, positionMs = 61_000, source = Source.APP))
        dev.rint.launcher.lock.LockScreen(onUnlock = {}, onShortcut = {})
    }

    @Test fun lock_classic() = lock(dev.rint.launcher.core.LockStyle.CLASSIC)
    @Test fun lock_blocks() = lock(dev.rint.launcher.core.LockStyle.BLOCKS)
    @Test fun lock_poster() = lock(dev.rint.launcher.core.LockStyle.POSTER)
    @Test fun lock_terminal() = lock(dev.rint.launcher.core.LockStyle.TERMINAL)
    @Test fun lock_music() = lock(dev.rint.launcher.core.LockStyle.MUSIC)
    @Test fun lock_rin() = lock(dev.rint.launcher.core.LockStyle.RIN)

    @Test fun assistant_chat() = shot {
        val e = RintApp.instance.assistant
        RintApp.instance.stores.setSecret("GEMINI", "test-key")
        e.chat.clear()
        e.chat += dev.rint.launcher.assistant.ChatItem(dev.rint.launcher.assistant.ChatRole.USER, "open YouTube and search lofi beats")
        e.chat += dev.rint.launcher.assistant.ChatItem(dev.rint.launcher.assistant.ChatRole.STEP, "opening YouTube")
        e.chat += dev.rint.launcher.assistant.ChatItem(dev.rint.launcher.assistant.ChatRole.STEP, "tapping element 4")
        e.chat += dev.rint.launcher.assistant.ChatItem(dev.rint.launcher.assistant.ChatRole.STEP, "typing “lofi beats”")
        e.chat += dev.rint.launcher.assistant.ChatItem(dev.rint.launcher.assistant.ChatRole.RIN, "done! lofi beats are up. want me to play the first one?")
        dev.rint.launcher.assistant.AssistantScreen(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun assistant_empty() = shot {
        RintApp.instance.stores.setSecret("GEMINI", "test-key")
        RintApp.instance.assistant.chat.clear()
        dev.rint.launcher.assistant.AssistantScreen(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun widgets_gallery() = shot {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B1226), Color(0xFF1B2F6B)))).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val types = listOf("pet", "todo", "timer", "calc", "calendar", "battery", "dice", "counter", "torch", "weather")
            val specs = types.mapNotNull { WidgetRegistry.find(it) }
            Row(Modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                W(specs[0].type, 2, 2, Modifier.weight(1f)); W("timer", 2, 2, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(250.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                W("todo", 2, 3, Modifier.weight(1f)); W("calc", 2, 3, Modifier.weight(1f))
            }
            W("calendar", 4, 3, Modifier.fillMaxWidth().height(220.dp))
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                W("battery", 2, 1, Modifier.weight(1f)); W("dice", 2, 1, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                W("counter", 2, 1, Modifier.weight(1f)); W("torch", 2, 1, Modifier.weight(1f))
            }
            W("ask", 4, 1, Modifier.fillMaxWidth().height(76.dp))
        }
    }

    @Composable
    private fun W(type: String, w: Int, h: Int, modifier: Modifier) {
        Panel(modifier, shape = RoundedCornerShape(24.dp)) {
            WidgetRegistry.find(type)!!.content(WidgetCtx("shot-$type", w, h))
        }
    }

}
