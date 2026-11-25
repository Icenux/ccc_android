package io.github.icenux.ccc.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubagentRunsTest {
    private fun run(id: String, parent: String = "", start: String = "10:00:00", ms: Long = 1000, status: String = "done") =
        SubagentRun(id, parent, started = "2026-11-01T${start}Z", durationMs = ms, status = status)

    @Test
    fun theTreePutsEveryRunUnderItsParent() {
        val runs = listOf(run("a"), run("b", "a"), run("c"), run("d", "b"), run("lost", "gone"))
        assertEquals(listOf("a", "b", "d", "c", "lost"), SubagentTimeline.treeOrder(runs).map { it.id })
    }

    @Test
    fun barsShareOneAxisAndOverlapWhenRunsDid() {
        val bars = SubagentTimeline.bars(listOf(run("a", ms = 50_000), run("b", start = "10:00:25", ms = 75_000)))
        assertEquals(0f, bars.getValue("a").from, 0.001f)
        assertEquals(0.5f, bars.getValue("a").to, 0.001f)
        assertEquals(0.25f, bars.getValue("b").from, 0.001f)
        assertEquals(1f, bars.getValue("b").to, 0.001f)
        assertTrue(bars.getValue("b").from < bars.getValue("a").to)
    }

    @Test
    fun durationsAndTokensAreShort() {
        assertEquals("4.9 s", SubagentTimeline.duration(4941))
        assertEquals("7 min 3 s", SubagentTimeline.duration(423_000))
        assertEquals("950", SubagentTimeline.tokens(950))
        assertEquals("1.4k", SubagentTimeline.tokens(1400))
        assertEquals("186k", SubagentTimeline.tokens(186_400))
    }

    @Test
    fun theApiAnswerIsRead() {
        val text = """{"summary":{"total":1,"running":1,"errors":0,"span_ms":5000,"tokens":900},
            "runs":[{"id":"a1","parent_id":"","kind":"Explore","description":"Find","status":"running","started":"2026-11-01T10:00:00+00:00",
            "duration_ms":5000,"tokens":900,"tool_count":2,"model":"claude-sonnet-5-5","depth":1,"background":true,"new_field":1,
            "steps":[{"index":0,"tool":"Bash","arg":"ls","offset_ms":1200,"duration_ms":null,"error":"","agent_id":"b2"}]}]}"""
        val runs = Json { ignoreUnknownKeys = true }.decodeFromString(SubagentRuns.serializer(), text)
        assertEquals(1, runs.summary.running)
        assertTrue(runs.runs[0].running)
        assertEquals("b2", runs.runs[0].steps[0].agentId)
        assertEquals(null, runs.runs[0].steps[0].durationMs)
    }
}
