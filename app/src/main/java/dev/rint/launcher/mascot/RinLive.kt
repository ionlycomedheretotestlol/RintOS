package dev.rint.launcher.mascot

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.rint.launcher.ui.rememberAmbientClock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Everything Rin can do. Head-only poses crop to his face. */
enum class Pose(val headOnly: Boolean = false) {
    FRONT, BACK, FACE_LEFT, FACE_RIGHT,
    HEAD(true), HAPPY(true), MEH(true), SHOCK(true), DROWSY(true), LOVE(true), TALK(true), THINK(true), LISTEN(true), DANCE_HEAD(true),
    WALK, SIT, JUMP, CROUCH, SLEEP, WAVE, DANCE, CHEER, GUITAR, GUITAR_SOLO, STRETCH, YAWN, CONFUSED, SHY, LAUGH, NOD,
}

private fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
}

/** Pulse that's 1 for [len] seconds once every ~[every] seconds (jittered), else 0. */
private fun occasional(t: Float, every: Float, len: Float, seed: Int): Float {
    val slot = floor(t / every).toInt()
    val start = slot * every + hash(slot * 31 + seed) * (every - len)
    val u = (t - start) / len
    return if (u in 0f..1f) sin(u * PI.toFloat()) else 0f
}

/** Turns a pose + time (+ voice level) into joint values. Pure, cheap, called every frame. */
fun animateRin(pose: Pose, t: Float, talk: Float, p: RinParams) {
    p.reset()
    val breathe = sin(t * 2.3f)
    val blink = occasional(t, 3.4f, 0.16f, 7)
    p.eyeOpen = 1f - blink
    p.headY = if (breathe > 0.4f) -0.6f else 0f
    p.earL = occasional(t, 5.1f, 0.22f, 3) * 0.9f
    p.earR = occasional(t + 2.1f, 6.3f, 0.2f, 11) * 0.9f
    p.tail = sin(t * 2.6f) * 0.22f
    p.lookX = (occasional(t, 7.5f, 1.4f, 5) * (if (hash(floor(t / 7.5f).toInt()) > 0.5f) 1f else -1f)) * 0.8f
    if (pose.headOnly) p.stance = Stance.HEAD
    when (pose) {
        Pose.FRONT, Pose.HEAD -> Unit
        Pose.BACK -> { p.lookY = -1f; p.lookX = 0f }
        Pose.FACE_LEFT -> p.lookX = -1f
        Pose.FACE_RIGHT -> p.lookX = 1f
        Pose.HAPPY -> { p.eyes = Eyes.HAPPY; p.blush = 1f; p.tilt = sin(t * 3f) * 0.08f; p.mouth = 0.35f }
        Pose.MEH -> { p.eyes = Eyes.MEH; p.lookX = 1f; p.earL = 0.5f }
        Pose.SHOCK -> { p.eyes = Eyes.SHOCK; p.eyeOpen = 1f; p.sweat = true; p.earL = -0.4f; p.earR = -0.4f; p.mouth = 0.45f; p.headY = -abs(sin(t * 20f)) * 0.6f }
        Pose.DROWSY -> { p.stance = Stance.PEEK; p.eyes = Eyes.MEH; p.lookX = 0f; p.eyeOpen = 0.8f - blink * 0.8f; p.headY = 2f + breathe * 0.4f }
        Pose.LOVE -> { p.eyes = Eyes.HEART; p.blush = 1f; p.tilt = sin(t * 4f) * 0.1f; p.mouth = 0.3f; p.headY = -abs(sin(t * 5f)) }
        Pose.TALK -> {
            val open = talk * (0.55f + 0.45f * abs(sin(t * 17f)))
            p.mouth = open; p.headY = -open * 1.2f; p.tilt = sin(t * 5f) * 0.05f * talk
            if (talk > 0.5f) p.eyes = if (sin(t * 1.3f) > 0.6f) Eyes.HAPPY else Eyes.OPEN
        }
        Pose.THINK -> { p.lookX = 0.9f; p.lookY = -1f; p.tilt = 0.12f + sin(t * 1.5f) * 0.04f; p.earR = 0.4f }
        Pose.LISTEN -> { p.earL = -0.5f; p.earR = -0.5f; p.tilt = sin(t * 2f) * 0.06f; p.eyeOpen = max(p.eyeOpen, 0.9f) }
        Pose.DANCE_HEAD -> {
            val b = sin(t * 11f)
            p.tilt = b * 0.2f; p.headY = -abs(b) * 1.6f; p.eyes = Eyes.HAPPY; p.earL = max(0f, b) * 0.6f; p.earR = max(0f, -b) * 0.6f
        }
        Pose.WALK -> {
            val c = sin(t * 9f)
            p.legL = c; p.legR = -c; p.bodyY = -abs(c) * 0.9f; p.armL = max(0f, -c) * 0.2f; p.armR = max(0f, c) * 0.2f
            p.tail = sin(t * 9f) * 0.35f; p.lookX = 1f
        }
        Pose.SIT -> { p.stance = Stance.SIT; p.tail = sin(t * 1.8f) * 0.3f }
        Pose.CROUCH -> { p.stance = Stance.SIT; p.bodyY = 1.5f; p.squash = 0.92f; p.lookX = 1f; p.earL = -0.3f; p.earR = -0.3f; p.tail = sin(t * 14f) * 0.2f }
        Pose.SLEEP -> {
            p.stance = Stance.SIT; p.eyes = Eyes.CLOSED; p.headY = 2f + sin(t * 1.2f) * 0.6f; p.tilt = 0.12f
            p.earL = 0.6f; p.earR = 0.6f; p.tail = 0f; p.blush = 0.6f
        }
        Pose.JUMP -> {
            val ph = (t * 1.25f) % 1f
            when {
                ph < 0.18f -> { p.squash = 1f - ph / 0.18f * 0.18f; p.eyes = Eyes.HAPPY }
                ph < 0.78f -> {
                    val a = (ph - 0.18f) / 0.6f
                    p.hop = sin(a * PI.toFloat()) * 12f; p.squash = 1.12f - a * 0.12f
                    p.armL = 0.9f; p.armR = 0.9f; p.legL = 0.6f; p.legR = 0.6f; p.eyes = Eyes.HAPPY; p.earL = -0.5f; p.earR = -0.5f
                }
                else -> p.squash = 0.84f + (ph - 0.78f) / 0.22f * 0.16f
            }
            p.tail = sin(t * 12f) * 0.4f
        }
        Pose.WAVE -> { p.armR = 1f; p.armWave = sin(t * 11f) * 0.35f; p.eyes = Eyes.HAPPY; p.blush = 0.8f; p.tilt = 0.06f; p.tail = sin(t * 8f) * 0.35f }
        Pose.DANCE -> {
            val b = sin(t * 9f)
            p.hop = abs(b) * 1.5f; p.tilt = b * 0.16f; p.armL = if (b > 0) 1f else 0.3f; p.armR = if (b > 0) 0.3f else 1f
            p.legL = max(0f, b); p.legR = max(0f, -b); p.eyes = Eyes.HAPPY; p.tail = b * 0.5f
        }
        Pose.GUITAR, Pose.GUITAR_SOLO -> {
            val solo = pose == Pose.GUITAR_SOLO
            val rate = if (solo) 16f else 8f
            p.guitar = true
            p.strum = abs(sin(t * rate))
            val bob = sin(t * 6.3f)
            p.headY = -abs(bob) * 1.2f; p.tilt = bob * (if (solo) 0.18f else 0.08f)
            p.eyes = if (solo) Eyes.CLOSED else if (sin(t * 0.9f) > 0.3f) Eyes.HAPPY else Eyes.OPEN
            p.lookX = 0f; p.lookY = 0.6f
            p.mouth = if (solo) 0.3f + 0.2f * abs(bob) else 0f
            p.tail = sin(t * 6.3f) * 0.45f
            p.legL = if (solo) max(0f, bob) * 0.6f else 0f
            p.hop = if (solo) abs(bob) * 1.2f else 0f
            p.earL = max(0f, bob) * 0.4f; p.earR = max(0f, -bob) * 0.4f
        }
        Pose.STRETCH -> {
            val k = (sin(t * 1.6f) + 1f) / 2f
            p.armL = 0.8f + 0.2f * k; p.armR = 0.8f + 0.2f * k; p.squash = 1f + 0.08f * k; p.eyes = Eyes.CLOSED
            p.mouth = 0.2f * k; p.earL = -0.4f * k; p.earR = -0.4f * k; p.tail = sin(t * 3f) * 0.3f
        }
        Pose.YAWN -> {
            val k = ((sin(t * 1.2f) + 1f) / 2f).let { it * it }
            p.mouth = 0.25f + 0.75f * k; p.eyes = if (k > 0.4f) Eyes.CLOSED else Eyes.MEH; p.tilt = -0.08f * k
            p.headY = -0.8f * k; p.earL = 0.5f * k; p.earR = 0.5f * k
        }
        Pose.CONFUSED -> {
            p.tilt = 0.22f + sin(t * 1.4f) * 0.05f; p.lookX = -0.6f; p.lookY = -0.4f
            p.earL = 0.4f; p.earR = -0.3f; p.tail = sin(t * 1.5f) * 0.15f
        }
        Pose.SHY -> {
            p.blush = 1f; p.lookX = sin(t * 0.8f) * 0.8f; p.lookY = 0.8f; p.tilt = -0.1f
            p.earL = 0.6f; p.earR = 0.6f; p.tail = sin(t * 5f) * 0.15f; p.legL = sin(t * 3f) * 0.2f
        }
        Pose.LAUGH -> {
            val b = abs(sin(t * 13f))
            p.eyes = Eyes.HAPPY; p.mouth = 0.5f + 0.4f * b; p.headY = -b * 1.1f; p.tilt = sin(t * 6.5f) * 0.08f
            p.blush = 0.6f; p.tail = sin(t * 10f) * 0.4f; p.squash = 1f - 0.03f * b
        }
        Pose.NOD -> {
            val b = sin(t * 7f)
            p.headY = max(0f, b) * 1.4f; p.lookY = max(0f, b) * 0.6f; p.eyes = if (b > 0.5f) Eyes.HAPPY else Eyes.OPEN
            p.tail = sin(t * 4f) * 0.25f
        }
        Pose.CHEER -> {
            p.armL = 1f; p.armR = 1f; p.armWave = sin(t * 14f) * 0.2f; p.eyes = Eyes.STAR; p.mouth = 0.6f
            p.hop = abs(sin(t * 7f)) * 3f; p.tail = sin(t * 14f) * 0.45f
        }
    }
}

