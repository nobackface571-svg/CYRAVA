package fr.refusbot.core

/**
 * Validation d'un fichier de règles avant utilisation (règles importées par l'utilisateur).
 *
 * Un fichier de règles ne doit JAMAIS pouvoir introduire une acceptation. Trois protections :
 *
 * 1. **Les sections de sécurité viennent toujours de l'appli** ([harden]) : le garde-fou livré est
 *    conservé (le fichier peut seulement y ajouter des formes interdites) et toute la gestion des
 *    interrupteurs (classes, états « coché / décoché », libellés à ne pas toucher) reste celle de
 *    l'appli. Un fichier ne peut donc ni faire passer un interrupteur activé pour désactivé, ni
 *    empêcher de le désactiver.
 * 2. **Preuve positive pour chaque formule ajoutée** : une formule cliquable absente des règles
 *    livrées doit contenir un mot-clé reconnu de sa catégorie (refus, options, validation,
 *    fournisseurs, retour), ne contenir aucune forme interdite ni aucun mot d'acceptation
 *    (« activer », « continuer », « ja »…). Au moindre doute, le fichier est refusé.
 * 3. **Bornes** : taille du fichier, nombre de formules, limites d'actions et de délais.
 *
 * Aucun code n'est exécuté : le fichier ne contient que des listes de formules et des nombres.
 */
object RulesValidator {

    /** Formes interdites que tout fichier de règles doit conserver. */
    val REQUIRED_FORBIDDEN = listOf("accept", "agree", "autoris", "allow", "consentir")

    /**
     * Négations reconnues : une formule de refus qui contient une forme interdite doit aussi
     * contenir une négation (« Continuer SANS accepter », « Do NOT consent », « NIET akkoord »…).
     */
    private val NEGATIONS = listOf(
        "sans", "pas", "ne", "non", "jamais", "not", "no", "don t", "without", "never", "nicht", "ohne", "kein",
        "sin", "senza", "sem", "nao", "niet", "zonder", "geen",
    )

    /**
     * Refus formulés comme une autorisation restreinte (Facebook : « Uniquement autoriser les cookies
     * essentiels »). Liste fermée, comparée mot pour mot : « uniquement » n'est PAS une négation
     * générale, et un fichier importé ne peut de toute façon ajouter aucune formule contenant
     * « autoris » ou « allow ». Le garde-fou bloque toute variante (« … et optionnels »).
     */
    private val ESSENTIAL_ONLY = setOf(
        "uniquement autoriser les cookies essentiels", "autoriser uniquement les cookies essentiels",
        "uniquement autoriser les cookies necessaires", "autoriser uniquement les cookies necessaires",
        "only allow essential cookies", "allow only essential cookies", "allow essential cookies only",
    )

    /** Taille maximale d'un fichier de règles importé (les règles livrées font environ 16 000 caractères). */
    const val MAX_FILE_CHARS = 500_000

    /** Nombre maximal de formules par liste : au-delà, l'analyse de chaque écran deviendrait lente. */
    const val MAX_PHRASES = 2_000
    const val MAX_PLATFORMS = 200
    const val MAX_NAME_LENGTH = 60

    // --- Mots-clés exigés des formules AJOUTÉES par un fichier importé (texte canonique) ---------

