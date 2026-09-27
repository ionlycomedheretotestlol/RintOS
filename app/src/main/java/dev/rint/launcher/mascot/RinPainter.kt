package dev.rint.launcher.mascot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Rin as flat 2D cartoon art (thin ink lines, flat fills), drawn from [RinParams] every frame.
 * Head-only poses get a subtle 2.5D edge like the app icon. Coordinates are the rig's
 * 48×56 space (ground at y=55); the caller scales the canvas.
 */
class RinPainter {
    var accent: Int = 0xFF3B7CFF.toInt()

    private val ink = 0xFF0C0D12.toInt()
    private val fur = 0xFFFFFFFF.toInt()
    private val furShade = 0xFFE3E7F3.toInt()
    private val depth = 0xFFB9C1E3.toInt()
    private val hood = 0xFF121318.toInt()
    private val hoodFold = 0xFF3C4050.toInt()
    private val collar = 0xFF1A1B22.toInt()
    private val pink = 0xFFFF9DBB.toInt()
    private val heart = 0xFFFF4F8B.toInt()
    private val star = 0xFFFFD84A.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    private val headPath = Path(); private val earInL = Path(); private val earInR = Path()
    private val bodyPath = Path(); private val tailPath = Path(); private val tipPath = Path()
    private val a = Path(); private val b = Path()

    private fun lighter(c: Int, k: Float): Int {
        val r = (c shr 16 and 0xff); val g = (c shr 8 and 0xff); val bl = (c and 0xff)
        return (0xff shl 24) or ((r + (255 - r) * k).toInt() shl 16) or ((g + (255 - g) * k).toInt() shl 8) or (bl + (255 - bl) * k).toInt()
    }

    /** Ink outline behind a shape (half of it shows outside the fill). */
    private fun ink(c: Canvas, path: Path, w: Float = 1.25f) {
        edge.color = ink; edge.strokeWidth = w
        c.drawPath(path, edge)
    }

    private fun solid(c: Canvas, path: Path, color: Int) {
        fill.shader = null; fill.color = color
        c.drawPath(path, fill)
    }

    private fun stroke(c: Canvas, path: Path, color: Int, w: Float) {
        line.color = color; line.strokeWidth = w
        c.drawPath(path, line)
    }

    fun draw(c: Canvas, p: RinParams) {
        c.save()
        if (p.squash != 1f) c.scale(1f / kotlin.math.sqrt(p.squash), p.squash, 24f, RinRig.GROUND)
        when (p.stance) {
            Stance.STAND -> {
                tail(c, p)
                legs(c, p)
                body(c, p)
                if (p.guitar) guitar(c, p)
                arms(c, p, 0f)
                head(c, p, 24f + p.headX, 19.5f + p.headY + p.bodyY, false)
            }
            Stance.SIT -> {
                tailSit(c, p)
                bodySit(c, p)
                if (p.guitar) guitar(c, p)
                arms(c, p, 4.5f)
                head(c, p, 24f + p.headX, 24.5f + p.headY + p.bodyY, false)
            }
            Stance.HEAD, Stance.PEEK -> head(c, p, 24f + p.headX, 19.5f + p.headY, true)
        }
        if (p.stance == Stance.PEEK) { paw(c, 15f, 31.6f); paw(c, 33f, 31.6f) }
        if (p.sweat) {
            a.reset(); a.moveTo(38f, 4.5f); a.quadTo(40.2f, 8.2f, 38f, 9.2f); a.quadTo(35.8f, 8.2f, 38f, 4.5f)
            ink(c, a, 0.8f); solid(c, a, lighter(accent, 0.35f))
        }
        c.restore()
        effects(c, p)
    }

    // ───────────────────────── head ─────────────────────────

    private fun earTip(fold: Float, side: Float): Pair<Float, Float> {
        val x = (12.6f - fold * 3.2f) * side
        val y = -19.6f + fold * 8f + (if (fold < 0) fold * 1.5f else 0f)
        return x to y
    }

