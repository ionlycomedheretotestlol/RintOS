package dev.rint.launcher.intro

import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinParams
import dev.rint.launcher.mascot.RinRig
import dev.rint.launcher.mascot.animateRin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The 8-bit world of the 1.4 film: a real low-resolution frame buffer (180 px wide) that the film
 * scales up with nearest-neighbour filtering, so Rin's pixels and the world's pixels line up.
 * Sky with dithered bands, stars, a rising sun, parallax clouds, mountains, hills, bushes, a tiled
 * ground, ? blocks, coins, and Rin himself, drawn by his real pixel rig.
 */
class PixelWorld(val vw: Int, val vh: Int, private val accent: Int) {
    val pixels = IntArray(vw * vh)
    val ground = (vh * 0.74f).toInt()
    private val rig = RinRig().also { it.accent = accent }
    private val params = RinParams()

    companion object {
        const val BLOCK = 14
        const val BIG_BLOCK = 22
        const val JUMP_H = 18f
        const val SPEED = 80f
        /** World x (feet centre) of each block: six feature blocks, then the big 1.4 block. */
        fun blockX(i: Int) = 90f + i * 160f

        private fun rgb(v: Long) = v.toInt()
        private val NIGHT = intArrayOf(rgb(0xFF070920), rgb(0xFF0D1136), rgb(0xFF161B4E), rgb(0xFF232A6A), rgb(0xFF3A3D88))
        private val DAY = intArrayOf(rgb(0xFF3C7DFF), rgb(0xFF5A95FF), rgb(0xFF7AAEFF), rgb(0xFF9CC8FF), rgb(0xFFC4E1FF))
        private val DAWN = intArrayOf(rgb(0xFF2B2E7A), rgb(0xFF5B3F95), rgb(0xFFB45F97), rgb(0xFFF08A77), rgb(0xFFFFC27A))
        private val FAR_NIGHT = rgb(0xFF1B2152); private val FAR_DAY = rgb(0xFF7C8FD8)
        private val NEAR_NIGHT = rgb(0xFF16304A); private val NEAR_DAY = rgb(0xFF3FAE62)
        private val GRASS = rgb(0xFF7CE07A); private val GRASS_DARK = rgb(0xFF43B054)
        private val DIRT_A = rgb(0xFFB8733E); private val DIRT_B = rgb(0xFF9A5C2E); private val DIRT_LINE = rgb(0xFF6E3E1C)
        private val GOLD = rgb(0xFFF8B800); private val GOLD_DARK = rgb(0xFFB06A00); private val OUT = rgb(0xFF2A1400)
        private val USED = rgb(0xFF9C6B3C); private val WHITE = rgb(0xFFFFFFFF); private val CLOUD_SHADE = rgb(0xFFD6E6FF)
        private val BUSH = rgb(0xFF2E9A4E); private val BUSH_LIGHT = rgb(0xFF5CCB6C); private val SUN = rgb(0xFFFFE08A)

        private fun lerp(a: Int, b: Int, k: Float): Int {
            val t = k.coerceIn(0f, 1f)
            fun ch(s: Int) = ((((a shr s) and 0xff) * (1 - t) + ((b shr s) and 0xff) * t).toInt() and 0xff) shl s
            return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
        }
    }

    private fun put(x: Int, y: Int, c: Int) { if (x in 0 until vw && y in 0 until vh) pixels[y * vw + x] = c }
    private fun rect(x: Int, y: Int, w: Int, h: Int, c: Int) {
        val x0 = max(0, x); val y0 = max(0, y); val x1 = min(vw, x + w); val y1 = min(vh, y + h)
        for (yy in y0 until y1) { val row = yy * vw; for (xx in x0 until x1) pixels[row + xx] = c }
    }

    /** Rin's world x (feet centre) at [t] seconds into the film. */
    fun rinX(t: Float): Float = when {
        t < Score.ADVENTURE * Score.BAR.toFloat() -> 10f
        else -> min(10f + SPEED * (t - Score.ADVENTURE * Score.BAR.toFloat()), blockX(6))
    }

    /** How high Rin is off the ground (0 = standing). */
    fun rinLift(t: Float): Float {
        for (h in Score.hits) {
            val u = (t - (h.toFloat() - Score.JUMP.toFloat())) / (Score.JUMP.toFloat() * 2)
            if (u in 0f..1f) return JUMP_H * 4 * u * (1 - u)
        }
        return 0f
    }

