package dev.rint.launcher

import dev.rint.launcher.music.MusicSources
import dev.rint.launcher.music.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Hits the real music APIs. Only runs with RINT_NET=1 (CI stays offline). */
class SourcesTest {
    @Test fun findsSongs() = runBlocking {
        assumeTrue(System.getenv("RINT_NET") == "1")
        for (t in listOf(Track("Blinding Lights", "The Weeknd"), Track("Bohemian Rhapsody", "Queen"), Track("Pixel Heart", "Nobody"))) {
            val hits = MusicSources.all.map { it.name to runCatching { it.find(t) }.getOrNull() }
            println("${t.title}: " + hits.joinToString { (n, h) -> "$n=" + (h?.let { if (it.preview) "preview" else "FULL" } ?: "-") })
        }
    }
}
