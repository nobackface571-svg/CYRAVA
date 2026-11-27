package fr.refusbot.core

/**
 * Compte rendu des interrupteurs d'une fenêtre, en mots simples, sans jamais confondre
 * « cliqué » et « désactivé » : seul un interrupteur RELU désactivé après le clic est désactivé.
 */
data class ToggleReport(
    /** Interrupteurs distincts détectés. */
    val seen: Int,
    /** Lus activés à la première lecture. */
    val initiallyOn: Int,
    /** Lus désactivés dès la première lecture (aucun clic nécessaire). */
    val alreadyOff: Int,
    /** Dont l'état n'a pas pu être lu au moins une fois (jamais touchés tant qu'il est inconnu). */
    val unknown: Int,
    /** Sur lesquels un clic a été envoyé. */
    val attempted: Int,
    /** Cliqués puis relus désactivés : seule preuve de désactivation. */
    val verifiedOff: Int,
    /** Cliqués mais relus toujours activés. */
    val stillOn: Int,
    /** Cliqués mais dont le nouvel état n'a pas pu être relu. */
    val unconfirmed: Int,
) {
    /** Exemple : « 12 détectés · 8 activés au départ · 4 déjà désactivés · 8 désactivés et vérifiés ». */
    fun describe(): String {
        if (seen == 0) return "aucun interrupteur détecté"
        val parts = mutableListOf("$seen interrupteur(s) détecté(s)")
        if (initiallyOn > 0) parts += "$initiallyOn activé(s) au départ"
        if (alreadyOff > 0) parts += "$alreadyOff déjà désactivé(s)"
        if (attempted > 0) parts += "$verifiedOff désactivé(s) et vérifié(s) sur $attempted cliqué(s)"
        if (stillOn > 0) parts += "$stillOn resté(s) activé(s)"
        if (unconfirmed > 0) parts += "$unconfirmed cliqué(s) sans relecture possible"
        if (unknown > 0) parts += "$unknown d'état illisible, non touché(s)"
        return parts.joinToString(" · ")
    }

    companion object {
        fun of(o: Outcome) = ToggleReport(
            seen = o.togglesSeen,
            initiallyOn = o.togglesInitiallyOn,
            alreadyOff = o.togglesAlreadyOff,
            unknown = o.togglesUnknown,
            attempted = o.togglesAttempted,
            verifiedOff = o.togglesOff,
            stillOn = o.togglesStillOn,
            unconfirmed = o.togglesUnconfirmed,
        )
    }
}
