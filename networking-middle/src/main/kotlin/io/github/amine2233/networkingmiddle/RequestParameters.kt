package io.github.amine2233.networkingmiddle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/** Anything that can be applied to a [UrlRequest] as query parameters. */
public fun interface RequestParameters {
    public fun apply(urlRequest: UrlRequest): UrlRequest
}

public object RequestParametersFactory {
    public fun queryParameters(query: Map<String, String>): RequestParameters = QueryParameters(query)

    public fun <T> queryEncodableParameters(
        query: T,
        serializer: KSerializer<T>,
        json: Json = Json,
    ): RequestParameters = QueryParametersEncodable(query, serializer, json)

    public inline fun <reified T> queryEncodableParameters(
        query: T,
        json: Json = Json,
    ): RequestParameters = queryEncodableParameters(query, serializer<T>(), json)

    /** Parses `a=1&b=2` into query parameters. */
    public fun stringRequestParameters(query: String): RequestParameters = RequestParametersString(query)
}

internal class QueryParameters(
    private val query: Map<String, String>,
) : RequestParameters {
    override fun apply(urlRequest: UrlRequest): UrlRequest = urlRequest.appendingQuery(query)
}

internal class QueryParametersEncodable<T>(
    private val query: T,
    private val serializer: KSerializer<T>,
    private val json: Json,
) : RequestParameters {
    override fun apply(urlRequest: UrlRequest): UrlRequest {
        val element = json.encodeToJsonElement(serializer, query)
        val obj = element as? JsonObject ?: return urlRequest
        return urlRequest.appendingQuery(obj.toQueryMap())
    }
}

internal class RequestParametersString(
    private val string: String,
) : RequestParameters {
    override fun apply(urlRequest: UrlRequest): UrlRequest {
        val items =
            string
                .split("&")
                .filter { it.isNotEmpty() }
                .associate { item ->
                    val parts = item.split("=", limit = 2)
                    parts[0] to parts.getOrElse(1) { "" }
                }
        return QueryParameters(items).apply(urlRequest)
    }
}

/** Only scalar fields become query items; nested objects/arrays and nulls are skipped. */
internal fun JsonObject.toQueryMap(): Map<String, String> =
    entries
        .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { key to it.content } }
        .toMap()
