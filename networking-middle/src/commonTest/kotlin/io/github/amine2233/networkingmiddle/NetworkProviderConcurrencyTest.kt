package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkProviderConcurrencyTest {
    private val baseURL = "https://api.test"

    @Test
    fun `1000 concurrent requests through one provider each get their own response`() =
        runTest {
            val provider =
                NetworkProviderFactory.create(
                    networkService = NetworkService { UrlResponse(200, body = it.headers["X-Request-Id"]?.encodeToByteArray()) },
                )

            val responses =
                withContext(Dispatchers.Default) {
                    (0 until 1000)
                        .map { i ->
                            async {
                                val request = provider.newBuilder("/ping", HttpMethod.Get, headers = mapOf("X-Request-Id" to "$i"))
                                i to provider.request(baseURL, request)
                            }
                        }.awaitAll()
                }

            assertEquals(1000, responses.size)
            responses.forEach { (i, response) ->
                assertEquals("$i", response.bodyString)
                assertEquals("$i", response.request.headers["X-Request-Id"])
            }
        }
}
