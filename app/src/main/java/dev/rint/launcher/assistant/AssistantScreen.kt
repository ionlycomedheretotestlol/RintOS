package dev.rint.launcher.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import dev.rint.launcher.ui.RawText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.PressEffect
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.pressable
import dev.rint.launcher.ui.rememberAmbientClock
import dev.rint.launcher.ui.rememberHaptic
import dev.rint.launcher.ui.glass
import kotlin.math.sin

enum class HeadMode { IDLE, LISTENING, THINKING, TALKING }

/** Rin's head, alive: lip-flaps to real audio loudness, glows while listening, side-eyes while thinking. */
@Composable
fun RinTalkingHead(mode: HeadMode, level: Float, size: Dp, modifier: Modifier = Modifier) {
    val accent = LocalRint.current.colors.accent
    val t = rememberAmbientClock()
    val lv by animateFloatAsState(level, label = "lv")
    val pose = when (mode) {
        HeadMode.TALKING -> Pose.TALK
        HeadMode.THINKING -> Pose.THINK
        HeadMode.LISTENING -> Pose.LISTEN
        HeadMode.IDLE -> Pose.HEAD
    }
    Box(modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val base = this.size.minDimension * 0.32f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f + 0.3f * lv), Color.Transparent), c, base * 1.6f), base * 1.6f, c)
            when (mode) {
                HeadMode.TALKING -> for (i in 0 until 3) {
                    val p = ((t * 1.4f + i / 3f) % 1f)
                    drawCircle(accent.copy(alpha = (1f - p) * 0.5f * (0.3f + lv)), base * (1f + p * 0.7f), c, style = Stroke(4f + 6f * lv))
                }
                HeadMode.LISTENING -> drawCircle(accent.copy(alpha = 0.7f), base * (1.05f + 0.35f * lv + 0.04f * sin(t * 5f)), c, style = Stroke(6f))
                HeadMode.THINKING -> for (i in 0 until 3) {
                    val a = t * 3f + i * 2.09f
                    drawCircle(Color.White.copy(alpha = 0.8f), 6f, Offset(c.x + kotlin.math.cos(a) * base * 1.2f, c.y + sin(a) * base * 1.2f))
                }
                HeadMode.IDLE -> Unit
            }
        }
        // the rig lip-syncs to [lv] itself; the layer just adds a little bounce
        RinSprite(pose, size, Modifier.graphicsLayer { translationY = -lv * 6f }, talk = lv)
    }
}

private val suggestions = listOf(
    "what's on my screen?",
    "open YouTube and search lofi beats",
    "make my launcher pink and bouncy",
    "play something chill",
    "turn on the Terminal preset",
    "tell me a fun fact",
)

