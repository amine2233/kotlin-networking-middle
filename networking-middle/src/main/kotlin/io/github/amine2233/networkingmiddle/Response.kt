package io.github.amine2233.networkingmiddle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** The result of a network call: body, headers, status code and potential transport error. */
public interface Response {
    public val request: UrlRequest
    public val body: ByteArray?
    public val headers: Map<String, List<String>>
    public val statusCode: Int
    public val error: Throwable?

    public companion object {
        public fun error(error: Throwable): Response = ResponseError(error, UrlRequest.empty)
    }
}

public class AnyResponse(
    override val request: UrlRequest,
    override val body: ByteArray?,
    override val headers: Map<String, List<String>>,
    override val statusCode: Int,
    override val error: Throwable?,
) : Response {
    public constructor(response: Response) : this(
        response.request,
        response.body,
        response.headers,
        response.statusCode,
        response.error,
    )

    override fun equals(other: Any?): Boolean =
        other is AnyResponse &&
            request == other.request &&
            body.contentEquals(other.body) &&
            headers == other.headers &&
            statusCode == other.statusCode &&
            error == other.error

    override fun hashCode(): Int = listOf(request, body?.contentHashCode(), headers, statusCode, error).hashCode()

    override fun toString(): String = description
}

internal class ResponseDefault(
    override val request: UrlRequest,
    override val statusCode: Int,
    override val body: ByteArray? = null,
    override val headers: Map<String, List<String>> = emptyMap(),
    override val error: Throwable? = null,
) : Response {
    override fun toString(): String = description
}

internal class ResponseError(
    override val error: Throwable,
    override val request: UrlRequest,
    override val statusCode: Int = 499,
) : Response {
    override val body: ByteArray? = null
    override val headers: Map<String, List<String>> = emptyMap()

    override fun toString(): String = description
}

private const val NULL_BODY = "Unable to decode from a `Response`; body was null."
private const val NULL_ERROR_BODY = "Unable to decode error from a `Response`; body was null."

/** Throws the transport error if present. */
public fun <R : Response> R.validate(): R = error?.let { throw it } ?: this

/** Decodes the body with kotlinx.serialization. `decode<Unit>()` is the equivalent of Swift's `EmptyDecodable`. */
public fun <Model> Response.decode(
    serializer: KSerializer<Model>,
    json: Json = Json,
): Model = decode(serializer, json, NULL_BODY)

public inline fun <reified Model> Response.decode(json: Json = Json): Model = decode(serializer<Model>(), json)

public fun <Model> Response.decodeResult(
    serializer: KSerializer<Model>,
    json: Json = Json,
): Result<Model> = decodeResult(serializer, json, NULL_BODY)

public inline fun <reified Model> Response.decodeResult(json: Json = Json): Result<Model> = decodeResult(serializer<Model>(), json)

public fun <Model> Response.decodeError(
    serializer: KSerializer<Model>,
    json: Json = Json,
): Model = decode(serializer, json, NULL_ERROR_BODY)

public inline fun <reified Model> Response.decodeError(json: Json = Json): Model = decodeError(serializer<Model>(), json)

public fun <Model> Response.decodeErrorResult(
    serializer: KSerializer<Model>,
    json: Json = Json,
): Result<Model> = decodeResult(serializer, json, NULL_ERROR_BODY)

public inline fun <reified Model> Response.decodeErrorResult(json: Json = Json): Result<Model> =
    decodeErrorResult(serializer<Model>(), json)

private fun <Model> Response.decode(
    serializer: KSerializer<Model>,
    json: Json,
    nullMessage: String,
): Model {
    val data = body ?: throw ApiError.Response(nullMessage)
    return json.decodeFromString(serializer, data.decodeToString())
}

private fun <Model> Response.decodeResult(
    serializer: KSerializer<Model>,
    json: Json,
    nullMessage: String,
): Result<Model> {
    val data = body ?: return Result.failure(ApiError.Response(nullMessage))
    return try {
        Result.success(json.decodeFromString(serializer, data.decodeToString()))
    } catch (error: SerializationException) {
        Result.failure(ApiError.Parser(error))
    } catch (error: IllegalArgumentException) {
        Result.failure(ApiError.Parser(error))
    }
}

public fun <Model> Response.map(transform: (Response) -> Model): Model = transform(this)

public fun <Model : Response> Response.flatMap(transform: (Response) -> Model): Model = transform(this)

public fun Response.result(): Result<Response> = error?.let { Result.failure(it) } ?: Result.success(this)

public fun <E : Throwable> Response.mapError(transform: (Throwable) -> E): Result<Response> =
    error?.let { Result.failure(transform(it)) } ?: Result.success(this)

public fun Response.log(completion: (String) -> Unit): Response = also { completion(description) }

public fun Response.debug(completion: (Response) -> Unit): Response = also { completion(this) }

/** Case-insensitive header lookup (HTTP/2 servers send lowercase names). */
public fun Response.header(name: String): List<String>? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

/** Throws [ApiError.Unauthenticated] when the status code is 401. */
public fun <R : Response> R.unauthenticated(): R = if (statusCode == 401) throw ApiError.Unauthenticated() else this

/** Throws [ApiError.Unauthorized] when the status code is 403. */
public fun <R : Response> R.unauthorized(): R = if (statusCode == 403) throw ApiError.Unauthorized() else this

/** Throws [ApiError.StatusCode] when the response has exactly this [statusCode] (same semantics as Swift). */
public fun <R : Response> R.validate(statusCode: Int): R = if (this.statusCode == statusCode) throw ApiError.StatusCode() else this

public val Response.description: String
    get() =
        buildString {
            append("--- Request ---")
            val uri = runCatching { java.net.URI(request.url) }.getOrNull()
            append("\n[${request.method.rawValue}] [${uri?.scheme ?: "unknown"}] ${uri?.host ?: "unknown"}")
            uri?.path?.takeIf { it.isNotEmpty() && it != "/" }?.let { append("\nPath: $it") }
            uri?.query?.let { query -> append("\n\tQuery: ${query.split("&").sorted().joinToString("&")}") }
            request.body?.let { append("\n\tBody[${it.size}]:" + (it.utf8OrNull()?.let { text -> "\n\t$text" } ?: "")) }
            if (request.headers.isNotEmpty()) {
                append("\nHeaders:\n\t[${request.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }}]")
            }
            append("\n--- Response ---")
            append("\nStatusCode: $statusCode")
            if (headers.isNotEmpty()) {
                append("\nHeaders:\n\t[${headers.entries.joinToString(",") { "${it.key}: ${it.value}" }}]")
            }
            error?.let {
                append("\nError: $it")
                append("\nError description: ${it.message}")
            }
            body?.takeIf { it.isNotEmpty() }?.let { append("\nBody[${it.size}]:" + (it.utf8OrNull()?.let { text -> "\n$text" } ?: "")) }
            append("\n--- End Response ---")
        }

private fun ByteArray.utf8OrNull(): String? = runCatching { decodeToString(throwOnInvalidSequence = true) }.getOrNull()
