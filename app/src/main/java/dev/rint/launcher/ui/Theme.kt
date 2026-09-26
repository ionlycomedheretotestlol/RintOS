package dev.rint.launcher.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.rint.launcher.R
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.core.ThemeMode
import dev.rint.launcher.core.UiFont

object RintFonts {
    val Inter = FontFamily(
        Font(R.font.inter_400, FontWeight.Normal),
        Font(R.font.inter_500, FontWeight.Medium),
        Font(R.font.inter_600, FontWeight.SemiBold),
        Font(R.font.inter_700, FontWeight.Bold),
        Font(R.font.inter_800, FontWeight.ExtraBold),
    )
    val Pixel = FontFamily(Font(R.font.silkscreen, FontWeight.Normal), Font(R.font.silkscreen_bold, FontWeight.Bold))
    val Terminal = FontFamily(Font(R.font.vt323))

    fun of(f: UiFont): FontFamily = when (f) {
        UiFont.INTER -> Inter
        UiFont.SYSTEM -> FontFamily.Default
        UiFont.PIXEL -> Pixel
        UiFont.TERMINAL -> Terminal
        UiFont.SERIF -> FontFamily.Serif
        UiFont.MONO -> FontFamily.Monospace
    }
}

@Immutable
data class RintColors(
    val dark: Boolean,
    val accent: Color,
    val onAccent: Color,
    val bg: Color,
    val panel: Color,
    val panelStrong: Color,
    val text: Color,
    val subtext: Color,
    val stroke: Color,
    val danger: Color = Color(0xFFFF5A6A),
)

@Immutable
data class RintLook(
    val cfg: RintConfig,
    val colors: RintColors,
    val font: FontFamily,
)

val LocalRint = staticCompositionLocalOf<RintLook> { error("RintTheme missing") }

/** Same as [LocalRint], but null outside a theme (for widgets that must render anywhere). */
val LocalRintOrNull = staticCompositionLocalOf<RintLook?> { null }

fun Long.color() = Color(this.toInt())
fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun RintTheme(cfg: RintConfig, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val look = remember(cfg, sysDark) {
        val dark = when (cfg.look.theme) {
            ThemeMode.AUTO -> sysDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK, ThemeMode.AMOLED -> true
        }
        val amoled = cfg.look.theme == ThemeMode.AMOLED
        val accent = cfg.look.accent.color()
        val a = cfg.look.panelOpacity
        val colors = if (dark) RintColors(
            dark = true,
            accent = accent,
            onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White,
            bg = if (amoled) Color.Black else Color(0xFF08090E),
            panel = (if (amoled) Color.Black else Color(0xFF1B1E29)).copy(alpha = a),
            panelStrong = if (amoled) Color(0xFF0E0E10) else Color(0xFF16181F),
            text = Color(0xFFF4F6FF),
            subtext = Color(0xFF9A9FAE),
            stroke = Color.White.copy(alpha = 0.14f),
        ) else RintColors(
            dark = false,
            accent = accent,
            onAccent = if (accent.luminance() > 0.55f) Color.Black else Color.White,
            bg = Color(0xFFF3F5FB),
            panel = Color.White.copy(alpha = a),
            panelStrong = Color.White,
            text = Color(0xFF0B0F1C),
            subtext = Color(0xFF5B6380),
            stroke = Color.Black.copy(alpha = 0.08f),
        )
        RintLook(cfg, colors, RintFonts.of(cfg.look.font))
    }
    val scheme = if (look.colors.dark) darkColorScheme(
        primary = look.colors.accent,
        onPrimary = look.colors.onAccent,
        surface = look.colors.panelStrong,
        background = look.colors.bg,
        onSurface = look.colors.text,
        surfaceVariant = look.colors.panelStrong,
    ) else lightColorScheme(
        primary = look.colors.accent,
        onPrimary = look.colors.onAccent,
        surface = look.colors.panelStrong,
        background = look.colors.bg,
        onSurface = look.colors.text,
        surfaceVariant = look.colors.panelStrong,
    )
    val base = TextStyle(fontFamily = look.font, color = look.colors.text)
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            bodyLarge = base.copy(fontSize = 16.sp),
            bodyMedium = base.copy(fontSize = 14.sp),
            bodySmall = base.copy(fontSize = 12.sp),
            titleLarge = base.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        ),
    ) {
        CompositionLocalProvider(LocalRint provides look, LocalRintOrNull provides look) {
            CompositionLocalProvider(LocalBackdrop provides rememberBackdrop(), content = content)
        }
    }
}

object Haptics {
    var enabled = true
    fun tick(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    fun tap(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun heavy(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    fun confirm(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }
}

@Composable
fun rememberHaptic(): View = LocalView.current
