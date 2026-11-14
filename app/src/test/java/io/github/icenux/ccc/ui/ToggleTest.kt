package io.github.icenux.ccc.ui

import io.github.icenux.ccc.data.LaunchGroup
import io.github.icenux.ccc.data.LaunchOption
import org.junit.Assert.assertEquals
import org.junit.Test

/** The New conversation options behave as launch_options.toggle in ccc. */
class ToggleTest {
    private fun group(key: String, exclusive: Boolean, required: Boolean, vararg keys: String) =
        LaunchGroup(key, key, exclusive, required, keys.map { LaunchOption(it, it) })

    private val session = group("session", true, true, "session_new", "session_continue", "session_from_pr")
    private val extra = group("session_extra", false, false, "fork")
    private val model = group("model", true, false, "model_opus", "model_sonnet")
    private val mcp = group("mcp", false, false, "mcp_select")
    private val groups = listOf(session, extra, model, mcp)

    @Test
    fun `an exclusive group keeps one`() {
        val picked = toggle(model, listOf("session_new", "model_opus"), "model_sonnet", groups)
        assertEquals(listOf("session_new", "model_sonnet"), picked)
    }

    @Test
    fun `a required group can't be emptied, an optional one can`() {
        assertEquals(listOf("session_new"), toggle(session, listOf("session_new"), "session_new", groups))
        assertEquals(listOf("session_new"), toggle(model, listOf("session_new", "model_opus"), "model_opus", groups))
    }

    @Test
    fun `a new session drops the fork`() {
        val forked = toggle(extra, listOf("session_continue"), "fork", groups)
        assertEquals(listOf("session_continue", "fork"), forked)
        assertEquals(listOf("session_new"), toggle(session, forked, "session_new", groups))
    }

    @Test
    fun `ticking an MCP server turns the mode on, unticking the mode forgets the servers`() {
        val one = toggle(mcp, listOf("session_new"), "mcp:docs", groups)
        assertEquals(listOf("session_new", "mcp_select", "mcp:docs"), one)
        val two = toggle(mcp, one, "mcp:db", groups)
        assertEquals(listOf("session_new", "mcp_select", "mcp:db", "mcp:docs"), two)
        assertEquals(listOf("session_new", "mcp_select", "mcp:db"), toggle(mcp, two, "mcp:docs", groups))
        assertEquals(listOf("session_new"), toggle(mcp, two, "mcp_select", groups))
    }

    private val plugins = group("plugins", false, false, "plugins_select")
    private val agent = group("agent", false, false, "agent_select")
    private val withMore = groups + plugins + agent

    @Test
    fun `ticking a plugin turns the mode on, unticking the mode forgets the plugins`() {
        val one = toggle(plugins, listOf("session_new"), "plugin:a@m", withMore)
        assertEquals(listOf("session_new", "plugins_select", "plugin:a@m"), one)
        assertEquals(listOf("session_new"), toggle(plugins, one, "plugins_select", withMore))
    }

    @Test
    fun `one agent at most, ticking it again drops it`() {
        val one = toggle(agent, listOf("session_new"), "agent:reviewer", withMore)
        assertEquals(listOf("session_new", "agent_select", "agent:reviewer"), one)
        assertEquals(listOf("session_new", "agent_select", "agent:tester"), toggle(agent, one, "agent:tester", withMore))
        assertEquals(listOf("session_new", "agent_select"), toggle(agent, one, "agent:reviewer", withMore))
        assertEquals(listOf("session_new"), toggle(agent, one, "agent_select", withMore))
    }

    private val permissions = group("permissions", true, true, "perm_skip", "perm_manual")
    private val launch = io.github.icenux.ccc.data.LaunchOptions(listOf(session, permissions), listOf("session_new", "perm_manual"))

    @Test
    fun `a saved choice with Bypass permissions falls back to the default mode while it is not allowed`() {
        assertEquals(listOf("session_new", "perm_manual"), usable(listOf("session_new", "perm_skip"), launch))
        assertEquals(listOf("session_new", "perm_skip"), usable(listOf("session_new", "perm_skip"), launch.copy(default = listOf("session_new", "perm_skip"))))
    }
}