    /** Round head with a few fluffy tufts, tall ears and a spiky little crown (the concept sheet). */
    private fun buildHead(p: RinParams) {
        val (lx, ly) = earTip(p.earL, -1f)
        val (rx, ry) = earTip(p.earR, 1f)
        val h = headPath
        h.reset()
        h.moveTo(0f, 10.6f)
        h.quadTo(5f, 10.8f, 8.2f, 9.2f)
        h.lineTo(10.6f, 11.2f)          // jaw tuft
        h.lineTo(11f, 8f)
        h.quadTo(12.8f, 6.6f, 13.4f, 5f)
        h.lineTo(16.4f, 5.6f)           // cheek tuft
        h.lineTo(13.9f, 2.4f)
        h.quadTo(14.6f, -1.2f, 13.2f, -4.4f)
        h.quadTo(14.9f, -11.5f, rx, ry) // right ear
        h.quadTo(9.6f, -15.4f, 5.6f, -10.6f)
        h.quadTo(4.2f, -11.4f, 2.8f, -11.5f)
        h.lineTo(1.9f, -14.8f)          // crown tufts
        h.lineTo(0.2f, -11.7f)
        h.lineTo(-1.6f, -13.6f)
        h.lineTo(-2.6f, -11.3f)
        h.quadTo(-4.2f, -11.3f, -5.6f, -10.6f)
        h.quadTo(-9.6f, -15.4f, lx, ly) // left ear
        h.quadTo(-14.9f, -11.5f, -13.2f, -4.4f)
        h.quadTo(-14.6f, -1.2f, -13.9f, 2.4f)
        h.lineTo(-16.6f, 3.4f)          // cheek tuft
        h.lineTo(-13.6f, 5.6f)
        h.lineTo(-15.4f, 8.4f)          // lower cheek tuft
        h.lineTo(-11.4f, 8.4f)
        h.lineTo(-10.2f, 11.4f)         // jaw tuft
        h.lineTo(-8.2f, 9.4f)
        h.quadTo(-5f, 10.8f, 0f, 10.6f)
        h.close()

        // the blue lives along the outer edge of each ear
        fun inner(path: Path, s: Float, tx: Float, ty: Float) {
            path.reset()
            path.moveTo(tx - 0.9f * s, ty + 2.6f)
            path.quadTo(12.9f * s, -12f, 12.2f * s, -6.4f)
            path.quadTo(10.6f * s, -8.2f, 9.6f * s, -9.6f)
            path.quadTo(10.8f * s, -13f, tx - 0.9f * s, ty + 2.6f)
            path.close()
        }
        inner(earInL, -1f, lx, ly)
        inner(earInR, 1f, rx, ry)
    }

    private var hcx = 24f
    private var hcy = 19.5f