    private val REFUSAL_STEMS = listOf(
        "refus", "rejet", "reject", "rejeit", "recus", "rechaz", "rifiut", "declin", "deny", "denie", "deneg",
        "oppos", "oppon", "oponer", "object", "opt out", "ablehn", "verweiger", "widerspr", "weiger", "afwijz", "bezwaar",
        "desactiv", "deactiv", "deaktiv", "disattiv", "desativ", "disable", "uitschakel", "turn off", "switch off",
        "decoch", "uncheck", "deselect", "abwahl", "bloqu", "block",
        "necessa", "necesari", "notwendig", "erforderlich", "noodzakelijk", "essenti", "essenzi", "esencial", "essenci",
    )
    private val CONFIRM_STEMS = listOf(
        "confirm", "conferm", "enregistr", "sauvegard", "valid", "save", "submit", "speicher", "bestatig", "guardar",
        "salva", "opslaan", "bevestig", "appliqu", "apply", "anwenden", "ubernehm", "aplicar", "applica", "toepass",
    )
    private val OPTIONS_STEMS = listOf(
        "option", "opcion", "opco", "opzion", "optie", "paramet", "regl", "prefer", "voorkeur", "personnalis",
        "personaliz", "personalis", "customi", "gerer", "manage", "choisir", "choix", "choice", "choose", "setting",
        "einstell", "verwalt", "anpass", "ajust", "configur", "gestion", "gestisci", "gestire", "gerir", "gerenciar",
        "beheer", "instelling", "aanpass", "impostaz", "definic", "savoir plus", "learn more", "more info",
        "plus d info", "detail", "finalit", "purpose", "zweck", "doeleinden", "afficher", "voir", "show", "view",
        "anzeigen", "mostra", "tonen", "bekijk",
    )
    private val VENDOR_STEMS = listOf(
        "fournisseur", "partenaire", "vendor", "partner", "anbieter", "proveedor", "socio", "fornitor", "fornecedor",
        "parceiro", "leverancier", "aanbieder", "tiers", "third part", "dritt", "tercero", "terze part", "terceiro",
        "derden", "iab",
    )
    private val BACK_STEMS = listOf(
        "retour", "revenir", "preced", "back", "zuruck", "atras", "volver", "indietro", "voltar", "terug",
        "navigate up", "remonter", "anterior", "vorige", "previous",
    )

    /** Mots entiers ressemblant à une acceptation, interdits dans toute formule ajoutée. */
    private val ACCEPT_LIKE_WORDS = listOf(
        "ja", "si", "sim", "claro", "vale", "capito", "entendido", "verstanden", "begrepen", "va bene", "alles klar",
        "weiter", "verder", "next", "suivant", "j ai compris",
    )

    /** Débuts de mots ressemblant à une acceptation (« activer », « continuer », « autorizar »…). */
    private val ACCEPT_LIKE_PREFIXES = listOf(
        "activ", "aktivier", "attiv", "ativ", "enabl", "inschakel", "einschalt", "autoriz", "permit", "continu",
        "poursuiv", "fortfahr", "prosegu", "prossegu", "doorgaan", "proceed",
    )

    /** « Tout valider », « Save all »… : ambigu (peut vouloir dire tout accepter), refusé pour une validation ajoutée. */
    private val QUANTIFIERS = listOf(
        "tout", "tous", "toute", "toutes", "all", "alle", "alles", "todo", "todos", "todas", "tutto", "tutti", "tutte", "tudo",
    )

    /** Longueur maximale d'une formule cliquable ajoutée : au-delà, son sens ne peut pas être garanti. */
    const val MAX_ADDED_WORDS = 8

