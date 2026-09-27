package dev.rint.launcher.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.rint.launcher.ui.RawText
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.RintConfig
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintTheme
import dev.rint.launcher.ui.color
import dev.rint.launcher.widgets.ClockFace

/** A self-contained miniature RintOS home screen rendered with [cfg]. */
@Composable
fun MockPhone(
    cfg: RintConfig,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    dockWave: Float = -1f,
    notchOpen: Boolean = false,
    lyric: String? = null,
) {
    val apps by RintApp.instance.apps.apps.collectAsState()
    val sample = remember(apps.size) { apps.shuffled(java.util.Random(3)).take(12) }
    RintTheme(cfg) {
        val look = LocalRint.current
        Box(
            modifier
                .size(width, width * 2.05f)
                .clip(RoundedCornerShape(width * 0.14f))
                .background(Color.Black)
                .border(3.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(width * 0.14f))
                .padding(5.dp)
                .clip(RoundedCornerShape(width * 0.12f))
        ) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0B1226), look.colors.accent.copy(alpha = 0.5f), Color(0xFF05070D)))))
            dev.rint.launcher.home.Wallpaper()
            Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                // notch
                val n = cfg.notch
                if (n.enabled) Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                    Box(
                        Modifier
                            .size(if (notchOpen) width - 30.dp else (n.width * 0.42f).dp, if (notchOpen) 70.dp else (n.height * 0.5f).dp)
                            .clip(RoundedCornerShape(if (notchOpen) 20.dp else 50.dp))
                            .background(n.color.color()),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (notchOpen && lyric != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            RinSprite(Pose.HAPPY, 30.dp)
                            Text(lyric, fontFamily = RintFonts.Terminal, fontSize = 15.sp, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                ClockFace(cfg.clock, Modifier.fillMaxWidth().height(width * 0.26f), compact = true)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(width * 0.3f).clip(RoundedCornerShape((cfg.look.corner * 0.5f).dp)).background(look.colors.panel)) {
                    Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(look.colors.accent))
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text("now playing", fontFamily = RintFonts.Pixel, fontSize = 6.sp, color = look.colors.subtext)
                            RawText(lyric?.let { dev.rint.launcher.ui.I18n.t(it) } ?: dev.rint.launcher.ui.I18n.t("♪ your song here"), fontFamily = RintFonts.Terminal, fontSize = 13.sp, color = look.colors.text, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                val iconSize = width * 0.16f
                sample.take(8).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { AppIconView(it.key, iconSize, icons = cfg.icons, showBadge = false) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (cfg.dock.enabled) Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape((cfg.dock.corner * 0.5f).dp))
                        .background(look.colors.panel.copy(alpha = cfg.dock.opacity)).padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    sample.drop(8).take(4).forEachIndexed { i, e ->
                        val s = if (dockWave >= 0f) 1f + 0.45f * (1f - kotlin.math.abs(dockWave - i) / 1.5f).coerceIn(0f, 1f) else 1f
                        AppIconView(e.key, iconSize, icons = cfg.icons, showBadge = false, modifier = Modifier.graphicsLayer {
                            scaleX = s; scaleY = s; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        })
                    }
                }
            }
        }
    }
}
