package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BearerAuthInterceptorTest {
    private val baseURL = "https://api.test"
    private val request = RequestFactory.create("/ping", HttpMethod.Get)

    private fun response(
        statusCode: Int,
        request: Request,
    ): Response = ResponseDefault(UrlRequest("https://api.test/ping", headers = request.headers), statusCode, null)

    private fun Request.authorization(): String? = headers["Authorization"]

    @Test
    fun `non-null token is sent as a bearer header and a 200 is returned without refreshing`() =
        runTest {
            val seen = mutableListOf<String?>()
            var refreshCalls = 0
            val interceptor =
                InterceptorFactory.bearerAuth(
                    token = { "t1" },
                    refresh = {
                        refreshCalls++
                        "t2"
                    },
                )

            val result =
                interceptor.intercept(baseURL, request) { _, r ->
                    seen += r.authorization()
                    response(200, r)
                }

            assertEquals(200, result.statusCode)
            assertEquals(listOf<String?>("Bearer t1"), seen)
            assertEquals(0, refreshCalls)
        }

    @Test
    fun `null token sends the request without an Authorization header`() =
        runTest {
            val seen = mutableListOf<Request>()
            val interceptor = InterceptorFactory.bearerAuth(token = { null }, refresh = { null })

            val result =
                interceptor.intercept(baseURL, request.setHeader("X-Other", "1")) { _, r ->
                    seen += r
                    response(200, r)
                }

            assertEquals(200, result.statusCode)
            assertNull(seen.single().authorization())
            assertEquals("1", seen.single().headers["X-Other"])
        }

    @Test
    fun `50 concurrent 401s for the same token trigger exactly one refresh and all retry with the new token`() =
        runTest {
            var current = "t1"
            val refreshArgs = mutableListOf<String?>()
            val seen = mutableListOf<String?>()
            val allFirstAttempts = CompletableDeferred<Unit>()
            var firstAttempts = 0
            val interceptor =
                InterceptorFactory.bearerAuth(
                    token = { current },
                    refresh = { failed ->
                        refreshArgs += failed
                        current = "t2"
                        "t2"
                    },
                )
            val next: Next = { _, r ->
                seen += r.authorization()
                if (r.authorization() == "Bearer t1") {
                    if (++firstAttempts == 50) allFirstAttempts.complete(Unit)
                    allFirstAttempts.await()
                    response(401, r)
                } else {
                    response(200, r)
                }
            }

            val results = (0 until 50).map { async { interceptor.intercept(baseURL, request, next) } }.awaitAll()

            assertEquals(List<String?>(50) { "Bearer t1" } + List(50) { "Bearer t2" }, seen)
            assertEquals(listOf<String?>("t1"), refreshArgs)
            assertTrue(results.all { it.statusCode == 200 })
            assertTrue(results.all { it.request.headers["Authorization"] == "Bearer t2" })
        }

    @Test
    fun `null refresh result returns the original 401 without retrying`() =
        runTest {
            var nextCalls = 0
            var original: Response? = null
            val interceptor = InterceptorFactory.bearerAuth(token = { "t1" }, refresh = { null })

            val result =
                interceptor.intercept(baseURL, request) { _, r ->
                    nextCalls++
                    response(401, r).also { original = it }
                }

            assertEquals(1, nextCalls)
            assertEquals(401, result.statusCode)
            assertSame(original, result)
            assertEquals("Bearer t1", result.request.headers["Authorization"])
        }

    @Test
    fun `a 401 on the retry is returned without another refresh`() =
        runTest {
            var current = "t1"
            var nextCalls = 0
            var refreshCalls = 0
            val interceptor =
                InterceptorFactory.bearerAuth(
                    token = { current },
                    refresh = {
                        refreshCalls++
                        current = "t2"
                        "t2"
                    },
                )

            val result =
                interceptor.intercept(baseURL, request) { _, r ->
                    nextCalls++
                    response(401, r)
                }

            assertEquals(2, nextCalls)
            assertEquals(1, refreshCalls)
            assertEquals(401, result.statusCode)
            assertEquals("Bearer t2", result.request.headers["Authorization"])
        }

    @Test
    fun `cancelling a waiting request leaves the running refresh untouched`() =
        runTest {
            var current = "t1"
            var refreshCalls = 0
            val refreshGate = CompletableDeferred<Unit>()
            val interceptor =
                InterceptorFactory.bearerAuth(
                    token = { current },
                    refresh = {
                        refreshCalls++
                        refreshGate.await()
                        current = "t2"
                        "t2"
                    },
                )
            val next: Next = { _, r -> response(if (r.authorization() == "Bearer t2") 200 else 401, r) }

            val refresher = async { interceptor.intercept(baseURL, request, next) }
            runCurrent()
            assertEquals(1, refreshCalls)
            val waiter = async { interceptor.intercept(baseURL, request, next) }
            runCurrent()

            waiter.cancelAndJoin()
            refreshGate.complete(Unit)

            val result = refresher.await()
            assertTrue(waiter.isCancelled)
            assertEquals(200, result.statusCode)
            assertEquals("Bearer t2", result.request.headers["Authorization"])
            assertEquals(1, refreshCalls)
        }

    @Test
    fun `cancelling the refreshing request makes a waiting request refresh itself`() =
        runTest {
            var current = "t1"
            val refreshArgs = mutableListOf<String?>()
            val interceptor =
                InterceptorFactory.bearerAuth(
                    token = { current },
                    refresh = { failed ->
                        refreshArgs += failed
                        if (refreshArgs.size == 1) awaitCancellation()
                        current = "t2"
                        "t2"
                    },
                )
            val next: Next = { _, r -> response(if (r.authorization() == "Bearer t2") 200 else 401, r) }

            val refresher = async { interceptor.intercept(baseURL, request, next) }
            runCurrent()
            val waiter = async { interceptor.intercept(baseURL, request, next) }
            runCurrent()
            assertEquals(1, refreshArgs.size)

            refresher.cancelAndJoin()

            val result = waiter.await()
            assertTrue(refresher.isCancelled)
            assertEquals(200, result.statusCode)
            assertEquals("Bearer t2", result.request.headers["Authorization"])
            assertEquals(listOf<String?>("t1", "t1"), refreshArgs)
        }

    @Test
    fun `custom isUnauthorized triggers a refresh on 403 while the default does not`() =
        runTest {
            var refreshCalls = 0
            val next: Next = { _, r -> response(if (r.authorization() == "Bearer t2") 200 else 403, r) }

            var defaultToken = "t1"
            val default =
                InterceptorFactory.bearerAuth(
                    token = { defaultToken },
                    refresh = {
                        refreshCalls++
                        defaultToken = "t2"
                        "t2"
                    },
                )
            assertEquals(403, default.intercept(baseURL, request, next).statusCode)
            assertEquals(0, refreshCalls)

            var customToken = "t1"
            val custom =
                InterceptorFactory.bearerAuth(
                    token = { customToken },
                    refresh = {
                        refreshCalls++
                        customToken = "t2"
                        "t2"
                    },
                    isUnauthorized = { it.statusCode == 403 },
                )
            assertEquals(200, custom.intercept(baseURL, request, next).statusCode)
            assertEquals(1, refreshCalls)
        }
}
