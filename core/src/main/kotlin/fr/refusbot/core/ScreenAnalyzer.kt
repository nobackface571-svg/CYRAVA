package fr.refusbot.core

const val PLATFORM_UNKNOWN = "inconnue"
const val PLATFORM_GENERIC_TCF = "TCF générique"

/** Niveau de certitude qu'une fenêtre est bien une fenêtre de consentement. */
enum class Confidence(val label: String) {
    /** Plateforme identifiée, ou nombreux indices et boutons de refus/réglages reconnus. */
    CERTAIN("certaine"),

    /** Indices suffisants et au moins un bouton connu. */
    PROBABLE("probable"),

    /** Pas une fenêtre de consentement (ou trop peu d'indices) : aucune action. */
    INSUFFISANT("insuffisante"),
}

/** Catégories de boutons reconnus. [ACCEPT] sert uniquement à la détection : jamais cliqué. */
enum class ButtonKind { REFUSE, OPTIONS, GLOBAL_REJECT, RADIO_REFUSE, CONFIRM, VENDOR_TAB, BACK, ACCEPT }

/** Cible concrète d'un clic, déjà validée par le garde-fou. */
sealed interface ClickTarget {
    /** Nœud dont le sous-arbre a été contrôlé par le garde-fou. */
    val guardNode: UiNode

    /** ACTION_CLICK sur ce nœud (repli : tap au centre de ses bornes). */
    data class Node(val node: UiNode) : ClickTarget {
        override val guardNode: UiNode get() = node
    }

    /** Tap (geste) au centre du nœud, faute de parent cliquable sûr. */
    data class Point(val node: UiNode, val x: Int, val y: Int) : ClickTarget {
        override val guardNode: UiNode get() = node
    }
}

data class ButtonCandidate(
    val kind: ButtonKind,
    /** Formule de rules.json reconnue. */
    val phrase: String,
    /** Libellé canonique du nœud. */
    val label: String,
    val labelNode: UiNode,
    /** Null si aucun clic sûr n'est possible (voir [blocked]). */
    val target: ClickTarget?,
    val blocked: SafetyGuard.Verdict.Blocked?,
    /** Identité stable du bouton au sein d'une session (anti-répétition). */
    val key: String,
    /** État sélectionné d’un bouton radio ; null si l’état n’est pas publié. */
    val selected: Boolean? = null,
    val isRadio: Boolean = false,
    /**
     * Choix « maître » qui règle toute une liste d'un coup : « Tous les partenaires › Bloquer »,
     * « Vous autorisez › Refuser » (Didomi). À cliquer avant les choix un par un.
     */
    val isMaster: Boolean = false,
    /**
     * Bouton « écrasé » sous la barre du navigateur (Opera, 22/11 à 16 h 47) : présent dans la page,
     * actif, mais de HAUTEUR NULLE à l'écran ; aucun défilement ne peut le faire apparaître (pied d'une
     * fenêtre fixe). Seuls « Tout refuser » (formule d'au moins deux mots ou identifiant) et la
     * validation en ont un. Cible = action d'accessibilité du nœud, jamais un appui aux coordonnées ;
     * garde-fou, relecture de la cible et toutes les conditions de validation s'appliquent.
     */
    val hiddenTarget: ClickTarget? = null,
    /**
     * Bouton du pied de la fenêtre situé SOUS l'écran, de taille normale (Didomi dans Firefox, RTBF,
     * 27/11 : « Enregistrer » et « Tout refuser » cachés sous la barre de Firefox). Le moteur ne s'en sert
     * qu'après avoir constaté qu'un défilement ou « faire apparaître » ne le déplace pas : fenêtre fixe,
     * aucun défilement ne l'amènera à l'écran. Même règles que [hiddenTarget] : action d'accessibilité du
     * nœud seulement, jamais d'appui aux coordonnées, garde-fou et conditions de validation inchangés.
     */
    val stuckTarget: ClickTarget? = null,
)

/** Section repliée (accordéon) à déplier pour révéler des interrupteurs cachés. */
data class ExpandCandidate(val node: UiNode, val signature: String, val label: String)

data class ToggleCandidate(
    val node: UiNode,
    val signature: String,
    val label: String,
    val target: ClickTarget?,
    val blocked: SafetyGuard.Verdict.Blocked?,
    /**
     * Interrupteur hors de l'écran mais présent dans l'arbre (Chrome expose toute la page) : décoché
     * par l'action d'accessibilité du nœud, jamais par un appui aux coordonnées (21/11 au soir).
     */
    val offscreen: Boolean = false,
)

/** Résultat de l'analyse d'un instantané d'écran. */
class ScreenAnalysis(
    val root: UiNode,
    val hints: Set<String>,
    val buttons: List<ButtonCandidate>,
    /** Interrupteurs cochés, actifs, non « toujours actifs » : à décocher. */
    val checkedToggles: List<ToggleCandidate>,
    /** Signatures de tous les interrupteurs vus → true (ON), false (OFF), null (UNKNOWN). */
    val toggleStates: Map<String, Boolean?>,
    /** Sections repliées (ACTION_EXPAND) pouvant cacher des interrupteurs. */
    val expandables: List<ExpandCandidate>,
    /**
     * Interrupteurs reconnus à leur classe mais dont l'appli ne publie pas l'état (ni « cochable »,
     * ni description d'état) : impossible de savoir s'ils sont activés, donc jamais touchés.
     */
    val unknownStateToggles: Int = 0,
    val scrollTarget: UiNode?,
    val windowBounds: Bounds,
    /** Change dès que quoi que ce soit change à l'écran (texte, état, position). */
    val fingerprint: Int,
    /** Identité « grossière » de la fenêtre (boutons reconnus + indices), stable au défilement. */
    val lightFingerprint: Int,
    private val minHints: Int,
    /** Plateforme reconnue (« Google UMP », « Didomi »…, « TCF générique » ou « inconnue »). */
    val platform: String = PLATFORM_UNKNOWN,
    /**
     * Empreinte STRUCTURELLE (plateforme, types de boutons, nombre d'interrupteurs par tranche,
     * marqueurs techniques) : sans aucun texte affiché, stable si la langue ou un libellé change.
     */
    val structureSignature: Int = 0,
    /** Signatures des interrupteurs dont l'état est inconnu dans cet instantané. */
    val unknownToggleSignatures: Set<String> = emptySet(),
    /** Un bouton « Tout refuser » sûr existe dans la fenêtre, mais hors de l'écran : défiler d'abord. */
    val offscreenRefusal: Boolean = false,
    /**
     * Le panneau de consentement a été lu en entier, même si la capture de l'écran a été arrêtée
     * ailleurs (Firefox expose toute la page : 5 000 éléments d'article avant la fenêtre, 20/11).
     */
    val scopeComplete: Boolean = false,
    /** Interrupteurs activés légitimement (toujours actif, « Nécessaires », état activé = refus). */
    val keptOnToggles: Set<String> = emptySet(),
    /** Panneau de consentement retenu (null si aucun) : les validations sont cherchées DEDANS. */
    val panelRoot: UiNode? = null,
    /**
     * Zone à faire défiler pour CHERCHER un bouton (validation, « Tout refuser ») : [scrollTarget]
     * s'il existe, sinon un conteneur défilant qui contient des boutons reconnus, même sans
     * interrupteur (Sourcepoint, 21/11 : « bouton de validation introuvable » sans avoir défilé).
     */
    val searchScrollTarget: UiNode? = scrollTarget,
    /** Un bouton de validation existe dans la fenêtre mais hors de l'écran : défiler jusqu'à lui. */
    val offscreenConfirm: Boolean = false,
    /**
     * Bouton de validation hors de l'écran, à faire apparaître d'un coup (ACTION_SHOW_ON_SCREEN) une
     * fois tous les choix de la liste décochés, au lieu de défiler 480 lignes une à une.
     */
    val confirmToReveal: UiNode? = null,
    /**
     * Empreinte des TEXTES du panneau (sans position ni état) : stable quand la page défile, mais
     * différente d'un site à l'autre (le nom du site figure presque toujours dans la fenêtre).
     * Sert aux délais d'attente dans les navigateurs (audit du 21/11, point 5).
     */
    val contentSignature: Int = 0,
    /**
     * Onglets de catégories (OneTrust en onglets) : chacun affiche les réglages d'une catégorie.
     * Hors de [buttons] : ils ne comptent ni pour reconnaître une fenêtre ni dans ses empreintes.
     */
    val categoryTabs: List<ButtonCandidate> = emptyList(),
    /**
     * Signature de chaque interrupteur vu → son libellé lisible (nom de la finalité ou du
     * fournisseur), pour NOMMER dans le diagnostic les interrupteurs non vérifiés. Texte d'écran :
     * réservé au diagnostic, jamais copié dans l'historique.
     */
    val toggleLabelsBySignature: Map<String, String> = emptyMap(),
    /**
     * Navigateur : le panneau retenu couvre presque toute la page affichée. Ce n'est donc pas une fenêtre
     * à part, mais la page elle-même, qui PARLE de cookies (article, fil de forum, README, formulaire).
     * Faux positifs du 27/11 : journal LinuxFr, fil XDA et page GitHub présentant CYRAVA (voir
     * [canStartSession]).
     */
    val pageWide: Boolean = false,
    /** Page entière : un vrai bouton de refus ou d'acceptation y est visible (une validation seule ne suffit pas). */
    val pageWideChoice: Boolean = false,
) {
    /** Un libellé de commande visible, y compris ACCEPT (reconnu mais jamais actionné). */
    val hasKnownButton: Boolean get() = buttons.any {
        it.labelNode.isVisibleToUser && it.labelNode.isEnabled && !it.labelNode.bounds.isEmpty
    }

    /** Les indices et au moins un libellé de commande doivent être réellement visibles. */
    val isConsent: Boolean get() = hints.size >= minHints && hasKnownButton

    /**
     * Peut-on OUVRIR une session sur cet écran ? Il faut un vrai choix de consentement visible (refus,
     * acceptation, validation, choix radio), des interrupteurs, ou l'identifiant technique d'une
     * plateforme connue. Des liens « Options », « Customize », « Manage cookies » seuls ne suffisent
     * pas : sur une page GitHub (21/11, 20:45), CYRAVA a ouvert le menu « Show options » d'un
     * commentaire puis la fenêtre « Notifications › Customize », parce que la page parlait de cookies.
     */
    val canStartSession: Boolean
        get() = isConsent && (
            // Page entière (27/11) : le lien « Modifier mes préférences » d'un menu, une case du formulaire
            // (licence CC BY-SA) ou les boutons « gras / souligné » d'un éditeur ne prouvent rien. Seuls
            // l'identifiant d'une plateforme connue ou un vrai bouton de choix peuvent ouvrir une session.
            if (pageWide) hints.any { it.startsWith("id:") } || pageWideChoice else
            buttons.any {
                it.kind in SESSION_START_KINDS && it.labelNode.isVisibleToUser && it.labelNode.isEnabled && !it.labelNode.bounds.isEmpty
            } || toggleStates.isNotEmpty() || hints.any { it.startsWith("id:") }
            )

    /** Niveau de certitude : seules les fenêtres « certaines » sont traitées en mode prudent. */
    val confidence: Confidence
        get() {
            if (!isConsent) return Confidence.INSUFFISANT
            val actionable = buttons.any { it.kind != ButtonKind.ACCEPT && it.kind != ButtonKind.BACK }
            val identified = platform != PLATFORM_UNKNOWN && platform != PLATFORM_GENERIC_TCF
            val textHints = hints.count { !it.startsWith("id:") }
            return if (actionable && (identified || textHints >= 3)) Confidence.CERTAIN else Confidence.PROBABLE
        }

    fun buttons(kind: ButtonKind): List<ButtonCandidate> = buttons.filter { it.kind == kind }
}

