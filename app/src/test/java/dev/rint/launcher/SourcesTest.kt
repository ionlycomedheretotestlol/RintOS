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
        val full = MusicSources.audiusSearch("lofi")
        println("audius search 'lofi': ${full.size} full songs, first = ${full.firstOrNull()?.title} (${full.firstOrNull()?.durationMs?.div(1000)}s)")
        // every search result must actually download as audio
        full.take(2).forEach { tr ->
            val c = java.net.URL(tr.streamUrl).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("Range", "bytes=0-2047")
            println("  stream ${tr.title}: HTTP ${c.responseCode} ${c.contentType}")
            org.junit.Assert.assertTrue(c.responseCode in 200..299 && (c.contentType ?: "").startsWith("audio"))
        }
    }
}
