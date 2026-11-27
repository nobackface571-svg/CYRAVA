package fr.refusbot.core

/**
 * Lecture rapide (relire seulement la fenêtre de cookies) : à garder seulement si elle est VRAIMENT
 * plus rapide qu'une lecture complète de l'écran.
 *
 * Galaxy Defense (22/11, 17 h 58, ci.11) : 6 lectures rapides ont pris 68 s (11 s chacune), contre
 * 5,8 s pour une lecture complète du même écran. La fenêtre y est presque tout l'écran (3 572 éléments),
 * et la relire à part coûtait plus cher. Plutôt que de deviner la cause, on MESURE : dès qu'une lecture
 * rapide est plus lente que la dernière lecture complète, elle est abandonnée pour toute la session.
 */
class PartialReadPolicy {
    private var lastFullMs = -1L

    /** Faux une fois la lecture rapide abandonnée pour cette session. */
    var enabled: Boolean = true
        private set

    /** Durée d'une lecture complète de l'écran. */
    fun recordFull(ms: Long) {
        lastFullMs = ms
    }

    /**
     * Durée d'une lecture rapide. Sans lecture complète mesurée pour comparer, on la garde ; sinon, elle
     * doit être plus rapide que la lecture complète (à [MARGIN_MS] près, pour ignorer les petits écarts).
     */
    fun recordPartial(ms: Long) {
        if (lastFullMs >= 0 && ms > lastFullMs + MARGIN_MS) enabled = false
    }

    companion object {
        const val MARGIN_MS = 50L
    }
}