/**
 * Page entière dans un navigateur (voir [ScreenAnalysis.pageWide]) : seuls un refus ou une acceptation
 * prouvent une fenêtre de consentement ; une validation seule peut être celle d'un formulaire du site.
 */
private val PAGE_START_KINDS = setOf(
    ButtonKind.REFUSE, ButtonKind.GLOBAL_REJECT, ButtonKind.ACCEPT, ButtonKind.RADIO_REFUSE,
)

/** Boutons qui prouvent un vrai choix de consentement (pas une simple navigation). */
private val SESSION_START_KINDS = setOf(
    ButtonKind.REFUSE, ButtonKind.GLOBAL_REJECT, ButtonKind.ACCEPT, ButtonKind.CONFIRM, ButtonKind.RADIO_REFUSE,
)

class ScreenAnalyzer(private val rules: Rules, private val guard: SafetyGuard = SafetyGuard(rules)) {

    private val kindPhrases: List<Pair<ButtonKind, List<String>>> = listOf(
        ButtonKind.REFUSE to rules.refuse,
        ButtonKind.GLOBAL_REJECT to rules.globalReject,
        ButtonKind.OPTIONS to rules.options,
        ButtonKind.CONFIRM to rules.confirm,
        ButtonKind.VENDOR_TAB to rules.vendorTabs,
        ButtonKind.RADIO_REFUSE to rules.radioRefuse,
        ButtonKind.BACK to rules.back,
        ButtonKind.ACCEPT to rules.accept,
    ).map { (k, list) -> k to list.sortedByDescending { it.length } }

    private val confirmFormulas: Set<String> = rules.confirm.toSet()
    /** Marqueurs d'identifiant propres à une plateforme (« didomi », « appconsent »…), sans les mots génériques. */
    private val platformMarkers: List<String> = rules.platforms.flatMap { it.markers }.map { it.lowercase() }

    /** Acceptations d'un seul mot (« consent », « accepter ») : mots ajoutés neutres seulement. */
    private val acceptSingleWords: Set<String> = rules.accept.filterTo(HashSet()) { TextNormalizer.wordCount(it) == 1 }

    private val maxLabelWords: Int =
        (kindPhrases.flatMap { it.second }.maxOfOrNull(TextNormalizer::wordCount) ?: 4) + rules.maxExtraWords

    /** Formule de [phrases] correspondant au libellé [canon] (égalité, ou à [Rules.maxExtraWords] mots près). */
    /**
     * @param strictAccept acceptation d'un seul mot sur un bouton ou un lien (pas un bouton radio) :
     * seuls des mots neutres sont tolérés autour. Pour un bouton radio « Autoriser ce partenaire »,
     * la reconnaissance large est gardée : elle sert à BLOQUER une validation, jamais à cliquer.
     */
    fun match(canon: String, phrases: List<String>, exactOnly: Boolean = false, strictAccept: Boolean = false): String? {
        if (canon.isEmpty()) return null
        for (p in phrases) {
            if (canon == p) return p
        }
        // Une inversion peut se trouver AU MILIEU du libellé (« Je refuse de refuser »),
        // pas seulement dans les mots ajoutés avant ou après une formule connue.
        // Les formules négatives livrées restent autorisées uniquement par la correspondance
        // exacte ci-dessus ; toute extension contenant une inversion est rejetée par prudence.
        // Calculés seulement si une formule encadre le libellé (22/11 : l'analyse d'une longue liste
        // passait l'essentiel de son temps ici). Sans formule trouvée, le résultat est null de toute façon.
        var inverted: Boolean? = null
        var words = -1
        for (p in phrases) {
            if (canon.length <= p.length) continue
            val extra = when {
                canon.startsWith(p) && canon[p.length] == ' ' -> canon.substring(p.length + 1)
                canon.endsWith(p) && canon[canon.length - p.length - 1] == ' ' -> canon.substring(0, canon.length - p.length - 1)
                else -> continue
            }
            if (inverted == null) inverted = Inversions.find(canon) != null
            if (inverted) return null
            if (words < 0) words = TextNormalizer.wordCount(canon)
            if (words - TextNormalizer.wordCount(p) > rules.maxExtraWords) continue
            // « Ne pas refuser », « Do not reject » : les mots ajoutés inversent le sens.
            if (Inversions.find(extra) != null) continue
            // Un choix radio est une option exclusive : pas de mots ajoutés autour de « Bloquer »,
            // qui pourraient transformer son sens ou faire correspondre le texte d’un groupe entier.
            if (exactOnly) continue
            // Formules de REFUS : seuls des mots neutres sont tolérés autour (« Tout refuser et
            // fermer »). « Pourquoi refuser », « Peut-on refuser », « Refuser coûte 2 € » ne sont
            // pas des boutons de refus : une liste de mots autorisés est plus sûre qu'une liste
            // de mots interdits, qui serait toujours incomplète.
            if (p in rules.refusalFormulas && extra.split(' ').any { it !in NEUTRAL_REFUSAL_WORDS }) continue
            // Même règle pour les VALIDATIONS (audit du 21/11, n° 3) : « Confirmer la commande »,
            // « Submit order », « Confirm and pay » ne sont pas des « Confirmer ». Seuls des mots neutres
            // (« mes choix », « et fermer »…) sont tolérés autour d'une formule de validation.
            if (p in confirmFormulas && p !in rules.refusalFormulas && extra.split(' ').any { it !in NEUTRAL_CONFIRM_WORDS }) continue
            // ACCEPTATION d'un seul mot (« Consent », « Accepter ») : le lien « Consent-O-Matic » d'une
            // page AlternativeTo était pris pour un bouton « Consent » et la page pour une bannière
            // (21/11 au soir). Seuls des mots neutres sont tolérés autour (« Accepter et fermer »).
            // Sans effet sur la sécurité : une acceptation n'est jamais cliquée, et le garde-fou
            // bloque toujours ces mots par ailleurs.
            if (strictAccept && p in acceptSingleWords && extra.split(' ').any { it !in NEUTRAL_ACCEPT_WORDS }) continue
            return p
        }
        return null
    }

    /**
     * Liste des catégories d'un centre de préférences en onglets : décrite « Cookie Categories »
     * (description ou description complémentaire), ou contenant le panneau OneTrust « ot-tab-desc ».
     */
    private fun isCategoryMenu(node: UiNode): Boolean {
        val described = listOfNotNull(node.contentDescription, node.supplementalDescription)
            .any { TextNormalizer.canonical(it) in CATEGORY_MENU_LABELS }
        return described || node.children.any { it.viewId?.lowercase()?.substringAfterLast('/') == "ot-tab-desc" }
    }

    /**
     * Onglets possibles d'une liste de catégories : ses enfants directs, et ceux d'une simple enveloppe
     * intermédiaire. OneTrust dans Opera (23/11, 14 h 40) range les onglets dans un bloc sans texte, seul
     * enfant de la liste « Cookie Categories » : aucun onglet n'était trouvé, d'où « aucun contrôle de
     * préférence accessible reconnu ». Une enveloppe = nœud non cliquable, sans texte, qui contient au moins
     * deux enfants directs cliquables, seule dans la liste. Un seul niveau est descendu.
     */
    private fun categoryTabCandidates(menu: UiNode): List<UiNode> {
        // Relecture sécurité du 23/11 : seulement si l'enveloppe est le SEUL enfant de la liste (cas réel d'Opera),
        // pour ne pas prendre une rangée de boutons du panneau pour des onglets.
        val only = menu.children.singleOrNull() ?: return menu.children
        val wrapper = !only.isClickable && only.text.isNullOrBlank() && only.contentDescription.isNullOrBlank() &&
            only.children.count { it.isClickable } >= 2
        return if (wrapper) only.children else menu.children
    }

    /** Bouton radio (classe ou rôle web), quelle que soit sa sécurité : sert seulement au choix du mode de reconnaissance. */
    private fun isRadioLike(node: UiNode): Boolean =
        node.className.contains("RadioButton", ignoreCase = true) || node.webRole?.equals("radio", ignoreCase = true) == true

    private fun isRadioRefuse(node: UiNode): Boolean =
        (node.className.contains("RadioButton", ignoreCase = true) || node.webRole?.equals("radio", ignoreCase = true) == true) &&
            !node.isPassword && !node.isEditable && !node.isAccessibilityDataSensitive && !node.insideProtectedSubtree

    /**
     * Groupe d'un bouton radio : le RadioGroup (ou rôle radiogroup) le plus proche ; sinon le premier
     * ancêtre (3 niveaux au plus) qui contient au moins deux radios ; sinon le parent.
     * Firefox (Didomi, 22/11) place chaque radio dans sa propre enveloppe (<label>) : le parent ne
     * contenait qu'UN radio, la paire « Refuser X / Accepter X » n'était jamais formée, et chaque
     * « Refuser X » coché passait pour « un choix radio coché qui n'est pas un refus reconnu ».
     * La règle de la paire reste stricte (exactement deux radios, même suite « X »).
     */
    private fun radioGroupOf(node: UiNode): UiNode? {
        node.ancestors().firstOrNull {
            it.className.contains("RadioGroup", ignoreCase = true) || it.webRole?.equals("radiogroup", ignoreCase = true) == true
        }?.let { return it }
        var p = node.parent
        var level = 0
        while (p != null && level < RADIO_GROUP_MAX_CLIMB) {
            if (p.walk().count(::isRadioLike) >= 2) return p
            p = p.parent
            level++
        }
        return node.parent
    }

