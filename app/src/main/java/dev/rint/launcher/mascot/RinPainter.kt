package dev.rint.launcher.mascot

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rin, drawn as smooth vector art from the same [RinParams] the animations drive.
 * Coordinates are the rig's 48×56 space; the caller scales the canvas.
 * Everything is allocated once, so a frame is just path building and fills.
 */
class RinPainter {
    var accent: Int = 0xFF3B7CFF.toInt()
        set(v) { if (v != field) { field = v; shadersDirty = true } }

    private var shadersDirty = true
    private val ink = 0xFF101326.toInt()
    private val fur = 0xFFFFFFFF.toInt()
    private val furShade = 0xFFD9E0F2.toInt()
    private val hood = 0xFF1C1F2C.toInt()
    private val hoodLight = 0xFF2C3144.toInt()
    private val pink = 0x99FF8FB3.toInt()
    private val tongue = 0xFFFF7C9C.toInt()
    private val mouthDark = 0xFF3A1428.toInt()
    private val heart = 0xFFFF4F8B.toInt()
    private val star = 0xFFFFD84A.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; color = 0xFF101326.toInt() }

    private val headPath = Path(); private val tuftL = Path(); private val tuftR = Path(); private val crown = Path(); private val earL = Path(); private val earR = Path(); private val innerL = Path(); private val innerR = Path()
    private val bodyPath = Path(); private val tailPath = Path(); private val tailTip = Path(); private val tmp = Path(); private val tmp2 = Path()
    private val legPath = Path(); private val armPath = Path(); private val pawPath = Path()

    private var furShader: Shader? = null
    private var hoodShader: Shader? = null

    private fun ensureShaders() {
        if (!shadersDirty) return
        furShader = RadialGradient(-3f, -4f, 16f, intArrayOf(fur, fur, furShade), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        hoodShader = LinearGradient(16f, 32f, 34f, 48f, intArrayOf(hoodLight, hood), null, Shader.TileMode.CLAMP)
        shadersDirty = false
    }

    private fun lighter(c: Int, k: Float): Int {
        val r = (c shr 16 and 0xff); val g = (c shr 8 and 0xff); val b = (c and 0xff)
        return (0xff shl 24) or ((r + (255 - r) * k).toInt() shl 16) or ((g + (255 - g) * k).toInt() shl 8) or (b + (255 - b) * k).toInt()
    }

    /** Draws Rin into [c], already scaled so one unit = one rig pixel. */
    fun draw(c: Canvas, p: RinParams) {
        ensureShaders()
        c.save()
        if (p.squash != 1f) c.scale(1f / sqrt(p.squash), p.squash, 24f, RinRig.GROUND)
        when (p.stance) {
            Stance.STAND -> {
                tail(c, p, 15f, 46.5f + p.bodyY)
                legs(c, p)
                body(c, p, 0f)
                if (p.guitar) guitar(c, p)
                arms(c, p, 0f)
                head(c, p, 24f + p.headX, 20f + p.headY + p.bodyY)
            }
            Stance.SIT -> {
                tailSit(c, p)
                bodySit(c, p)
                if (p.guitar) guitar(c, p)
                arms(c, p, 5f)
                head(c, p, 24f + p.headX, 25f + p.headY + p.bodyY)
            }
            Stance.HEAD, Stance.PEEK -> head(c, p, 24f + p.headX, 20f + p.headY)
        }
        if (p.stance == Stance.PEEK) {
            fill.shader = null; fill.color = fur
            paw(c, 14f, 31.5f); paw(c, 34f, 31.5f)
        }
        if (p.sweat) {
            tmp.reset(); tmp.moveTo(37.5f, 5f); tmp.quadTo(39.6f, 8.6f, 37.5f, 9.6f); tmp.quadTo(35.4f, 8.6f, 37.5f, 5f)
            fill.shader = null; fill.color = lighter(accent, 0.45f); c.drawPath(tmp, fill)
        }
        c.restore()
    }

    private fun stroked(c: Canvas, path: Path, w: Float = 1.5f) {
        outline.strokeWidth = w
        c.drawPath(path, outline)
    }

    // ── head ───────────────────────────────────────────────
    private fun buildEar(dst: Path, inner: Path, side: Float, fold: Float) {
        val tipX = (12.5f - fold * 3f) * side
        val tipY = -19.5f + fold * 7f + (if (fold < 0) fold * 2f else 0f)
        dst.reset()
        dst.moveTo(2.2f * side, -8.2f)
        dst.quadTo((tipX + 2.5f * side) * 0.55f, (tipY - 8.5f) * 0.5f, tipX, tipY)
        dst.quadTo(12.8f * side, -12f, 11.8f * side, -4.5f)
        dst.close()
        inner.reset()
        inner.moveTo(4.8f * side, -8f)
        inner.quadTo((tipX + 3f * side) * 0.6f, (tipY - 6f) * 0.55f, tipX - 1.2f * side, tipY + 3.4f)
        inner.quadTo(10.8f * side, -10.5f, 10.1f * side, -6.2f)
        inner.close()
    }

    private fun head(c: Canvas, p: RinParams, hx: Float, hy: Float) {
        c.save()
        c.translate(hx, hy)
        c.rotate(Math.toDegrees(p.tilt.toDouble()).toFloat())
        buildEar(earL, innerL, -1f, p.earL)
        buildEar(earR, innerR, 1f, p.earR)
        // fluffy head: an ellipse with cheek tufts and a spiky crown
        headPath.reset()
        headPath.addOval(-12.4f, -10.6f, 12.4f, 10.4f, Path.Direction.CW)
        for (s in floatArrayOf(-1f, 1f)) {
            val tmp = if (s < 0) tuftL else tuftR
            tmp.reset()
            tmp.moveTo(11.4f * s, 0f); tmp.quadTo(13.6f * s, 2.6f, 15.2f * s, 4.2f); tmp.quadTo(13.4f * s, 5f, 14f * s, 7.6f)
            tmp.quadTo(11.6f * s, 7.8f, 9.6f * s, 9.6f); tmp.lineTo(7f * s, 7f); tmp.close()
        }
        val tmp = crown
        tmp.reset(); tmp.moveTo(-5f, -8.8f); tmp.quadTo(-4.5f, -12f, -3f, -14.2f); tmp.quadTo(-1.2f, -11f, 0f, -9.4f)
        tmp.quadTo(1.2f, -11.2f, 2.4f, -13.4f); tmp.quadTo(3.8f, -11f, 4.6f, -8.6f); tmp.close()

        // outline pass (silhouette), then fills
        stroked(c, earL, 2.2f); stroked(c, earR, 2.2f); stroked(c, headPath, 2.2f)
        stroked(c, tuftL, 2.2f); stroked(c, tuftR, 2.2f); stroked(c, crown, 2.2f)
        fill.color = fur; fill.shader = furShader
        c.drawPath(earL, fill); c.drawPath(earR, fill)
        fill.color = fur
        fill.shader = LinearGradient(0f, -18f, 0f, -6f, lighter(accent, 0.35f), accent, Shader.TileMode.CLAMP)
        c.drawPath(innerL, fill); c.drawPath(innerR, fill)
        fill.shader = furShader
        c.drawPath(headPath, fill); c.drawPath(tuftL, fill); c.drawPath(tuftR, fill); c.drawPath(crown, fill)
        fill.shader = null
        // soft underside shade
        fill.color = 0x22324066
        c.drawOval(-10f, 5.5f, 10f, 10.6f, fill)
        fill.color = fur

        if (p.blush > 0.05f) {
            fill.color = pink
            fill.alpha = (0x99 * p.blush.coerceIn(0f, 1f)).toInt()
            c.drawOval(-10.3f, 3.6f, -6.1f, 5.8f, fill); c.drawOval(6.1f, 3.6f, 10.3f, 5.8f, fill)
            fill.alpha = 255
        }
        eye(c, p, -5.6f); eye(c, p, 5.6f)
        mouth(c, p)
        c.restore()
    }

    private fun eye(c: Canvas, p: RinParams, ex: Float) {
        val cx = ex + p.lookX * 1.3f
        val cy = 0.9f + p.lookY * 1.1f
        fill.shader = null
        line.color = ink; line.strokeWidth = 1.2f
        when (p.eyes) {
            Eyes.OPEN, Eyes.SHOCK, Eyes.MEH -> {
                val shock = p.eyes == Eyes.SHOCK
                val rx = if (shock) 3f else 2.7f
                val ry = (if (shock) 3.4f else 4.1f) * p.eyeOpen.coerceIn(0f, 1f)
                if (ry < 0.9f) { c.drawLine(cx - 2.3f, cy + 0.8f, cx + 2.3f, cy + 0.8f, line); return }
                fill.color = fur
                fill.shader = LinearGradient(0f, cy - ry, 0f, cy + ry, intArrayOf(ink, ink, lighter(accent, 0.1f)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                c.save()
                if (p.eyes == Eyes.MEH) c.clipRect(cx - rx - 1, cy - 1.2f, cx + rx + 1, cy + ry + 1)
                c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, fill)
                c.restore()
                fill.shader = null; fill.color = fur
                c.drawOval(cx - 1.75f, cy - ry * 0.72f, cx + 0.25f, cy - ry * 0.72f + 2.1f, fill)
                c.drawCircle(cx + 1.05f, cy + ry * 0.35f, if (shock) 0.75f else 0.5f, fill)
                if (p.eyes == Eyes.MEH) c.drawLine(cx - rx, cy - 1.2f, cx + rx, cy - 1.2f, line)
            }
            Eyes.HAPPY -> {
                tmp.reset(); tmp.moveTo(cx - 2.5f, cy + 1.2f); tmp.quadTo(cx, cy - 2.6f, cx + 2.5f, cy + 1.2f)
                line.strokeWidth = 1.4f; c.drawPath(tmp, line)
            }
            Eyes.CLOSED -> {
                tmp.reset(); tmp.moveTo(cx - 2.5f, cy + 0.4f); tmp.quadTo(cx, cy + 2.6f, cx + 2.5f, cy + 0.4f)
                line.strokeWidth = 1.3f; c.drawPath(tmp, line)
            }
            Eyes.HEART -> {
                tmp.reset()
                tmp.moveTo(cx, cy + 2.6f)
                tmp.cubicTo(cx - 3.6f, cy, cx - 2.4f, cy - 3.2f, cx, cy - 1.2f)
                tmp.cubicTo(cx + 2.4f, cy - 3.2f, cx + 3.6f, cy, cx, cy + 2.6f)
                fill.color = heart; c.drawPath(tmp, fill)
                fill.color = fur; c.drawCircle(cx - 1.2f, cy - 1f, 0.5f, fill)
            }
            Eyes.STAR -> {
                tmp.reset()
                for (i in 0 until 8) {
                    val a = (i * Math.PI / 4 - Math.PI / 2).toFloat()
                    val r = if (i % 2 == 0) 3f else 1.1f
                    val x = cx + cos(a) * r; val y = cy + sin(a) * r
                    if (i == 0) tmp.moveTo(x, y) else tmp.lineTo(x, y)
                }
                tmp.close(); fill.color = star; c.drawPath(tmp, fill)
            }
        }
    }

    private fun mouth(c: Canvas, p: RinParams) {
        val open = p.mouth.coerceIn(0f, 1f)
        fill.shader = null
        if (open > 0.12f) {
            val ry = 0.8f + open * 1.9f
            val rx = 1.6f + open * 0.8f
            val cy = 6.6f + ry * 0.45f
            fill.color = mouthDark
            c.drawOval(-rx, cy - ry, rx, cy + ry, fill)
            if (open > 0.4f) { fill.color = tongue; c.drawOval(-rx * 0.7f, cy, rx * 0.7f, cy + ry, fill) }
            return
        }
        // ω
        tmp.reset()
        tmp.moveTo(-2.3f, 6f); tmp.quadTo(-1.2f, 8.2f, 0f, 6.3f); tmp.quadTo(1.2f, 8.2f, 2.3f, 6f)
        line.color = 0xFF5C678C.toInt(); line.strokeWidth = 0.9f
        c.drawPath(tmp, line)
        fill.color = 0xFF5C678C.toInt(); c.drawOval(-0.8f, 4.6f, 0.8f, 5.6f, fill)
    }

    // ── body ───────────────────────────────────────────────
    private fun emblem(c: Canvas, x: Float, y: Float) {
        tmp.reset()
        tmp.moveTo(x, y - 2.4f); tmp.quadTo(x + 0.35f, y - 0.35f, x + 2.4f, y); tmp.quadTo(x + 0.35f, y + 0.35f, x, y + 2.4f)
        tmp.quadTo(x - 0.35f, y + 0.35f, x - 2.4f, y); tmp.quadTo(x - 0.35f, y - 0.35f, x, y - 2.4f)
        fill.shader = null; fill.color = accent; c.drawPath(tmp, fill)
    }

    private fun body(c: Canvas, p: RinParams, dy: Float) {
        val by = p.bodyY + dy
        bodyPath.reset()
        bodyPath.moveTo(15.5f, 35f + by)
        bodyPath.quadTo(24f, 31f + by, 32.5f, 35f + by)
        bodyPath.quadTo(34f, 42f + by, 33.2f, 47.2f + by)
        bodyPath.quadTo(24f, 48.8f + by, 14.8f, 47.2f + by)
        bodyPath.quadTo(14f, 42f + by, 15.5f, 35f + by)
        bodyPath.close()
        stroked(c, bodyPath, 2.2f)
        fill.color = fur; fill.shader = hoodShader; c.drawPath(bodyPath, fill); fill.shader = null
        // hood collar
        fill.color = hoodLight; c.drawOval(15.5f, 32.4f + by, 32.5f, 36.6f + by, fill)
        // pocket
        line.color = 0xFF0E1019.toInt(); line.strokeWidth = 0.6f
        c.drawLine(18.5f, 43.4f + by, 29.5f, 43.4f + by, line)
        // zipper + pull
        line.color = accent; line.strokeWidth = 0.9f
        c.drawLine(24f, 35.6f + by, 24f, 47.4f + by, line)
        fill.color = lighter(accent, 0.4f); c.drawRoundRect(23.5f, 36.2f + by, 25.8f, 38.2f + by, 0.5f, 0.5f, fill)
        // drawstrings
        line.color = 0xFFE9ECF5.toInt(); line.strokeWidth = 0.6f
        c.drawLine(21.6f, 35.8f + by, 21.2f, 39.6f + by, line); c.drawLine(26.4f, 35.8f + by, 26.8f, 39.6f + by, line)
        emblem(c, 29f, 40.2f + by)
    }

    private fun legs(c: Canvas, p: RinParams) {
        for (side in 0..1) {
            val ph = if (side == 0) p.legL else p.legR
            val lift = max(0f, ph) * 2.2f
            val x = if (side == 0) 20f + ph * 0.8f else 28f - ph * 0.8f
            legPath.reset()
            legPath.addRoundRect(x - 2.4f, 45.5f + p.bodyY, x + 2.4f, 53f - lift + p.bodyY, 1.6f, 1.6f, Path.Direction.CW)
            legPath.addOval(x - 3.3f + (if (side == 0) -0.3f else 0.3f), 50.9f - lift + p.bodyY, x + 3.3f + (if (side == 0) -0.3f else 0.3f), 54.7f - lift + p.bodyY, Path.Direction.CW)
            stroked(c, legPath, 2f)
            fill.shader = null; fill.color = if (side == 1) furShade else fur
            c.drawPath(legPath, fill)
            line.color = 0xFFB9C2DB.toInt(); line.strokeWidth = 0.45f
            c.drawLine(x - 1f, 53.3f - lift + p.bodyY, x - 1f, 54.4f - lift + p.bodyY, line)
            c.drawLine(x + 1f, 53.3f - lift + p.bodyY, x + 1f, 54.4f - lift + p.bodyY, line)
        }
    }

    private fun paw(c: Canvas, x: Float, y: Float) {
        pawPath.reset(); pawPath.addOval(x - 2.4f, y - 2.2f, x + 2.4f, y + 2.2f, Path.Direction.CW)
        stroked(c, pawPath, 1.6f)
        fill.shader = null; fill.color = fur; c.drawPath(pawPath, fill)
    }

    private fun arms(c: Canvas, p: RinParams, dy: Float) {
        val sy = 36f + p.bodyY + dy
        for (side in floatArrayOf(-1f, 1f)) {
            val raise = if (side < 0) p.armL else p.armR
            val wave = if (side < 0) 0f else p.armWave
            val sx = if (side < 0) 16f else 32f
            val a = (0.25f + raise * 2.4f + wave) * side
            var hx = sx + sin(a) * 9.5f
            var hy = sy + cos(a) * 9.5f
            if (p.guitar) {
                // fretting hand on the neck, strumming hand over the sound hole
                if (side < 0) { hx = 14.5f; hy = 38.5f + p.bodyY + dy } else { hx = 27.5f; hy = 43.5f + p.bodyY + dy + p.strum * 2.4f }
            }
            armPath.reset(); armPath.moveTo(sx, sy); armPath.lineTo(hx, hy)
            outline.strokeWidth = 6.2f; c.drawPath(armPath, outline)
            line.color = if (side > 0) hood else hoodLight; line.strokeWidth = 4.4f
            c.drawPath(armPath, line)
            paw(c, hx, hy)
        }
    }

    private fun guitar(c: Canvas, p: RinParams) {
        c.save()
        val by = p.bodyY + (if (p.stance == Stance.SIT) 5f else 0f)
        c.translate(26f, 43f + by)
        c.rotate(-28f)
        // neck + head
        tmp.reset(); tmp.addRoundRect(-19f, -1f, -4f, 1f, 0.6f, 0.6f, Path.Direction.CW)
        tmp.addRoundRect(-23f, -1.8f, -18.5f, 1.8f, 0.8f, 0.8f, Path.Direction.CW)
        stroked(c, tmp, 1.6f)
        fill.shader = null; fill.color = 0xFF6B3F22.toInt(); c.drawPath(tmp, fill)
        // body
        tmp2.reset()
        tmp2.addCircle(-2f, 0f, 4.4f, Path.Direction.CW)
        tmp2.addCircle(4.4f, 0f, 5.6f, Path.Direction.CW)
        stroked(c, tmp2, 1.8f)
        fill.color = accent; c.drawPath(tmp2, fill)
        fill.color = 0xFF6B3F22.toInt(); c.drawRoundRect(-19f, -1f, -4f, 1f, 0.6f, 0.6f, fill)
        fill.color = 0x33FFFFFF; c.drawCircle(3.2f, -2f, 3f, fill)
        fill.color = ink; c.drawCircle(1f, 0f, 1.6f, fill)
        fill.color = 0xFF2A1A10.toInt(); c.drawRect(6.2f, -2.2f, 7f, 2.2f, fill)
        // strings, vibrating while strummed
        line.color = 0xCCFFFFFF.toInt(); line.strokeWidth = 0.18f
        for (i in -1..1) {
            val wob = p.strum * 0.35f * sin(i * 2.1f + p.strum * 40f)
            c.drawLine(-22f, i * 0.45f, 6.6f, i * 0.45f + wob, line)
        }
        c.restore()
    }

    private fun tail(c: Canvas, p: RinParams, baseX: Float, baseY: Float) {
        tailPath.reset()
        val n = 18
        var tipStart = 0f to 0f
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val ang = -2.65f + p.tail + t * 1.3f
            val r = t * 14f
            val cx = baseX + cos(ang) * r * 0.9f
            val cy = baseY + sin(ang) * r - t * t * 3f
            val rad = (1.8f + 3.6f * sin(t * 3.1f).coerceAtLeast(0.35f))
            tailPath.addCircle(cx, cy, rad, Path.Direction.CW)
            if (i == 11) tipStart = cx to cy
        }
                stroked(c, tailPath, 2.2f)
        fill.color = fur
        val tip = baseX + cos(-2.65f + p.tail + 1.3f) * 12.6f to baseY + sin(-2.65f + p.tail + 1.3f) * 14f - 3f
        fill.shader = LinearGradient(tipStart.first, tipStart.second, tip.first, tip.second, intArrayOf(fur, accent, lighter(accent, 0.3f)), floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(tailPath, fill)
        fill.shader = null
    }

    private fun bodySit(c: Canvas, p: RinParams) {
        val by = p.bodyY
        bodyPath.reset()
        bodyPath.addOval(14f, 38.5f + by, 34f, 53.5f + by, Path.Direction.CW)
        bodyPath.addOval(13.5f, 35.8f + by, 34.5f, 42.2f + by, Path.Direction.CW)
                stroked(c, bodyPath, 2.2f)
        fill.color = fur; fill.shader = hoodShader; c.drawPath(bodyPath, fill); fill.shader = null
        line.color = accent; line.strokeWidth = 0.9f
        c.drawLine(24f, 40f + by, 24f, 51f + by, line)
        emblem(c, 29f, 45f + by)
        for (x in floatArrayOf(19f, 29f)) {
            pawPath.reset(); pawPath.addOval(x - 3.6f, 50.3f + by, x + 3.6f, 54.7f + by, Path.Direction.CW)
            stroked(c, pawPath, 1.8f); fill.color = fur; c.drawPath(pawPath, fill)
        }
    }

    private fun tailSit(c: Canvas, p: RinParams) {
        tailPath.reset()
        val n = 18
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val ang = 3.4f - t * 2.6f + p.tail * 0.5f
            val cx = 24f + cos(ang) * (15f - t * 3f)
            val cy = 50f + p.bodyY + sin(ang) * (6f - t * 2f)
            tailPath.addCircle(cx, cy, 2.2f + 2.6f * sin(t * 3.1f).coerceAtLeast(0.4f), Path.Direction.CW)
        }
                stroked(c, tailPath, 2.2f)
        fill.color = fur
        fill.shader = LinearGradient(9f, 50f, 38f, 48f, intArrayOf(fur, fur, accent), floatArrayOf(0f, 0.6f, 0.8f), Shader.TileMode.CLAMP)
        c.drawPath(tailPath, fill); fill.shader = null
    }
}
