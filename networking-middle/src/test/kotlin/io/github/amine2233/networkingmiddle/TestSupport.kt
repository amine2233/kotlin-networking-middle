package io.github.amine2233.networkingmiddle

internal fun stubService(
    statusCode: Int = 200,
    body: String? = null,
    headers: Map<String, List<String>> = emptyMap(),
    onExecute: (UrlRequest) -> Unit = {},
): NetworkService =
    NetworkService { request ->
        onExecute(request)
        UrlResponse(statusCode, headers, body?.encodeToByteArray())
    }

internal val Response.bodyString: String? get() = body?.decodeToString()
