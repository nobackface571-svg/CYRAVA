package fr.refusbot.core

/** Limites anti-boucle et temporisations (valeurs par défaut = cahier des charges). */
data class Limits(
    /** Nombre maximal d'étapes (un clic, un lot d'interrupteurs ou un défilement) par fenêtre. */
    val maxActions: Int = 40,
    /** Durée maximale d'une session sur une fenêtre de consentement. */
    val timeoutMs: Long = 30_000,
    /** Pause minimale/maximale entre deux étapes. */
    val minDelayMs: Long = 150,
    val maxDelayMs: Long = 400,
    /** Pause entre deux interrupteurs décochés au sein d'un même lot. */
    val toggleDelayMs: Long = 40,
    /** Regroupement des événements d'accessibilité. */
    val debounceMs: Long = 300,
    /** Attente quand l'écran n'a pas encore réagi à un clic. */
    val waitAfterClickMs: Long = 300,
    /** Nombre d'attentes successives avant de considérer qu'un clic n'a rien fait. */
    val maxWaits: Int = 3,
    /** Après un refus réussi, on ignore la même fenêtre pendant ce délai. */
    val cooldownMs: Long = 20_000,
    /** Après un échec, on ne réessaie pas la même fenêtre avant ce délai (pas de notifications en rafale). */
    val failureCooldownMs: Long = 600_000,
)

/**
 * Profil d'une plateforme de consentement (CMP) : reconnue par des fragments d'identifiants
 * techniques (viewId / id HTML) ou par des formulations qui lui sont propres.
 */
data class PlatformProfile(
    val name: String,
    /** Fragments (minuscules) recherchés dans les identifiants de vues. */
    val markers: List<String>,
    /** Formulations canoniques caractéristiques. */
    val phrases: List<String>,
)

/**
 * Règles de détection et de décision, chargées depuis `assets/rules.json`.
 * Toutes les formules sont stockées sous forme canonique ([TextNormalizer.canonical]).
 */
data class Rules(
    val version: Int,
    val limits: Limits,
    /** Nombre minimal d'indices distincts pour reconnaître une fenêtre de consentement. */
    val minHints: Int,
    val hints: List<String>,
    /** Refus direct (1er niveau) : « Tout refuser », « Continuer sans accepter »… */
    val refuse: List<String>,
    /** Ouverture des options : « Gérer les options », « Personnaliser »… */
    val options: List<String>,
    /** Boutons globaux des écrans de réglages : « Tout désactiver », « S'opposer à tout »… */
    val globalReject: List<String>,
    /** Validation : « Confirmer les choix », « Enregistrer »… */
    val confirm: List<String>,
    /** Onglets/liens vers la liste des fournisseurs ou partenaires. */
    val vendorTabs: List<String>,
    /** Retour à la page précédente (sous-page fournisseurs sans bouton de validation). */
    val back: List<String>,
    /** Boutons d'acceptation : servent UNIQUEMENT à reconnaître la fenêtre, jamais cliqués. */
    val accept: List<String>,
    /** Formules « bloquer / refuser » propres aux boutons radio de choix exclusifs. */
    val radioRefuse: List<String> = emptyList(),
    /** Garde-fou : sous-chaînes interdites (texte canonique). */
    val forbiddenSubstrings: List<String>,
    /** Garde-fou : mots ou expressions interdits (mots entiers). */
    val forbiddenWords: List<String>,
    /** Interrupteurs à ne jamais toucher (« Toujours actif », « Strictement nécessaire »…). */
    val alwaysActive: List<String>,
    /** Libellés d'interrupteurs qu'il ne faut pas décocher (ex. bouton segmenté « Refuser » coché). */
    val keepCheckedLabels: List<String>,
    /** Fragments de nom de classe identifiant un interrupteur / une case à cocher. */
    val toggleClasses: List<String>,
    /** Fragments de nom de classe exclus des interrupteurs (boutons radio…). */
    val toggleExcludedClasses: List<String>,
    /** Nombre de mots supplémentaires tolérés autour d'une formule (« Tout refuser et fermer »). */
    val maxExtraWords: Int,
    /** Fragments d'identifiants (viewId) propres aux CMP (didomi, onetrust…) : comptent comme indices. */
    val cmpMarkers: List<String> = emptyList(),
    /** Descriptions d'état signifiant « coché / activé » (WebView récentes, Compose…). */
    val checkedStates: List<String> = emptyList(),
    /** Descriptions d'état signifiant « décoché / désactivé » (prioritaires). */
    val uncheckedStates: List<String> = emptyList(),
    /** Profils des plateformes connues (ordre = priorité de reconnaissance). */
    val platforms: List<PlatformProfile> = emptyList(),
    /**
     * Identifiants techniques (minuscules) de boutons dont la fonction est documentée par la CMP
     * elle-même (ex. Didomi « btn-toggle-disagree » = Tout refuser). Seuls REFUSE, GLOBAL_REJECT,
     * OPTIONS et CONFIRM sont possibles ; jamais ACCEPT. Toujours issus des règles livrées.
     */
    val buttonIds: Map<ButtonKind, List<String>> = emptyMap(),
) {
    /**
     * État coché d'après la description d'état : vrai, faux, ou null si elle ne dit rien.
     * Certaines WebView publient l'état ainsi plutôt que par isChecked().
     */
    fun checkedFromDescription(description: CharSequence?): Boolean? {
        val canon = TextNormalizer.canonical(description)
        if (canon.isEmpty()) return null
        if (isIndeterminateDescription(canon)) return null
        if (uncheckedStates.any { canon == it || TextNormalizer.containsPhrase(canon, it) }) return false
        if (checkedStates.any { canon == it || TextNormalizer.containsPhrase(canon, it) }) return true
        return null
    }

    fun isIndeterminateDescription(description: CharSequence?): Boolean =
        isIndeterminateDescription(TextNormalizer.canonical(description))

    private fun isIndeterminateDescription(canon: String): Boolean =
        listOf("partially checked", "partially selected", "mixed", "partiellement coche")
            .any { canon == it || TextNormalizer.containsPhrase(canon, it) }

    /** Toutes les formules de refus explicites : seules exceptions au garde-fou. */
    val refusalFormulas: Set<String> by lazy { (refuse + globalReject).toSet() }

    companion object {
        fun fromJson(json: String): Rules = RulesParser.parse(json)
    }
}

