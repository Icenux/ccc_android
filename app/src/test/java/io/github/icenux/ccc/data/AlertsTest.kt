package io.github.icenux.ccc.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertsTest {
    private val bash = Permission(5, 1, "Bash", conversation = "shop-api")
    private val error = Waiting(3, "data-pipeline", "error", "error")
    private val approvalWaiting = Waiting(1, "shop-api", "approval", "waits for approval")

    @Test
    fun `new approvals and conversations that need you are shown once`() {
        val first = Alerts.changes(listOf(bash), listOf(approvalWaiting, error), emptySet())
        assertEquals(listOf(Alert.Approval(bash), Alert.NeedsYou(error)), first.show)
        assertEquals(emptySet<String>(), first.gone)

        val again = Alerts.changes(listOf(bash), listOf(approvalWaiting, error), first.show.map { it.key }.toSet())
        assertEquals(emptyList<Alert>(), again.show)
    }

    @Test
    fun `an answered approval or a fixed conversation goes away`() {
        val shown = setOf("p:5", "w:3:error")
        val after = Alerts.changes(emptyList(), emptyList(), shown)
        assertEquals(shown, after.gone)
        // Needs you again later: shown again, since it was taken away.
        assertEquals(listOf(Alert.NeedsYou(error)), Alerts.changes(emptyList(), listOf(error), emptySet()).show)
    }

    @Test
    fun `a new ccc version is told once, and again for the next one`() {
        val update = UpdateInfo(available = true, behind = 3, remoteHead = "a1b2c3d")
        val first = Alerts.changes(emptyList(), emptyList(), emptySet(), update)
        assertEquals(listOf(Alert.Update(3, "a1b2c3d")), first.show)
        assertEquals(emptyList<Alert>(), Alerts.changes(emptyList(), emptyList(), first.show.map { it.key }.toSet(), update).show)
        val next = update.copy(remoteHead = "e4f5a6b", behind = 4)
        assertEquals(listOf(Alert.Update(4, "e4f5a6b")), Alerts.changes(emptyList(), emptyList(), setOf("u:a1b2c3d"), next).show)
        // Updated: it goes away.
        assertEquals(setOf("u:a1b2c3d"), Alerts.changes(emptyList(), emptyList(), setOf("u:a1b2c3d"), UpdateInfo()).gone)
        assertEquals(emptyList<Alert>(), Alerts.changes(emptyList(), emptyList(), emptySet(), UpdateInfo(available = true)).show)
    }

    @Test
    fun `kinds switched off in the settings are not shown`() {
        val kinds = setOf(AlertKind.APPROVAL)
        val changes = Alerts.changes(listOf(bash), listOf(error), emptySet(), kinds = kinds)
        assertEquals(listOf<Alert>(Alert.Approval(bash)), changes.show)
        // Switched off while shown: it is taken away.
        assertEquals(setOf("w:3:error"), Alerts.changes(emptyList(), listOf(error), setOf("w:3:error"), kinds = kinds).gone)
    }

    @Test
    fun `a used up usage window is told once, for the 4 hour and the weekly one, and goes away with the reset`() {
        val out = Usage(fiveHour = UsageWindow(100.0, "t1"), sevenDay = UsageWindow(100.0, "t2"))
        val first = Alerts.changes(emptyList(), emptyList(), emptySet(), usage = out)
        assertEquals(listOf(Alert.UsageLimit("5h", "t1"), Alert.UsageLimit("7d", "t2")), first.show)
        assertEquals(emptyList<Alert>(), Alerts.changes(emptyList(), emptyList(), first.show.map { it.key }.toSet(), usage = out).show)
        val reset = Usage(fiveHour = UsageWindow(3.0, "t3"), sevenDay = UsageWindow(100.0, "t2"))
        assertEquals(setOf("l:5h"), Alerts.changes(emptyList(), emptyList(), setOf("l:5h", "l:7d"), usage = reset).gone)
        assertEquals(emptyList<Alert>(), Alerts.changes(emptyList(), emptyList(), emptySet(), usage = Usage(fiveHour = UsageWindow(99.0))).show)
    }

    @Test
    fun `a switched off limit kind silences the usage alert too`() {
        val out = Usage(fiveHour = UsageWindow(100.0))
        assertEquals(emptyList<Alert>(), Alerts.changes(emptyList(), emptyList(), emptySet(), kinds = setOf("done"), usage = out).show)
    }
}
