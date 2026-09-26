package dev.rint.launcher

import dev.rint.launcher.intro.IntroSynth
import dev.rint.launcher.intro.Score
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/** Renders the whole intro soundtrack offline: no NaNs, no dead air where there shouldn't be, no brick-wall clipping. */
class SynthTest {
    @Test fun timelineLevels() {
        val synth = IntroSynth()
        val frames = (IntroSynth.BAR * IntroSynth.SR).toInt()
        val bars = Score.END + 2
        val all = ShortArray(frames * 2 * bars)
        val buf = ShortArray(frames * 2)
        val report = StringBuilder()
        val start = System.nanoTime()
        for (b in 0 until bars) {
            if (b == Score.END) synth.switch(IntroSynth.Mode.CHILL)
            synth.render(buf, frames)
            buf.copyInto(all, b * frames * 2)
            var sum = 0.0; var peak = 0; var clipped = 0
            for (s in buf) { sum += s.toDouble() * s; peak = maxOf(peak, abs(s.toInt())); if (abs(s.toInt()) > 28000) clipped++ }
            val rms = sqrt(sum / buf.size) / Short.MAX_VALUE
            report.appendLine("bar %2d %-8s rms %.3f peak %.2f hot %.3f".format(b, Score.part(b), rms, peak / 32767.0, clipped.toDouble() / buf.size))
            if (Score.part(b) != Score.Part.SILENCE && b >= 1) assertTrue("bar $b is silent", rms > 0.01)
            assertTrue("bar $b is a wall of clipping", clipped.toDouble() / buf.size < 0.05)
        }
        val secs = (System.nanoTime() - start) / 1e9
        report.appendLine("rendered ${bars * IntroSynth.BAR}s of audio in %.2fs".format(secs))
        println(report)
        System.getenv("RINT_WAV")?.let { writeWav(File(it), all) }
    }

    private fun writeWav(f: File, pcm: ShortArray) {
        DataOutputStream(FileOutputStream(f).buffered()).use { o ->
            fun le32(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff); o.write(v shr 16 and 0xff); o.write(v shr 24 and 0xff) }
            fun le16(v: Int) { o.write(v and 0xff); o.write(v shr 8 and 0xff) }
            o.writeBytes("RIFF"); le32(36 + pcm.size * 2); o.writeBytes("WAVEfmt "); le32(16); le16(1); le16(2)
            le32(IntroSynth.SR); le32(IntroSynth.SR * 4); le16(4); le16(16); o.writeBytes("data"); le32(pcm.size * 2)
            for (s in pcm) le16(s.toInt())
        }
    }
}
