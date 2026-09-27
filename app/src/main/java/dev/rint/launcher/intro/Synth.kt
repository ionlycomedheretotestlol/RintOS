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
 * "Rin's Upgrade Day": the bar map of the RintOS 1.4 film. The picture reads this too, so music,
 * sound effects and visuals can never drift apart. 120 BPM, one bar = 2 s, 46 bars ≈ 92 s.
 */
object Score {
    const val BPM = 120.0
    const val BEAT = 60.0 / BPM
    const val BAR = BEAT * 4
    const val WAKE = 0         // night in the 8-bit world; Rin sleeps, dawn, he wakes
    const val ADVENTURE = 2    // side-scroller: Rin walks and headbutts a ? block every bar
    const val POWERUP = 8      // the giant 1.4 block, the orb, the power-up
    const val UPGRADE = 10     // the game glitches, shatters, and rebuilds in 3D
    const val HD = 12          // the drop: eight new things, two bars each
    const val STARS = 28       // breakdown: Rin under the stars
    const val SHOW = 32        // drop 2: Rin's concert
    const val FINALE = 40      // the 3D 1.4 and fireworks
    const val CREDITS = 44
    const val END = 46
    const val TIMELINE_SECONDS = END * BAR

    enum class Part { WAKE, ADVENTURE, POWERUP, UPGRADE, HD, STARS, SHOW, FINALE, CREDITS, AFTER }

    fun part(bar: Int) = when {
        bar < ADVENTURE -> Part.WAKE
        bar < POWERUP -> Part.ADVENTURE
        bar < UPGRADE -> Part.POWERUP
        bar < HD -> Part.UPGRADE
        bar < STARS -> Part.HD
        bar < SHOW -> Part.STARS
        bar < FINALE -> Part.SHOW
        bar < CREDITS -> Part.FINALE
        bar < END -> Part.CREDITS
        else -> Part.AFTER
    }

    /** Whether the kick is playing in [bar] (the picture pulses with it). */
    fun kick(bar: Int) = bar in HD until STARS || bar in STARS + 2 until FINALE + 4

    /** Bars where something huge hits on the downbeat. */
    val impacts = intArrayOf(HD, SHOW, FINALE)

    /** When Rin's head hits each ? block (seconds): six small ones, then the big 1.4 block. */
    val hits = DoubleArray(7) { ((ADVENTURE + it) * 4 + 2) * BEAT }
    const val JUMP = 0.25   // seconds from takeoff to the hit (and from the hit to landing)

    /** When the orb lands on Rin and the power-up starts. */
    const val GRAB = POWERUP * 4.0 * BEAT + BAR
    /** When the game screen shatters. */
    const val SHATTER = UPGRADE * BAR + BEAT * 2

    /** Fireworks: rockets burst on beats 1 and 3 of every finale bar. */
    val fireworks = DoubleArray(8) { FINALE * BAR + it * BEAT * 2 }

    /** A whoosh at every feature change in the drop. */
    val whooshes = DoubleArray(7) { (HD + 2 + it * 2) * BAR }
}

