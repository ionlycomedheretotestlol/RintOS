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
    @Test fun intro_0_heart() = film(1.02f)
    @Test fun intro_1_boot() = film(bar * 2 + 3.3f)
    @Test fun intro_2_wordmark() = film(bar * 4 + 2.6f)
    @Test fun intro_3_tagline() = film(bar * 7 + 1.7f)
    @Test fun intro_3b_countdown() = film(bar * 9 + 0.55f)
    @Test fun intro_4_montage_color() = film(bar * 10 + 5.15f)
    @Test fun intro_5_montage_notch() = film(bar * 10 + 13.6f)
    @Test fun intro_6_barrage() = film(bar * 18 + 2.6f)
    @Test fun intro_6b_warp() = film(bar * 21 + 0.8f)
    @Test fun intro_6c_silence() = film(bar * 22 + 0.5f)
    @Test fun intro_7_rin() = film(bar * 23 + 3.0f)
    @Test fun intro_7b_landing() = film(bar * 23 + 0.5f)
    @Test fun intro_8_personalize() = shot { Personalize {} }

    @Test fun home_default() = shot(wallpaperCfg) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun home_terminal_preset() = shot(Presets.all[1].apply(wallpaperCfg)) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

    @Test fun home_candy_preset() = shot(Presets.all[3].apply(wallpaperCfg)) {
        Launcher(remember { LauncherState(CoroutineScope(Dispatchers.Unconfined)) })
    }

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
