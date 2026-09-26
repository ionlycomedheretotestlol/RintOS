package dev.rint.launcher.intro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.RintApp
import dev.rint.launcher.home.LauncherState
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.Pill
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.rememberHaptic

private enum class Hint { NONE, SWIPE_UP, HOLD, UP, DOUBLE_TAP }

private data class Step(val text: String, val pose: Pose, val hint: Hint)

private val steps = listOf(
    Step("hey! welcome home.\nwant the 30-second tour?", Pose.FRONT, Hint.NONE),
    Step("swipe up anywhere → every app,\nplus search that does math & music.", Pose.HEAD, Hint.SWIPE_UP),
    Step("hold on empty space → widgets,\nwallpaper and the big settings.", Pose.SIT, Hint.HOLD),
    Step("hold any app → rename it, hide it,\nor drag it wherever you want.", Pose.HAPPY, Hint.HOLD),
    Step("see the notch up top? tap it.\nhold it for music.", Pose.SHOCK, Hint.UP),
    Step("the music widget plays any song\nwith live lyrics. I dance in the breaks.", Pose.HAPPY, Hint.NONE),
    Step("double-tap empty space to lock\n(needs one permission).", Pose.DROWSY, Hint.DOUBLE_TAP),
    Step("and literally everything is customizable.\nhave fun. I'll be around :3", Pose.SIT, Hint.NONE),
)

@Composable
fun GuideOverlay(state: LauncherState) {
    val look = LocalRint.current
    val v = rememberHaptic()
    val i = state.guideStep.coerceIn(0, steps.lastIndex)
    fun finish() {
        state.guideStep = -1
        RintApp.instance.stores.config.update { it.copy(guideSeen = true) }
    }
    fun next() {
        Haptics.tap(v)
        if (i >= steps.lastIndex) finish() else state.guideStep = i + 1
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(remember { MutableInteractionSource() }, null) { next() }
    ) {
        HintAnim(steps[i].hint, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(i, label = "g", transitionSpec = { (slideInVertically { it / 3 } + fadeIn()) togetherWith fadeOut() }) { idx ->
                val s = steps[idx]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Text(s.text, fontFamily = look.font, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF0A0E1E), lineHeight = 21.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    RinSprite(s.pose, 110.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (i == 0) {
                    Pill("skip") { finish() }
                    Pill("sure!", selected = true) { next() }
                } else {
                    Text("${i}/${steps.lastIndex}", fontFamily = RintFonts.Pixel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                    Spacer(Modifier.width(6.dp))
                    Pill(if (i == steps.lastIndex) "let's go" else "next", selected = true) { next() }
                }
            }
        }
    }
}

@Composable
private fun HintAnim(h: Hint, modifier: Modifier) {
    if (h == Hint.NONE) return
    val look = LocalRint.current
    val t = rememberInfiniteTransition(label = "hint")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(modifier.graphicsLayer { alpha = 0.9f }) {
        val c = Offset(size.width / 2, size.height * 0.42f)
        when (h) {
            Hint.SWIPE_UP -> {
                val y = c.y + 160f - p * 320f
                drawCircle(Color.White.copy(alpha = 1f - p), 34f, Offset(c.x, y))
                drawLine(look.colors.accent.copy(alpha = 0.6f), Offset(c.x, c.y + 160f), Offset(c.x, y), 8f, StrokeCap.Round)
            }
            Hint.HOLD -> {
                drawCircle(Color.White.copy(alpha = 0.8f), 30f, c)
                drawCircle(look.colors.accent.copy(alpha = 1f - p), 30f + p * 90f, c, style = Stroke(6f))
            }
            Hint.UP -> {
                val y = size.height * 0.08f + (1 - p) * 60f
                drawLine(Color.White, Offset(c.x, y + 120f), Offset(c.x, y), 8f, StrokeCap.Round)
                drawLine(Color.White, Offset(c.x - 30f, y + 30f), Offset(c.x, y), 8f, StrokeCap.Round)
                drawLine(Color.White, Offset(c.x + 30f, y + 30f), Offset(c.x, y), 8f, StrokeCap.Round)
            }
            Hint.DOUBLE_TAP -> {
                val a = if (p < 0.5f) p * 2 else (p - 0.5f) * 2
                drawCircle(Color.White.copy(alpha = 0.8f), 28f, c)
                drawCircle(look.colors.accent.copy(alpha = 1f - a), 28f + a * 60f, c, style = Stroke(5f))
            }
            Hint.NONE -> Unit
        }
    }
}
