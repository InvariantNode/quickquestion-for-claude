package io.github.invariantnode.quickquestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamJsonTest {
    @Test
    fun textDelta() {
        val line = """{"type":"stream_event","event":{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}}"""
        assertEquals(listOf<ClaudeEvent>(ClaudeEvent.TextDelta("Hi")), StreamJson.parseLine(line))
    }

    @Test
    fun thinkingDeltaIsIgnored() {
        val line = """{"type":"stream_event","event":{"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"hm"}}}"""
        assertTrue(StreamJson.parseLine(line).isEmpty())
    }

    @Test
    fun textBlockStart() {
        val line = """{"type":"stream_event","event":{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}}"""
        assertEquals(listOf<ClaudeEvent>(ClaudeEvent.TextBlockStart), StreamJson.parseLine(line))
    }

    @Test
    fun toolBlockStartIsNotATextBlock() {
        val line = """{"type":"stream_event","event":{"type":"content_block_start","content_block":{"type":"tool_use","id":"t1","name":"Read"}}}"""
        assertTrue(StreamJson.parseLine(line).isEmpty())
    }

    @Test
    fun assistantToolUseLabels() {
        val line = """{"type":"assistant","message":{"content":[
            {"type":"text","text":"ignored"},
            {"type":"tool_use","id":"a","name":"Read","input":{"file_path":"/p/src/Foo.kt"}},
            {"type":"tool_use","id":"b","name":"Grep","input":{"pattern":"needle"}},
            {"type":"tool_use","id":"c","name":"Glob","input":{"pattern":"*.kt"}}]}}""".replace("\n", "")
        val labels = StreamJson.parseLine(line).map { (it as ClaudeEvent.ToolUse).label }
        assertEquals(listOf("Read Foo.kt", "Searched for \"needle\"", "Listed files matching *.kt"), labels)
    }

    @Test
    fun longGrepPatternIsTruncated() {
        val pattern = "x".repeat(200)
        val line = """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"a","name":"Grep","input":{"pattern":"$pattern"}}]}}"""
        val label = (StreamJson.parseLine(line).single() as ClaudeEvent.ToolUse).label
        assertTrue(label.length < 80 && label.contains("…"))
    }

    @Test
    fun result() {
        val ok = StreamJson.parseLine("""{"type":"result","subtype":"success","is_error":false,"result":"done"}""")
        assertEquals(listOf<ClaudeEvent>(ClaudeEvent.TurnEnd(false, "done")), ok)
        val err = StreamJson.parseLine("""{"type":"result","subtype":"error","is_error":true,"result":"boom"}""")
        assertEquals(listOf<ClaudeEvent>(ClaudeEvent.TurnEnd(true, "boom")), err)
    }

    @Test
    fun noiseIsIgnored() {
        listOf(
            """{"type":"system","subtype":"init"}""",
            """{"type":"system","subtype":"hook_started"}""",
            """{"type":"rate_limit_event"}""",
            "not json",
            "",
        ).forEach { assertTrue(it, StreamJson.parseLine(it).isEmpty()) }
    }
}
