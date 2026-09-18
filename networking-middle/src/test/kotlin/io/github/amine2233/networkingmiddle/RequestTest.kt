package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestTest {
    @Test
    fun `asUrlRequest joins base url and path without duplicate slashes`() =
        runTest {
            val request = RequestFactory.create(path = "/v1/users", method = HttpMethod.Get)
            assertEquals("https://api.test/v1/users", request.asUrlRequest("https://api.test/").url)
            assertEquals("https://api.test/v1/users", request.asUrlRequest("https://api.test").url)
        }

    @Test
    fun `asUrlRequest applies method headers parameters and body`() =
        runTest {
            val request =
                RequestFactory.create(
                    path = "search",
                    method = HttpMethod.Post,
                    parameters = RequestParametersFactory.queryParameters(mapOf("q" to "a b")),
                    body = RequestBodyFactory.stringBody("hello"),
                    headers = mapOf("X-Test" to "1"),
                )
            val urlRequest = request.asUrlRequest("https://api.test")
            assertEquals("https://api.test/search?q=a%20b", urlRequest.url)
            assertEquals(HttpMethod.Post, urlRequest.method)
            assertEquals("1", urlRequest.headers["X-Test"])
            assertEquals("hello", urlRequest.body?.decodeToString())
        }

    @Test
    fun `asUrlRequest wraps parameters and body failures`() =
        runTest {
            val boom = IllegalStateException("boom")
            val badParams = RequestFactory.create("/", HttpMethod.Get, parameters = RequestParameters { throw boom })
            assertFailsWith<ApiError.RequestParameters> { badParams.asUrlRequest("https://api.test") }

            val badBody = RequestFactory.create("/", HttpMethod.Get, body = RequestBody { throw boom })
            assertFailsWith<ApiError.Body> { badBody.asUrlRequest("https://api.test") }
        }

    @Test
    fun `asUrlRequest rejects invalid url`() =
        runTest {
            val request = RequestFactory.create(path = "a path with spaces", method = HttpMethod.Get)
            assertFailsWith<ApiError.InvalidUrl> { request.asUrlRequest("https://api.test") }
        }

    @Test
    fun `header helpers return new request and leave the original untouched`() {
        val original = RequestFactory.create("/", HttpMethod.Get)
        val bearer = original.authorised(bearer = "abc")
        val token = original.token("xyz")
        val custom = original.setHeader("K", "v")
        val removed = custom.setHeader("K", null)

        assertEquals("Bearer abc", bearer.headers["Authorization"])
        assertEquals("token xyz", token.headers["Authorization"])
        assertEquals("v", custom.headers["K"])
        assertNull(removed.headers["K"])
        assertEquals(emptyMap(), original.headers)
    }

    @Test
    fun `toAnyRequest copies all fields`() {
        val request = RequestFactory.create("/p", HttpMethod.Patch, headers = mapOf("a" to "b"))
        val any = request.toAnyRequest()
        assertEquals(request.path, any.path)
        assertEquals(request.method, any.method)
        assertEquals(request.headers, any.headers)
    }
}