    /**
     * Paires « Refuser X » / « Accepter X » (écran de réglages Didomi, diagnostic Oodrive du 18/11) :
     * un libellé radio qui COMMENCE par un mot de refus est accepté seulement si son groupe contient
     * exactement deux radios, que l'autre commence par un mot d'acceptation, que la suite « X » est
     * identique dans les deux, et qu'aucun des deux ne contient d'inversion. Sinon : rien.
     * Renvoie, pour chaque radio de refus retenue, la formule de refus et le libellé complet.
     */
    private fun pairedRadioRefusals(radios: List<UiNode>, texts: Map<UiNode, List<String>>): Map<UiNode, Pair<String, String>> {
        val refuseWords = rules.radioRefuse.filter { TextNormalizer.wordCount(it) == 1 }
        if (refuseWords.isEmpty()) return emptyMap()
        val result = HashMap<UiNode, Pair<String, String>>()
        val groups = LinkedHashMap<UiNode, MutableList<UiNode>>()
        radios.forEach { r -> radioGroupOf(r)?.let { g -> groups.getOrPut(g) { ArrayList() }.add(r) } }
        for (group in groups.values) {
            if (group.size != 2) continue
            for ((refuse, other) in listOf(group[0] to group[1], group[1] to group[0])) {
                pairPhrase(texts[refuse].orEmpty(), texts[other].orEmpty(), refuseWords)?.let { result[refuse] = it }
            }
        }
        return result
    }

    private fun pairPhrase(refuseLabels: List<String>, otherLabels: List<String>, refuseWords: List<String>): Pair<String, String>? {
        for (label in refuseLabels) {
            if (Inversions.find(label) != null) continue
            val word = refuseWords.firstOrNull { label.startsWith("$it ") } ?: continue
            val suffix = label.substring(word.length + 1).trim()
            if (suffix.isEmpty()) continue
            for (other in otherLabels) {
                if (Inversions.find(other) != null) continue
                val accept = PAIRED_ACCEPT_WORDS.firstOrNull { other.startsWith("$it ") } ?: continue
                if (other.substring(accept.length + 1).trim() == suffix) return word to label
            }
        }
        return null
    }

    fun isToggle(node: UiNode): Boolean {
        if (node.isAccessibilityDataSensitive || node.insideProtectedSubtree || node.isPassword || node.isEditable) return false
        if (isRadioRefuse(node)) return false
        // Verdict par nom de classe, calculé une fois (22/11 : l'analyse d'une liste de 367 cases
        // passait l'essentiel de son temps à recalculer ces comparaisons de texte).
        val byClass = classVerdicts.getOrPut(node.className) {
            val cls = node.className.lowercase()
            when {
                rules.toggleExcludedClasses.any { cls.contains(it) } -> CLASS_EXCLUDED
                rules.toggleClasses.any { cls.contains(it) } -> CLASS_TOGGLE
                else -> CLASS_NEUTRAL
            }
        }
        if (byClass == CLASS_EXCLUDED) return false
        // Chromium/WebView peut exposer un contrôle ARIA sans classe Android dédiée
        // (par exemple android.view.View + chromeRole=switch). Le rôle sémantique
        // doit donc suffire à identifier le contrôle, sans pour autant prouver son état.
        val webToggle = node.webRole?.lowercase() in WEB_TOGGLE_ROLES
        return node.isCheckable || webToggle || byClass == CLASS_TOGGLE
    }

    /** Verdict « interrupteur » par nom de classe (exclu, interrupteur, neutre), partagé entre analyses. */
    private val classVerdicts = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * État d'un interrupteur : activé (true), désactivé (false) ou INCONNU (null).
     *
     * L'état Android direct fait foi quand il dit « coché ». La description d'état peut compléter
     * une vue native cochable, mais pas un rôle ARIA switch/checkbox que Chromium expose sans
     * propriété Android cochable : « Off » a été observé pour deux valeurs DOM opposées.
     * Dans ce cas l'état reste inconnu ; on n'y touche pas et le refus ne peut pas être déclaré complet.
     */
    fun toggleState(node: UiNode): Boolean? {
        if (node.isAccessibilityDataSensitive || node.insideProtectedSubtree || node.isPassword || node.isEditable) return null
        // Un rôle ou une classe de switch ne prouve pas que l'état est publié. Sans isCheckable,
        // « Off » peut être renvoyé par Chromium pour aria-checked=true comme pour false.
        if (!node.isCheckable) return null
        if (node.isIndeterminate || rules.isIndeterminateDescription(node.stateDescription)) return null
        // isCheckable + isChecked est la preuve Android. Une description d'état reconnue et
        // contradictoire rend le nœud ambigu : on le classe UNKNOWN au lieu de choisir une source.
        val described = rules.checkedFromDescription(node.stateDescription)
        if (described != null && described != node.isChecked) return null
        return node.isChecked
    }

