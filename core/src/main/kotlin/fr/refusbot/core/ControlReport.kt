package fr.refusbot.core

/**
 * Pourquoi un interrupteur n'a pas pu être décoché ET vérifié. Sert à nommer, dans le diagnostic,
 * chaque interrupteur resté activé ou douteux, au lieu du seul compteur actuel.
 */
enum class ControlIssueKind(val explication: String) {
    /** Relu encore ACTIVÉ après le clic : l'appli a ignoré ou annulé le clic. */
    STILL_ON("relu encore activé après le clic"),

    /** L'appli ne publie jamais l'état de cet interrupteur : impossible de savoir s'il est activé. */
    UNKNOWN_STATE("état jamais publié par l'appli : interrupteur jamais touché"),

    /** Le garde-fou a empêché le clic (cible ambiguë, risque d'activer autre chose…). */
    BLOCKED("clic empêché par le garde-fou"),

    /** Cliqué, mais le nouvel état n'a pas pu être relu avant la fin de la session. */
    UNCONFIRMED("cliqué, mais le nouvel état n'a pas été relu"),

    /** Vu activé mais jamais traité (grisé, hors de portée, ou limite de la session atteinte). */
    UNTRIED("vu activé mais jamais traité"),
}

/**
 * Un interrupteur non vérifié, nommé, avec la raison. Le libellé est du texte d'écran :
 * réservé au fichier de diagnostic, jamais copié dans l'historique (règle de confidentialité).
 */
data class ControlIssue(
    /** Libellé lisible de l'interrupteur (nom de la finalité ou du fournisseur). */
    val label: String,
    val kind: ControlIssueKind,
    /** Signature technique de l'interrupteur, pour le recouper avec la section « analysis ». */
    val signature: String,
) {
    companion object {
        /** Plafond de la liste : une liste de 800 partenaires ne doit pas gonfler le diagnostic. */
        const val MAX_ISSUES = 200
    }
}
