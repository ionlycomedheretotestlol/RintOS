package dev.rint.launcher.intro

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.rint.launcher.ui.BlockFont
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * A tiny real-3D renderer for the intro, in plain Kotlin: a perspective camera, shaded cubes,
 * backface culling, depth sorting and fog, all drawn in one Canvas.drawVertices call per frame.
 * No WebView, no WebGL, no GPU driver quirks — it runs anywhere Android draws.
 */
internal class Voxel3D(private val accent: Int) {
    // ── camera ─────────────────────────────────────────
    private var ex = 0f; private var ey = 0f; private var ez = 30f
    private var rx = 1f; private var ry = 0f; private var rz = 0f
    private var ux = 0f; private var uy = 1f; private var uz = 0f
    private var fx = 0f; private var fy = 0f; private var fz = -1f
    private var focal = 1f; private var cxs = 0f; private var cys = 0f
    private var fovDeg = 55f; private var aspect = 0.5f

    private fun lookAt(eyeX: Float, eyeY: Float, eyeZ: Float, tx: Float, ty: Float, tz: Float, roll: Float = 0f) {
        ex = eyeX; ey = eyeY; ez = eyeZ
        var ax = tx - ex; var ay = ty - ey; var az = tz - ez
        var n = sqrt(ax * ax + ay * ay + az * az); ax /= n; ay /= n; az /= n
        fx = ax; fy = ay; fz = az
        val upx = sin(roll); val upy = cos(roll); val upz = 0f
        // right = f × up
        var qx = fy * upz - fz * upy; var qy = fz * upx - fx * upz; var qz = fx * upy - fy * upx
        n = sqrt(qx * qx + qy * qy + qz * qz).coerceAtLeast(1e-5f); qx /= n; qy /= n; qz /= n
        rx = qx; ry = qy; rz = qz
        // up = right × f
        ux = ry * fz - rz * fy; uy = rz * fx - rx * fz; uz = rx * fy - ry * fx
    }

    private fun viewport(w: Float, h: Float, fov: Float) {
        fovDeg = fov; aspect = w / h
        focal = (h / 2f) / tan(Math.toRadians(fov / 2.0).toFloat())
        cxs = w / 2f; cys = h / 2f
    }

    /** How far the camera must be for a [w]×[h] thing to fit the (portrait) screen. */
    private fun fitDistance(w: Float, h: Float, margin: Float): Float {
        val v = Math.toRadians(fovDeg / 2.0).toFloat()
        val hf = kotlin.math.atan(tan(v) * aspect)
        return max((w / 2f) * margin / tan(hf), (h / 2f) * margin / tan(v))
    }

    // camera-space helpers (reuse fields to avoid allocation)
    private var pz = 0f; private var psx = 0f; private var psy = 0f
    private fun project(x: Float, y: Float, z: Float): Boolean {
        val dx = x - ex; val dy = y - ey; val dz = z - ez
        val cz = dx * fx + dy * fy + dz * fz
        if (cz < 0.4f) return false
        val cx = dx * rx + dy * ry + dz * rz
        val cy = dx * ux + dy * uy + dz * uz
        pz = cz; psx = cxs + cx * focal / cz; psy = cys - cy * focal / cz
        return true
    }

    // ── face buffer ───────────────────────────────────
    private var faceCount = 0
    private var fPts = FloatArray(8 * 4096)       // 4 screen points per face
    private var fCol = IntArray(4096)
    private var fDepth = FloatArray(4096)
    private var keys = LongArray(4096)
    private var verts = FloatArray(12 * 4096)
    private var cols = IntArray(6 * 4096)
    private val vtxPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun ensure(n: Int) {
        if (n <= fCol.size) return
        val c = max(n, fCol.size * 2)
        fPts = fPts.copyOf(8 * c); fCol = fCol.copyOf(c); fDepth = fDepth.copyOf(c); keys = keys.copyOf(c)
        verts = verts.copyOf(12 * c); cols = cols.copyOf(6 * c)
    }

