package io.github.amine2233.networkingmiddle

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestParametersTest {
    private val base = UrlRequest("https://api.test/items")

    @Serializable
    data class Filter(
        val page: Int,
        val name: String,
        val active: Boolean,
    )

    @Test
    fun `queryParameters appends encoded query and keeps existing query`() {
        val applied = RequestParametersFactory.queryParameters(mapOf("q" to "a&b")).apply(base.copy(url = base.url + "?x=1"))
        assertEquals("https://api.test/items?x=1&q=a%26b", applied.url)
    }

    @Test
    fun `queryEncodableParameters flattens a serializable object`() {
        val applied = RequestParametersFactory.queryEncodableParameters(Filter(2, "kotlin", true)).apply(base)
        assertEquals("https://api.test/items?page=2&name=kotlin&active=true", applied.url)
    }

    @Test
    fun `stringRequestParameters parses key value pairs`() {
        val applied = RequestParametersFactory.stringRequestParameters("a=1&b&c=x=y").apply(base)
        assertEquals("https://api.test/items?a=1&b=&c=x%3Dy", applied.url)
    }

    @Test
    fun `empty parameters leave the url untouched`() {
        assertEquals(base, RequestParametersFactory.queryParameters(emptyMap()).apply(base))
        assertEquals(base, RequestParametersFactory.stringRequestParameters("").apply(base))
    }

    @Serializable
    data class Nested(
        val id: Int,
        val tag: String?,
        val inner: Filter,
        val list: List<Int>,
    )

    @Test
    fun `queryEncodableParameters skips nulls and nested values`() {
        val applied = RequestParametersFactory.queryEncodableParameters(Nested(1, null, Filter(1, "n", false), listOf(1))).apply(base)
        assertEquals("https://api.test/items?id=1", applied.url)
    }

    @Test
    fun `appendingQuery keeps fragments and handles a trailing question mark`() {
        assertEquals("https://a/b?k=v#frag", UrlRequest("https://a/b#frag").appendingQuery(mapOf("k" to "v")).url)
        assertEquals("https://a/b?k=v", UrlRequest("https://a/b?").appendingQuery(mapOf("k" to "v")).url)
        assertEquals("https://a/b?k%2F=%C3%A9%2B%3D", UrlRequest("https://a/b").appendingQuery(mapOf("k/" to "é+=")).url)
    }
}
