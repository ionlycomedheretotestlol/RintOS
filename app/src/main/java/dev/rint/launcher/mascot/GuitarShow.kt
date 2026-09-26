package dev.rint.launcher.mascot

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** The forbidden button. */
object GuitarShow {
    var open by mutableStateOf(false)
}

/**
 * A plucked-string band in ~200 lines: Karplus–Strong strings strummed through G–D–Em–C,
 * a stomp-clap groove, a pentatonic solo and a big ringing last chord.
 */
class GuitarSynth {
    companion object {
        const val SR = 32000
        const val BPM = 100.0
        const val BEAT = 60.0 / BPM
        const val BAR = BEAT * 4
        const val INTRO = 3.0           // darkness, then the spotlight clunk at 1.2 s
        const val VERSE_BARS = 4
        const val SOLO_BARS = 4
        val SONG_START = INTRO
        val SOLO_START = INTRO + VERSE_BARS * BAR
        val FINAL_CHORD = INTRO + (VERSE_BARS + SOLO_BARS) * BAR
        val END = FINAL_CHORD + BAR * 1.5
        private val CHORDS = arrayOf(
            intArrayOf(43, 47, 50, 55, 59, 67), // G
            intArrayOf(50, 57, 62, 66),         // D
            intArrayOf(40, 47, 52, 55, 59, 64), // Em
            intArrayOf(48, 52, 55, 60, 64),     // C
        )
        // down/up strum pattern on 8ths: D . D U . U D U
        private val STRUM = intArrayOf(1, 0, 1, -1, 0, -1, 1, -1)
        private val SOLO = intArrayOf(79, 76, 74, 76, 79, 81, 79, 76, 74, 71, 74, 76, 74, 71, 67, 69)
        private fun hz(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)
    }

    private class Str { var buf = DoubleArray(1); var len = 1; var idx = 0; var gain = 0.0; var decay = 0.996; var start = 0L; var active = false }
    private val strings = Array(16) { Str() }
    private var next = 0
    private val rnd = Random(7)
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var written = 0L
    private var lastEvent = -1

    fun seconds(): Double = track?.let { runCatching { it.playbackHeadPosition.toDouble() / SR }.getOrDefault(0.0) } ?: 0.0

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min * 2, SR / 5 * 2)).setTransferMode(AudioTrack.MODE_STREAM).build()
        }.getOrNull() ?: return
        track = t; running = true; t.play()
        Thread({
            val buf = ShortArray(SR / 50)
            while (running) { render(buf); if (t.write(buf, 0, buf.size) < 0) break }
        }, "rint-guitar").apply { isDaemon = true; start() }
    }

    fun release() {
        running = false
        runCatching { track?.stop(); track?.release() }
        track = null
    }

    private fun pluck(midi: Int, gain: Double, at: Long, decay: Double = 0.996) {
        val s = strings[next]; next = (next + 1) % strings.size
        val len = (SR / hz(midi)).toInt().coerceAtLeast(2)
        if (s.buf.size < len) s.buf = DoubleArray(len)
        s.len = len; s.idx = 0; s.gain = gain; s.decay = decay; s.start = at; s.active = true
        for (i in 0 until len) s.buf[i] = rnd.nextDouble() * 2 - 1
    }

    private fun strum(chord: IntArray, dir: Int, at: Long, gain: Double) {
        val order = if (dir > 0) chord.indices else chord.indices.reversed()
        var k = 0
        for (i in order) { pluck(chord[i], gain * (if (dir > 0) 1.0 else 0.7), at + k * (SR * 0.011).toLong()); k++ }
    }

    /** Schedules events on the 8th-note grid, just ahead of the render head. */
    private fun schedule(n: Long) {
        val t = n.toDouble() / SR
        if (t < SONG_START) return
        val e = floor((t - SONG_START) / (BEAT / 2)).toInt()
        if (e == lastEvent) return
        lastEvent = e
        val at = n
        val bar = e / 8
        val step = e % 8
        val chord = CHORDS[bar % 4]
        when {
            bar < VERSE_BARS -> if (STRUM[step] != 0) strum(chord, STRUM[step], at, 0.55)
            bar < VERSE_BARS + SOLO_BARS -> {
                if (step % 4 == 0) strum(chord, 1, at, 0.35)
                val note = SOLO[((bar - VERSE_BARS) * 8 + step) % SOLO.size]
                pluck(note, 0.7, at, 0.9975)
                if (step % 2 == 1) pluck(note + 12, 0.25, at + SR / 60, 0.995)
            }
            bar == VERSE_BARS + SOLO_BARS && step == 0 -> { strum(CHORDS[0], 1, at, 0.8); pluck(79, 0.5, at + SR / 20, 0.9985) }
        }
    }

    private fun render(out: ShortArray) {
        for (i in out.indices) {
            val n = written + i
            schedule(n)
            val t = n.toDouble() / SR
            var s = 0.0
            for (st in strings) {
                if (!st.active || n < st.start) continue
                val a = st.buf[st.idx]
                val b = st.buf[(st.idx + 1) % st.len]
                val v = (a + b) * 0.5 * st.decay
                st.buf[st.idx] = v
                st.idx = (st.idx + 1) % st.len
                s += a * st.gain
                if (n - st.start > SR * 4) st.active = false
            }
            // the spotlight clunk
            val clunk = t - 1.2
            if (clunk in 0.0..0.6) s += sin(2 * PI * 55 * clunk) * exp(-clunk * 9) * 0.9 + (rnd.nextDouble() - 0.5) * exp(-clunk * 40) * 0.6
            // stomp + clap groove while the band plays
            if (t >= SONG_START && t < FINAL_CHORD) {
                val bt = (t - SONG_START) % BEAT
                val beat = floor((t - SONG_START) / BEAT).toInt() % 4
                if (beat == 0 || beat == 2) s += sin(2 * PI * (50 * bt + 60 * (1 - exp(-bt * 30)) / 30)) * exp(-bt * 10) * 0.7
                else s += (rnd.nextDouble() * 2 - 1) * exp(-bt * 30) * 0.28
            }
            if (t >= FINAL_CHORD) {
                val ft = t - FINAL_CHORD
                if (ft < 1.5) s += (rnd.nextDouble() * 2 - 1) * exp(-ft * 1.8) * 0.15   // cymbal
            }
            out[i] = (tanh(s * 0.9) * 0.85 * Short.MAX_VALUE).toInt().toShort()
        }
        written += out.size
    }
}

