package dev.rint.launcher.home

import android.app.ActivityOptions
import android.view.View
import androidx.compose.ui.geometry.Rect
import dev.rint.launcher.RintApp
import dev.rint.launcher.apps.AppEntry
import dev.rint.launcher.core.OpenAnim

fun launchApp(view: View, entry: AppEntry, bounds: Rect?) {
    val anim = RintApp.instance.stores.config.value.motion.openAnim
    val r = bounds?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }
    val opts = when {
        r == null -> null
        anim == OpenAnim.SCALE_UP -> ActivityOptions.makeScaleUpAnimation(view.rootView, r.left, r.top, r.width(), r.height())
        anim == OpenAnim.CLIP_REVEAL -> ActivityOptions.makeClipRevealAnimation(view.rootView, r.left, r.top, r.width(), r.height())
        anim == OpenAnim.NONE -> ActivityOptions.makeCustomAnimation(view.context, 0, 0)
        else -> null
    }
    RintApp.instance.apps.launch(entry, r, opts?.toBundle())
}
