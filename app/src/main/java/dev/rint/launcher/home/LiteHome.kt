package dev.rint.launcher.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.rint.launcher.mascot.Pose
import dev.rint.launcher.mascot.RinSprite
import dev.rint.launcher.ui.LocalRint
import dev.rint.launcher.ui.RintFonts
import dev.rint.launcher.ui.Text
import kotlinx.coroutines.delay

/**
 * Saver Home: a lighter home for weak phones. Wallpaper, app pages, dock, search and the Rin
 * button stay; widgets and the wandering Rin go. [fade] is 1 while widgets are shown, 0 when gone.
 */
object LiteHome {
    val fade = Animatable(1f)
    var loading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
}

/** Runs the switch: a short loading screen, then the widgets float away (or come back). */
@Composable
fun LiteHomeController(lite: Boolean) {
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(lite) {
        if (first) { first = false; LiteHome.fade.snapTo(if (lite) 0f else 1f); return@LaunchedEffect }
        if (lite) {
            LiteHome.loading = true
            for (i in 1..28) { LiteHome.progress = i / 28f; delay(45) }
            delay(200)
            LiteHome.loading = false
            delay(250)
            LiteHome.fade.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        } else {
            LiteHome.fade.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
    }
}

@Composable
fun LiteHomeLoading() {
    val look = LocalRint.current
    Box(Modifier.fillMaxSize().background(Color(0xF2050608)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RinSprite(Pose.STRETCH, 96.dp)
            Spacer(Modifier.height(18.dp))
            Text("making home lighter…", color = Color.White, fontFamily = look.font, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.width(180.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(LiteHome.progress).clip(RoundedCornerShape(3.dp)).background(look.colors.accent))
            }
            Spacer(Modifier.height(10.dp))
            Text("packing away widgets", color = Color.White.copy(alpha = 0.45f), fontFamily = RintFonts.Pixel, fontSize = 9.sp)
        }
    }
}