    data class Report(val errors: List<String>, val warnings: List<String> = emptyList()) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    /**
     * @param baseline règles livrées avec l'appli. Fourni pour un fichier importé : les contrôles
     *   stricts (sections de sécurité inchangées, preuve positive des formules ajoutées) s'appliquent.
     */
    fun validate(rules: Rules, baseline: Rules? = null): Report {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        val guard = SafetyGuard(rules)

        if (rules.version < 1) errors += "numéro de version absent ou invalide"
        REQUIRED_FORBIDDEN.filter { it !in rules.forbiddenSubstrings }.forEach {
            errors += "le garde-fou doit conserver la forme interdite « $it »"
        }
        // Les acceptations doivent toutes être bloquées.
        rules.accept.filter { guard.checkText(it).isAllowed }.forEach {
            errors += "« $it » (acceptation) ne serait pas bloqué par le garde-fou"
        }
        // Les catégories cliquables autres que le refus ne doivent contenir aucune forme interdite.
        (rules.options + rules.confirm + rules.vendorTabs + rules.back).forEach { p ->
            guard.forbiddenToken(p)?.let { errors += "« $p » contient la forme interdite « $it »" }
        }
        // Une formule de refus contenant une forme interdite doit être une négation explicite.
        (rules.refuse + rules.globalReject).forEach { p ->
            val token = guard.forbiddenToken(p) ?: return@forEach
            val negated = p.startsWith("dis") || NEGATIONS.any { TextNormalizer.containsPhrase(p, it) } ||
                p in ESSENTIAL_ONLY
            if (!negated) errors += "« $p » contient « $token » sans négation : ressemble à une acceptation"
        }
        // Les formules radio de refus sont une catégorie à part : elles doivent exprimer un blocage
        // explicite et ne peuvent jamais contenir une acceptation ou une inversion de sens.
        rules.radioRefuse.forEach { phrase ->
            val token = guard.forbiddenToken(phrase)
            val acceptLike = acceptLike(phrase)
            val inversion = Inversions.find(phrase)
            when {
                token != null -> errors += "« $phrase » (radioRefuse) contient la forme interdite « $token »"
                acceptLike != null -> errors += "« $phrase » (radioRefuse) ressemble à une acceptation (« $acceptLike »)"
                inversion != null -> errors += "« $phrase » (radioRefuse) peut inverser le sens (« $inversion »)"
                REFUSAL_STEMS.none { " $phrase".contains(" $it") } -> errors += "« $phrase » (radioRefuse) ne contient aucun mot-clé de blocage reconnu"
            }
        }
        // Une même formule ne peut pas être à la fois acceptation et autre chose.
        val accept = rules.accept.toSet()
        (rules.refuse + rules.globalReject + rules.options + rules.confirm + rules.vendorTabs + rules.back)
            .filter { it in accept }
            .forEach { errors += "« $it » figure aussi parmi les acceptations" }

        if (baseline != null) checkAgainstBaseline(rules, baseline, guard, errors)

        val l = rules.limits
        if (l.maxActions !in 1..200) errors += "maxActions doit être entre 1 et 200"
        if (l.timeoutMs !in 1_000..120_000) errors += "timeoutMs doit être entre 1 et 120 secondes"
        if (l.minDelayMs < 50) errors += "minDelayMs doit être d'au moins 50 ms"
        if (l.maxDelayMs > 10_000) errors += "maxDelayMs ne peut pas dépasser 10 secondes"
        if (l.toggleDelayMs !in 0..2_000) errors += "toggleDelayMs doit être entre 0 et 2 secondes"
        if (l.debounceMs < 100) errors += "debounceMs doit être d'au moins 100 ms"
        if (l.waitAfterClickMs !in 100..10_000) errors += "waitAfterClickMs doit être entre 0,1 et 10 secondes"
        if (l.maxWaits !in 1..20) errors += "maxWaits doit être entre 1 et 20"
        if (l.cooldownMs < 5_000) errors += "cooldownMs doit être d'au moins 5 secondes"
        if (l.failureCooldownMs < 60_000) errors += "failureCooldownMs doit être d'au moins 1 minute"
        if (rules.minHints < 2) errors += "minHints doit valoir au moins 2 (pas d'automatisation sur un seul indice)"
        if (rules.maxExtraWords !in 0..4) errors += "maxExtraWords doit être entre 0 et 4"
        listOf(
            "hints" to rules.hints, "refuse" to rules.refuse, "options" to rules.options,
            "globalReject" to rules.globalReject, "confirm" to rules.confirm, "vendorTabs" to rules.vendorTabs,
            "back" to rules.back, "accept" to rules.accept, "radioRefuse" to rules.radioRefuse, "forbidden.substrings" to rules.forbiddenSubstrings,
            "forbidden.words" to rules.forbiddenWords, "cmpMarkers" to rules.cmpMarkers,
        ).filter { it.second.size > MAX_PHRASES }.forEach {
            errors += "la liste « ${it.first} » dépasse $MAX_PHRASES formules"
        }
        if (rules.platforms.size > MAX_PLATFORMS) errors += "trop de profils de plateformes ($MAX_PLATFORMS au maximum)"
        rules.platforms.filter { it.name.length > MAX_NAME_LENGTH }.forEach {
            errors += "nom de plateforme trop long : « ${it.name.take(MAX_NAME_LENGTH)}… »"
        }
        if (rules.refuse.isEmpty()) warnings += "aucune formule de refus direct"
        return Report(errors, warnings)
    }

