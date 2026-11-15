package io.github.amine2233.networkingmiddle

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** The next handler in the chain of interceptors. */
public typealias Next = suspend (baseURL: String, request: Request) -> Response

/** Intercepts a request on its way to the [NetworkService] and the response on its way back. */
public fun interface Interceptor {
    public suspend fun intercept(
        baseURL: String,
        request: Request,
        next: Next,
    ): Response
}

public object InterceptorFactory {
    public fun interceptorLogger(printer: (String) -> Unit): Interceptor =
        Interceptor { baseURL, request, next ->
            next(baseURL, request).also { printer(it.description) }
        }

    public fun interceptorTimeLogger(printer: (String) -> Unit): Interceptor =
        Interceptor { baseURL, request, next ->
            val start = TimeSource.Monotonic.markNow()
            next(baseURL, request).also {
                val seconds = start.elapsedNow().inWholeMilliseconds / 1000.0
                printer("[Request][Time] ${"%.2f".format(java.util.Locale.ROOT, seconds)} seconds")
            }
        }

    /**
     * Adds `Authorization: Bearer <token>` to every request and refreshes the token once when it is rejected.
     *
     * Per request:
     * 1. Calls [token] once. A non-null token is sent as `Authorization: Bearer <token>`; a `null` token sends the
     *    request unchanged.
     * 2. Returns the response as is when [isUnauthorized] is false.
     * 3. Otherwise, under a mutex, calls [token] again. If it differs from the token this request used (the failed
     *    token, possibly `null`), another request already refreshed it and that token is used. If it is unchanged,
     *    [refresh] is called once with the failed token.
     * 4. When the resulting token is `null`, returns the original unauthorized response.
     * 5. Otherwise releases the mutex and retries once with the new token, returning that response whatever it is.
     *    There is at most one retry and no further refresh.
     *
     * [refresh] must store the new token so that later [token] calls return it: the single-flight check compares
     * against a fresh [token] read, so concurrent unauthorized responses for the same token trigger one refresh.
     *
     * The mutex belongs to this interceptor instance: every request through it shares one refresh, even across
     * providers. `next` is never called while the mutex is held.
     *
     * Cancellation: [refresh] runs in the coroutine of the request that holds the mutex. Cancelling a request that
     * is only waiting for the mutex does not affect the running refresh. Cancelling the refreshing request cancels
     * the refresh; the next waiter then sees the token unchanged and calls [refresh] itself.
     * Exceptions from [token], [refresh] and `next` propagate unchanged.
     */
    public fun bearerAuth(
        token: suspend () -> String?,
        refresh: suspend (failedToken: String?) -> String?,
        isUnauthorized: (Response) -> Boolean = { it.statusCode == 401 },
    ): Interceptor {
        val mutex = Mutex()
        return Interceptor { baseURL, request, next ->
            val failedToken = token()
            val response = next(baseURL, if (failedToken != null) request.authorised(failedToken) else request)
            if (!isUnauthorized(response)) return@Interceptor response

            val newToken =
                mutex.withLock {
                    val current = token()
                    if (current != failedToken) current else refresh(failedToken)
                }
            if (newToken == null) response else next(baseURL, request.authorised(newToken))
        }
    }
}
