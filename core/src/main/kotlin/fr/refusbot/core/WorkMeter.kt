package fr.refusbot.core

/**
 * Compteur du travail fait par le service depuis son démarrage (27/11/2026, question de la batterie).
 *
 * Deux sortes de passages :
 * - **lecture sans fenêtre** : l'écran d'une appli surveillée a été lu, aucune fenêtre de cookies
 *   n'y était (c'est le coût « de fond » quand on navigue dans une appli surveillée) ;
 * - **fenêtre traitée** : CYRAVA a agi ou attendu sur une fenêtre de cookies.
 *
 * Ce ne sont que des durées de calcul mesurées, pas une consommation en pourcentage de batterie :
 * seule la page « Batterie » d'Android peut donner celle-ci. Rien n'est enregistré sur le téléphone ;
 * tout repart de zéro au redémarrage du service.
 */
class WorkMeter(private val clock: () -> Long = System::currentTimeMillis) {
    data class Snapshot(
        val since: Long,
        val idleReads: Int,
        val idleMs: Long,
        val windows: Int,
        val windowMs: Long,
    ) {
        val totalMs: Long get() = idleMs + windowMs
    }

    private var since = clock()
    private var idleReads = 0
    private var idleMs = 0L
    private var windows = 0
    private var windowMs = 0L

    /** [acted] : vrai si le passage a porté sur une fenêtre de cookies (action ou attente). */
    @Synchronized
    fun record(durationMs: Long, acted: Boolean) {
        val d = durationMs.coerceAtLeast(0)
        if (acted) {
            windows++
            windowMs += d
        } else {
            idleReads++
            idleMs += d
        }
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(since, idleReads, idleMs, windows, windowMs)

    @Synchronized
    fun reset() {
        since = clock()
        idleReads = 0
        idleMs = 0
        windows = 0
        windowMs = 0
    }
}
