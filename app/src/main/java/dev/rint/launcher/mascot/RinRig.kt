package dev.rint.launcher.mascot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Eyes { OPEN, HAPPY, CLOSED, SHOCK, MEH, HEART, STAR }
enum class Stance { STAND, SIT, HEAD, PEEK }

/** Everything that can move on Rin. Units are sprite pixels / radians. */
class RinParams {
    var stance = Stance.STAND
    var eyes = Eyes.OPEN
    var eyeOpen = 1f
    var lookX = 0f
    var lookY = 0f
    var mouth = 0f          // 0 = ":3", 1 = wide open
    var headX = 0f
    var headY = 0f
    var tilt = 0f
    var bodyY = 0f
    var squash = 1f         // <1 squashed, >1 stretched (around the feet)
    var earL = 0f           // + folds, - perks up
    var earR = 0f
    var tail = 0f           // wag angle
    var legL = 0f           // walk phase: lift/shift
    var legR = 0f
    var armL = 0f           // 0 down, 1 raised
    var armR = 0f
    var armWave = 0f
    var blush = 0f
    var sweat = false
    var hop = 0f            // whole-sprite lift, applied when drawing (keeps the jump inside the frame)
    fun reset() {
        hop = 0f
        stance = Stance.STAND; eyes = Eyes.OPEN; eyeOpen = 1f; lookX = 0f; lookY = 0f; mouth = 0f
        headX = 0f; headY = 0f; tilt = 0f; bodyY = 0f; squash = 1f; earL = 0f; earR = 0f; tail = 0f
        legL = 0f; legR = 0f; armL = 0f; armR = 0f; armWave = 0f; blush = 0f; sweat = false
    }
}

/**
 * Rin, drawn from geometry into a 48×56 pixel buffer every frame, then outlined.
 * Parts are evaluated per pixel (a tiny "pixel shader"), so every joint animates freely
 * while the result stays crisp pixel art.
 */
class RinRig {
    companion object {
        const val W = 48
        const val H = 56
        const val GROUND = 55f
        // head-only crop
        const val HEAD_X = 4
        const val HEAD_Y = 0
        const val HEAD_W = 40
        const val HEAD_H = 36

        private const val CLEAR = 0
        private val OUTLINE = argb(0xFF0B0F24)
        private val FUR = argb(0xFFFFFFFF)
        private val FUR_SHADE = argb(0xFFCBD5F2)
        private val BLUE = argb(0xFF3B7CFF)
        private val BLUE_LIGHT = argb(0xFF7FB0FF)
        private val EYE = argb(0xFF0B0F24)
        private val HOOD = argb(0xFF1E2233)
        private val HOOD_SHADE = argb(0xFF141722)
        private val PINK = argb(0xFFF7A8C4)
        private val TONGUE = argb(0xFFFF7C9C)
        private val MOUTH = argb(0xFF3A1428)
        private val MOUTH_LINE = argb(0xFF5C678C)
        private val HEART = argb(0xFFFF4F8B)
        private val STAR = argb(0xFFFFD84A)

        private fun argb(v: Long) = v.toInt()
    }

    val pixels = IntArray(W * H)
    private val scratch = IntArray(W * H)

    private fun put(x: Int, y: Int, c: Int) {
        if (x in 0 until W && y in 0 until H) pixels[y * W + x] = c
    }