@Composable
fun AssistantScreen(state: LauncherState) {
    val look = LocalRint.current
    val ctx = LocalContext.current
    val engine = RintApp.instance.assistant
    val v = rememberHaptic()
    val ai = look.cfg.ai
    val speaking by engine.voiceOut.speaking.collectAsState()
    val outLevel by engine.voiceOut.level.collectAsState()
    val listening by engine.voiceIn.listening.collectAsState()
    val inLevel by engine.voiceIn.level.collectAsState()
    val partial by engine.voiceIn.partial.collectAsState()
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val hasKey = engine.keyFor() != null
    BackHandler { engine.voiceIn.stop(); AssistantOverlay.open = false }

    fun listen() {
        engine.voiceOut.stop()
        engine.voiceIn.listen { heard -> engine.ask(heard) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) listen() }
    fun mic() {
        Haptics.tap(v)
        if (listening) { engine.voiceIn.stop(); return }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) {
        engine.turnDone.collect {
            if (RintApp.instance.stores.config.value.ai.handsFree && AssistantOverlay.open &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) listen()
        }
    }
    LaunchedEffect(engine.chat.size) { if (engine.chat.isNotEmpty()) list.animateScrollToItem(engine.chat.lastIndex) }

    val mode = when {
        speaking -> HeadMode.TALKING
        listening -> HeadMode.LISTENING
        engine.busy -> HeadMode.THINKING
        else -> HeadMode.IDLE
    }
    val headSize by animateDpAsState(if (engine.chat.size > 2) 84.dp else 130.dp, label = "hs")

    Box(
        Modifier.fillMaxSize()
            .then(
                if (dev.rint.launcher.ui.LocalBackdrop.current != null)
                    Modifier.glass(androidx.compose.ui.graphics.RectangleShape, tint = 2.3f, edge = false)
                else Modifier.background(look.colors.bg)
            )
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.KeyboardArrowDown, "close", tint = Color.White, modifier = Modifier.size(34.dp).pressable(PressEffect.BOUNCE) { engine.voiceIn.stop(); AssistantOverlay.open = false })
                Spacer(Modifier.weight(1f))
                Text("${ai.provider.name.lowercase()} · ${ai.model()}", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.AddComment, "new chat", tint = Color.White, modifier = Modifier.size(26.dp).pressable(PressEffect.BOUNCE) { engine.reset() })
                Spacer(Modifier.size(14.dp))
                Icon(Icons.Rounded.Tune, "settings", tint = Color.White, modifier = Modifier.size(26.dp).pressable(PressEffect.BOUNCE) {
                    AssistantOverlay.open = false; state.settingsSection = "ai"; state.settingsOpen = true
                })
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RinTalkingHead(mode, if (speaking) outLevel else inLevel, headSize)
                Text(
                    when {
                        listening -> partial.ifBlank { "listening…" }
                        speaking -> "talking"
                        engine.busy -> engine.status.ifBlank { "thinking…" }
                        else -> "hi, I'm ${look.cfg.mascot.name}. ask me anything"
                    },
                    fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 2,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasKey) item { SetupCard(state) }
                if (engine.chat.isEmpty() && hasKey) item {
                    Column {
                        Text("TRY", fontFamily = RintFonts.Pixel, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                        Spacer(Modifier.height(8.dp))
                        suggestions.chunked(2).forEach { row ->
                            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { s -> Pill(s) { engine.ask(s) } } }
                        }
                    }
                }
                itemsIndexed(engine.chat) { _, item -> ChatBubble(item) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (draft.isEmpty()) Text("ask ${look.cfg.mascot.name}…", color = Color.White.copy(alpha = 0.45f), fontFamily = look.font, fontSize = 16.sp)
                        BasicTextField(
                            draft, { draft = it },
                            textStyle = TextStyle(color = Color.White, fontFamily = look.font, fontSize = 16.sp),
                            cursorBrush = SolidColor(look.colors.accent),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { engine.ask(draft.trim()); draft = "" }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (draft.isNotBlank()) Icon(Icons.AutoMirrored.Rounded.Send, "send", tint = look.colors.accent,
                        modifier = Modifier.size(24.dp).pressable(PressEffect.BOUNCE) { engine.ask(draft.trim()); draft = "" })
                }
                Spacer(Modifier.size(10.dp))
                val micScale by animateFloatAsState(if (listening) 1.12f + inLevel * 0.25f else 1f, label = "m")
                Box(
                    Modifier.size(56.dp).graphicsLayer { scaleX = micScale; scaleY = micScale }.clip(CircleShape)
                        .background(if (engine.busy) look.colors.danger else look.colors.accent)
                        .pressable(PressEffect.BOUNCE) { if (engine.busy) engine.stop() else mic() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (engine.busy || listening) Icons.Rounded.Stop else Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}


@Composable
private fun ChatBubble(item: ChatItem) {
    val look = LocalRint.current
    when (item.role) {
        ChatRole.STEP -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(look.colors.accent))
            Spacer(Modifier.size(8.dp))
            RawText(item.text, fontFamily = RintFonts.Terminal, fontSize = 16.sp, color = Color.White.copy(alpha = 0.6f))
        }
        ChatRole.USER -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                item.text, color = look.colors.onAccent, fontFamily = look.font, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)).background(look.colors.accent).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        ChatRole.RIN, ChatRole.ERROR -> Row(verticalAlignment = Alignment.Top) {
            RinSprite(if (item.role == ChatRole.ERROR) Pose.SAD else Pose.HEAD, 30.dp)
            Spacer(Modifier.size(8.dp))
            Column(
                Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp))
                    .background(if (item.role == ChatRole.ERROR) look.colors.danger.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                RawText(item.text, color = Color.White, fontFamily = look.font, fontSize = 15.sp, lineHeight = 20.sp)
                item.question?.let { q ->
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("no") { q.complete(false) }
                        Pill("yes, do it", selected = true) { q.complete(true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(state: LauncherState) {
    val look = LocalRint.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.1f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)).padding(18.dp)
    ) {
        Text("give me a brain first", fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick Gemini, Groq, Claude or OpenRouter and paste an API key. A free Gemini key also gives me a voice.",
            fontFamily = look.font, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("open settings", selected = true) { AssistantOverlay.open = false; state.settingsSection = "ai"; state.settingsOpen = true }
        }
    }
}