object RulesParser {

    fun parse(json: String): Rules {
        val root = MiniJson.parse(json) as? Map<*, *>
            ?: throw MiniJson.ParseException("rules.json : objet racine attendu")
        val buttons = root["buttons"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val forbidden = root["forbidden"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val toggles = root["toggles"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val limitsMap = root["limits"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val d = Limits()
        val limits = Limits(
            maxActions = limitsMap.long("maxActions", d.maxActions.toLong()).toInt(),
            timeoutMs = limitsMap.long("timeoutMs", d.timeoutMs),
            minDelayMs = limitsMap.long("minDelayMs", d.minDelayMs),
            maxDelayMs = limitsMap.long("maxDelayMs", d.maxDelayMs),
            toggleDelayMs = limitsMap.long("toggleDelayMs", d.toggleDelayMs),
            debounceMs = limitsMap.long("debounceMs", d.debounceMs),
            waitAfterClickMs = limitsMap.long("waitAfterClickMs", d.waitAfterClickMs),
            maxWaits = limitsMap.long("maxWaits", d.maxWaits.toLong()).toInt(),
            cooldownMs = limitsMap.long("cooldownMs", d.cooldownMs),
            failureCooldownMs = limitsMap.long("failureCooldownMs", d.failureCooldownMs),
        )
        require(limits.maxActions > 0 && limits.timeoutMs > 0) { "rules.json : limites invalides" }
        require(limits.minDelayMs <= limits.maxDelayMs) { "rules.json : minDelayMs > maxDelayMs" }

        return Rules(
            version = root.long("version", 1).toInt(),
            limits = limits,
            minHints = root.long("minHints", 2).toInt(),
            hints = root.phrases("hints"),
            refuse = buttons.phrases("refuse"),
            options = buttons.phrases("options"),
            globalReject = buttons.phrases("globalReject"),
            confirm = buttons.phrases("confirm"),
            vendorTabs = buttons.phrases("vendorTabs"),
            back = buttons.phrases("back"),
            accept = buttons.phrases("accept"),
            radioRefuse = buttons.phrases("radioRefuse"),
            forbiddenSubstrings = forbidden.phrases("substrings"),
            forbiddenWords = forbidden.phrases("words"),
            alwaysActive = toggles.phrases("alwaysActive"),
            keepCheckedLabels = toggles.phrases("keepCheckedLabels"),
            toggleClasses = toggles.rawList("classes").map { it.lowercase() },
            toggleExcludedClasses = toggles.rawList("excludedClasses").map { it.lowercase() },
            maxExtraWords = root.long("maxExtraWords", 2).toInt(),
            cmpMarkers = root.rawList("cmpMarkers").map { it.lowercase() },
            checkedStates = toggles.phrases("checkedStates"),
            uncheckedStates = toggles.phrases("uncheckedStates"),
            buttonIds = parseButtonIds(root["buttonIds"]),
            platforms = (root["platforms"] as? List<*>).orEmpty().mapNotNull { p ->
                val m = p as? Map<*, *> ?: return@mapNotNull null
                val name = (m["name"] as? String)?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                PlatformProfile(name, m.rawList("markers").map { it.lowercase() }, m.phrases("phrases"))
            },
        ).also {
            require(it.forbiddenSubstrings.isNotEmpty()) { "rules.json : le garde-fou ne peut pas être vide" }
            require(it.refuse.isNotEmpty() && it.confirm.isNotEmpty()) { "rules.json : listes de boutons vides" }
        }
    }

    private fun parseButtonIds(value: Any?): Map<ButtonKind, List<String>> {
        val map = value as? Map<*, *> ?: return emptyMap()
        val result = LinkedHashMap<ButtonKind, List<String>>()
        for ((key, list) in map) {
            val kind = when (key) {
                "refuse" -> ButtonKind.REFUSE
                "globalReject" -> ButtonKind.GLOBAL_REJECT
                "options" -> ButtonKind.OPTIONS
                "confirm" -> ButtonKind.CONFIRM
                else -> null // ACCEPT et toute autre catégorie sont ignorés.
            } ?: continue
            val ids = (list as? List<*>).orEmpty()
                .mapNotNull { (it as? String)?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
            if (ids.isNotEmpty()) result[kind] = ids
        }
        return result
    }

    private fun Map<*, *>.long(key: String, default: Long): Long = (this[key] as? Number)?.toLong() ?: default

    private fun Map<*, *>.rawList(key: String): List<String> =
        (this[key] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) } ?: emptyList()

    private fun Map<*, *>.phrases(key: String): List<String> =
        rawList(key).map(TextNormalizer::canonical).filter { it.isNotEmpty() }.distinct()
}
