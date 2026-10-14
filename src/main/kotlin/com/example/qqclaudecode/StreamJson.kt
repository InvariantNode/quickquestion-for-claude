package com.example.qqclaudecode

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Events we care about from `claude --output-format stream-json`. Everything else is ignored. */
sealed interface ClaudeEvent {
    /** A new text content block started; text blocks are separated by a blank line. */
    data object TextBlockStart : ClaudeEvent

    data class TextDelta(val text: String) : ClaudeEvent

    data class ToolUse(val id: String, val label: String) : ClaudeEvent

    /** End of a turn. Not necessarily the last line on stdout, because hook events can follow. */
    data class TurnEnd(val isError: Boolean, val message: String?) : ClaudeEvent

    /** The process exited. */
    data class Exited(val exitCode: Int?, val stderr: String) : ClaudeEvent
}

object StreamJson {
    private const val MAX_LABEL = 60

    fun parseLine(line: String): List<ClaudeEvent> {
        if (line.isBlank()) return emptyList()
        val obj = try {
            JsonParser.parseString(line) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        return when (obj.str("type")) {
            "stream_event" -> listOfNotNull(obj.obj("event")?.let(::parseStreamEvent))
            "assistant" -> parseAssistant(obj)
            "result" -> listOf(
                ClaudeEvent.TurnEnd(
                    isError = obj.get("is_error")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
                    message = obj.str("result"),
                ),
            )
            else -> emptyList() // system, hook, rate_limit, user, ...
        }
    }

    private fun parseStreamEvent(event: JsonObject): ClaudeEvent? = when (event.str("type")) {
        "content_block_delta" -> {
            val delta = event.obj("delta")
            if (delta?.str("type") == "text_delta") delta.str("text")?.let { ClaudeEvent.TextDelta(it) } else null
        }
        "content_block_start" ->
            if (event.obj("content_block")?.str("type") == "text") ClaudeEvent.TextBlockStart else null
        else -> null
    }

    /** From the full assistant message only `tool_use` blocks are used; text arrives through deltas. */
    private fun parseAssistant(obj: JsonObject): List<ClaudeEvent> {
        val content = obj.obj("message")?.array("content") ?: return emptyList()
        return content.mapNotNull { block ->
            val b = block as? JsonObject ?: return@mapNotNull null
            if (b.str("type") != "tool_use") return@mapNotNull null
            ClaudeEvent.ToolUse(b.str("id") ?: "", describeTool(b.str("name") ?: "tool", b.obj("input")))
        }
    }

    private fun describeTool(name: String, input: JsonObject?): String = when (name) {
        "Read" -> "Read ${shortPath(input?.str("file_path") ?: "")}".trim()
        "Grep" -> "Searched for \"${truncate(input?.str("pattern") ?: "")}\""
        "Glob" -> "Listed files matching ${truncate(input?.str("pattern") ?: "")}".trim()
        else -> name
    }

    private fun shortPath(p: String) = p.substringAfterLast('/').substringAfterLast('\\')

    private fun truncate(s: String) = if (s.length <= MAX_LABEL) s else s.take(MAX_LABEL - 1) + "…"

    private fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.array(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
}