    /**
     * @param webContentOnly navigateur web : seule la page (sous-arbre WebView) est analysée. La barre
     * d'adresse (« facebook.com/cookie/consent ») fournissait de faux indices, et le menu « Plus
     * d'options » du navigateur était pris pour le bouton « Plus d'options » du site (diagnostic du 20/11).
     */
    /**
     * @param continuing session en cours dans un navigateur, après un défilement ou un clic qui ne ferme
     * pas la fenêtre (voir [RefusalEngine]). Firefox et Opera, 22/11 (16 h 44, 16 h 46) : une fois la
     * liste défilée, le titre et le texte d'introduction sont sortis de l'écran, et le conteneur de la
     * plateforme (« didomi-popup »…) dépasse en haut de l'écran : plus aucun indice lisible, la fenêtre
     * passait pour fermée (« la fenêtre s'est fermée sans validation »). Dans ce mode seulement,
     * l'identifiant technique d'une plateforme sur un nœud qui DÉBORDE de l'écran compte, et il suffit
     * comme indice. Les clics exigent toujours un contrôle visible (ou écrasé, voir [ButtonCandidate.hiddenTarget]).
     */
    fun analyze(root: UiNode, webContentOnly: Boolean = false, continuing: Boolean = false): ScreenAnalysis {
        val allNodes = root.walk().toList()
        val allCanonTexts = HashMap<UiNode, List<String>>(allNodes.size)
        allNodes.forEach { n ->
            allCanonTexts[n] = n.ownTexts().map(TextNormalizer::canonical).filter { it.isNotEmpty() }
        }
        val browserUi: Set<UiNode> = if (webContentOnly) browserInterface(root) else emptySet()

        // Détermine le plus petit sous-arbre qui contient à la fois les indices de consentement
        // visibles et un vrai contrôle d'action. Les liens de pied de page et le texte d'article
        // ne peuvent donc plus emprunter les indices d'une autre partie de la page.
        fun visible(n: UiNode) = n.isVisibleToUser && !n.bounds.isEmpty &&
            !n.isPassword && !n.isEditable && !n.isAccessibilityDataSensitive && !n.insideProtectedSubtree &&
            n !in browserUi
        // Firefox (GeckoView) marque « invisibles » la plupart des textes d'une page pourtant affichés
        // (Facebook, 20/11 : seul le texte des boutons était « visible »). Dans un navigateur, un TEXTE
        // dont les bornes sont dans la zone de la page compte comme indice. Cela ne concerne que les
        // indices : un clic exige toujours un nœud déclaré visible par le navigateur.
        val viewport = if (webContentOnly) {
            root.walk().filter { it.className.contains("WebView", ignoreCase = true) && !it.bounds.isEmpty }
                .maxByOrNull { it.bounds.area }?.bounds
        } else null
        fun readable(n: UiNode): Boolean {
            if (visible(n)) return true
            val v = viewport ?: return false
            val b = n.bounds
            return !b.isEmpty && n !in browserUi && !n.isPassword && !n.isEditable &&
                !n.isAccessibilityDataSensitive && !n.insideProtectedSubtree &&
                b.top >= v.top && b.bottom <= v.bottom && b.left >= v.left - 2 && b.right <= v.right + 2
        }
        // Mode « suite de session » (voir [analyze]) : conteneur de la plateforme qui déborde de l'écran.
        fun markerReadable(n: UiNode): Boolean {
            if (!continuing) return false
            val v = viewport ?: return false
            val b = n.bounds
            return !b.isEmpty && n !in browserUi && !n.isPassword && !n.isEditable &&
                !n.isAccessibilityDataSensitive && !n.insideProtectedSubtree &&
                b.bottom > v.top && b.top < v.bottom && b.right > v.left && b.left < v.right
        }
        val hintCache = HashMap<UiNode, Set<String>>()
        fun localHints(n: UiNode): Set<String> = hintCache.getOrPut(n) {
            val textReadable = readable(n)
            if (!textReadable && !markerReadable(n)) return@getOrPut emptySet()
            val text = if (textReadable) allCanonTexts[n].orEmpty().joinToString(" ") else ""
            val found = rules.hints.filterTo(LinkedHashSet()) { TextNormalizer.containsPhrase(text, it) }
            val id = n.viewId?.lowercase()
            if (id != null && rules.cmpMarkers.any { id.contains(it) }) {
                rules.cmpMarkers.firstOrNull { id.contains(it) }?.let { found.add("id:$it") }
            }
            found
        }
        val allPairedRadios = pairedRadioRefusals(allNodes.filter(::isRadioRefuse), allCanonTexts)

        // France Télévisions (Opera, 27/11) : les choix des préférences sont des paires de boutons « Non | Oui »
        // sur une même ligne. Inconnus, ils ne comptaient pas : CYRAVA croyait la fenêtre fermée et abandonnait.
        // Un « Non » n'est reconnu que s'il a un « Oui » juste à sa droite, à la même hauteur, dans le même bloc
        // (2 niveaux au plus) ; jamais seul. Le « Oui » est noté comme acceptation (jamais cliquée).
        val yesNoCache = HashMap<UiNode, UiNode?>()
        fun yesNoPartner(n: UiNode): UiNode? = yesNoCache.getOrPut(n) {
            if (n.isPassword || n.isEditable || n.isAccessibilityDataSensitive || n.insideProtectedSubtree) return@getOrPut null
            if (isRadioLike(n) || n.isCheckable || !n.isEnabled || !visible(n)) return@getOrPut null
            if (allCanonTexts[n].orEmpty().none { it in YES_NO_REFUSALS }) return@getOrPut null
            // Élément cliquable qui porte le « Non » : il ne doit contenir aucun « Oui » (sélecteur à deux
            // positions en un seul élément : l'actionner pourrait passer sur « Oui », relecture sécurité du 27/11).
            val carrier = if (n.isClickable) n else n.ancestors().take(2).firstOrNull { it.isClickable && it.isEnabled }
                ?: return@getOrPut null
            if (carrier.walk().any { o -> allCanonTexts[o].orEmpty().any { it in YES_NO_ACCEPTS } }) return@getOrPut null
            val b = n.bounds
            var block = n.parent
            var level = 0
            while (block != null && level < 3) {
                val partner = block.walk().firstOrNull { o ->
                    o !== n && !n.isDescendantOf(o) && !o.isDescendantOf(n) && visible(o) && !o.isCheckable &&
                        allCanonTexts[o].orEmpty().any { it in YES_NO_ACCEPTS } &&
                        kotlin.math.abs(o.bounds.centerY - b.centerY) <= 30 && o.bounds.left > b.left
                }
                if (partner != null) {
                    // La question de la ligne ne doit pas porter sur un refus (« Refuser la mesure d'audience ? »,
                    // « Désactiver… ? », « S'opposer… ») : « Non » y GARDERAIT les cookies. La paire est alors ignorée.
                    val rowTexts = block.walk().filter { it !== n && it !== partner }
                        .flatMap { allCanonTexts[it].orEmpty().asSequence() }.joinToString(" ")
                    val negative = YES_NO_NEGATIVE_WORDS.any { w -> rowTexts.split(' ').any { it.startsWith(w) } }
                    return@getOrPut if (negative) null else partner
                }
                block = block.parent
                level++
            }
            null
        }

        /** Force d'un contrôle pour choisir le panneau : 0 aucun, 1 navigation, 2 décisif, 3 fort. */
        val actionCache = HashMap<UiNode, Int>()
        fun actionLevel(n: UiNode): Int = actionCache.getOrPut(n) {
            if (!visible(n) || !n.isEnabled || isToggle(n)) return@getOrPut 0
            val radio = isRadioRefuse(n)
            if (n in allPairedRadios || radio && allCanonTexts[n].orEmpty().any { match(it, rules.radioRefuse, exactOnly = true) != null }) {
                // « Tous les partenaires › Bloquer » règle toute la liste : contrôle fort.
                val ctx = masterContext(n, allCanonTexts)
                return@getOrPut if (MASTER_CHOICE_PHRASES.any { TextNormalizer.containsPhrase(ctx, it) }) 3 else 2
            }
            // Un simple texte (titre « Cookies et partenaires ») n'est pas un contrôle : il doit être
            // cliquable lui-même ou porté par un parent cliquable (texte d'un bouton WebView).
            if (!n.isClickable && n.ancestors().none { it.isClickable && it.isEnabled }) return@getOrPut 0
            if (yesNoPartner(n) != null) return@getOrPut 2
            val shortId = n.viewId?.lowercase()?.substringAfterLast('/')
            if (shortId != null && rules.buttonIds.values.any { shortId in it }) return@getOrPut 3
            var level = 0
            for (label in allCanonTexts[n].orEmpty()) {
                // ACCEPT sert aussi d’indice de structure : une bannière qui ne propose
                // que « Tout accepter » doit être reconnue, puis refusée sans clic.
                // L’interdiction d’actionner ACCEPT reste appliquée lors de la création des cibles.
                for ((kind, phrases) in kindPhrases) {
                    if (kind == ButtonKind.RADIO_REFUSE) continue
                    val p = match(label, phrases, strictAccept = kind == ButtonKind.ACCEPT && !isRadioLike(n)) ?: continue
                    val l = when (kind) {
                        ButtonKind.VENDOR_TAB, ButtonKind.BACK -> 1
                        ButtonKind.GLOBAL_REJECT, ButtonKind.OPTIONS, ButtonKind.CONFIRM -> 3
                        ButtonKind.REFUSE -> if (TextNormalizer.wordCount(p) >= 2) 3 else 2
                        else -> 2
                    }
                    if (l > level) level = l
                }
            }
            level
        }
        fun localAction(n: UiNode): Boolean = actionLevel(n) > 0
        /** Vrai bouton de refus de toute la fenêtre (« Refuser les cookies », identifiant documenté). */
        fun strongRefusal(n: UiNode): Boolean {
            if (actionLevel(n) < 3) return false
            val shortId = n.viewId?.lowercase()?.substringAfterLast('/')
            if (shortId != null && (rules.buttonIds[ButtonKind.REFUSE]?.contains(shortId) == true ||
                    rules.buttonIds[ButtonKind.GLOBAL_REJECT]?.contains(shortId) == true)
            ) return true
            return allCanonTexts[n].orEmpty().any { label ->
                match(label, rules.globalReject) != null ||
                    match(label, rules.refuse)?.let { TextNormalizer.wordCount(it) >= 2 } == true
            }
        }
        val evidence = allNodes.filter { localHints(it).isNotEmpty() || localAction(it) }
        val candidateRoots = LinkedHashSet<UiNode>()
        evidence.forEach { node ->
            candidateRoots.add(node)
            node.ancestors().forEach { ancestor -> candidateRoots.add(ancestor) }
        }
        val cmpContainerMarkers = rules.platforms.flatMap { it.markers }.map { it.lowercase() }
        data class PanelCandidate(
            val node: UiNode, val nodeCount: Int, val hintCount: Int, val controlCount: Int,
            val bestAction: Int, val cmpContainer: Boolean, val hasRefusal: Boolean = false,
        )
        // Deux passes : d'abord un panneau avec un vrai bouton ; à défaut seulement (page de préférences
        // défilée où le bouton n'est pas à l'écran), un panneau dont les contrôles sont des interrupteurs.
        fun pickPanel(togglesCount: Boolean): PanelCandidate? = candidateRoots.mapNotNull { candidate ->
            val subtree = candidate.walk().toList()
            // Interrupteurs et sections dépliables : contrôles d'une page de préférences (accordéons).
            val visibleToggles = subtree.filter { visible(it) && it.isEnabled && (isToggle(it) || isRadioRefuse(it) || it.isExpandable) }
            val actionNodes = subtree.filter(::localAction)
            // Les indices viennent du texte de la fenêtre, pas du libellé du bouton lui-même : un lien
            // de pied de page « Paramètres des cookies » ne doit pas, seul, former une bannière.
            // Seul l'identifiant technique d'une CMP (« id: ») reste un indice valable sur un bouton.
            fun panelHints(n: UiNode): Set<String> =
                if (n in actionNodes) localHints(n).filterTo(LinkedHashSet()) { it.startsWith("id:") } else localHints(n)
            val hintsHere = subtree.flatMap { panelHints(it) }.toSet()
            val hintNodes = subtree.filter { panelHints(it).isNotEmpty() }
            val controls = if (togglesCount) actionNodes + visibleToggles else actionNodes
            val enoughHints = hintsHere.size >= rules.minHints || (continuing && hintsHere.any { it.startsWith("id:") })
            if (!enoughHints || controls.isEmpty()) return@mapNotNull null

            // Quand le seul ancêtre commun est la fenêtre entière, vérifier que le bouton est
            // spatialement proche du texte de consentement. Cela évite d'associer un lien de pied
            // de page à un article qui parle de cookies. Un véritable panneau dédié n'a pas besoin
            // de ce repli géométrique.
            val spansWholeWindow = candidate === root ||
                (root.bounds.area > 0L && candidate.bounds.area * 100L >= root.bounds.area * 80L)
            if (spansWholeWindow) {
                // Les interrupteurs comptent comme contrôles proches : sur une page de préférences, le
                // titre est près des interrupteurs et le bouton de validation tout en bas de la liste.
                // S'il y a de vrais choix, seule la distance à ces boutons compte : le lien
                // « nos 4 partenaires » du paragraphe ne doit pas valider un panneau trop large.
                val decisive = actionNodes.filter { actionLevel(it) >= 2 }
                val nearNodes = (decisive.ifEmpty { actionNodes }) + visibleToggles
                val nearestDistance = hintNodes.minOfOrNull { hint ->
                    nearNodes.minOf { control -> kotlin.math.abs(hint.bounds.centerY - control.bounds.centerY) }
                } ?: Int.MAX_VALUE
                val maxDistance = maxOf(160, root.bounds.height * 28 / 100)
                if (nearestDistance > maxDistance) return@mapNotNull null
            }
            val id = candidate.viewId?.lowercase()
            PanelCandidate(
                candidate, subtree.size, hintsHere.size, controls.size,
                bestAction = actionNodes.maxOfOrNull(::actionLevel) ?: 0,
                cmpContainer = id != null && cmpContainerMarkers.any { id.contains(it) },
                hasRefusal = actionNodes.any(::strongRefusal),
            )
        }.let { found ->
            // Avec un vrai bouton : le plus petit panneau. Sans bouton (interrupteurs seuls) : le plus
            // petit panneau qui contient le plus de contrôles, sinon une seule ligne de la liste suffirait
            // à former le panneau et les autres lignes seraient ignorées.
            if (togglesCount) {
                found.minWithOrNull(compareBy<PanelCandidate> { -it.controlCount }.thenBy { it.nodeCount }.thenBy { it.node.bounds.area })
            } else {
                // Le plus petit panneau, mais pas n'importe lequel : un paragraphe contenant seulement
                // le lien « nos 30 partenaires » formait un panneau à lui seul, et les vrais boutons
                // (« Continuer sans accepter », « Gérer les options ») restaient hors de l'analyse
                // (Ultra Premium Direct, Galaxy Defense, Le Monde, 20/11). Ordre de préférence :
                // conteneur d'une CMP connue avec un bouton décisif, puis panneau avec un contrôle fort
                // (refus d'au moins deux mots, options, validation, choix maître), puis décisif, puis tout.
                val smallest = compareBy<PanelCandidate> { it.nodeCount }.thenBy { it.node.bounds.area }
                // AliExpress (Chrome, 27/11) : le paragraphe et son lien « paramètres de cookies » formaient
                // le plus petit panneau « fort » ; les vrais boutons « Refuser les cookies » et « Personnaliser »,
                // juste à côté, restaient hors de l'analyse et rien n'était fait. Si ce panneau n'a aucun refus
                // et qu'un panneau qui le CONTIENT en a un, celui-ci est retenu (le plus petit des deux).
                val strong = found.filter { it.bestAction >= 3 }.minWithOrNull(smallest)
                val widerWithRefusal = strong?.takeIf { !it.hasRefusal }?.let { st ->
                    found.filter { it.hasRefusal && it.node !== st.node && st.node.isDescendantOf(it.node) }
                        .minWithOrNull(smallest)
                }
                found.filter { it.cmpContainer && it.bestAction >= 2 }.minWithOrNull(smallest)
                    ?: widerWithRefusal
                    ?: strong
                    ?: found.filter { it.bestAction >= 2 }.minWithOrNull(smallest)
                    ?: found.minWithOrNull(smallest)
            }
        }
        val panel = pickPanel(togglesCount = false) ?: pickPanel(togglesCount = true)
        // Diagnostics du 22/11 (Didomi dans Opera, Commanders Act) : le panneau retenu était la liste
        // des choix, et le bouton « Enregistrer… » du pied de la fenêtre restait dehors : « bouton de
        // validation introuvable ». Si le panneau n'a aucune validation, il est élargi au plus proche
        // ancêtre (3 niveaux au plus, jamais la fenêtre entière ni une page web entière) qui en contient
        // une. Le garde-fou s'applique toujours à chaque clic.
        fun isConfirmNode(n: UiNode): Boolean {
            if (!visible(n) || !n.isEnabled || !localAction(n)) return false
            val shortId = n.viewId?.lowercase()?.substringAfterLast('/')
            if (shortId != null && rules.buttonIds[ButtonKind.CONFIRM]?.contains(shortId) == true) return true
            return allCanonTexts[n].orEmpty().any { match(it, rules.confirm) != null }
        }
        // AppConsent (Opera, 22/11) : « bouton de validation introuvable, même après défilement ». Le
        // bouton peut aussi être SOUS l'écran (pas encore visible) : il compte pour élargir le panneau
        // (jamais pour être cliqué : il sera d'abord amené à l'écran), et l'élargissement peut monter
        // jusqu'à 6 niveaux quand l'ancêtre est le conteneur d'une plateforme connue (« appconsent »…).
        // Didomi dans Opera (22/11, 16 h 47) : le pied de la fenêtre (« Refuser tout », « Enregistrer ») est
        // écrasé sous la barre du bas d'Opera (hauteur nulle), et « Enregistrer » reste GRISÉ tant que
        // chaque finalité n'a pas de choix. Ces boutons comptent aussi pour délimiter la fenêtre (jamais
        // pour être cliqués directement : voir hiddenTarget et le moteur).
        fun isHiddenConfirmNode(n: UiNode): Boolean {
            val squashed = n.bounds.isEmpty
            if (n.isVisibleToUser && !squashed || n in browserUi) return false
            if (n.isPassword || n.isEditable || n.isAccessibilityDataSensitive || n.insideProtectedSubtree) return false
            if (!n.isClickable && n.ancestors().none { it.isClickable }) return false
            val shortId = n.viewId?.lowercase()?.substringAfterLast('/')
            if (shortId != null && rules.buttonIds[ButtonKind.CONFIRM]?.contains(shortId) == true) return true
            if (allCanonTexts[n].orEmpty().any { match(it, rules.confirm) != null }) return true
            // « Refuser tout » écrasé dans le même pied de fenêtre : identifiant documenté seulement.
            return squashed && shortId != null && rules.buttonIds[ButtonKind.REFUSE]?.contains(shortId) == true
        }
        fun hasConfirm(n: UiNode) = n.walk().any { isConfirmNode(it) || isHiddenConfirmNode(it) }
        fun platformContainer(n: UiNode): Boolean {
            val id = n.viewId?.lowercase() ?: return false
            return platformMarkers.any { id.contains(it) }
        }
        val scopedRoot = panel?.node?.let { p ->
            if (hasConfirm(p)) {
                p
            } else {
                p.ancestors().take(PANEL_EXTEND_MAX).withIndex().firstOrNull { (level, anc) ->
                    anc !== root && !anc.className.contains("WebView", ignoreCase = true) &&
                        (level < PANEL_EXTEND_LEVELS || platformContainer(anc)) && hasConfirm(anc)
                }?.value ?: p
            }
        }
        val nodes = scopedRoot?.walk()?.filter { it !in browserUi }?.toList().orEmpty()
        val canonTexts = HashMap<UiNode, List<String>>(nodes.size)
        val joined = StringBuilder()
        val visibleJoined = StringBuilder()
        var fp = 17
        for (n in nodes) {
            val texts = allCanonTexts[n].orEmpty()
            canonTexts[n] = texts
            texts.forEach { joined.append("  ").append(it) }
            if (readable(n)) texts.forEach { visibleJoined.append("  ").append(it) }
            fp = 31 * fp + n.className.hashCode()
            fp = 31 * fp + texts.hashCode()
            fp = 31 * fp + (if (n.isChecked) 1 else 0) + (if (n.isEnabled) 2 else 0) + (if (n.isVisibleToUser) 4 else 0)
            fp = 31 * fp + (n.stateDescription?.hashCode() ?: 0)
            fp = 31 * fp + n.bounds.top
            fp = 31 * fp + n.bounds.left
        }
        val allText = joined.toString()
        val visibleText = visibleJoined.toString()
        val hints = rules.hints.filterTo(LinkedHashSet()) { TextNormalizer.containsPhrase(visibleText, it) }
        if (rules.cmpMarkers.isNotEmpty()) {
            for (n in nodes) {
                if (!visible(n) && !markerReadable(n)) continue
                val id = n.viewId?.lowercase() ?: continue
                rules.cmpMarkers.firstOrNull { id.contains(it) }?.let { hints.add("id:$it") }
            }
        }

        // Libellés des interrupteurs : un « bouton » qui porte le même libellé qu'un interrupteur est
        // probablement son étiquette (<label for>) ; le toucher basculerait l'interrupteur.
        val toggleNodes = nodes.filter { isToggle(it) && !it.isPassword }
        val radioNodes = nodes.filter(::isRadioRefuse)
        val toggleLabels = toggleNodes.flatMapTo(HashSet()) { canonTexts[it].orEmpty() }

        // --- Boutons reconnus -------------------------------------------------------------
        data class RawButton(val kind: ButtonKind, val phrase: String, val label: String, val node: UiNode)
        val raw = ArrayList<RawButton>()
        val nodeSet = nodes.toHashSet()
        val pairedRadios = pairedRadioRefusals(radioNodes, canonTexts)
        for (n in nodes) {
            if (n.isPassword || n.isAccessibilityDataSensitive || isToggle(n)) continue
            pairedRadios[n]?.let { (phrase, label) -> raw.add(RawButton(ButtonKind.RADIO_REFUSE, phrase, label, n)) }
            // Bouton reconnu à son identifiant technique documenté par la CMP (ex. Didomi
            // « btn-toggle-disagree » = Tout refuser), même si son texte est inhabituel. Jamais si
            // son texte ressemble à une acceptation : le texte affiché garde toujours le dernier mot.
            val shortId = n.viewId?.lowercase()?.substringAfterLast('/')
            if (shortId != null && !isRadioRefuse(n)) {
                val texts = canonTexts[n].orEmpty()
                val acceptLike = texts.any { match(it, rules.accept) != null }
                // Audit du 21/11 (n° 4) : sans aucun texte lisible, le garde-fou n'a rien à contrôler.
                // Un bouton reconnu par son seul identifiant doit donc porter un texte.
                val readable = n.subtreeTexts().any { TextNormalizer.canonical(it).isNotEmpty() }
                if (!acceptLike && readable) {
                    for ((kind, ids) in rules.buttonIds) {
                        if (shortId in ids) raw.add(RawButton(kind, "id:$shortId", texts.firstOrNull() ?: shortId, n))
                    }
                }
            }
            yesNoPartner(n)?.let { yes ->
                if (yes in nodeSet) {
                    raw.add(RawButton(ButtonKind.REFUSE, "non", allCanonTexts[n].orEmpty().first { it in YES_NO_REFUSALS }, n))
                    raw.add(RawButton(ButtonKind.ACCEPT, "oui", allCanonTexts[yes].orEmpty().first { it in YES_NO_ACCEPTS }, yes))
                }
            }
            val radio = isRadioRefuse(n)
            for (canon in canonTexts[n].orEmpty()) {
                if (TextNormalizer.wordCount(canon) > maxLabelWords) continue
                for ((kind, phrases) in kindPhrases) {
                    if (kind == ButtonKind.RADIO_REFUSE && !radio) continue
                    if (radio && kind != ButtonKind.RADIO_REFUSE && kind != ButtonKind.ACCEPT) continue
                    val p = match(
                        canon, phrases,
                        exactOnly = kind == ButtonKind.RADIO_REFUSE,
                        strictAccept = kind == ButtonKind.ACCEPT && !isRadioLike(n),
                    ) ?: continue
                    // « Tous les partenaires » est l'intitulé du choix maître, pas un onglet à ouvrir.
                    if (kind == ButtonKind.VENDOR_TAB && MASTER_CHOICE_PHRASES.any { canon == it }) continue
                    // Lien d'évitement « Aller à "Enregistrer" » (Didomi) : il fait défiler, il n'enregistre rien.
                    if (kind == ButtonKind.CONFIRM && SKIP_LINK_PREFIXES.any { canon.startsWith(it) }) continue
                    raw.add(RawButton(kind, p, canon, n))
                }
            }
        }
        // Un bouton reconnu comme VALIDATION n'est jamais aussi un bouton d'options : « Valider mes
        // préférences » contient « préférences » et était cliqué comme une simple ouverture des réglages,
        // d'où « la fenêtre s'est fermée sans validation » (AppConsent simulé, 22/11 au soir).
        val confirmNodes = raw.filter { it.kind == ButtonKind.CONFIRM }.mapTo(HashSet()) { it.node }
        raw.removeAll { it.kind == ButtonKind.OPTIONS && it.node in confirmNodes }
        val labelCounts = raw.distinctBy { it.label to it.node.key }.groupingBy { it.label }.eachCount()
        val buttons = ArrayList<ButtonCandidate>()
        val seenTargets = HashSet<String>()
        for (r in raw) {
            // « 2 partenaires », « nos 751 partenaires » : compteur qui ouvre une simple liste de noms
            // (SAFTI 18/11), jamais un onglet de réglages utile. Reconnu mais jamais cliqué.
            val countChip = r.kind == ButtonKind.VENDOR_TAB && PARTNER_COUNT.matches(r.label)
            val (target, blocked) = if (countChip || r.kind == ButtonKind.ACCEPT || !r.node.isEnabled || r.label in toggleLabels) {
                null to null
            } else {
                // Une validation générique (« Enregistrer », « OK ») ne doit jamais être
                // déclenchée par coordonnées : elle exige un vrai contrôle cliquable/parent.
                val allowTap = r.kind != ButtonKind.VENDOR_TAB &&
                    r.kind != ButtonKind.BACK && r.kind != ButtonKind.CONFIRM
                resolveTarget(r.node, allowTap)
            }
            // Pied de fenêtre écrasé sous la barre du navigateur (voir ButtonCandidate.hiddenTarget).
            val footerKind = r.kind == ButtonKind.CONFIRM || r.kind == ButtonKind.GLOBAL_REJECT ||
                (r.kind == ButtonKind.REFUSE && (r.phrase.startsWith("id:") || TextNormalizer.wordCount(r.phrase) >= 2))
            val hiddenTarget = if (webContentOnly && footerKind && target == null && blocked == null &&
                r.label !in toggleLabels && r.node.bounds.isEmpty
            ) resolveSquashedButton(r.node) else null
            val stuckTarget = if (webContentOnly && footerKind && target == null && hiddenTarget == null && blocked == null &&
                r.label !in toggleLabels && !r.node.bounds.isEmpty && !r.node.isVisibleToUser && r.node !in browserUi &&
                viewport != null && r.node.bounds.top >= viewport.bottom
            ) resolveSquashedButton(r.node, requireEmpty = false) else null
            val key = if ((labelCounts[r.label] ?: 1) > 1) "${r.label}|${rowContext(r.node, canonTexts)}" else r.label
            // Un même bouton peut apparaître via son texte et sa description : on dédoublonne.
            val dedupe = "${r.kind}|${((target ?: hiddenTarget) as? ClickTarget.Node)?.node?.key ?: r.node.key}"
            if (!seenTargets.add(dedupe)) continue
            val master = (r.kind == ButtonKind.REFUSE || r.kind == ButtonKind.RADIO_REFUSE) &&
                masterContext(r.node, canonTexts).let { ctx -> MASTER_CHOICE_PHRASES.any { TextNormalizer.containsPhrase(ctx, it) } }
            buttons.add(ButtonCandidate(r.kind, r.phrase, r.label, r.node, target, blocked, key,
                selected = if (isRadioRefuse(r.node) && r.node.isCheckable) r.node.isChecked else null,
                isRadio = isRadioRefuse(r.node), isMaster = master, hiddenTarget = hiddenTarget, stuckTarget = stuckTarget))
        }

        // --- Onglets de catégories (OneTrust, 22/11 au soir) ---------------------------------
        // Centre de préférences OneTrust en mise en page « onglets » : la liste « Cookie Categories »
        // n'affiche l'interrupteur que de la catégorie sélectionnée (panneau « ot-tab-desc »). Sans
        // ouvrir chaque catégorie, CYRAVA ne voyait aucun réglage (« aucun contrôle de préférence
        // accessible reconnu », Opera et Chrome). Un onglet ne fait qu'afficher une catégorie : il
        // n'enregistre rien. Garde-fou, filtre de navigation et relecture de la cible s'appliquent.
        val categoryTabs = ArrayList<ButtonCandidate>()
        val tabLabels = HashSet<String>()
        for (menu in nodes) {
            if (!isCategoryMenu(menu)) continue
            for (tab in categoryTabCandidates(menu)) {
                if (!tab.isClickable || isToggle(tab) || isRadioLike(tab) || tab.isPassword || tab.isAccessibilityDataSensitive) continue
                if (tab.walk().any { it !== tab && (isToggle(it) || it.isClickable) }) continue
                val label = canonTexts[tab].orEmpty().firstOrNull()?.takeIf { it.isNotEmpty() } ?: continue
                if (TextNormalizer.wordCount(label) > MAX_CATEGORY_TAB_WORDS) continue
                if (match(label, rules.accept) != null || match(label, rules.confirm) != null) continue
                // Un refus ou une ouverture d'options n'est jamais un onglet de catégorie (relecture du 23/11).
                if (match(label, rules.refuse) != null || match(label, rules.globalReject) != null || match(label, rules.options) != null) continue
                if (!tabLabels.add(label)) continue
                val (target, blocked) = if (tab.isEnabled) resolveTarget(tab, allowTap = false) else (null to null)
                categoryTabs.add(
                    ButtonCandidate(
                        ButtonKind.VENDOR_TAB, "categorie", label, tab, target, blocked, "categorie|$label",
                        selected = tab.isSelected,
                    ),
                )
            }
        }

        // Un vrai bouton « Tout refuser » existe dans la fenêtre mais hors de l'écran (en bas d'une
        // longue page de préférences Didomi, par exemple) : le moteur doit défiler jusqu'à lui
        // plutôt que de cliquer ailleurs. Seuls les identifiants documentés et les formules d'au
        // moins deux mots comptent (un « Refuser » seul peut être le choix d'une seule finalité).
        val offscreenRefusal = buttons.any { b ->
            (b.kind == ButtonKind.REFUSE || b.kind == ButtonKind.GLOBAL_REJECT) &&
                b.target == null && b.blocked == null && b.hiddenTarget == null &&
                !b.labelNode.isVisibleToUser && b.labelNode.isEnabled && !b.labelNode.bounds.isEmpty &&
                (b.phrase.startsWith("id:") || TextNormalizer.wordCount(b.phrase) >= 2) &&
                guard.checkNode(b.labelNode) is SafetyGuard.Verdict.Allowed
        }

        // --- Interrupteurs --------------------------------------------------------------
        val toggleStates = LinkedHashMap<String, Boolean?>()
        val labelsBySignature = LinkedHashMap<String, String>()
        var unknownStates = 0
        val unknownSignatures = LinkedHashSet<String>()
        val checked = ArrayList<ToggleCandidate>()
        val keptOnSignatures = LinkedHashSet<String>()
        val sigCounts = HashMap<String, Int>()
        // Libellés génériques répétés (« Consentement », « Intérêt légitime ») : ils ne distinguent pas les lignes.
        val sharedLabels = toggleNodes.map { canonTexts[it].orEmpty().joinToString(" ") }
            .filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        // Nombre d'interrupteurs dans chaque sous-arbre (même définition que isToggle, sur tout l'arbre).
        val togglesInSubtree = java.util.IdentityHashMap<UiNode, Int>()
        for (n in root.walk()) {
            if (!isToggle(n)) continue
            var a: UiNode? = n
            while (a != null) {
                togglesInSubtree[a] = (togglesInSubtree[a] ?: 0) + 1
                a = a.parent
            }
        }
        for (t in toggleNodes) {
            val ownLabel = canonTexts[t].orEmpty().joinToString(" ")
            val row = toggleRow(t, togglesInSubtree)
            val rowText = row.walk().flatMap { canonTexts[it].orEmpty().asSequence() }.joinToString(" ").take(200)
            val context = toggleContext(t, canonTexts, sharedLabels).ifEmpty { rowText }
            val base = "$context|$ownLabel|${t.className.substringAfterLast('.')}"
            val ordinal = sigCounts.merge(base, 1, Int::plus) ?: 1
            val sig = if (ordinal == 1) base else "$base#$ordinal"
            labelsBySignature[sig] = ownLabel.ifEmpty { context }.ifEmpty { sig }
            val state = toggleState(t)
            if (state == null) {
                unknownStates++
                unknownSignatures.add(sig)
            }
            toggleStates[sig] = state
            if (state != true) continue
            // Interrupteurs qui restent légitimement activés : « toujours actif », catégorie obligatoire
            // « Nécessaires » (Cookiebot, Franklin 18/11 : le site refuse de la décocher et affiche une
            // alerte bloquante), ou case dont l'état activé signifie un refus (« Non », « Refuser »).
            val firstRowText = row.walk().map { canonTexts[it].orEmpty().firstOrNull().orEmpty() }.firstOrNull { it.isNotEmpty() }.orEmpty()
            // Audit I3 : « obligatoire », « requis »… seulement comme libellé court de la ligne (badge,
            // titre), ou sur un interrupteur grisé ; jamais trouvé au milieu d'une description.
            val shortRowTexts = row.walk().flatMap { canonTexts[it].orEmpty().asSequence() }
                .filter { TextNormalizer.wordCount(it) <= 4 }.toList()
            val alwaysActiveHere = rules.alwaysActive.any { p ->
                // Contre-audit : libellé EXACT seulement (« Toujours actif », « Obligatoire ») ;
                // un badge « Consentement requis » ne suffit pas.
                shortRowTexts.any { it == p } ||
                    (!t.isEnabled && TextNormalizer.containsPhrase(rowText, p))
            }
            val keptOn = alwaysActiveHere ||
                isNecessaryHeading(ownLabel) || isNecessaryHeading(firstRowText) ||
                (ownLabel.isNotEmpty() && (ownLabel in rules.keepCheckedLabels || ownLabel in rules.refusalFormulas))
            if (keptOn) {
                keptOnSignatures.add(sig)
                continue
            }
            // Activé mais grisé : impossible à décocher. Il reste compté comme activé (jamais « refusé »).
            if (!t.isEnabled) continue
            val (target, blocked) = resolveTarget(t, allowTap = true, forToggle = true)
            if (target == null && blocked == null && !t.isVisibleToUser) {
                // Liste de 480 partenaires (Google UMP, 21/11 au soir) : les lignes hors de l'écran sont
                // déjà dans l'arbre, avec leur état. Les décocher sans défiler évite des dizaines de
                // balayages ; l'état est relu à la capture suivante, comme pour les autres.
                val (offTarget, offBlocked) = resolveOffscreenToggle(t)
                checked.add(ToggleCandidate(t, sig, ownLabel, offTarget, offBlocked, offscreen = offTarget != null))
                continue
            }
            checked.add(ToggleCandidate(t, sig, ownLabel, target, blocked))
        }

        // --- Sections repliées -------------------------------------------------------------
        val expandCounts = HashMap<String, Int>()
        val expandables = nodes
            .filter {
                it.isExpandable && it.isEnabled && it.isVisibleToUser && !it.bounds.isEmpty &&
                    !it.isPassword && !it.isAccessibilityDataSensitive && !isToggle(it)
            }
            .filter { guard.checkNode(it).isAllowed }
            .map { n ->
                val label = n.walk().flatMap { canonTexts[it].orEmpty().asSequence() }.joinToString(" ").take(100)
                val ordinal = expandCounts.merge(label, 1, Int::plus) ?: 1
                ExpandCandidate(n, if (ordinal == 1) label else "$label#$ordinal", label)
            }

        // --- Conteneur à faire défiler -----------------------------------------------------
        val scrollTarget = nodes.asSequence()
            .filter { it.isScrollable && it.isEnabled && it.isVisibleToUser && !it.isAccessibilityDataSensitive && !it.bounds.isEmpty }
            .filterNot { n -> SCROLL_EXCLUDED.any { n.className.contains(it, ignoreCase = true) } }
            .map { container ->
                Triple(container, (toggleNodes + radioNodes).count { it.isDescendantOf(container) }, container.walk().count())
            }
            .filter { (_, containedToggles, _) -> containedToggles > 0 }
            // Privilégie le conteneur qui contient le plus de contrôles CMP, puis le plus petit
            // conteneur et le sous-arbre le plus proche des contrôles (liste interne plutôt que WebView).
            .minWithOrNull(
                compareByDescending<Triple<UiNode, Int, Int>> { it.second }
                    .thenBy { it.first.bounds.area }
                    .thenBy { it.third },
            )?.first

        // Zone de recherche : la liste des interrupteurs si elle existe, sinon le conteneur défilant
        // qui contient le plus de boutons reconnus (validation cachée en bas de la fenêtre).
        val searchScrollTarget = scrollTarget ?: nodes.asSequence()
            .filter { it.isScrollable && it.isEnabled && it.isVisibleToUser && !it.isAccessibilityDataSensitive && !it.bounds.isEmpty }
            .filterNot { n -> SCROLL_EXCLUDED.any { n.className.contains(it, ignoreCase = true) } }
            .map { container -> container to buttons.count { it.labelNode.isDescendantOf(container) } }
            .filter { (_, containedButtons) -> containedButtons > 0 }
            .maxWithOrNull(compareBy<Pair<UiNode, Int>> { it.second }.thenByDescending { it.first.bounds.area })
            ?.first
        // Un bouton écrasé (hauteur nulle) ne peut pas être amené à l'écran : ni recherche, ni « afficher ».
        val offscreenConfirm = buttons.any { b ->
            b.kind == ButtonKind.CONFIRM && b.blocked == null && !b.labelNode.isVisibleToUser && b.labelNode.isEnabled &&
                !b.labelNode.bounds.isEmpty && guard.checkNode(b.labelNode) is SafetyGuard.Verdict.Allowed
        }
        val confirmToReveal = buttons.firstOrNull { b ->
            b.kind == ButtonKind.CONFIRM && b.blocked == null && !b.labelNode.isVisibleToUser && b.labelNode.isEnabled &&
                !b.labelNode.bounds.isEmpty && guard.checkNode(b.labelNode) is SafetyGuard.Verdict.Allowed
        }?.labelNode

        // --- Page entière (faux positifs du 27/11) -------------------------------------------
        // Dans un navigateur, une vraie fenêtre de cookies est un bloc à part (bandeau, boîte au milieu).
        // Si le plus petit panneau qui réunit les indices et les contrôles couvre presque toute la page
        // affichée, c'est la page elle-même qui parle de cookies : un article, un fil de forum, une
        // documentation (journal LinuxFr, fil XDA et README GitHub de CYRAVA, 27/11). Le seuil de 80 %
        // est celui déjà utilisé pour la fenêtre entière ; il est mesuré ici sur la zone de la page
        // (WebView) et non sur tout l'écran, qui contient aussi la barre d'adresse du navigateur.
        val pageWide = webContentOnly && viewport != null && scopedRoot != null && run {
            val p = scopedRoot.bounds
            val w = minOf(p.right, viewport.right) - maxOf(p.left, viewport.left)
            val h = minOf(p.bottom, viewport.bottom) - maxOf(p.top, viewport.top)
            w > 0 && h > 0 && w.toLong() * h * 100L >= viewport.area * PAGE_WIDE_PERCENT
        }
        // Un vrai bouton : cliquable lui-même ou porté par un élément cliquable proche (3 niveaux) dont
        // le texte reste court. Un fragment de phrase (« … and accept. », « tap OK ») n'en est pas un.
        fun realControl(n: UiNode): Boolean {
            val carrier = if (n.isClickable && n.isEnabled) n
            else n.ancestors().take(3).firstOrNull { it.isClickable && it.isEnabled } ?: return false
            val words = carrier.walk().sumOf { o -> allCanonTexts[o].orEmpty().sumOf { TextNormalizer.wordCount(it) } }
            return words <= maxLabelWords
        }
        // Une simple validation (« Enregistrer », « Save ») ne suffit pas : une page de réglages de compte
        // qui parle de cookies en a une, et CYRAVA y aurait décoché des cases (relecture sécurité du 27/11).
        val pageWideChoice = pageWide && buttons.any {
            it.kind in PAGE_START_KINDS && it.labelNode.isVisibleToUser && it.labelNode.isEnabled &&
                !it.labelNode.bounds.isEmpty && realControl(it.labelNode)
        }

        val light = (buttons.map { "${it.kind}:${it.label}" }.sorted() + hints.sorted()).hashCode()

        // --- Plateforme et empreinte structurelle -------------------------------------------
        val viewIds = nodes.mapNotNull { it.viewId?.lowercase() }
        val platform = rules.platforms.firstOrNull { p ->
            p.markers.any { m -> viewIds.any { it.contains(m) } } ||
                p.phrases.any { TextNormalizer.containsPhrase(allText, it) }
        }?.name ?: if (hints.any { it in GENERIC_TCF_HINTS }) PLATFORM_GENERIC_TCF else PLATFORM_UNKNOWN
        val toggleBucket = when (toggleStates.size) {
            0 -> 0
            in 1..5 -> 1
            in 6..30 -> 2
            else -> 3
        }
        // Conserver les effectifs par catégorie, pas seulement leur présence :
        // remplacer un bouton Refuser par un bouton Options doit modifier l’empreinte,
        // tout en restant indépendant du libellé et de la langue.
        val buttonShape = buttons
            .groupingBy { it.kind }
            .eachCount()
            .entries
            .sortedBy { it.key.name }
            .joinToString(",") { "${it.key.name}:${it.value}" }
        val structure = listOf(
            platform,
            buttonShape,
            toggleBucket.toString(),
            hints.filter { it.startsWith("id:") }.sorted().joinToString(","),
        ).joinToString("|").hashCode()

        return ScreenAnalysis(
            root = root,
            hints = hints,
            buttons = buttons,
            checkedToggles = checked,
            toggleStates = toggleStates,
            expandables = expandables,
            unknownStateToggles = unknownStates,
            scrollTarget = scrollTarget,
            windowBounds = root.bounds,
            fingerprint = fp,
            lightFingerprint = light,
            minHints = if (continuing && hints.any { it.startsWith("id:") }) 1 else rules.minHints,
            platform = platform,
            structureSignature = structure,
            unknownToggleSignatures = unknownSignatures,
            offscreenRefusal = offscreenRefusal,
            scopeComplete = scopedRoot != null && scopedRoot.walk().none { it.childrenOmitted },
            keptOnToggles = keptOnSignatures,
            panelRoot = scopedRoot,
            searchScrollTarget = searchScrollTarget,
            offscreenConfirm = offscreenConfirm,
            confirmToReveal = confirmToReveal,
            contentSignature = allText.hashCode(),
            categoryTabs = categoryTabs,
            toggleLabelsBySignature = labelsBySignature,
            pageWide = pageWide,
            pageWideChoice = pageWideChoice,
        )
    }