    private fun effects(c: Canvas, p: RinParams) {
        val t = p.time
        fill.shader = null
        if (p.tears) for (side in listOf(-1f, 1f)) {
            val fall = (t * 7f + if (side > 0) 2.5f else 0f) % 7f
            a.reset(); val x = hcx + side * 6.4f; val y = hcy + 4f + fall
            a.moveTo(x, y - 1.4f); a.quadTo(x + 1f, y + 0.6f, x, y + 0.9f); a.quadTo(x - 1f, y + 0.6f, x, y - 1.4f)
            solid(c, a, 0xFF8EC5FF.toInt())
        }
        if (p.steam) for (i in 0..1) {
            val ph = (t * 1.6f + i * 0.5f) % 1f
            if (ph > 0.85f) continue
            val side = if (i == 0) -1f else 1f
            fill.color = 0xFFD7DCEA.toInt(); fill.alpha = (255 * (1f - ph)).toInt()
            c.drawCircle(hcx + side * (9f + ph * 2f), hcy - 13f - ph * 7f, 1.3f + ph * 1.2f, fill); fill.alpha = 255
        }
        if (p.notes) for (i in 0..1) {
            val ph = (t * 0.7f + i * 0.5f) % 1f
            val x = hcx + 13f + ph * 4f + sin(ph * 12f) * 1.2f; val y = hcy - 6f - ph * 14f
            fill.color = accent; c.drawOval(x - 1.3f, y + 2f, x + 0.3f, y + 3.2f, fill)
            line.color = accent; line.strokeWidth = 0.45f; c.drawLine(x + 0.1f, y + 2.6f, x + 0.1f, y - 0.8f, line); c.drawLine(x + 0.1f, y - 0.8f, x + 1.4f, y - 0.2f, line)
        }
        if (p.zzz) for (i in 0..2) {
            val ph = (t * 0.5f + i / 3f) % 1f
            if (ph > 0.8f) continue
            val x = hcx + 11f + ph * 6f; val y = hcy - 8f - ph * 12f; val s = 1f + ph * 1.2f
            line.color = 0xFFB8C0DA.toInt(); line.strokeWidth = 0.45f
            c.drawLine(x, y, x + s, y, line); c.drawLine(x + s, y, x, y + s, line); c.drawLine(x, y + s, x + s, y + s, line)
        }
        if (p.sparkles) for (i in 0..3) {
            val ph = (t * 1.3f + i * 0.25f) % 1f
            if (ph > 0.6f) continue
            val an = i * 1.7f + 0.4f; val r = 1.6f * sin(ph / 0.6f * PI.toFloat())
            val x = hcx + cos(an) * 17f; val y = hcy + sin(an) * 13f - 2f
            a.reset(); a.moveTo(x, y - r * 1.6f); a.quadTo(x, y, x + r, y); a.quadTo(x, y, x, y + r * 1.6f); a.quadTo(x, y, x - r, y); a.quadTo(x, y, x, y - r * 1.6f)
            solid(c, a, star)
        }
        if (p.food) {
            fill.color = 0xFFFFA24A.toInt(); c.drawCircle(hcx + 4.5f, hcy + 9.4f, 1.8f, fill)
            fill.color = 0xFFC66B1E.toInt(); c.drawCircle(hcx + 5.2f, hcy + 9f, 0.5f, fill)
        }
    }

    private fun head(c: Canvas, p: RinParams, hx: Float, hy: Float, depth25: Boolean) {
        hcx = hx; hcy = hy
        c.save()
        c.translate(hx, hy)
        c.rotate(Math.toDegrees(p.tilt.toDouble()).toFloat())
        c.scale(0.86f, 1.04f)
        buildHead(p)
        if (depth25) {
            // 2.5D: a thick lavender side, like the app icon
            for (k in 3 downTo 1) {
                c.save(); c.translate(-0.4f * k, 0.4f * k)
                if (k == 3) ink(c, headPath, 1.3f)
                solid(c, headPath, depth)
                c.restore()
            }
        }
        ink(c, headPath, 1.3f)
        solid(c, headPath, fur)
        if (!depth25) {
            a.reset(); a.addOval(-9f, 6.6f, 9f, 10.8f, Path.Direction.CW)
            c.save(); c.clipPath(headPath); solid(c, a, furShade); c.restore()
        }
        solid(c, earInL, accent); solid(c, earInR, accent)
        a.reset(); a.moveTo(-6.6f, -9.4f); a.quadTo(-8.4f, -11.8f, -9.4f, -13.2f)
        b.reset(); b.moveTo(6.6f, -9.4f); b.quadTo(8.4f, -11.8f, 9.4f, -13.2f)
        stroke(c, a, ink, 0.45f); stroke(c, b, ink, 0.45f)

        if (p.blush > 0.05f) {
            fill.shader = null; fill.color = pink; fill.alpha = (150 * p.blush.coerceIn(0f, 1f)).toInt()
            c.drawOval(-11.4f, 4.6f, -7.6f, 6.6f, fill); c.drawOval(7.6f, 4.6f, 11.4f, 6.6f, fill)
            fill.alpha = 255
        }
        eye(c, p, -5.6f); eye(c, p, 5.6f)
        mouth(c, p)
        c.restore()
    }