    private fun inTri(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Boolean {
        val d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
        val d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
        val d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
        val neg = d1 < 0 || d2 < 0 || d3 < 0
        val pos = d1 > 0 || d2 > 0 || d3 > 0
        return !(neg && pos)
    }

    private fun inEllipse(px: Float, py: Float, cx: Float, cy: Float, rx: Float, ry: Float): Boolean {
        val dx = (px - cx) / rx
        val dy = (py - cy) / ry
        return dx * dx + dy * dy <= 1f
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay
        val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-4f)).coerceIn(0f, 1f)
        val dx = px - (ax + vx * t); val dy = py - (ay + vy * t)
        return sqrt(dx * dx + dy * dy)
    }

    fun render(p: RinParams) {
        pixels.fill(CLEAR)
        when (p.stance) {
            Stance.STAND -> { tail(p, 15f, 46.5f); body(p, 0f); legs(p); arms(p, 0f); head(p, 24f + p.headX, 20f + p.headY + p.bodyY) }
            Stance.SIT -> { tailSit(p); bodySit(p); arms(p, 5f); head(p, 24f + p.headX, 25f + p.headY + p.bodyY) }
            Stance.HEAD, Stance.PEEK -> head(p, 24f + p.headX, 20f + p.headY)
        }
        if (p.stance == Stance.PEEK) {
            fill(30) { x, y -> inEllipse(x, y, 14f, 31.5f, 3.6f, 2.4f) || inEllipse(x, y, 34f, 31.5f, 3.6f, 2.4f) }
        }
        if (p.squash != 1f) squash(p.squash)
        outline()
        if (p.sweat) { put(37, 6, BLUE_LIGHT); put(37, 7, BLUE); put(36, 8, BLUE); put(37, 8, BLUE); put(38, 8, BLUE); put(37, 9, BLUE) }
    }

    /** Fills pixels for which [test] holds; [shade] picks fur shading. */
    private inline fun fill(maxY: Int = H, test: (Float, Float) -> Boolean) {
        for (y in 0 until min(H, maxY + 6)) for (x in 0 until W) {
            if (test(x + 0.5f, y + 0.5f)) pixels[y * W + x] = FUR
        }
    }

    // ── head ───────────────────────────────────────────────
    private fun head(p: RinParams, hcx: Float, hcy: Float) {
        val c = cos(-p.tilt); val s = sin(-p.tilt)
        val y0 = max(0, (hcy - 24).toInt()); val y1 = min(H - 1, (hcy + 14).toInt())
        val x0 = max(0, (hcx - 22).toInt()); val x1 = min(W - 1, (hcx + 22).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val dx = x + 0.5f - hcx; val dy = y + 0.5f - hcy
            val lx = dx * c - dy * s
            val ly = dx * s + dy * c
            val col = headColor(p, lx, ly)
            if (col != CLEAR) pixels[y * W + x] = col
        }
    }

    private fun ear(lx: Float, ly: Float, side: Float, fold: Float): Int {
        // side = -1 left, +1 right (mirrored in x)
        val x = lx * side
        val tipX = 12.5f - fold * 3f
        val tipY = -19.5f + fold * 7f + (if (fold < 0) fold * 2f else 0f)
        if (!inTri(x, ly, tipX, tipY, 11.5f, -5f, 2.5f, -8.5f)) return CLEAR
        val itx = tipX - 1.3f; val ity = tipY + 3.2f
        return if (inTri(x, ly, itx, ity, 10.2f, -6.2f, 4.8f, -8f)) BLUE else FUR
    }

    private fun headColor(p: RinParams, lx: Float, ly: Float): Int {
        val rx = 12.8f; val ry = 10.2f
        var col = CLEAR
        // ears (behind the head)
        val el = ear(lx, ly, -1f, p.earL)
        if (el != CLEAR) col = el
        val er = ear(lx, ly, 1f, p.earR)
        if (er != CLEAR) col = er
        // spiky fur: crown + cheek tufts
        val ax = abs(lx)
        val tuft = inTri(lx, ly, -5f, -8.5f, 0f, -9f, -3f, -14f) || inTri(lx, ly, -1f, -9.5f, 4.5f, -8.5f, 2.2f, -13.2f) ||
            inTri(ax, ly, 11.5f, 0f, 12f, 6f, 17.5f, 5f) || inTri(ax, ly, 10.5f, 4f, 10f, 9f, 15.5f, 10f) ||
            inTri(ax, ly, 6f, 8.5f, 9.5f, 7.5f, 8.8f, 11.8f)
        val inHead = inEllipse(lx, ly, 0f, 0f, rx, ry)
        if (inHead || tuft) {
            col = if ((lx / rx) * 0.3f + (ly / ry) * 0.85f > 0.78f) FUR_SHADE else FUR
        }
        if (!inHead) return col
        // blush
        if (p.blush > 0.05f && (inEllipse(lx, ly, -8.2f, 4.6f, 1.8f, 0.8f) || inEllipse(lx, ly, 8.2f, 4.6f, 1.8f, 0.8f))) col = PINK
        // eyes
        val eyeCol = eye(p, lx, ly, -5.6f).takeIf { it != CLEAR } ?: eye(p, lx, ly, 5.6f)
        if (eyeCol != CLEAR) col = eyeCol
        // mouth
        val m = mouth(p, lx, ly)
        if (m != CLEAR) col = m
        return col
    }

    private fun eye(p: RinParams, lx: Float, ly: Float, ex: Float): Int {
        val cx = ex + p.lookX * 1.3f
        val cy = 0.9f + p.lookY * 1.1f
        val dx = lx - cx; val dy = ly - cy
        when (p.eyes) {
            Eyes.OPEN, Eyes.SHOCK, Eyes.MEH -> {
                val shock = p.eyes == Eyes.SHOCK
                val rx = if (shock) 2.9f else 2.55f
                val ry = (if (shock) 3.3f else 3.9f) * p.eyeOpen.coerceIn(0f, 1f)
                if (ry < 0.9f) return if (abs(dy - 0.8f) < 0.55f && abs(dx) < 2.3f) EYE else CLEAR
                if (!inEllipse(lx, ly, cx, cy, rx, ry)) return CLEAR
                if (p.eyes == Eyes.MEH && dy < -0.4f) return if (dy > -1.4f) EYE else CLEAR
                // glints
                if (inEllipse(lx, ly, cx - 0.75f, cy - ry * 0.45f, 0.75f, 0.9f)) return FUR
                if (shock && inEllipse(lx, ly, cx + 0.9f, cy + 1.2f, 0.5f, 0.5f)) return FUR
                return EYE
            }
            Eyes.HAPPY -> {
                val v = dy + 0.9f * abs(dx) - 0.3f
                return if (abs(dx) < 2.6f && v > -0.55f && v < 0.55f) EYE else CLEAR
            }
            Eyes.CLOSED -> {
                val v = dy - 0.25f * dx * dx / 2.5f - 1.0f
                return if (abs(dx) < 2.5f && abs(v) < 0.5f) EYE else CLEAR
            }
            Eyes.HEART -> {
                val hx = dx; val hy = dy + 0.4f
                val inHeart = inEllipse(hx, hy, -1.1f, -0.8f, 1.35f, 1.3f) || inEllipse(hx, hy, 1.1f, -0.8f, 1.35f, 1.3f) ||
                    inTri(hx, hy, -2.4f, -0.4f, 2.4f, -0.4f, 0f, 2.6f)
                return if (inHeart) (if (hx < -1f && hy < -1f) FUR else HEART) else CLEAR
            }
            Eyes.STAR -> {
                val inStar = (abs(dx) < 0.6f && abs(dy) < 2.8f) || (abs(dy) < 0.6f && abs(dx) < 2.6f) || (abs(dx) + abs(dy) < 1.7f)
                return if (inStar) STAR else CLEAR
            }
        }
    }

    private fun mouth(p: RinParams, lx: Float, ly: Float): Int {
        val open = p.mouth.coerceIn(0f, 1f)
        if (open > 0.12f) {
            val ry = 0.8f + open * 1.9f
            val cy = 6.6f + ry * 0.45f
            if (!inEllipse(lx, ly, 0f, cy, 1.6f + open * 0.8f, ry)) return CLEAR
            return if (ly > cy + ry * 0.25f && open > 0.4f) TONGUE else MOUTH
        }
        // ω  — the ":3" mouth
        val xi = floor(lx + 0.5f).toInt()
        val yi = floor(ly).toInt()
        return when {
            yi == 6 && (xi == -2 || xi == 0 || xi == 2) -> MOUTH_LINE
            yi == 7 && (xi == -1 || xi == 1) -> MOUTH_LINE
            else -> CLEAR
        }
    }

    // ── body ───────────────────────────────────────────────
    private fun body(p: RinParams, dy: Float) {
        val by = p.bodyY + dy
        for (y in 30 until 49) for (x in 10 until 38) {
            val fx = x + 0.5f; val fy = y + 0.5f - by
            val hood = inEllipse(fx, fy, 24f, 34.2f, 10.5f, 3.2f) ||
                (fx in 15f..33f && fy in 34f..47.5f && !(fy > 45.5f && (fx < 16f || fx > 32f)))
            if (!hood) continue
            var c = if (fx > 29f && fy > 36f) HOOD_SHADE else HOOD
            if (abs(fx - 24f) < 0.6f && fy in 35.5f..47f) c = BLUE                     // zipper
            if (fy in 36.2f..37.8f && fx in 24f..25.8f) c = BLUE_LIGHT                 // zipper pull
            val sx = fx - 29f; val sy = fy - 41f                                         // ✦ emblem
            if ((abs(sx) < 0.6f && abs(sy) < 2.2f) || (abs(sy) < 0.6f && abs(sx) < 2.2f) || abs(sx) + abs(sy) < 1.3f) c = BLUE
            pixels[y * W + x] = c
        }
    }

    private fun legs(p: RinParams) {
        for (y in 44 until H) for (x in 12 until 36) {
            val fx = x + 0.5f; val fy = y + 0.5f - p.bodyY
            val liftL = max(0f, p.legL) * 2.2f; val liftR = max(0f, p.legR) * 2.2f
            val lx = 20f + p.legL * 0.8f; val rx = 28f - p.legR * 0.8f
            val left = (abs(fx - lx) < 2.4f && fy in 46f..(52.5f - liftL)) || inEllipse(fx, fy, lx - 0.3f, 52.8f - liftL, 3.2f, 1.9f)
            val right = (abs(fx - rx) < 2.4f && fy in 46f..(52.5f - liftR)) || inEllipse(fx, fy, rx + 0.3f, 52.8f - liftR, 3.2f, 1.9f)
            if (left || right) {
                val toe = (left && fy > 52.4f - liftL && (abs(fx - (lx - 0.3f)) < 0.5f)) || (right && fy > 52.4f - liftR && abs(fx - (rx + 0.3f)) < 0.5f)
                pixels[y * W + x] = if (toe) FUR_SHADE else if (fx > 24f) FUR_SHADE else FUR
            }
        }
    }

    private fun arms(p: RinParams, dy: Float) {
        val by = p.bodyY + dy
        val shoulderL = 16f to 36f + by
        val shoulderR = 32f to 36f + by
        fun hand(side: Float, raise: Float, wave: Float): Pair<Float, Float> {
            val a = (0.25f + raise * 2.4f + wave) * side
            val len = 9.5f
            val sx = if (side < 0) shoulderL.first else shoulderR.first
            val sy = shoulderL.second
            return (sx + sin(a) * len * side * side) to (sy + cos(a) * len)
        }
        val (hlx, hly) = hand(-1f, p.armL, 0f)
        val (hrx, hry) = hand(1f, p.armR, p.armWave)
        for (y in 0 until H) for (x in 0 until W) {
            val fx = x + 0.5f; val fy = y + 0.5f
            val dl = segDist(fx, fy, shoulderL.first, shoulderL.second, hlx, hly)
            val dr = segDist(fx, fy, shoulderR.first, shoulderR.second, hrx, hry)
            val pawL = inEllipse(fx, fy, hlx, hly, 2.3f, 2.1f)
            val pawR = inEllipse(fx, fy, hrx, hry, 2.3f, 2.1f)
            when {
                pawL || pawR -> pixels[y * W + x] = FUR
                dl < 2.3f || dr < 2.3f -> pixels[y * W + x] = if (dr < 2.3f) HOOD_SHADE else HOOD
            }
        }
    }

    private fun tailCurve(t: Float, baseX: Float, baseY: Float, wag: Float, curl: Float): Pair<Float, Float> {
        // a swooping S-curve up and out to the left, swung by [wag]
        val ang = -2.65f + wag + t * (1.3f + curl)
        val r = t * 14f
        return (baseX + cos(ang) * r * 0.9f) to (baseY + sin(ang) * r - t * t * 3f)
    }

    private fun tail(p: RinParams, baseX: Float, baseY: Float) {
        val n = 16
        val pts = Array(n) { i -> tailCurve(i / (n - 1f), baseX, baseY + p.bodyY, p.tail, 0f) }
        for (y in 0 until H) for (x in 0 until W) {
            val fx = x + 0.5f; val fy = y + 0.5f
            for (i in 0 until n) {
                val t = i / (n - 1f)
                val rad = (1.8f + 3.6f * sin(t * 3.1f).coerceAtLeast(0.35f)) * (1f + 0.1f * sin(i * 1.9f))
                val (cx, cy) = pts[i]
                val dx = fx - cx; val dy = fy - cy
                if (dx * dx + dy * dy <= rad * rad) {
                    pixels[y * W + x] = if (t > 0.66f) (if (dx > 0.8f) BLUE else BLUE_LIGHT) else if (dy > 1f) FUR_SHADE else FUR
                    break
                }
            }
        }
    }

    private fun bodySit(p: RinParams) {
        val by = p.bodyY
        for (y in 34 until H) for (x in 8 until 40) {
            val fx = x + 0.5f; val fy = y + 0.5f - by
            val hood = inEllipse(fx, fy, 24f, 39f, 10.5f, 3.2f) || inEllipse(fx, fy, 24f, 46f, 10f, 7.5f)
            val feet = inEllipse(fx, fy, 19f, 52.5f, 3.6f, 2.2f) || inEllipse(fx, fy, 29f, 52.5f, 3.6f, 2.2f)
            when {
                feet -> pixels[y * W + x] = if (fy > 53f) FUR_SHADE else FUR
                hood -> {
                    var c = if (fx > 29f) HOOD_SHADE else HOOD
                    if (abs(fx - 24f) < 0.6f && fy in 40f..51f) c = BLUE
                    val sx = fx - 29f; val sy = fy - 45f
                    if ((abs(sx) < 0.6f && abs(sy) < 2.2f) || (abs(sy) < 0.6f && abs(sx) < 2.2f) || abs(sx) + abs(sy) < 1.3f) c = BLUE
                    pixels[y * W + x] = c
                }
            }
        }
    }

    private fun tailSit(p: RinParams) {
        // curled around the feet
        val n = 16
        for (y in 36 until H) for (x in 0 until W) {
            val fx = x + 0.5f; val fy = y + 0.5f
            for (i in 0 until n) {
                val t = i / (n - 1f)
                val ang = 3.4f - t * 2.6f + p.tail * 0.5f
                val cx = 24f + cos(ang) * (15f - t * 3f)
                val cy = 50f + p.bodyY + sin(ang) * (6f - t * 2f)
                val rad = 2.2f + 2.6f * sin(t * 3.1f).coerceAtLeast(0.4f)
                val dx = fx - cx; val dy = fy - cy
                if (dx * dx + dy * dy <= rad * rad) {
                    pixels[y * W + x] = if (t > 0.7f) BLUE else if (dy > 1f) FUR_SHADE else FUR
                    break
                }
            }
        }
    }

    /** Vertical squash & stretch anchored at the feet. */
    private fun squash(k: Float) {
        System.arraycopy(pixels, 0, scratch, 0, pixels.size)
        pixels.fill(CLEAR)
        val widen = 1f / sqrt(k)
        for (y in 0 until H) for (x in 0 until W) {
            val sy = GROUND - (GROUND - (y + 0.5f)) / k
            val sx = 24f + (x + 0.5f - 24f) / widen
            val ix = sx.toInt(); val iy = sy.toInt()
            if (ix in 0 until W && iy in 0 until H) pixels[y * W + x] = scratch[iy * W + ix]
        }
    }

    /** 1px dark outline around everything, like hand-made pixel art. */
    private fun outline() {
        System.arraycopy(pixels, 0, scratch, 0, pixels.size)
        for (y in 0 until H) for (x in 0 until W) {
            if (scratch[y * W + x] != CLEAR) continue
            val n = (x > 0 && scratch[y * W + x - 1] != CLEAR) || (x < W - 1 && scratch[y * W + x + 1] != CLEAR) ||
                (y > 0 && scratch[(y - 1) * W + x] != CLEAR) || (y < H - 1 && scratch[(y + 1) * W + x] != CLEAR)
            if (n) pixels[y * W + x] = OUTLINE
        }
    }
}
