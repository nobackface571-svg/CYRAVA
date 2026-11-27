package fr.refusbot.core

/**
 * Mots qui inversent ou annulent le sens d'une formule : « Do NOT reject », « No LONGER oppose »,
 * « Ne PAS refuser », « ANNULER le refus ».
 *
 * Deux usages :
 * - à l'écran, un libellé qui ajoute l'un de ces mots autour d'une formule connue n'est PAS reconnu
 *   comme cette formule (« Ne pas refuser » n'est pas « Refuser ») ;
 * - à l'import, une formule ajoutée qui en contient un est ambiguë, donc refusée.
 *
 * Les formules livrées qui contiennent une négation (« Continuer sans accepter ») ne sont
 * reconnues que mot pour mot.
 */
object Inversions {
    private val WORDS = listOf(
        "ne", "n", "pas", "non", "ni", "sans", "jamais", "plus", "aucun", "aucune", "rien", "cesser", "arreter",
        "annuler", "retirer", "lever", "not", "no", "don t", "dont", "doesn t", "won t", "cannot", "can t", "never",
        "without", "longer", "none", "nothing", "stop", "cancel", "undo", "withdraw", "revoke", "remove", "nicht",
        "nie", "niemals", "kein", "keine", "keinen", "ohne", "nein", "aufheben", "widerrufen", "sin", "nunca",
        "jamas", "ningun", "ninguno", "ninguna", "cancelar", "anular", "retirar", "senza", "mai", "nessun",
        "nessuno", "nessuna", "annulla", "revoca", "sem", "nao", "nenhum", "nenhuma", "niet", "geen", "nooit",
        "zonder", "annuleren", "intrekken", "impossible", "impossibile", "imposible", "unmoglich", "sauf", "except", "excepte", "excepto", "salvo", "refuse de",
    )

    /** Premier mot d'inversion présent dans un texte canonique, ou null. */
    fun find(canon: String): String? = WORDS.firstOrNull { TextNormalizer.containsPhrase(canon, it) }
}
