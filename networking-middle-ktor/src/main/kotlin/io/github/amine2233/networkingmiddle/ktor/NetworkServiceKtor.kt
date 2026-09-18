package io.github.amine2233.networkingmiddle.ktor

import io.github.amine2233.networkingmiddle.NetworkService
import io.github.amine2233.networkingmiddle.UrlRequest
import io.github.amine2233.networkingmiddle.UrlResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpMethod

/**
 * [NetworkService] backed by a Ktor [HttpClient]. Configure engine, timeouts and pinning on the client.
 * Works with `expectSuccess = true` too: HTTP errors are returned as a [UrlResponse], never thrown.
 * Response header lookup is case-insensitive; an empty body maps to `null`.
 */
public class NetworkServiceKtor(
    private val client: HttpClient,
) : NetworkService {
    override suspend fun execute(request: UrlRequest): UrlResponse {
        val response =
            try {
                client.request(request.url) {
                    method = HttpMethod.parse(request.method.rawValue)
                    request.headers.forEach { (key, value) -> headers.append(key, value) }
                    request.body?.let { setBody(it) }
                }
            } catch (error: ResponseException) {
                error.response
            }
        return response.toUrlResponse()
    }

    private suspend fun HttpResponse.toUrlResponse(): UrlResponse =
        UrlResponse(
            statusCode = status.value,
            headers = headers.entries().associateTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) { it.key to it.value },
            body = readRawBytes().takeIf { it.isNotEmpty() },
        )
}

public fun HttpClient.asNetworkService(): NetworkService = NetworkServiceKtor(this)
