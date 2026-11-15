package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `asUrlRequest propagates cancellation of a suspended body`() =
        runTest {
            val request = RequestFactory.create("/", HttpMethod.Post, body = RequestBody { awaitCancellation() })
            val run = runCancelled { request }

            assertTrue(run.isCancelled)
            assertEquals(emptyList(), run.handled)
        }

    @Test
    fun `asUrlRequest does not wrap a body failure caused by cancellation`() =
        runTest {
            val body =
                RequestBody {
                    try {
                        awaitCancellation()
                    } catch (error: CancellationException) {
                        throw IllegalStateException("cancelled", error)
                    }
                }
            val request = RequestFactory.create("/", HttpMethod.Post, body = body)
            val run = runCancelled { request }

            assertTrue(run.isCancelled)
            assertEquals(emptyList(), run.handled)
            assertIs<CancellationException>(run.escaped)
        }

    @Test
    fun `asUrlRequest does not wrap a parameters failure caused by cancellation`() =
        runTest {
            val run =
                runCancelled { job ->
                    val parameters =
                        RequestParameters {
                            try {
                                job.cancel()
                                job.ensureActive()
                                it
                            } catch (error: CancellationException) {
                                throw IllegalStateException("cancelled", error)
                            }
                        }
                    RequestFactory.create("/", HttpMethod.Get, parameters = parameters)
                }

            assertTrue(run.isCancelled)
            assertEquals(emptyList(), run.handled)
            assertIs<CancellationException>(run.escaped)
        }

    private class CancelledRun(
        val isCancelled: Boolean,
        val handled: List<Throwable>,
        val escaped: Throwable?,
    )

    private suspend fun TestScope.runCancelled(makeRequest: (Job) -> Request): CancelledRun {
        val handled = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, error -> handled += error }
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler) + handler)
        var escaped: Throwable? = null
        val job =
            scope.launch {
                try {
                    makeRequest(coroutineContext.job).asUrlRequest("https://api.test")
                } catch (error: Throwable) {
                    escaped = error
                    throw error
                }
            }
        runCurrent()
        job.cancelAndJoin()
        return CancelledRun(job.isCancelled, handled, escaped)
    }
}
