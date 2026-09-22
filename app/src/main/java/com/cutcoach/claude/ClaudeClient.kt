package com.cutcoach.claude

import com.cutcoach.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talking to Claude, through the Worker.
 *
 * No HTTP library: one POST with a JSON body does not justify a dependency,
 * and `HttpURLConnection` is in the platform. It runs on the IO dispatcher
 * because it blocks.
 *
 * The app holds the Worker's address and the shared secret, both from
 * local.properties. It does not hold the Anthropic key and has no field for
 * one, so there is nothing here to leak.
 *
 * Nothing in this file decides anything about training. It fetches text; the
 * guardrails in [validate] decide whether any of it may be shown.
 */
object ClaudeClient {

    /** The models the protocol names, and the only ones the Worker accepts. */
    const val SONNET = "claude-sonnet-5"
    const val HAIKU = "claude-haiku-4-5"

    /** False until the owner has deployed the Worker and filled in local.properties. */
    val isConfigured: Boolean
        get() = BuildConfig.CLAUDE_PROXY_URL.isNotBlank() &&
            BuildConfig.CLAUDE_PROXY_SECRET.isNotBlank()

    private val json = Json { ignoreUnknownKeys = true }

    /** What came back, with what it cost. */
    data class Answer(
        val text: String,
        val inputTokens: Int,
        val outputTokens: Int,
    )

    sealed interface Result {
        data class Ok(val answer: Answer) : Result

        /** Something went wrong that the owner can act on, in plain words. */
        data class Failed(val message: String) : Result
    }

    /**
     * One request.
     *
     * [prompt] carries only summaries. Assembling them is the caller's job and
     * is done in [Summaries], which is where the "never raw health records"
     * rule is actually enforced.
     */
    suspend fun ask(
        model: String,
        system: String,
        prompt: String,
        maxTokens: Int = 1024,
    ): Result = withContext(Dispatchers.IO) {
        if (!isConfigured) {
            return@withContext Result.Failed(
                "The Claude layer is not set up. Deploy the Worker and add " +
                    "cutcoach.proxyUrl and cutcoach.proxySecret to local.properties.",
            )
        }

        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", system)
            put("messages", buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", prompt)
                    },
                )
            })
        }.toString()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(BuildConfig.CLAUDE_PROXY_URL).openConnection() as HttpURLConnection)
                .apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("content-type", "application/json")
                    setRequestProperty("x-cutcoach-secret", BuildConfig.CLAUDE_PROXY_SECRET)
                }

            connection.outputStream.use { it.write(body.toByteArray()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status !in 200..299) {
                return@withContext Result.Failed(readError(text, status))
            }
            parse(text)?.let { Result.Ok(it) }
                ?: Result.Failed("Claude returned something this app could not read.")
        } catch (e: Exception) {
            // The message only. An exception carrying the request would carry
            // the shared secret in its headers.
            Result.Failed("Could not reach the proxy: ${e.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }

    /** Pull the text and the token counts out of a Messages API response. */
    private fun parse(raw: String): Answer? = try {
        val root = json.parseToJsonElement(raw).jsonObject
        val text = root["content"]?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            ?.joinToString("")
            .orEmpty()
        val usage = root["usage"]?.jsonObject
        Answer(
            text = text,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0,
        ).takeIf { it.text.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun readError(raw: String, status: Int): String = try {
        val message = json.parseToJsonElement(raw).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.content
        message ?: "The proxy returned $status."
    } catch (_: Exception) {
        "The proxy returned $status."
    }

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 45_000
}

/**
 * Pull a JSON object out of what a model wrote.
 *
 * Models fence JSON in ``` blocks or add a sentence before it however firmly
 * they are asked not to, and a schema that only parses perfect output would
 * fail on answers that were substantively correct. This takes the outermost
 * braces and leaves the rest.
 */
fun extractJsonObject(text: String): JsonObject? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return try {
        Json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
    } catch (_: Exception) {
        null
    }
}
