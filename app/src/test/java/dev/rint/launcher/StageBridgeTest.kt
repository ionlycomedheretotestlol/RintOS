package dev.rint.launcher

import android.webkit.JavascriptInterface
import dev.rint.launcher.intro.StageBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * WebView calls the 3D stage's bridge by reflection. A non-public class makes every call fail
 * silently (the 1.3 bug where three.js never showed up), so lock the contract down here.
 */
class StageBridgeTest {
    @Test fun bridgeIsCallableFromJavaScript() {
        val c = StageBridge::class.java
        assertTrue("bridge class must be public", Modifier.isPublic(c.modifiers))
        val exposed = c.methods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }.map { it.name }.toSet()
        assertEquals(setOf("time", "config", "ready", "fail"), exposed)
        c.methods.filter { it.name in exposed }.forEach { assertTrue("${it.name} must be public", Modifier.isPublic(it.modifiers)) }
    }
}
