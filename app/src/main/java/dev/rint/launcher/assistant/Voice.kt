package dev.rint.launcher.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import dev.rint.launcher.RintApp
import dev.rint.launcher.core.VoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Rin's voice. Gemini's TTS models speak (free tier with a Gemini API key); Android's
 * built-in TextToSpeech is the offline fallback. [level] (0..1) drives the talking animation.
 */
class VoiceOut(private val ctx: Context) {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking
    private var track: AudioTrack? = null
    private var tts: TextToSpeech? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun stop() {
        runCatching { track?.pause(); track?.flush(); track?.release() }
        track = null
        tts?.stop()
        _level.value = 0f
        _speaking.value = false
    }

    /** Returns false when voice is off or no engine could speak. */
    suspend fun speak(text: String): Boolean {
        val cfg = RintApp.instance.stores.config.value.ai
        if (!cfg.voice || text.isBlank()) return false
        val clean = text.replace(Regex("[*_`#>]"), "").take(900)
        val geminiKey = RintApp.instance.stores.secret("GEMINI")
        if (cfg.voiceEngine == VoiceEngine.GEMINI) {
            if (geminiKey == null) return false
            val ok = runCatching { gemini(clean, geminiKey, cfg.ttsModel, cfg.voiceName) }.getOrDefault(false)
            if (ok) return true
        }
        return android(clean)
    }

    private suspend fun gemini(text: String, key: String, model: String, voice: String): Boolean {
        val pcm = withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("contents", buildJsonArray { add(buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", (if (dev.rint.launcher.ui.I18n.pt) "Say this naturally and warmly, in Brazilian Portuguese: " else "Say this naturally and warmly: ") + "$text") }) }) }) })
                put("generationConfig", buildJsonObject {
                    put("responseModalities", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) })
                    put("speechConfig", buildJsonObject {
                        put("voiceConfig", buildJsonObject { put("prebuiltVoiceConfig", buildJsonObject { put("voiceName", voice) }) })
                    })
                })
            }
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15_000; c.readTimeout = 60_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("x-goog-api-key", key)
            try {
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                if (c.responseCode !in 200..299) return@withContext null
                val res = json.parseToJsonElement(c.inputStream.bufferedReader().readText()).jsonObject
                val data = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject?.get("inlineData")?.jsonObject?.get("data")?.jsonPrimitive?.content
                data?.let { Base64.decode(it, Base64.DEFAULT) }
            } finally {
                c.disconnect()
            }
        } ?: return false
        play(pcm, 24_000)
        return true
    }

    private suspend fun play(pcm: ByteArray, rate: Int) {
        stop()
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT), rate / 5 * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        _speaking.value = true
        t.play()
        withContext(Dispatchers.IO) {
            val chunk = rate / 25 * 2 // 40ms of 16-bit mono
            var off = 0
            while (off < pcm.size && coroutineContext.isActive && track === t) {
                val n = minOf(chunk, pcm.size - off)
                var sum = 0.0
                var i = off
                while (i + 1 < off + n) {
                    val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toDouble()
                    sum += s * s
                    i += 2
                }
                _level.value = (sqrt(sum / (n / 2).coerceAtLeast(1)) / 6000.0).toFloat().coerceIn(0f, 1f)
                if (t.write(pcm, off, n) < 0) break
                off += n
            }
        }
        delay(250)
        if (track === t) stop()
    }

    private suspend fun android(text: String): Boolean {
        val engine = tts ?: run {
            var ready = false
            var failed = false
            val e = TextToSpeech(ctx) { status -> if (status == TextToSpeech.SUCCESS) ready = true else failed = true }
            var waited = 0
            while (!ready && !failed && waited < 40) { delay(50); waited++ }
            if (!ready) return false
            runCatching { e.setLanguage(dev.rint.launcher.ui.I18n.locale) }
            e.setPitch(1.25f)
            e.setSpeechRate(1.05f)
            tts = e
            e
        }
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { _speaking.value = true }
            override fun onDone(id: String?) { finished.set(true) }
            @Deprecated("old API") override fun onError(id: String?) { finished.set(true) }
        })
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rin")
        // Android's engine exposes no loudness, so animate a believable talking rhythm instead.
        var t = 0
        while (!finished.get()) {
            _level.value = (0.35f + 0.35f * kotlin.math.sin(t * 0.9f) + 0.2f * kotlin.math.sin(t * 2.3f)).coerceIn(0f, 1f)
            delay(60); t++
        }
        _level.value = 0f
        _speaking.value = false
        return true
    }
}

/** Speech-to-text via Android's recognizer: free, no key, works offline on many phones. */
class VoiceIn(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    val listening = MutableStateFlow(false)
    val level = MutableStateFlow(0f)
    val partial = MutableStateFlow("")

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun listen(onResult: (String) -> Unit) {
        stop()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        partial.value = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { listening.value = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(db: Float) { level.value = ((db + 2f) / 12f).coerceIn(0f, 1f) }
            override fun onBufferReceived(b: ByteArray?) = Unit
            override fun onEndOfSpeech() { level.value = 0f }
            override fun onError(e: Int) { listening.value = false; level.value = 0f }
            override fun onResults(b: Bundle?) {
                listening.value = false
                level.value = 0f
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onResult)
            }
            override fun onPartialResults(b: Bundle?) {
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { partial.value = it }
            }
            override fun onEvent(t: Int, b: Bundle?) = Unit
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            val tag = dev.rint.launcher.ui.I18n.locale.toLanguageTag()
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
        })
    }

    fun stop() {
        runCatching { rec?.cancel(); rec?.destroy() }
        rec = null
        listening.value = false
        level.value = 0f
    }
}