    /**
     * Cible de clic sûre pour [node] :
     * 1. le nœud lui-même s'il est cliquable ;
     * 2. sinon son premier parent cliquable (4 niveaux max) s'il ne contient aucun autre élément interactif ;
     * 3. sinon un tap au centre du nœud (si [allowTap] et visible).
     * Chaque cible passe par le garde-fou ; en cas de blocage, aucune cible n'est renvoyée.
     * Pour un bouton ([forToggle] faux), une cible qui est ou contient un interrupteur est refusée :
     * la toucher pourrait RECOCHER cet interrupteur (ex. <label> englobant une case à cocher).
     */
    fun resolveTarget(
        node: UiNode,
        allowTap: Boolean,
        forToggle: Boolean = false,
    ): Pair<ClickTarget?, SafetyGuard.Verdict.Blocked?> {
        // Un élément hors écran ne doit jamais être actionné depuis un instantané.
        // Il pourra être réévalué après un défilement qui l'aura rendu visible.
        if (!node.isVisibleToUser || !node.isEnabled) return null to null

        fun guarded(target: ClickTarget): Pair<ClickTarget?, SafetyGuard.Verdict.Blocked?> {
            if (!forToggle && target.guardNode.walk().any { isToggle(it) }) return null to null
            return when (val v = guard.checkNode(target.guardNode)) {
                is SafetyGuard.Verdict.Blocked -> null to v
                SafetyGuard.Verdict.Allowed -> target to null
            }
        }

        if (node.isClickable) return guarded(ClickTarget.Node(node))
        var p = node.parent
        var depth = 0
        while (p != null && depth < 4) {
            if (p.isClickable && p.isEnabled && p.isVisibleToUser && !p.bounds.isEmpty) {
                val ancestor: UiNode = p
                val othersInteractive = ancestor.walk().any {
                    it !== ancestor && it !== node && (it.isClickable || isToggle(it)) && !node.isDescendantOf(it)
                }
                if (!othersInteractive) return guarded(ClickTarget.Node(ancestor))
                break
            }
            p = p.parent
            depth++
        }
        if (allowTap && node.isVisibleToUser && !node.bounds.isEmpty) {
            return guarded(ClickTarget.Point(node, node.bounds.centerX, node.bounds.centerY))
        }
        return null to null
    }

