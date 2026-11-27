package fr.refusbot.core

/**
 * Niveau de compatibilité d'une appli, déduit uniquement de l'historique réel (jamais estimé).
 * Libellés et explications traduits : `app/.../ui/Labels.kt`.
 */
enum class Compatibility {
    COMPATIBLE,
    REFUS_CLIQUE,
    PARTIELLE,
    A_VERIFIER,
    NON_PRISE_EN_CHARGE,
    OBSERVEE,
    INCONNUE,
}

object CompatibilityRating {
    /** Nombre de fenêtres récentes prises en compte. */
    const val WINDOW = 5

    /**
     * @param recent statuts de l'appli, du plus récent au plus ancien.
     * @param lastInterfaceChanged la dernière fenêtre (hors interruptions) avait une structure différente
     *   de toutes celles déjà refusées dans cette appli.
     */
    fun rate(recent: List<OutcomeStatus>, lastInterfaceChanged: Boolean): Compatibility {
        // Une interruption (pause, arrêt d'urgence, appli quittée) ne dit rien de la compatibilité.
        val relevant = recent.filter { it != OutcomeStatus.INTERRUPTED }.take(WINDOW)
        if (relevant.isEmpty()) return Compatibility.INCONNUE
        // Interface modifiée et pas (encore) de refus vérifié dessus : à vérifier.
        if (lastInterfaceChanged && !relevant.first().isClickedRefusal) return Compatibility.A_VERIFIER
        val acted = relevant.filter { it != OutcomeStatus.OBSERVED }
        return when {
            acted.isEmpty() -> Compatibility.OBSERVEE
            acted.all { it.isVerified } -> Compatibility.COMPATIBLE
            acted.none { it.isRefusal } -> Compatibility.NON_PRISE_EN_CHARGE
            acted.all { it.isClickedRefusal } -> Compatibility.REFUS_CLIQUE
            else -> Compatibility.PARTIELLE
        }
    }

    /**
     * Comme [rate], à partir de l'historique (statut, motif). Un mur « accepter ou payer » est un choix
     * du site, pas une faiblesse de RefusBot : il ne compte pas dans la compatibilité de l'appli
     * (sinon Chrome passe « Partielle » dès qu'un site à mur payant est visité).
     */
    fun rateHistory(recent: List<Pair<OutcomeStatus, String?>>, lastInterfaceChanged: Boolean): Compatibility =
        rate(recent.filterNot { ConsentOrPay.isPaywall(it.second) }.map { it.first }, lastInterfaceChanged)

    /** Refus mené à son terme (vérifié ou non), par opposition à partiel, impossible ou observé. */
    private val OutcomeStatus.isClickedRefusal: Boolean
        get() = isVerified || this == OutcomeStatus.UNVERIFIED || this == OutcomeStatus.DIRECT_SUCCESS
}