    fun camX(t: Float) = rinX(t) - vw * 0.35f

    /** Vertical establishing pan in the first seconds: the camera starts in the stars. */
    fun panY(t: Float): Int {
        val k = (t / 3.2f).coerceIn(0f, 1f)
        val e = 1 - (1 - k) * (1 - k) * (1 - k)
        return ((1 - e) * vh * 0.45f).toInt()
    }

    fun rinPose(t: Float): Pose {
        val adv = Score.ADVENTURE * Score.BAR.toFloat()
        val bigHit = Score.hits[6].toFloat()
        return when {
            t < 2.9f -> Pose.SLEEP
            t < 3.5f -> Pose.YAWN
            t < adv -> Pose.STRETCH
            t < bigHit - Score.JUMP.toFloat() -> if (rinLift(t) > 0f) Pose.CHEER else Pose.WALK
            t < bigHit + Score.JUMP.toFloat() -> Pose.CHEER
            t < Score.GRAB.toFloat() -> Pose.BACK
            else -> Pose.CHEER
        }
    }

    /** The block's vertical nudge when hit, and whether it has been hit yet. */
    private fun blockBump(i: Int, t: Float): Float {
        val d = t - Score.hits[i].toFloat()
        return if (d in 0f..0.18f) -4f * sin(d / 0.18f * Math.PI.toFloat()) else 0f
    }

    fun blockTop(i: Int): Int = ground - 54 - JUMP_H.toInt() - (if (i == 6) BIG_BLOCK else BLOCK)