    /**
     * Cible d'un interrupteur ACTIVÉ hors de l'écran : le nœud lui-même s'il est cliquable, sinon son
     * premier parent cliquable (4 niveaux max) qui ne contient aucun autre élément interactif. Toujours
     * une action d'accessibilité sur un nœud ([ClickTarget.Node]), jamais un appui aux coordonnées
     * (la position d'un élément hors écran ne veut rien dire). Le garde-fou s'applique.
     */
    private fun resolveOffscreenToggle(toggle: UiNode): Pair<ClickTarget?, SafetyGuard.Verdict.Blocked?> {
        if (toggle.isVisibleToUser || !toggle.isEnabled || toggleState(toggle) != true) return null to null
        fun guarded(n: UiNode): Pair<ClickTarget?, SafetyGuard.Verdict.Blocked?> =
            when (val v = guard.checkNode(n)) {
                is SafetyGuard.Verdict.Blocked -> null to v
                SafetyGuard.Verdict.Allowed -> ClickTarget.Node(n) to null
            }
        if (toggle.isClickable) return guarded(toggle)
        var p = toggle.parent
        var depth = 0
        while (p != null && depth < 4) {
            if (p.isClickable && p.isEnabled) {
                val ancestor: UiNode = p
                val othersInteractive = ancestor.walk().any {
                    it !== ancestor && it !== toggle && (it.isClickable || isToggle(it)) && !toggle.isDescendantOf(it)
                }
                return if (othersInteractive) null to null else guarded(ancestor)
            }
            p = p.parent
            depth++
        }
        return null to null
    }

