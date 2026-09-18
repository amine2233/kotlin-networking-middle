package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RequestBodyTest {
    private val base = UrlRequest("https://api.test/items", HttpMethod.Post)

    @Serializable
    data class Login(
        val user: String,
        val password: String,
    )

    @Test
    fun `encodableBody sets json content type and body`() =
        runTest {
            val applied = RequestBodyFactory.encodableBody(Login("a", "b")).apply(base)
            assertEquals("application/json", applied.headers["Content-Type"])
            assertEquals("""{"user":"a","password":"b"}""", applied.body?.decodeToString())
        }

    @Test
    fun `formBody url encodes values`() =
        runTest {
            val applied = RequestBodyFactory.formBody(mapOf("user" to "a b", "n" to 1)).apply(base)
            assertEquals("application/x-www-form-urlencoded;charset=UTF-8", applied.headers["Content-Type"])
            assertEquals("user=a%20b&n=1", applied.body?.decodeToString())
        }

    @Test
    fun `jsonBody serializes a json element`() =
        runTest {
            val applied = RequestBodyFactory.jsonBody(buildJsonObject { put("k", 1) }).apply(base)
            assertEquals("application/json", applied.headers["Content-Type"])
            assertEquals("""{"k":1}""", applied.body?.decodeToString())
        }

    @Test
    fun `string and data bodies do not touch headers`() =
        runTest {
            val string = RequestBodyFactory.stringBody("raw").apply(base)
            val data = RequestBodyFactory.dataBody(byteArrayOf(1, 2)).apply(base)
            assertEquals("raw", string.body?.decodeToString())
            assertEquals(listOf<Byte>(1, 2), data.body?.toList())
            assertNull(string.headers["Content-Type"])
        }

    @Test
    fun `encodableBody failures surface as ApiError Body`() =
        runTest {
            val failing = kotlinx.serialization.json.Json { encodeDefaults = true }
            val body = RequestBodyFactory.encodableBody(Double.NaN, failing)
            assertFailsWith<ApiError.Body> { body.apply(base) }
            val request = RequestFactory.create("/", HttpMethod.Post, body = body)
            assertFailsWith<ApiError.Body> { request.asUrlRequest("https://api.test") }
        }

    @Test
    fun `formBody encodes keys`() =
        runTest {
            val applied = RequestBodyFactory.formBody(mapOf("a b" to "c")).apply(base)
            assertEquals("a%20b=c", applied.body?.decodeToString())
        }
}
