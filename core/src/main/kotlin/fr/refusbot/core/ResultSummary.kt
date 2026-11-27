package fr.refusbot.core

/** Catégorie lisible du dernier résultat constaté dans une appli. */
enum class ResultCategory {
    VERIFIED,
    UNVERIFIED,
    INCOMPLETE,
    FAILED,
    NO_ACTION,
    ;

    companion object {
        fun of(status: OutcomeStatus): ResultCategory = when (status) {
            OutcomeStatus.OPTIONS_SUCCESS -> VERIFIED
            OutcomeStatus.UNVERIFIED, OutcomeStatus.DIRECT_SUCCESS -> UNVERIFIED
            OutcomeStatus.PARTIAL, OutcomeStatus.HANDED_OVER -> INCOMPLETE
            OutcomeStatus.IMPOSSIBLE, OutcomeStatus.INCOMPLETE_CAPTURE -> FAILED
            OutcomeStatus.OBSERVED, OutcomeStatus.INTERRUPTED -> NO_ACTION
        }
    }
}

/**
 * Bilan des applis COCHÉES, d'après le dernier résultat constaté dans chacune (historique réel
 * d'usage, jamais le laboratoire). Les applis décochées ne sont pas comptées : chaque appli cochée
 * tombe dans exactement une catégorie, ou dans « aucune fenêtre rencontrée ».
 */
data class ResultSummary(
    val selected: Int,
    val byCategory: Map<ResultCategory, Int>,
    val noResult: Int,
) {
    fun count(c: ResultCategory): Int = byCategory[c] ?: 0

    companion object {
        /** @param latest dernier statut constaté par paquet (toutes applis confondues). */
        fun of(monitored: Set<String>, latest: Map<String, OutcomeStatus>): ResultSummary {
            val withResult = monitored.mapNotNull { latest[it] }
            return ResultSummary(
                selected = monitored.size,
                byCategory = withResult.groupingBy { ResultCategory.of(it) }.eachCount(),
                noResult = monitored.count { it !in latest },
            )
        }
    }
}