/**
 * The RintOS 1.4 soundtrack, synthesized live (no audio files). Chiptune for the 8-bit world
 * (pulse lead, 32nd arps, triangle bass, NES noise drums, jump/coin/power-up effects), then full
 * sound for the upgrade: funk bass, electric piano, marimba, strings, a house kit, shaker, "hey!"s,
 * whooshes, crowd cheers, a key change and fireworks. B minor / D major, Bm–G–D–A.
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
        private val FREQ = DoubleArray(140) { 440.0 * 2.0.pow((it - 69) / 12.0) }
        private const val TAU = 2 * PI

        private val CHORDS = arrayOf(intArrayOf(59, 62, 66), intArrayOf(59, 62, 67), intArrayOf(57, 62, 66), intArrayOf(57, 61, 64))
        private val ROOTS = intArrayOf(47, 43, 50, 45)
        private val HOOK = arrayOf(
            intArrayOf(78, -1, 76, 74, 76, -1, 71, 74),
            intArrayOf(74, -1, 79, 78, 76, -1, 74, 71),
            intArrayOf(73, 74, 76, 78, -1, 81, 78, 76),
            intArrayOf(76, -1, 73, -1, 71, 73, 76, -1),
        )
        private val BASS = intArrayOf(0, -1, 12, 0, -1, 0, 7, -1, 0, -1, 12, -1, 10, 0, -1, 7)
        private val STABS = intArrayOf(2, 6, 10, 13)
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

    fun pause() { runCatching { track?.pause() } }
    fun resume() { runCatching { track?.play() } }

    /** Seconds since the start as heard by the listener (follows the playback head). */
    fun seconds(): Double = track?.let { runCatching { it.playbackHeadPosition.toDouble() / SR }.getOrDefault(0.0) } ?: 0.0

    fun secondsInMode(): Double = seconds() - modeStartSample.toDouble() / SR

    /** Mode changes are quantized to the next bar so every transition lands on the one. */
    fun switch(m: Mode) {
        val barSamples = (BAR * SR).toLong()
        pendingAt = ((written / barSamples) + 1) * barSamples
        pendingMode = m
    }

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

    // ───────────── arrangement ─────────────
    private var cLead = 0.0; private var cArp = 0.0; private var cBass = 0.0; private var cDrums = 0.0; private var cChime = 0.0
    private var cSlow = false
    private var hBass = 0.0; private var hKeys = 0.0; private var hMarimba = 0.0; private var hStrings = 0.0
    private var hKick = 0.0; private var hSnare = 0.0; private var hShaker = 0.0; private var hOpenHat = 0.0; private var hHey = 0.0
    private var halfTime = false
    private var chimeAt = 0.0; private var tr = 0
    private var crush = 0.0; private var stutter = false; private var riser = -1.0; private var fill = -1.0; private var swell = 0.0; private var lowCut = 0.0
    private var jingle = false

    private fun reset() {
        cLead = 0.0; cArp = 0.0; cBass = 0.0; cDrums = 0.0; cChime = 0.0; cSlow = false
        hBass = 0.0; hKeys = 0.0; hMarimba = 0.0; hStrings = 0.0; hKick = 0.0; hSnare = 0.0; hShaker = 0.0; hOpenHat = 0.0; hHey = 0.0
        halfTime = false; crush = 0.0; stutter = false; riser = -1.0; fill = -1.0; swell = 0.0; lowCut = 0.0; tr = 0; jingle = false
    }

    private fun full() {
        hBass = 1.0; hKeys = 1.0; hMarimba = 1.0; hStrings = 0.6; hKick = 1.0; hSnare = 1.0; hShaker = 1.0; hOpenHat = 1.0; hHey = 1.0
    }

    private fun chip() { cLead = 1.0; cArp = 0.7; cBass = 1.0; cDrums = 1.0 }

    private fun chill() { hKeys = 0.7; hStrings = 0.8; hShaker = 0.6; hBass = 0.45; hMarimba = 0.35 }

    private fun arrange(bar: Int, pb: Double, time: Double) {
        reset()
        fun prog(from: Int, bars: Int) = ((time - from * BAR) / (bars * BAR)).coerceIn(0.0, 1.0)
        when (mode) {
            Mode.TIMELINE -> when (Score.part(bar)) {
                Score.Part.WAKE -> {
                    // a lullaby: the hook, slowly, on a soft pulse, with a sleepy arp
                    cChime = if (bar == 0) 1.0 else 0.0; chimeAt = 0.0
                    cLead = 0.55; cSlow = true; cArp = 0.35 + 0.35 * prog(0, 2)
                    if (bar == 1 && pb > 0.75) { swell = ((pb - 0.75) * 4).pow(2.0) * 0.4 }
                }
                Score.Part.ADVENTURE -> { chip(); if (bar == Score.ADVENTURE) cLead = 0.0 }
                Score.Part.POWERUP -> {
                    if (bar == Score.POWERUP) { if (pb < 0.5) chip() }   // the music stops dead when he hits the big block
                    else jingle = true
                }
                Score.Part.UPGRADE -> {
                    // the game glitches: bit-crushed, stuttering, then a riser into the drop
                    val p = prog(Score.UPGRADE, 2)
                    cLead = 1.0; cArp = 1.0; cBass = 1.0 - p; crush = 0.3 + 0.7 * p; stutter = true
                    riser = p; hStrings = 0.6 * p
                    if (bar == Score.HD - 1) { fill = pb; if (pb > 0.85) { cLead = 0.0; cArp = 0.0; cBass = 0.0 } }
                }
                Score.Part.HD -> {
                    full()
                    if (bar < Score.HD + 2) hStrings = 0.9
                    if (bar >= Score.HD + 8) { cLead = 0.35; hStrings = 0.8 }   // the 8-bit hook comes back, riding on top
                    if (bar == Score.STARS - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0 } }
                }
                Score.Part.STARS -> {
                    val p = prog(Score.STARS, 4)
                    halfTime = true
                    hKeys = 1.0; hStrings = 1.0; hMarimba = 0.5; hBass = 0.4; cLead = 0.3; cSlow = true
                    lowCut = 0.8 * (1.0 - p)
                    if (bar >= Score.STARS + 2) { hKick = 0.8; hSnare = 0.7; hShaker = 0.6; riser = prog(Score.STARS + 2, 2) }
                    if (bar == Score.SHOW - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0; hKeys = 0.0 } }
                }
                Score.Part.SHOW -> {
                    full(); cLead = 0.7; cArp = 0.45; hStrings = 0.9
                    if (bar == Score.FINALE - 1) { fill = pb; if (pb > 0.75) { hKick = 0.0; hBass = 0.0 } }
                }
                Score.Part.FINALE -> { full(); cLead = 0.8; cArp = 0.5; hStrings = 1.0; tr = 2 }
                Score.Part.CREDITS -> {
                    val p = prog(Score.CREDITS, 2)
                    hKeys = 1.0 - 0.7 * p; hStrings = 1.0 - 0.5 * p; hMarimba = 0.5 * (1 - p); tr = 2
                    if (bar == Score.END - 1) { cChime = 1.0; chimeAt = (Score.END - 1) * BAR }
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
    private var lfsr = 1
    private fun nesNoise(): Double {
        val bit = (lfsr xor (lfsr shr 1)) and 1
        lfsr = (lfsr shr 1) or (bit shl 14)
        return if (lfsr and 1 == 1) 1.0 else -1.0
    }

    private var cLeadPh = 0.0; private var cArpPh = 0.0; private var cBassPh = 0.0; private var chimePh = 0.0; private var jinglePh = 0.0
    private var bassPh = 0.0; private var bassLp1 = 0.0; private var bassLp2 = 0.0
    private val stringPh = DoubleArray(6); private var strLpL = 0.0; private var strLpR = 0.0
    private var hatLp = 0.0; private var snLp = 0.0; private var riserLp = 0.0; private var riserPh = 0.0; private var swellLp = 0.0
    private var impLp = 0.0; private var heyPh = 0.0; private val heyLow = DoubleArray(2); private val heyBand = DoubleArray(2)
    private var crushHoldL = 0.0; private var crushHoldR = 0.0; private var crushCount = 0
    private var hpL = 0.0; private var hpR = 0.0
    // sound effects
    private var sfxPh = 0.0; private var sfxPh2 = 0.0; private var whooshLp = 0.0; private var shatterLp = 0.0
    private val crowdBars = intArrayOf(Score.SHOW, Score.SHOW + 4)
    private val crowdLow = DoubleArray(2); private val crowdBand = DoubleArray(2); private var fwLp = 0.0

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
    private fun tri4(ph: Double) = floor((1 - 4 * abs(ph - 0.5)) * 8) / 8

    private fun sinceAny(times: IntArray, time: Double): Double {
        var best = -1.0
        for (b in times) { val d = time - b * BAR; if (d >= 0 && (best < 0 || d < best)) best = d }
        return best
    }

    private fun sinceAny(times: DoubleArray, time: Double): Double {
        var best = -1.0
        for (x in times) { val d = time - x; if (d >= 0 && (best < 0 || d < best)) best = d }
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
        val root = ROOTS[ci] + tr

        var l = 0.0; var r = 0.0
        var sendRev = 0.0; var sendDly = 0.0

        val kickOn = hKick > 0
        val duck = if (kickOn) 0.35 + 0.65 * (1 - exp(-kt * 10)) else 1.0

        // ════════════ the 8-bit world ════════════
        var chipL = 0.0; var chipR = 0.0

        if (cChime > 0) {
            val tc = (time - chimeAt).coerceAtLeast(0.0)
            val step = (tc * 12).toInt()
            val note = CHIME[step.coerceAtMost(CHIME.size - 1)] + tr
            val st = if (step < CHIME.size - 1) (tc * 12 - step) / 12 else tc - (CHIME.size - 1) / 12.0
            chimePh = (chimePh + FREQ[note] / SR) % 1.0
            val env = if (step < CHIME.size - 1) 1.0 else exp(-st * 1.4)
            val g = pulse(chimePh, 0.25) * 0.14 * env * cChime
            chipL += g; chipR += g; sendRev += g * 0.4; sendDly += g * 0.4
        }

        if (cLead > 0) {
            // cSlow: the hook at half speed (a lullaby / a memory)
            val pos = if (cSlow) beatPos else beatPos * 2
            val stepAll = floor(pos).toInt()
            val step = stepAll % 8
            val row = HOOK[if (cSlow) (((stepAll / 8) % 4) + 4) % 4 else ci]
            var idx = step
            var st = (pos - stepAll) * (if (cSlow) BEAT else BEAT / 2)
            val unit = if (cSlow) BEAT else BEAT / 2
            if (row[idx] < 0 && idx > 0) { idx -= 1; st += unit }
            val note = row[idx]
            if (note >= 0) {
                val vib = 1 + 0.006 * sin(TAU * 6 * time) * (st * 3).coerceAtMost(1.0)
                cLeadPh = (cLeadPh + FREQ[note + tr] * vib / SR) % 1.0
                val env = if (cSlow) exp(-st * 2.2) else (0.8 + 0.2 * exp(-st * 8)) * (if (st < unit * 1.9) 1.0 else 0.0)
                val g = pulse(cLeadPh, if (cSlow) 0.5 else 0.125 + 0.125 * (bar % 2)) * 0.075 * cLead * env
                chipL += g * 0.9; chipR += g * 1.1; sendDly += g * 0.35; sendRev += g * 0.2
            }
        }

        if (cArp > 0) {
            val rate = if (cSlow || mode == Mode.TIMELINE && bar < Score.ADVENTURE) 4.0 else 8.0
            val k = floor(beatPos * rate).toInt() % 3
            cArpPh = (cArpPh + FREQ[chord[k] + 12 + tr] / SR) % 1.0
            val g = pulse(cArpPh, 0.5) * 0.045 * cArp
            chipL += g * 1.2; chipR += g * 0.8
        }

        if (cBass > 0) {
            val note = root - 12 + (if (eighth % 2 == 1) 12 else 0)
            cBassPh = (cBassPh + FREQ[note] / SR) % 1.0
            val g = tri4(cBassPh) * 0.22 * cBass * (if (inEighth < 0.85) 1.0 else 0.0)
            chipL += g; chipR += g
        }

        if (cDrums > 0) {
            if (beatInBar == 0 || beatInBar == 2) {
                val ph = 55 * kt + (220 - 55) * (1 - exp(-kt * 40)) / 40
                val k = pulse(ph - floor(ph), 0.5) * exp(-kt * 14) * 0.2
                chipL += k; chipR += k
            }
            if (beatInBar == 1 || beatInBar == 3) { val s = nesNoise() * exp(-kt * 16) * 0.16; chipL += s; chipR += s }
            if (six % 2 == 1) { val s = nesNoise() * exp(-inSix * BEAT / 4 * 120) * 0.05; chipL += s * 0.7; chipR += s * 1.3 }
        }

        // the power-up jingle: climbing arpeggios, each beat a step higher
        if (jingle) {
            val lt = time - Score.GRAB
            if (lt in 0.0..(BAR * 0.9)) {
                val stepI = (lt / (BEAT / 6)).toInt()
                val beatN = (lt / BEAT).toInt()
                val notes = intArrayOf(62, 66, 69, 74, 78, 81)
                val note = notes[stepI % 6] + beatN * 2 + (if (stepI % 12 >= 6) 12 else 0)
                jinglePh = (jinglePh + FREQ[note.coerceAtMost(130)] / SR) % 1.0
                val g = pulse(jinglePh, 0.25) * 0.11 * (1.0 - lt / BAR * 0.5)
                chipL += g; chipR += g; sendDly += g * 0.3; sendRev += g * 0.3
            }
        }

        // bit-crusher + stutter: the game breaking
        if (crush > 0) {
            val hold = 1 + (crush * 24).toInt()
            if (crushCount++ % hold == 0) { crushHoldL = chipL; crushHoldR = chipR }
            val q = 2.0.pow(16.0 - crush * 13)
            chipL = floor(crushHoldL * q) / q; chipR = floor(crushHoldR * q) / q
        }
        if (stutter) {
            val slice = floor(beatPos * 8).toInt()
            if ((slice * 2654435761L).toInt() ushr 29 < 2) { chipL = 0.0; chipR = 0.0 }
        }
        l += chipL; r += chipR
        sendRev += (chipL + chipR) * 0.1

        // ════════════ the full-sound world ════════════
        if (hStrings > 0) {
            var sl = 0.0; var sr = 0.0
            for (v in 0 until 3) {
                val f = FREQ[chord[v] + tr] * (1 + 0.003 * sin(TAU * (5.2 + v * 0.4) * time))
                stringPh[v * 2] = (stringPh[v * 2] + f * 0.997 / SR) % 1.0
                stringPh[v * 2 + 1] = (stringPh[v * 2 + 1] + f * 1.003 / SR) % 1.0
                sl += saw(stringPh[v * 2]); sr += saw(stringPh[v * 2 + 1])
            }
            strLpL += 0.045 * (sl / 3 - strLpL); strLpR += 0.045 * (sr / 3 - strLpR)
            val g = 0.2 * hStrings * (0.6 + 0.4 * duck)
            l += strLpL * g; r += strLpR * g
            sendRev += (strLpL + strLpR) * g * 0.6
        }

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
                    val f = FREQ[chord[v] + 12 + tr]
                    s += sin(TAU * f * st + 1.6 * exp(-st * 6) * sin(TAU * f * st))
                }
                val env = (st / 0.003).coerceAtMost(1.0) * exp(-st * (if (halfTime) 1.2 else 3.5)) * gate
                val g = s / 3 * 0.2 * hKeys * env * (0.7 + 0.3 * duck)
                l += g * 1.1; r += g * 0.9
                sendRev += g * 0.4; sendDly += g * 0.2
            }
        }

        if (hBass > 0) {
            val step = BASS[sixInBar]
            if (step >= 0 && !(halfTime && sixInBar % 4 != 0)) {
                val st = inSix * BEAT / 4
                bassPh = (bassPh + FREQ[root - 12 + step] / SR) % 1.0
                val env = exp(-st * 14)
                val cut = 0.02 + 0.3 * env
                bassLp1 += cut * (saw(bassPh) - bassLp1); bassLp2 += cut * (bassLp1 - bassLp2)
                val g = (tanh(bassLp2 * 2.2) * 0.5 + sin(TAU * bassPh) * 0.3) * 0.42 * hBass * (0.5 + 0.5 * env) * duck
                l += g; r += g
            } else { bassLp1 *= 0.99; bassLp2 *= 0.99 }
        }

        if (hMarimba > 0) {
            val step = eighth % 8
            val note = HOOK[ci][step]
            if (note >= 0) {
                val st = inEighth * BEAT / 2
                val f = FREQ[note + tr]
                val m = sin(TAU * f * st + 2.5 * exp(-st * 30) * sin(TAU * f * 4 * st))
                val env = (st / 0.002).coerceAtMost(1.0) * exp(-st * 9)
                val g = m * 0.2 * hMarimba * env
                val pan = if (step % 2 == 0) 0.25 else -0.25
                l += g * (1 - pan); r += g * (1 + pan)
                sendDly += g * 0.45; sendRev += g * 0.3
            }
        }

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

        if (kickOn && (!halfTime || beatInBar == 0 || beatInBar == 2)) {
            val ph = 50 * kt + (140 - 50) * (1 - exp(-kt * 28)) / 28
            val k = tanh(sin(TAU * ph) * exp(-kt * 7) * 2.4) * 0.6 + noise() * exp(-kt * 500) * 0.1
            l += k * hKick; r += k * hKick
        }

        val snareBeat = if (halfTime) beatInBar == 2 else (beatInBar == 1 || beatInBar == 3)
        if (hSnare > 0 && snareBeat) {
            val nz = noise()
            snLp += 0.4 * (nz - snLp)
            val s = ((nz - snLp) * exp(-kt * 14) * 0.5 + sin(TAU * 185 * kt) * exp(-kt * 22) * 0.4) * 0.55 * hSnare
            l += s; r += s; sendRev += s * 0.35
        }

        if (hShaker > 0) {
            val nz = noise()
            hatLp += 0.6 * (nz - hatLp)
            val hp = nz - hatLp
            var g = hp * exp(-inSix * BEAT / 4 * 55) * 0.08 * (if (six % 2 == 1) 1.0 else 0.55) * hShaker
            if (hOpenHat > 0 && six % 4 == 2) g += hp * exp(-inSix * BEAT / 4 * 9) * 0.07 * hOpenHat
            l += g * 0.8; r += g * 1.2
        }

        if (fill >= 0) {
            val p = fill.coerceIn(0.0, 1.0)
            val grid = when { p < 0.5 -> BEAT / 2; p < 0.75 -> BEAT / 4; else -> BEAT / 8 }
            val st = time % grid
            val s = (noise() * 0.6 + sin(TAU * 200 * st) * 0.5) * exp(-st * 26) * (0.25 + 0.75 * p) * 0.45
            l += s; r += s; sendRev += s * 0.3
        }

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

        // ════════════ sound effects, locked to the picture ════════════
        if (mode == Mode.TIMELINE) {
            // jump: a square wave that sweeps up as Rin leaves the ground
            val tj = sinceAny(Score.hits, time + Score.JUMP)
            if (tj in 0.0..0.2) {
                sfxPh = (sfxPh + (320 + 1400 * tj / 0.2) / SR) % 1.0
                val g = pulse(sfxPh, 0.5) * 0.07 * exp(-tj * 8)
                l += g; r += g
            }
            // coin: the classic two-note ding when his head hits a block (the big block: lower + a thump)
            val th = sinceAny(Score.hits, time)
            if (th in 0.0..0.6) {
                val big = time >= Score.hits[6] - 0.01
                val f = if (th < 0.07) (if (big) 659.0 else 988.0) else (if (big) 880.0 else 1319.0)
                sfxPh2 = (sfxPh2 + f / SR) % 1.0
                val g = pulse(sfxPh2, 0.5) * 0.08 * (if (th < 0.07) 1.0 else exp(-(th - 0.07) * 6))
                l += g * 0.9; r += g * 1.1; sendRev += g * 0.3
                if (th < 0.08) { val k = pulse((75 * th) % 1.0, 0.5) * exp(-th * 40) * 0.15; l += k; r += k }
            }
            // the orb rising out of the big block: little ascending blips
            val tOrb = time - Score.hits[6]
            if (tOrb in 0.1..1.0) {
                val stepI = ((tOrb - 0.1) / 0.075).toInt()
                val f = FREQ[74 + stepI * 2]
                sfxPh = (sfxPh + f / SR) % 1.0
                val st = (tOrb - 0.1) % 0.075
                val g = pulse(sfxPh, 0.25) * 0.05 * exp(-st * 30)
                l += g; r += g; sendDly += g * 0.3
            }
            // shatter: glass and a noise crash as the game breaks
            val ts = time - Score.SHATTER
            if (ts in 0.0..1.6) {
                shatterLp += (0.05 + 0.6 * exp(-ts * 3)) * (noise() - shatterLp)
                var g = shatterLp * 0.5 * exp(-ts * 2.2)
                // glass: random high pings
                val ping = (floor(ts * 40)).toInt()
                if (ping < 40 && ((ping * 7919) % 5 == 0)) {
                    val pt = ts * 40 - ping
                    g += sin(TAU * (2500 + (ping * 331) % 2500) * pt / 40) * exp(-pt * 6) * 0.08
                }
                l += g; r += g * 0.9; sendRev += g * 0.6
            }
            // whooshes between features in the drop
            val tw = sinceAny(Score.whooshes, time + 0.3)
            if (tw in 0.0..0.5) {
                val p = tw / 0.5
                whooshLp += (0.02 + 0.3 * sin(PI * p)) * (noise() - whooshLp)
                val g = whooshLp * 0.35 * sin(PI * p)
                l += g * (1 - p); r += g * p; sendRev += g * 0.3
            }
            // the crowd: a roar at the start of the concert and when the confetti drops
            val tc = sinceAny(crowdBars, time)
            if (tc in 0.0..3.0) {
                val x = noise()
                var y = 0.0
                for (f in 0 until 2) {
                    val fc = if (f == 0) 900.0 else 2400.0
                    val gg = 2 * sin(PI * fc / SR)
                    crowdLow[f] += gg * crowdBand[f]
                    val high = x - crowdLow[f] - 0.5 * crowdBand[f]
                    crowdBand[f] += gg * high
                    y += crowdBand[f]
                }
                val env = (tc / 0.25).coerceAtMost(1.0) * exp(-tc * 0.9) * (0.8 + 0.2 * sin(TAU * 7 * time))
                val g = y * 0.12 * env
                l += g * 1.1; r += g * 0.9; sendRev += g * 0.5
            }
            // fireworks: a whistle up, then a boom and crackle
            for (fw in Score.fireworks) {
                val tf = time - fw
                if (tf in -0.5..0.0) {
                    val p = (tf + 0.5) / 0.5
                    val g = sin(TAU * (900 + 1700 * p) * tf) * 0.03 * p
                    l += g * 0.7; r += g * 1.3
                }
                if (tf in 0.0..1.4) {
                    fwLp += 0.3 * (noise() - fwLp)
                    val boom = sin(TAU * 45 * tf) * exp(-tf * 5) * 0.4
                    val crackle = if (noise() > 0.93) noise() * exp(-tf * 2.5) * 0.25 else 0.0
                    val g = boom + fwLp * exp(-tf * 6) * 0.3 + crackle
                    l += g; r += g; sendRev += g * 0.5
                }
            }
        }

        // impacts on each drop
        val tImp = if (mode == Mode.TIMELINE) sinceAny(Score.impacts, time) else if (mode == Mode.DROP && time < 3) time else -1.0
        if (tImp in 0.0..3.0) {
            val boom = sin(TAU * (32 * tImp + (110 - 32) * (1 - exp(-tImp * 8)) / 8)) * exp(-tImp * 2.0) * 0.7
            impLp += 0.2 * (noise() - impLp)
            val air = impLp * exp(-tImp * 7) * 0.5
            l += boom + air; r += boom + air; sendRev += boom * 0.3 + air
        }

        if (lowCut > 0) {
            val c = 0.002 + 0.08 * lowCut
            hpL += c * (l - hpL); hpR += c * (r - hpR)
            l -= hpL * lowCut; r -= hpR * lowCut
        }

        val dl = delayL[dIdx]; val dr = delayR[dIdx]
        dFb += 0.35 * (dr - dFb)
        delayL[dIdx] = sendDly + dFb * 0.42
        delayR[dIdx] = dl
        dIdx = (dIdx + 1) % dLen
        l += dl * 0.55; r += dr * 0.55
        sendRev += (dl + dr) * 0.15

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
