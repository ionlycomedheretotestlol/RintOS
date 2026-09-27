package dev.rint.launcher.intro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The film's bar map for RintOS 1.4. The picture reads this too, so music and visuals can never
 * drift apart. 120 BPM, one bar = 2 s, 30 bars = one minute.
 */
object Score {
    const val BPM = 120.0
    const val BEAT = 60.0 / BPM
    const val BAR = BEAT * 4
    const val CHIP = 2        // 8-bit groove: everything you already love
    const val UPGRADE = 6     // riser: the 8-bit world melts into full sound
    const val NEW = 8         // drop 1: eight new features, one per bar
    const val BREAK = 16      // half-time: the 3D "1.4" assembles
    const val DROP2 = 20      // drop 2: both worlds at once, Rin's new animations
    const val OUTRO = 26      // the big 1.4, credits
    const val END = 30
    const val TIMELINE_SECONDS = END * BAR

    enum class Part { COLD, CHIP, UPGRADE, NEW, BREAK, DROP2, OUTRO, AFTER }

    fun part(bar: Int) = when {
        bar < CHIP -> Part.COLD
        bar < UPGRADE -> Part.CHIP
        bar < NEW -> Part.UPGRADE
        bar < BREAK -> Part.NEW
        bar < DROP2 -> Part.BREAK
        bar < OUTRO -> Part.DROP2
        bar < END -> Part.OUTRO
        else -> Part.AFTER
    }

    /** Whether the kick is playing in [bar] (the picture pulses with it). */
    fun kick(bar: Int) = bar in CHIP until UPGRADE + 1 || bar in NEW until BREAK || bar in BREAK + 2 until OUTRO

    /** Bars where something huge hits on the downbeat. */
    val impacts = intArrayOf(NEW, DROP2, OUTRO)
}

/**
 * "Upgrade", the RintOS 1.4 theme, synthesized live (no audio files). It starts as an 8-bit
 * chiptune (pulse lead, triangle bass, noise drums), then gets bit-crushed, melts, and drops into
 * full sound: funk bass, electric piano stabs, a marimba hook, strings, a house kit and shaker.
 * B minor / D major, Bm–G–D–A.
 *
 * [mode] follows the intro: TIMELINE plays the [Score], CHILL loops while the user personalizes,
 * RISER builds for 2 bars, DROP loops until START, STOP is silence.
 */
class IntroSynth {
    enum class Mode { TIMELINE, CHILL, RISER, DROP, STOP }

    companion object {
        const val SR = 32000
        const val BEAT = Score.BEAT
        const val BAR = Score.BAR
        const val TIMELINE_SECONDS = Score.TIMELINE_SECONDS
        private val FREQ = DoubleArray(128) { 440.0 * 2.0.pow((it - 69) / 12.0) }
        private const val TAU = 2 * PI

        private val CHORDS = arrayOf(intArrayOf(59, 62, 66), intArrayOf(59, 62, 67), intArrayOf(57, 62, 66), intArrayOf(57, 61, 64))
        private val ROOTS = intArrayOf(47, 43, 50, 45)
        private val HOOK = arrayOf(
            intArrayOf(78, -1, 76, 74, 76, -1, 71, 74),
            intArrayOf(74, -1, 79, 78, 76, -1, 74, 71),
            intArrayOf(73, 74, 76, 78, -1, 81, 78, 76),
            intArrayOf(76, -1, 73, -1, 71, 73, 76, -1),
        )
        /** Funk bass: 16th-note pattern (semitones above the root, -1 = rest, 12 = octave pop). */
        private val BASS = intArrayOf(0, -1, 12, 0, -1, 0, 7, -1, 0, -1, 12, -1, 10, 0, -1, 7)
        /** Electric piano stabs on the 16th grid. */
        private val STABS = intArrayOf(2, 6, 10, 13)
        /** The boot chime: an 8-bit arpeggio up the D major chord, then the hook's first notes. */
        private val CHIME = intArrayOf(74, 78, 81, 86, 90, 86)
    }

