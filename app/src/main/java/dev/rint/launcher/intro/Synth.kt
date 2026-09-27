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

/**
 * The film's bar map. The picture reads this too, so music and visuals can never drift apart.
 * 128 BPM, one bar = 1.875 s.
 */
object Score {
    const val BPM = 128.0
    const val BEAT = 60.0 / BPM
    const val BAR = BEAT * 4
    const val BOOT = 2       // heartbeat → BRAAM, the story so far
    const val RISE = 6       // three.js: the wordmark assembles in 3D
    const val BUILD = 10     // words slam in, the groove starts, countdown
    const val DROP = 14      // the drop: every look, every color
    const val TUNNEL = 22    // three.js: warp tunnel, second drop
    const val LIFT = 26      // the evolution of Rin + the feature barrage
    const val BREAK = 30     // breakdown: piano, the part where it gets emotional
    const val SILENCE = 34   // nothing. a heartbeat.
    const val FINAL = 35     // BRAAM + key change. 1.3.
    const val OUTRO = 41     // credits
    const val END = 43
    const val TIMELINE_SECONDS = END * BAR

    enum class Part { HEART, BOOT, RISE, BUILD, DROP, TUNNEL, LIFT, BREAK, SILENCE, FINAL, OUTRO, AFTER }

    fun part(bar: Int) = when {
        bar < BOOT -> Part.HEART
        bar < RISE -> Part.BOOT
        bar < BUILD -> Part.RISE
        bar < DROP -> Part.BUILD
        bar < TUNNEL -> Part.DROP
        bar < LIFT -> Part.TUNNEL
        bar < BREAK -> Part.LIFT
        bar < SILENCE -> Part.BREAK
        bar < FINAL -> Part.SILENCE
        bar < OUTRO -> Part.FINAL
        bar < END -> Part.OUTRO
        else -> Part.AFTER
    }

    /** Whether the four-on-the-floor kick is playing in [bar] (the picture pulses with it). */
    fun kick(bar: Int) = bar in RISE + 2 until BREAK || bar in BREAK + 2 until SILENCE || bar in FINAL until OUTRO

    /** Bars where something huge hits on the downbeat. */
    val impacts = intArrayOf(BOOT, DROP, TUNNEL, FINAL, OUTRO)
}

