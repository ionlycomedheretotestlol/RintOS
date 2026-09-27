package dev.rint.launcher

import dev.rint.launcher.core.HomeItem
import dev.rint.launcher.core.HomeLayout
import dev.rint.launcher.core.ItemKind
import dev.rint.launcher.music.LyricLine
import dev.rint.launcher.music.Lyrics
import dev.rint.launcher.music.LyricsService
import dev.rint.launcher.search.Calc
import dev.rint.launcher.search.Fuzzy
import dev.rint.launcher.search.Units
import dev.rint.launcher.widgets.timeInWords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun calc() {
        assertEquals(14.0, Calc.eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, Calc.eval("(2+3)*4")!!, 1e-9)
        assertEquals(1024.0, Calc.eval("2^10")!!, 1e-9)
        assertEquals(6.0, Calc.eval("2(3)")!!, 1e-9)
        assertEquals(0.5, Calc.eval("50%")!!, 1e-9)
        assertEquals(12.0, Calc.eval("3×4")!!, 1e-9)
        assertEquals(1.0, Calc.eval("sin(90)")!!, 1e-9)
        assertNull(Calc.eval("2+"))
        assertTrue(Calc.looksLikeMath("12*4"))
        assertFalse(Calc.looksLikeMath("tiktok"))
        assertFalse(Calc.looksLikeMath("spotify"))
        assertEquals("1,234", Calc.format(1234.0))
        assertEquals("0.25", Calc.format(0.25))
    }

    @Test fun units() {
        assertEquals("5 km = 3.107 mi", Units.convert("5 km to mi"))
        assertEquals("100 c = 212 f", Units.convert("100 c to f"))
        assertNull(Units.convert("5 km to kg"))
    }

    @Test fun fuzzy() {
        assertTrue(Fuzzy.score("yt", "YouTube") > Fuzzy.score("yt", "Keyboard settings"))
        assertTrue(Fuzzy.score("spo", "Spotify") > Fuzzy.score("spo", "Sports news"))
        assertEquals(0, Fuzzy.score("xyz", "Camera"))
        assertTrue(Fuzzy.score("gm", "Google Maps") > 0)
    }

    @Test fun lrc() {
        val lines = LyricsService.parseLrc("[ar: x]\n[00:12.50]hello\n[00:15.2]world\n[01:02.345][01:30.00]chorus")
        assertEquals(listOf(12_500L, 15_200L, 62_345L, 90_000L), lines.map { it.timeMs })
        assertEquals("chorus", lines.last().text)
    }

    @Test fun lyricBreaks() {
        val l = Lyrics(listOf(LyricLine(10_000, "a"), LyricLine(12_000, "b"), LyricLine(40_000, "c"), LyricLine(42_000, "")), synced = true, source = "t")
        assertTrue("intro counts as a break", l.isBreak(2_000))
        assertFalse(l.isBreak(11_000))
        assertTrue("long gap after b is a break", l.isBreak(20_000))
        assertFalse("but not right after b starts", l.isBreak(13_000))
        assertTrue("blank line is a break", l.isBreak(43_000))
        assertEquals(1, l.indexAt(12_500))
        assertEquals(-1, l.indexAt(500))
    }

    @Test fun layoutFitsAndReflows() {
        val l = HomeLayout(pages = 1, items = listOf(
            HomeItem(page = 0, x = 0, y = 0, w = 4, h = 2, kind = ItemKind.WIDGET, widget = "clock"),
            HomeItem(page = 0, x = 4, y = 3, kind = ItemKind.APP, app = "a/b"),
        ))
        assertFalse(l.fits(0, 1, 1, 1, 1, 5, 6))
        assertTrue(l.fits(0, 0, 2, 1, 1, 5, 6))
        val r = l.reflow(4, 6)
        assertTrue(r.items.all { it.x + it.w <= 4 })
        assertEquals(2, r.items.size)
    }

    @Test fun words() {
        assertEquals("half past ten", timeInWords(10, 30))
        assertEquals("quarter to three", timeInWords(14, 44))
        assertEquals("twelve o'clock", timeInWords(0, 1))
    }

    @Test fun oldConfigsGetStreamingMusic() {
        // a 1.0-era config: music section saved with "APP" and no version field
        val old = """{"onboarded":true,"music":{"playVia":"APP","lyricsSize":30.0}}"""
        val cfg = dev.rint.launcher.core.RintJson.decodeFromString(dev.rint.launcher.core.RintConfig.serializer(), old)
        org.junit.Assert.assertEquals(0, cfg.music.version)
        org.junit.Assert.assertEquals(dev.rint.launcher.core.PlayVia.APP, cfg.music.playVia)
        // the retired YOUTUBE value falls back to streaming instead of breaking the config
        val yt = dev.rint.launcher.core.RintJson.decodeFromString(dev.rint.launcher.core.RintConfig.serializer(), """{"music":{"playVia":"YOUTUBE"}}""")
        org.junit.Assert.assertEquals(dev.rint.launcher.core.PlayVia.STREAM, yt.music.playVia)
    }
}
