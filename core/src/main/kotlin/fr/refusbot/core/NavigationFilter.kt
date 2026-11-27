package fr.refusbot.core

/**
 * Liste noire de navigation, commune à toutes les plateformes (refonte du 21/11).
 *
 * Constat (Didomi / boursorama.com, Firefox) : CYRAVA cliquait en boucle sur des lignes d'accordéon
 * (« + Traitements de données basés sur le consentement », « Catégories de données », noms de
 * partenaires…) ; la liste s'allongeait, la capture devenait incomplète et la validation n'arrivait
 * jamais. Désormais, un élément qui DÉPLIE une section ou ouvre une page sans rapport avec le refus
 * n'est jamais cliqué, quel que soit son type reconnu.
 *
 * Ce filtre s'ajoute au [SafetyGuard] (qui bloque toute acceptation) ; il ne le remplace pas.
 */
object NavigationFilter {

    private val ACTION_KINDS = setOf(ButtonKind.BACK, ButtonKind.CONFIRM, ButtonKind.REFUSE, ButtonKind.GLOBAL_REJECT)

    /** Premier caractère d'une ligne d'accordéon : « + », « - », « › », chevrons… */
    private val ACCORDION_MARKS = setOf(
        '+', '-', '−', '–', '—', '›', '‹', '>', '<', '»', '«',
        '▸', '▹', '▾', '▿', '▼', '►', '▶', '◂', '◀',
        '˅', '˄', '⌄', '⌃', '∨', '∧', '⊕', '⊖', '❯', '❮',
        '⮟', '⮞', '⯆', '⯈', '˃', '˂',
    )

    /** Titres de sections dépliables (forme canonique, libellé exact) : jamais cliqués. */
    private val ACCORDION_TITLES = setOf(
        "categories de donnees", "stockage de l appareil", "voir les infos de l utilisateur", "interet legitime",
        "data categories", "device storage", "legitimate interest", "berechtigtes interesse",
    )

    /** Débuts de libellés de sections dépliables. */
    private val ACCORDION_PREFIXES = listOf(
        "a propos du stockage", "about device storage", "voir les infos", "categories de donnees",
        "stockage de l appareil",
    )

    /**
     * Détails d'une finalité : au premier écran, « En savoir plus » ou « Détails » peut ouvrir les
     * réglages ; une fois les réglages ouverts, ces libellés ne font que déplier une finalité.
     */
    private val PURPOSE_DETAIL_TITLES = setOf(
        "en savoir plus", "voir plus", "afficher plus", "afficher la suite", "details", "detail",
        "plus de details", "voir les details", "afficher les details", "lire la suite",
        "learn more", "read more", "see more", "show more", "more details", "show details", "view details",
        "mehr erfahren", "mehr anzeigen", "details anzeigen", "mas informacion", "piu informazioni",
        "saiba mais", "meer informatie",
    )

    /** Liens vers une page externe (politique de confidentialité, mentions légales…). */
    private val EXTERNAL_PHRASES = listOf(
        "politique de confidentialite", "politique de protection des donnees", "politique cookies",
        "politique de cookies", "politique relative aux cookies", "mentions legales", "conditions generales",
        "privacy policy", "privacy notice", "cookie policy", "terms of use", "datenschutzerklarung",
        "cookie richtlinie", "impressum",
    )

    /**
     * Motif de refus si [button] ne doit jamais être cliqué ; null s'il est autorisé.
     * @param settingsOpened les réglages sont ouverts : « En savoir plus », « Détails » y déplient une finalité.
     */
    fun blockReason(button: ButtonCandidate, settingsOpened: Boolean): String? {
        // Retour, validation et refus : leurs signes (« ‹ Retour ») ne sont pas des accordéons.
        val action = button.kind in ACTION_KINDS
        val target = (button.target as? ClickTarget.Node)?.node
        // « Paramétrer » au premier écran peut être marqué dépliable (il ouvre les réglages sur place).
        val openingSettings = button.kind == ButtonKind.OPTIONS && !settingsOpened
        if (!action && !openingSettings && (button.labelNode.isExpandable || target?.isExpandable == true)) {
            return "section dépliable (accordéon) : jamais dépliée"
        }
        val raws = listOfNotNull(button.labelNode.text, button.labelNode.contentDescription)
        if (!action && raws.any(::startsWithAccordionMark)) return "ligne d'accordéon (« + », « › »…) : jamais dépliée"
        return blockReasonForLabel(button.label, settingsOpened)
    }

    /** Même contrôle sur un libellé canonique seul (tests, fichiers de règles). */
    fun blockReasonForLabel(canonical: String, settingsOpened: Boolean): String? {
        if (canonical in ACCORDION_TITLES || ACCORDION_PREFIXES.any { canonical.startsWith(it) }) {
            return "titre de section dépliable : jamais déplié"
        }
        if (settingsOpened && (canonical in PURPOSE_DETAIL_TITLES || PURPOSE_DETAIL_TITLES.any { canonical.startsWith("$it ") })) {
            return "détails d'une finalité (« En savoir plus », « Voir plus »…) : jamais dépliés"
        }
        if (EXTERNAL_PHRASES.any { TextNormalizer.containsPhrase(canonical, it) }) {
            return "lien vers une page externe : jamais ouvert"
        }
        return null
    }

    /** Vrai si le texte affiché commence par un signe d'accordéon (« + Finalités », « › Partenaires »). */
    fun startsWithAccordionMark(raw: String): Boolean {
        val first = raw.trimStart().firstOrNull() ?: return false
        return first in ACCORDION_MARKS
    }
}