@Composable
fun GuitarShowOverlay() {
    if (!GuitarShow.open) return
    val look = LocalRint.current
    val accent = look.colors.accent
    val synth = remember { GuitarSynth() }
    var t by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        runCatching { synth.start() }
        onDispose { synth.release() }
    }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) withFrameNanos { now ->
            val a = synth.seconds().toFloat()
            val w = (now - start) / 1e9f
            t = if (a > 0.05f || w < 0.6f) a else w
        }
    }
    BackHandler { GuitarShow.open = false }
    val done = t > GuitarSynth.END
    val light = ((t - 1.2f) / 0.15f).coerceIn(0f, 1f)
    val solo = t >= GuitarSynth.SOLO_START && t < GuitarSynth.FINAL_CHORD
    val beat = if (t > GuitarSynth.SONG_START) (((t - GuitarSynth.SONG_START) / GuitarSynth.BEAT) % 1.0).toFloat() else 1f
    val kick = exp(-beat * 6f)

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .clickable(remember { MutableInteractionSource() }, null) { if (done) GuitarShow.open = false }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (light <= 0f) return@Canvas
            val cx = size.width / 2
            val floor = size.height * 0.72f
            val flicker = if (t < 1.5f) (if ((t * 30).toInt() % 3 == 0) 0.6f else 1f) else 1f
            // stage floor
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1206), Color.Black), floor, size.height), Offset(0f, floor), Size(size.width, size.height - floor))
            // the main cone
            val cone = Path().apply {
                moveTo(cx - 24f, 0f); lineTo(cx + 24f, 0f); lineTo(cx + size.width * 0.34f, floor); lineTo(cx - size.width * 0.34f, floor); close()
            }
            drawPath(cone, Brush.verticalGradient(listOf(Color(0xFFFFF4D6).copy(alpha = 0.55f * light * flicker), Color(0xFFFFE9B0).copy(alpha = 0.12f * light)), 0f, floor))
            drawOval(Color(0xFFFFF1C9).copy(alpha = 0.35f * light * flicker), Offset(cx - size.width * 0.34f, floor - 26f), Size(size.width * 0.68f, 52f))
            // solo: colored sweeping lights + sparkles
            if (solo) {
                val sweep = sin((t - GuitarSynth.SOLO_START.toFloat()) * 2.2f)
                for (k in 0..1) {
                    val sx = if (k == 0) 0f else size.width
                    val tx = cx + sweep * size.width * 0.35f * (if (k == 0) 1 else -1)
                    val c = if (k == 0) accent else Color(0xFFFF4FA3)
                    val p = Path().apply { moveTo(sx, 0f); lineTo(tx - 60f, floor); lineTo(tx + 60f, floor); close() }
                    drawPath(p, c.copy(alpha = 0.18f + 0.12f * kick))
                }
                val rnd = Random((t * 8).toInt())
                repeat(24) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * floor
                    drawCircle(Color.White.copy(alpha = rnd.nextFloat() * 0.8f), 2f + rnd.nextFloat() * 3f, Offset(x, y))
                }
            }
            // floating music notes
            if (t > GuitarSynth.SONG_START) {
                val rnd = Random(3)
                repeat(10) { i ->
                    val ph = ((t * 0.35f + rnd.nextFloat()) % 1f)
                    val x = cx + (rnd.nextFloat() - 0.5f) * size.width * 0.8f + sin(t * 2f + i) * 20f
                    val y = floor - ph * floor * 0.8f
                    val a = (1f - ph) * 0.8f
                    drawCircle(accent.copy(alpha = a), 7f, Offset(x, y))
                    drawRect(accent.copy(alpha = a), Offset(x + 5f, y - 26f), Size(3f, 26f))
                }
            }
            if (t >= GuitarSynth.FINAL_CHORD) {
                val ft = t - GuitarSynth.FINAL_CHORD.toFloat()
                drawRect(Color.White.copy(alpha = (1f - ft / 0.5f).coerceIn(0f, 1f) * 0.8f))
                val rnd = Random(11)
                val cols = listOf(accent, Color.White, Color(0xFFFFD60A), Color(0xFFFF6FB5))
                repeat(80) {
                    val x = rnd.nextFloat() * size.width
                    val y = (ft * (200f + rnd.nextFloat() * 300f) + rnd.nextFloat() * size.height * 0.4f - 60f) % size.height
                    drawRect(cols[it % 4], Offset(x, y), Size(10f, 5f))
                }
            }
        }
        // the text before the lights
        if (t < 1.2f) {
            Text(
                "you pressed it.", fontFamily = RintFonts.Terminal, fontSize = 24.sp, color = Color.White,
                modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = ((t - 0.2f) / 0.3f).coerceIn(0f, 1f) },
            )
        }
        if (light > 0f) {
            val pose = when {
                t < 2.2f -> Pose.SHOCK
                t < GuitarSynth.SONG_START -> Pose.WAVE
                t < GuitarSynth.SOLO_START -> Pose.GUITAR
                t < GuitarSynth.FINAL_CHORD -> Pose.GUITAR_SOLO
                else -> Pose.CHEER
            }
            Column(
                Modifier.align(Alignment.Center).offset(y = 40.dp).graphicsLayer {
                    val s = 1f + 0.03f * kick * (if (t > GuitarSynth.SONG_START) 1f else 0f)
                    scaleX = s; scaleY = s
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val bubble = when {
                    t in 1.6f..GuitarSynth.SONG_START.toFloat() -> "…oh. an audience."
                    t >= GuitarSynth.FINAL_CHORD + 0.6f -> "thank you, thank you"
                    else -> null
                }
                Box(Modifier.height(44.dp)) { bubble?.let { SpeechBubble(it) } }
                Spacer(Modifier.height(4.dp))
                RinSprite(pose, 220.dp)
            }
        }
        if (solo) Text(
            "GUITAR SOLO", fontFamily = RintFonts.Pixel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp).graphicsLayer {
                val s = 1f + 0.12f * kick; scaleX = s; scaleY = s; rotationZ = sin(t * 5f) * 4f
            },
        )
        if (done) Text(
            "tap anywhere to leave the concert", fontFamily = RintFonts.Pixel, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
        )
        Text(
            "✕", fontSize = 20.sp, color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).clickable { GuitarShow.open = false }.padding(8.dp),
        )
    }
}
