package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InterceptorTest {
    private val request = RequestFactory.create("/ping", HttpMethod.Get)
    private val next: Next = { _, _ -> ResponseDefault(UrlRequest("https://api.test/ping"), 200, "pong".encodeToByteArray()) }

    @Test
    fun `interceptorLogger prints the response description and returns it`() =
        runTest {
            val logs = mutableListOf<String>()
            val response = InterceptorFactory.interceptorLogger { logs += it }.intercept("https://api.test", request, next)
            assertEquals(200, response.statusCode)
            assertEquals(1, logs.size)
            assertTrue("StatusCode: 200" in logs.first())
            assertTrue("pong" in logs.first())
        }

    @Test
    fun `interceptorTimeLogger prints elapsed seconds`() =
        runTest {
            val logs = mutableListOf<String>()
            val response = InterceptorFactory.interceptorTimeLogger { logs += it }.intercept("https://api.test", request, next)
            assertEquals(200, response.statusCode)
            assertTrue(Regex("""\[Request]\[Time] \d+\.\d{2} seconds""").matches(logs.single()), logs.single())
        }
}
