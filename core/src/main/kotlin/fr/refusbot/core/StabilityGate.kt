package fr.refusbot.core

/**
 * Attente qu'une fenêtre ait fini de s'afficher avant de l'analyser.
 *
 * Historique du 18/11 : des fenêtres Didomi analysées 0,3 s après leur apparition, avec 1 à 3 contrôles
 * seulement, puis la même fenêtre de nouveau 1 à 2 s plus tard. On compare donc le nombre de nœuds de
 * deux captures successives : l'écran est « stable » quand il ne bouge plus, ou après [maxChecks]
 * captures (on ne bloque jamais indéfiniment ; la sécurité reste assurée par le moteur).
 */
class StabilityGate(
    private val maxChecks: Int = DEFAULT_MAX_CHECKS,
    private val maxWaitMs: Long = DEFAULT_MAX_WAIT_MS,
) {
    private var previous = -1
    private var checks = 0

    /**
     * Enregistre le nombre de nœuds d'une capture ; vrai si l'on peut analyser maintenant.
     * [elapsedMs] : temps écoulé depuis le début de l'attente. 22/11 : une capture de page lourde
     * prend plus d'une seconde, et 5 contrôles faisaient attendre jusqu'à 6 s avant de commencer.
     */
    fun observe(nodeCount: Int, elapsedMs: Long = 0): Boolean {
        checks++
        val stable = previous >= 0 && nodeCount == previous
        previous = nodeCount
        return stable || checks >= maxChecks || elapsedMs >= maxWaitMs
    }

    companion object {
        const val DEFAULT_MAX_CHECKS = 5
        /** Pause entre deux captures de contrôle (5 contrôles ≈ 1,25 s au plus). */
        const val CHECK_INTERVAL_MS = 250L
        /** Au-delà, on analyse quand même (la sécurité reste assurée par le moteur). */
        const val DEFAULT_MAX_WAIT_MS = 1_500L
    }
}