    /** Draws the whole frame at [t]; [daylight] 0 = night, 1 = day. [hideRin] leaves Rin out (the film draws an HD Rin instead). */
    fun draw(t: Float, hideRin: Boolean = false) {
        val daylight = ((t - 0.6f) / 3.2f).coerceIn(0f, 1f)
        val cam = camX(t)
        val py = panY(t)
        // ── sky: five bands with checkerboard dithering between them
        val bandH = (ground + py) / 5 + 1
        for (y in 0 until vh) {
            val wy = y - py
            val band = ((wy + bandH * 0) / bandH.toFloat()).coerceIn(0f, 4.999f)
            val bi = band.toInt(); val frac = band - bi
            val c0 = sky(bi, daylight); val c1 = sky(min(4, bi + 1), daylight)
            val row = y * vw
            for (x in 0 until vw) {
                val dither = frac > 0.66f && ((x + y) and 1) == 0
                pixels[row + x] = if (dither) c1 else c0
            }
        }
        // ── stars fade out with the dawn
        if (daylight < 1f) {
            var s = 1234567
            for (i in 0 until 90) {
                s = s * 1103515245 + 12345
                val sx = ((s ushr 8) and 0xffff) % vw
                s = s * 1103515245 + 12345
                val sy = ((s ushr 8) and 0xffff) % (vh / 2 + py) - py / 2
                val tw = sin(t * 3f + i) > -0.3f
                if (tw && (1f - daylight) > (i % 5) / 6f) put(sx, sy + py / 2, if (i % 7 == 0) SUN else WHITE)
            }
        }
        // ── the sun rises behind the mountains
        val sunY = (ground - 30 - daylight * 60).toInt() + py
        val sunX = (vw * 0.78f - cam * 0.05f).toInt()
        for (dy in -9..9) for (dx in -9..9) if (dx * dx + dy * dy <= 81) put(sunX + dx, sunY + dy, lerp(rgb(0xFFFF9A5A), SUN, daylight))
        // ── clouds (parallax 0.2), slowly drifting
        for (i in -1..8) {
            val wx = i * 70f + 20f - (cam * 0.2f + t * 3f) % 70f
            val cy = 22 + (i * 37 % 40) + py
            cloud(wx.toInt(), cy, lerp(rgb(0xFF2A2F66), WHITE, daylight), lerp(rgb(0xFF20245A), CLOUD_SHADE, daylight))
        }
        // ── far mountains (parallax 0.3)
        val farC = lerp(FAR_NIGHT, FAR_DAY, daylight)
        val farTop = lerp(FAR_NIGHT, WHITE, daylight * 0.9f)
        for (x in 0 until vw) {
            val wx = x + cam * 0.3f
            val h = 34 + 16 * sin(wx * 0.021f) + 9 * sin(wx * 0.057f + 1.3f) + 5 * sin(wx * 0.13f)
            val top = ground - (floor(h / 2) * 2).toInt() + py
            for (y in top until ground + py) put(x, y, if (y < top + 3 && h > 44) farTop else farC)
        }
        // ── near hills (parallax 0.6)
        val nearC = lerp(NEAR_NIGHT, NEAR_DAY, daylight)
        val nearL = lerp(NEAR_NIGHT, rgb(0xFF62C97C), daylight)
        for (x in 0 until vw) {
            val wx = x + cam * 0.6f
            val h = 16 + 9 * sin(wx * 0.035f + 0.4f) + 4 * sin(wx * 0.09f)
            val top = ground - h.toInt() + py
            for (y in top until ground + py) put(x, y, if (y < top + 2) nearL else nearC)
        }
        // ── bushes on the ground line
        for (i in -1..30) {
            val wx = i * 64f + 30f
            val sx = (wx - cam).toInt()
            if (sx < -20 || sx > vw + 20) continue
            val bw = 10 + (i * 7) % 8
            for (dy in 0..bw / 2) for (dx in -bw..bw) {
                if (dx * dx / (bw * bw).toFloat() + dy * dy / ((bw / 2f) * (bw / 2f)) <= 1f)
                    put(sx + dx, ground - dy + py, lerp(rgb(0xFF123028), if (dy > bw / 3) BUSH_LIGHT else BUSH, daylight))
            }
        }
        // ── ground: grass lip + checkered dirt that scrolls with the camera
        val gy = ground + py
        for (x in 0 until vw) {
            val wx = floor(x + cam).toInt()
            put(x, gy, lerp(rgb(0xFF2C6B3A), GRASS, daylight)); put(x, gy + 1, lerp(rgb(0xFF1F4F2B), GRASS, daylight))
            put(x, gy + 2, lerp(rgb(0xFF1A4024), GRASS_DARK, daylight))
            if (((wx / 3) and 1) == 0) put(x, gy + 3, lerp(rgb(0xFF1A4024), GRASS_DARK, daylight))
            for (y in gy + 3 until vh) {
                val cx = ((wx and 0x7fffffff) / 8) and 1; val cy = ((y - gy) / 8) and 1
                val c = if (cx xor cy == 0) DIRT_A else DIRT_B
                val edge = (wx and 7) == 0 || ((y - gy) and 7) == 0
                pixels[y * vw + x] = lerp(rgb(0xFF2A1C14), if (edge) DIRT_LINE else c, daylight * 0.8f + 0.2f)
            }
        }
        // ── ? blocks
        for (i in 0 until 7) drawBlock(i, t, cam, py)
        // ── coins popping out of hit blocks
        for (i in 0 until 6) {
            val d = t - Score.hits[i].toFloat()
            if (d !in 0f..0.7f) continue
            val cx = (blockX(i) - cam).toInt()
            val cy = blockTop(i) - 6 - (34 * (1 - (1 - d / 0.45f).coerceAtLeast(0f).let { it * it })).toInt() + py
            val w = (abs(cos(d * 22f)) * 4).toInt()
            rect(cx - w, cy - 5, w * 2 + 1, 10, GOLD)
            rect(cx - w, cy - 5, 1, 10, GOLD_DARK)
            if (w > 1) rect(cx - w + 1, cy - 3, 1, 4, WHITE)
        }
        // ── the 1.4 orb rising out of the big block, then dropping onto Rin
        val bigHit = Score.hits[6].toFloat()
        if (t in bigHit..Score.GRAB.toFloat()) {
            val d = t - bigHit
            val cx = (blockX(6) - cam).toInt()
            val rise = (d / 0.6f).coerceAtMost(1f)
            val fall = ((t - (Score.GRAB.toFloat() - 0.35f)) / 0.35f).coerceIn(0f, 1f)
            val topY = blockTop(6) - 10 - (rise * 16).toInt()
            val cy = (topY + fall * (ground - 40 - topY)).toInt() + (sin(t * 8f) * 2).toInt() + py
            // a glowing orb: a halo that pulses, a shiny core, and four sparkles spinning around it
            val halo = 10 + (sin(t * 12f) * 1.5f).toInt()
            for (dy in -halo..halo) for (dx in -halo..halo) {
                val r2 = dx * dx + dy * dy
                if (r2 in 50..halo * halo && ((dx + dy + (t * 20).toInt()) and 1) == 0) put(cx + dx, cy + dy, lerp(accent, WHITE, 0.5f))
            }
            for (dy in -7..7) for (dx in -7..7) {
                val r2 = dx * dx + dy * dy
                if (r2 <= 49) put(cx + dx, cy + dy, when {
                    r2 > 36 -> WHITE
                    r2 < 6 && dx <= -1 && dy <= -1 -> WHITE
                    dy > 2 -> lerp(accent, OUT, 0.3f)
                    else -> accent
                })
            }
            for (k in 0 until 4) {
                val a = t * 5f + k * 1.5708f
                val sx = cx + (cos(a) * 13).toInt(); val sy = cy + (sin(a) * 13).toInt()
                put(sx, sy, GOLD); put(sx - 1, sy, GOLD); put(sx + 1, sy, GOLD); put(sx, sy - 1, GOLD); put(sx, sy + 1, GOLD)
            }
        }
        // ── Rin, drawn by his real pixel rig
        if (!hideRin) {
            val pose = rinPose(t)
            animateRin(pose, t, 0f, params)
            rig.render(params)
            val sx = (rinX(t) - cam).toInt() - RinRig.W / 2
            val sy = ground - RinRig.GROUND.toInt() - rinLift(t).toInt() + py
            val src = rig.pixels
            for (y in 0 until RinRig.H) for (x in 0 until RinRig.W) {
                val c = src[y * RinRig.W + x]
                if ((c ushr 24) != 0) put(sx + x, sy + y, c)
            }
        }
    }

