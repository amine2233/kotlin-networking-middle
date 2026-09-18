package io.github.amine2233.networkingmiddle

/** The HTTP request method types. A plain class (not a value class) so Java/Android callers see real signatures. */
public class HttpMethod(
    public val rawValue: String,
) {
    override fun equals(other: Any?): Boolean = other is HttpMethod && rawValue == other.rawValue

    override fun hashCode(): Int = rawValue.hashCode()

    override fun toString(): String = rawValue

    public companion object {
        public val Get: HttpMethod = HttpMethod("GET")
        public val Put: HttpMethod = HttpMethod("PUT")
        public val Post: HttpMethod = HttpMethod("POST")
        public val Patch: HttpMethod = HttpMethod("PATCH")
        public val Delete: HttpMethod = HttpMethod("DELETE")
        public val Head: HttpMethod = HttpMethod("HEAD")
    }
}

/** A network request with its essential components. */
public interface Request {
    public val path: String
    public val method: HttpMethod
    public val parameters: RequestParameters?
    public val body: RequestBody?
    public val headers: Map<String, String>
}

public object RequestFactory {
    public fun create(
        path: String,
        method: HttpMethod,
        parameters: RequestParameters? = null,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap(),
    ): Request = RequestBuilder(path, method, parameters, body, headers)
}

/** Resolves the full URL from [baseURL] and applies method, headers, parameters and body. */
public suspend fun Request.asUrlRequest(baseURL: String): UrlRequest {
    val url = baseURL.appendingPathComponent(path)
    if (runCatching { java.net.URI(url) }.isFailure) throw ApiError.InvalidUrl(url)

    var urlRequest = UrlRequest(url = url, method = method, headers = headers)

    parameters?.let {
        urlRequest =
            try {
                it.apply(urlRequest)
            } catch (error: ApiError) {
                throw error
            } catch (error: Exception) {
                throw ApiError.RequestParameters(error)
            }
    }

    body?.let {
        urlRequest =
            try {
                it.apply(urlRequest)
            } catch (error: ApiError) {
                throw error
            } catch (error: Exception) {
                throw ApiError.Body(error)
            }
    }

    return urlRequest
}

public fun Request.authorised(bearer: String): Request = setHeader("Authorization", "Bearer $bearer")

public fun Request.bearer(value: String): Request = authorised(value)

public fun Request.token(token: String): Request = setHeader("Authorization", "token $token")

/** Returns a copy with [key] set to [value]; a `null` value removes the header (Swift `set(_:forHttpHeaderKey:)`). */
public fun Request.setHeader(
    key: String,
    value: String?,
): Request {
    val newHeaders = if (value == null) headers - key else headers + (key to value)
    return RequestBuilder(path, method, parameters, body, newHeaders)
}

public fun Request.toAnyRequest(): AnyRequest = AnyRequest(this)

internal data class RequestBuilder(
    override val path: String,
    override val method: HttpMethod,
    override val parameters: RequestParameters? = null,
    override val body: RequestBody? = null,
    override val headers: Map<String, String> = emptyMap(),
) : Request

/** A concrete snapshot of any [Request]. */
public data class AnyRequest(
    override val path: String,
    override val method: HttpMethod,
    override val parameters: RequestParameters?,
    override val body: RequestBody?,
    override val headers: Map<String, String>,
) : Request {
    public constructor(request: Request) : this(
        request.path,
        request.method,
        request.parameters,
        request.body,
        request.headers,
    )
}
