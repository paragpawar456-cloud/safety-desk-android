package com.parag.safetydesk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One turn of a conversation the app keeps itself (Claude remembers nothing between calls). */
data class Turn(val user: Boolean, val text: String, val images: List<ByteArray> = emptyList())

class AiException(message: String) : Exception(message)

/** How hard Claude thinks before answering (the API's output_config.effort). */
enum class Effort(val v: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }

/**
 * Talks to Claude over the Messages API (plain HTTPS + JSON, no SDK, so the APK stays small),
 * with the safety-manager instructions file as the system prompt.
 * Blocking calls: run them off the main thread.
 */
class Ai(private val ctx: Context, private val store: Store) {
    private val instructions: String by lazy { ctx.assets.open("agent_instructions.md").bufferedReader().readText() }

    fun system(): String {
        val s = store
        val site = listOf(
            "Project" to s.pref("project"), "Location" to s.pref("location"), "Client" to s.pref("client"),
            "Main contractor" to s.pref("contractor"), "Towers / areas" to s.pref("towers"),
            "Workers on site (as entered)" to s.pref("workers"), "Safe man-hours (as entered)" to s.pref("manhours"),
            "Safety manager" to s.pref("manager"),
        ).filter { it.second.isNotBlank() }.joinToString("\n") { "- ${it.first}: ${it.second}" }
        return instructions + "\n\n### 14. THIS DEVICE\n" +
            "You are running inside the Safety Desk Android app used by the Safety Manager. Today's date is ${Store.fmt(Store.today())}.\n" +
            "Write in Markdown (headings, numbered lists, and pipe tables where useful) so the app can render, share and print it.\n" +
            "Site details entered in the app (anything not listed is unknown — say so rather than inventing it):\n" +
            (site.ifBlank { "- none entered yet" })
    }

    /**
     * Streams an answer. [onText] receives the whole answer so far on each update.
     * When [web] is true, Claude may search the web (Section 3 of the instructions).
     */
    fun ask(turns: List<Turn>, web: Boolean = false, effort: Effort = Effort.MEDIUM, onText: (String) -> Unit): String {
        val key = store.apiKey
        if (key.isBlank()) throw AiException("Add your Anthropic API key in Settings first.")
        val model = store.model
        val messages = JSONArray()
        for (t in turns) {
            val content: Any = if (t.user && t.images.isNotEmpty()) JSONArray().apply {
                t.images.forEach { img ->
                    put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64")
                        .put("media_type", "image/jpeg").put("data", android.util.Base64.encodeToString(img, android.util.Base64.NO_WRAP))))
                }
                put(JSONObject().put("type", "text").put("text", t.text))
            } else t.text
            messages.put(JSONObject().put("role", if (t.user) "user" else "assistant").put("content", content))
        }
        val body = JSONObject().put("model", model).put("max_tokens", 32000).put("stream", true)
            .put("system", system()).put("output_config", JSONObject().put("effort", effort.v))
        // Server-side refusal fallback: a declined request is retried on a suitable model.
        val fallback = model != "claude-haiku-5-5"
        if (fallback) body.put("fallbacks", "default")
        if (web) body.put("tools", JSONArray().put(JSONObject().put("type", "web_search_20260209").put("name", "web_search").put("max_uses", 6)))
        val sb = StringBuilder()
        try {
            repeat(4) {
                body.put("messages", messages)
                val (stop, content) = stream(key, body, fallback) { d -> sb.append(d); onText(sb.toString()) }
                when (stop) {
                    // Web search loop paused: send the partial answer back and let Claude carry on.
                    "pause_turn" -> messages.put(JSONObject().put("role", "assistant").put("content", content))
                    "refusal" -> throw AiException("Claude declined this request. Rephrase it with the site context and try again.")
                    "max_tokens" -> { sb.append("\n\n_(Answer cut short — ask for a shorter section.)_"); onText(sb.toString()); return sb.toString() }
                    else -> return sb.toString()
                }
            }
        } catch (e: AiException) { throw e
        } catch (e: Exception) { throw AiException("Could not reach Claude. Check the internet connection. (${e.javaClass.simpleName}: ${e.message})") }
        return sb.toString()
    }

    /** One streaming request. Returns the stop reason and the assistant's content blocks. */
    private fun stream(key: String, body: JSONObject, fallback: Boolean, onDelta: (String) -> Unit): Pair<String, JSONArray> {
        val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 30_000; c.readTimeout = 600_000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("x-api-key", key)
        c.setRequestProperty("anthropic-version", "2023-06-01")
        if (fallback) c.setRequestProperty("anthropic-beta", "server-side-fallback-2026-07-01")
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code != 200) {
                val msg = runCatching { JSONObject(c.errorStream.bufferedReader().readText()).getJSONObject("error").getString("message") }.getOrDefault("")
                throw AiException(when (code) {
                    401 -> "The API key was rejected. Check it in Settings."
                    429 -> "Too many requests right now. Wait a minute and try again."
                    529 -> "Claude is overloaded right now. Try again in a few minutes."
                    else -> "Claude returned an error ($code): $msg"
                })
            }
            val blocks = ArrayList<JSONObject>()
            val json = HashMap<Int, StringBuilder>()
            var stop = ""
            c.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val ev = JSONObject(line.substring(5).trim())
                    when (ev.optString("type")) {
                        "content_block_start" -> {
                            val i = ev.getInt("index")
                            while (blocks.size <= i) blocks.add(JSONObject())
                            blocks[i] = ev.getJSONObject("content_block")
                        }
                        "content_block_delta" -> {
                            val i = ev.getInt("index"); val d = ev.getJSONObject("delta"); val b = blocks[i]
                            when (d.optString("type")) {
                                "text_delta" -> { val t = d.getString("text"); b.put("text", b.optString("text") + t); onDelta(t) }
                                "input_json_delta" -> json.getOrPut(i) { StringBuilder() }.append(d.getString("partial_json"))
                                "citations_delta" -> b.put("citations", (b.optJSONArray("citations") ?: JSONArray()).put(d.getJSONObject("citation")))
                                "thinking_delta" -> b.put("thinking", b.optString("thinking") + d.getString("thinking"))
                                "signature_delta" -> b.put("signature", d.getString("signature"))
                            }
                        }
                        "content_block_stop" -> {
                            val i = ev.getInt("index")
                            json.remove(i)?.let { s -> blocks[i].put("input", if (s.isBlank()) JSONObject() else JSONObject(s.toString())) }
                        }
                        "message_delta" -> ev.optJSONObject("delta")?.optString("stop_reason")?.takeIf { it.isNotEmpty() && it != "null" }?.let { stop = it }
                        "error" -> throw AiException("Claude returned an error: " + ev.optJSONObject("error")?.optString("message"))
                    }
                }
            }
            return stop to JSONArray(blocks)
        } finally { c.disconnect() }
    }

    fun ask(prompt: String, web: Boolean = false, images: List<ByteArray> = emptyList(), effort: Effort = Effort.MEDIUM, onText: (String) -> Unit = {}) =
        ask(listOf(Turn(true, prompt, images)), web, effort, onText)

    /** Pulls the first JSON array or object out of an answer. */
    fun extractJson(s: String): String {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(s)?.groupValues?.get(1)
        val body = fence ?: s
        val start = body.indexOfFirst { it == '[' || it == '{' }
        val end = body.indexOfLast { it == ']' || it == '}' }
        if (start < 0 || end <= start) throw AiException("Claude's answer did not contain the expected data. Try again.")
        return body.substring(start, end + 1)
    }
}
