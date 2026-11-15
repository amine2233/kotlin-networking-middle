package io.github.amine2233.networkingmiddle.ktor

import io.github.amine2233.networkingmiddle.ApiError
import io.github.amine2233.networkingmiddle.HttpMethod
import io.github.amine2233.networkingmiddle.NetworkProviderFactory
import io.github.amine2233.networkingmiddle.RequestBodyFactory
import io.github.amine2233.networkingmiddle.RequestModifierFactory
import io.github.amine2233.networkingmiddle.RequestParametersFactory
import io.github.amine2233.networkingmiddle.UrlRequest
import io.github.amine2233.networkingmiddle.validate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkServiceKtorTest {
    @Test
    fun `execute maps request and response`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("https://api.test/items?q=1", request.url.toString())
                    assertEquals("POST", request.method.value)
                    assertEquals("Bearer t", request.headers["Authorization"])
                    assertEquals("payload", request.body.toByteArray().decodeToString())
                    respond("ok", HttpStatusCode.Created, headersOf("X-Id", "42"))
                }

            val service = HttpClient(engine).asNetworkService()
            val response =
                service.execute(
                    UrlRequest(
                        "https://api.test/items?q=1",
                        HttpMethod.Post,
                        mapOf("Authorization" to "Bearer t"),
                        "payload".encodeToByteArray(),
                    ),
                )

            assertEquals(201, response.statusCode)
            assertEquals("ok", response.body?.decodeToString())
            assertEquals(listOf("42"), response.headers["X-Id"])
        }

    @Test
    fun `empty body maps to null`() =
        runTest {
            val service = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }).asNetworkService()
            assertNull(service.execute(UrlRequest("https://api.test")).body)
        }

    @Test
    fun `engine failure becomes an error response through the provider`() =
        runTest {
            val service = HttpClient(MockEngine { throw IOException("offline") }).asNetworkService()
            val provider = NetworkProviderFactory.create(service)
            val response = provider.request("https://api.test", provider.newBuilder("/", HttpMethod.Get))
            assertEquals(499, response.statusCode)
            assertIs<IOException>(response.error)
        }

    @Test
    fun `end to end with modifiers parameters and body`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("https://api.test/login?lang=fr", request.url.toString())
                    assertEquals("Bearer t", request.headers["Authorization"])
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    respond("""{"ok":true}""", HttpStatusCode.OK)
                }
            val provider =
                NetworkProviderFactory.create(
                    networkService = HttpClient(engine).asNetworkService(),
                    modifiers = listOf(RequestModifierFactory.bearerToken("t")),
                )
            val request =
                provider.newBuilder(
                    path = "/login",
                    method = HttpMethod.Post,
                    parameters = RequestParametersFactory.queryParameters(mapOf("lang" to "fr")),
                    body = RequestBodyFactory.stringBody("""{"user":"a"}"""),
                    headers = mapOf("Content-Type" to "application/json"),
                )
            val response = provider.request("https://api.test", request).validate()
            assertEquals(200, response.statusCode)
            assertEquals("""{"ok":true}""", response.body?.decodeToString())
        }

    @Test
    fun `expectSuccess clients still return http errors as responses`() =
        runTest {
            val client = HttpClient(MockEngine { respond("nope", HttpStatusCode.InternalServerError) }) { expectSuccess = true }
            val response = client.asNetworkService().execute(UrlRequest("https://api.test"))
            assertEquals(500, response.statusCode)
            assertEquals("nope", response.body?.decodeToString())
        }

    @Test
    fun `response headers are case insensitive and custom methods round trip`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals("PURGE", request.method.value)
                    respond("", HttpStatusCode.OK, headersOf("Content-Type", "text/plain"))
                }
            val response = HttpClient(engine).asNetworkService().execute(UrlRequest("https://api.test", HttpMethod("PURGE")))
            assertEquals(listOf("text/plain"), response.headers["content-type"])
        }

    @Test
    fun `encodable body is sent as json through the provider`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    assertEquals("""{"user":"a"}""", request.body.toByteArray().decodeToString())
                    respond("", HttpStatusCode.OK)
                }
            val provider = NetworkProviderFactory.create(HttpClient(engine).asNetworkService())
            val request =
                provider.newBuilder(
                    path = "/users",
                    method = HttpMethod.Post,
                    body = RequestBodyFactory.encodableBody(mapOf("user" to "a")),
                )
            assertEquals(200, provider.request("https://api.test", request).validate().statusCode)
        }

    @Test
    fun `request headers replace default request headers`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(listOf("b"), request.headers.getAll("X-App"))
                    respond("", HttpStatusCode.OK)
                }
            val client = HttpClient(engine) { defaultRequest { header("X-App", "a") } }
            val response = client.asNetworkService().execute(UrlRequest("https://api.test", headers = mapOf("X-App" to "b")))
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `string body without content type is sent as octet stream`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(ContentType.Application.OctetStream, request.body.contentType)
                    respond("", HttpStatusCode.OK)
                }
            val provider = NetworkProviderFactory.create(HttpClient(engine).asNetworkService())
            val request = provider.newBuilder(path = "/", method = HttpMethod.Post, body = RequestBodyFactory.stringBody("hi"))
            assertEquals(200, provider.request("https://api.test", request).validate().statusCode)
        }

    @Test
    fun `lower case content type header sets the body content type`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(ContentType.Text.Plain, request.body.contentType)
                    respond("", HttpStatusCode.OK)
                }
            val response =
                HttpClient(engine).asNetworkService().execute(
                    UrlRequest("https://api.test", HttpMethod.Post, mapOf("content-type" to "text/plain"), "hi".encodeToByteArray()),
                )
            assertEquals(200, response.statusCode)
        }

    @Test
    fun `malformed content type fails before any request`() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls++
                    respond("", HttpStatusCode.OK)
                }
            val error =
                assertFailsWith<ApiError.Client> {
                    HttpClient(engine).asNetworkService().execute(
                        UrlRequest(
                            "https://api.test",
                            HttpMethod.Post,
                            mapOf("Content-Type" to "not a content type"),
                            "hi".encodeToByteArray(),
                        ),
                    )
                }
            assertTrue(error.message.orEmpty().contains("not a content type"))
            assertEquals(0, calls)
        }

    @Test
    fun `first content type casing wins when several are present`() =
        runTest {
            val engine =
                MockEngine { request ->
                    assertEquals(ContentType.Application.Json, request.body.contentType)
                    respond("", HttpStatusCode.OK)
                }
            val response =
                HttpClient(engine).asNetworkService().execute(
                    UrlRequest(
                        "https://api.test",
                        HttpMethod.Post,
                        linkedMapOf("Content-Type" to "application/json", "content-type" to "text/plain"),
                        "{}".encodeToByteArray(),
                    ),
                )
            assertEquals(200, response.statusCode)
        }
}
