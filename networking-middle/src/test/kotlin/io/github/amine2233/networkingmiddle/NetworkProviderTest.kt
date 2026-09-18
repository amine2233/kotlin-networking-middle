package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NetworkProviderTest {
    private val baseURL = "https://api.test"

    @Test
    fun `request goes through modifiers then interceptors then service`() =
        runTest {
            val calls = mutableListOf<String>()
            var executed: UrlRequest? = null

            val provider =
                NetworkProviderFactory.create(
                    networkService = stubService(body = "ok") { executed = it },
                    modifiers =
                        listOf(
                            RequestModifier { _, r ->
                                calls += "m1"
                                r.setHeader("M1", "1")
                            },
                            RequestModifier { _, r ->
                                calls += "m2"
                                r.setHeader("M2", "2")
                            },
                        ),
                    interceptors =
                        listOf(
                            Interceptor { u, r, next ->
                                calls += "i1-before"
                                next(u, r).also { calls += "i1-after" }
                            },
                            Interceptor { u, r, next ->
                                calls += "i2-before"
                                next(u, r).also { calls += "i2-after" }
                            },
                        ),
                )

            val response = provider.request(baseURL, provider.newBuilder("/ping", HttpMethod.Get))

            assertEquals(listOf("m1", "m2", "i1-before", "i2-before", "i2-after", "i1-after"), calls)
            assertEquals(200, response.statusCode)
            assertEquals("ok", response.bodyString)
            assertEquals(mapOf("M1" to "1", "M2" to "2"), executed?.headers)
            assertEquals("https://api.test/ping", executed?.url)
        }

    @Test
    fun `modifyRequests and intercept return a new provider and leave the original untouched`() =
        runTest {
            var count = 0
            val service = stubService { count++ }
            val original = NetworkProviderFactory.create(service)
            val chained =
                original
                    .modifyRequests { _, r -> r.authorised("t") }
                    .intercept { u, r, next -> next(u, r) }

            original.request(baseURL, original.newBuilder("/", HttpMethod.Get))
            chained.request(baseURL, chained.newBuilder("/", HttpMethod.Get))
            assertEquals(2, count)
            assertIs<NetworkProvider>(chained)
        }

    @Test
    fun `interceptor can short circuit the chain`() =
        runTest {
            var serviceCalled = false
            val provider =
                NetworkProviderFactory
                    .create(stubService { serviceCalled = true })
                    .intercept { _, _, _ -> Response.error(ApiError.Unknown()) }

            val response = provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get))
            assertEquals(false, serviceCalled)
            assertEquals(499, response.statusCode)
            assertIs<ApiError.Unknown>(response.error)
        }

    @Test
    fun `transport failure becomes an error response instead of throwing`() =
        runTest {
            val provider = NetworkProviderFactory.create(NetworkService { throw java.io.IOException("offline") })
            val response = provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get))
            assertEquals(499, response.statusCode)
            assertIs<java.io.IOException>(response.error)
            assertNull(response.body)
            assertFailsWith<java.io.IOException> { response.validate() }
        }

    @Test
    fun `request building failure throws from inside the chain after interceptors ran`() =
        runTest {
            var interceptorRan = false
            val provider =
                NetworkProviderFactory
                    .create(stubService())
                    .intercept { u, r, next ->
                        interceptorRan = true
                        next(u, r)
                    }
            val request = provider.newBuilder("/", HttpMethod.Get, parameters = RequestParameters { throw IllegalStateException() })
            assertFailsWith<ApiError.RequestParameters> { provider.request(baseURL, request) }
            assertTrue(interceptorRan)
        }

    @Test
    fun `interceptor exceptions propagate instead of becoming a response`() =
        runTest {
            val provider =
                NetworkProviderFactory
                    .create(stubService())
                    .intercept { _, _, _ -> throw IllegalStateException("interceptor") }
            assertFailsWith<IllegalStateException> { provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get)) }
        }

    @Test
    fun `cancellation propagates even when the service rethrows it as a plain exception`() =
        runTest {
            val service =
                NetworkService {
                    try {
                        delay(10_000)
                    } catch (error: CancellationException) {
                        throw java.io.IOException("Canceled")
                    }
                    UrlResponse(200)
                }
            val provider = NetworkProviderFactory.create(service)
            val job = launch { provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get)) }
            runCurrent()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }

    @Test
    fun `later modifiers and request headers override earlier ones`() =
        runTest {
            var executed: UrlRequest? = null
            val provider =
                NetworkProviderFactory.create(
                    networkService = stubService { executed = it },
                    modifiers = listOf(RequestModifierFactory.bearerToken("first"), RequestModifierFactory.bearerToken("second")),
                )
            provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get, headers = mapOf("Authorization" to "request")))
            assertEquals("Bearer second", executed?.headers?.get("Authorization"))
        }

    @Test
    fun `provider is not affected by later mutation of the given lists`() =
        runTest {
            var count = 0
            val modifiers =
                mutableListOf<RequestModifier>(
                    RequestModifier { _, r ->
                        count++
                        r
                    },
                )
            val provider = NetworkProviderFactory.create(stubService(), modifiers)
            modifiers +=
                RequestModifier { _, r ->
                    count++
                    r
                }
            provider.request(baseURL, provider.newBuilder("/", HttpMethod.Get))
            assertEquals(1, count)
        }
}
