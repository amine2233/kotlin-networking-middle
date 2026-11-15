package io.github.amine2233.networkingmiddle

import kotlin.coroutines.cancellation.CancellationException

/** The main entry point: builds, modifies, intercepts and sends requests. */
public interface NetworkProvider {
    public fun modifyRequests(action: (baseURL: String, request: Request) -> Request): NetworkProvider

    public fun intercept(action: suspend (baseURL: String, request: Request, next: Next) -> Response): NetworkProvider

    /**
     * Applies the modifiers, runs the interceptor chain and sends [request] to [baseURL].
     *
     * Throws:
     * - [ApiError.InvalidUrl], [ApiError.RequestParameters] and [ApiError.Body] when the request can't be built.
     * - `CancellationException` when the calling coroutine is cancelled.
     * - Anything thrown by a user-supplied modifier or interceptor.
     *
     * Returns inside the [Response]:
     * - Transport failures, as `statusCode = 499` with `error` set; [validate] rethrows them.
     * - Every HTTP status, 4xx and 5xx included.
     */
    @Throws(ApiError::class, CancellationException::class)
    public suspend fun request(
        baseURL: String,
        request: Request,
    ): Response

    public fun newBuilder(
        path: String,
        method: HttpMethod,
        parameters: RequestParameters? = null,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): Request
}

public object NetworkProviderFactory {
    public fun create(
        networkService: NetworkService,
        modifiers: List<RequestModifier> = emptyList(),
        interceptors: List<Interceptor> = emptyList(),
    ): NetworkProvider = NetworkProviderDefault(networkService, modifiers, interceptors)
}

internal class NetworkProviderDefault(
    private val http: NetworkService,
    modifiers: List<RequestModifier>,
    interceptors: List<Interceptor>,
) : NetworkProvider {
    private val modifiers = modifiers.toList()
    private val interceptors = interceptors.toList()

    override fun newBuilder(
        path: String,
        method: HttpMethod,
        parameters: RequestParameters?,
        body: RequestBody?,
        headers: Map<String, String>,
    ): Request = RequestBuilder(path, method, parameters, body, headers)

    override fun modifyRequests(action: (String, Request) -> Request): NetworkProvider =
        NetworkProviderDefault(http, modifiers + RequestModifier(action), interceptors)

    override fun intercept(action: suspend (String, Request, Next) -> Response): NetworkProvider =
        NetworkProviderDefault(http, modifiers, interceptors + Interceptor(action))

    @Throws(ApiError::class, CancellationException::class)
    override suspend fun request(
        baseURL: String,
        request: Request,
    ): Response {
        val modified = modifiers.fold(request) { acc, modifier -> modifier.modify(baseURL, acc) }

        val chain: Next =
            interceptors.foldRight<Interceptor, Next>({ url, req -> http.perform(url, req) }) { interceptor, next ->
                { url, req -> interceptor.intercept(url, req, next) }
            }

        return chain(baseURL, modified)
    }
}
