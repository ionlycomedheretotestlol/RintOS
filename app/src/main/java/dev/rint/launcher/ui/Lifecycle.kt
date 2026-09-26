package dev.rint.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** True while something opaque covers the home screen, so ambient animations can rest. */
val LocalCovered = staticCompositionLocalOf { false }

/** True while the activity is at least STARTED (visible). */
@Composable
fun rememberForeground(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var fg by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, _ -> fg = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return fg
}

/**
 * A seconds counter that only advances while [running], the screen is visible and nothing
 * covers it. Ambient animations derive from it, so they freeze (and cost nothing) off-screen.
 */
@Composable
fun rememberAmbientClock(running: Boolean = true): Float {
    val active = running && rememberForeground() && !LocalCovered.current && !RintSprings.reduce
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = -1L
        while (true) withFrameMillis { ms ->
            if (last >= 0) t += (ms - last) / 1000f
            last = ms
        }
    }
    return t
}
