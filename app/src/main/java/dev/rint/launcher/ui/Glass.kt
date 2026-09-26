package dev.rint.launcher.ui

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import dev.rint.launcher.R
import dev.rint.launcher.core.WallpaperArt
import dev.rint.launcher.core.WallpaperMode
import kotlin.math.roundToInt

@DrawableRes
fun WallpaperArt.res(): Int = when (this) {
    WallpaperArt.TIDE -> R.drawable.wp_tide
    WallpaperArt.PIXEL_NIGHT -> R.drawable.wp_pixel
    WallpaperArt.PAPER -> R.drawable.wp_paper
}

/**
 * A tiny copy of the wallpaper. Drawn stretched with bilinear filtering it *is* a strong,
 * cheap gaussian-ish blur — the basis of RintOS's frosted glass.
 */
class Backdrop(val full: ImageBitmap, val tiny: ImageBitmap)

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(): Backdrop? {
    val l = LocalRint.current.cfg.look
    if (l.wallpaper == WallpaperMode.PHOTO) return rememberPhotoBackdrop(l.photoVersion)
    if (l.wallpaper != WallpaperMode.ART) return null
    val full = ImageBitmap.imageResource(l.art.res())
    return remember(full) { makeBackdrop(full) }
}

/** The user's own photo wallpaper, decoded off the main thread at screen size. */
@Composable
private fun rememberPhotoBackdrop(version: Long): Backdrop? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val state = androidx.compose.runtime.produceState<Backdrop?>(null, version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val f = PhotoWallpaper.file(ctx)
                if (!f.exists()) return@runCatching null
                val bmp = android.graphics.BitmapFactory.decodeFile(f.path) ?: return@runCatching null
                makeBackdrop(bmp.asImageBitmap())
            }.getOrNull()
        }
    }
    return state.value
}

/** Stores the picked photo as a screen-sized JPEG inside the app (no permission needed later). */
object PhotoWallpaper {
    fun file(ctx: android.content.Context) = java.io.File(ctx.filesDir, "wallpaper.jpg")

    fun save(ctx: android.content.Context, uri: android.net.Uri): Boolean = runCatching {
        val dm = ctx.resources.displayMetrics
        val target = maxOf(dm.widthPixels, dm.heightPixels).coerceAtMost(2400)
        val src = android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val big = maxOf(info.size.width, info.size.height)
            if (big > target) { val k = target.toFloat() / big; dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        true
    }.getOrDefault(false)
}

private fun makeBackdrop(full: ImageBitmap): Backdrop {
    return run {
        val src = full.asAndroidBitmap()
        val w = 120
        val h = (src.height * w.toFloat() / src.width).roundToInt()
        val small = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        Backdrop(full, boxBlur(small, radius = 7, passes = 3).asImageBitmap())
    }
}

/** Three box-blur passes ≈ a gaussian. Runs once per wallpaper on a 120px-wide copy. */
private fun boxBlur(b: Bitmap, radius: Int, passes: Int): Bitmap {
    val w = b.width
    val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            var r = 0; var g = 0; var bl = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + c] else src[c * w + line]
            }
            for (i in -radius..radius) { val p = at(i); r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; bl += p and 0xFF }
            val n = radius * 2 + 1
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                dst[idx] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (bl / n)
                val add = at(i + radius + 1)
                val sub = at(i - radius)
                r += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                bl += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
    repeat(passes) {
        pass(px, tmp, true)
        pass(tmp, px, false)
    }
    b.setPixels(px, 0, w, 0, 0, w, h)
    return b
}

/** Where a Crop-scaled image of [img] lands inside a [root]-sized screen. */
private fun cropRect(img: IntSize, root: IntSize): Pair<IntOffset, IntSize> {
    val s = maxOf(root.width / img.width.toFloat(), root.height / img.height.toFloat())
    val w = (img.width * s).roundToInt()
    val h = (img.height * s).roundToInt()
    return IntOffset((root.width - w) / 2, (root.height - h) / 2) to IntSize(w, h)
}

/**
 * iOS-style material: the wallpaper behind this element, heavily blurred, a soft tint for
 * legibility, and a hairline specular edge. Falls back to a solid tint without a backdrop.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Float = 1f, edge: Boolean = true): Modifier {
    val look = LocalRint.current
    val backdrop = LocalBackdrop.current
    val view = LocalView.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val dark = look.colors.dark
    val veil = if (dark) Color(0xFF05070F).copy(alpha = (0.30f * tint).coerceAtMost(0.9f)) else Color.White.copy(alpha = (0.5f * tint).coerceAtMost(0.9f))
    val lift = if (dark) Color.White.copy(alpha = 0.07f * tint) else Color.White.copy(alpha = 0.18f * tint)
    return this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (backdrop != null) {
                val root = IntSize(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
                val (off, sz) = cropRect(IntSize(backdrop.tiny.width, backdrop.tiny.height), root)
                drawImage(
                    backdrop.tiny,
                    dstOffset = IntOffset(off.x - pos.x.roundToInt(), off.y - pos.y.roundToInt()),
                    dstSize = sz,
                    filterQuality = FilterQuality.Medium,
                )
                drawRect(veil)
                drawRect(lift)
            } else {
                drawRect(look.colors.panel)
            }
        }
        .then(
            if (edge) Modifier.border(
                0.8.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.30f else 0.7f), Color.White.copy(alpha = 0.04f))),
                shape,
            ) else Modifier
        )
}
