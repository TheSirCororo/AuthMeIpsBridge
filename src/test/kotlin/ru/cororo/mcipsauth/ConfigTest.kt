package ru.cororo.mcipsauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTest {
    private fun parse(url: String?, key: String? = "key") = Config.parse(url, key, 3, 1)

    @Test
    fun `accepts https url and strips trailing slash`() {
        val config = parse(" https://forum.example.com/ ")
        assertEquals("https://forum.example.com", config.forumUrl)
        assertFalse(config.isInsecure)
    }

    @Test
    fun `accepts url with path`() {
        assertEquals("https://example.com/forum", parse("https://example.com/forum/").forumUrl)
    }

    @Test
    fun `flags plain http as insecure`() {
        assertTrue(parse("http://forum.example.com").isInsecure)
    }

    @Test
    fun `rejects unusable urls`() {
        for (url in listOf(null, "", "   ", "forum.example.com", "ftp://example.com", "file:///etc/passwd", "https://", "https://exa mple.com", "https://example.com/?a=b")) {
            assertFailsWith<IllegalArgumentException>(url.toString()) { parse(url) }
        }
    }

    @Test
    fun `rejects missing or placeholder api key`() {
        for (key in listOf(null, "", " ", Config.PLACEHOLDER_API_KEY)) {
            assertFailsWith<IllegalArgumentException>(key.toString()) { parse("https://example.com", key) }
        }
    }
}
