package io.github.icenux.ccc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ElapsedTest {
    private fun m(i: Int, kind: String, t: String) = Message(i, kind, "x", "2026-11-11T10:$t+00:00")

    @Test
    fun finishedTurnsGetTheirDuration() {
        val messages = listOf(
            m(0, "human", "00:00"), m(1, "assistant_text", "00:20"), m(2, "tool_call", "00:40"), m(3, "assistant_text", "02:14"),
            m(4, "human", "05:00"), m(5, "assistant_text", "05:09"),
        )
        assertEquals(mapOf(3 to 134L, 5 to 9L), Elapsed.turns(messages, running = false))
    }

    @Test
    fun runningTurnIsLeftToTheClock() {
        val messages = listOf(m(0, "human", "00:00"), m(1, "assistant_text", "00:10"), m(2, "human", "01:00"), m(3, "assistant_text", "01:05"))
        assertEquals(mapOf(1 to 10L), Elapsed.turns(messages, running = true))
    }

    @Test
    fun text() {
        assertEquals("12 s", Elapsed.text(12))
        assertEquals("2 min 14 s", Elapsed.text(134))
        assertEquals("1 h 05 min", Elapsed.text(3900))
    }
}
