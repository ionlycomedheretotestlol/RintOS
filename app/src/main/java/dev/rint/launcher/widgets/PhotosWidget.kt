package dev.rint.launcher.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.Icon
import dev.rint.launcher.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.rememberAmbientClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Picked photos are copied (downscaled) into the app, so the widget never loses access. */
private object PhotoStore {
    fun dir(ctx: Context, id: String) = File(ctx.filesDir, "photos/${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}").apply { mkdirs() }

    fun list(ctx: Context, id: String): List<File> = dir(ctx, id).listFiles()?.filter { it.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    fun add(ctx: Context, id: String, uris: List<Uri>) {
        val d = dir(ctx, id)
        uris.take(30).forEachIndexed { i, uri ->
            runCatching {
                val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                    val big = maxOf(info.size.width, info.size.height)
                    if (big > 1200) { val k = 1200f / big; dec.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt()) }
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                File(d, "${System.currentTimeMillis()}_$i.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
        }
    }

    fun clear(ctx: Context, id: String) = dir(ctx, id).listFiles()?.forEach { it.delete() }
}

/**
 * Photos: a slow slideshow of pictures you pick, with a gentle drift. Tap for the next one,
 * long-press to pick a new set. Decodes one photo at a time, and only while visible.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PhotosWidget(ctx: WidgetCtx) {
    val look = LocalRint.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val files by produceState(emptyList<File>(), version) { value = withContext(Dispatchers.IO) { PhotoStore.list(context, ctx.id) } }
    var index by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            withContext(Dispatchers.IO) { PhotoStore.clear(context, ctx.id); PhotoStore.add(context, ctx.id, uris) }
            index = 0; version++
        }
    }
    fun choose() {
        if (!ctx.preview) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    if (files.isEmpty()) {
        Column(
            Modifier.fillMaxSize().clickable { choose() }.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = look.colors.accent, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(6.dp))
            Text("Photos", color = look.colors.text, fontFamily = look.font, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (ctx.h >= 2) Text("tap to pick your favorites", color = look.colors.subtext, fontFamily = look.font, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
        }
        return
    }

    // advance every 7 s, but only while someone can see it
    val clock = rememberAmbientClock()
    val slot = (clock / 7f).toInt()
    LaunchedEffect(slot) { if (slot > 0) index = (index + 1) % files.size }
    val file = files[index % files.size]
    Box(
        Modifier.fillMaxSize().combinedClickable(onLongClick = { choose() }) { index = (index + 1) % files.size }
    ) {
        Crossfade(file, animationSpec = tween(900), label = "photo") { f ->
            val img by produceState<ImageBitmap?>(null, f) {
                value = withContext(Dispatchers.IO) { runCatching { android.graphics.BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() }
            }
            img?.let {
                Image(it, null, Modifier.fillMaxSize().graphicsLayer {
                    val k = (clock % 7f) / 7f
                    scaleX = 1.06f + 0.05f * k; scaleY = scaleX
                }, contentScale = ContentScale.Crop)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
        Text(
            "${index % files.size + 1}/${files.size}", color = Color.White.copy(alpha = 0.8f), fontFamily = RintFonts.Pixel, fontSize = 8.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(6.dp)),
        )
    }
}