    /**
     * Cible d'un bouton écrasé (hauteur nulle, voir [ButtonCandidate.hiddenTarget]) : le nœud lui-même
     * s'il est cliquable, sinon son premier parent cliquable (4 niveaux au plus) qui ne contient aucun
     * autre élément interactif ni interrupteur. Toujours une action d'accessibilité sur un nœud, jamais
     * un appui aux coordonnées. Un bouton grisé n'a pas de cible. Le garde-fou s'applique.
     */
    private fun resolveSquashedButton(node: UiNode, requireEmpty: Boolean = true): ClickTarget? {
        if (!node.isEnabled || (requireEmpty && !node.bounds.isEmpty)) return null
        fun guarded(n: UiNode): ClickTarget? {
            if (n.walk().any { isToggle(it) || isRadioLike(it) }) return null
            return if (guard.checkNode(n).isAllowed) ClickTarget.Node(n) else null
        }
        if (node.isClickable) return guarded(node)
        var p = node.parent
        var depth = 0
        while (p != null && depth < 4) {
            if (p.isClickable) {
                if (!p.isEnabled) return null
                val ancestor: UiNode = p
                val othersInteractive = ancestor.walk().any {
                    it !== ancestor && it !== node && (it.isClickable || isToggle(it)) && !node.isDescendantOf(it)
                }
                return if (othersInteractive) null else guarded(ancestor)
            }
            p = p.parent
            depth++
        }
        return null
    }

