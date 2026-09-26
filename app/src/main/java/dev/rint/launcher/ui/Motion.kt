package dev.rint.launcher.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import dev.rint.launcher.core.Motion

/** Global motion knobs, driven by Settings → Motion. */
object RintSprings {
    @Volatile var speed = 1f
    @Volatile var bounce = 0.62f
    @Volatile var reduce = false

    fun apply(m: Motion) {
        speed = m.speed.coerceIn(0.25f, 3f)
        bounce = m.bounce.coerceIn(0.15f, 1f)
        reduce = m.reduce
    }

    private fun stiff(base: Float) = if (reduce) Spring.StiffnessHigh else base * speed * speed

    fun <T> sheet(): SpringSpec<T> = spring(dampingRatio = if (reduce) 1f else (bounce + 0.28f).coerceAtMost(1f), stiffness = stiff(420f))
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = if (reduce) 1f else bounce * 0.75f, stiffness = stiff(Spring.StiffnessMediumLow))
    fun <T> soft(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = stiff(Spring.StiffnessLow))
}