    @Volatile var mode = Mode.TIMELINE
        private set
    @Volatile var muted = false
    @Volatile private var running = false
    @Volatile var modeStartSample = 0L
        private set
    @Volatile var written = 0L
        private set
    @Volatile private var pendingMode: Mode? = null
    @Volatile private var pendingAt = 0L
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun start() {
        if (running) return
        val min = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(SR).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(maxOf(min * 2, SR / 5 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        t.play()
        thread = Thread({ loop(t) }, "rint-synth").apply { priority = Thread.MAX_PRIORITY; isDaemon = true; start() }
    }

    /** Pausing the track also freezes the intro timeline (it follows the playback head). */
    fun pause() {
        runCatching { track?.pause() }
    }

    fun resume() {
        runCatching { track?.play() }
    }

    /** Seconds since the start as heard by the listener (follows the playback head). */
    fun seconds(): Double = track?.let { runCatching { it.playbackHeadPosition.toDouble() / SR }.getOrDefault(0.0) } ?: 0.0

    fun secondsInMode(): Double = seconds() - modeStartSample.toDouble() / SR

    /** Mode changes are quantized to the next bar so every transition lands on the one. */
    fun switch(m: Mode) {
        val barSamples = (BAR * SR).toLong()
        pendingAt = ((written / barSamples) + 1) * barSamples
        pendingMode = m
    }

    /** Tape-stop: slows the track to a halt, then releases. */
    fun tapeStop() {
        val t = track ?: return
        Thread {
            var rate = SR.toFloat()
            while (rate > 1500f && running) {
                rate *= 0.92f
                runCatching { t.playbackRate = rate.toInt() }
                Thread.sleep(16)
            }
            release()
        }.apply { isDaemon = true }.start()
    }

    fun release() {
        running = false
        runCatching { if (Thread.currentThread() != thread) thread?.join(200) }
        runCatching { track?.stop(); track?.release() }
        track = null
    }

    private fun loop(t: AudioTrack) {
        val frames = SR / 50
        val buf = ShortArray(frames * 2)
        while (running) {
            render(buf, frames)
            if (t.write(buf, 0, buf.size) < 0) break
        }
    }

    /** Renders [frames] stereo frames (interleaved) into [out]. Public to the module so tests can listen offline. */
    internal fun render(out: ShortArray, frames: Int) {
        for (i in 0 until frames) {
            val n = written + i
            pendingMode?.let { if (n >= pendingAt) { mode = it; modeStartSample = n; pendingMode = null } }
            if (muted || mode == Mode.STOP) { out[i * 2] = 0; out[i * 2 + 1] = 0; continue }
            frame(n)
            out[i * 2] = (tanh(outL * 1.1) * 0.88 * Short.MAX_VALUE).toInt().toShort()
            out[i * 2 + 1] = (tanh(outR * 1.1) * 0.88 * Short.MAX_VALUE).toInt().toShort()
        }
        written += frames
    }

    // ───────────── arrangement (levels, set per sample; no allocation) ─────────────
    // 8-bit world
    private var cLead = 0.0; private var cArp = 0.0; private var cBass = 0.0; private var cDrums = 0.0; private var cChime = 0.0
    // full-sound world
    private var hBass = 0.0; private var hKeys = 0.0; private var hMarimba = 0.0; private var hStrings = 0.0
    private var hKick = 0.0; private var hSnare = 0.0; private var hShaker = 0.0; private var hOpenHat = 0.0; private var hHey = 0.0
    private var halfTime = false
    // transitions
    private var chimeAt = 0.0
    private var crush = 0.0; private var riser = -1.0; private var fill = -1.0; private var swell = 0.0; private var lowCut = 0.0

    private fun reset() {
        cLead = 0.0; cArp = 0.0; cBass = 0.0; cDrums = 0.0; cChime = 0.0
        hBass = 0.0; hKeys = 0.0; hMarimba = 0.0; hStrings = 0.0; hKick = 0.0; hSnare = 0.0; hShaker = 0.0; hOpenHat = 0.0; hHey = 0.0
        halfTime = false; crush = 0.0; riser = -1.0; fill = -1.0; swell = 0.0; lowCut = 0.0
    }

    private fun full() {
        hBass = 1.0; hKeys = 1.0; hMarimba = 1.0; hStrings = 0.6; hKick = 1.0; hSnare = 1.0; hShaker = 1.0; hOpenHat = 1.0; hHey = 1.0
    }

    private fun chill() {
        hKeys = 0.7; hStrings = 0.8; hShaker = 0.6; hBass = 0.45; hMarimba = 0.35
    }

    private fun arrange(bar: Int, pb: Double, time: Double) {
        reset()
        fun prog(from: Int, bars: Int) = ((time - from * BAR) / (bars * BAR)).coerceIn(0.0, 1.0)
        when (mode) {
            Mode.TIMELINE -> when (Score.part(bar)) {
                Score.Part.COLD -> {
                    cChime = 1.0; chimeAt = 0.0
                    if (bar == 1) { cArp = 0.5 * pb; swell = pb.pow(4.0) * 0.5 }
                }
                Score.Part.CHIP -> {
                    cLead = if (bar >= Score.CHIP + 1) 1.0 else 0.0; cArp = 0.7; cBass = 1.0; cDrums = 1.0
                    if (bar == Score.UPGRADE - 1) fill = pb
                }
                Score.Part.UPGRADE -> {
                    // the 8-bit world melts: bit-crushed harder and harder while a riser climbs
                    val p = prog(Score.UPGRADE, 2)
                    cLead = 1.0; cArp = 1.0; cBass = 1.0 - p; cDrums = if (bar == Score.UPGRADE) 1.0 else 0.0
                    crush = p; riser = p; hStrings = 0.5 * p
                    if (bar == Score.NEW - 1) { fill = pb; if (pb > 0.85) { cLead = 0.0; cArp = 0.0 } }
                }
                Score.Part.NEW -> {
                    full()
                    if (bar < Score.NEW + 2) hStrings = 0.9
                    if (bar == Score.BREAK - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0 } }
                }
                Score.Part.BREAK -> {
                    val p = prog(Score.BREAK, 4)
                    halfTime = true
                    hKeys = 1.0; hStrings = 1.0; hMarimba = 0.7; hBass = 0.5; cLead = 0.35
                    hShaker = if (bar >= Score.BREAK + 2) 0.7 else 0.0
                    if (bar >= Score.BREAK + 2) { hKick = 0.8; hSnare = 0.8 }
                    lowCut = 1.0 - p
                    if (bar >= Score.BREAK + 2) riser = prog(Score.BREAK + 2, 2)
                    if (bar == Score.DROP2 - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0; hKeys = 0.0 } }
                }
                Score.Part.DROP2 -> {
                    full(); cLead = 0.7; cArp = 0.5; hStrings = 0.9
                    if (bar == Score.OUTRO - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0 } }
                }
                Score.Part.OUTRO -> {
                    val p = prog(Score.OUTRO, 4)
                    if (bar < Score.OUTRO + 2) { full(); hStrings = 1.0 } else {
                        hKeys = 1.0 - p; hStrings = 1.0 - p * 0.6; hMarimba = 0.6 * (1 - p)
                        if (bar == Score.END - 1) { cChime = 1.0; chimeAt = (Score.END - 1) * BAR }
                    }
                }
                Score.Part.AFTER -> chill()
            }
            Mode.CHILL -> chill()
            Mode.RISER -> {
                val p = (time / (2 * BAR)).coerceIn(0.0, 1.0)
                hKeys = 1.0 - 0.5 * p; hStrings = 0.8; cArp = 0.6 * p; riser = p; fill = p; hShaker = 0.6
                hKick = if (p < 0.5) 0.7 else 0.0; lowCut = p * 0.6
            }
            Mode.DROP -> { full(); cLead = 0.6; cArp = 0.4 }
            Mode.STOP -> Unit
        }
    }

    // ───────────── voices state ─────────────
    private var seed = 0x7A3B9D1
    private fun noise(): Double {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return seed / 2147483648.0
    }
    private var lfsr = 1   // NES-style noise
    private fun nesNoise(): Double {
        val bit = (lfsr xor (lfsr shr 1)) and 1
        lfsr = (lfsr shr 1) or (bit shl 14)
        return if (lfsr and 1 == 1) 1.0 else -1.0
    }

    private var cLeadPh = 0.0; private var cArpPh = 0.0; private var cBassPh = 0.0; private var chimePh = 0.0
    private var bassPh = 0.0; private var bassLp1 = 0.0; private var bassLp2 = 0.0; private var bassBand = 0.0
    private val stringPh = DoubleArray(6); private var strLpL = 0.0; private var strLpR = 0.0
    private var hatLp = 0.0; private var snLp = 0.0; private var riserLp = 0.0; private var riserPh = 0.0; private var swellLp = 0.0
    private var impLp = 0.0; private var heyPh = 0.0; private val heyLow = DoubleArray(2); private val heyBand = DoubleArray(2)
    private var crushHoldL = 0.0; private var crushHoldR = 0.0; private var crushCount = 0
    private var hpL = 0.0; private var hpR = 0.0

    private val dLen = (BEAT * 0.75 * SR).toInt()
    private val delayL = DoubleArray(dLen); private val delayR = DoubleArray(dLen)
    private var dIdx = 0; private var dFb = 0.0
    private val combLen = intArrayOf(1116, 1188, 1277, 1356, 1139, 1211, 1300, 1379)
    private val combs = Array(8) { DoubleArray(combLen[it]) }
    private val combIdx = IntArray(8); private val combFilt = DoubleArray(8)
    private val apLen = intArrayOf(556, 441, 579, 464)
    private val aps = Array(4) { DoubleArray(apLen[it]) }
    private val apIdx = IntArray(4)

    private var outL = 0.0
    private var outR = 0.0

    private fun saw(ph: Double) = ph * 2 - 1
    private fun pulse(ph: Double, duty: Double) = if (ph < duty) 1.0 else -1.0
    /** 4-bit style triangle, like the NES. */
    private fun tri4(ph: Double) = floor((1 - 4 * abs(ph - 0.5)) * 8) / 8

    private fun sinceAny(bars: IntArray, time: Double): Double {
        var best = -1.0
        for (b in bars) { val d = time - b * BAR; if (d >= 0 && (best < 0 || d < best)) best = d }
        return best
    }

    private fun frame(n: Long) {
        val time = (n - modeStartSample).toDouble() / SR
        val beatPos = time / BEAT
        val beatI = floor(beatPos).toInt()
        val inBeat = beatPos - beatI
        val kt = inBeat * BEAT
        val bar = beatI / 4
        val beatInBar = beatI % 4
        val pb = (beatPos / 4) - bar
        val six = floor(beatPos * 4).toInt()
        val inSix = beatPos * 4 - six
        val sixInBar = six % 16
        val eighth = floor(beatPos * 2).toInt()
        val inEighth = beatPos * 2 - eighth
        arrange(bar, pb, time)

        val ci = ((bar % 4) + 4) % 4
        val chord = CHORDS[ci]
        val root = ROOTS[ci]

        var l = 0.0; var r = 0.0
        var sendRev = 0.0; var sendDly = 0.0

        // kick timing: half-time puts the snare on 3 only
        val kickOn = hKick > 0
        val duck = if (kickOn) 0.35 + 0.65 * (1 - exp(-kt * 10)) else 1.0

        // ════════════ the 8-bit world ════════════
        var chipL = 0.0; var chipR = 0.0

        // boot chime: a fast pulse arpeggio, then a held note
        if (cChime > 0) {
            val tc = (time - chimeAt).coerceAtLeast(0.0)
            val step = (tc * 12).toInt()
            val note = CHIME[step.coerceAtMost(CHIME.size - 1)]
            val st = if (step < CHIME.size - 1) (tc * 12 - step) / 12 else tc - (CHIME.size - 1) / 12.0
            chimePh = (chimePh + FREQ[note] / SR) % 1.0
            val env = if (step < CHIME.size - 1) 1.0 else exp(-st * 1.4)
            val g = pulse(chimePh, 0.25) * 0.14 * env * cChime
            chipL += g; chipR += g; sendRev += g * 0.4; sendDly += g * 0.4
        }

        // pulse lead: the hook, 12.5% duty with a little vibrato
        if (cLead > 0) {
            val step = eighth % 8
            val row = HOOK[ci]
            var idx = step
            var st = inEighth * BEAT / 2
            if (row[idx] < 0 && idx > 0) { idx -= 1; st += BEAT / 2 }
            val note = row[idx]
            if (note >= 0) {
                val vib = 1 + 0.006 * sin(TAU * 6 * time) * (st * 3).coerceAtMost(1.0)
                cLeadPh = (cLeadPh + FREQ[note] * vib / SR) % 1.0
                val env = (0.8 + 0.2 * exp(-st * 8)) * (if (st < BEAT * 0.95) 1.0 else 0.0)
                val g = pulse(cLeadPh, 0.125 + 0.125 * (bar % 2)) * 0.075 * cLead * env
                chipL += g * 0.9; chipR += g * 1.1; sendDly += g * 0.35
            }
        }

        // chip arps: 32nd-note chord arpeggio, the classic sound
        if (cArp > 0) {
            val k = floor(beatPos * 8).toInt() % 3
            cArpPh = (cArpPh + FREQ[chord[k] + 12] / SR) % 1.0
            val g = pulse(cArpPh, 0.5) * 0.045 * cArp
            chipL += g * 1.2; chipR += g * 0.8
        }

        // triangle bass: root on 8ths with octave jumps
        if (cBass > 0) {
            val note = root - 12 + (if (eighth % 2 == 1) 12 else 0)
            cBassPh = (cBassPh + FREQ[note] / SR) % 1.0
            val g = tri4(cBassPh) * 0.22 * cBass * (if (inEighth < 0.85) 1.0 else 0.0)
            chipL += g; chipR += g
        }

        // noise drums: kick = pitch sweep, snare = noise burst, hats = short ticks
        if (cDrums > 0) {
            if (beatInBar == 0 || beatInBar == 2) {
                val ph = 55 * kt + (220 - 55) * (1 - exp(-kt * 40)) / 40
                chipL += pulse(ph - floor(ph), 0.5) * exp(-kt * 14) * 0.2; chipR += pulse(ph - floor(ph), 0.5) * exp(-kt * 14) * 0.2
            }
            if (beatInBar == 1 || beatInBar == 3) { val s = nesNoise() * exp(-kt * 16) * 0.16; chipL += s; chipR += s }
            if (six % 2 == 1) { val s = nesNoise() * exp(-inSix * BEAT / 4 * 120) * 0.05; chipL += s * 0.7; chipR += s * 1.3 }
        }

        // bit-crusher: during the upgrade the 8-bit world literally degrades
        if (crush > 0) {
            val hold = 1 + (crush * 24).toInt()
            if (crushCount++ % hold == 0) { crushHoldL = chipL; crushHoldR = chipR }
            val bits = 16.0 - crush * 13
            val q = 2.0.pow(bits)
            chipL = floor(crushHoldL * q) / q; chipR = floor(crushHoldR * q) / q
        }
        l += chipL; r += chipR
        sendRev += (chipL + chipR) * 0.1

        // ════════════ the full-sound world ════════════

        // strings: six detuned saws, slow and warm
        if (hStrings > 0) {
            var sl = 0.0; var sr = 0.0
            for (v in 0 until 3) {
                val f = FREQ[chord[v]] * (1 + 0.003 * sin(TAU * (5.2 + v * 0.4) * time))
                stringPh[v * 2] = (stringPh[v * 2] + f * 0.997 / SR) % 1.0
                stringPh[v * 2 + 1] = (stringPh[v * 2 + 1] + f * 1.003 / SR) % 1.0
                sl += saw(stringPh[v * 2]); sr += saw(stringPh[v * 2 + 1])
            }
            strLpL += 0.045 * (sl / 3 - strLpL); strLpR += 0.045 * (sr / 3 - strLpR)
            val g = 0.2 * hStrings * (0.6 + 0.4 * duck)
            l += strLpL * g; r += strLpR * g
            sendRev += (strLpL + strLpR) * g * 0.6
        }

        // electric piano: FM chord stabs on the off-16ths (house style), or held in the break
        if (hKeys > 0) {
            var hit = -1
            if (halfTime) hit = if (sixInBar >= 8) 8 else 0
            else for (h in STABS) if (h <= sixInBar) hit = h
            if (hit >= 0) {
                val st = (sixInBar - hit + inSix) * BEAT / 4
                val len = if (halfTime) BEAT * 2 else BEAT * 0.4
                val gate = if (st < len) 1.0 else exp(-(st - len) * 30)
                var s = 0.0
                for (v in 0 until 3) {
                    val f = FREQ[chord[v] + 12]
                    val idx = 1.6 * exp(-st * 6)
                    s += sin(TAU * f * st + idx * sin(TAU * f * st))
                }
                val env = (st / 0.003).coerceAtMost(1.0) * exp(-st * (if (halfTime) 1.2 else 3.5)) * gate
                val g = s / 3 * 0.2 * hKeys * env * (0.7 + 0.3 * duck)
                l += g * 1.1; r += g * 0.9
                sendRev += g * 0.4; sendDly += g * 0.2
            }
        }

        // funk bass: plucked saw through an envelope filter, syncopated 16ths, ducked
        if (hBass > 0) {
            val step = BASS[sixInBar]
            val useStep = if (step < 0) -1 else step
            if (useStep >= 0 && !(halfTime && sixInBar % 4 != 0)) {
                val st = inSix * BEAT / 4
                bassPh = (bassPh + FREQ[root - 12 + useStep] / SR) % 1.0
                val env = exp(-st * 14)
                val cut = 0.02 + 0.3 * env
                val x = saw(bassPh)
                bassLp1 += cut * (x - bassLp1); bassLp2 += cut * (bassLp1 - bassLp2)
                val sub = sin(TAU * bassPh) * 0.6
                val g = (tanh(bassLp2 * 2.2) * 0.5 + sub * 0.5) * 0.42 * hBass * (0.5 + 0.5 * env) * duck
                l += g; r += g
            } else { bassLp1 *= 0.99; bassLp2 *= 0.99 }
        }

        // marimba hook: FM, bright and woody, answering the chip lead
        if (hMarimba > 0) {
            val step = eighth % 8
            val row = HOOK[ci]
            val note = row[step]
            if (note >= 0) {
                val st = inEighth * BEAT / 2
                val f = FREQ[note]
                val m = sin(TAU * f * st + 2.5 * exp(-st * 30) * sin(TAU * f * 4 * st))
                val env = (st / 0.002).coerceAtMost(1.0) * exp(-st * 9)
                val g = m * 0.2 * hMarimba * env
                val pan = if (step % 2 == 0) 0.25 else -0.25
                l += g * (1 - pan); r += g * (1 + pan)
                sendDly += g * 0.45; sendRev += g * 0.3
            }
        }

        // "hey!": a formant-filtered shout on beat 4 of every other bar
        if (hHey > 0 && bar % 2 == 1 && beatInBar == 3 && kt < 0.25) {
            heyPh = (heyPh + 180.0 / SR) % 1.0
            val x = saw(heyPh) + noise() * 0.3
            var y = 0.0
            for (f in 0 until 2) {
                val fc = if (f == 0) 700.0 else 1600.0
                val g = 2 * sin(PI * fc / SR)
                heyLow[f] += g * heyBand[f]
                val high = x - heyLow[f] - 0.2 * heyBand[f]
                heyBand[f] += g * high
                y += heyBand[f]
            }
            val env = (kt / 0.01).coerceAtMost(1.0) * exp(-kt * 12)
            val g = y * 0.12 * env * hHey
            l += g; r += g; sendRev += g * 0.6; sendDly += g * 0.3
        }

        // house kick (four on the floor; half-time: 1 and 3)
        if (kickOn && (!halfTime || beatInBar == 0 || beatInBar == 2)) {
            val ph = 50 * kt + (140 - 50) * (1 - exp(-kt * 28)) / 28
            val k = tanh(sin(TAU * ph) * exp(-kt * 7) * 2.4) * 0.6 + noise() * exp(-kt * 500) * 0.1
            l += k * hKick; r += k * hKick
        }

        // snare: noise + body tone on 2 and 4 (half-time: on 3)
        val snareBeat = if (halfTime) beatInBar == 2 else (beatInBar == 1 || beatInBar == 3)
        if (hSnare > 0 && snareBeat) {
            val nz = noise()
            snLp += 0.4 * (nz - snLp)
            val body = sin(TAU * 185 * kt) * exp(-kt * 22) * 0.4
            val s = ((nz - snLp) * exp(-kt * 14) * 0.5 + body) * 0.55 * hSnare
            l += s; r += s; sendRev += s * 0.35
        }

        // shaker: 16ths with swing accents; open hat on the offbeats
        if (hShaker > 0) {
            val nz = noise()
            hatLp += 0.6 * (nz - hatLp)
            val hp = nz - hatLp
            val acc = if (six % 2 == 1) 1.0 else 0.55
            var g = hp * exp(-inSix * BEAT / 4 * 55) * 0.08 * acc * hShaker
            if (hOpenHat > 0 && six % 4 == 2) g += hp * exp(-inSix * BEAT / 4 * 9) * 0.07 * hOpenHat
            l += g * 0.8; r += g * 1.2
        }

        // drum fill: accelerating snare into the next section
        if (fill >= 0) {
            val p = fill.coerceIn(0.0, 1.0)
            val grid = when { p < 0.5 -> BEAT / 2; p < 0.75 -> BEAT / 4; else -> BEAT / 8 }
            val st = time % grid
            val s = (noise() * 0.6 + sin(TAU * 200 * st) * 0.5) * exp(-st * 26) * (0.25 + 0.75 * p) * 0.45
            l += s; r += s; sendRev += s * 0.3
        }

        // riser: filtered noise + a climbing tone
        if (riser >= 0) {
            val p = riser.coerceIn(0.0, 1.0)
            riserLp += (0.01 + 0.45 * p * p) * (noise() - riserLp)
            riserPh = (riserPh + (200 + 1800 * p * p) / SR) % 1.0
            val s = riserLp * 0.28 * p * p + saw(riserPh) * 0.03 * p
            l += s * (1 - 0.3 * sin(TAU * time * 2)); r += s * (1 + 0.3 * sin(TAU * time * 2))
            sendRev += s * 0.4
        }

        if (swell > 0) {
            swellLp += (0.02 + 0.3 * swell) * (noise() - swellLp)
            val s = swellLp * 0.4 * swell
            l += s; r += s; sendRev += s * 0.5
        }

        // impacts: a deep boom and a burst of air on each drop
        val tImp = if (mode == Mode.TIMELINE) sinceAny(Score.impacts, time) else if (mode == Mode.DROP && time < 3) time else -1.0
        if (tImp in 0.0..3.0) {
            val boom = sin(TAU * (32 * tImp + (110 - 32) * (1 - exp(-tImp * 8)) / 8)) * exp(-tImp * 2.0) * 0.7
            impLp += 0.2 * (noise() - impLp)
            val air = impLp * exp(-tImp * 7) * 0.5
            l += boom + air; r += boom + air; sendRev += boom * 0.3 + air
        }

        // the break opens up slowly: a high-pass that sweeps down (thin → full)
        if (lowCut > 0) {
            val c = 0.002 + 0.08 * lowCut
            hpL += c * (l - hpL); hpR += c * (r - hpR)
            l -= hpL * lowCut; r -= hpR * lowCut
        }

        // ping-pong delay (dotted 8th)
        val dl = delayL[dIdx]; val dr = delayR[dIdx]
        dFb += 0.35 * (dr - dFb)
        delayL[dIdx] = sendDly + dFb * 0.42
        delayR[dIdx] = dl
        dIdx = (dIdx + 1) % dLen
        l += dl * 0.55; r += dr * 0.55
        sendRev += (dl + dr) * 0.15

        // reverb: 4 combs + 2 allpasses per side
        val input = sendRev * 0.03
        var rl = 0.0; var rr = 0.0
        for (c in 0 until 8) {
            val buf = combs[c]
            val y = buf[combIdx[c]]
            combFilt[c] = y * 0.6 + combFilt[c] * 0.4
            buf[combIdx[c]] = input + combFilt[c] * 0.85
            combIdx[c] = (combIdx[c] + 1) % buf.size
            if (c < 4) rl += y else rr += y
        }
        for (a in 0 until 4) {
            val buf = aps[a]
            val x = if (a < 2) rl else rr
            val b = buf[apIdx[a]]
            buf[apIdx[a]] = x + b * 0.5
            val y = b - x
            apIdx[a] = (apIdx[a] + 1) % buf.size
            if (a < 2) rl = y else rr = y
        }
        outL = l + rl * 0.85
        outR = r + rr * 0.85
    }
}
