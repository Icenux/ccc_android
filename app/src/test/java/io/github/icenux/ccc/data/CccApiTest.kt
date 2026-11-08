package io.github.icenux.ccc.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** CccApi against a small fake of ccc's API (the JDK's own HTTP server). */
class CccApiTest {
    private data class Seen(val method: String, val uri: String, val auth: String?, val body: String)

    private lateinit var server: HttpServer
    private val seen = CopyOnWriteArrayList<Seen>()
    private val answers = mutableMapOf<String, Pair<Int, String>>()

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/") { exchange -> handle(exchange) }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().decodeToString()
        seen += Seen(exchange.requestMethod, exchange.requestURI.toString(), exchange.requestHeaders.getFirst("Authorization"), body)
        val path = exchange.requestURI.path.removePrefix("/api/v1/")
        if (path == "events") {
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use {
                it.write(": connected\n\nevent: changed\ndata: {\"conversations\": [3], \"removed\": [4]}\n\n".toByteArray())
                it.flush()
            }
            return
        }
        val (code, answer) = answers[path] ?: (404 to """{"error": "Not found."}""")
        val bytes = answer.toByteArray()
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun api(token: String = "secret") = CccApi("http://127.0.0.1:${server.address.port}", token)

    @Test
    fun `reads the git state of a conversation and ignores the context an older ccc still sends`() = runBlocking {
        answers["conversations"] = 200 to """[{"id": 1, "name": "shop", "path": "/p", "status": "idle",
            "delivery_mode": "interactive", "context": {"tokens": 186000, "percent": 93, "level": "danger"},
            "git": {"branch": "feature-x", "added": 12, "removed": 3, "ahead": 2, "behind": 0}},
            {"id": 2, "name": "docs", "path": "/d", "status": "idle", "delivery_mode": "headless",
            "context": null, "git": null}]"""

        val (shop, docs) = api().conversations()

        assertEquals("feature-x +12−3 ↑2", shop.git?.text)
        assertNull(docs.git)
    }

    @Test
    fun `reads the latest goal loop of a conversation for the Dashboard`() = runBlocking {
        answers["conversations"] = 200 to """[{"id": 1, "name": "shop", "path": "/p", "status": "running",
            "delivery_mode": "interactive", "goal": {"id": 7, "status": "running", "status_label": "running",
            "phase_label": "Claude works on round 3", "round": 3, "max_rounds": 5, "finished_at": null}},
            {"id": 2, "name": "docs", "path": "/d", "status": "idle", "delivery_mode": "headless", "goal": null},
            {"id": 3, "name": "api", "path": "/a", "status": "idle", "delivery_mode": "headless",
            "goal": {"id": 8, "status": "passed", "status_label": "met", "phase_label": "", "round": 2, "max_rounds": 5}}]"""

        val (shop, docs, api) = api().conversations()

        assertEquals(true, shop.goal?.running)
        assertEquals("◎", shop.goal?.icon)
        assertEquals("Claude works on round 3", shop.goal?.phaseLabel)
        assertEquals(3, shop.goal?.round)
        assertEquals(5, shop.goal?.maxRounds)
        assertNull(docs.goal)
        assertEquals("✅", api.goal?.icon)
        assertEquals("met", api.goal?.statusLabel)
    }

    @Test
    fun `reads the update a new ccc version brings and applies it`() = runBlocking {
        answers["status"] = 200 to """{"version": "0.1.0", "language": "en", "daemon_running": true, "conversations": 1,
            "running": 0, "needs_approval": 0, "android_build": 7,
            "update": {"available": true, "behind": 2, "remote_head": "abc1234", "commits": ["abc1234 one", "def5678 two"],
            "android": true, "error": null, "checked_at": "2026-10-30T12:00:00", "can_apply": false,
            "reason": "A task is running (shop)"}}"""
        val status = api().status()
        assertEquals(7, status.androidBuild)
        assertEquals(2, status.update?.behind)
        assertEquals("A task is running (shop)", status.update?.reason)
        assertEquals(false, status.update?.canApply)

        answers["update"] = 200 to """{"ok": true, "message": "Updated to abc1234 — ccc is restarting."}"""
        assertEquals("Updated to abc1234 — ccc is restarting.", api().applyUpdate().message)
        assertEquals("POST", seen.last().method)
    }

