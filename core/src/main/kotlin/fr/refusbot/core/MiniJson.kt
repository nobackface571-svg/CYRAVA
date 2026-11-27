package fr.refusbot.core

/**
 * Lecteur/écrivain JSON minimal (aucune dépendance) pour `rules.json` et l'export diagnostic.
 * Objets → [Map], tableaux → [List], nombres → [Long] ou [Double].
 */
object MiniJson {

    class ParseException(message: String) : IllegalArgumentException(message)

    /**
     * Profondeur maximale d'imbrication acceptée à la lecture : un fichier importé du type
     * « [[[[… » ne doit pas pouvoir faire déborder la pile et planter l'appli.
     */
    const val MAX_DEPTH = 64

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWhitespace()
        val value = p.readValue()
        p.skipWhitespace()
        if (!p.atEnd()) throw p.error("contenu inattendu après la valeur JSON")
        return value
    }

    fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == '\u2028' || c == '\u2029') {
                    sb.append("\\u").append(String.format("%04x", c.code))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** Sérialise Map/List/String/Number/Boolean/null, avec indentation optionnelle. */
    fun write(value: Any?, indent: Int = 2): String {
        val sb = StringBuilder()
        writeTo(sb, value, indent, 0)
        return sb.toString()
    }

    private fun writeTo(sb: StringBuilder, value: Any?, indent: Int, level: Int) {
        // Même borne qu'à la lecture : une structure construite par ailleurs (ou un cycle) ne doit
        // pas pouvoir faire déborder la pile à l'écriture.
        if (level > MAX_DEPTH) throw ParseException("imbrication trop profonde à l'écriture (plus de $MAX_DEPTH niveaux)")
        fun newline(l: Int) {
            if (indent > 0) {
                sb.append('\n')
                repeat(l * indent) { sb.append(' ') }
            }
        }
        when (value) {
            null -> sb.append("null")
            is String -> sb.append(quote(value))
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value)
            is Number -> {
                val d = value.toDouble()
                if (d.isNaN() || d.isInfinite()) sb.append("null") else sb.append(d)
            }
            is Map<*, *> -> {
                if (value.isEmpty()) {
                    sb.append("{}")
                    return
                }
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    newline(level + 1)
                    sb.append(quote(k.toString())).append(if (indent > 0) ": " else ":")
                    writeTo(sb, v, indent, level + 1)
                }
                newline(level)
                sb.append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) {
                    sb.append("[]")
                    return
                }
                sb.append('[')
                items.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    newline(level + 1)
                    writeTo(sb, v, indent, level + 1)
                }
                newline(level)
                sb.append(']')
            }
            is IntArray -> writeTo(sb, value.toList(), indent, level)
            else -> sb.append(quote(value.toString()))
        }
    }

    private class Parser(private val s: String) {
        private var i = 0
        private var depth = 0

        fun atEnd() = i >= s.length

        fun error(msg: String): ParseException {
            val line = s.substring(0, minOf(i, s.length)).count { it == '\n' } + 1
            return ParseException("JSON invalide (ligne $line) : $msg")
        }

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw error("fin de texte inattendue")
            return when (val c = s[i]) {
                '{' -> nested { readObject() }
                '[' -> nested { readArray() }
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else throw error("caractère inattendu '$c'")
            }
        }

        private inline fun <T> nested(block: () -> T): T {
            if (++depth > MAX_DEPTH) throw error("imbrication trop profonde (plus de $MAX_DEPTH niveaux)")
            try {
                return block()
            } finally {
                depth--
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!s.startsWith(word, i)) throw error("littéral invalide")
            i += word.length
            return value
        }

        /**
         * Nombre JSON strict : -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?
         * La lecture caractère par caractère évite d'accepter des formes invalides
         * (« 01 », « 1. », « 1e », « --5 ») qu'une lecture gloutonne laissait passer ou rejetait
         * avec un message trompeur.
         */
        private fun readNumber(): Number {
            val start = i
            if (i < s.length && s[i] == '-') i++
            // Partie entière : « 0 » seul, ou un chiffre 1-9 suivi de chiffres (pas de zéro initial).
            if (atEnd() || !s[i].isDigit()) throw error("nombre invalide")
            if (s[i] == '0') {
                i++
            } else {
                while (i < s.length && s[i].isDigit()) i++
            }
            var isDecimal = false
            if (i < s.length && s[i] == '.') {
                isDecimal = true
                i++
                if (atEnd() || !s[i].isDigit()) throw error("chiffre attendu après le point décimal")
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && s[i] in "eE") {
                isDecimal = true
                i++
                if (i < s.length && s[i] in "+-") i++
                if (atEnd() || !s[i].isDigit()) throw error("chiffre attendu dans l'exposant")
                while (i < s.length && s[i].isDigit()) i++
            }
            val token = s.substring(start, i)
            return if (!isDecimal) {
                token.toLongOrNull() ?: token.toDoubleOrNull() ?: throw error("nombre invalide '$token'")
            } else {
                token.toDoubleOrNull() ?: throw error("nombre invalide '$token'")
            }
        }

        private fun readString(): String {
            i++ // guillemet ouvrant
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw error("chaîne non terminée")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw error("échappement incomplet")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val first = readHexEscape()
                                // Paire de substitution : un « high surrogate » immédiatement suivi de
                                // « \uDC00-\uDFFF » forme un seul caractère (émojis, etc.). Sinon le
                                // surrogate est conservé tel quel, comme le font les parseurs courants.
                                val pairStart = i
                                if (first.isHighSurrogate() && i + 1 < s.length && s[i] == '\\' && s[i + 1] == 'u') {
                                    i += 2
                                    val second = readHexEscape()
                                    if (second.isLowSurrogate()) {
                                        sb.append(first).append(second)
                                    } else {
                                        i = pairStart
                                        sb.append(first)
                                    }
                                } else {
                                    sb.append(first)
                                }
                            }
                            else -> throw error("échappement inconnu '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        /** Lit les 4 chiffres hexadécimaux d'une séquence « \uXXXX » (positionnée après le « u »). */
        private fun readHexEscape(): Char {
            if (i + 4 > s.length) throw error("séquence \\u incomplète")
            val hex = s.substring(i, i + 4)
            val code = hex.toIntOrNull(16) ?: throw error("séquence \\u invalide")
            i += 4
            return code.toChar()
        }

        private fun readArray(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == ']') {
                i++
                return out
            }
            while (true) {
                out.add(readValue())
                skipWhitespace()
                if (atEnd()) throw error("tableau non terminé")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw error("',' ou ']' attendu")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == '}') {
                i++
                return out
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || s[i] != '"') throw error("clé attendue")
                val key = readString()
                skipWhitespace()
                if (atEnd() || s[i] != ':') throw error("':' attendu")
                i++
                out[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw error("objet non terminé")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw error("',' ou '}' attendu")
                }
            }
        }
    }
}
