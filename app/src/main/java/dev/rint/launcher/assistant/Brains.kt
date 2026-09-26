package dev.rint.launcher.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import dev.rint.launcher.core.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ToolSpec(val name: String, val description: String, val properties: JsonObject, val required: List<String>) {
    val schema: JsonObject get() = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }
}

data class Call(val id: String, val name: String, val args: JsonObject)
data class ToolOutcome(val callId: String, val name: String, val text: String, val imageB64: String? = null)

sealed interface Turn {
    data class User(val text: String, val imageB64: String? = null) : Turn
    data class Assistant(val text: String, val calls: List<Call>, val raw: Any? = null) : Turn
    data class Results(val results: List<ToolOutcome>) : Turn
}

data class Reply(val text: String, val calls: List<Call>, val raw: Any? = null)

class BrainException(msg: String) : Exception(msg)

interface Brain {
    suspend fun turn(system: String, history: List<Turn>, tools: List<ToolSpec>): Reply
}

private val json = Json { ignoreUnknownKeys = true }

private fun post(url: String, body: String, headers: Map<String, String>): String {
    val c = URL(url).openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.connectTimeout = 20_000
    c.readTimeout = 120_000
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
    try {
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) {
            val msg = runCatching {
                val o = json.parseToJsonElement(text).jsonObject
                (o["error"] as? JsonObject)?.get("message")?.jsonPrimitive?.content
            }.getOrNull() ?: text.take(300)
            throw BrainException("HTTP $code: $msg")
        }
        return text
    } finally {
        c.disconnect()
    }
}

/** Only the newest screenshot is worth its tokens; older ones become a placeholder. */
private fun lastImageIndex(history: List<Turn>): Int = history.indexOfLast {
    (it is Turn.User && it.imageB64 != null) || (it is Turn.Results && it.results.any { r -> r.imageB64 != null })
}

// ─────────────────────────── Gemini ───────────────────────────

class GeminiBrain(private val key: String, private val model: String) : Brain {
    override suspend fun turn(system: String, history: List<Turn>, tools: List<ToolSpec>): Reply = withContext(Dispatchers.IO) {
        val keepImg = lastImageIndex(history)
        val contents = buildJsonArray {
            history.forEachIndexed { i, t ->
                when (t) {
                    is Turn.User -> add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", t.text) })
                            if (t.imageB64 != null && i == keepImg) add(inline(t.imageB64))
                        })
                    })
                    is Turn.Assistant -> add((t.raw as? JsonObject) ?: buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            if (t.text.isNotBlank()) add(buildJsonObject { put("text", t.text) })
                            t.calls.forEach { c -> add(buildJsonObject { put("functionCall", buildJsonObject { put("name", c.name); put("args", c.args) }) }) }
                        })
                    })
                    is Turn.Results -> add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray {
                            t.results.forEach { r ->
                                add(buildJsonObject {
                                    put("functionResponse", buildJsonObject {
                                        put("name", r.name)
                                        put("response", buildJsonObject { put("result", r.text) })
                                    })
                                })
                            }
                            if (i == keepImg) t.results.mapNotNull { it.imageB64 }.lastOrNull()?.let { add(inline(it)) }
                        })
                    })
                }
            }
        }
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
            put("contents", contents)
            if (tools.isNotEmpty()) put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("functionDeclarations", buildJsonArray {
                        tools.forEach { t ->
                            add(buildJsonObject {
                                put("name", t.name); put("description", t.description)
                                // Gemini rejects object schemas with no properties
                                if (t.properties.isNotEmpty()) put("parameters", t.schema)
                            })
                        }
                    })
                })
            })
        }
        val res = json.parseToJsonElement(
            post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent", body.toString(), mapOf("x-goog-api-key" to key))
        ).jsonObject
        val cand = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw BrainException("Gemini returned no answer")
        val content = cand["content"]?.jsonObject ?: return@withContext Reply("(Gemini stopped: ${cand["finishReason"]?.jsonPrimitive?.content})", emptyList())
        val parts = content["parts"]?.jsonArray.orEmpty()
        val text = parts.mapNotNull { p -> p.jsonObject.takeIf { it["thought"]?.jsonPrimitive?.booleanOrNull != true }?.get("text")?.jsonPrimitive?.content }.joinToString("")
        val calls = parts.mapNotNull { p ->
            p.jsonObject["functionCall"]?.jsonObject?.let { fc ->
                Call(fc["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(), fc["name"]!!.jsonPrimitive.content, fc["args"]?.jsonObject ?: JsonObject(emptyMap()))
            }
        }
        // replay the model turn verbatim so thought signatures survive
        Reply(text, calls, buildJsonObject { put("role", "model"); put("parts", JsonArray(parts)) })
    }

    private fun inline(b64: String) = buildJsonObject {
        put("inlineData", buildJsonObject { put("mimeType", "image/jpeg"); put("data", b64) })
    }
}

// ─────────────────────────── OpenAI-compatible (Groq, OpenRouter) ───────────────────────────

