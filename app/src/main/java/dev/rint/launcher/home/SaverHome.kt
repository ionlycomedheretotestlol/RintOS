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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun SaverHome() {
    val look = LocalRint.current
    val battery by BatteryWatch.state.collectAsState()
    var open by remember { mutableStateOf(false) }
    BackHandler(open) { open = false }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!open) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(look.colors.accent).clickable { open = true })
                Spacer(Modifier.height(18.dp))
                Text("saver mode · ${battery.level}%", color = Color.White.copy(alpha = 0.5f), fontFamily = RintFonts.Pixel, fontSize = 10.sp)
                Text("tap the dot", color = Color.White.copy(alpha = 0.3f), fontFamily = look.font, fontSize = 12.sp)
            }
        } else SaverDrawer { open = false }
    }
}

@Composable
private fun SaverDrawer(onClose: () -> Unit) {
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
                Text(e.label, color = Color.White.copy(alpha = 0.85f), fontFamily = look.font, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
