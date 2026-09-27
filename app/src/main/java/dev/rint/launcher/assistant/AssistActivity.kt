package dev.rint.launcher.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.rint.launcher.RintApp
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.RintTheme

/**
 * Rin as the phone's assistant: hold the home button (or swipe from a corner) anywhere and a
 * small sheet slides up over what you're doing. He starts listening right away; what you say
 * appears live, and you can type instead. When he needs to operate the phone, the sheet gets
 * out of the way and his floating bubble takes over.
 */
class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val cfg by RintApp.instance.stores.config.state.collectAsState()
            RintTheme(cfg) { AssistSheet(onClose = { finish() }) }
        }
    }

    override fun onStop() {
        super.onStop()
        RintApp.instance.assistant.voiceIn.stop()
        if (!isChangingConfigurations) finish()
    }
}

@Composable
private fun AssistSheet(onClose: () -> Unit) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val engine = RintApp.instance.assistant
    val speaking by engine.voiceOut.speaking.collectAsState()
    val outLevel by engine.voiceOut.level.collectAsState()
    val listening by engine.voiceIn.listening.collectAsState()
    val inLevel by engine.voiceIn.level.collectAsState()
    val partial by engine.voiceIn.partial.collectAsState()
    var draft by remember { mutableStateOf("") }
    var heard by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    val slide = remember { Animatable(1f) }

    fun listen() {
        engine.voiceOut.stop()
        engine.voiceIn.listen { said -> heard = said; engine.ask(said) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() else typing = true }
    fun mic() {
        if (listening) { engine.voiceIn.stop(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun send() {
        val t = draft.trim()
        if (t.isEmpty()) return
        heard = t; draft = ""
        engine.ask(t)
    }

    LaunchedEffect(Unit) {
        slide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 380f))
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen() else typing = true
    }
    LaunchedEffect(Unit) {
        engine.turnDone.collect {
            if (RintApp.instance.stores.config.value.ai.handsFree && !typing &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) listen()
        }
    }
    // once Rin starts driving the phone, get out of the way (his floating panel takes over)
    LaunchedEffect(engine.operating) { if (engine.operating) onClose() }

    val lastRin = engine.chat.lastOrNull { it.role == ChatRole.RIN || it.role == ChatRole.ERROR }?.text
    val mode = when {
        speaking -> HeadMode.TALKING
        listening -> HeadMode.LISTENING
        engine.busy -> HeadMode.THINKING
        else -> HeadMode.IDLE
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .clickable(remember { MutableInteractionSource() }, null) { engine.voiceIn.stop(); onClose() },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding().padding(10.dp)
                .graphicsLayer { translationY = slide.value * 900f; alpha = 1f - slide.value }
                .clip(RoundedCornerShape(30.dp)).background(look.colors.panelStrong.copy(alpha = 0.97f))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                RinTalkingHead(mode, if (speaking) outLevel else inLevel, 64.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    val you = if (listening && partial.isNotBlank()) partial else heard
                    if (you.isNotBlank()) Text(you, color = look.colors.subtext, fontFamily = look.font, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            listening && partial.isBlank() -> "I'm listening…"
                            engine.busy && lastRin == null -> engine.status.ifBlank { "thinking…" }
                            engine.busy -> engine.status.ifBlank { lastRin ?: "" }
                            lastRin != null && heard.isNotBlank() -> lastRin
                            engine.keyFor() == null -> "add an API key in Settings → Rin assistant and I'm all yours."
                            else -> "hey. what do you need?"
                        },
                        color = look.colors.text, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp,
                    )
                }
                Icon(Icons.Rounded.Close, "close", tint = look.colors.subtext, modifier = Modifier.size(24.dp).clickable { engine.voiceIn.stop(); onClose() })
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(look.colors.text.copy(alpha = 0.07f)).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (draft.isEmpty()) Text("type to Rin", color = look.colors.subtext, fontFamily = look.font, fontSize = 15.sp)
                    BasicTextField(
                        draft, { draft = it; if (it.isNotEmpty()) { typing = true; engine.voiceIn.stop() } },
                        textStyle = TextStyle(color = look.colors.text, fontFamily = look.font, fontSize = 15.sp),
                        cursorBrush = SolidColor(look.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val icon = when { draft.isNotBlank() -> Icons.AutoMirrored.Rounded.Send; engine.busy -> Icons.Rounded.Stop; else -> Icons.Rounded.Mic }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (listening) look.colors.accent else look.colors.text.copy(alpha = 0.1f))
                        .clickable { when { draft.isNotBlank() -> send(); engine.busy -> engine.stop(); else -> mic() } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = if (listening) look.colors.onAccent else look.colors.text, modifier = Modifier.size(22.dp))
                }
            }
            AnimatedVisibility(listening, enter = fadeIn(), exit = fadeOut()) {
                Text("listening · tap the mic to stop", color = look.colors.accent, fontFamily = RintFonts.Pixel, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp, start = 6.dp))
            }
        }
    }
}
