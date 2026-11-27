package fr.refusbot.core

/**
 * Motif reconnu dans le texte français écrit par le moteur (ou par le service) : un identifiant
 * stable et les valeurs variables (nombres, libellé cité du site, sous-motif).
 *
 * Le moteur écrit toujours ses motifs en français ; ils restent ainsi dans l'historique enregistré
 * et dans les exports JSON/CSV. L'appli s'en sert seulement pour AFFICHER le motif dans la langue
 * choisie (27/11/2026 : en anglais, l'historique montrait encore des phrases en français).
 *
 * Un argument dont l'identifiant figure dans [nested] est lui-même un motif à traduire.
 */
data class ReasonKey(val id: String, val args: List<String> = emptyList(), val nested: ReasonKey? = null)

object ReasonCatalog {
    private class Rule(val id: String, pattern: String, val nestedArg: Boolean = false) {
        val regex = Regex("^$pattern$")
    }

    private const val N = """(\d+)"""
    private const val Q = """(.*)"""

    // Ordre important : les formules précises avant les formules générales.
    private val rules = listOf(
        // Moteur : fin de session
        Rule("window_left", "fenêtre quittée avant la fin"),
        Rule("left_truncated", "fenêtre de consentement quittée pendant une capture incomplète"),
        Rule("truncated_no_action", "capture incomplète : limite de nœuds ou de profondeur atteinte ; aucune action exécutée"),
        Rule("observation_truncated", """mode observation : aucune action \(capture incomplète\)"""),
        Rule("prudent_truncated", """reconnaissance (\S+) : aucune action en mode prudent \(capture incomplète\)"""),
        Rule("observation", "mode observation : aucune action"),
        Rule("prudent", """reconnaissance (\S+) : aucune action en mode prudent"""),
        Rule("long_list_hold", "liste trop longue : un choix encore accepté est visible ; validation laissée à l'utilisateur"),
        Rule("budget_time", "délai de $N s presque écoulé avant la validation"),
        Rule("timeout", "délai de $N s dépassé"),
        Rule("stall", "plus rien ne bougeait à l'écran depuis $N s"),
        Rule("scroll_impossible", "défilement impossible : certains interrupteurs peuvent être restés hors de la capture"),
        Rule("scroll_global", "défilement impossible : refus global utilisé, la fin de la liste n'a pas été lue"),
        Rule("closed_no_confirm", "la fenêtre s'est fermée sans validation"),
        Rule("budget_actions", "limite de $N actions atteinte avant la validation"),
        Rule("max_actions", "limite de $N actions atteinte"),
        Rule("only_confirm", "seul un bouton de validation est proposé : rien n'a été refusé, aucun clic"),
        Rule("untouched_on", """$N interrupteur\(s\) visible\(s\) encore activé\(s\) et non traité\(s\) ; validation bloquée"""),
        Rule("radio_unknown_checked", "un choix radio coché n'est pas un refus reconnu ; validation bloquée"),
        Rule(
            "radio_unsafe",
            """choix radio non sûrs : $N « Bloquer » non sélectionné\(s\), $N « Autoriser » encore sélectionné\(s\), $N état\(s\) inconnu\(s\) ; validation bloquée""",
        ),
        Rule("doubtful_hold", """$N interrupteur\(s\) impossibles à lire ou à désactiver : validation laissée à l'utilisateur"""),
        Rule("unknown_state_block", """état de $N interrupteur\(s\) non communiqué ; validation bloquée par prudence"""),
        Rule("no_pref_controls", "aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence"),
        Rule("confirm_greyed", "bouton de validation grisé par le site : un choix reste à faire à la main"),
        // Parties non vues (seules ou suivies de la décision)
        Rule("list_not_read", """liste des choix pas lue jusqu'en bas \(temps ou défilements épuisés\)"""),
        Rule("vendors_not_opened", "page des partenaires non ouverte faute de temps"),
        Rule("categories_not_opened", """catégorie\(s\) de cookies non ouverte\(s\)"""),
        Rule("vendors_skipped", "liste des partenaires trop longue pour être lue en entier : non traitée"),
        Rule("unexplored_hold", "$Q : validation laissée à l'utilisateur", nestedArg = true),
        Rule("unexplored_block", "$Q : validation bloquée par prudence", nestedArg = true),
        // Motifs d'échec
        Rule("radio_not_verified", "choix radio « Bloquer / Autoriser » non vérifié"),
        Rule("toggles_blocked", "interrupteurs impossibles à décocher sans risque"),
        Rule("confirm_no_close", "bouton de validation cliqué, mais la fenêtre ne s'est pas fermée"),
        Rule("confirm_not_found_scrolled", "bouton de validation introuvable, même après défilement jusqu'en bas de la fenêtre"),
        Rule("confirm_not_found", "bouton de validation introuvable"),
        Rule("paywall", Regex.escape(ConsentOrPay.REASON)),
        Rule("accept_only", "aucune option de refus \\(seulement « $Q »\\)"),
        Rule("no_refuse", "aucun bouton de refus reconnu"),
        // Garde-fou (SafetyGuard)
        Rule("guard_sensitive", "garde-fou : cible marquée comme sensible bloquée"),
        Rule("guard_protected", "garde-fou : cible située dans un sous-arbre protégé bloquée"),
        Rule("guard_foreign", "garde-fou : texte dans un alphabet non pris en charge bloqué"),
        Rule("guard_token", "garde-fou : bouton contenant « $Q » bloqué"),
        // Refus partiels
        Rule("still_on", """$N interrupteur\(s\) restés activés"""),
        Rule(
            "radio_partial",
            """choix radio non vérifiés : $N « Bloquer » non sélectionné\(s\), $N « Autoriser » sélectionné\(s\), $N état\(s\) inconnu\(s\)""",
        ),
        Rule("blocked_n", """$N interrupteur\(s\) non modifiables sans risque"""),
        Rule("unconfirmed", """$N interrupteur\(s\) cliqué\(s\) dont le nouvel état n'a pas pu être relu"""),
        Rule("unknown_state", """état de $N interrupteur\(s\) non communiqué par l'appli, non vérifiés"""),
        Rule("too_long_unverified", "fenêtre trop longue pour être relue en entier : refus non vérifié"),
        Rule("unreadable_choices", """$N choix « Refuser » / « Non » cliqué\(s\) dont l'appli ne publie pas l'état"""),
        Rule("seen_on_untried", """$N interrupteur\(s\) vu\(s\) activé\(s\) sans avoir pu être désactivé\(s\)"""),
        Rule("master_block", "choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre"),
        Rule("unverified_closed", "bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement"),
        // Service et historique
        Rule("new_interface_prudent", "fenêtre différente de celles déjà refusées dans cette appli : automatisation suspendue \\(profil Prudent\\), à vérifier dans l'onglet Compatibilité"),
        Rule("rules_changed", "règles modifiées pendant le traitement"),
        Rule("emergency_stop", "arrêt d'urgence demandé"),
        Rule("user_pause", "mise en pause par l'utilisateur"),
        Rule("service_interrupted", "service d'accessibilité interrompu par Android"),
        Rule("service_stopped", "service d'accessibilité arrêté par Android"),
        Rule("legacy_entry", "Ancienne entrée : l'état final des préférences n'était pas enregistré\\."),
    )

    /** Identifiants que l'appli doit savoir afficher (voir le test de couverture). */
    val ids: List<String> = rules.map { it.id }

    /** Motifs qui peuvent précéder « : validation laissée à l'utilisateur / bloquée par prudence ». */
    private val nestable = setOf("list_not_read", "vendors_not_opened", "categories_not_opened")

    /**
     * Reconnaît un motif. Renvoie null pour un texte inconnu (motif d'une ancienne version, par
     * exemple) : l'appli affiche alors le texte d'origine tel quel, sans rien inventer.
     */
    fun parse(reason: String?): ReasonKey? {
        val text = reason?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (rule in rules) {
            val m = rule.regex.matchEntire(text) ?: continue
            val args = m.groupValues.drop(1)
            if (rule.nestedArg) {
                val inner = parse(args.single())?.takeIf { it.id in nestable } ?: continue
                return ReasonKey(rule.id, nested = inner)
            }
            return ReasonKey(rule.id, args)
        }
        return null
    }
}
