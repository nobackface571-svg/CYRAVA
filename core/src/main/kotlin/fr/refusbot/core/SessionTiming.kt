package fr.refusbot.core

/**
 * Chronométrage d'une fenêtre traitée (22/11) : où passe le temps sur un vrai téléphone ?
 *
 * Le service d'accessibilité ajoute la durée de chaque étape ; le résumé ([Summary]) est enregistré
 * dans l'historique et ses exports, et dans le diagnostic. Uniquement des durées et des nombres :
 * aucun texte d'écran. Sert à choisir les accélérations, jamais à décider d'un clic.
 */
class SessionTiming(private val clock: () -> Long = System::currentTimeMillis) {

    /** Étapes mesurées. [key] : nom stable dans les fichiers exportés. */
    enum class Phase(val key: String) {
        /** Attente que la fenêtre ait fini de s'afficher, avant la première analyse. */
        STABILITY("attenteAffichage"),

        /** Copie de l'écran (questions posées à l'appli, une par élément). */
        CAPTURE("lectureEcran"),

        /**
         * Lecture de la seule fenêtre de cookies déjà repérée (22/11 au soir), pendant une longue liste :
         * la chaîne de ses ancêtres et son contenu, sans le reste de la page.
         */
        PARTIAL_CAPTURE("lectureFenetre"),

        /** Décision du moteur sur la copie. */
        ANALYSIS("analyse"),

        /** Lots d'interrupteurs décochés (vérifications comprises). */
        TOGGLES("decochage"),

        /** Défilements, balayages et « afficher le bouton » (mesure du déplacement comprise). */
        SCROLL("defilement"),

        /** Autres clics : refus, options, validation, onglets. */
        CLICKS("clics"),

        /** Attentes demandées par le moteur (l'écran n'a pas encore réagi). */
        WAITS("attentes"),

        /** Pauses entre deux étapes. */
        PAUSES("pauses"),
    }

    private val startedAt = clock()
    private val totals = LongArray(Phase.entries.size)
    private val counts = IntArray(Phase.entries.size)
    private var maxNodes = 0

    /** Ajoute une durée mesurée (négative = horloge déréglée : ignorée). */
    fun add(phase: Phase, durationMs: Long) {
        if (durationMs < 0) return
        totals[phase.ordinal] += durationMs
        counts[phase.ordinal]++
    }

    /** Mesure [block] et l'ajoute à [phase]. */
    inline fun <T> measure(phase: Phase, block: () -> T): T {
        val start = now()
        try {
            return block()
        } finally {
            add(phase, now() - start)
        }
    }

    @PublishedApi
    internal fun now(): Long = clock()

    /** Taille de la plus grande copie d'écran de la fenêtre (nombre d'éléments). */
    fun noteNodes(count: Int) {
        if (count > maxNodes) maxNodes = count
    }

    fun summary(): Summary = Summary(
        totalMs = (clock() - startedAt).coerceAtLeast(0),
        phaseMs = Phase.entries.associateWith { totals[it.ordinal] },
        phaseCounts = Phase.entries.associateWith { counts[it.ordinal] },
        maxNodes = maxNodes,
    )

    /** Résumé figé, enregistré avec l'entrée de l'historique. */
    data class Summary(
        /** Du début de la session (première capture) à son résultat. */
        val totalMs: Long,
        val phaseMs: Map<Phase, Long>,
        val phaseCounts: Map<Phase, Int>,
        val maxNodes: Int,
    ) {
        /** Temps non attribué à une étape (gestion interne, enregistrement…). */
        val otherMs: Long get() = (totalMs - phaseMs.values.sum()).coerceAtLeast(0)

        /**
         * Format plat des fichiers exportés, en millisecondes :
         * `{"totalMs":…, "lectureEcranMs":…, "lectureEcranNombre":…, …, "autreMs":…, "elementsMax":…}`.
         */
        fun toMap(): LinkedHashMap<String, Long> {
            val m = LinkedHashMap<String, Long>()
            m["totalMs"] = totalMs
            for (p in Phase.entries) {
                m["${p.key}Ms"] = phaseMs[p] ?: 0L
                m["${p.key}Nombre"] = (phaseCounts[p] ?: 0).toLong()
            }
            m["autreMs"] = otherMs
            m["elementsMax"] = maxNodes.toLong()
            return m
        }

        companion object {
            /** Relit [toMap] ; null si la clé « totalMs » manque (ancienne entrée). */
            fun fromMap(m: Map<String, Long>): Summary? {
                val total = m["totalMs"] ?: return null
                return Summary(
                    totalMs = total,
                    phaseMs = Phase.entries.associateWith { m["${it.key}Ms"] ?: 0L },
                    phaseCounts = Phase.entries.associateWith { (m["${it.key}Nombre"] ?: 0L).toInt() },
                    maxNodes = (m["elementsMax"] ?: 0L).toInt(),
                )
            }
        }
    }
}
