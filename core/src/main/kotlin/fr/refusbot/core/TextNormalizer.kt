package fr.refusbot.core

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation du texte affiché à l'écran.
 *
 * - [normalize] : minuscules, sans accents, apostrophes et espaces unifiés, espaces compactés.
 * - [canonical] : [normalize] puis tout caractère non alphanumérique devient une espace.
 *   C'est la forme utilisée pour comparer les libellés aux formules de `rules.json`
 *   (« S'opposer à tout » → « s opposer a tout », « Save & exit » → « save exit »).
 */
object TextNormalizer {
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val INVISIBLE = Regex("[\\u200B-\\u200D\\u2060\\uFEFF\\u00AD]")
    private val SPACE_LIKE = Regex("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]")
    private val APOSTROPHES = Regex("[\\u2018\\u2019\\u201B\\u02BC\\u00B4`]")

    fun normalize(input: CharSequence?): String {
        if (input.isNullOrEmpty()) return ""
        var s = input.toString().lowercase(Locale.ROOT)
        s = s.replace("œ", "oe").replace("æ", "ae").replace("ß", "ss")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        s = s.replace(INVISIBLE, "")
            .replace(SPACE_LIKE, " ")
            .replace(APOSTROPHES, "'")
        return s.replace(WHITESPACE, " ").trim()
    }

    fun canonical(input: CharSequence?): String =
        normalize(input).replace(NON_ALPHANUMERIC, " ").trim()

    /** Vrai si [phrase] (forme canonique) apparaît comme suite de mots entiers dans [haystack] (forme canonique). */
    fun containsPhrase(haystack: String, phrase: String): Boolean {
        if (phrase.isEmpty() || haystack.isEmpty()) return false
        return " $haystack ".contains(" $phrase ")
    }

    fun wordCount(canonical: String): Int =
        if (canonical.isEmpty()) 0 else canonical.count { it == ' ' } + 1
}
