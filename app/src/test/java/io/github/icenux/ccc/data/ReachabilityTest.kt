package io.github.icenux.ccc.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReachabilityTest {
    @Test
    fun `a few failures in a row make the server unreachable`() {
        val reach = Reachability(limit = 3)
        assertFalse(reach.failed())
        assertFalse(reach.failed())
        assertTrue(reach.failed())
        assertTrue(reach.unreachable)
        assertTrue(reach.failed()) // stays so while it keeps failing
    }

    @Test
    fun `an answer between the failures starts the count again`() {
        val reach = Reachability(limit = 3)
        reach.failed()
        reach.failed()
        reach.reached()
        assertFalse(reach.failed())
        assertFalse(reach.unreachable)
    }

    @Test
    fun `reached clears the unreachable state`() {
        val reach = Reachability()
        repeat(Reachability.LIMIT) { reach.failed() }
        assertTrue(reach.unreachable)
        reach.reached()
        assertFalse(reach.unreachable)
    }

    @Test
    fun `nothing failed yet is reachable`() {
        assertFalse(Reachability().unreachable)
    }
}