/**
 * The RintOS intro soundtrack, synthesized live: no audio files, no licensing, ~0 APK bytes.
 * Stereo, A minor (i–VI–III–VII), with a pad, arp, sub + plucked bass, supersaw stabs, a lead hook,
 * drums, snare rolls, risers, BRAAMs, impacts, crashes, a heartbeat, ping-pong delay and reverb.
 *
 * [mode] follows the intro: TIMELINE plays the scripted [Score], CHILL loops while the user
 * personalizes, RISER builds for 2 bars, DROP loops until START, STOP is silence.
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

        // "Rin's Anthem" — E minor, i–VI–iv–V (Em–C–Am–B), the V borrowed from harmonic minor
        private val CHORDS = arrayOf(intArrayOf(55, 59, 64), intArrayOf(55, 60, 64), intArrayOf(57, 60, 64), intArrayOf(54, 59, 63))
        private val ROOTS = intArrayOf(40, 48, 45, 47)
        private val HOOK = arrayOf(
            intArrayOf(71, -1, 74, 76, 79, -1, 76, 74),
            intArrayOf(76, -1, 72, -1, 74, 76, 72, 71),
            intArrayOf(69, 72, 76, -1, 74, 72, 71, 69),
            intArrayOf(71, -1, 75, -1, 78, -1, 75, 71),
        )
        /** The hook's opening, played slowly on a music-box bell to tease it early. */
        private val BELL = intArrayOf(71, 74, 76, 79, 76, 72, 74, 71, 69, 72, 76, 74, 71, 75, 78, 75)
        private val STABS = intArrayOf(0, 3, 6, 8, 11, 14)
        private val ARP = intArrayOf(0, 2, 1, 2, 0, 2, 1, 2)
        private val DRONE = intArrayOf(28, 40, 47)
        private val BRAAM = intArrayOf(28, 40, 47)
        private val CHOP_STEPS = intArrayOf(0, 2, 5, 7, 10, 12, 15)
        private val CHOP_NOTES = intArrayOf(2, 0, 1, 2, 0, 1, 2)
        private val VOWELS = arrayOf(doubleArrayOf(800.0, 1150.0), doubleArrayOf(500.0, 900.0), doubleArrayOf(350.0, 2100.0))
        private val KEYS_SEQ = intArrayOf(0, 2, 1, 2, 0, 1, 2, 1)
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
            out[i * 2] = (tanh(outL * 1.15) * 0.86 * Short.MAX_VALUE).toInt().toShort()
            out[i * 2 + 1] = (tanh(outR * 1.15) * 0.86 * Short.MAX_VALUE).toInt().toShort()
        }
        written += frames
    }

    // ───────────── arrangement (levels, set per sample; no allocation) ─────────────
    private var aPad = 0.0; private var aPadCut = 0.0; private var aDrone = 0.0; private var aHeart = 0.0
    private var aArp = 0.0; private var aArpCut = 0.0; private var aSub = 0.0; private var aPluck = 0.0
    private var aKick = 0.0; private var aHats = 0; private var aClap = 0.0; private var aSaws = 0.0
    private var aLead = 0.0; private var aLeadCut = 0.0; private var aLeadOct = 0
    private var aRiser = -1.0; private var aRoll = -1.0; private var aSwell = 0.0; private var aChoir = 0.0; private var aChop = 0.0; private var aKeys = 0.0; private var aReese = 0.0; private var aBell = 0.0; private var aFuture = 0.0

    private fun full(energy: Double) {
        aPad = 0.75; aPadCut = 0.14; aArp = 0.8; aArpCut = 0.22; aSub = 0.55; aPluck = 0.6
        aKick = 1.0; aHats = 2; aClap = 1.0; aSaws = energy; aLead = 1.0; aLeadCut = 0.28
    }

    private fun chill() {
        aPad = 0.9; aPadCut = 0.06; aArp = 0.55; aArpCut = 0.09; aSub = 0.45; aKick = 0.4; aHats = 1
    }

    private fun arrange(bar: Int, pb: Double, time: Double) {
        aPad = 0.0; aPadCut = 0.05; aDrone = 0.0; aHeart = 0.0; aArp = 0.0; aArpCut = 0.1; aSub = 0.0; aPluck = 0.0
        aKick = 0.0; aHats = 0; aClap = 0.0; aSaws = 0.0; aLead = 0.0; aLeadCut = 0.2; aLeadOct = 0
        aRiser = -1.0; aRoll = -1.0; aSwell = 0.0; aChoir = 0.0; aChop = 0.0; aKeys = 0.0; aReese = 0.0; aBell = 0.0; aFuture = 0.0
        fun prog(from: Int, bars: Int) = ((time - from * BAR) / (bars * BAR)).coerceIn(0.0, 1.0)
        when (mode) {
            Mode.TIMELINE -> when (Score.part(bar)) {
                Score.Part.HEART -> {
                    aHeart = 1.0
                    if (bar == 1) aBell = 0.6
                    aDrone = 0.35 + 0.65 * (time / (2 * BAR))
                    if (bar == 1) aSwell = pb.pow(4.0)
                }
                Score.Part.BOOT -> {
                    val p = prog(Score.BOOT, 4)
                    aPad = (p * 3).coerceAtMost(1.0); aPadCut = 0.02 + 0.04 * p
                    aDrone = (1 - p * 2).coerceAtLeast(0.0)
                    aSub = 0.35
                    aKeys = 0.35 * p; aBell = 0.8
                    if (bar >= Score.BOOT + 2) { aArp = 0.65; aArpCut = 0.05 + 0.08 * (p * 2 - 1) }
                    if (bar == Score.RISE - 1) aSwell = pb.pow(6.0) * 0.5
                }
                Score.Part.RISE -> {
                    val p = prog(Score.RISE, 4)
                    aPad = 1.0; aPadCut = 0.05 + 0.06 * p; aArp = 1.0; aArpCut = 0.1 + 0.1 * p; aSub = 0.5
                    aLead = 0.35 + 0.3 * p; aLeadCut = 0.04 + 0.08 * p
                    aChoir = 0.35 * p; aBell = 0.45 * (1 - p)
                    if (bar >= Score.RISE + 2) { aKick = 0.65; aHats = 1; aRiser = prog(Score.RISE + 2, 2) * 0.6 }
                    if (bar == Score.BUILD - 1 && pb > 0.75) aSwell = ((pb - 0.75) * 4).pow(3.0) * 0.6
                }
                Score.Part.BUILD -> {
                    val p = prog(Score.BUILD, 4)
                    aPad = 0.9; aPadCut = 0.08 + 0.06 * p; aArp = 1.0; aArpCut = 0.14 + 0.1 * p; aSub = 0.55; aPluck = 1.0
                    aLead = 0.8; aLeadCut = 0.12 + 0.1 * p
                    aKick = 1.0; aHats = if (bar >= Score.BUILD + 2) 3 else 1; aClap = 1.0; aChop = 0.5 * p
                    if (bar >= Score.BUILD + 2) aRiser = prog(Score.BUILD + 2, 2)
                    if (bar == Score.DROP - 1) {
                        aRoll = pb
                        if (pb >= 0.75) { aKick = 0.0; aSub = 0.0; aPluck = 0.0; aHats = 0; aClap = 0.0 }
                    }
                }
                Score.Part.DROP -> { full(1.0); aChop = 1.0; aReese = 1.0 }
                Score.Part.TUNNEL -> { full(1.1); aSaws = 0.0; aFuture = 1.2; aChoir = 0.9; aChop = 1.0; aReese = 1.0; aLeadOct = 12 }
                Score.Part.LIFT -> {
                    full(1.15)
                    aChoir = 0.8; aReese = 0.8
                    if (bar >= Score.LIFT + 2) aRiser = prog(Score.LIFT + 2, 2)
                    if (bar == Score.BREAK - 1) {
                        aRoll = pb
                        if (pb >= 0.75) { aKick = 0.0; aSub = 0.0; aPluck = 0.0; aSaws = 0.0; aHats = 0; aReese = 0.0; aClap = 0.0 }
                    }
                }
                Score.Part.BREAK -> {
                    // the breakdown: keys, pad, choir, a heartbeat-slow sub. then it builds again.
                    val p = prog(Score.BREAK, 4)
                    aPad = 1.0; aPadCut = 0.035 + 0.06 * p; aKeys = 1.0; aChoir = 0.55; aSub = 0.3; aBell = 0.5
                    aLead = if (bar >= Score.BREAK + 1) 0.4 else 0.0; aLeadCut = 0.05 + 0.05 * p
                    if (bar >= Score.BREAK + 2) {
                        aKick = 0.5 + 0.4 * prog(Score.BREAK + 2, 2); aHats = 1; aArp = 0.8; aArpCut = 0.1 + 0.2 * p
                        aRiser = prog(Score.BREAK + 2, 2); aChop = 0.5
                    }
                    if (bar == Score.SILENCE - 1) {
                        aRoll = pb
                        if (pb >= 0.75) { aKick = 0.0; aHats = 0; aKeys = 0.0; aChop = 0.0 }
                    }
                }
                Score.Part.SILENCE -> {
                    // a breath of nothing. just a heartbeat, then the inhale.
                    aHeart = if (pb < 0.6) 0.7 else 0.0
                    if (pb > 0.7) aSwell = ((pb - 0.7) / 0.3).pow(3.0)
                }
                Score.Part.FINAL -> {
                    full(1.2)
                    aChoir = 1.0; aChop = 1.0; aReese = 1.0
                    aLeadOct = if (bar >= Score.OUTRO - 2) 12 else 0
                    if (bar >= Score.FINAL + 4) { aFuture = 0.9; aSaws = 0.0 }
                    if (bar == Score.OUTRO - 1 && pb > 0.75) { aKick = 0.0; aHats = 0; aClap = 0.0; aRoll = (pb - 0.75) * 4 }
                }
                Score.Part.OUTRO -> {
                    val p = prog(Score.OUTRO, 2)
                    aPad = 1.0 - 0.5 * p; aPadCut = 0.08; aKeys = 0.8 * (1 - p); aChoir = 0.8 * (1 - p); aSub = 0.4 * (1 - p); aBell = 0.8
                    aLead = if (bar == Score.OUTRO) 0.6 else 0.0; aLeadCut = 0.12
                }
                Score.Part.AFTER -> chill()
            }
            Mode.CHILL -> chill()
            Mode.RISER -> {
                val p = (time / (2 * BAR)).coerceIn(0.0, 1.0)
                aPad = 1.0 - 0.4 * p; aPadCut = 0.05 + 0.2 * p; aArp = 1.0; aArpCut = 0.1 + 0.25 * p
                aSub = 0.4 * (1 - p); aRiser = p; aRoll = p
                aKick = if (p < 0.5) 0.7 else 0.0
                aLead = 0.5; aLeadCut = 0.05 + 0.2 * p; aChoir = 0.5 * p
            }
            Mode.DROP -> { full(1.2); aChoir = 1.0; aChop = 1.0; aReese = 1.0 }
            Mode.STOP -> Unit
        }
    }

    // ───────────── voices state ─────────────
    private var seed = 0x2545F491
    private fun noise(): Double {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return seed / 2147483648.0
    }

    private val padPh = DoubleArray(6)
    private var padLpL = 0.0; private var padLpR = 0.0
    private val dronePh = DoubleArray(3); private var droneLp = 0.0
    private var arpPh = 0.0; private var arpLp = 0.0
    private var subPh = 0.0; private var pluckPh = 0.0; private var pluckLp = 0.0
    private val sawPh = DoubleArray(9); private var sawLpL = 0.0; private var sawLpR = 0.0
    private val leadPh = DoubleArray(3); private var leadLp1 = 0.0; private var leadLp2 = 0.0
    private val braamPh = DoubleArray(9); private var braamLp = 0.0
    private var chopPh = 0.0; private val chopLow = DoubleArray(2); private val chopBand = DoubleArray(2)
    private val reesePh = DoubleArray(2); private var reeseLp1 = 0.0; private var reeseLp2 = 0.0
    private val choirPh = DoubleArray(3); private val cLow = DoubleArray(2); private val cBand = DoubleArray(2)
    private var hatLp = 0.0; private var clapLp = 0.0; private var riserLp = 0.0; private var riserPh = 0.0
    private var crashLpL = 0.0; private var crashLpR = 0.0; private var impLp = 0.0; private var swellLp = 0.0

    // ping-pong delay (dotted 8th) and a small Schroeder reverb
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

    /** Seconds since the most recent hit at one of [bars] (relative to the mode), or -1. */
    private fun since(bars: IntArray, time: Double): Double {
        var best = -1.0
        for (b in bars) { val d = time - b * BAR; if (d >= 0 && (best < 0 || d < best)) best = d }
        return best
    }

    private val crashBars = intArrayOf(Score.DROP, Score.DROP + 4, Score.TUNNEL, Score.LIFT, Score.FINAL, Score.FINAL + 4, Score.OUTRO)
    private val braamBars = Score.impacts

    private fun frame(n: Long) {
        val time = (n - modeStartSample).toDouble() / SR
        val beatPos = time / BEAT
        val beatI = floor(beatPos).toInt()
        val inBeat = beatPos - beatI                  // 0..1 through the beat
        val kt = inBeat * BEAT                        // seconds since the beat
        val bar = beatI / 4
        val beatInBar = beatI % 4
        val pb = (beatPos / 4) - bar                  // 0..1 through the bar
        val six = floor(beatPos * 4).toInt()
        val inSix = beatPos * 4 - six
        val sixInBar = six % 16
        val eighth = floor(beatPos * 2).toInt()
        val inEighth = beatPos * 2 - eighth
        arrange(bar, pb, time)

        // chord progression restarts at each section so every drop lands on Am
        val anchor = when {
            mode != Mode.TIMELINE -> 0
            bar >= Score.OUTRO -> Score.OUTRO
            bar >= Score.FINAL -> Score.FINAL
            else -> Score.BOOT
        }
        val ci = if (mode == Mode.TIMELINE && bar < Score.BOOT) 0 else ((bar - anchor) % 4 + 4) % 4
        val chord = CHORDS[ci]
        // the truck-driver key change: everything up a whole step for the 1.0 finale
        val tr = if ((mode == Mode.TIMELINE && bar >= Score.FINAL && bar < Score.END) || mode == Mode.DROP) 2 else 0

        var l = 0.0; var r = 0.0
        var sendRev = 0.0; var sendDly = 0.0

        val duck = if (aKick > 0) 0.28 + 0.72 * (1 - exp(-kt * 9)) else 1.0

        // pad: 3 notes x 2 detuned saws, spread L/R
        if (aPad > 0) {
            var pl = 0.0; var pr = 0.0
            for (v in 0 until 3) {
                val f = FREQ[chord[v] + tr]
                padPh[v * 2] = (padPh[v * 2] + f * 0.996 / SR) % 1.0
                padPh[v * 2 + 1] = (padPh[v * 2 + 1] + f * 1.004 / SR) % 1.0
                pl += saw(padPh[v * 2]); pr += saw(padPh[v * 2 + 1])
            }
            padLpL += aPadCut * (pl / 3 - padLpL); padLpR += aPadCut * (pr / 3 - padLpR)
            val g = 0.22 * aPad * duck
            l += padLpL * g + padLpR * g * 0.3; r += padLpR * g + padLpL * g * 0.3
            sendRev += (padLpL + padLpR) * g * 0.35
        }

        // drone: A1 + A2 + E2, very dark
        if (aDrone > 0) {
            var s = 0.0
            for (k in 0 until 3) { dronePh[k] = (dronePh[k] + FREQ[DRONE[k]] * (1 + 0.002 * k) / SR) % 1.0; s += saw(dronePh[k]) }
            droneLp += 0.012 * (s / 3 - droneLp)
            val g = droneLp * 0.5 * aDrone * (0.8 + 0.2 * sin(TAU * 0.25 * time))
            l += g; r += g; sendRev += g * 0.3
        }

        // heartbeat: lub-dub every two beats
        if (aHeart > 0) {
            val cyc = beatPos / 2 - floor(beatPos / 2)
            val t1 = cyc * 2 * BEAT
            val t2 = t1 - 0.17
            var h = sin(TAU * (48 * t1 + 40 * (1 - exp(-t1 * 30)) / 30)) * exp(-t1 * 16)
            if (t2 > 0) h += 0.7 * sin(TAU * (44 * t2 + 30 * (1 - exp(-t2 * 30)) / 30)) * exp(-t2 * 18)
            h *= 0.85 * aHeart
            l += h; r += h; sendRev += h * 0.15
        }

        // arp: 16ths over chord tones, ping-ponging across the stereo field
        if (aArp > 0) {
            val note = chord[ARP[six % 8]] + 12 + if ((six / 8) % 2 == 1) 12 else 0
            arpPh = (arpPh + FREQ[note + tr] / SR) % 1.0
            val sq = (if (arpPh < 0.5) 1.0 else -1.0) * exp(-inSix * 4.5)
            arpLp += aArpCut * (sq - arpLp)
            val g = arpLp * 0.11 * aArp
            val pan = if (six % 2 == 0) 0.3 else -0.3
            l += g * (1 - pan); r += g * (1 + pan)
            sendDly += g * 0.5; sendRev += g * 0.2
        }

        // sub bass (ducked) + plucked offbeat bass
        val root = ROOTS[ci] + tr
        if (aSub > 0) {
            subPh = (subPh + FREQ[root - 12] / SR) % 1.0
            val s = sin(TAU * subPh) * 0.42 * aSub * duck
            l += s; r += s
        }
        if (aPluck > 0 && eighth % 2 == 1) {
            pluckPh = (pluckPh + FREQ[root] / SR) % 1.0
            val env = exp(-inEighth * 6)
            pluckLp += (0.04 + 0.2 * env) * (saw(pluckPh) - pluckLp)
            val s = pluckLp * 0.35 * env * aPluck
            l += s; r += s
        }

        // supersaw stabs
        if (aSaws > 0) {
            var hit = -1
            for (h in STABS) if (h <= sixInBar) hit = h
            if (hit >= 0) {
                val st = (sixInBar - hit + inSix) * BEAT / 4
                val env = exp(-st * 7) * (1 - exp(-st * 400))
                var sl = 0.0; var sr = 0.0
                for (v in 0 until 3) {
                    val f = FREQ[chord[v] + 12 + tr]
                    for (d in 0 until 3) {
                        val k = v * 3 + d
                        sawPh[k] = (sawPh[k] + f * (0.993 + 0.007 * d) / SR) % 1.0
                        val s = saw(sawPh[k])
                        if (d <= 1) sl += s
                        if (d >= 1) sr += s
                    }
                }
                val cut = 0.08 + 0.35 * env
                sawLpL += cut * (sl / 6 - sawLpL); sawLpR += cut * (sr / 6 - sawLpR)
                val g = 0.3 * aSaws * env * (0.5 + 0.5 * duck)
                l += sawLpL * g; r += sawLpR * g
                sendRev += (sawLpL + sawLpR) * g * 0.25; sendDly += (sawLpL + sawLpR) * g * 0.12
            }
        }

        // choir: saws through two vowel formants ("aah"), slow and huge
        if (aChoir > 0) {
            var x = 0.0
            for (v in 0 until 3) {
                choirPh[v] = (choirPh[v] + FREQ[chord[v] + 12 + tr] * (1 + 0.004 * sin(TAU * (4.8 + v * 0.3) * time)) / SR) % 1.0
                x += saw(choirPh[v])
            }
            x /= 3
            var y = 0.0
            for (k in 0 until 2) {
                val fc = if (k == 0) 760.0 else 1180.0
                val f = 2 * sin(PI * fc / SR)
                cLow[k] += f * cBand[k]
                val high = x - cLow[k] - 0.22 * cBand[k]
                cBand[k] += f * high
                y += cBand[k] * (if (k == 0) 1.0 else 0.6)
            }
            val g = y * 0.09 * aChoir * (0.6 + 0.4 * duck)
            l += g * 1.1; r += g * 0.9; sendRev += g * 0.8
        }

        // vocal chops: a formant-filtered "voice" chopping chord tones on a syncopated 16th grid
        if (aChop > 0) {
            var hit = -1
            for (h in CHOP_STEPS) if (h <= sixInBar) hit = h
            if (hit >= 0) {
                val k = CHOP_STEPS.indexOf(hit)
                val st = (sixInBar - hit + inSix) * BEAT / 4
                val env = (st / 0.008).coerceAtMost(1.0) * exp(-st * 11) * (if (st < 0.16) 1.0 else exp(-(st - 0.16) * 60))
                val note = chord[CHOP_NOTES[k]] + 24 + tr
                chopPh = (chopPh + FREQ[note] * (1 + 0.01 * sin(TAU * 6 * time)) / SR) % 1.0
                val x = saw(chopPh) * 0.7 + (if (chopPh < 0.3) 0.5 else -0.2)
                var y = 0.0
                val v = k % VOWELS.size
                for (f in 0 until 2) {
                    val fc = VOWELS[v][f]
                    val g = 2 * sin(PI * fc / SR)
                    chopLow[f] += g * chopBand[f]
                    val high = x - chopLow[f] - 0.16 * chopBand[f]
                    chopBand[f] += g * high
                    y += chopBand[f] * (if (f == 0) 1.0 else 0.7)
                }
                val gg = y * 0.07 * aChop * env
                val pan = if (k % 2 == 0) 0.35 else -0.35
                l += gg * (1 - pan); r += gg * (1 + pan)
                sendDly += gg * 0.5; sendRev += gg * 0.35
            }
        }

        // electric piano (FM): chord tones in 8ths, the heart of the breakdown
        if (aKeys > 0) {
            val kt8 = inEighth * BEAT / 2
            val step = eighth % 8
            val note = chord[KEYS_SEQ[step]] + 12 + tr + (if (bar % 2 == 1 && step >= 4) 12 else 0)
            val f = FREQ[note]
            val idx = 2.2 * exp(-kt8 * 5)
            val env = (kt8 / 0.004).coerceAtMost(1.0) * exp(-kt8 * 2.6)
            val ep = sin(TAU * f * kt8 + idx * sin(TAU * f * kt8)) * env
            val g = ep * 0.16 * aKeys
            val pan = if (step % 2 == 0) 0.2 else -0.2
            l += g * (1 - pan); r += g * (1 + pan)
            sendRev += g * 0.45; sendDly += g * 0.25
        }

        // reese bass: two detuned saws, a wobbling filter, ducked by the kick
        if (aReese > 0) {
            val f = FREQ[root - 12]
            reesePh[0] = (reesePh[0] + f * 0.993 / SR) % 1.0
            reesePh[1] = (reesePh[1] + f * 1.007 / SR) % 1.0
            val x = (saw(reesePh[0]) + saw(reesePh[1])) * 0.5
            val wob = 0.5 + 0.5 * sin(TAU * time * (Score.BPM / 60.0) / 2)
            val cut = 0.012 + 0.05 * wob
            reeseLp1 += cut * (x - reeseLp1); reeseLp2 += cut * (reeseLp1 - reeseLp2)
            val g = tanh(reeseLp2 * 2.5) * 0.22 * aReese * duck
            l += g; r += g
        }

        // music-box bell: the hook, slowly, one note per beat (FM, inharmonic ratio)
        if (aBell > 0) {
            val note = BELL[((beatI % 16) + 16) % 16] + 12 + tr
            val f = FREQ[note]
            val bt = kt
            val idx = 3.0 * exp(-bt * 6)
            val v = sin(TAU * f * bt + idx * sin(TAU * f * 3.5 * bt)) * exp(-bt * 2.2) * (bt / 0.003).coerceAtMost(1.0)
            val g = v * 0.13 * aBell
            val pan = if (beatI % 2 == 0) 0.3 else -0.3
            l += g * (1 - pan); r += g * (1 + pan)
            sendRev += g * 0.6; sendDly += g * 0.35
        }

        // future-bass chords: supersaws that swell up after every kick, with a wobble
        if (aFuture > 0) {
            var sl = 0.0; var sr = 0.0
            val wob = 1 + 0.006 * sin(TAU * time * (Score.BPM / 60.0) * 2)
            for (v in 0 until 3) {
                val f = FREQ[chord[v] + 12 + tr] * wob
                for (d in 0 until 3) {
                    val k = v * 3 + d
                    sawPh[k] = (sawPh[k] + f * (0.991 + 0.009 * d) / SR) % 1.0
                    val x = saw(sawPh[k])
                    if (d <= 1) sl += x
                    if (d >= 1) sr += x
                }
            }
            val swell = (1 - exp(-kt * 5)) * (0.4 + 0.6 * inBeat)
            val cut = 0.05 + 0.25 * swell
            sawLpL += cut * (sl / 6 - sawLpL); sawLpR += cut * (sr / 6 - sawLpR)
            val g = 0.3 * aFuture * swell
            l += sawLpL * g; r += sawLpR * g
            sendRev += (sawLpL + sawLpR) * g * 0.3
        }

        // lead hook
        if (aLead > 0) {
            val step = eighth % 8
            val row = HOOK[ci]
            var idx = step
            var st = inEighth * BEAT / 2
            if (row[idx] < 0 && idx > 0) { idx -= 1; st += BEAT / 2 }
            val note = row[idx]
            if (note >= 0) {
                val len = (if (idx < 7 && row[idx + 1] < 0) BEAT else BEAT / 2) - 0.03
                val gate = if (st < len) 1.0 else exp(-(st - len) * 60)
                val env = (st / 0.006).coerceAtMost(1.0) * (0.72 + 0.28 * exp(-st * 9)) * gate
                val vib = 1 + 0.005 * sin(TAU * 5.6 * time) * (st * 4).coerceAtMost(1.0)
                val f = FREQ[note + aLeadOct + tr] * vib
                leadPh[0] = (leadPh[0] + f * 0.997 / SR) % 1.0
                leadPh[1] = (leadPh[1] + f * 1.003 / SR) % 1.0
                leadPh[2] = (leadPh[2] + f * 0.5 / SR) % 1.0
                val duty = 0.5 + 0.28 * sin(TAU * 0.6 * time)
                val raw = (if (leadPh[0] < duty) 0.9 else -0.9) + saw((leadPh[1] * 2) % 1.0) * 0.55 + (if (leadPh[2] < 0.5) 0.5 else -0.5)
                val cut = aLeadCut * (0.6 + 0.4 * env)
                leadLp1 += cut * (raw / 2.6 - leadLp1)
                leadLp2 += cut * (leadLp1 - leadLp2)
                val g = leadLp2 * 0.3 * aLead * env
                l += g; r += g
                sendDly += g * 0.45; sendRev += g * 0.4
            } else {
                leadLp1 *= 0.995; leadLp2 *= 0.995
            }
        }

        // kick: pitch-swept sine + click, saturated
        if (aKick > 0) {
            val ph = 48 * kt + (160 - 48) * (1 - exp(-kt * 32)) / 32
            val k = tanh(sin(TAU * ph) * exp(-kt * 6.5) * 2.2) * 0.62 + noise() * exp(-kt * 400) * 0.12
            l += k * aKick; r += k * aKick
        }

        // hats: offbeat 8ths, or 16ths with open hats on the offbeat
        if (aHats > 0) {
            val nz = noise()
            hatLp += 0.55 * (nz - hatLp)
            val hp = nz - hatLp
            var env = 0.0
            if (aHats == 1) { if (eighth % 2 == 1) env = exp(-inEighth * BEAT / 2 * 60) * 1.0 }
            else if (aHats == 3) {
                // trap hats: 16ths, then 32nd-note rolls on the last beat of the bar
                val rate = if (beatInBar == 3) 8.0 else 4.0
                val ph = beatPos * rate - floor(beatPos * rate)
                env = exp(-ph * BEAT / rate * 110) * (if (beatInBar == 3) 0.6 + 0.4 * inBeat else 0.6)
            }
            else env = if (six % 4 == 2) exp(-inSix * BEAT / 4 * 14) * 1.1 else exp(-inSix * BEAT / 4 * 90) * (if (six % 2 == 1) 0.7 else 0.45)
            val g = hp * env * 0.13
            l += g * 0.8; r += g * 1.2
        }

        // clap on 2 and 4
        if (aClap > 0 && (beatInBar == 1 || beatInBar == 3)) {
            val nz = noise()
            clapLp += 0.35 * (nz - clapLp)
            val bp = nz - clapLp
            var env = 0.0
            for (o in 0 until 3) { val d = kt - o * 0.011; if (d >= 0) env = maxOf(env, exp(-d * 180)) }
            val d = kt - 0.022
            if (d >= 0) env = maxOf(env, exp(-d * 16))
            val g = bp * env * 0.42 * aClap
            l += g * 1.1; r += g * 0.9; sendRev += g * 0.4
        }

        // snare roll, accelerating: quarters → 8ths → 16ths → 32nds
        if (aRoll >= 0) {
            val p = aRoll.coerceIn(0.0, 1.0)
            val grid = when { p < 0.25 -> BEAT; p < 0.5 -> BEAT / 2; p < 0.75 -> BEAT / 4; else -> BEAT / 8 }
            val st = (time % grid)
            val s = (noise() * 0.7 + sin(TAU * 190 * st) * 0.5) * exp(-st * 28) * (0.2 + 0.8 * p * p) * 0.5
            l += s; r += s; sendRev += s * 0.3
        }

        // riser: sweeping noise + climbing tone
        if (aRiser >= 0) {
            val p = aRiser.coerceIn(0.0, 1.0)
            riserLp += (0.01 + 0.5 * p * p) * (noise() - riserLp)
            riserPh = (riserPh + (180 + 1900 * p * p) / SR) % 1.0
            val s = riserLp * 0.3 * p * p + saw(riserPh) * 0.035 * p
            l += s * (1 - 0.3 * sin(TAU * time * 2)); r += s * (1 + 0.3 * sin(TAU * time * 2))
            sendRev += s * 0.4
        }

        // inhale before the big hits
        if (aSwell > 0) {
            swellLp += (0.02 + 0.3 * aSwell) * (noise() - swellLp)
            val s = swellLp * 0.45 * aSwell
            l += s; r += s; sendRev += s * 0.5
        }

        // one-shots: BRAAM, impact, crash
        val tBraam: Double; val tCrash: Double
        if (mode == Mode.TIMELINE) {
            tBraam = since(braamBars, time)
            tCrash = since(crashBars, time)
        } else if (mode == Mode.DROP) {
            tBraam = time
            tCrash = time % (BAR * 8)
        } else { tBraam = -1.0; tCrash = -1.0 }

        if (tBraam in 0.0..5.0) {
            var s = 0.0
            for (v in 0 until 3) for (d in 0 until 3) {
                val k = v * 3 + d
                braamPh[k] = (braamPh[k] + FREQ[BRAAM[v] + tr] * (0.994 + 0.006 * d) / SR) % 1.0
                s += saw(braamPh[k])
            }
            braamLp += (0.015 + 0.22 * exp(-tBraam * 2.2)) * (s / 9 - braamLp)
            val env = (tBraam / 0.015).coerceAtMost(1.0) * exp(-tBraam * 0.85)
            val g = braamLp * 1.25 * env
            l += g; r += g; sendRev += g * 0.5
            // impact: deep boom + a burst of air
            val boom = sin(TAU * (28 * tBraam + (95 - 28) * (1 - exp(-tBraam * 7)) / 7)) * exp(-tBraam * 1.7) * 0.75
            impLp += 0.2 * (noise() - impLp)
            val air = impLp * exp(-tBraam * 9) * 0.6
            l += boom + air; r += boom + air; sendRev += (boom * 0.3 + air)
        }
        if (tCrash in 0.0..3.0) {
            val nl = noise(); val nr = noise()
            crashLpL += 0.6 * (nl - crashLpL); crashLpR += 0.6 * (nr - crashLpR)
            val env = exp(-tCrash * 1.5) * 0.2
            l += (nl - crashLpL) * env; r += (nr - crashLpR) * env
            sendRev += (nl - crashLpL) * env * 0.3
        }

        // ping-pong delay
        val dl = delayL[dIdx]; val dr = delayR[dIdx]
        dFb += 0.35 * (dr - dFb)
        delayL[dIdx] = sendDly + dFb * 0.45
        delayR[dIdx] = dl
        dIdx = (dIdx + 1) % dLen
        l += dl * 0.6; r += dr * 0.6
        sendRev += (dl + dr) * 0.15

        // reverb: 4 combs + 2 allpasses per side
        val input = sendRev * 0.03
        var rl = 0.0; var rr = 0.0
        for (c in 0 until 8) {
            val buf = combs[c]
            val y = buf[combIdx[c]]
            combFilt[c] = y * 0.6 + combFilt[c] * 0.4
            buf[combIdx[c]] = input + combFilt[c] * 0.86
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
        outL = l + rl * 0.9
        outR = r + rr * 0.9
    }
}
