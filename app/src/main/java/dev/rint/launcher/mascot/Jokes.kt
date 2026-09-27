package dev.rint.launcher.mascot

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/** Settings → Dangerous. Every button here is a joke. None of them do anything bad. */
enum class Joke(val title: String, val desc: String) {
    SYSTEM32("Delete System32", "what could go wrong"),
    RAM("Download more RAM", "16 GB, free, totally legit"),
    SNEEZE("Make Rin sneeze", "bless him in advance"),
    SUMMON("Summon 100 Rins", "there is no undo"),
    FLIP("Flip the phone upside down", "the software way"),
    SPIN("Spin to win", "you won't win"),
    BACKFLIP("Make Rin do a backflip", "he's been practicing"),
    SELF_DESTRUCT("Self destruct", "5… 4… 3…"),
}

/** Whole-launcher chaos the jokes can apply (read by the launcher's root layer). */
object Chaos {
    var joke by mutableStateOf<Joke?>(null)
    val rotation = Animatable(0f)
    val shake = Animatable(0f)
}

@Composable
fun JokeOverlay() {
    val joke = Chaos.joke ?: return
    val look = LocalRint.current
    var t by remember(joke) { mutableFloatStateOf(0f) }
    LaunchedEffect(joke) {
        val start = System.nanoTime()
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val end = when (joke) {
        Joke.SYSTEM32 -> 8.5f; Joke.RAM -> 8f; Joke.SNEEZE -> 5f; Joke.SUMMON -> 7f
        Joke.FLIP -> 6.5f; Joke.SPIN -> 4.5f; Joke.BACKFLIP -> 4.5f; Joke.SELF_DESTRUCT -> 9f
    }
    LaunchedEffect(t >= end) { if (t >= end) finish() }
    BackHandler { finish() }

    // launcher-wide effects
    LaunchedEffect(joke) {
        when (joke) {
            Joke.FLIP -> {
                Chaos.rotation.animateTo(180f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
                delay(3200)
                Chaos.rotation.animateTo(360f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
                Chaos.rotation.snapTo(0f)
            }
            Joke.SPIN -> {
                Chaos.rotation.animateTo(1080f, tween(3200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                Chaos.rotation.snapTo(0f)
            }
            Joke.SNEEZE -> {
                delay(2200)
                Chaos.shake.snapTo(1f)
                Chaos.shake.animateTo(0f, tween(900))
            }
            Joke.SELF_DESTRUCT -> {
                val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull()
                repeat(5) { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 180); delay(1000) }
                tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600)
                Chaos.shake.snapTo(1f)
                Chaos.shake.animateTo(0f, tween(1200))
                delay(1500); tone?.release()
            }
            else -> Unit
        }
    }

    val dim = when (joke) { Joke.FLIP, Joke.SPIN -> 0f; else -> 0.82f }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim))
            .clickable(remember { MutableInteractionSource() }, null) { if (t > 1.5f) finish() },
        contentAlignment = Alignment.Center,
    ) {
        when (joke) {
            Joke.SYSTEM32 -> System32(t)
            Joke.RAM -> Ram(t)
            Joke.SNEEZE -> Sneeze(t)
            Joke.SUMMON -> Summon(t, look.colors.accent.toArgb())
            Joke.FLIP -> Bubble("wrong way up!", Pose.CONFUSED)
            Joke.SPIN -> Bubble("wheeeee", Pose.CHEER)
            Joke.BACKFLIP -> Backflip(t)
            Joke.SELF_DESTRUCT -> SelfDestruct(t)
        }
    }
}

private fun finish() {
    Chaos.joke = null
}

@Composable
private fun Bubble(text: String, pose: Pose) {
    Column(Modifier.padding(top = 300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SpeechBubble(text)
        Spacer(Modifier.height(6.dp))
        RinSprite(pose, 120.dp)
    }
}

@Composable
private fun Terminal(lines: List<String>, color: Color = Color(0xFF9CF6C1)) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        lines.takeLast(14).forEach { Text(it, fontFamily = RintFonts.Terminal, fontSize = 18.sp, color = color, maxLines = 1) }
    }
}

@Composable
private fun ProgressBar(p: Float, color: Color) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.12f))) {
        Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(14.dp).background(color))
    }
}

private val sysFiles = listOf("kernel32.dll", "hal.dll", "ntoskrnl.exe", "explorer.exe", "winload.efi", "the recycle bin", "clippy.exe", "minesweeper", "the start button", "your homework")

@Composable
private fun System32(t: Float) {
    if (t < 5f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Deleting C:\\Windows\\System32", color = Color.White, fontFamily = RintFonts.Terminal, fontSize = 22.sp)
            Spacer(Modifier.height(16.dp))
            ProgressBar(t / 4.6f, Color(0xFFFF453A))
            Spacer(Modifier.height(10.dp))
            Terminal(sysFiles.take(((t / 4.6f) * sysFiles.size).toInt().coerceAtMost(sysFiles.size)).map { "  deleted $it" }, Color(0xFFFF8A80))
        }
    } else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { translationX = if (t < 5.3f) sin(t * 90f) * 20f else 0f }) {
        SpeechBubble("just kidding. this is Android. there is no System32.")
        Spacer(Modifier.height(8.dp))
        RinSprite(Pose.LAUGH, 150.dp)
    }
}

