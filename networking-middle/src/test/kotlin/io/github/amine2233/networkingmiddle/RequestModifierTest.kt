package io.github.amine2233.networkingmiddle

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestModifierTest {
    private val request = RequestFactory.create("/", HttpMethod.Get)

    @Test
    fun `bearerToken sets Authorization header`() {
        val modified = RequestModifierFactory.bearerToken("abc").modify("https://api.test", request)
        assertEquals("Bearer abc", modified.headers["Authorization"])
    }

    @Test
    fun `token sets Authorization header`() {
        val modified = RequestModifierFactory.token("abc").modify("https://api.test", request)
        assertEquals("token abc", modified.headers["Authorization"])
    }

    @Test
    fun `header sets a custom header`() {
        val modified = RequestModifierFactory.header("X-Key", "v").modify("https://api.test", request)
        assertEquals("v", modified.headers["X-Key"])
    }
}
