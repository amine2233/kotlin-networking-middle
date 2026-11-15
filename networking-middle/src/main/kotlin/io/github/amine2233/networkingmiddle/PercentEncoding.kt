package io.github.amine2233.networkingmiddle

private const val HEX_DIGITS = "0123456789ABCDEF"

/** RFC 3986 percent-encoding of the UTF-8 bytes: only the unreserved set `A-Z a-z 0-9 - . _ ~` stays literal. */
internal fun String.urlEncoded(): String =
    buildString {
        for (byte in this@urlEncoded.encodeToByteArray()) {
            val value = byte.toInt() and 0xFF
            if (value.toChar().isUnreserved()) {
                append(value.toChar())
            } else {
                append('%').append(HEX_DIGITS[value shr 4]).append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

/** Decodes `%XX` escapes as UTF-8 and keeps `+` as `+`. A malformed escape such as `100%` keeps the whole string as written. */
internal fun String.percentDecoded(): String {
    if (!hasValidPercentEscapes()) return this
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        if (this[index] == '%') {
            bytes += substring(index + 1, index + 3).toInt(16).toByte()
            index += 3
        } else {
            val end = indexOf('%', index).takeIf { it >= 0 } ?: length
            bytes += substring(index, end).encodeToByteArray().toList()
            index = end
        }
    }
    return bytes.toByteArray().decodeToString()
}

internal fun String.hasValidPercentEscapes(): Boolean =
    indices.all { index ->
        this[index] != '%' || (index + 2 < length && this[index + 1].isHexDigit() && this[index + 2].isHexDigit())
    }

private fun Char.isUnreserved(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