@Composable
private fun Ram(t: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (t < 4.5f) {
            Text("Downloading 16 GB of RAM…", color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(14.dp))
            val p = (t / 4f).coerceAtMost(0.97f)
            ProgressBar(p, LocalRint.current.colors.accent)
            Spacer(Modifier.height(8.dp))
            val speed = if (t < 3f) "${(420 - t * 130).toInt().coerceAtLeast(3)} MB/s" else "0 B/s"
            Text("${(p * 16).let { "%.1f".format(it) }} GB of 16 GB · $speed", color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Terminal, fontSize = 18.sp)
            Spacer(Modifier.height(20.dp))
            RinSprite(if (t < 3f) Pose.THINK else Pose.SHY, 110.dp)
        } else {
            SpeechBubble("…I ate it. it was crunchy.")
            Spacer(Modifier.height(8.dp))
            RinSprite(Pose.HAPPY, 150.dp)
            Text("RAM added: 0 bytes", color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Pixel, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun Sneeze(t: Float) {
    val pose = when { t < 2.2f -> Pose.YAWN; t < 3.2f -> Pose.SHOCK; else -> Pose.SHY }
    val text = when { t < 0.8f -> "a…"; t < 1.5f -> "a-a…"; t < 2.2f -> "a-a-a…"; t < 3.2f -> "CHOO!!"; else -> "…excuse me." }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer {
        val wind = if (t < 2.2f) 1f + 0.08f * (t / 2.2f) else 1f
        scaleX = wind; scaleY = wind
        translationX = if (t in 2.2f..2.6f) sin(t * 120f) * 18f else 0f
    }) {
        Text(text, color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = if (text.startsWith("CHOO")) 48.sp else 28.sp)
        Spacer(Modifier.height(10.dp))
        RinSprite(pose, 170.dp)
    }
}

@Composable
private fun Summon(t: Float, accent: Int) {
    val painter = remember { RinPainter().also { it.accent = accent } }
    val params = remember { RinParams() }
    val rnd = remember { Random(100) }
    val drops = remember { List(100) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 2.5f, 0.6f + rnd.nextFloat() * 0.8f, rnd.nextFloat() * 6f) } }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                drops.forEachIndexed { i, d ->
                    val lt = t - d[1]
                    if (lt < 0f) return@forEachIndexed
                    val s = size.width / 48f * 0.28f * d[2]
                    val x = d[0] * size.width
                    val fallY = (lt * 650f * d[2]).coerceAtMost(size.height - 60f * d[2] - (i % 7) * 26f)
                    animateRin(if (i % 3 == 0) Pose.HAPPY else if (i % 3 == 1) Pose.HEAD else Pose.LOVE, t + d[3], 0f, params)
                    nc.save()
                    nc.translate(x - 24f * s, fallY - 20f * s)
                    nc.rotate(sin(t * 3f + i) * 12f, 24f * s, 20f * s)
                    nc.scale(s, s)
                    painter.draw(nc, params)
                    nc.restore()
                }
            }
        }
        val n = drops.count { t > it[1] }
        Text("$n / 100 Rins", color = Color.White, fontFamily = RintFonts.Pixel, fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp))
        if (t > 5f) Box(Modifier.align(Alignment.Center)) { SpeechBubble("we live here now.") }
    }
}

@Composable
private fun Backflip(t: Float) {
    val p = ((t - 0.8f) / 1.1f).coerceIn(0f, 1f)
    val pose = when { t < 0.8f -> Pose.CROUCH; p < 1f -> Pose.JUMP; t < 2.4f -> Pose.CROUCH; else -> Pose.CHEER }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(44.dp)) { if (t > 2.4f) SpeechBubble("10/10. nailed it.") }
        RinSprite(pose, 170.dp, Modifier.graphicsLayer {
            rotationZ = -360f * p * p * (3 - 2 * p)
            translationY = -sin(p * Math.PI.toFloat()) * 420f
        })
        if (t > 2.4f) Text("🏅", fontSize = 40.sp)
    }
}

@Composable
private fun SelfDestruct(t: Float) {
    val n = 5 - t.toInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (t < 5f) {
            Text("SELF DESTRUCT", color = Color(0xFFFF3B30), fontFamily = RintFonts.Pixel, fontSize = 18.sp)
            Text("$n", color = Color.White, fontFamily = RintFonts.Inter, fontWeight = FontWeight.Black, fontSize = 140.sp,
                modifier = Modifier.graphicsLayer { val s = 1f + 0.4f * (1f - (t % 1f)); scaleX = s; scaleY = s })
            RinSprite(Pose.SHOCK, 100.dp)
        } else if (t < 5.6f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 1f - (t - 5f) / 0.6f)))
        } else {
            SpeechBubble("…you really pressed it. twice.")
            Spacer(Modifier.height(8.dp))
            RinSprite(Pose.CONFUSED, 150.dp)
            Text("nothing was destroyed. except your trust in buttons.", color = Color.White.copy(alpha = 0.6f), fontFamily = RintFonts.Pixel,
                fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp, start = 24.dp, end = 24.dp))
        }
    }
}