    /** Contrôles stricts d'un fichier importé par rapport aux règles livrées. */
    private fun checkAgainstBaseline(rules: Rules, baseline: Rules, guard: SafetyGuard, errors: MutableList<String>) {
        (baseline.forbiddenSubstrings - rules.forbiddenSubstrings.toSet()).forEach {
            errors += "le garde-fou doit conserver la forme interdite « $it »"
        }
        (baseline.forbiddenWords - rules.forbiddenWords.toSet()).forEach {
            errors += "le garde-fou doit conserver le mot interdit « $it »"
        }
        if (!sameSafetySections(rules, baseline)) {
            errors += "la gestion des interrupteurs (section « toggles ») ne peut pas être modifiée par un fichier importé"
        }
        if (rules.minHints < baseline.minHints) errors += "minHints ne peut pas être inférieur à ${baseline.minHints}"
        if (rules.maxExtraWords > baseline.maxExtraWords) errors += "maxExtraWords ne peut pas dépasser ${baseline.maxExtraWords}"

        fun checkAdded(category: String, phrases: List<String>, known: Set<String>, stems: List<String>, noQuantifier: Boolean = false) {
            for (p in phrases) {
                if (p in known) continue
                val token = guard.forbiddenToken(p)
                val acceptLike = acceptLike(p)
                val inversion = Inversions.find(p)
                when {
                    token != null ->
                        errors += "« $p » ($category) : une formule ajoutée ne peut pas contenir la forme interdite « $token »"
                    acceptLike != null ->
                        errors += "« $p » ($category) contient « $acceptLike », qui ressemble à une acceptation"
                    inversion != null ->
                        errors += "« $p » ($category) contient « $inversion », qui peut inverser le sens : formule ambiguë"
                    TextNormalizer.wordCount(p) > MAX_ADDED_WORDS ->
                        errors += "« $p » ($category) est trop longue pour une formule ajoutée ($MAX_ADDED_WORDS mots au maximum)"
                    noQuantifier && QUANTIFIERS.any { TextNormalizer.containsPhrase(p, it) } ->
                        errors += "« $p » ($category) : « tout / all » est ambigu pour une validation"
                    // Le mot-clé doit commencer un mot (« reject… »), pas seulement apparaître dans un autre.
                    stems.none { " $p".contains(" $it") } ->
                        errors += "« $p » ($category) ne contient aucun mot-clé reconnu pour cette catégorie"
                }
            }
        }
        val knownRefusals = baseline.refusalFormulas
        checkAdded("refus", rules.refuse, knownRefusals, REFUSAL_STEMS)
        checkAdded("radioRefuse", rules.radioRefuse, baseline.radioRefuse.toSet(), REFUSAL_STEMS)
        checkAdded("refus global", rules.globalReject, knownRefusals, REFUSAL_STEMS)
        checkAdded("options", rules.options, baseline.options.toSet(), OPTIONS_STEMS)
        checkAdded("validation", rules.confirm, baseline.confirm.toSet(), CONFIRM_STEMS, noQuantifier = true)
        checkAdded("fournisseurs", rules.vendorTabs, baseline.vendorTabs.toSet(), VENDOR_STEMS)
        checkAdded("retour", rules.back, baseline.back.toSet(), BACK_STEMS)
    }

