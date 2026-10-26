package app.kozica.data.routes

import java.text.Normalizer
import java.util.Locale

/**
 * The search key, by `fold()` in `scripts/build-network.py`, which wrote the index: lower case,
 * accents off, the letters no decomposition reaches mapped by hand, anything else a single space.
 */
fun fold(text: String): String {
    val lowered = text.lowercase(Locale.ROOT).map { UNDECOMPOSED[it] ?: it.toString() }.joinToString("")
    val bare = Normalizer.normalize(lowered, Normalizer.Form.NFKD).filter { Character.getType(it) != NON_SPACING_MARK }
    return bare
        .map { if (it in 'a'..'z' || it in '0'..'9') it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

private const val NON_SPACING_MARK = Character.NON_SPACING_MARK.toInt()

private val UNDECOMPOSED =
    mapOf('ł' to "l", 'đ' to "d", 'ð' to "d", 'ø' to "o", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'ı' to "i")