/**
 * Rin, drawn in code every frame (no image files): smooth vector art by default, or the
 * classic pixel rig. Animates only while visible, via the ambient clock.
 */
@Composable
fun RinSprite(
    pose: Pose,
    size: Dp,
    modifier: Modifier = Modifier,
    flip: Boolean = false,
    talk: Float = 0f,
    animated: Boolean = true,
    timeOffset: Float = 0f,
) {
    val look = dev.rint.launcher.ui.LocalRintOrNull.current
    val pixel = look?.cfg?.mascot?.style == dev.rint.launcher.core.MascotStyle.PIXEL
    val accent = look?.cfg?.mascot?.color?.toInt() ?: look?.colors?.accent?.toArgb() ?: 0xFF3B7CFF.toInt()
    val params = remember { RinParams() }
    val painter = remember { RinPainter() }
    val rig = remember(pixel) { if (pixel) RinRig() else null }
    val bmp = remember(pixel) { if (pixel) Bitmap.createBitmap(RinRig.W, RinRig.H, Bitmap.Config.ARGB_8888) else null }
    val image = remember(bmp) { bmp?.asImageBitmap() }
    val seed = remember { hash(System.identityHashCode(params)) * 50f }
    // a quick "pop" whenever the pose changes, so reactions feel physical
    val pop = remember { Animatable(1f) }
    LaunchedEffect(pose) { pop.snapTo(0.82f); pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f)) }
    val t = rememberAmbientClock(animated)
    Canvas(modifier.size(size)) {
        animateRin(pose, t + seed + timeOffset, talk, params)
        val srcX = if (pose.headOnly) RinRig.HEAD_X else 0
        val srcY = if (pose.headOnly) RinRig.HEAD_Y else 0
        val srcW = if (pose.headOnly) RinRig.HEAD_W else RinRig.W
        val srcH = if (pose.headOnly) RinRig.HEAD_H else RinRig.H
        val k = min(this.size.width / srcW, this.size.height / srcH)
        val dw = srcW * k
        val dh = srcH * k
        val dx = (this.size.width - dw) / 2
        val dy = (if (pose.headOnly) (this.size.height - dh) / 2 else this.size.height - dh) - params.hop * k
        scale(if (flip) -1f else 1f, pop.value, pivot = androidx.compose.ui.geometry.Offset(this.size.width / 2, this.size.height)) {
            if (rig != null && bmp != null && image != null) {
                rig.render(params)
                bmp.setPixels(rig.pixels, 0, RinRig.W, 0, 0, RinRig.W, RinRig.H)
                drawImage(image, srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()), dstSize = IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.None)
            } else {
                painter.accent = accent
                drawIntoCanvas { c ->
                    val nc = c.nativeCanvas
                    nc.save()
                    nc.translate(dx, dy)
                    nc.scale(k, k)
                    nc.translate(-srcX.toFloat(), -srcY.toFloat())
                    painter.draw(nc, params)
                    nc.restore()
                }
            }
        }
    }
}

/** Rin's head grooving to the beat — used in lyric breaks. */
@Composable
fun BobbingHead(active: Boolean, size: Dp, modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") bpm: Float = 104f) {
    RinSprite(if (active) Pose.DANCE_HEAD else Pose.HEAD, size, modifier, animated = true)
}
