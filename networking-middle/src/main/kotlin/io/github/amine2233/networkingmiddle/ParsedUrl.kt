package io.github.amine2233.networkingmiddle

/** The raw (still percent-encoded) parts of an absolute `scheme://host` URL. */
internal data class ParsedUrl(
    val scheme: String,
    val host: String,
    val path: String,
    val query: String?,
    val fragment: String?,
)

private const val DISALLOWED_CHARACTERS = "\"<>\\^`{|}"

/** Returns `null` for whitespace, control or disallowed characters, malformed `%` escapes, a missing scheme or an empty host. */
internal fun parseUrlOrNull(url: String): ParsedUrl? {
    if (url.any { it.isWhitespace() || it.isISOControl() || it in DISALLOWED_CHARACTERS }) return null
    if (!url.hasValidPercentEscapes()) return null

    val scheme = url.substringBefore(':', missingDelimiterValue = "")
    if (!scheme.isValidScheme()) return null
    val afterScheme = url.substring(scheme.length + 1)
    if (!afterScheme.startsWith("//")) return null

    val rest = afterScheme.substring(2)
    val authorityEnd = rest.indexOfFirst { it in "/?#" }.takeIf { it >= 0 } ?: rest.length
    val host = rest.substring(0, authorityEnd).hostOrNull() ?: return null

    val target = rest.substring(authorityEnd)
    val fragment = target.substringAfter('#', missingDelimiterValue = "").takeIf { '#' in target }
    val beforeFragment = target.substringBefore('#')
    val query = beforeFragment.substringAfter('?', missingDelimiterValue = "").takeIf { '?' in beforeFragment }
    val path = beforeFragment.substringBefore('?')
    return ParsedUrl(scheme, host, path, query, fragment)
}

private fun String.isValidScheme(): Boolean =
    isNotEmpty() &&
        first().isAsciiLetter() &&
        all { it.isAsciiLetter() || it in '0'..'9' || it in "+-." }

/** Strips `userinfo@` and `:port`; an IPv6 literal keeps its brackets. */
private fun String.hostOrNull(): String? {
    val hostAndPort = substringAfterLast('@')
    val host =
        if (hostAndPort.startsWith("[")) {
            hostAndPort.substringBefore(']', missingDelimiterValue = "").takeIf { it.isNotEmpty() }?.let { "$it]" }
        } else {
            hostAndPort.substringBefore(':')
        }
    return host?.takeIf { it.isNotEmpty() && it != "[]" }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