    private fun sky(i: Int, day: Float): Int = when {
        day < 0.5f -> lerp(NIGHT[i], DAWN[i], day * 2)
        else -> lerp(DAWN[i], DAY[i], (day - 0.5f) * 2)
    }

    private fun cloud(x: Int, y: Int, c: Int, shade: Int) {
        rect(x + 4, y, 14, 4, c); rect(x, y + 3, 24, 5, c); rect(x + 8, y - 3, 8, 4, c)
        rect(x, y + 7, 24, 1, shade)
    }

    private fun drawBlock(i: Int, t: Float, cam: Float, py: Int) {
        val size = if (i == 6) BIG_BLOCK else BLOCK
        val x = (blockX(i) - cam).toInt() - size / 2
        if (x < -size || x > vw) return
        val y = blockTop(i) + blockBump(i, t).toInt() + py
        val hit = t >= Score.hits[i].toFloat()
        val face = when {
            i == 6 -> if (hit) lerp(accent, OUT, 0.35f) else lerp(accent, WHITE, 0.15f + 0.15f * sin(t * 6f))
            hit -> USED
            else -> GOLD
        }
        rect(x, y, size, size, OUT)
        rect(x + 1, y + 1, size - 2, size - 2, face)
        rect(x + 1, y + 1, size - 2, 1, lerp(face, WHITE, 0.4f))
        // rivets
        put(x + 2, y + 2, OUT); put(x + size - 3, y + 2, OUT); put(x + 2, y + size - 3, OUT); put(x + size - 3, y + size - 3, OUT)
        if (!hit) {
            if (i == 6) {
                // "1.4" in 3×5 pixel digits
                val cx = x + size / 2 - 5; val cy = y + size / 2 - 3
                digit(1, cx, cy, WHITE); put(cx + 4, cy + 4, WHITE); digit(4, cx + 6, cy, WHITE)
            } else {
                // a blinking "?"
                val q = if (sin(t * 7f + i) > -0.2f) WHITE else GOLD_DARK
                val cx = x + size / 2 - 2; val cy = y + 3
                rect(cx, cy, 4, 1, q); rect(cx + 3, cy + 1, 1, 2, q); rect(cx + 1, cy + 3, 2, 1, q); rect(cx + 1, cy + 4, 1, 2, q); put(cx + 1, cy + 7, q)
            }
        }
    }

    private fun digit(d: Int, x: Int, y: Int, c: Int) {
        val glyph = when (d) {
            1 -> intArrayOf(0b010, 0b110, 0b010, 0b010, 0b111)
            4 -> intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001)
            else -> intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111)
        }
        for (r in 0 until 5) for (b in 0 until 3) if ((glyph[r] shr (2 - b)) and 1 == 1) put(x + b, y + r, c)
    }
}
