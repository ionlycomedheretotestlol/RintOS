package dev.rint.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import dev.rint.launcher.ui.RawText
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.assistant.ChatRole
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.system.BatteryWatch
import dev.rint.launcher.ui.AppIconView
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts

/**
 * Battery saver home: everything collapsed into one dot on black. Tap the dot for a plain
 * app list with Rin in the middle. No widgets, no wallpaper, no ambient animation.
 */
@Composable
fun SaverHome(state: LauncherState? = null) {
    val look = LocalRint.current
    val battery by BatteryWatch.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    BackHandler(open) { open = false }
    val openSettings: () -> Unit = { state?.settingsOpen = true; state?.settingsSection = "power" }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.animation.AnimatedContent(open, label = "saver", transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)) + androidx.compose.animation.scaleIn(initialScale = 0.9f)) togetherWith
                (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.scaleOut(targetScale = 1.08f))
        }) { isOpen ->
            if (!isOpen) SaverDot(battery.level, battery.charging, onOpen = { open = true }, onSettings = openSettings)
            else SaverDrawer(onClose = { open = false }, onSettings = openSettings)
        }
    }
}

/** The dot: breathes slowly, wears the battery as a ring, and a sleepy Rin keeps watch. */
@Composable
private fun SaverDot(level: Int, charging: Boolean, onOpen: () -> Unit, onSettings: () -> Unit) {
    val look = LocalRint.current
    val t = dev.rint.launcher.ui.rememberAmbientClock()
    val breathe = 1f + 0.06f * kotlin.math.sin(t * 1.6f)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val stroke = 4.dp.toPx()
                    drawCircle(look.colors.accent.copy(alpha = 0.12f), radius = size.minDimension / 2 - stroke)
                    drawArc(
                        if (charging) Color(0xFF3DDC84) else look.colors.accent, -90f, 360f * level / 100f, false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                    // a single spark orbiting the ring
                    val a = t * 0.9f
                    val r = size.minDimension / 2 - stroke
                    drawCircle(Color.White.copy(alpha = 0.7f), 2.dp.toPx(), center + androidx.compose.ui.geometry.Offset(kotlin.math.cos(a) * r, kotlin.math.sin(a) * r))
                }
                Box(
                    Modifier.size(64.dp).graphicsLayer { scaleX = breathe; scaleY = breathe }
                        .clip(CircleShape).background(look.colors.accent).clickable { onOpen() }
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("saver mode · $level%", color = Color.White.copy(alpha = 0.5f), fontFamily = RintFonts.Pixel, fontSize = 10.sp)
            Text("tap the dot", color = Color.White.copy(alpha = 0.3f), fontFamily = look.font, fontSize = 12.sp)
        }
        RinSprite(Pose.DROWSY, 56.dp, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SaverChip("Saver Home") { dev.rint.launcher.RintApp.instance.stores.config.update { it.copy(battery = it.battery.copy(saverHome = true)) } }
            SaverChip("settings", onSettings)
        }
    }
}

@Composable
private fun SaverChip(label: String, onClick: () -> Unit) {
    val look = LocalRint.current
    Text(
        label, color = Color.White.copy(alpha = 0.8f), fontFamily = look.font, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
private fun SaverDrawer(onClose: () -> Unit, onSettings: () -> Unit) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val apps by RintApp.instance.apps.apps.collectAsState()
    val engine = RintApp.instance.assistant
    var q by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf("") }
    val shown = remember(apps, q) { if (q.isBlank()) apps else apps.filter { it.label.contains(q, true) } }
    val reply = engine.chat.lastOrNull { it.role == ChatRole.RIN || it.role == ChatRole.ERROR }?.text
    LazyVerticalGrid(
        GridCells.Fixed(4),
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(4) }) {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RinSprite(Pose.HEAD, 72.dp, animated = false)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (engine.busy) engine.status.ifBlank { "thinking…" } else reply ?: "saver mode. ask me anything, I'll keep it light.",
                    color = Color.White, fontFamily = look.font, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (ask.isEmpty()) Text("ask Rin…", color = Color.White.copy(alpha = 0.4f), fontFamily = look.font, fontSize = 15.sp)
                        BasicTextField(
                            ask, { ask = it }, singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontFamily = look.font, fontSize = 15.sp),
                            cursorBrush = SolidColor(look.colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (ask.isNotBlank()) { engine.ask(ask.trim()); ask = "" } }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.05f)).padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (q.isEmpty()) Text("search apps", color = Color.White.copy(alpha = 0.4f), fontFamily = look.font, fontSize = 14.sp)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontFamily = look.font, fontSize = 14.sp),
                            cursorBrush = SolidColor(look.colors.accent), modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("settings", color = Color.White.copy(alpha = 0.6f), fontFamily = look.font, fontSize = 14.sp, modifier = Modifier.clickable { onSettings() })
                    Spacer(Modifier.width(12.dp))
                    Text("close", color = look.colors.accent, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clickable { onClose() })
                }
            }
        }
        items(shown, key = { it.key }) { e ->
            Column(
                Modifier.clickable { launchApp(view, e, androidx.compose.ui.geometry.Rect.Zero) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconView(e.key, 48.dp)
                Spacer(Modifier.height(4.dp))
                RawText(e.label, color = Color.White.copy(alpha = 0.85f), fontFamily = look.font, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