class OpenAiCompatBrain(
    private val base: String,
    private val key: String,
    private val model: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : Brain {
    override suspend fun turn(system: String, history: List<Turn>, tools: List<ToolSpec>): Reply = withContext(Dispatchers.IO) {
        val keepImg = lastImageIndex(history)
        fun img(b64: String) = buildJsonObject {
            put("type", "image_url")
            put("image_url", buildJsonObject { put("url", "data:image/jpeg;base64,$b64") })
        }
        val messages = buildJsonArray {
            add(buildJsonObject { put("role", "system"); put("content", system) })
            history.forEachIndexed { i, t ->
                when (t) {
                    is Turn.User -> add(buildJsonObject {
                        put("role", "user")
                        if (t.imageB64 != null && i == keepImg) put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", t.text) })
                            add(img(t.imageB64))
                        }) else put("content", t.text)
                    })
                    is Turn.Assistant -> add(buildJsonObject {
                        put("role", "assistant")
                        put("content", t.text.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
                        if (t.calls.isNotEmpty()) put("tool_calls", buildJsonArray {
                            t.calls.forEach { c ->
                                add(buildJsonObject {
                                    put("id", c.id); put("type", "function")
                                    put("function", buildJsonObject { put("name", c.name); put("arguments", c.args.toString()) })
                                })
                            }
                        })
                    })
                    is Turn.Results -> {
                        t.results.forEach { r -> add(buildJsonObject { put("role", "tool"); put("tool_call_id", r.callId); put("content", r.text) }) }
                        val shot = t.results.mapNotNull { it.imageB64 }.lastOrNull()
                        if (shot != null && i == keepImg) add(buildJsonObject {
                            put("role", "user")
                            put("content", buildJsonArray {
                                add(buildJsonObject { put("type", "text"); put("text", "Screenshot for the tool result above.") })
                                add(img(shot))
                            })
                        })
                    }
                }
            }
        }
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { t ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject { put("name", t.name); put("description", t.description); put("parameters", t.schema) })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
        }
        val res = json.parseToJsonElement(post("$base/chat/completions", body.toString(), mapOf("Authorization" to "Bearer $key") + extraHeaders)).jsonObject
        val msg = res["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject ?: throw BrainException("no answer from $model")
        val text = (msg["content"] as? JsonPrimitive)?.content.orEmpty()
        val calls = msg["tool_calls"]?.jsonArray.orEmpty().map { tc ->
            val o = tc.jsonObject
            val f = o["function"]!!.jsonObject
            val args = runCatching { json.parseToJsonElement(f["arguments"]!!.jsonPrimitive.content).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            Call(o["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(), f["name"]!!.jsonPrimitive.content, args)
        }
        Reply(text, calls)
    }
}

// ─────────────────────────── Claude (official Anthropic Java SDK) ───────────────────────────

class ClaudeBrain(key: String, private val model: String) : Brain {
    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(key).build()

    private fun plain(e: JsonElement): Any? = when (e) {
        is JsonNull -> null
        is JsonPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.longOrNull ?: e.doubleOrNull ?: e.content
        is JsonObject -> e.mapValues { plain(it.value) }
        is JsonArray -> e.map { plain(it) }
    }

    private fun image(b64: String) = ImageBlockParam.builder()
        .source(Base64ImageSource.builder().data(b64).mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build())
        .build()

    override suspend fun turn(system: String, history: List<Turn>, tools: List<ToolSpec>): Reply = withContext(Dispatchers.IO) {
        // Claude's history stays append-only (thinking blocks are replayed as returned).
        val messages = history.map { t ->
            when (t) {
                is Turn.User -> MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(
                    listOfNotNull(
                        t.imageB64?.let { ContentBlockParam.ofImage(image(it)) },
                        ContentBlockParam.ofText(TextBlockParam.builder().text(t.text).build()),
                    )
                ).build()
                is Turn.Assistant -> t.raw as MessageParam
                is Turn.Results -> MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(
                    t.results.map { r ->
                        ContentBlockParam.ofToolResult(
                            ToolResultBlockParam.builder()
                                .toolUseId(r.callId)
                                .contentOfBlocks(listOfNotNull(
                                    ToolResultBlockParam.Content.Block.ofText(TextBlockParam.builder().text(r.text).build()),
                                    r.imageB64?.let { ToolResultBlockParam.Content.Block.ofImage(image(it)) },
                                ))
                                .build()
                        )
                    }
                ).build()
            }
        }
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            .thinking(ThinkingConfigAdaptive.builder().build())
            .messages(messages)
        tools.forEach { t ->
            val props = Tool.InputSchema.Properties.builder()
            t.properties.forEach { (k, v) -> props.putAdditionalProperty(k, JsonValue.from(plain(v))) }
            b.addTool(Tool.builder().name(t.name).description(t.description)
                .inputSchema(Tool.InputSchema.builder().properties(props.build()).required(t.required).build()).build())
        }
        if (model == "claude-opus-5" || model == "claude-fable-5-1") {
            // server-side refusal fallback: if a safety classifier declines, the API retries on a fallback model
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        val response = client.messages().create(b.build())
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            return@withContext Reply("I can't help with that one.", emptyList(), response.toParam())
        }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        val calls = response.content().mapNotNull { blk ->
            blk.toolUse().orElse(null)?.let { tu ->
                val args = json.parseToJsonElement(jsonMapper().writeValueAsString(tu._input())).jsonObject
                Call(tu.id(), tu.name(), args)
            }
        }
        Reply(text, calls, response.toParam())
    }
}

object Brains {
    fun create(provider: AiProvider, key: String, model: String): Brain = when (provider) {
        AiProvider.GEMINI -> GeminiBrain(key, model)
        AiProvider.GROQ -> OpenAiCompatBrain("https://api.groq.com/openai/v1", key, model)
        AiProvider.OPENROUTER -> OpenAiCompatBrain(
            "https://openrouter.ai/api/v1", key, model,
            mapOf("HTTP-Referer" to "https://github.com/ionlycomedheretotestlol/new-product-maybe", "X-Title" to "RintOS"),
        )
        AiProvider.CLAUDE -> ClaudeBrain(key, model)
    }
}
