package dev.rint.launcher.mascot

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Eyes { OPEN, HAPPY, CLOSED, SHOCK, MEH, HEART, STAR, SAD, ANGRY, WINK }
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
    var guitar = false
    var strum = 0f
    // little effects around him
    var tears = false
    var steam = false
    var notes = false
    var zzz = false
    var sparkles = false
    var food = false
    var spinX = 1f          // horizontal squeeze for spins (1 = normal, -1 = turned around)
    var time = 0f
    fun reset() {
        hop = 0f; guitar = false; strum = 0f
        tears = false; steam = false; notes = false; zzz = false; sparkles = false; food = false; spinX = 1f
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
        private val WOOD = argb(0xFF6B3F22)
        private val STEAM = argb(0xFFD7DCEA)
        private val TEAR = argb(0xFF8EC5FF)
        private val FOOD = argb(0xFFFFA24A)
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

    /** Ear insides, tail tip, zipper: follows the user's accent. */
    var accent: Int = 0xFF3B7CFF.toInt()
        set(v) { field = v; BLUE = v or 0xFF000000.toInt(); BLUE_LIGHT = mix(BLUE, 0xFFFFFFFF.toInt(), 0.4f) }
    private var BLUE = argb(0xFF3B7CFF)
    private var BLUE_LIGHT = argb(0xFF7FB0FF)
    private fun mix(a: Int, b: Int, k: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xff) * (1 - k) + ((b shr s) and 0xff) * k).toInt() shl s
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
    private var hcx = 24f
    private var hcy = 20f

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
            Stance.STAND -> { tail(p, 15f, 46.5f); body(p, 0f); legs(p); if (p.guitar) guitar(p, 0f); arms(p, 0f); head(p, 24f + p.headX, 20f + p.headY + p.bodyY) }
            Stance.SIT -> { tailSit(p); bodySit(p); if (p.guitar) guitar(p, 5f); arms(p, 5f); head(p, 24f + p.headX, 25f + p.headY + p.bodyY) }
            Stance.HEAD, Stance.PEEK -> head(p, 24f + p.headX, 20f + p.headY)
        }
        if (p.stance == Stance.PEEK) {
            fill(30) { x, y -> inEllipse(x, y, 14f, 31.5f, 3.6f, 2.4f) || inEllipse(x, y, 34f, 31.5f, 3.6f, 2.4f) }
        }
        if (p.squash != 1f) squash(p.squash)
        outline()
        if (p.sweat) { put(37, 6, BLUE_LIGHT); put(37, 7, BLUE); put(36, 8, BLUE); put(37, 8, BLUE); put(38, 8, BLUE); put(37, 9, BLUE) }
        effects(p)
    }

    private fun guitar(p: RinParams, dy: Float) {
        val by = p.bodyY + dy
        for (y in 0 until H) for (x in 0 until W) {
            val fx = x + 0.5f; val fy = y + 0.5f
            if (inEllipse(fx, fy, 28f, 43f + by, 4f, 3.4f) || inEllipse(fx, fy, 24.5f, 42f + by, 2.8f, 2.6f)) {
                pixels[y * W + x] = if (inEllipse(fx, fy, 26.5f, 42.5f + by, 1.1f, 1.1f)) OUTLINE else BLUE
            } else if (segDist(fx, fy, 23f, 42f + by, 7f, 36.5f + by) < 0.8f) pixels[y * W + x] = WOOD
        }
        // strings shimmer while strumming
        if (p.strum > 0.5f) for (x in 12..22) put(x, (38.2f + (x - 12) * 0.34f + by).toInt(), FUR)
    }

    /** Tears, steam, music notes, z's, sparkles, snacks: drawn on top in pixel form. */
    private fun effects(p: RinParams) {
        val t = p.time
        val ex = 5.6f
        if (p.tears) for (side in listOf(-1f, 1f)) {
            val fall = (t * 7f + if (side > 0) 2.5f else 0f) % 7f
            val x = (hcx + side * (ex + 1f)).toInt(); val y = (hcy + 4f + fall).toInt()
            put(x, y, TEAR); put(x, y + 1, TEAR)
            put((hcx + side * ex).toInt(), (hcy + 3.5f).toInt(), TEAR)
        }
        if (p.steam) for (i in 0..1) {
            val ph = (t * 1.6f + i * 0.5f) % 1f
            val side = if (i == 0) -1f else 1f
            val cx = (hcx + side * (9f + ph * 2f)).toInt(); val cy = (hcy - 13f - ph * 7f).toInt()
            if (ph < 0.85f) { put(cx, cy, STEAM); put(cx + 1, cy, STEAM); put(cx, cy - 1, STEAM); put(cx - 1, cy, STEAM); put(cx, cy + 1, STEAM) }
        }
        if (p.notes) for (i in 0..1) {
            val ph = (t * 0.7f + i * 0.5f) % 1f
            val x = (hcx + 13f + ph * 4f + sin(ph * 12f) * 1.2f).toInt(); val y = (hcy - 6f - ph * 14f).toInt()
            if (y >= 1) { put(x, y, BLUE); put(x, y + 1, BLUE); put(x, y + 2, BLUE); put(x - 1, y + 2, BLUE); put(x - 1, y + 3, BLUE); put(x + 1, y, BLUE) }
        }
        if (p.zzz) for (i in 0..2) {
            val ph = (t * 0.5f + i / 3f) % 1f
            val x = (hcx + 11f + ph * 6f).toInt(); val y = (hcy - 8f - ph * 12f).toInt()
            val c = if (ph < 0.8f) STEAM else CLEAR
            if (c != CLEAR && y >= 0) { put(x, y, c); put(x + 1, y, c); put(x + 1, y + 1, c); put(x, y + 2, c); put(x, y + 2, c); put(x + 1, y + 2, c) }
        }
        if (p.sparkles) for (i in 0..3) {
            val ph = (t * 1.3f + i * 0.25f) % 1f
            if (ph > 0.6f) continue
            val a = i * 1.7f + 0.4f
            val x = (hcx + cos(a) * 17f).toInt(); val y = (hcy + sin(a) * 13f - 2f).toInt()
            put(x, y, STAR); put(x - 1, y, STAR); put(x + 1, y, STAR); put(x, y - 1, STAR); put(x, y + 1, STAR)
        }
        if (p.food) {
            val x = hcx.toInt() + 3; val y = (hcy + 9f).toInt()
            for (dx in 0..2) for (dy in 0..1) put(x + dx, y + dy, FOOD)
            if ((t * 4f).toInt() % 2 == 0) put(x + 4, y + 2, FOOD)
        }
    }

    /** Fills pixels for which [test] holds; [shade] picks fur shading. */
    private inline fun fill(maxY: Int = H, test: (Float, Float) -> Boolean) {
        for (y in 0 until min(H, maxY + 6)) for (x in 0 until W) {
            if (test(x + 0.5f, y + 0.5f)) pixels[y * W + x] = FUR
        }
    }

    // ── head ───────────────────────────────────────────────
    private fun head(p: RinParams, hcx: Float, hcy: Float) {
        this.hcx = hcx; this.hcy = hcy
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
        val side = if (ex < 0) -1f else 1f
        when (p.eyes) {
            Eyes.WINK -> return if (side > 0) {
                val v = dy - 0.25f * dx * dx / 2.5f - 1.0f
                if (abs(dx) < 2.5f && abs(v) < 0.5f) EYE else CLEAR
            } else eyeOpenShape(p, lx, ly, cx, cy, dx, dy)
            Eyes.SAD, Eyes.ANGRY -> {
                if (!inEllipse(lx, ly, cx, cy, 2.55f, 3.9f)) return CLEAR
                val slope = if (p.eyes == Eyes.SAD) 0.55f * side else -0.55f * side
                if (dy < -1.1f + dx * slope) return CLEAR
                if (inEllipse(lx, ly, cx - 0.6f, cy + 0.6f, 0.6f, 0.7f)) return FUR
                return EYE
            }
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

    private fun eyeOpenShape(p: RinParams, lx: Float, ly: Float, cx: Float, cy: Float, dx: Float, dy: Float): Int {
        val ry = 3.9f * p.eyeOpen.coerceIn(0.3f, 1f)
        if (!inEllipse(lx, ly, cx, cy, 2.55f, ry)) return CLEAR
        if (inEllipse(lx, ly, cx - 0.75f, cy - ry * 0.45f, 0.75f, 0.9f)) return FUR
        return EYE
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
