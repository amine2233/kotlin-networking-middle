package io.github.amine2233.networkingmiddle

import kotlin.test.Test
import kotlin.test.assertEquals

class PercentEncodingTest {
    @Test
    fun `urlEncoded keeps the unreserved set and percent-encodes every other utf8 byte`() {
        listOf(
            "abcXYZ019" to "abcXYZ019",
            "-._~" to "-._~",
            "*" to "%2A",
            "+" to "%2B",
            "a b" to "a%20b",
            "%" to "%25",
            ":/?#[]@" to "%3A%2F%3F%23%5B%5D%40",
            "!$&'()*+,;=" to "%21%24%26%27%28%29%2A%2B%2C%3B%3D",
            "é" to "%C3%A9",
            "ñÇ" to "%C3%B1%C3%87",
            "😀" to "%F0%9F%98%80",
            "" to "",
        ).forEach { (input, expected) -> assertEquals(expected, input.urlEncoded(), "urlEncoded of <$input>") }
    }

    @Test
    fun `percentDecoded decodes utf8 escapes and keeps malformed input as written`() {
        listOf(
            "a%20b" to "a b",
            "a+b" to "a+b",
            "%7E%2a" to "~*",
            "%C3%A9" to "é",
            "%F0%9F%98%80" to "😀",
            "100%" to "100%",
            "%zz" to "%zz",
            "%4" to "%4",
            "%C3" to "�",
        ).forEach { (input, expected) -> assertEquals(expected, input.percentDecoded(), "percentDecoded of <$input>") }
    }
}
