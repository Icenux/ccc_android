package io.github.icenux.ccc.data

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `a newer version is a bigger number, not a longer text`() {
        assertTrue(Versions.isNewer("v0.10.0", "0.9.0"))
        assertTrue(Versions.isNewer("1.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.2.1", "1.2"))
        assertFalse(Versions.isNewer("1.2.0", "1.2"))
        assertFalse(Versions.isNewer("0.9.0", "0.10.0"))
    }

    @Test
    fun `a build made by hand or an odd tag never asks to be updated`() {
        assertFalse(Versions.isNewer("9.9.9", "0.0.0-dev"))
        assertFalse(Versions.isNewer("nightly", "1.0.0"))
        assertNull(Versions.parse("1.x"))
    }

    /** The rules ccc_TUI's own updater follows too: a copy of tests/fixtures/release-rules.json of the ccc_TUI repository (gradle runs in app/). */
    private val rules = Json.parseToJsonElement(
        File("src/test/resources/release-rules.json").readText()
    ).jsonObject

    @Test
    fun `versions follow the rules shared with ccc`() {
        for (case in rules["parse"]!!.jsonArray) {
            val (text, expected) = case.jsonArray
            val numbers = (expected as? JsonArray)?.map { it.jsonPrimitive.int }
            assertEquals(text.jsonPrimitive.content, numbers, Versions.parse(text.jsonPrimitive.content))
        }
        for (case in rules["newer"]!!.jsonArray) {
            val (latest, installed, expected) = case.jsonArray
            assertEquals(
                "${latest.jsonPrimitive.content} vs ${installed.jsonPrimitive.content}",
                expected.jsonPrimitive.boolean,
                Versions.isNewer(latest.jsonPrimitive.content, installed.jsonPrimitive.content),
            )
        }
    }

    @Test
    fun `releases are picked by the rules shared with ccc`() {
        for (case in rules["releases"]!!.jsonArray.map { it.jsonObject }) {
            val got = parseRelease(case["release"]!!.toString(), case["asset"]!!.jsonPrimitive.content)
            val expect = case["expect"]
            val name = case["name"]!!.jsonPrimitive.content
            if (expect is JsonObject) {
                assertEquals(name, expect["version"]!!.jsonPrimitive.content, got?.version)
                assertEquals(name, expect["url"]!!.jsonPrimitive.content, got?.apkUrl)
                assertEquals(name, expect["notes"]!!.jsonPrimitive.content, got?.notes)
            } else {
                assertNull(name, got)
            }
        }
    }

    private val release = """{"tag_name": "v1.2.0", "body": "Dashboard", "draft": false, "prerelease": false,
        "assets": [{"name": "notes.txt", "browser_download_url": "https://x/notes"},
                   {"name": "ccc.apk", "browser_download_url": "https://x/ccc.apk"}]}"""

    @Test
    fun `reads the release with the app attached`() {
        assertEquals(AppRelease("1.2.0", "https://x/ccc.apk", "Dashboard"), parseRelease(release))
    }

    private fun releaseWith(vararg names: String, tag: String = "v1.2.0") =
        """{"tag_name": "$tag", "body": "", "draft": false, "prerelease": false, "assets": [""" +
            names.joinToString(",") { """{"name": "$it", "browser_download_url": "https://x/$it"}""" } + "]}"

    @Test
    fun `the app is found by its file named after the version, or by the old name`() {
        assertEquals("ccc_1.2.0.apk", apkName("1.2.0"))
        assertEquals("ccc_1.2.0.apk", apkName("v1.2.0"))
        // both attached (the transition): the named one wins
        assertEquals("https://x/ccc_1.2.0.apk", parseAppRelease(releaseWith("ccc.apk", "ccc_1.2.0.apk"))?.apkUrl)
        assertEquals("https://x/ccc_1.2.0.apk", parseAppRelease(releaseWith("ccc_1.2.0.apk"))?.apkUrl)
        // a release from before the rename
        assertEquals("https://x/ccc.apk", parseAppRelease(releaseWith("ccc.apk"))?.apkUrl)
        assertEquals("1.2.0", parseAppRelease(releaseWith("ccc.apk", tag = "1.2.0"))?.version)
    }

    @Test
    fun `a file named after another version is not the app of this release`() {
        assertNull(parseAppRelease(releaseWith("ccc_1.1.0.apk")))
        assertNull(parseAppRelease(releaseWith("other.apk")))
    }

    @Test
    fun `a release without the app, a draft or garbage is no release`() {
        assertNull(parseRelease(release.replace("ccc.apk", "other.apk")))
        assertNull(parseRelease(release.replace("\"draft\": false", "\"draft\": true")))
        assertNull(parseRelease("""{"message": "Not Found"}"""))
    }

    @Test
    fun `asks GitHub without any token and downloads the app`() = runBlocking {
        var auth: String? = "unset"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/o/r/releases/latest") { exchange ->
            auth = exchange.requestHeaders.getFirst("Authorization")
            val bytes = release.replace("https://x/ccc.apk", "http://127.0.0.1:${server.address.port}/ccc.apk").toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.createContext("/ccc.apk") { exchange ->
            exchange.sendResponseHeaders(200, 8)
            exchange.responseBody.use { it.write("APKBYTES".toByteArray()) }
        }
        server.start()
        try {
            val api = ReleaseApi("o/r", base = "http://127.0.0.1:${server.address.port}")
            val latest = api.latest()!!
            val file = File.createTempFile("ccc", ".apk").apply { delete() }
            api.download(latest.apkUrl, file)
            assertEquals("1.2.0", latest.version)
            assertNull(auth)
            assertEquals("APKBYTES", file.readText())
            file.delete()
            Unit
        } finally {
            server.stop(0)
        }
    }
}
