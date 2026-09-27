package dev.rint.launcher.settings

import dev.rint.launcher.core.ClockStyle
import dev.rint.launcher.core.DockStyle
import dev.rint.launcher.core.IconShape
import dev.rint.launcher.core.IconStyle
import dev.rint.launcher.core.LabelColor
import dev.rint.launcher.core.LyricsFont
import dev.rint.launcher.core.MonoBackground
import dev.rint.launcher.core.NotchShape
import dev.rint.launcher.core.PageIndicator
import dev.rint.launcher.core.PageTransition
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.core.RINT_BLUE
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.SearchBarPos
import dev.rint.launcher.core.SearchBarStyle
import dev.rint.launcher.core.ThemeMode
import dev.rint.launcher.core.UiFont
import dev.rint.launcher.core.WallpaperMode

class Preset(val name: String, val tagline: String, val swatch: List<Long>, val apply: (RintConfig) -> RintConfig)

/** One-tap starting points. They only touch looks, never your layout, apps or gestures. */
object Presets {
    private fun base(c: RintConfig) = RintConfig(
        lang = c.lang, onboarded = c.onboarded, guideSeen = c.guideSeen, hiddenApps = c.hiddenApps, renamedApps = c.renamedApps,
        gestures = c.gestures, search = c.search, music = c.music.copy(lyricsFont = LyricsFont.TERMINAL), mascot = c.mascot,
        home = RintConfig().home.copy(columns = c.home.columns, rows = c.home.rows, lockLayout = c.home.lockLayout),
        dock = RintConfig().dock.copy(count = c.dock.count),
    )

    val all = listOf(
        Preset("Rint", "white, blue, black. the classic.", listOf(0xFFFFFFFFL, RINT_BLUE, 0xFF0A0E1EL)) { base(it) },
        Preset("Terminal", "green phosphor, blocky everything", listOf(0xFF000000L, 0xFF39FF88L, 0xFF0E2A18L)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(theme = ThemeMode.AMOLED, accent = 0xFF39FF88L, font = UiFont.TERMINAL, corner = 4f, wallpaper = WallpaperMode.SOLID, solidColor = 0xFF000000L),
                    icons = b.icons.copy(style = IconStyle.OUTLINE, shape = IconShape.SQUARE, monoFg = 0xFF39FF88L, shadow = false, press = PressEffect.GLOW, badgeColor = 0xFF39FF88L),
                    labels = b.labels.copy(uppercase = true, color = LabelColor.ACCENT, size = 10f),
                    home = b.home.copy(searchStyle = SearchBarStyle.TERMINAL, searchHint = "run…", transition = PageTransition.FADE, indicator = PageIndicator.NUMBERS),
                    dock = b.dock.copy(style = DockStyle.LINE, magnify = false),
                    clock = b.clock.copy(style = ClockStyle.BLOCKS, useAccent = true, seconds = true),
                    notch = b.notch.copy(shape = NotchShape.TAB, glow = true),
                )
            }
        },
        Preset("Paper", "light, calm, ink icons", listOf(0xFFF4F1EAL, 0xFF111111L, 0xFFE0DBD0L)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(theme = ThemeMode.LIGHT, accent = 0xFF111111L, font = UiFont.SERIF, wallpaper = WallpaperMode.SOLID, solidColor = 0xFFF4F1EAL, textShadow = false),
                    icons = b.icons.copy(style = IconStyle.RINT, monoBg = MonoBackground.NONE, monoFg = 0xFF111111L, shadow = false, shape = IconShape.CIRCLE),
                    labels = b.labels.copy(color = LabelColor.BLACK),
                    clock = b.clock.copy(style = ClockStyle.WORDS, useAccent = true),
                    dock = b.dock.copy(style = DockStyle.NONE),
                    notch = b.notch.copy(color = 0xFF111111L, glow = false),
                )
            }
        },
        Preset("Candy", "pink, round, extremely bouncy", listOf(0xFFFF6FB5L, 0xFFFFD6EBL, 0xFF7A3CFFL)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(accent = 0xFFFF6FB5L, corner = 34f, wallpaper = WallpaperMode.MESH, gradientA = 0xFF2A1238L, gradientB = 0xFF7A3CFFL),
                    icons = b.icons.copy(shape = IconShape.PEBBLE, monoBg = MonoBackground.WHITE, monoFg = 0xFFFF6FB5L, press = PressEffect.WOBBLE),
                    home = b.home.copy(transition = PageTransition.CAROUSEL, indicator = PageIndicator.PAW, searchStyle = SearchBarStyle.PILL),
                    clock = b.clock.copy(style = ClockStyle.STACKED),
                    motion = b.motion.copy(bounce = 0.3f),
                    notch = b.notch.copy(shape = NotchShape.PILL),
                )
            }
        },
        Preset("Glass", "see-through, floaty, moving colors", listOf(0x66FFFFFFL, 0xFF5AC8FAL, 0xFF1B2F6BL)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(accent = 0xFF5AC8FAL, panelOpacity = 0.35f, blur = 60f, wallpaper = WallpaperMode.MESH, gradientA = 0xFF06101FL, gradientB = 0xFF1B2F6BL),
                    icons = b.icons.copy(monoBg = MonoBackground.GLASS, shape = IconShape.SQUIRCLE, outlineWidth = 1f, outlineColor = 0x55FFFFFFL),
                    dock = b.dock.copy(style = DockStyle.GLASS, opacity = 0.3f),
                    home = b.home.copy(transition = PageTransition.CUBE),
                    clock = b.clock.copy(style = ClockStyle.THIN, align = dev.rint.launcher.core.Align.CENTER),
                )
            }
        },
        Preset("Minimal", "no labels, no dock, just calm", listOf(0xFF000000L, 0xFFFFFFFFL, 0xFF444444L)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(theme = ThemeMode.AMOLED, accent = 0xFFFFFFFFL),
                    icons = b.icons.copy(style = IconStyle.GRAYSCALE, shape = IconShape.CIRCLE, shadow = false, size = 50f),
                    labels = b.labels.copy(show = false),
                    dock = b.dock.copy(enabled = false),
                    home = b.home.copy(searchBar = SearchBarPos.HIDDEN, indicator = PageIndicator.LINE),
                    clock = b.clock.copy(style = ClockStyle.THIN, showDate = true, align = dev.rint.launcher.core.Align.CENTER),
                    notch = b.notch.copy(shape = NotchShape.DOT, glow = false),
                )
            }
        },
        Preset("Retro", "pixel fonts, hexes, orange CRT", listOf(0xFF1A0F05L, 0xFFFF9F1CL, 0xFFFFE8C2L)) { c ->
            base(c).let { b ->
                b.copy(
                    look = b.look.copy(accent = 0xFFFF9F1CL, font = UiFont.PIXEL, corner = 8f, wallpaper = WallpaperMode.GRADIENT, gradientA = 0xFF1A0F05L, gradientB = 0xFF4A2408L),
                    icons = b.icons.copy(shape = IconShape.HEXAGON, monoBg = MonoBackground.BLACK, monoFg = 0xFFFF9F1CL, press = PressEffect.SHRINK),
                    labels = b.labels.copy(size = 9f, uppercase = true),
                    clock = b.clock.copy(style = ClockStyle.PIXEL, useAccent = true),
                    home = b.home.copy(transition = PageTransition.TILT, searchStyle = SearchBarStyle.TERMINAL),
                )
            }
        },
    )
}
