package io.github.amine2233.networkingmiddle

import java.net.URLEncoder

/** The platform-neutral equivalent of Foundation's `URLRequest`: what a [NetworkService] executes. */
public data class UrlRequest(
    val url: String,
    val method: HttpMethod = HttpMethod.Get,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    public fun setHeader(
        key: String,
        value: String,
    ): UrlRequest = copy(headers = headers + (key to value))

    public fun appendingQuery(items: Map<String, String>): UrlRequest {
        if (items.isEmpty()) return this
        val query = items.entries.joinToString("&") { "${it.key.urlEncoded()}=${it.value.urlEncoded()}" }
        val (base, fragment) = url.split("#", limit = 2).let { it[0] to it.getOrNull(1) }
        val separator =
            when {
                base.endsWith("?") -> ""
                '?' in base -> "&"
                else -> "?"
            }
        return copy(url = base + separator + query + (fragment?.let { "#$it" } ?: ""))
    }

    override fun equals(other: Any?): Boolean =
        other is UrlRequest &&
            url == other.url &&
            method == other.method &&
            headers == other.headers &&
            body.contentEquals(other.body)

    override fun hashCode(): Int = listOf(url, method, headers, body?.contentHashCode()).hashCode()

    public companion object {
        internal val empty: UrlRequest = UrlRequest("https://example.com")
    }
}

/** What a [NetworkService] returns: the raw transport result, before it is wrapped into a [Response]. */
public data class UrlResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is UrlResponse && statusCode == other.statusCode && headers == other.headers && body.contentEquals(other.body)

    override fun hashCode(): Int = listOf(statusCode, headers, body?.contentHashCode()).hashCode()
}

internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

internal fun String.appendingPathComponent(pathComponent: String): String {
    if (pathComponent.isEmpty()) return this
    return removeSuffix("/") + "/" + pathComponent.removePrefix("/")
}
