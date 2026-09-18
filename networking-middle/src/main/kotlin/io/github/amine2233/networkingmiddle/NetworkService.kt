package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The underlying network engine, injected into the [NetworkProvider].
 * The core never imports a transport: implement this with Ktor (see `networking-middle-ktor`)
 * or with a one-line lambda in tests. Transport failures are thrown; the provider turns them into a [Response].
 */
public fun interface NetworkService {
    public suspend fun execute(request: UrlRequest): UrlResponse
}

internal suspend fun NetworkService.perform(
    baseURL: String,
    request: Request,
): Response {
    val urlRequest = request.asUrlRequest(baseURL)
    return try {
        val result = execute(urlRequest)
        ResponseDefault(urlRequest, result.statusCode, result.body, result.headers)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        ResponseError(error, urlRequest)
    }
}
