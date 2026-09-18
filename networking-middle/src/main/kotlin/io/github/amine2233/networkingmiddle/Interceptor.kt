package io.github.amine2233.networkingmiddle

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
}
