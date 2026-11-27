package fr.refusbot.core

/**
 * Mur « accepter ou payer » (ex. Le Point avec Sourcepoint, diagnostic du 18/11) : les seules sorties
 * sont « Tout accepter » ou « Refuser et s'abonner » (payer). Il n'existe pas de refus gratuit :
 * ce n'est pas un échec de RefusBot, et rien ne doit être cliqué. Sert uniquement à expliquer le
 * résultat ; ne déclenche jamais d'action.
 */
object ConsentOrPay {
    private val DIRECT = listOf(
        "refuser et s abonner", "refuser et vous abonner", "refuser et m abonner",
        "reject and subscribe", "decline and subscribe", "ablehnen und abonnieren",
        // Journal des Femmes (AppConsent, 20/11) : « Refuser et payer 1 € ».
        "refuser et payer", "payer pour refuser", "reject and pay", "decline and pay",
    )
    // « Je m'abonne » / « J'accepte tous les cookies » (site Didomi + abonnement, Chrome, 20/11).
    private val SUBSCRIBE = listOf(
        "s abonner", "abonnez vous", "abonnement", "je m abonne", "m abonner",
        "subscribe", "subscription", "abonnieren", "abo",
    )
    private val ACCEPT = listOf("accepter", "tout accepter", "j accepte", "accept", "accept all", "akzeptieren")

    /** Vrai si les textes (forme canonique) de la fenêtre décrivent un choix « accepter ou payer ». */
    fun detect(canonTexts: List<String>): Boolean {
        val all = canonTexts.joinToString("  ")
        if (DIRECT.any { TextNormalizer.containsPhrase(all, it) }) return true
        return SUBSCRIBE.any { TextNormalizer.containsPhrase(all, it) } && ACCEPT.any { TextNormalizer.containsPhrase(all, it) }
    }

    fun detect(root: UiNode): Boolean = detect(root.subtreeTexts().map(TextNormalizer::canonical))

    /**
     * Comme [detect], mais seulement dans le voisinage des boutons d'acceptation (3 niveaux au-dessus) :
     * un lien « S'abonner » ailleurs sur une page d'actualité (Paddock GP, 20/11/2026) ne fait pas un
     * mur payant.
     */
    fun detectNearAccept(acceptNodes: List<UiNode>): Boolean = acceptNodes.any { node ->
        var scope = node
        repeat(3) { scope = scope.parent ?: return@repeat }
        detect(scope.subtreeTexts().map(TextNormalizer::canonical))
    }

    const val REASON = "mur payant : pas de refus gratuit sur ce site (accepter ou s'abonner)"

    /** Vrai si ce motif d'historique est celui d'un mur payant. */
    fun isPaywall(reason: String?): Boolean = reason == REASON
}