    @Test
    fun `knows when ccc must be restarted after an upgrade and restarts it`() = runBlocking {
        answers["status"] = 200 to """{"version": "0.1.3", "language": "en", "daemon_running": true, "conversations": 1,
            "running": 0, "needs_approval": 0, "restart_needed": true}"""
        assertEquals(true, api().status().restartNeeded)
        // an older ccc says nothing about it
        answers["status"] = 200 to """{"version": "0.1.2", "language": "en", "daemon_running": true, "conversations": 1,
            "running": 0, "needs_approval": 0}"""
        assertEquals(false, api().status().restartNeeded)

        answers["restart"] = 200 to """{"ok": true, "message": "ccc is restarting."}"""
        assertEquals("ccc is restarting.", api().restart().message)
        assertEquals("POST", seen.last().method)
    }

    @Test
    fun `reads the list with the token`() = runBlocking {
        answers["conversations"] = 200 to """[{"id": 1, "name": "shop", "path": "/p", "status": "blocked_error",
            "delivery_mode": "interactive", "pending": 2, "needs_approval": true, "last_error": "boom",
            "group": {"key": "own:Release", "title": "Release"}, "something_new": 1}]"""

        val list = api().conversations()

        assertEquals("Bearer secret", seen.single().auth)
        assertEquals(1, list.size)
        assertEquals(ConversationStatus.BLOCKED_ERROR, list[0].state)
        assertEquals(2, list[0].pending)
        assertEquals("Release", list[0].group?.title)
    }

    @Test
    fun `pairing trades the code for a token without sending one`() = runBlocking {
        answers["pair"] = 201 to """{"token": "t0k", "device_id": 4, "name": "Pixel", "version": "0.1.0"}"""

        val paired = CccApi("http://127.0.0.1:${server.address.port}").pair("one-time", "Pixel")

        assertEquals("t0k", paired.token)
        assertEquals(4, paired.deviceId)
        assertNull(seen.single().auth)
        assertEquals("POST", seen.single().method)
        assertTrue(seen.single().body.contains("\"code\":\"one-time\""))
    }