    /**
     * Nœuds de l'interface du navigateur (barre d'adresse, onglets, menu) : tout ce qui n'est pas
     * dans une WebView. Vide si l'écran ne contient aucune WebView (page native du navigateur).
     */
    private fun browserInterface(root: UiNode): Set<UiNode> {
        val pages = root.walk().filter { it.className.contains("WebView", ignoreCase = true) }.toList()
        if (pages.isEmpty()) return emptySet()
        val inPage = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<UiNode, Boolean>())
        pages.forEach { p -> p.walk().forEach { inPage.add(it) } }
        return root.walk().filterTo(java.util.Collections.newSetFromMap(java.util.IdentityHashMap<UiNode, Boolean>())) { it !in inPage }
    }

    /**
     * Plus grand ancêtre (4 niveaux max) ne contenant qu'un seul interrupteur : la « ligne » de l'interrupteur.
     * [togglesInSubtree] : nombre d'interrupteurs de chaque sous-arbre, calculé une fois par analyse.
     * Avant le 22/11, chaque interrupteur relisait toute la liste : 367 cases = 1,3 s d'analyse sur téléphone.
     */
    private fun toggleRow(toggle: UiNode, togglesInSubtree: Map<UiNode, Int>): UiNode {
        var row = toggle
        var p = toggle.parent
        var depth = 0
        while (p != null && depth < 4) {
            // L'interrupteur lui-même est compté dans le sous-arbre de son ancêtre : « un autre » = plus d'un.
            if ((togglesInSubtree[p] ?: 0) > 1) break
            row = p
            p = p.parent
            depth++
        }
        return row
    }

    /**
     * Libellé de l'élément auquel appartient un interrupteur (nom de la finalité ou du fournisseur) :
     * textes du plus proche ancêtre (5 niveaux max) qui en contient, hors interrupteurs et hors
     * libellés génériques répétés. Sert à suivre chaque interrupteur d'un instantané à l'autre.
     */
    private fun toggleContext(toggle: UiNode, canonTexts: Map<UiNode, List<String>>, sharedLabels: Set<String>): String {
        var p = toggle.parent
        var depth = 0
        while (p != null && depth < 5) {
            val texts = p.walk().filter { !isToggle(it) }
                .flatMap { canonTexts[it].orEmpty().asSequence() }
                .filter { it !in sharedLabels }
                .distinct()
                .toList()
            if (texts.isNotEmpty()) return texts.joinToString(" ").take(160)
            p = p.parent
            depth++
        }
        return ""
    }

    /**
     * Texte de la ligne d'un choix de refus, pour savoir s'il s'agit du choix maître (« Tous les
     * partenaires › Bloquer ») : on remonte tant que la ligne ne contient aucun AUTRE refus
     * (les « Bloquer X » des autres fournisseurs arrêtent la remontée).
     */
    private fun masterContext(node: UiNode, canonTexts: Map<UiNode, List<String>>): String {
        val refuseWords = (rules.radioRefuse + rules.refuse).filter { TextNormalizer.wordCount(it) == 1 }
        fun refusal(t: String) = refuseWords.any { t == it || t.startsWith("$it ") }
        var row = node
        var p = node.parent
        var depth = 0
        while (p != null && depth < 4) {
            val other = p.walk().any { n ->
                n !== node && !n.isDescendantOf(node) && !node.isDescendantOf(n) && canonTexts[n].orEmpty().any(::refusal)
            }
            if (other) break
            row = p
            p = p.parent
            depth++
        }
        // Intitulé placé juste AVANT la ligne (Chrome : « Tous les partenaires » puis le groupe radio),
        // seulement s'il s'agit d'un simple texte sans aucun choix de refus.
        val label = row.parent?.children?.let { siblings ->
            val i = siblings.indexOfFirst { it === row }
            siblings.getOrNull(i - 1)?.takeIf { prev ->
                prev.walk().none { n -> canonTexts[n].orEmpty().any(::refusal) || isRadioRefuse(n) || isToggle(n) }
            }
        }?.walk()?.flatMap { canonTexts[it].orEmpty().asSequence() }?.joinToString(" ").orEmpty()
        return (label + " " + row.walk().flatMap { canonTexts[it].orEmpty().asSequence() }.joinToString(" ")).trim().take(200)
    }

    /** Contexte textuel d'un bouton dont le libellé apparaît plusieurs fois (ex. « Refuser » par fournisseur). */
    private fun rowContext(node: UiNode, canonTexts: Map<UiNode, List<String>>): String {
        val label = canonTexts[node].orEmpty().firstOrNull().orEmpty()
        var row = node
        var p = node.parent
        var depth = 0
        while (p != null && depth < 4) {
            // Le libellé répété À L'INTÉRIEUR du bouton (radio « Bloquer » + son texte « Bloquer »)
            // ne compte pas : sinon la ligne s'arrêtait au bouton et « Tous les partenaires » était perdu.
            val sameLabel = p.walk().count { n ->
                n !== node && !n.isDescendantOf(node) && canonTexts[n].orEmpty().any { it == label }
            }
            if (sameLabel > 0) break
            row = p
            p = p.parent
            depth++
        }
        return row.walk().flatMap { canonTexts[it].orEmpty().asSequence() }.joinToString(" ").take(120)
    }

    private companion object {
        /** Niveaux au plus pour retrouver le groupe d'un radio enveloppé seul (Firefox). */
        const val RADIO_GROUP_MAX_CLIMB = 3

        /** Intitulés (forme canonique) de la liste des catégories d'un centre de préférences en onglets. */
        val CATEGORY_MENU_LABELS = setOf(
            "cookie categories", "categories de cookies", "categories des cookies", "categories de traceurs",
            "cookie kategorien", "categorias de cookies", "categorie di cookie",
        )
        /** Un onglet de catégorie porte un titre court (« Cookies marketing de Blizzard et de ses affiliés »). */
        const val MAX_CATEGORY_TAB_WORDS = 12

        /** Élargissement du panneau jusqu'au bouton de validation : 3 niveaux, 6 dans le conteneur d'une plateforme connue. */
        const val PANEL_EXTEND_LEVELS = 3
        const val PANEL_EXTEND_MAX = 6
        /** Part de la page affichée (en %) au-delà de laquelle le panneau est la page elle-même. */
        const val PAGE_WIDE_PERCENT = 80L
        val WEB_TOGGLE_ROLES = setOf("switch", "checkbox")
        val SKIP_LINK_PREFIXES = listOf("aller a ", "aller au ", "go to ", "skip to ", "jump to ")

        /** Intitulés (forme canonique) d'une ligne qui règle toute la liste d'un coup. */
        /** Paires « Non | Oui » (voir yesNoPartner dans [analyze]) : libellés exacts seulement. */
        val YES_NO_REFUSALS = setOf("non", "no")
        val YES_NO_ACCEPTS = setOf("oui", "yes")
        /** Débuts de mots qui inversent le sens d'une paire « Non | Oui » (« refuser », « désactiver »…). */
        val YES_NO_NEGATIVE_WORDS = listOf(
            "refus", "desactiv", "oppos", "bloqu", "retir", "rejet", "supprim", "interdi", "empech",
            "refuse", "disabl", "object", "block", "withdraw", "reject", "deny", "opt",
        )

        val MASTER_CHOICE_PHRASES = listOf(
            "tous les partenaires", "tous les fournisseurs", "toutes les finalites", "vous autorisez",
            "all partners", "all vendors", "all purposes", "alle partner", "todos los socios", "tutti i partner",
        )

        /**
         * Premier mot du libellé « Accepter X » qui accompagne « Refuser X » dans une paire radio
         * (forme canonique). Sert uniquement à RECONNAÎTRE la paire : ce bouton n'est jamais cliqué.
         */
        val PAIRED_ACCEPT_WORDS = listOf(
            "accepter", "autoriser", "accept", "allow", "akzeptieren", "zulassen", "erlauben",
            "aceptar", "permitir", "accetta", "accettare", "consenti", "aceitar", "accepteren", "toestaan",
        )

        /** Mots tolérés autour d'une formule de refus (texte canonique, 7 langues). */
        val NEUTRAL_REFUSAL_WORDS = setOf(
            // français
            "tout", "tous", "toutes", "et", "fermer", "maintenant", "le", "la", "les", "des", "cookies", "cookie",
            "traceurs", "optionnels", "facultatifs", "publicitaires", "x",
            // anglais
            "all", "and", "close", "now", "the", "optional", "tracking", "trackers", "advertising",
            // allemand
            "alle", "und", "schliessen", "jetzt", "optionalen",
            // espagnol
            "todo", "todos", "todas", "y", "cerrar", "las", "los", "ahora", "opcionales",
            // italien
            "tutto", "tutti", "e", "chiudi", "chiudere", "i", "ora", "facoltativi",
            // portugais
            "tudo", "fechar", "os", "agora", "opcionais",
            // néerlandais
            "alles", "en", "sluiten", "nu", "optionele",
        )
        /** Mots tolérés autour d'une formule de validation (texte canonique). Pas de « tout / all » : ambigu. */
        val NEUTRAL_CONFIRM_WORDS = setOf(
            // français
            "mes", "mon", "ma", "les", "la", "le", "vos", "votre", "et", "choix", "preferences", "parametres",
            "reglages", "selection", "fermer", "quitter", "x", "de", "des", "du", "cookies", "cookie", "traceurs",
            // anglais
            "my", "the", "your", "and", "choices", "preferences", "settings", "selection", "close", "exit", "cookies", "cookie",
            // allemand
            "meine", "und", "auswahl", "einstellungen", "schliessen", "cookie",
            // espagnol
            "mis", "y", "preferencias", "configuracion", "seleccion", "cerrar",
            // italien
            "le", "mie", "e", "preferenze", "scelte", "impostazioni", "chiudi",
            // portugais
            "minhas", "escolhas", "fechar",
            // néerlandais
            "mijn", "en", "voorkeuren", "keuzes", "instellingen", "sluiten",
        )
        /**
         * Mots tolérés autour d'une acceptation d'un seul mot (« Accepter et continuer », « Accept all
         * cookies »). Un nom propre (« Consent-O-Matic ») ou une phrase n'en font pas un bouton.
         */
        val NEUTRAL_ACCEPT_WORDS = NEUTRAL_REFUSAL_WORDS + setOf(
            "continuer", "continue", "weiter", "continuar", "continua", "doorgaan", "merci", "thanks",
            "j", "je", "i", "ok", "oui", "yes", "ja", "si",
        )
        const val CLASS_NEUTRAL = 0
        const val CLASS_TOGGLE = 1
        const val CLASS_EXCLUDED = 2
        val SCROLL_EXCLUDED = listOf("Horizontal", "ViewPager", "Spinner", "SeekBar")
        val GENERIC_TCF_HINTS = setOf(
            "iab", "tcf", "interet legitime", "interets legitimes", "legitimate interest", "legitimate interests",
            "fournisseurs", "vendors", "berechtigtes interesse", "interes legitimo", "legittimo interesse",
            "interesse legitimo", "gerechtvaardigd belang",
        )
    }
}

/** Compteur de partenaires (« 2 partenaires », « nos 751 partenaires », « 12 vendors ») : jamais cliqué. */
private val PARTNER_COUNT = Regex("^(nos |voir nos |voir les |les )?\\d+ (partenaires?|fournisseurs?|vendors?|partners?)( iab)?$")

/**
 * Titre EXACT d'une catégorie obligatoire, éventuellement suivi du nombre de cookies : « Nécessaires »,
 * « Nécessaires 17 », « Cookies strictement nécessaires ». « Nécessaires à la publicité » ou un
 * fournisseur « Essential Ads » ne correspondent pas.
 */
private val NECESSARY_HEADING = Regex(
    "^(cookies |traceurs )?(strictement )?(necessaires?|essentiels?|necessary|essential|strictly necessary)( cookies)?( \\d+)?$",
)

/** Vrai si le texte (forme canonique) est exactement le titre d'une catégorie « Nécessaires ». */
internal fun isNecessaryHeading(canon: String): Boolean = NECESSARY_HEADING.matches(canon)
