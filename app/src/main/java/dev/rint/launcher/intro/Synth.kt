package dev.rint.launcher.intro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * The RintOS intro soundtrack, synthesized live (no audio files, no licensing).
 * 112 BPM, A minor, i–VI–III–VII. The arrangement follows [mode] so visuals and music
 * stay locked together: TIMELINE plays the scripted 24 bars, then CHILL loops while the
 * user personalizes, RISER builds for 2 bars, DROP loops until START is pressed.
 */
class IntroSynth {
    enum class Mode { TIMELINE, CHILL, RISER, DROP, STOP }

    companion object {
        const val SR = 32000
        const val BPM = 112.0
        const val BEAT = 60.0 / BPM
        const val BAR = BEAT * 4
        const val TIMELINE_BARS = 24
        val TIMELINE_SECONDS = TIMELINE_BARS * BAR
    }

    @Volatile var mode = Mode.TIMELINE
    @Volatile var muted = false
    @Volatile private var running = false
    @Volatile var modeStartSample = 0L
        private set
    @Volatile var written = 0L
        private set
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    // chord per bar: Am, F, C, G
    private val chords = arrayOf(
        doubleArrayOf(57.0, 60.0, 64.0), doubleArrayOf(53.0, 57.0, 60.0),
        doubleArrayOf(60.0, 64.0, 67.0), doubleArrayOf(55.0, 59.0, 62.0),
    )
    private val roots = doubleArrayOf(45.0, 41.0, 48.0, 43.0)
    private fun hz(m: Double) = 440.0 * 2.0.pow((m - 69) / 12)

    private val padPh = DoubleArray(6)
    private var arpPh = 0.0
    private var bassPh = 0.0
    private var padLp = 0.0
    private var arpLp = 0.0
    private var hatHp = 0.0
    private var riserLp = 0.0
    private val rnd = Random(42)

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min * 2, SR / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        t.play()
        thread = Thread({ loop(t) }, "rint-synth").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    /** Seconds since the start as heard by the listener (follows the playback head). */
    fun seconds(): Double = track?.let { it.playbackHeadPosition.toDouble() / SR } ?: 0.0

    fun secondsInMode(): Double = seconds() - modeStartSample.toDouble() / SR

    fun switch(m: Mode) {
        // quantize mode changes to the next bar so transitions land on the beat
        val barSamples = (BAR * SR).toLong()
        val next = ((written / barSamples) + 1) * barSamples
        pendingMode = m
        pendingAt = next
    }

    @Volatile private var pendingMode: Mode? = null
    @Volatile private var pendingAt = 0L

    /** Tape-stop: slows the track to a halt, then releases. */
    fun tapeStop() {
        val t = track ?: return
        Thread {
            var rate = SR.toFloat()
            while (rate > 1500f) {
                rate *= 0.92f
                runCatching { t.playbackRate = rate.toInt() }
                Thread.sleep(16)
            }
            release()
        }.start()
    }

    fun release() {
        running = false
        runCatching { thread?.join(200) }
        runCatching { track?.stop(); track?.release() }
        track = null
    }

    private fun loop(t: AudioTrack) {
        val chunk = SR / 50
        val buf = ShortArray(chunk)
        while (running) {
            for (i in 0 until chunk) {
                val n = written + i
                pendingMode?.let { if (n >= pendingAt) { mode = it; modeStartSample = n; pendingMode = null } }
                val s = if (muted || mode == Mode.STOP) 0.0 else sample(n)
                buf[i] = (tanh(s * 1.1) * 0.82 * Short.MAX_VALUE).toInt().toShort()
            }
            written += chunk
            if (t.write(buf, 0, chunk) < 0) break
        }
    }

