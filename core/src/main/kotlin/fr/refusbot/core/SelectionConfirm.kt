package fr.refusbot.core

/**
 * « Autoriser la sélection » (Cookiebot et d'autres) : bouton qui ENREGISTRE les choix affichés.
 * Il contient « autoriser », donc le garde-fou le bloque toujours… sauf dans un cas précis, validé
 * par le propriétaire le 18/11/2026 :
 *
 * - le libellé est EXACTEMENT une des formules ci-dessous (jamais « Autoriser tous ») ;
 * - le bouton est DANS le panneau de consentement analysé, pas ailleurs dans la page ;
 * - l'exploration est terminée (rien de replié, bas de liste atteint, onglets visités) ;
 * - RefusBot est dans les préférences et a VU tous les interrupteurs, tous d'état connu, et plus
 *   aucun n'est activé (sauf les catégories obligatoires que le site interdit de décocher) ;
 * - aucun choix radio douteux, aucune capture incomplète.
 *
 * Valider une sélection où tout est refusé revient alors à refuser. Ces formules sont fixées dans
 * le code : un fichier de règles importé ne peut pas en ajouter.
 */
object SelectionConfirm {
    // Libellés Cookiebot (français, anglais, allemand) uniquement : rien d'autre que « autoriser ».
    val FORMULAS: Set<String> = setOf(
        "autoriser la selection", "autoriser ma selection",
        "allow selection", "allow my selection",
        "auswahl erlauben",
    )

    fun isFormula(canon: String): Boolean = canon in FORMULAS

    /** Premier élément cliquable, visible et actif dont tous les textes sont une de ces formules. */
    fun find(root: UiNode): UiNode? = root.walk().firstOrNull { n ->
        n.isClickable && n.isEnabled && n.isVisibleToUser && !n.bounds.isEmpty &&
            !n.isEditable && !n.isPassword && !n.isAccessibilityDataSensitive && !n.insideProtectedSubtree &&
            n.subtreeTexts().map(TextNormalizer::canonical).filter { it.isNotEmpty() }
                .let { texts -> texts.isNotEmpty() && texts.all(::isFormula) }
    }
}
