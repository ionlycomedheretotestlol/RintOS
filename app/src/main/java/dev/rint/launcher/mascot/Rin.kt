package dev.rint.launcher.mascot

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.core.MascotPresence
import dev.rint.launcher.ui.Haptics
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.rememberHaptic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(2.dp, Color(0xFF0A0E1E), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color(0xFF0A0E1E))
    }
}

private enum class Scene { NONE, PEEK, WALK, SLEEP, CHARGE, HAPPY }

/**
 * Rin's home-screen life. He only shows up at meaningful moments (a greeting, bedtime,
 * charging, a rare stroll) and never covers anything interactive for long.
 */
@Composable
fun RinDirector(
    modifier: Modifier = Modifier,
    homeVisits: Int,
    charging: Boolean,
    bottomInset: Dp,
) {
    val look = LocalRint.current
    val cfg = look.cfg.mascot
    if (!cfg.enabled) return
    val v = rememberHaptic()
    var scene by remember { mutableStateOf(Scene.NONE) }
    var bubble by remember { mutableStateOf<String?>(null) }
    var reaction by remember { mutableStateOf(Pose.JUMP) }
    var greeted by remember { mutableIntStateOf(-1) }
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(1f) }
    var flip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val size = (76 * cfg.size).dp

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val night = cfg.sleepsAtNight && (hour >= 23 || hour < 6)
    val chance = when (cfg.presence) {
        MascotPresence.SHY -> 0.04f
        MascotPresence.NORMAL -> 0.12f
        MascotPresence.CLINGY -> 0.35f
    }

    LaunchedEffect(homeVisits, night) {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        when {
            night && scene == Scene.NONE -> { scene = Scene.SLEEP; y.snapTo(0f) }
            cfg.greets && greeted != day -> {
                greeted = day
                scene = Scene.PEEK
                bubble = when (hour) {
                    in 5..11 -> "morning!"
                    in 12..17 -> "hey hey"
                    in 18..22 -> "evening~"
                    else -> "up late?"
                }
                y.snapTo(1f)
                y.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
                delay(2600)
                bubble = null
                y.animateTo(1f, tween(380))
                scene = if (night) Scene.SLEEP else Scene.NONE
                if (night) y.snapTo(0f)
            }
        }
    }

    LaunchedEffect(charging) {
        if (charging && cfg.reactsToCharging && scene == Scene.NONE && homeVisits > 0) {
            scene = Scene.CHARGE
            bubble = "yum, power"
            y.snapTo(1f)
            y.animateTo(0f, spring(dampingRatio = 0.5f))
            delay(2400)
            bubble = null
            y.animateTo(1f, tween(300))
            scene = Scene.NONE
        }
    }

    if (scene == Scene.NONE) return
    val tr = rememberInfiniteTransition(label = "walk")
    val step by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing)), label = "s")
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = with(density) { maxWidth.toPx() }
        val sizePx = with(density) { size.toPx() }
        val poke = Modifier.clickable(remember { MutableInteractionSource() }, null) {
            Haptics.tap(v)
            scope.launch {
                val prev = scene
                scene = Scene.HAPPY
                reaction = listOf(Pose.JUMP, Pose.CHEER, Pose.DANCE, Pose.WAVE).random()
                bubble = listOf("hi!", ":3", "hehe", "boop", "!!").random()
                delay(1400)
                bubble = null
                scene = if (prev == Scene.HAPPY) Scene.NONE else prev
                if (scene == Scene.PEEK) scene = Scene.NONE
            }
        }
        val pose = when (scene) {
            Scene.PEEK -> Pose.WAVE
            Scene.WALK -> Pose.WALK
            Scene.SLEEP -> Pose.SLEEP
            Scene.CHARGE -> Pose.CHEER
            Scene.HAPPY -> reaction
            Scene.NONE -> Pose.FRONT
        }
        val baseX = when (scene) {
            Scene.WALK -> x.value * w - sizePx / 2
            Scene.SLEEP -> w - sizePx - 24f
            else -> w / 2 - sizePx / 2
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomInset)
                .offset { IntOffset(baseX.roundToInt(), (y.value * sizePx * 1.2f).roundToInt()) }
                .wrapContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            bubble?.let { SpeechBubble(it, Modifier.padding(bottom = 4.dp)) }
            if (scene == Scene.SLEEP) {
                Text("z", fontFamily = RintFonts.Pixel, color = look.colors.accent, fontSize = 12.sp,
                    modifier = Modifier.graphicsLayer { translationY = -step * 18f; alpha = 1f - step; translationX = step * 12f })
            }
            RinSprite(
                pose = pose,
                size = size,
                flip = scene == Scene.WALK && flip,
                modifier = poke.graphicsLayer {
                    if (scene == Scene.WALK) translationY = -kotlin.math.abs(sin(step * Math.PI.toFloat() * 2)) * 6f
                    if (scene == Scene.SLEEP) scaleY = 1f + 0.03f * sin(step * Math.PI.toFloat() * 2)
                },
            )
        }
    }
}
