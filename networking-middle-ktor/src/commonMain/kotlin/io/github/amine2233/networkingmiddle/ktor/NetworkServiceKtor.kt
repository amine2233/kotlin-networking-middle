package io.github.amine2233.networkingmiddle.ktor

import io.github.amine2233.networkingmiddle.ApiError
import io.github.amine2233.networkingmiddle.NetworkService
import io.github.amine2233.networkingmiddle.UrlRequest
import io.github.amine2233.networkingmiddle.UrlResponse
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import io.ktor.util.AttributeKey
import io.ktor.util.CaseInsensitiveMap

/**
 * [NetworkService] backed by a Ktor [HttpClient]. Configure engine, timeouts and pinning on the client.
 * Works with `expectSuccess = true` too: HTTP errors are returned as a [UrlResponse], never thrown.
 * Response header lookup is case-insensitive; an empty body maps to `null`.
 *
 * The body is sent with the content type of the request's `Content-Type` header (any casing, first match wins),
 * or `application/octet-stream` when there is none; an unparseable value throws [ApiError.Client] before any I/O.
 * Other request headers replace values the client already sets, such as `DefaultRequest` headers.
 *
 * The adapter never closes [client]: the caller owns the [HttpClient] lifecycle and must call `close()`.
 */
public class NetworkServiceKtor(
    private val client: HttpClient,
) : NetworkService {
    init {
        // DefaultRequest appends its missing values after the request block runs, so the request headers are
        // applied again once the Before phase is done.
        client.attributes.computeIfAbsent(HeaderOverridesInstalled) {
            client.requestPipeline.intercept(HttpRequestPipeline.State) {
                context.attributes.getOrNull(HeaderOverrides)?.forEach { (key, value) -> context.headers[key] = value }
            }
        }
    }

    override suspend fun execute(request: UrlRequest): UrlResponse {
        val content = request.body?.let { ByteArrayContent(it, request.bodyContentType()) }
        val overrides = request.headers.filterKeys { !it.equals(HttpHeaders.ContentType, ignoreCase = true) }
        val response =
            try {
                client.request(request.url) {
                    method = HttpMethod.parse(request.method.rawValue)
                    overrides.forEach { (key, value) -> headers[key] = value }
                    attributes.put(HeaderOverrides, overrides)
                    content?.let { setBody(it) }
                }
            } catch (error: ResponseException) {
                error.response
            }
        return response.toUrlResponse()
    }

    private fun UrlRequest.bodyContentType(): ContentType {
        val value =
            headers.entries.firstOrNull { it.key.equals(HttpHeaders.ContentType, ignoreCase = true) }?.value
                ?: return ContentType.Application.OctetStream
        return try {
            ContentType.parse(value)
        } catch (error: Exception) {
            throw ApiError.Client("Invalid Content-Type: $value")
        }
    }

    private suspend fun HttpResponse.toUrlResponse(): UrlResponse =
        UrlResponse(
            statusCode = status.value,
            headers = headers.entries().associateTo(CaseInsensitiveMap<List<String>>()) { it.key to it.value },
            body = readRawBytes().takeIf { it.isNotEmpty() },
        )

    private companion object {
        val HeaderOverridesInstalled = AttributeKey<Unit>("NetworkServiceKtorHeaderOverridesInstalled")
        val HeaderOverrides = AttributeKey<Map<String, String>>("NetworkServiceKtorHeaderOverrides")
    }
}

/**
 * Wraps this client in a [NetworkServiceKtor]: bodies default to `application/octet-stream` unless a `Content-Type`
 * header is set, and request headers override client defaults. The caller keeps owning the client and must `close()` it.
 */
public fun HttpClient.asNetworkService(): NetworkService = NetworkServiceKtor(this)