    private fun eye(c: Canvas, p: RinParams, ex: Float) {
        val side = if (ex < 0) -1f else 1f
        val cx = ex + p.lookX * 1.4f
        val cy = 0.4f + p.lookY * 1.1f
        line.color = ink
        when (p.eyes) {
            Eyes.WINK -> {
                if (side > 0) { a.reset(); a.moveTo(cx - 2.5f, cy + 0.4f); a.quadTo(cx, cy + 2.4f, cx + 2.5f, cy + 0.4f); stroke(c, a, ink, 0.75f) }
                else { fill.shader = null; fill.color = ink; c.drawOval(cx - 2.45f, cy - 4.3f, cx + 2.45f, cy + 4.3f, fill); fill.color = fur; c.drawCircle(cx - 0.8f, cy - 1.8f, 0.8f, fill) }
            }
            Eyes.SAD, Eyes.ANGRY -> {
                val slope = if (p.eyes == Eyes.SAD) 0.55f * side else -0.55f * side
                a.reset(); a.moveTo(cx - 4f, cy - 1.1f - 4f * slope); a.lineTo(cx + 4f, cy - 1.1f + 4f * slope); a.lineTo(cx + 4f, cy + 6f); a.lineTo(cx - 4f, cy + 6f); a.close()
                c.save(); c.clipPath(a)
                fill.shader = null; fill.color = ink; c.drawOval(cx - 2.45f, cy - 4.3f, cx + 2.45f, cy + 4.3f, fill)
                c.restore()
                fill.color = fur; c.drawCircle(cx - 0.6f, cy + 0.8f, 0.7f, fill)
                line.color = ink; line.strokeWidth = 0.7f
                c.drawLine(cx - 2.9f, cy - 1.6f - 2.9f * slope, cx + 2.9f, cy - 1.6f + 2.9f * slope, line)
            }
            Eyes.OPEN, Eyes.SHOCK, Eyes.MEH -> {
                val shock = p.eyes == Eyes.SHOCK
                val rx = if (shock) 2.9f else 2.45f
                val ry = (if (shock) 3.6f else 4.3f) * p.eyeOpen.coerceIn(0f, 1f)
                // the brow line that hugs the outer top of each eye
                a.reset()
                a.moveTo(ex - 1.6f * side, cy - 5.9f)
                a.quadTo(ex + 4.6f * side, cy - 6.4f, ex + 4.4f * side, cy + 1.8f)
                stroke(c, a, ink, 0.55f)
                if (ry < 0.9f) { line.strokeWidth = 0.9f; c.drawLine(cx - 2.2f, cy + 0.8f, cx + 2.2f, cy + 0.8f, line); return }
                fill.shader = null; fill.color = ink
                c.save()
                if (p.eyes == Eyes.MEH) c.clipRect(cx - rx - 1, cy - 0.8f, cx + rx + 1, cy + ry + 1)
                c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, fill)
                c.restore()
                if (p.eyes == Eyes.MEH) { line.strokeWidth = 0.7f; c.drawLine(cx - rx - 0.4f, cy - 0.8f, cx + rx + 0.4f, cy - 0.8f, line) }
                if (shock) { fill.color = fur; c.drawCircle(cx, cy, 0.9f, fill) }
            }
            Eyes.HAPPY -> {
                a.reset(); a.moveTo(cx - 2.6f, cy + 1.4f); a.quadTo(cx, cy - 3.4f, cx + 2.6f, cy + 1.4f)
                stroke(c, a, ink, 0.8f)
            }
            Eyes.CLOSED -> {
                a.reset(); a.moveTo(cx - 2.5f, cy + 0.4f); a.quadTo(cx, cy + 2.4f, cx + 2.5f, cy + 0.4f)
                stroke(c, a, ink, 0.75f)
            }
            Eyes.HEART -> {
                a.reset()
                a.moveTo(cx, cy + 2.6f)
                a.cubicTo(cx - 3.6f, cy, cx - 2.4f, cy - 3.2f, cx, cy - 1.2f)
                a.cubicTo(cx + 2.4f, cy - 3.2f, cx + 3.6f, cy, cx, cy + 2.6f)
                solid(c, a, heart)
            }
            Eyes.STAR -> {
                a.reset()
                for (i in 0 until 8) {
                    val ang = (i * PI / 4 - PI / 2).toFloat()
                    val r = if (i % 2 == 0) 3.1f else 1.1f
                    val x = cx + cos(ang) * r; val y = cy + sin(ang) * r
                    if (i == 0) a.moveTo(x, y) else a.lineTo(x, y)
                }
                a.close(); solid(c, a, star)
            }
        }
    }

    private fun mouth(c: Canvas, p: RinParams) {
        val open = p.mouth.coerceIn(0f, 1f)
        if (open > 0.12f) {
            // open mouth with the blue tongue from the concept sheet
            val w = 1.5f + open * 0.9f
            val h = 1f + open * 2.2f
            a.reset()
            a.moveTo(-w, 6f); a.quadTo(0f, 6.6f, w, 6f); a.quadTo(w * 0.9f, 6f + h * 1.4f, 0f, 6f + h * 1.4f); a.quadTo(-w * 0.9f, 6f + h * 1.4f, -w, 6f); a.close()
            ink(c, a, 0.9f); solid(c, a, 0xFF2A1020.toInt())
            if (open > 0.35f) {
                b.reset(); b.addOval(-w * 0.7f, 6f + h * 0.55f, w * 0.7f, 6f + h * 1.45f, Path.Direction.CW)
                c.save(); c.clipPath(a); solid(c, b, lighter(accent, 0.25f)); c.restore()
            }
            return
        }
        a.reset()
        a.moveTo(-2.1f, 5.9f); a.quadTo(-1.1f, 7.7f, 0f, 6.3f); a.quadTo(1.1f, 7.7f, 2.1f, 5.9f)
        stroke(c, a, ink, 0.55f)
    }

    // ───────────────────────── body ─────────────────────────

    private fun emblem(c: Canvas, x: Float, y: Float, r: Float = 1.8f) {
        a.reset()
        a.moveTo(x, y - r); a.quadTo(x + r * 0.16f, y - r * 0.16f, x + r * 0.9f, y); a.quadTo(x + r * 0.16f, y + r * 0.16f, x, y + r)
        a.quadTo(x - r * 0.16f, y + r * 0.16f, x - r * 0.9f, y); a.quadTo(x - r * 0.16f, y - r * 0.16f, x, y - r); a.close()
        solid(c, a, accent)
    }

    private fun body(c: Canvas, p: RinParams) {
        val by = p.bodyY
        bodyPath.reset()
        bodyPath.moveTo(15.4f, 32f + by)
        bodyPath.quadTo(24f, 29.6f + by, 32.6f, 32f + by)
        bodyPath.quadTo(36f, 37.4f + by, 35f, 43.4f + by)
        bodyPath.quadTo(24f, 45.4f + by, 13f, 43.4f + by)
        bodyPath.quadTo(12f, 37.4f + by, 15.4f, 32f + by)
        bodyPath.close()
        ink(c, bodyPath); solid(c, bodyPath, hood)
        a.reset(); a.moveTo(13.6f, 42f + by); a.quadTo(24f, 43.8f + by, 34.4f, 42f + by)
        stroke(c, a, hoodFold, 0.4f)
        val handsIn = p.armL < 0.05f && p.armR < 0.05f && p.armWave == 0f && !p.guitar
        if (handsIn) {
            a.reset(); a.moveTo(15.8f, 33.6f + by); a.quadTo(13.4f, 37.6f + by, 17.4f, 40f + by)
            b.reset(); b.moveTo(32.2f, 33.6f + by); b.quadTo(34.6f, 37.6f + by, 30.6f, 40f + by)
            stroke(c, a, hoodFold, 0.45f); stroke(c, b, hoodFold, 0.45f)
            a.reset(); a.moveTo(16.8f, 41.6f + by); a.quadTo(17.4f, 37.8f + by, 20.4f, 37.3f + by)
            a.lineTo(27.6f, 37.3f + by); a.quadTo(30.6f, 37.8f + by, 31.2f, 41.6f + by)
            stroke(c, a, hoodFold, 0.45f)
        }
        a.reset()
        a.moveTo(16.4f, 33.8f + by); a.quadTo(15.6f, 29.4f + by, 19.6f, 29.2f + by); a.quadTo(24f, 30.8f + by, 28.4f, 29.2f + by)
        a.quadTo(32.4f, 29.4f + by, 31.6f, 33.8f + by); a.quadTo(24f, 35.8f + by, 16.4f, 33.8f + by); a.close()
        ink(c, a, 1f); solid(c, a, collar)
        b.reset(); b.moveTo(19.4f, 31.2f + by); b.quadTo(20.2f, 32.8f + by, 19.8f, 34.4f + by)
        stroke(c, b, hoodFold, 0.4f)
        b.reset(); b.moveTo(28.6f, 31.2f + by); b.quadTo(27.8f, 32.8f + by, 28.2f, 34.4f + by)
        stroke(c, b, hoodFold, 0.4f)
        line.color = accent; line.strokeWidth = 0.7f
        c.drawLine(24f, 34.8f + by, 24f, 41.2f + by, line)
        line.strokeWidth = 0.45f
        c.drawRect(23.1f, 36.2f + by, 24.9f, 37.6f + by, line)
        emblem(c, 29.6f, 39.6f + by)
    }

    private fun legs(c: Canvas, p: RinParams) {
        for (side in 0..1) {
            val ph = if (side == 0) p.legL else p.legR
            val lift = max(0f, ph) * 2.4f
            val x = if (side == 0) 20f + ph * 0.9f else 28f - ph * 0.9f
            val top = 41.5f + p.bodyY
            val bot = 54.6f - lift
            a.reset()
            a.moveTo(x - 2.4f, top)
            a.lineTo(x - 2.4f, bot - 1.8f)
            a.quadTo(x - 3.3f, bot, x - 0.6f, bot)
            a.lineTo(x + 1.4f, bot)
            a.quadTo(x + 3.2f, bot, x + 2.4f, bot - 2f)
            a.lineTo(x + 2.4f, top)
            a.close()
            ink(c, a, 1.2f)
            solid(c, a, if (side == 1) furShade else fur)
            line.color = ink; line.strokeWidth = 0.35f
            c.drawLine(x - 0.5f, bot - 0.9f, x - 0.5f, bot - 0.1f, line)
            c.drawLine(x + 0.8f, bot - 0.9f, x + 0.8f, bot - 0.1f, line)
        }
    }

    private fun paw(c: Canvas, x: Float, y: Float) {
        a.reset(); a.addOval(x - 2.3f, y - 2f, x + 2.3f, y + 2f, Path.Direction.CW)
        ink(c, a, 1.1f); solid(c, a, fur)
        line.color = ink; line.strokeWidth = 0.35f
        c.drawLine(x - 0.6f, y + 0.4f, x - 0.6f, y + 1.6f, line); c.drawLine(x + 0.6f, y + 0.4f, x + 0.6f, y + 1.6f, line)
    }

    private fun arms(c: Canvas, p: RinParams, dy: Float) {
        val sy = 33.6f + p.bodyY + dy
        for (side in floatArrayOf(-1f, 1f)) {
            val raise = if (side < 0) p.armL else p.armR
            val wave = if (side < 0) 0f else p.armWave
            if (!p.guitar && raise < 0.05f && wave == 0f) continue
            val sx = if (side < 0) 16.2f else 31.8f
            val ang = (0.25f + raise * 2.4f + wave) * side
            var hx = sx + sin(ang) * 8.6f
            var hy = sy + cos(ang) * 8.6f
            if (p.guitar) {
                if (side < 0) { hx = 14.5f; hy = 38.5f + p.bodyY + dy } else { hx = 27.5f; hy = 42f + p.bodyY + dy + p.strum * 2.2f }
            }
            a.reset(); a.moveTo(sx, sy); a.lineTo(hx, hy)
            edge.color = ink; edge.strokeWidth = 5.2f; c.drawPath(a, edge)
            line.color = hood; line.strokeWidth = 4f; c.drawPath(a, line)
            paw(c, hx, hy)
        }
    }

    private fun guitar(c: Canvas, p: RinParams) {
        c.save()
        val by = p.bodyY + (if (p.stance == Stance.SIT) 4.5f else 0f)
        c.translate(26f, 41.5f + by)
        c.rotate(-28f)
        a.reset(); a.addRoundRect(-19f, -0.9f, -4f, 0.9f, 0.6f, 0.6f, Path.Direction.CW)
        a.addRoundRect(-23f, -1.7f, -18.5f, 1.7f, 0.8f, 0.8f, Path.Direction.CW)
        ink(c, a, 1.1f); solid(c, a, 0xFF6B3F22.toInt())
        b.reset(); b.addCircle(-2f, 0f, 4.2f, Path.Direction.CW); b.addCircle(4.3f, 0f, 5.4f, Path.Direction.CW)
        ink(c, b, 1.2f); solid(c, b, accent)
        fill.color = 0xFF6B3F22.toInt(); c.drawRoundRect(-19f, -0.9f, -4f, 0.9f, 0.6f, 0.6f, fill)
        fill.color = ink; c.drawCircle(1f, 0f, 1.5f, fill)
        fill.color = 0xFF2A1A10.toInt(); c.drawRect(6.2f, -2.1f, 7f, 2.1f, fill)
        line.color = 0xCCFFFFFF.toInt(); line.strokeWidth = 0.16f
        for (i in -1..1) {
            val wob = p.strum * 0.35f * sin(i * 2.1f + p.strum * 40f)
            c.drawLine(-22f, i * 0.42f, 6.6f, i * 0.42f + wob, line)
        }
        c.restore()
    }

    // ───────────────────────── tail ─────────────────────────

    /** A big fluffy tail: circles along a curve, white with a hard-edged blue tip. */
    private fun buildTail(path: Path, tip: Path, x0: Float, y0: Float, a0: Float, bend: Float, len: Float, size: Float) {
        path.reset(); tip.reset()
        val n = 20
        var x = x0; var y = y0
        val step = len / n
        for (i in 0 until n) {
            val t = i / (n - 1f)
            val ang = a0 + bend * t
            x += cos(ang) * step; y += sin(ang) * step
            val r = size * (0.42f + 0.58f * sin(min(1f, t * 1.25f) * PI.toFloat() * 0.5f)) * (if (t > 0.9f) 1f - (t - 0.9f) * 3f else 1f)
            path.addCircle(x, y, r, Path.Direction.CW)
            if (t >= 0.8f) tip.addCircle(x, y, r * 1.05f, Path.Direction.CW)
        }
    }

    private fun drawTail(c: Canvas) {
        ink(c, tailPath, 1.3f)
        solid(c, tailPath, fur)
        c.save(); c.clipPath(tailPath)
        solid(c, tipPath, accent)
        c.restore()
    }

    private fun tail(c: Canvas, p: RinParams) {
        buildTail(tailPath, tipPath, 17f, 41.5f + p.bodyY, (PI + 0.05).toFloat() + p.tail * 0.7f, 1.45f, 18f, 3.5f)
        drawTail(c)
    }

    private fun bodySit(c: Canvas, p: RinParams) {
        val by = p.bodyY
        bodyPath.reset()
        bodyPath.moveTo(15.2f, 37f + by)
        bodyPath.quadTo(24f, 34.4f + by, 32.8f, 37f + by)
        bodyPath.quadTo(36.4f, 44f + by, 34.6f, 51.4f + by)
        bodyPath.quadTo(24f, 53.4f + by, 13.4f, 51.4f + by)
        bodyPath.quadTo(11.6f, 44f + by, 15.2f, 37f + by)
        bodyPath.close()
        ink(c, bodyPath); solid(c, bodyPath, hood)
        a.reset()
        a.moveTo(16.4f, 38.4f + by); a.quadTo(15.6f, 34f + by, 19.6f, 33.8f + by); a.quadTo(24f, 35.4f + by, 28.4f, 33.8f + by)
        a.quadTo(32.4f, 34f + by, 31.6f, 38.4f + by); a.quadTo(24f, 40.4f + by, 16.4f, 38.4f + by); a.close()
        ink(c, a, 1f); solid(c, a, collar)
        line.color = accent; line.strokeWidth = 0.7f
        c.drawLine(24f, 39.4f + by, 24f, 46.4f + by, line)
        line.strokeWidth = 0.45f
        c.drawRect(23.1f, 40.8f + by, 24.9f, 42.2f + by, line)
        emblem(c, 29.4f, 45.4f + by)
        for (x in floatArrayOf(19.6f, 28.4f)) paw(c, x, 53f + by)
    }

    private fun tailSit(c: Canvas, p: RinParams) {
        buildTail(tailPath, tipPath, 15f, 51f + p.bodyY, (PI + 0.1).toFloat() + p.tail * 0.6f, 1.35f, 14f, 3f)
        drawTail(c)
    }
}
