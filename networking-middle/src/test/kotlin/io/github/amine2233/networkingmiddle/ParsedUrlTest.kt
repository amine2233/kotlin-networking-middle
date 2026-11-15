package io.github.amine2233.networkingmiddle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParsedUrlTest {
    @Test
    fun `parses scheme host path query and fragment`() {
        assertEquals(
            ParsedUrl("https", "api.test", "/users/1", "b=2&a=1", "top"),
            parseUrlOrNull("https://api.test/users/1?b=2&a=1#top"),
        )
    }

    @Test
    fun `url without path has empty path and no query or fragment`() {
        val url = assertNotNull(parseUrlOrNull("https://api.test"))
        assertEquals("", url.path)
        assertNull(url.query)
        assertNull(url.fragment)
    }

    @Test
    fun `host drops userinfo and port`() {
        assertEquals("host", parseUrlOrNull("http://user@host:8080/x")?.host)
    }

    @Test
    fun `invalid urls return null`() {
        listOf(
            "https://api.test/a b",
            "api.test/users",
            "/users",
            "https:///users",
            "https://:8080/x",
            "https://api.test/<x>",
            "https://api.test/100%",
        ).forEach { assertNull(parseUrlOrNull(it), "expected <$it> to be rejected") }
    }
}
