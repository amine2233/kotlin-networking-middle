package io.github.amine2233.networkingmiddle

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResponseTest {
    @Serializable
    data class User(
        val id: Int,
        val name: String,
    )

    private val urlRequest = UrlRequest("https://api.test/users/1?b=2&a=1", HttpMethod.Get, mapOf("H" to "v"))

    private fun response(
        status: Int = 200,
        body: String? = """{"id":1,"name":"amine"}""",
        error: Throwable? = null,
    ): Response = ResponseDefault(urlRequest, status, body?.encodeToByteArray(), mapOf("Content-Type" to listOf("application/json")), error)

    @Test
    fun `decode returns the model`() {
        assertEquals(User(1, "amine"), response().decode<User>())
    }

    @Test
    fun `decode throws when body is null`() {
        assertFailsWith<ApiError.Response> { response(body = null).decode<User>() }
    }

    @Test
    fun `decodeResult wraps parser failures`() {
        val result = response(body = "not json").decodeResult<User>()
        assertIs<ApiError.Parser>(result.exceptionOrNull())
        assertTrue(response().decodeResult<User>().isSuccess)
        assertIs<ApiError.Response>(response(body = null).decodeResult<User>().exceptionOrNull())
    }

    @Test
    fun `validate returns self or throws the error`() {
        val ok = response()
        assertSame(ok, ok.validate())
        assertFailsWith<IllegalStateException> { response(error = IllegalStateException()).validate() }
    }

    @Test
    fun `result and mapError reflect the error`() {
        assertTrue(response().result().isSuccess)
        val mapped = response(error = IllegalStateException("x")).mapError { ApiError.Client(it.message ?: "") }
        assertIs<ApiError.Client>(mapped.exceptionOrNull())
    }

    @Test
    fun `status helpers throw only on their code`() {
        assertFailsWith<ApiError.Unauthenticated> { response(401).unauthenticated() }
        assertFailsWith<ApiError.Unauthorized> { response(403).unauthorized() }
        assertFailsWith<ApiError.StatusCode> { response(500).validate(statusCode = 500) }
        val ok = response()
        assertSame(ok, ok.unauthenticated().unauthorized().validate(statusCode = 500))
    }

    @Test
    fun `map flatMap log and debug`() {
        val r = response()
        assertEquals(200, r.map { it.statusCode })
        assertEquals(AnyResponse(r), r.flatMap { AnyResponse(it) })
        var logged = ""
        assertSame(r, r.log { logged = it })
        assertTrue("StatusCode: 200" in logged)
        var debugged: Response? = null
        assertSame(r, r.debug { debugged = it })
        assertSame(r, debugged)
    }

    @Test
    fun `description contains request and response details`() {
        val description = response(error = IllegalStateException("boom")).description
        listOf(
            "[GET] [https] api.test",
            "Path: /users/1",
            "Query: a=1&b=2",
            "[H: v]",
            "StatusCode: 200",
            "Content-Type: [application/json]",
            "Error description: boom",
            "\"name\":\"amine\"",
        ).forEach { assertTrue(it in description, "missing <$it> in:\n$description") }
    }

    @Test
    fun `error factory builds a 499 response`() {
        val response = Response.error(ApiError.Unknown())
        assertEquals(499, response.statusCode)
        assertIs<ApiError.Unknown>(response.error)
    }

    @Test
    fun `byte array holders compare by content`() {
        assertEquals(UrlRequest("u", body = "x".encodeToByteArray()), UrlRequest("u", body = "x".encodeToByteArray()))
        assertEquals(UrlResponse(200, body = "x".encodeToByteArray()), UrlResponse(200, body = "x".encodeToByteArray()))
        assertEquals(AnyResponse(response()), AnyResponse(response()))
        assertEquals(AnyResponse(response()).hashCode(), AnyResponse(response()).hashCode())
    }

    @Test
    fun `header lookup ignores case`() {
        assertEquals(listOf("application/json"), response().header("content-type"))
    }

    @Test
    fun `decodeError uses its own null body message`() {
        val error = assertFailsWith<ApiError.Response> { response(body = null).decodeError<User>() }
        assertTrue("decode error" in error.message.orEmpty())
        assertEquals(User(1, "amine"), response().decodeError<User>())
        assertTrue(response().decodeErrorResult<User>().isSuccess)
    }

    @Test
    fun `description handles binary bodies`() {
        val binary = ResponseDefault(urlRequest, 200, byteArrayOf(-1, -2, -3))
        assertTrue("Body[3]:" in binary.description)
        assertTrue("\uFFFD" !in binary.description)
    }
}