    /** Renvoie le mot ressemblant à une acceptation trouvé dans une formule canonique, ou null. */
    private fun acceptLike(canon: String): String? {
        ACCEPT_LIKE_WORDS.firstOrNull { TextNormalizer.containsPhrase(canon, it) }?.let { return it }
        return canon.split(' ').firstOrNull { word -> ACCEPT_LIKE_PREFIXES.any { word.startsWith(it) } }
    }

    private fun sameSafetySections(a: Rules, b: Rules): Boolean =
        a.alwaysActive.toSet() == b.alwaysActive.toSet() &&
            a.keepCheckedLabels.toSet() == b.keepCheckedLabels.toSet() &&
            a.toggleClasses.toSet() == b.toggleClasses.toSet() &&
            a.toggleExcludedClasses.toSet() == b.toggleExcludedClasses.toSet() &&
            a.checkedStates.toSet() == b.checkedStates.toSet() &&
            a.uncheckedStates.toSet() == b.uncheckedStates.toSet()

    /**
     * Règles réellement utilisées pour un fichier importé : ses formules, mais les sections de
     * sécurité des règles livrées (garde-fou complété, interrupteurs, seuils de détection).
     */
    fun harden(imported: Rules, baseline: Rules): Rules = imported.copy(
        forbiddenSubstrings = (baseline.forbiddenSubstrings + imported.forbiddenSubstrings).distinct(),
        forbiddenWords = (baseline.forbiddenWords + imported.forbiddenWords).distinct(),
        alwaysActive = baseline.alwaysActive,
        keepCheckedLabels = baseline.keepCheckedLabels,
        toggleClasses = baseline.toggleClasses,
        toggleExcludedClasses = baseline.toggleExcludedClasses,
        checkedStates = baseline.checkedStates,
        uncheckedStates = baseline.uncheckedStates,
        // Ces champs influencent la reconnaissance d'un écran comme consentement. Un fichier
        // importé ne peut pas élargir les écrans où l'automatisation agit.
        hints = baseline.hints,
        cmpMarkers = baseline.cmpMarkers,
        platforms = baseline.platforms,
        buttonIds = baseline.buttonIds,
        minHints = maxOf(imported.minHints, baseline.minHints),
        maxExtraWords = minOf(imported.maxExtraWords, baseline.maxExtraWords),
    )

    /**
     * Analyse et valide un texte JSON ; renvoie les règles seulement si elles sont sûres.
     *
     * @param baseline règles livrées avec l'appli : à fournir pour tout fichier importé. Les règles
     *   renvoyées sont alors « durcies » ([harden]) et contrôlées strictement.
     */
    fun parseAndValidate(json: String, baseline: Rules? = null): Pair<Rules?, Report> {
        if (json.length > MAX_FILE_CHARS) {
            return null to Report(listOf("fichier trop volumineux (plus de $MAX_FILE_CHARS caractères)"))
        }
        val parsed = try {
            Rules.fromJson(json)
        } catch (e: IllegalArgumentException) {
            return null to Report(listOf("fichier illisible : ${e.message}"))
        }
        val notes = ArrayList<String>()
        val rules = if (baseline != null) {
            if (!sameSafetySections(parsed, baseline) || parsed.minHints < baseline.minHints || parsed.maxExtraWords > baseline.maxExtraWords ||
                parsed.hints != baseline.hints || parsed.cmpMarkers != baseline.cmpMarkers || parsed.platforms != baseline.platforms) {
                notes += "les réglages qui influencent la sécurité (interrupteurs, seuils, indices et profils de détection) sont ignorés : ceux de l'appli sont conservés"
            }
            harden(parsed, baseline)
        } else {
            parsed
        }
        val report = validate(rules, baseline)
        return (if (report.isValid) rules else null) to report.copy(warnings = notes + report.warnings)
    }
}
