package com.parag.safetydesk

import android.content.Context
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.WebSearchTool20260209
import java.time.Duration

/** One turn of a conversation the app keeps itself (Claude remembers nothing between calls). */
data class Turn(val user: Boolean, val text: String, val images: List<ByteArray> = emptyList())

class AiException(message: String) : Exception(message)

/**
 * Talks to Claude with the safety-manager instructions file as the system prompt.
 * Blocking calls: run them off the main thread.
 */
class Ai(private val ctx: Context, private val store: Store) {
    private var client: AnthropicClient? = null
    private var clientKey = ""

    private val instructions: String by lazy { ctx.assets.open("agent_instructions.md").bufferedReader().readText() }

    private fun client(): AnthropicClient {
        val key = store.apiKey
        if (key.isBlank()) throw AiException("Add your Anthropic API key in Settings first.")
        if (client == null || key != clientKey) {
            client = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofMinutes(10)).build()
            clientKey = key
        }
        return client!!
    }

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
    fun ask(turns: List<Turn>, web: Boolean = false, effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM, onText: (String) -> Unit): String {
        val model = store.model
        val b = MessageCreateParams.builder()
            .model(model)
            .maxTokens(32000L)
            .system(system())
            .outputConfig(OutputConfig.builder().effort(effort).build())
        if (model != "claude-haiku-5-5") {
            // Server-side refusal fallback: a declined request is retried on a suitable model.
            b.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        if (web) b.addTool(WebSearchTool20260209.builder().maxUses(6L).build())
        for (t in turns) {
            if (t.user) {
                if (t.images.isEmpty()) b.addUserMessage(t.text)
                else b.addUserMessageOfBlockParams(t.images.map { img ->
                    ContentBlockParam.ofImage(ImageBlockParam.builder().source(
                        Base64ImageSource.builder().data(android.util.Base64.encodeToString(img, android.util.Base64.NO_WRAP))
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG).build()).build())
                } + ContentBlockParam.ofText(TextBlockParam.builder().text(t.text).build()))
            } else b.addAssistantMessage(t.text)
        }
        var params = b.build()
        val sb = StringBuilder()
        try {
            repeat(4) {
                val acc = MessageAccumulator.create()
                client().messages().createStreaming(params).use { stream ->
                    stream.stream().forEach { ev ->
                        acc.accumulate(ev)
                        ev.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { d ->
                            sb.append(d.text()); onText(sb.toString())
                        }
                    }
                }
                val msg: Message = acc.message()
                when (msg.stopReason().orElse(null)) {
                    StopReason.PAUSE_TURN -> params = params.toBuilder().addMessage(msg).build() // web search loop paused: resume
                    StopReason.REFUSAL -> throw AiException("Claude declined this request. Rephrase it with the site context and try again.")
                    StopReason.MAX_TOKENS -> { sb.append("\n\n_(Answer cut short — ask for a shorter section.)_"); onText(sb.toString()); return sb.toString() }
                    else -> return sb.toString()
                }
            }
        } catch (e: AiException) { throw e
        } catch (e: UnauthorizedException) { throw AiException("The API key was rejected. Check it in Settings.")
        } catch (e: RateLimitException) { throw AiException("Too many requests right now. Wait a minute and try again.")
        } catch (e: AnthropicServiceException) { throw AiException("Claude returned an error (${e.statusCode()}): ${e.message}")
        } catch (e: Exception) { throw AiException("Could not reach Claude. Check the internet connection. (${e.javaClass.simpleName}: ${e.message})") }
        return sb.toString()
    }

    fun ask(prompt: String, web: Boolean = false, images: List<ByteArray> = emptyList(), effort: OutputConfig.Effort = OutputConfig.Effort.MEDIUM, onText: (String) -> Unit = {}) =
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