    private val bg = 0xFF02030A.toInt()
    private fun shade(color: Int, light: Float, depth: Float, fog: Float): Int {
        val f = (1f - exp(-depth * fog)).coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val c = ((color shr shift) and 0xFF) * light
            val b = (bg shr shift) and 0xFF
            return (c * (1 - f) + b * f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private val corner = FloatArray(24)
    private val cornerOk = BooleanArray(8)
    private val cSx = FloatArray(8); private val cSy = FloatArray(8); private val cZ = FloatArray(8)
    private val faces = arrayOf(
        intArrayOf(1, 3, 7, 5), intArrayOf(0, 4, 6, 2), intArrayOf(2, 6, 7, 3),
        intArrayOf(0, 1, 5, 4), intArrayOf(4, 5, 7, 6), intArrayOf(0, 2, 3, 1),
    )
    private val normals = arrayOf(floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
        floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f))
    private val lx = 0.35f; private val ly = 0.75f; private val lz = 0.56f
    var glow = 0f      // the kick, brightens accent-facing sides
    var fog = 0.018f

    /** Adds a cube (center, edge, rotation in radians, color) to this frame. */
    fun cube(x: Float, y: Float, z: Float, s: Float, color: Int, ax: Float = 0f, ay: Float = 0f, az: Float = 0f, sz: Float = s) {
        val h = s / 2f; val hz = sz / 2f
        val rot = ax != 0f || ay != 0f || az != 0f
        val cxr = cos(ax); val sxr = sin(ax); val cyr = cos(ay); val syr = sin(ay); val czr = cos(az); val szr = sin(az)
        for (i in 0 until 8) {
            var px = if (i and 1 != 0) h else -h
            var py = if (i and 2 != 0) h else -h
            var pzl = if (i and 4 != 0) hz else -hz
            if (rot) {
                // Rx, then Ry, then Rz
                val y1 = py * cxr - pzl * sxr; val z1 = py * sxr + pzl * cxr; py = y1; pzl = z1
                val x2 = px * cyr + pzl * syr; val z2 = -px * syr + pzl * cyr; px = x2; pzl = z2
                val x3 = px * czr - py * szr; val y3 = px * szr + py * czr; px = x3; py = y3
            }
            corner[i * 3] = x + px; corner[i * 3 + 1] = y + py; corner[i * 3 + 2] = z + pzl
            cornerOk[i] = project(corner[i * 3], corner[i * 3 + 1], corner[i * 3 + 2])
            cSx[i] = psx; cSy[i] = psy; cZ[i] = pz
        }
        for (f in 0 until 6) {
            val idx = faces[f]
            if (!(cornerOk[idx[0]] && cornerOk[idx[1]] && cornerOk[idx[2]] && cornerOk[idx[3]])) continue
            // normal (rotated)
            var nx = normals[f][0]; var ny = normals[f][1]; var nz = normals[f][2]
            if (rot) {
                val y1 = ny * cxr - nz * sxr; val z1 = ny * sxr + nz * cxr; ny = y1; nz = z1
                val x2 = nx * cyr + nz * syr; val z2 = -nx * syr + nz * cyr; nx = x2; nz = z2
                val x3 = nx * czr - ny * szr; val y3 = nx * szr + ny * czr; nx = x3; ny = y3
            }
            // backface cull: face must look toward the eye
            val mx = (corner[idx[0] * 3] + corner[idx[2] * 3]) / 2f
            val my = (corner[idx[0] * 3 + 1] + corner[idx[2] * 3 + 1]) / 2f
            val mz = (corner[idx[0] * 3 + 2] + corner[idx[2] * 3 + 2]) / 2f
            if (nx * (ex - mx) + ny * (ey - my) + nz * (ez - mz) <= 0f) continue
            val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
            val rim = max(0f, -nx * 0.7f + ny * 0.2f)
            val light = 0.66f + 0.5f * diffuse + glow * 0.35f * rim
            val depth = (cZ[idx[0]] + cZ[idx[1]] + cZ[idx[2]] + cZ[idx[3]]) / 4f
            addFace(cSx[idx[0]], cSy[idx[0]], cSx[idx[1]], cSy[idx[1]], cSx[idx[2]], cSy[idx[2]], cSx[idx[3]], cSy[idx[3]],
                shade(color, light.coerceAtMost(1.35f), depth, fog), depth)
        }
    }

    private val quadPts = FloatArray(8)

    /** A flat quad in world space (confetti). */
    fun quad(x: Float, y: Float, z: Float, w: Float, h: Float, rotA: Float, rotB: Float, color: Int) {
        val ca = cos(rotA); val sa = sin(rotA); val cb = cos(rotB); val sb = sin(rotB)
        val pts = quadPts
        var d = 0f
        for (i in 0 until 4) {
            val qx = (if (i == 1 || i == 2) w else -w) / 2f
            val qy = (if (i >= 2) h else -h) / 2f
            val px = qx * ca - qy * sa
            val py = (qx * sa + qy * ca) * cb
            val pzl = (qx * sa + qy * ca) * sb
            if (!project(x + px, y + py, z + pzl)) return
            pts[i * 2] = psx; pts[i * 2 + 1] = psy; d += pz
        }
        addFace(pts[0], pts[1], pts[2], pts[3], pts[4], pts[5], pts[6], pts[7], color, d / 4f)
    }

    private fun addFace(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int, depth: Float) {
        ensure(faceCount + 1)
        val o = faceCount * 8
        fPts[o] = x0; fPts[o + 1] = y0; fPts[o + 2] = x1; fPts[o + 3] = y1
        fPts[o + 4] = x2; fPts[o + 5] = y2; fPts[o + 6] = x3; fPts[o + 7] = y3
        fCol[faceCount] = color; fDepth[faceCount] = depth
        faceCount++
    }

    fun begin() { faceCount = 0 }

    /** Sorts far-to-near and draws everything in one call. */
    fun flush(c: Canvas) {
        val n = faceCount
        if (n == 0) return
        for (i in 0 until n) keys[i] = (java.lang.Float.floatToIntBits(fDepth[i].coerceAtLeast(0f)).toLong() shl 20) or i.toLong()
        java.util.Arrays.sort(keys, 0, n)
        var v = 0
        for (k in n - 1 downTo 0) {
            val i = (keys[k] and 0xFFFFF).toInt()
            val o = i * 8
            val col = fCol[i]
            // two triangles: 0-1-2, 0-2-3
            verts[v * 2] = fPts[o]; verts[v * 2 + 1] = fPts[o + 1]; cols[v++] = col
            verts[v * 2] = fPts[o + 2]; verts[v * 2 + 1] = fPts[o + 3]; cols[v++] = col
            verts[v * 2] = fPts[o + 4]; verts[v * 2 + 1] = fPts[o + 5]; cols[v++] = col
            verts[v * 2] = fPts[o]; verts[v * 2 + 1] = fPts[o + 1]; cols[v++] = col
            verts[v * 2] = fPts[o + 4]; verts[v * 2 + 1] = fPts[o + 5]; cols[v++] = col
            verts[v * 2] = fPts[o + 6]; verts[v * 2 + 1] = fPts[o + 7]; cols[v++] = col
        }
        c.drawVertices(Canvas.VertexMode.TRIANGLES, v * 2, verts, 0, null, 0, cols, 0, null, 0, 0, vtxPaint)
        faceCount = 0
    }

    // ═════════════════════ the scenes ═════════════════════

    private class Voxel(val tx: Float, val ty: Float, val tz: Float, val sx: Float, val sy: Float, val sz: Float,
                        val spinX: Float, val spinY: Float, val spinZ: Float, val delay: Float, val wave: Float, val color: Int, val back: Boolean)

    private class Word(val voxels: List<Voxel>, val w: Float, val h: Float, val cell: Float)

    private val rnd = Random(7)
    private val white = 0xFFFFFFFF.toInt()
    private val backWhite = 0xFFC9CFE6.toInt()
    private val backAccent = run {
        val r = ((accent shr 16) and 0xFF) * 7 / 10; val g = ((accent shr 8) and 0xFF) * 7 / 10; val b = (accent and 0xFF) * 7 / 10
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun word(lines: List<String>, accentLine: (Int, Int) -> Boolean, cell: Float, depth: Int): Word {
        val w = lines.maxOf { BlockFont.cells(it).second }
        val out = ArrayList<Voxel>()
        val h = lines.size * 6 - 1
        lines.forEachIndexed { li, l ->
            val (cells, lw) = BlockFont.cells(l)
            val off = (w - lw) / 2
            cells.forEach { (cx0, cy0) ->
                val cx = cx0 + off; val cy = cy0 + li * 6
                for (z in 0 until depth) {
                    val tx = (cx - w / 2f + 0.5f) * cell; val ty = (h / 2f - cy - 0.5f) * cell; val tz = -z * cell
                    // start somewhere far away, in a random direction
                    val th = rnd.nextFloat() * 2 * PI.toFloat(); val ph = acos(2 * rnd.nextFloat() - 1)
                    val r = 40f + rnd.nextFloat() * 60f
                    val acc = accentLine(li, cx0)
                    out += Voxel(tx, ty, tz, tx + r * sin(ph) * cos(th), ty + r * cos(ph), tz + r * sin(ph) * sin(th),
                        rnd.nextFloat() * 6, rnd.nextFloat() * 6, rnd.nextFloat() * 6, rnd.nextFloat(), cx0 * 0.35f,
                        if (acc) (if (z > 0) backAccent else accent) else (if (z > 0) backWhite else white), z > 0)
                }
            }
        }
        return Word(out, w * cell, h * cell, cell)
    }

    private val stack = word(listOf("RINT", "OS"), { line, _ -> line == 1 }, 1f, 2)
    private val version = word(listOf("1.3"), { _, x -> x >= 4 }, 0.7f, 1)
    private val big = word(listOf("1.3"), { _, x -> x >= 4 }, 1.6f, 2)
    private val ring = word(listOf("RINTOS"), { _, x -> x >= 18 }, 0.5f, 1)

    private val stars = FloatArray(900 * 3).also {
        for (i in 0 until 900) {
            val r = 80 + rnd.nextFloat() * 220; val th = rnd.nextFloat() * 2 * PI.toFloat(); val ph = acos(2 * rnd.nextFloat() - 1)
            it[i * 3] = r * sin(ph) * cos(th); it[i * 3 + 1] = r * cos(ph); it[i * 3 + 2] = r * sin(ph) * sin(th)
        }
    }
    private val starPts = FloatArray(900 * 2)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND; strokeWidth = 3f }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = white; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val confetti = List(260) { floatArrayOf((rnd.nextFloat() - .5f) * 40, rnd.nextFloat() * 30, (rnd.nextFloat() - .5f) * 20, 3 + rnd.nextFloat() * 5, rnd.nextFloat() * 6, rnd.nextFloat() * 6) }
    private val palette = intArrayOf(accent, white, 0xFFFFD60A.toInt(), 0xFFFF6FB5.toInt(), 0xFF39FF88.toInt())

    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
    private fun easeInOut(x: Float): Float { val c = clamp01(x); return if (c < .5f) 4 * c * c * c else 1 - (-2 * c + 2).pow(3) / 2 }
    private fun easeOutBack(x: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1; val p = clamp01(x) - 1; return 1 + c3 * p * p * p + c1 * p * p }
    private fun easeOutExpo(x: Float) = if (x >= 1f) 1f else 1f - 2f.pow(-10f * clamp01(x))

    private fun kick(t: Float): Float {
        val bar = floor(t / Score.BAR.toFloat()).toInt()
        if (!Score.kick(bar)) return 0f
        val ph = t / Score.BEAT.toFloat() - floor(t / Score.BEAT.toFloat())
        return exp(-ph * 7f)
    }

    private fun drawStars(c: Canvas, spin: Float) {
        var n = 0
        val cs = cos(spin); val sn = sin(spin)
        for (i in 0 until 900) {
            val x = stars[i * 3] * cs - stars[i * 3 + 2] * sn
            val z = stars[i * 3] * sn + stars[i * 3 + 2] * cs
            if (project(x, stars[i * 3 + 1], z)) { starPts[n * 2] = psx; starPts[n * 2 + 1] = psy; n++ }
        }
        c.drawPoints(starPts, 0, n * 2, starPaint)
    }

    private fun placeWord(wd: Word, ox: Float, oy: Float, oz: Float, fn: (Voxel, FloatArray) -> Boolean) {
        val o = FloatArray(7) // x, y, z, scale, rx, ry, rz
        for (v in wd.voxels) {
            if (!fn(v, o)) continue
            cube(ox + o[0], oy + o[1], oz + o[2], 0.9f * wd.cell * o[3], v.color, o[4], o[5], o[6])
        }
    }

    /** Draws the 3D part of the film at time [t] (seconds); returns false outside 3D sections. */
    fun render(c: Canvas, w: Float, h: Float, t: Float): Boolean {
        val bar = floor(t / Score.BAR.toFloat()).toInt()
        val k = kick(t)
        val K = 2f / Score.BAR.toFloat()       // choreography is written in "120 BPM seconds"
        viewport(w, h, 55f)
        glow = k
        begin()
        when {
            bar in Score.RISE until Score.BUILD -> {
                fog = 0.006f
                val lt = (t - Score.RISE * Score.BAR.toFloat()) * K
                val orbit = -1.25f + 1.25f * easeInOut(lt / 6.2f)
                val fit = fitDistance(stack.w, stack.h + 9f, 1.12f)
                val rad = fit * (1.5f - 0.5f * easeInOut(lt / 6.5f)) - (if (lt > 7f) (lt - 7f) * fit * 0.6f else 0f)
                lookAt(sin(orbit) * rad, 9f - 8f * easeInOut(lt / 6f), cos(orbit) * rad, 0f, -0.8f, 0f)
                drawStars(c, t * 0.02f)
                // receding floor grid in the accent color
                linePaint.color = (accent and 0x00FFFFFF) or 0x66000000
                val gz = (t * 6f) % 2f
                for (i in -20..20) {
                    if (project(i * 2f, -9f, -60f) ) { val ax = psx; val ay = psy; if (project(i * 2f, -9f, 60f)) c.drawLine(ax, ay, psx, psy, linePaint) }
                    val z = i * 3f + gz
                    if (project(-60f, -9f, z)) { val ax = psx; val ay = psy; if (project(60f, -9f, z)) c.drawLine(ax, ay, psx, psy, linePaint) }
                }
                placeWord(stack, 0f, 2.2f, 0f) { v, o ->
                    val p = easeOutBack((lt - v.delay * 2.4f) / 1.6f)
                    val q = clamp01(p) + (if (p > 1f) p - 1f else 0f)
                    o[0] = v.sx + (v.tx - v.sx) * q; o[1] = v.sy + (v.ty - v.sy) * q; o[2] = v.sz + (v.tz - v.sz) * q
                    val b = if (lt > 4f) 1f + 0.22f * k * (0.5f + 0.5f * sin(v.wave + t * 6f)) else 1f
                    o[3] = b; o[4] = v.spinX * (1 - clamp01(p)); o[5] = v.spinY * (1 - clamp01(p)); o[6] = v.spinZ * (1 - clamp01(p))
                    true
                }
                placeWord(version, 0f, -6.6f, 0.6f) { v, o ->
                    val p = easeOutExpo((lt - 4f - v.delay * 0.4f) / 0.6f)
                    if (p <= 0.01f) return@placeWord false
                    o[0] = v.tx; o[1] = v.ty; o[2] = v.tz + (1 - p) * 30f; o[3] = p; o[4] = 0f; o[5] = 0f; o[6] = 0f
                    true
                }
                flush(c)
            }
            bar in Score.TUNNEL until Score.LIFT -> {
                fog = 0.014f
                val lt = (t - Score.TUNNEL * Score.BAR.toFloat()) * K
                val travel = lt * 45f + (if (lt > 5f) (lt - 5f).pow(3) * 10f else 0f)
                val fov = 60f + 45f * clamp01((lt - 5f) / 3f) + 6f * k
                viewport(w, h, fov)
                val camX = sin(t * 0.7f) * 0.8f; val camY = cos(t * 0.5f) * 0.6f
                lookAt(camX, camY, -travel, camX * 0.5f, camY * 0.5f, -travel - 20f, sin(t * 0.4f) * 0.25f)
                val rings = 30; val per = 16; val gap = 7f; val span = rings * gap
                for (r in 0 until rings) {
                    val z = -travel + 6f - (((-travel + 6f + r * gap) % span) + span) % span
                    val rot = t * (if (r % 2 == 1) 0.6f else -0.6f) + r * 0.2f
                    val radius = 7f + 0.8f * sin(r * 0.7f + t * 2f) + 1.2f * k
                    val col = if (r % 3 == 0) accent else white
                    for (q in 0 until per) {
                        val a = rot + q.toFloat() / per * 2f * PI.toFloat()
                        cube(cos(a) * radius, sin(a) * radius, z, 1.2f * (1f + 0.4f * k), col, 0f, 0f, a, sz = 2.6f)
                    }
                }
                flush(c)
                // feature cards flying past
                val feats = tunnelCards
                feats.forEachIndexed { j, text ->
                    val at = 0.35f + j * 0.62f
                    val z = -(at * 45f) - 24f
                    val x = (if (j % 2 == 1) 1f else -1f) * 2.2f
                    val y = ((j % 3) - 1) * 1.6f
                    if (!project(x, y, z)) return@forEachIndexed
                    if (pz > 70f) return@forEachIndexed
                    val s = focal / pz
                    val cw = 8f * s; val ch = 2f * s
                    val vis = clamp01((70f - pz) / 20f)
                    cardPaint.color = if (j % 2 == 0) accent else 0xFF2A2F45.toInt()
                    cardPaint.alpha = (255 * vis).toInt()
                    c.drawRoundRect(RectF(psx - cw / 2, psy - ch / 2, psx + cw / 2, psy + ch / 2), ch / 2, ch / 2, cardPaint)
                    // fit the text inside the card
                    textPaint.textSize = ch * 0.42f
                    val tw = textPaint.measureText(text)
                    if (tw > cw * 0.86f) textPaint.textSize *= cw * 0.86f / tw
                    textPaint.alpha = (255 * vis).toInt()
                    c.drawText(text, psx, psy + ch * 0.15f, textPaint)
                }
            }
            bar in Score.FINAL until Score.END -> {
                fog = 0.003f
                val lt = (t - Score.FINAL * Score.BAR.toFloat()) * K
                val outro = max(0f, t - Score.OUTRO * Score.BAR.toFloat()) * K
                val slam = easeOutExpo(lt / 0.45f)
                val pull = easeInOut(outro / 4f)
                val fitC = fitDistance(big.w * 1.1f, big.h * 2.4f, 1.15f)
                var camX = sin(t * 0.25f) * fitC * 0.18f
                var camY = -2f + 2f * pull
                if (lt < 0.5f) { val sh = (1f - lt / 0.5f) * 2.5f; camX += (rnd.nextFloat() - .5f) * sh; camY += (rnd.nextFloat() - .5f) * sh }
                lookAt(camX, camY, fitC * (1f + 0.3f * pull) + (1f - slam) * 10f, 0f, 3.5f, 0f)
                drawStars(c, t * 0.01f)
                val spin = sin(t * 0.6f) * 0.35f
                val cs = cos(spin); val sn = sin(spin)
                val fade = 1f - clamp01((outro - 2.8f) / 1.2f)
                if (fade > 0f) {
                    placeWord(big, 0f, 0f, 0f) { v, o ->
                        val q = clamp01(slam + v.delay * 0.02f)
                        val x = v.sx + (v.tx - v.sx) * q; val z = v.sz + (v.tz - v.sz) * q
                        o[0] = x * cs + z * sn; o[1] = 5.5f + v.sy + (v.ty - v.sy) * q; o[2] = -x * sn + z * cs
                        o[3] = if (v.back) 1f else 1f + 0.28f * k * (0.6f + 0.4f * sin(v.wave * 2f + t * 5f))
                        o[4] = 0f; o[5] = spin; o[6] = 0f
                        true
                    }
                    val rr = big.w * 0.75f
                    val show = clamp01((lt - 1.2f) / 0.6f)
                    if (show > 0f) placeWord(ring, 0f, 0f, 0f) { v, o ->
                        val a = v.tx / 9f + t * 0.4f
                        o[0] = cos(a) * rr; o[1] = 5.5f + v.ty * 0.9f - 1f; o[2] = sin(a) * rr
                        o[3] = show; o[4] = 0f; o[5] = -a; o[6] = 0f
                        true
                    }
                    if (lt > 0.3f) confetti.forEachIndexed { i, cf ->
                        val y = 22f - ((lt * cf[3] + cf[1]) % 34f)
                        quad(cf[0] + sin(lt + cf[4]) * 1.5f, y, cf[2], 0.35f, 0.18f, lt * cf[5], lt * cf[4], palette[i % palette.size])
                    }
                    flush(c)
                }
            }
            else -> return false
        }
        return true
    }

    private val tunnelCards = listOf(
        "Rin, your AI", "songs play right here", "${dev.rint.launcher.settings.Schema.optionCount} settings", "10 lock screens",
        "your photo & live wallpapers", "battery saver dot", "no ads. no tracking.", "…and it's free",
    )
}

/** The native 3D layer of the film. Draw-phase only: it never recomposes. */
@Composable
internal fun Native3D(time: () -> Float, modifier: Modifier = Modifier) {
    val accent = dev.rint.launcher.ui.LocalRintOrNull.current?.colors?.accent?.toArgb() ?: 0xFF3B7CFF.toInt()
    val engine = remember(accent) { Voxel3D(accent) }
    androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
        val t = time()
        drawIntoCanvas { engine.render(it.nativeCanvas, size.width, size.height, t) }
    }
}