    @Test
    fun `a wrong pairing code is an error with ccc's own text`() = runBlocking {
        answers["pair"] = 401 to """{"error": "The pairing code is wrong, used or has run out."}"""
        try {
            CccApi("http://127.0.0.1:${server.address.port}").pair("nope", "Pixel")
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
    }

    @Test
    fun `a refused token is reported as signed out, a wrong pairing code is not`() = runBlocking {
        answers["status"] = 401 to """{"error": "Missing or wrong token."}"""
        answers["pair"] = 401 to """{"error": "The pairing code is wrong, used or has run out."}"""
        var refused = 0
        val base = "http://127.0.0.1:${server.address.port}"
        try {
            CccApi(base, "revoked", onUnauthorized = { refused++ }).status()
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
        assertEquals(1, refused)
        try {
            CccApi(base, onUnauthorized = { refused++ }).pair("nope", "Pixel")
            fail("expected an error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
        assertEquals(1, refused)
    }

    @Test
    fun `lists the devices and signs one out`() = runBlocking {
        answers["devices"] = 200 to """{"devices": [{"id": 1, "name": "Pixel", "last_seen_at": "2026-10-30T12:00:00",
            "last_ip": "100.64.0.7", "current": true}, {"id": 2, "name": "Tablet", "created_at": "2026-10-29T08:00:00"}]}"""
        answers["devices/2"] = 200 to """{"ok": true}"""

        val devices = api().devices()
        api().removeDevice(2)

        assertEquals(listOf("Pixel", "Tablet"), devices.map { it.name })
        assertTrue(devices[0].current)
        assertEquals(false, devices[1].current)
        assertNull(devices[1].lastSeenAt)
        assertEquals("DELETE", seen.last().method)
    }

    @Test
    fun `reads the history of the signed out devices with when and how`() = runBlocking {
        answers["devices"] = 200 to """{"devices": [{"id": 1, "name": "Pixel", "current": true, "revoked_at": null, "revoked_by": null},
            {"id": 2, "name": "Tablet", "revoked_at": "2026-10-31T11:00:00", "revoked_by": "computer"}]}"""

        val devices = api().devices(history = true)

        assertTrue(seen.last().uri.contains("all=1"))
        assertNull(devices[0].revokedAt)
        assertEquals("2026-10-31T11:00:00", devices[1].revokedAt)
        assertEquals("computer", devices[1].revokedBy)
        api().devices()
        assertEquals(false, seen.last().uri.contains("all=1"))
    }

    @Test
    fun `messages ask for what is new`() = runBlocking {
        answers["conversations/7/messages"] = 200 to """{"total": 3, "messages": [
            {"index": 2, "kind": "assistant_text", "text": "Done", "time": "2026-10-21T10:00:00+00:00"}]}"""

        val messages = api().messages(7, after = 1)

        assertEquals("/api/v1/conversations/7/messages?after=1&limit=100", seen.single().uri)
        assertEquals(listOf("Done"), messages.messages.map { it.text })
    }

    @Test
    fun `send and decide post JSON`() = runBlocking {
        answers["conversations/7/messages"] = 201 to """{"id": 9, "text": "go on"}"""
        answers["permissions/5"] = 200 to """{"id": 5, "decision": "always"}"""

        api().send(7, "go on")
        api().decide(5, "always")

        assertEquals(listOf("POST", "POST"), seen.map { it.method })
        assertEquals("""{"text":"go on"}""", seen[0].body)
        assertEquals("""{"decision":"always"}""", seen[1].body)
    }

    @Test
    fun `an error carries ccc's message and code`() = runBlocking {
        answers["status"] = 401 to """{"error": "Missing or wrong API token."}"""
        try {
            api("wrong").status()
            fail("no error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
            assertEquals("Missing or wrong API token.", e.message)
        }
    }

    @Test
    fun `unknown usage is null`() = runBlocking {
        answers["usage"] = 200 to "null"
        assertNull(api().usage())
    }

    @Test
    fun `the events stream says connected, then what changed`() = runBlocking {
        val events = api().events().take(2).toList()
        assertEquals(ServerEvent.Connected, events[0])
        assertEquals(ServerEvent.Changed(Change(listOf(3), listOf(4))), events[1])
    }

    @Test
    fun `a refused events stream is an error`() = runBlocking {
        server.removeContext("/api/v1/")
        server.createContext("/api/v1/") { exchange ->
            val bytes = """{"error": "Missing or wrong API token."}""".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        try {
            api().events().first()
            fail("no error")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
    }

    @Test
    fun `the address as typed`() {
        assertEquals("https://pc.tail.ts.net/api/v1/", CccApi.baseUrl("pc.tail.ts.net").toString())
        assertEquals("https://pc.tail.ts.net/api/v1/", CccApi.baseUrl(" https://pc.tail.ts.net/ ").toString())
        assertEquals("http://10.0.2.2:8737/api/v1/", CccApi.baseUrl("http://10.0.2.2:8737/api/v1").toString())
        assertNull(CccApi.baseUrl(""))
        assertTrue(CccApi.baseUrl("not an address") == null)
    }

    // --- Claude Code agents (the files in .claude/agents) -----------------------------------------------

    @Test
    fun `lists the agents of a folder with the folder as the query and none for the user's own`() = runBlocking {
        answers["agents"] = 200 to """[{"name": "reviewer", "description": "Reviews", "scope": "project", "tools": ["Read", "Grep"],
            "model": "haiku", "problem": "", "overridden": false, "file": "reviewer.md"},
            {"name": "bad", "description": "", "scope": "user", "tools": null, "model": "", "problem": "no description",
            "overridden": false, "file": "bad.md"}]"""

        val (reviewer, bad) = api().subagents("/p")
        api().subagents("")

        assertEquals(listOf("Read", "Grep"), reviewer.tools)
        assertNull(bad.tools)  // null: every tool
        assertEquals("no description", bad.problem)
        assertEquals("/api/v1/agents?path=%2Fp", seen[0].uri)
        assertEquals("/api/v1/agents", seen[1].uri)
    }

    @Test
    fun `opens one agent whole and changes it with the version it was opened with`() = runBlocking {
        answers["agents/project/reviewer"] = 200 to """{"name": "reviewer", "description": "Reviews", "scope": "project",
            "tools": ["Read"], "model": "haiku", "prompt": "You review.", "version": "12-34"}"""
        val agent = api().subagent("project", "reviewer", "/p")
        assertEquals("You review.", agent.prompt)

        api().editSubagent(agent.copy(description = "New", tools = null, model = "", prompt = "Changed"), "/p")

        val change = seen.last()
        assertEquals("POST", change.method)
        assertEquals("/api/v1/agents/project/reviewer", change.uri)
        for (part in listOf(""""version":"12-34"""", """"tools":null""", """"description":"New"""", """"path":"/p"""", """"prompt":"Changed"""")) {
            assertTrue("$part in ${change.body}", change.body.replace(" ", "").contains(part.replace(" ", "")))
        }
    }

    @Test
    fun `creates an agent with its tools as a list and removes one with a DELETE`() = runBlocking {
        answers["agents"] = 201 to """{"name": "checker", "scope": "project", "new_folder": true, "version": "1-2"}"""
        answers["agents/project/checker"] = 200 to """{"removed": "checker"}"""

        val made = api().createSubagent(Subagent("checker", "Checks", "project", listOf("Read", "Grep"), "haiku", prompt = "You check."), "/p")
        val removed = api().deleteSubagent("project", "checker", "/p")

        assertTrue(made.newFolder)
        val body = seen[0].body.replace(" ", "")
        assertTrue(body, body.contains(""""scope":"project"""") && body.contains(""""name":"checker"""") && body.contains(""""tools":["Read","Grep"]"""))
        assertEquals("checker", removed.removed)
        assertEquals("DELETE", seen[1].method)
        assertEquals("/api/v1/agents/project/checker?path=%2Fp", seen[1].uri)
    }

    @Test
    fun `reads the templates and the wizard's choices and the prompt ccc writes`() = runBlocking {
        answers["agent-options"] = 200 to """{"templates": [{"key": "test-runner", "title": "Test runner", "description": "Runs tests",
            "tools": ["Bash"], "model": "haiku", "prompt": "You run tests."}],
            "access": [{"key": "read", "label": "Only read", "tools": ["Read", "Grep", "Glob"]}],
            "effort": [{"key": "fast", "label": "Fast", "model": "haiku"}], "models": ["", "sonnet"],
            "default_access": "read", "default_effort": "balanced"}"""
        answers["agents/prompt"] = 200 to """{"prompt": "You check.", "by": "claude"}"""

        val options = api().subagentOptions()
        val prompt = api().subagentPrompt("Checks texts", "read", claude = true)

        assertEquals("test-runner", options.templates.single().key)
        assertEquals(listOf("Read", "Grep", "Glob"), options.access.single().tools)
        assertEquals("balanced", options.defaultEffort)
        assertEquals("claude", prompt.by)
        assertTrue(seen.last().body.replace(" ", "").contains(""""claude":true"""))
    }

    @Test
    fun `a refusal of a change reaches the user as ccc's own message`() = runBlocking {
        answers["agents"] = 403 to """{"error": "This device may not do that: it is operate, this needs admin."}"""
        try {
            api().createSubagent(Subagent("x", "d", "user", null, "", prompt = "p"), "")
            fail("expected an ApiException")
        } catch (e: ApiException) {
            assertEquals(403, e.code)
            assertTrue(e.message!!.contains("needs admin"))
        }
    }
}