    private fun sample(n: Long): Double {
        val time = n.toDouble() / SR
        val beatPos = time / BEAT
        val bar = floor(beatPos / 4).toInt()
        val inBeat = beatPos - floor(beatPos)
        val sixteenth = floor(beatPos * 4).toInt()
        val inSix = beatPos * 4 - sixteenth
        val beatInBar = floor(beatPos).toInt() % 4

        // arrangement
        var pad = 1.0; var arp = 0.0; var drums = 0.0; var bassOn = 0.0; var snare = 0.0; var riser = 0.0
        var cutoff = 0.06
        when (mode) {
            Mode.TIMELINE -> {
                val b = bar
                cutoff = 0.03 + 0.09 * (b.coerceAtMost(8) / 8.0)
                arp = if (b >= 4) 1.0 else 0.0
                drums = if (b in 8..19) 1.0 else 0.0
                bassOn = if (b in 8..19) 1.0 else 0.35
                snare = if (b in 16..19) 1.0 else 0.0
                if (b == 19) riser = (beatPos / 4 - 19)
                if (b >= 20) { arp = 0.7; cutoff = 0.05 }
            }
            Mode.CHILL -> { arp = 0.55; drums = 0.35; bassOn = 0.6; cutoff = 0.06 }
            Mode.RISER -> {
                val p = ((n - modeStartSample).toDouble() / SR / (BAR * 2)).coerceIn(0.0, 1.0)
                riser = p; arp = 1.0; drums = if (p < 0.5) 0.6 else 0.0; bassOn = 0.4; cutoff = 0.05 + 0.2 * p; pad = 1.0 - 0.5 * p
            }
            Mode.DROP -> { arp = 1.0; drums = 1.0; bassOn = 1.0; snare = 1.0; cutoff = 0.14 }
            Mode.STOP -> return 0.0
        }

        val chord = chords[((bar % 4) + 4) % 4]
        var out = 0.0

        // pad: 3 notes x 2 detuned saws, soft-filtered
        var p = 0.0
        for (v in 0 until 3) for (d in 0 until 2) {
            val idx = v * 2 + d
            val f = hz(chord[v]) * (if (d == 0) 0.997 else 1.003)
            padPh[idx] = (padPh[idx] + f / SR) % 1.0
            p += padPh[idx] * 2 - 1
        }
        padLp += cutoff * (p / 6 - padLp)
        out += padLp * 0.55 * pad

        // arp: 16ths over chord tones, two octaves
        if (arp > 0) {
            val seq = intArrayOf(0, 1, 2, 1, 0, 2, 1, 2)
            val note = chord[seq[sixteenth % 8]] + 12 + if ((sixteenth / 8) % 2 == 1) 12 else 0
            arpPh = (arpPh + hz(note) / SR) % 1.0
            val sq = if (arpPh < 0.5) 1.0 else -1.0
            val env = exp(-inSix * 5.5)
            arpLp += 0.18 * (sq * env - arpLp)
            out += arpLp * 0.16 * arp
        }

        // bass: root, pumping
        if (bassOn > 0) {
            val r = roots[((bar % 4) + 4) % 4]
            bassPh = (bassPh + hz(r) / SR) % 1.0
            val tri = 1 - 4 * kotlin.math.abs(bassPh - 0.5)
            val pump = if (drums > 0) (0.35 + 0.65 * (1 - exp(-inBeat * 7))) else 1.0
            out += tri * 0.32 * bassOn * pump
        }

        // kick on every beat
        if (drums > 0) {
            val kt = inBeat * BEAT
            val kickPhase = 45 * kt + 110.0 / 28 * (1 - exp(-kt * 28))
            out += sin(2 * PI * kickPhase) * exp(-kt * 9) * 0.9 * drums
            // hats on off-8ths
            val eighth = beatPos * 2 - floor(beatPos * 2)
            if (floor(beatPos * 2).toInt() % 2 == 1) {
                val noise = rnd.nextDouble() * 2 - 1
                val hp = noise - hatHp
                hatHp = noise
                out += hp * exp(-eighth * BEAT / 2 * 90) * 0.12 * drums
            }
        }
        if (snare > 0 && (beatInBar == 1 || beatInBar == 3)) {
            val st = inBeat * BEAT
            out += ((rnd.nextDouble() * 2 - 1) * 0.5 + sin(2 * PI * 185 * st) * 0.4) * exp(-st * 16) * 0.5 * snare
        }
        if (riser > 0) {
            val noise = rnd.nextDouble() * 2 - 1
            riserLp += (0.02 + 0.5 * riser) * (noise - riserLp)
            out += riserLp * 0.35 * riser * riser
        }
        return out
    }
}
