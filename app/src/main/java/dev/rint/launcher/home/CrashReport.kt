package dev.rint.launcher.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.CrashLog
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts

/** Shown once after a crash so the report can be copied or shared. */
@Composable
fun CrashReport(state: LauncherState) {
    val ctx = LocalContext.current
    val look = LocalRint.current
    var text by remember { mutableStateOf(CrashLog.read(ctx)) }
    val report = text ?: return
    fun close() { CrashLog.clear(ctx); text = null }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { MutableInteractionSource() }, null) { close() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(look.colors.panelStrong)
                .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding().padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RinSprite(Pose.SHOCK, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("oops — something broke", fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = look.colors.text)
                    Text("Copy or share this report so it can be fixed.", fontFamily = look.font, fontSize = 13.sp, color = look.colors.subtext)
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.35f))
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)
            ) {
                Text(report, fontFamily = RintFonts.Terminal, fontSize = 13.sp, color = Color(0xFFB8C4FF))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("dismiss") { close() }
                Pill("copy") {
                    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("RintOS crash", report))
                    state.say("copied")
                }
                Pill("share", selected = true) {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                    runCatching { ctx.startActivity(Intent.createChooser(i, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    close()
                }
            }
        }
    }
}
