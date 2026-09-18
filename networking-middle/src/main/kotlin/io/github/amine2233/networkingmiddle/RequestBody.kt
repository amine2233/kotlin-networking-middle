package io.github.amine2233.networkingmiddle

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/** Anything that can be applied to a [UrlRequest] as a body. */
public fun interface RequestBody {
    public suspend fun apply(urlRequest: UrlRequest): UrlRequest
}

public object RequestBodyFactory {
    public fun <T> encodableBody(
        model: T,
        serializer: KSerializer<T>,
        json: Json = Json,
    ): RequestBody = EncodableBodyRequest(model, serializer, json)

    public inline fun <reified T> encodableBody(
        model: T,
        json: Json = Json,
    ): RequestBody = encodableBody(model, serializer<T>(), json)

    public fun formBody(data: Map<String, Any>): RequestBody = FormBodyRequest(data)

    public fun jsonBody(
        model: JsonElement,
        json: Json = Json,
    ): RequestBody = JsonBodyRequest(model, json)

    public fun stringBody(value: String): RequestBody = StringBodyRequest(value)

    public fun dataBody(value: ByteArray): RequestBody = DataBodyRequest(value)
}

private const val CONTENT_TYPE = "Content-Type"

internal class EncodableBodyRequest<T>(
    private val model: T,
    private val serializer: KSerializer<T>,
    private val json: Json,
) : RequestBody {
    override suspend fun apply(urlRequest: UrlRequest): UrlRequest {
        val data =
            try {
                json.encodeToString(serializer, model).encodeToByteArray()
            } catch (error: Exception) {
                throw ApiError.Body(error)
            }
        return urlRequest.setHeader(CONTENT_TYPE, "application/json").copy(body = data)
    }
}

internal class FormBodyRequest(
    private val data: Map<String, Any>,
) : RequestBody {
    override suspend fun apply(urlRequest: UrlRequest): UrlRequest =
        urlRequest
            .setHeader(CONTENT_TYPE, "application/x-www-form-urlencoded;charset=UTF-8")
            .copy(body = data.keyValuePairs().encodeToByteArray())
}

internal class JsonBodyRequest(
    private val element: JsonElement,
    private val json: Json,
) : RequestBody {
    override suspend fun apply(urlRequest: UrlRequest): UrlRequest =
        urlRequest
            .setHeader(CONTENT_TYPE, "application/json")
            .copy(body = json.encodeToString(JsonElement.serializer(), element).encodeToByteArray())
}

internal class StringBodyRequest(
    private val string: String,
) : RequestBody {
    override suspend fun apply(urlRequest: UrlRequest): UrlRequest = urlRequest.copy(body = string.encodeToByteArray())
}

internal class DataBodyRequest(
    private val data: ByteArray,
) : RequestBody {
    override suspend fun apply(urlRequest: UrlRequest): UrlRequest = urlRequest.copy(body = data)
}

internal fun Map<String, Any>.keyValuePairs(): String =
    entries.joinToString("&") { "${it.key.urlEncoded()}=${it.value.toString().urlEncoded()}" }
