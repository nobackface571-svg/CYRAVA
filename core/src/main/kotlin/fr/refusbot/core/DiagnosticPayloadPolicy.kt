package fr.refusbot.core

/** Limite de taille des exports de diagnostic contenant du texte d'écran. */
object DiagnosticPayloadPolicy {
    const val MAX_BYTES = 1_000_000

    fun isAllowed(json: String): Boolean =
        json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES
}
