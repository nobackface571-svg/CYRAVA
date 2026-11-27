package fr.refusbot.core

/**
 * Écran à traiter : la fenêtre active d'une appli surveillée.
 *
 * Lecture partielle (22/11 au soir) : pendant une longue liste, le service peut ne relire que la
 * fenêtre de cookies déjà repérée. [root] est alors la racine de la fenêtre, mais seule la chaîne
 * d'ancêtres du panneau est présente (sans leurs autres enfants), puis le panneau en entier ;
 * [partialTopKey] est la clé de ce panneau dans l'arbre. -1 : lecture complète.
 */
data class Screen(
    val packageName: String,
    val root: UiNode,
    val isTruncated: Boolean = false,
    val partialTopKey: Int = -1,
) {
    val isPartial: Boolean get() = partialTopKey >= 0
}

/** Opération élémentaire demandée au pilote Android. */
sealed interface Op {
    /** Clic sur un bouton (refus, options, validation…) ou un interrupteur à décocher. */
    data class Click(
        val target: ClickTarget,
        val label: String,
        val isToggle: Boolean,
        val toggleNode: UiNode? = null,
        /** « Autoriser la sélection » après vérification que tout est refusé (voir [SelectionConfirm]). */
        val selectionConfirm: Boolean = false,
        /**
         * Appui de secours aux coordonnées si l'action d'accessibilité échoue. Jamais pour une
         * validation (audit du 21/11) : seul un vrai contrôle cliquable peut enregistrer des choix.
         */
        val allowTapFallback: Boolean = true,
        /**
         * Interrupteur hors de l'écran (présent dans l'arbre), ou bouton écrasé sous la barre du
         * navigateur ([ButtonCandidate.hiddenTarget]) : action d'accessibilité sur le nœud seulement,
         * sans exiger qu'il soit visible, et jamais d'appui aux coordonnées.
         */
        val offscreen: Boolean = false,
    ) : Op

    /**
     * ACTION_SHOW_ON_SCREEN : fait apparaître le bouton de validation, en bas d'une longue liste déjà
     * entièrement décochée, sans défiler ligne à ligne. Ne clique sur rien.
     */
    data class ShowOnScreen(val node: UiNode, val label: String) : Op

    /** ACTION_EXPAND : déplie une section (accordéon) qui peut cacher des interrupteurs. */
    data class Expand(val node: UiNode, val label: String) : Op

    /** ACTION_SCROLL_FORWARD sur le conteneur ; repli : balayage dans ses bornes. */
    data class Scroll(val container: UiNode) : Op

    /** Balayage vers le haut dans une zone (aucun conteneur défilable exposé). */
    data class Swipe(val area: Bounds, val window: UiNode) : Op
}

/** Décision du moteur pour l'instantané courant. */
sealed interface Step {
    /** Rien à faire (pas de fenêtre de consentement). */
    data object Idle : Step

    /** Attendre puis reprendre un instantané (l'écran n'a pas encore réagi). */
    data class Wait(val delayMs: Long) : Step

    /** Exécuter ces opérations, attendre [Limits.minDelayMs]–[Limits.maxDelayMs], puis recommencer. */
    data class Act(val ops: List<Op>, val description: String) : Step

    /** Session terminée. */
    data class Done(val outcome: Outcome) : Step
}

enum class RefusalMethod { DIRECT, OPTIONS }

/** Résultat des opérations remises au service d'accessibilité après une décision du moteur. */
data class ActionExecutionStats(
    val requested: Int = 0,
    val executed: Int = 0,
    val blocked: Int = 0,
    val navigationBlocked: Boolean = false,
    /** Le défilement n'a rien déplacé et Android ne propose plus de défiler : fin de liste mesurée. */
    val scrollAtEnd: Boolean = false,
)

/** Profil de comportement choisi par l'utilisateur (global ou par appli). */
enum class Policy {
    /** Refuse toutes les options facultatives reconnues (fenêtres « certaines » ou « probables »). */
    STRICT,

    /** N'agit que sur les fenêtres reconnues avec une confiance « certaine ». */
    PRUDENT,

    /** N'agit jamais : analyse et journalise seulement. */
    OBSERVATION,
}

/** Résultat d'une session, tel qu'affiché dans l'historique. */
enum class OutcomeStatus(val label: String) {
    /** Conservé pour relire l'historique des versions précédentes : n'est plus jamais produit. */
    DIRECT_SUCCESS("Refus direct (ancienne version, non vérifié)"),

    /** Interrupteurs observés désactivés avant la validation : seule preuve que le moteur peut constater. */
    OPTIONS_SUCCESS("Refus par les préférences : interrupteurs vérifiés désactivés"),

    /**
     * Le bouton de refus a été cliqué et la fenêtre s'est fermée, mais rien d'observable ne prouve
     * que l'appli a enregistré le refus : la fermeture seule n'est pas une preuve.
     */
    UNVERIFIED("Refusé avec le bouton du site (non vérifiable)"),
    PARTIAL("Refus partiel"),
    INCOMPLETE_CAPTURE("Capture incomplète — refus non établi"),
    IMPOSSIBLE("Refus impossible"),
    INTERRUPTED("Action interrompue"),
    OBSERVED("Observé (aucune action)"),
    /** Refus non validé : l'utilisateur doit terminer lui-même les choix incertains. */
    HANDED_OVER("À terminer à la main — fenêtre laissée ouverte"),
    ;

    /** Seul statut appuyé par une observation de l'état final des choix. */
    val isVerified: Boolean get() = this == OPTIONS_SUCCESS

    val isRefusal: Boolean get() = this == DIRECT_SUCCESS || this == OPTIONS_SUCCESS || this == UNVERIFIED || this == PARTIAL
}

data class Outcome(
    val packageName: String,
    val success: Boolean,
    val method: RefusalMethod?,
    val togglesOff: Int,
    val actions: Int,
    val reason: String?,
    /** Nombre d'interrupteurs distincts vus pendant la session (0 = aucun détecté dans les réglages). */
    val togglesSeen: Int = 0,
    /** Plus grand nombre d'interrupteurs dont l'état n'était pas publié par l'appli sur un écran. */
    val togglesUnknown: Int = 0,
    /**
     * Dernier écran de consentement vu, joint aux échecs « non résolus » pour l'export diagnostic.
     * Null en cas de succès ou si la fenêtre a simplement été fermée / quittée.
     */
    val diagnosticRoot: UiNode? = null,
    /** Résultat détaillé (succès, partiel, impossible, interrompu, observé). */
    val status: OutcomeStatus = if (success) OutcomeStatus.OPTIONS_SUCCESS else OutcomeStatus.IMPOSSIBLE,
    /** Plateforme reconnue au début de la session. */
    val platform: String = PLATFORM_UNKNOWN,
    /** Confiance de la reconnaissance au début de la session. */
    val confidence: Confidence = Confidence.PROBABLE,
    /** Empreinte structurelle de la fenêtre (comparaison d'une version à l'autre de l'appli). */
    val structureSignature: Int = 0,
    /** Interrupteurs restés cochés malgré les tentatives (refus incomplet). */
    val togglesStillOn: Int = 0,
    /** Interrupteurs lus activés lors de leur première lecture. */
    val togglesInitiallyOn: Int = 0,
    /** Interrupteurs déjà désactivés lors de leur première lecture. */
    val togglesAlreadyOff: Int = 0,
    /** Interrupteurs sur lesquels un clic a été envoyé. */
    val togglesAttempted: Int = 0,
    /** Interrupteurs cliqués sans relecture explicite de leur nouvel état. */
    val togglesUnconfirmed: Int = 0,
    /** Une fenêtre satisfaisant les critères de consentement a été détectée. */
    val consentDetected: Boolean = false,
    /** Maximum de boutons, interrupteurs et sections reconnus sur une capture de la session. */
    val controlsRecognized: Int = 0,
    /** Opérations remises au service d'accessibilité. */
    val actionAttempts: Int = 0,
    /** Opérations acceptées par Android ; cela ne prouve pas le changement d'état du site. */
    val actionExecutions: Int = 0,
    /** Opérations bloquées par un garde-fou ou une revalidation. */
    val blockedActions: Int = 0,
    /** true uniquement si l'état final des préférences a été observé désactivé ; null si aucune action. */
    val stateVerified: Boolean? = null,
    /** La capture a atteint sa limite de profondeur ou de nœuds. */
    val captureTruncated: Boolean = false,
    /**
     * Interrupteurs non vérifiés, NOMMÉS, avec la raison (resté activé, état jamais publié,
     * garde-fou, état non relu, jamais traité). Le libellé est du texte d'écran : réservé au
     * fichier de diagnostic, jamais copié dans l'historique (règle de confidentialité).
     */
    val controlIssues: List<ControlIssue> = emptyList(),
)

/**
 * Machine à états qui refuse une fenêtre de consentement, une étape à la fois.
 *
 * Le pilote (service d'accessibilité ou test) appelle [next] avec un instantané, exécute les
 * opérations renvoyées, attend, puis rappelle [next] jusqu'à [Step.Done] ou [Step.Idle].
 *
 * Parcours COMMUN à toutes les plateformes (Didomi, Sourcepoint, Axeptio, OneTrust, Quantcast,
 * Cookiebot, inconnues…). Les plateformes ne fournissent que leurs libellés et repères d'écran
 * (`rules.json`, `cmp-catalog.json`), jamais leur propre logique de clic.
 *  1. premier écran : refus direct (« Tout refuser », « Continuer sans accepter »…), puis fin ;
 *  2. sinon ouverture des réglages (« Paramétrer », « Personnaliser »…) ;
 *  3. finalités : d'abord le choix maître ou « Tout désactiver » de l'écran, puis chaque finalité
 *     encore acceptée (interrupteur, « Bloquer », « Refuser » de chaque ligne). Le « Tout refuser » de
 *     la page des préférences, qui ferme souvent la fenêtre sans rien laisser vérifier, n'est plus
 *     qu'un dernier recours, après ces choix (BFM, 27/11) ;
 *  4. lien partenaires / fournisseurs : l'ouvrir et y cliquer le refus global, sans déplier aucun
 *     partenaire ;
 *  5. validation (« Enregistrer », « Confirmer mes choix »…) ; si elle n'est pas visible, défiler
 *     jusqu'en bas de la fenêtre avant de conclure qu'elle manque.
 *
 * Jamais de clic sans passage par le [SafetyGuard] (aucune acceptation) ni par le
 * [NavigationFilter] (aucun accordéon, aucune page externe). Aucune section n'est jamais dépliée.
 * Un même élément n'est jamais cliqué deux fois ; un clic sans effet fait passer à l'étape suivante.
 * Limites : [Limits.maxActions] étapes et [Limits.timeoutMs] par fenêtre.
 */
class RefusalEngine(
    private val rules: Rules,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val analyzer = ScreenAnalyzer(rules)
    private val limits = rules.limits

    /** Laisser la fenêtre ouverte si un choix reste illisible ou non vérifié. */
    @Volatile
    var holdWhenUnverifiable: Boolean = false

    /**
     * « Refus complet » (réglage, demandé le 20/11) : même quand un « Tout refuser » existe, passer
     * par les préférences et leurs sous-pages pour décocher aussi l'intérêt légitime, vérifier, puis
     * enregistrer. Didomi, par exemple, laisse l'intérêt légitime actif après un refus au premier
     * niveau si le site l'a configuré ainsi. Repli automatique sur le refus direct quand les
     * préférences ne permettent pas de terminer.
     */
    var completeRefusal: Boolean = false

    private var session: Session? = null
    /** Délai par appli : jusqu'à quand, et pour quelles fenêtres ([ANY_WINDOW] = toutes). */
    private val cooldowns = HashMap<String, Pair<Long, Set<Int>>>()

    /**
     * Navigateurs, après un échec ou une fenêtre laissée à l'utilisateur : courte pause sur la forme de la
     * fenêtre (boutons et indices), même si son texte change tout seul (compteur, « il y a 2 min »…).
     * Sans elle, une nouvelle session repartait à chaque capture (relecture sécurité du 21/11).
     */
    private val shortCooldowns = HashMap<String, Pair<Long, Int>>()

    /** Reprend les délais d'un ancien moteur (règles changées) : la fenêtre traitée n'est pas refaite. */
    fun adoptCooldowns(previous: RefusalEngine) {
        cooldowns.putAll(previous.cooldowns)
        shortCooldowns.putAll(previous.shortCooldowns)
        unusablePreferences.addAll(previous.unusablePreferences)
        unusablePreferenceApps.addAll(previous.unusablePreferenceApps)
    }

    /**
     * Plateformes dont les préférences se sont montrées inutilisables (aucun réglage lisible) en refus
     * complet, depuis le démarrage du service : leur « Tout refuser » n'est plus mis en attente.
     */
    private val unusablePreferences = HashSet<String>()

    /**
     * Applis dont les préférences se sont montrées NÉFASTES sur une plateforme inconnue (la fenêtre
     * s'est perdue en les ouvrant), depuis le démarrage du service : leur « Tout refuser » n'est
     * plus mis en attente des préférences. Les navigateurs n'y entrent jamais : chez eux, ouvrir les
     * options d'une fenêtre inconnue peut faire perdre la fenêtre (Opera, 21/11), donc leur refus
     * direct reste immédiat comme avant.
     */
    private val unusablePreferenceApps = HashSet<String>()

    private fun coolingDown(packageName: String, a: ScreenAnalysis, now: Long): Boolean {
        val cd = cooldowns[packageName]
        if (cd != null && now < cd.first && (ANY_WINDOW in cd.second || windowKey(packageName, a) in cd.second)) return true
        val short = shortCooldowns[packageName]
        return short != null && now < short.first && short.second == a.lightFingerprint
    }

    /** Dernière analyse (utile pour le diagnostic). */
    var lastAnalysis: ScreenAnalysis? = null
        private set

    /** Dernier écran ressemblant à une fenêtre de consentement (pour le diagnostic). */
    private var lastConsentRoot: UiNode? = null

    val isActive: Boolean get() = session != null

    /**
     * Le moteur demande une lecture COMPLÈTE de l'écran à la prochaine étape (mode prudent de la
     * lecture partielle) : panneau introuvable ou différent dans la lecture partielle, ou décision
     * de fermer la fenêtre (validation, refus) à prendre. Remis à faux par [consumeFullCaptureRequest].
     */
    var fullCaptureRequested: Boolean = false
        private set

    /** Vrai (une fois) si une lecture complète a été demandée depuis le dernier appel. */
    fun consumeFullCaptureRequest(): Boolean = fullCaptureRequested.also { fullCaptureRequested = false }

    /** L'écran en cours d'analyse est une lecture partielle. */
    private var analyzingPartial = false
    private var partialMethodBefore: RefusalMethod? = null
    private var partialNoteBefore: String? = null

    fun reset() {
        session = null
        lastConsentRoot = null
        fullCaptureRequested = false
    }

    /** Enregistre le résultat du pilote après une étape d'action. */
    fun recordExecution(stats: ActionExecutionStats) {
        val s = session ?: return
        s.actionAttempts += stats.requested
        s.actionExecutions += stats.executed
        s.blockedActions += stats.blocked
        // Défilement continu : l'exécuteur a MESURÉ que la liste ne bouge plus et qu'Android ne propose
        // plus de défiler. Inutile d'attendre trois captures identiques (≈ 1 s) pour le constater.
        if (stats.scrollAtEnd && s.lastOp is PendingOp.Scroll) {
            s.scrollEnded = true
            s.lastOp = null
            s.waits = 0
        }
    }

    /** Clé du délai d'attente : la fenêtre ; dans un navigateur, son contenu (un site n'est pas un autre). */
    private fun windowKey(packageName: String, a: ScreenAnalysis): Int =
        if (BrowserPolicy.isBrowser(packageName)) a.contentSignature else a.lightFingerprint

    private fun recognizedControlCount(a: ScreenAnalysis): Int =
        a.buttons.size + a.toggleStates.size + a.expandables.size

    /** Analyse seule (détection), sans démarrer de session. */
    fun analyze(root: UiNode, packageName: String = ""): ScreenAnalysis =
        analyzer.analyze(root, webContentOnly = BrowserPolicy.isBrowser(packageName))

    /**
     * Avancement d'une longue liste d'interrupteurs : (choix traités, total connu). Null sans session ou
     * pour une liste courte. Le total grandit si de nouvelles lignes apparaissent au défilement.
     * Sert seulement à l'affichage : « traité » veut dire cliqué, pas vérifié.
     */
    fun progress(): Pair<Int, Int>? {
        val s = session ?: return null
        val total = maxOf(s.firstState.values.count { it == true }, s.toggleAttempts.size)
        if (total < PROGRESS_MIN_TOGGLES) return null
        return s.toggleAttempts.size to total
    }

    /** Signale qu'un défilement n'a pas pu être exécuté : le bas de la liste reste inconnu. */
    fun markNavigationBlocked() {
        session?.navigationBlocked = true
    }

    /**
     * Arrêt d'urgence (ou intervention de l'utilisateur) : termine la session en cours comme
     * « interrompue » et n'agit plus sur cette fenêtre pendant le délai de grace d'échec.
     */
    fun abort(reason: String): Outcome? {
        val s = session ?: return null
        val a = lastAnalysis ?: return null
        val outcome = (finish(s, a, success = false, reason = reason, diagnostic = false, interrupted = true) as Step.Done).outcome
        // Aucune nouvelle action sur cette appli pendant le délai de grâce, quelle que soit la page affichée.
        cooldowns[s.packageName] = (clock() + limits.failureCooldownMs) to setOf(ANY_WINDOW)
        return outcome
    }

    /**
     * @param policy profil à appliquer à cette fenêtre, décidé au début de la session à partir de
     * l'analyse (plateforme, confiance, empreinte) : STRICT, PRUDENT ou OBSERVATION.
     */
    fun next(screen: Screen, policy: (ScreenAnalysis) -> Policy = { Policy.STRICT }): Step {
        val now = clock()
        var a = analyzer.analyze(screen.root, webContentOnly = BrowserPolicy.isBrowser(screen.packageName))
        // Suite de session dans un navigateur (Firefox 16 h 44, Opera 16 h 46, 22/11) : après un défilement ou
        // un clic qui ne ferme rien, le titre de la fenêtre a pu sortir de l'écran. Relecture avec l'identifiant
        // de la plateforme reconnue au départ (voir ScreenAnalyzer.analyze, « continuing ») avant de conclure
        // que la fenêtre s'est fermée. Seulement pour une plateforme identifiée, et la même.
        session?.let { cur ->
            if (!a.isConsent && cur.closingClicks == 0 && cur.packageName == screen.packageName &&
                BrowserPolicy.isBrowser(screen.packageName) &&
                cur.platform != PLATFORM_UNKNOWN && cur.platform != PLATFORM_GENERIC_TCF
            ) {
                val c = analyzer.analyze(screen.root, webContentOnly = true, continuing = true)
                if (c.isConsent && c.platform == cur.platform) a = c
            }
        }
        // Lecture partielle (mode prudent) : elle ne sert que si elle redonne EXACTEMENT le même
        // panneau, lu en entier, dans une session en cours. Sinon : rien n'est décidé ni noté, et une
        // lecture complète est demandée pour l'étape suivante.
        analyzingPartial = screen.isPartial
        if (screen.isPartial) {
            val usable = session != null && !screen.isTruncated && a.isConsent && a.scopeComplete &&
                a.panelRoot?.key == screen.partialTopKey
            if (!usable) {
                fullCaptureRequested = true
                return Step.Wait(0)
            }
        }
        lastAnalysis = a
        partialMethodBefore = session?.method
        partialNoteBefore = session?.explorationNote
        // Capture arrêtée en route, mais hors du panneau de consentement (reste de la page) : le
        // panneau a été lu en entier, la fenêtre se traite normalement.
        // Si l'endroit de l'arrêt n'est pas connu, toute la fenêtre est considérée incomplète.
        val truncated = screen.isTruncated &&
            (!a.scopeComplete || screen.root.walk().none { it.childrenOmitted })

        var s = session
        s?.let {
            if (a.isConsent) it.seenKeys.add(windowKey(screen.packageName, a))
            it.controlsRecognized = maxOf(it.controlsRecognized, recognizedControlCount(a))
            it.captureTruncated = it.captureTruncated || truncated
        }
        if (s != null && s.packageName != screen.packageName) {
            // L'utilisateur a changé d'appli : on abandonne proprement.
            return finish(s, a, success = false, reason = "fenêtre quittée avant la fin", diagnostic = false)
        }
        if (truncated) {
            // Une capture tronquée n'est pas, à elle seule, la preuve d'une fenêtre de consentement.
            // Ne pas journaliser les écrans ordinaires (jeux, réglages, etc.) contenant des mots-clés.
            if (!a.isConsent || (s == null && !a.canStartSession)) {
                if (s == null) return Step.Idle
                if (s.truncatedDirectClick) {
                    // « Tout refuser » a été cliqué et la fenêtre a disparu : même vérification de
                    // disparition qu'en temps normal, puis résultat « non vérifiable ».
                    if (s.goneChecks < GONE_CHECKS) {
                        s.goneChecks++
                        return Step.Wait(limits.waitAfterClickMs)
                    }
                    return finish(s, a, true, null)
                }
                return finish(s, a, success = false, reason = "fenêtre de consentement quittée pendant une capture incomplète", diagnostic = false, interrupted = true)
            }

            val reason = "capture incomplète : limite de nœuds ou de profondeur atteinte ; aucune action exécutée"
            if (s == null) {
                if (coolingDown(screen.packageName, a, now)) return Step.Idle

                // Respecter les profils même lorsque la capture est tronquée : OBSERVATION ne doit
                // jamais produire un refus partiel, et PRUDENT exige toujours une confiance certaine.
                val p = policy(a)
                if (p == Policy.OBSERVATION || (p == Policy.PRUDENT && a.confidence != Confidence.CERTAIN)) {
                    cooldowns[screen.packageName] = (now + limits.failureCooldownMs) to setOf(windowKey(screen.packageName, a))
                    val observedReason = if (p == Policy.OBSERVATION) {
                        "mode observation : aucune action (capture incomplète)"
                    } else {
                        "reconnaissance ${a.confidence.label} : aucune action en mode prudent (capture incomplète)"
                    }
                    return Step.Done(
                        Outcome(
                            packageName = screen.packageName, success = false, method = null, togglesOff = 0,
                            actions = 0, reason = observedReason, togglesSeen = a.toggleStates.size,
                            togglesUnknown = a.unknownStateToggles, diagnosticRoot = screen.root,
                            status = OutcomeStatus.OBSERVED, platform = a.platform, confidence = a.confidence,
                            structureSignature = a.structureSignature,
                            consentDetected = true,
                            controlsRecognized = recognizedControlCount(a),
                            captureTruncated = true,
                        ),
                    )
                }
                s = Session(screen.packageName, now, windowKey(screen.packageName, a), a.platform, a.confidence, a.structureSignature, a.lightFingerprint)
                s.controlsRecognized = recognizedControlCount(a)
                s.captureTruncated = truncated
                session = s
            }
            lastConsentRoot = screen.root
            if (s.truncatedDirectClick) {
                // La fenêtre est toujours là après le clic : on laisse le temps à l'écran de réagir,
                // puis on s'arrête sans rien tenter d'autre sur une fenêtre lue en partie.
                if (a.fingerprint == s.lastFingerprint && s.waits < limits.maxWaits) {
                    s.waits++
                    return Step.Wait(limits.waitAfterClickMs)
                }
                return finish(s, a, success = false, reason = reason, incompleteCapture = true)
            }
            // Fenêtre trop grande pour être lue en entier (longues listes de fournisseurs) : seul un
            // refus DIRECT visible dans la partie lue est autorisé. Jamais d'options, d'interrupteurs
            // ni de validation sur une fenêtre incomplète ; le résultat restera « non vérifiable ».
            if (s.actions == 0 && s.lastOp == null && !s.optionsOpened) {
                pick(s, a, ButtonKind.REFUSE)?.let { b ->
                    s.method = RefusalMethod.DIRECT
                    s.truncatedDirectClick = true
                    return click(s, a, b, closing = true, "refus direct (capture incomplète) : « ${b.phrase} »")
                }
            }
            // Liste trop longue, mais avec un choix maître visible (« Tous les partenaires › Bloquer ») :
            // il règle toute la liste, y compris la partie non lue. Le sélectionner s'il ne l'est pas,
            // puis enregistrer. Le résultat sera « refus partiel » (la liste n'est pas relue en entier).
            val master = a.buttons(ButtonKind.RADIO_REFUSE).firstOrNull {
                it.isMaster && it.labelNode.isVisibleToUser && it.target != null && it.blocked == null
            }
            if (master != null && !s.truncatedDirectClick) {
                if (master.selected != true && master.key !in s.doneKeys) {
                    s.method = s.method ?: RefusalMethod.OPTIONS
                    return click(s, a, master, closing = false, "choix maître (liste trop longue) : « ${master.label} »")
                }
                // Audit du 21/11 (n° 2 d) : jamais de validation si un « Autoriser » est vu sélectionné ou si
                // un interrupteur visible est encore activé : le site enregistrerait une acceptation.
                val acceptSelected = a.buttons(ButtonKind.ACCEPT).any { it.isRadio && it.selected == true && it.labelNode.isVisibleToUser }
                val visibleOn = a.checkedToggles.any { it.node.isVisibleToUser }
                // Relecture sécurité : « Autoriser Google Advertising Products » (plus de 2 mots ajoutés)
                // n'est pas reconnu comme acceptation ; son « Bloquer » non sélectionné, si.
                val subordinateNotBlocked = a.buttons(ButtonKind.RADIO_REFUSE).any {
                    !it.isMaster && it.selected != true && it.labelNode.isVisibleToUser
                }
                if (master.selected == true && (acceptSelected || visibleOn || subordinateNotBlocked || unknownRadioChecked(a))) {
                    return finish(
                        s, a, success = false,
                        reason = "liste trop longue : un choix encore accepté est visible ; validation laissée à l'utilisateur",
                        handedOver = true,
                    )
                }
                if (master.selected == true) {
                    pick(s, a, ButtonKind.CONFIRM)?.let { b ->
                        s.method = s.method ?: RefusalMethod.OPTIONS
                        s.truncatedDirectClick = true
                        return click(s, a, b, closing = true, "validation (liste trop longue) : « ${b.phrase} »")
                    }
                }
            }
            // Sous-page ouverte par CYRAVA (liste de 300 partenaires) trop longue pour être lue : revenir
            // une fois à la page précédente, lisible en entier, et y terminer sans rouvrir cette liste.
            // Le résultat restera « partiel » : les choix de cette liste n'ont pas été traités.
            if (s.visitedTabs.isNotEmpty() && !s.wentBack) {
                pick(s, a, ButtonKind.BACK)?.let { b ->
                    s.wentBack = true
                    s.skippedNote = "liste des partenaires trop longue pour être lue en entier : non traitée"
                    s.newPage()
                    return click(s, a, b, closing = false, "retour : liste trop longue « ${b.phrase} »")
                }
            }
            return finish(s, a, success = false, reason = reason, incompleteCapture = true)
        }

        if (s == null) {
            if (!a.canStartSession) return Step.Idle
            if (coolingDown(screen.packageName, a, now)) return Step.Idle
            val p = policy(a)
            if (p == Policy.OBSERVATION || (p == Policy.PRUDENT && a.confidence != Confidence.CERTAIN)) {
                // Aucune action : on journalise ce qui a été vu, une fois par fenêtre.
                cooldowns[screen.packageName] = (now + limits.failureCooldownMs) to setOf(windowKey(screen.packageName, a))
                val reason = if (p == Policy.OBSERVATION) {
                    "mode observation : aucune action"
                } else {
                    "reconnaissance ${a.confidence.label} : aucune action en mode prudent"
                }
                return Step.Done(
                    Outcome(
                        packageName = screen.packageName, success = false, method = null, togglesOff = 0,
                        actions = 0, reason = reason, togglesSeen = a.toggleStates.size,
                        togglesUnknown = a.unknownStateToggles, diagnosticRoot = screen.root,
                        status = OutcomeStatus.OBSERVED, platform = a.platform, confidence = a.confidence,
                        structureSignature = a.structureSignature,
                        consentDetected = true,
                        controlsRecognized = recognizedControlCount(a),
                        captureTruncated = truncated,
                    ),
                )
            }
            s = Session(screen.packageName, now, windowKey(screen.packageName, a), a.platform, a.confidence, a.structureSignature, a.lightFingerprint)
            s.controlsRecognized = recognizedControlCount(a)
            s.captureTruncated = truncated
            session = s
        }
        // Audit I4 : une page de choix radio Didomi défilée reste une fenêtre de cookies.
        // 21/11 au soir : la fenêtre Google UMP fermée à la main, la page AlternativeTo derrière (mots
        // « consent », « cookie » et deux boutons « J'aime » cochables) passait encore pour la même
        // fenêtre : CYRAVA continuait sur la page, concluait « bouton de validation introuvable » et
        // le diagnostic montrait la page au lieu de la fenêtre. De simples interrupteurs ne prolongent
        // donc la session que si la plateforme reconnue au départ est toujours là.
        // « TCF générique » reste compatible : une sous-page de la même fenêtre (liste des partenaires)
        // n'affiche pas toujours les repères de sa plateforme, mais parle de partenaires ou d'intérêt légitime.
        val samePlatform = s.platform == PLATFORM_UNKNOWN || s.platform == PLATFORM_GENERIC_TCF ||
            a.platform == s.platform || a.platform == PLATFORM_GENERIC_TCF
        val consentLike = a.isConsent || (a.hints.isNotEmpty() &&
            (a.buttons(ButtonKind.RADIO_REFUSE).isNotEmpty() || (a.toggleStates.isNotEmpty() && samePlatform)))
        if (consentLike) lastConsentRoot = screen.root

        // Filet de sécurité absolu (attentes comprises).
        if (now - s.startedAt > limits.timeoutMs + HARD_TIMEOUT_GRACE_MS) {
            return finish(s, a, false, "délai de ${limits.timeoutMs / 1000} s dépassé")
        }

        // L'écran a-t-il réagi à la dernière opération ?
        val last = s.lastOp
        val changed = a.fingerprint != s.lastFingerprint
        if (changed) s.lastProgressAt = now
        // Plus rien ne bouge depuis longtemps : inutile d'attendre la fin du délai total (21/11 au soir).
        if (now - s.lastProgressAt > STALL_MS) {
            return finish(s, a, false, "plus rien ne bougeait à l'écran depuis ${STALL_MS / 1000} s")
        }
        if (last != null && !changed) {
            if (s.waits < limits.maxWaits) {
                s.waits++
                return Step.Wait(limits.waitAfterClickMs)
            }
            when (last) {
                is PendingOp.Button -> s.noEffect.merge(last.key, 1, Int::plus)
                // Écran inchangé après un défilement : c'est la fin de la liste, SAUF si l'exécuteur
                // a déclaré le défilement bloqué (markNavigationBlocked : action acceptée sans
                // déplacement alors qu'Android propose encore de défiler, ou défilement impossible).
                // Le cas « bloqué » empêche toute validation (voir plus bas).
                is PendingOp.Scroll -> s.scrollEnded = true
                // Sans effet, l'apparition du bouton ne prouve rien sur la fin de la liste.
                PendingOp.Toggles, PendingOp.Expand, PendingOp.Reveal -> Unit
            }
        } else if (last is PendingOp.Button) {
            s.doneKeys.add(last.key)
            if (last.closing) {
                s.closingClicks++
                // Après un refus/une validation, on n'agit plus que sur une VRAIE fenêtre de
                // consentement : jamais sur l'interface de l'appli qui réapparaît derrière.
                if (!a.isConsent) s.closingPending = true
            }
        } else if (last is PendingOp.Scroll) {
            s.scrolls++
        }
        s.lastOp = null
        s.waits = 0
        if (s.closingPending && a.isConsent) s.closingPending = false

        // Pied de fenêtre sous l'écran (RTBF dans Firefox, 27/11) : un bouton dont la position n'a pas bougé
        // après un défilement ou « faire apparaître » est dans une fenêtre fixe. Aucun défilement ne
        // l'amènera à l'écran : il pourra être cliqué par son nœud (voir pickStuck).
        noteFooterPositions(s, a)

        // Suivi des interrupteurs décochés.
        s.seenToggles.addAll(a.toggleStates.keys)
        // Dernier état connu de CHAQUE interrupteur vu, même défilé hors de l'écran depuis.
        s.lastToggleStates.putAll(a.toggleStates)
        // Libellé lisible de chaque interrupteur vu (pour nommer les non vérifiés dans le diagnostic).
        s.toggleLabels.putAll(a.toggleLabelsBySignature)
        s.keptOn.addAll(a.keptOnToggles)
        s.unknownToggleSignatures.addAll(a.unknownToggleSignatures)
        for ((sig, isChecked) in a.toggleStates) {
            val stateUnknown = sig in a.unknownToggleSignatures || isChecked == null
            if (sig !in s.firstState) s.firstState[sig] = if (stateUnknown) null else isChecked
            if (!stateUnknown) {
                // Une valeur devenue explicitement lisible sur une capture ultérieure résout
                // l'incertitude précédente pour cette même ligne (sans effacer les inconnues d'autres pages).
                s.unknownToggleSignatures.remove(sig)
            }
            if (sig in s.toggleAttempts) {
                when {
                    stateUnknown -> {
                        s.stillChecked.remove(sig)
                        s.verifiedOff.remove(sig)
                    }
                    isChecked -> {
                        s.stillChecked.add(sig)
                        s.verifiedOff.remove(sig)
                    }
                    else -> {
                        s.stillChecked.remove(sig)
                        s.verifiedOff.add(sig)
                    }
                }
            }
        }

        // Les boutons radio « Bloquer » doivent être relus sélectionnés après le clic.
        // Une radio non sélectionnée ou d’état inconnu reste en attente et interdit la validation.
        // Refonte du 21/11 : on se fie aux contrôles GLOBAUX. Si le choix maître (« Tous les
        // partenaires › Bloquer ») est vu sélectionné, les « Bloquer » un par un de cette page ne
        // sont ni exigés ni cliqués. Mais un « Autoriser » vu SÉLECTIONNÉ n'est jamais ignoré, même
        // sous un choix maître et même hors de l'écran : deux signaux contraires = pas de « vérifié »
        // (relecture sécurité du 21/11). L'état de TOUS les choix lus compte ; seuls les visibles
        // sont cliqués.
        val masterRadioRefused = masterRadioRefused(a)
        if (masterRadioRefused) {
            val subordinate = a.buttons.filter { it.isRadio && !it.isMaster && it.kind == ButtonKind.RADIO_REFUSE }
                .mapTo(HashSet()) { it.key }
            s.radioUnverified.removeAll(subordinate)
            s.radioUnknown.removeAll(subordinate)
        }
        for (radio in a.buttons.filter { it.isRadio && (it.isMaster || it.kind != ButtonKind.RADIO_REFUSE || !masterRadioRefused) }) {
            when (radio.kind) {
                ButtonKind.RADIO_REFUSE -> {
                    s.radioSeen.add(radio.key)
                    when (radio.selected) {
                        true -> { s.radioUnverified.remove(radio.key); s.radioUnknown.remove(radio.key) }
                        false -> { s.radioUnverified.add(radio.key); s.radioUnknown.remove(radio.key) }
                        null -> s.radioUnknown.add(radio.key)
                    }
                }
                ButtonKind.ACCEPT -> when (radio.selected) {
                    true -> { s.radioAcceptSelected.add(radio.key); s.radioUnknown.remove(radio.key) }
                    false -> { s.radioAcceptSelected.remove(radio.key); s.radioUnknown.remove(radio.key) }
                    null -> s.radioUnknown.add(radio.key)
                }
                else -> Unit
            }
        }

        // Un défilement bloqué signifie que des fournisseurs peuvent rester invisibles.
        // Ne jamais valider ni annoncer un refus complet dans ce cas.
        // Refus global déjà cliqué après un défilement impossible : on vérifie seulement que la fenêtre se ferme.
        val blockedButClosing = s.navigationBlocked && s.closingClicks > 0 && (!consentLike || s.closingPending)
        // Galaxy Defense (22/11, 17 h 20, diagnostic) : la liste ne bouge pas, mais la WebView présente TOUTES
        // ses lignes (367 interrupteurs, ceux sous l'écran avec une hauteur nulle), chacune avec son état. Rien
        // n'est hors de la capture : la fin de la liste est lue sans défiler. Les interrupteurs encore activés
        // sont décochés par leur nœud, relus, et la validation garde toutes ses conditions.
        if (s.navigationBlocked && !blockedButClosing && s.closingClicks == 0 && listFullyExposed(s, a, truncated)) {
            s.navigationBlocked = false
            s.scrollEnded = true
            s.listFullyExposed = true
        }
        // Galaxy Defense (22/11, 17 h 58, ci.11) : la même liste ENTIÈREMENT présente dans la capture, mais
        // qui, cette fois, bougeait. CYRAVA la parcourait écran par écran (3,8 s par défilement), n'arrivait
        // pas en bas dans le délai et concluait « liste pas lue jusqu'en bas ». Les mêmes conditions strictes
        // (capture complète, page web, lignes sous l'écran présentes, état de TOUS les interrupteurs connu)
        // suffisent : la fin de la liste est lue sans défiler, qu'elle bouge ou non.
        // Exception : un interrupteur cliqué hors de l'écran et relu encore activé (Chrome ignore parfois ces
        // clics) doit être amené à l'écran pour son second essai : le défilement reste alors permis.
        val needsVisibleRetry = a.checkedToggles.any { it.signature in s.offscreenTried && it.signature !in s.offscreenRetried }
        // Relecture sécurité du 23/11 : une liste chargée au fur et à mesure ne met dans la capture que quelques
        // lignes d'avance sous l'écran ; la croire complète ferait valider et annoncer « vérifié » sans avoir vu
        // le reste. Deux preuves sont donc exigées pour une liste qui défile : (1) les lignes sous l'écran sont
        // presque toutes de hauteur nulle (Galaxy Defense : 364 sur 365), et (2) la dernière ligne, amenée à
        // l'écran une fois, ne fait apparaître AUCUNE ligne nouvelle. Sinon : défilement normal, comme avant.
        if (!s.navigationBlocked && s.closingClicks == 0) {
            val exposed = !s.scrollEnded && !needsVisibleRetry && !s.exposureRejected &&
                listFullyExposed(s, a, truncated, requireCollapsedRows = true)
            if (exposed) {
                val keys = a.toggleStates.keys.toSet()
                val probe = s.exposureProbe
                if (probe == null) {
                    val last = a.scrollTarget?.walk()?.lastOrNull { it.isCheckable && !it.isVisibleToUser }
                    if (last != null) {
                        s.exposureProbe = keys
                        return act(
                            s, a, listOf(Op.ShowOnScreen(last, "fin de la liste")), PendingOp.Reveal,
                            "vérification de la fin de la liste", countsAsAction = false,
                        )
                    }
                } else if (keys == probe) {
                    s.scrollEnded = true
                    s.listFullyExposed = true
                    s.endFromExposure = true
                } else {
                    // Des lignes nouvelles sont apparues : la liste se charge au fur et à mesure.
                    s.exposureRejected = true
                }
            } else if (s.exposureProbe != null && !s.endFromExposure && !s.exposureRejected &&
                a.toggleStates.keys.toSet() != s.exposureProbe
            ) {
                s.exposureRejected = true
            } else if (s.endFromExposure && needsVisibleRetry) {
                s.scrollEnded = false
                s.endFromExposure = false
            }
        }
        if (s.navigationBlocked && !blockedButClosing) {
            // Repli (21/11 au soir) : un « Tout refuser » visible
            // règle aussi la partie non lue de la liste. Jamais de validation dans ce cas.
            if (s.closingClicks == 0 && s.actions < limits.maxActions) {
                val global = pick(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }
                    ?: pickHidden(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }
                    ?: pickStuck(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }.takeIf { s.refusalRevealed }
                if (global != null) {
                    // Jamais « vérifié » : la fin de la liste n'a pas été lue (relecture sécurité, 21/11 au soir).
                    s.explorationNote = s.explorationNote ?: "défilement impossible : refus global utilisé, la fin de la liste n'a pas été lue"
                    s.method = s.method ?: RefusalMethod.DIRECT
                    return click(s, a, global, closing = true, "refus (défilement impossible) : « ${global.phrase} »")
                }
                // « Tout refuser » présent, mais sous l'écran (pied de la fenêtre Didomi dans Chrome, 22/11 au
                // soir) : l'amener à l'écran d'un coup (un défilement d'accessibilité, pas un clic), une fois.
                // Il sera cliqué à l'étape suivante, avec les mêmes contrôles qu'un bouton visible.
                if (!s.refusalRevealed) {
                    a.buttons(ButtonKind.REFUSE).firstOrNull {
                        isGlobalRefusal(it) && it.target == null && it.blocked == null &&
                            !it.labelNode.isVisibleToUser && it.labelNode.isEnabled
                    }?.let { hidden ->
                        s.refusalRevealed = true
                        return act(
                            s, a, listOf(Op.ShowOnScreen(hidden.labelNode, hidden.label)), PendingOp.Reveal,
                            "aller au bouton de refus", countsAsAction = false,
                        )
                    }
                }
            }
            return finish(
                s, a, success = false,
                reason = "défilement impossible : certains interrupteurs peuvent être restés hors de la capture",
                incompleteCapture = true,
            )
        }

        // La fenêtre a-t-elle disparu ?
        if (!consentLike || s.closingPending) {
            // Aucun refus ni validation cliqué : la fenêtre ne devrait pas avoir disparu. Une page qui se
            // recharge (centre de préférences Sourcepoint ouvert dans un nouveau cadre, Opera 22/11 à 16 h 46)
            // peut rester vide plus de 0,75 s : on lui laisse environ 2 s avant de conclure.
            val goneLimit = if (s.closingClicks == 0) GONE_CHECKS_NO_CLOSE else GONE_CHECKS
            if (s.goneChecks < goneLimit) {
                s.goneChecks++
                return Step.Wait(limits.waitAfterClickMs)
            }
            return if (s.closingClicks > 0) {
                finish(s, a, true, null)
            } else {
                // Reste « interrompue », mais garde la dernière fenêtre vue : sans elle, impossible de
                // comprendre pourquoi la fenêtre a disparu (cas Axeptio du 18/11).
                // Préférences tentées sur une plateforme inconnue (hors navigateur) et fenêtre perdue :
                // ne plus mettre le « Tout refuser » de CETTE APPLI en attente des préférences (jusqu'au
                // redémarrage du service) : le refus direct sera utilisé dès la prochaine fenêtre.
                if (s.optionsOpened && s.platform == PLATFORM_UNKNOWN && !BrowserPolicy.isBrowser(s.packageName)) {
                    unusablePreferenceApps.add(s.packageName)
                }
                finish(s, a, false, "la fenêtre s'est fermée sans validation", diagnostic = true, interrupted = true)
            }
        }
        s.goneChecks = 0

        if (now - s.startedAt > limits.timeoutMs) {
            return finish(s, a, false, "délai de ${limits.timeoutMs / 1000} s dépassé")
        }
        if (s.actions >= limits.maxActions) {
            return finish(s, a, false, "limite de ${limits.maxActions} actions atteinte")
        }

        return decide(s, a, now) ?: finish(s, a, false, failureReason(s, a))
    }

    private fun decide(s: Session, a: ScreenAnalysis, now: Long): Step? {
        val remaining = limits.maxActions - s.actions
        val elapsed = now - s.startedAt
        val outOfActions = remaining <= 2
        val outOfTime = elapsed >= limits.timeoutMs * 8 / 10
        val closingOnly = outOfActions || outOfTime
        val canExplore = remaining > 6 && elapsed < limits.timeoutMs * 6 / 10
        // Défilement continu (21/11) : défiler pour lire la suite de la liste reste permis jusqu'à la fin
        // du budget (pas seulement les 60 premiers %), sinon la validation arrivait sur une liste lue à moitié.
        val canScroll = !closingOnly && remaining > 3
        s.budgetNote = when {
            outOfActions -> "limite de ${limits.maxActions} actions atteinte avant la validation"
            outOfTime -> "délai de ${limits.timeoutMs / 1000} s presque écoulé avant la validation"
            else -> s.skippedNote
        }

        // a. Refus direct. Un refus global (identifiant documenté ou formule d'au moins deux mots :
        // « Tout refuser ») passe avant un « Refuser » seul, qui peut ne viser qu'une finalité ; et si
        // un refus global attend plus bas, on n'actionne pas un « Refuser » seul en attendant.
        // « Confirmer votre refus » (TF1+, 20/11) termine un refus déjà commencé : il passe en premier.
        val confirmRefusal = pick(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) && it.phrase.startsWith("confirmer ") }
        // « Refuser tout » écrasé sous la barre d'Opera (22/11, 16 h 47) : même rôle qu'un bouton visible.
        val globalRefusal = pick(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }
            ?: pickHidden(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }
            ?: pickStuck(s, a, ButtonKind.REFUSE) { isGlobalRefusal(it) }
        // Refus complet : tant qu'il reste du travail dans les préférences, le refus direct attend
        // (il sert de repli à la fin). « Confirmer votre refus » n'attend jamais.
        // Plateforme inconnue sur le premier écran : un refus GLOBAL (« Tout refuser ») n'attend pas,
        // SAUF hors navigateur (option « préférences d'abord », 26/11) : dans une appli, ouvrir les
        // options d'une fenêtre inconnue fait rarement perdre la fenêtre, et le parcours préférences
        // est le seul VÉRIFIABLE (les interrupteurs sont relus). Si la fenêtre se perd quand même,
        // l'appli est mémorisée dans unusablePreferenceApps et le refus direct redevient immédiat.
        // Dans un navigateur, rien ne change : ses « options » peuvent ouvrir une autre page (Google
        // « Plus d'options », fenêtre d'Opera, 21/11) : la fenêtre disparaît, plus aucun repli possible.
        // Un « Refuser » seul (peut-être une seule finalité) attend toujours les préférences.
        val workLeft = completeRefusal && !closingOnly && preferenceWorkLeft(s, a, canExplore, canScroll)
        // Refus complet sur toute plateforme reconnue : l'intérêt légitime n'est souvent décoché que dans
        // les préférences. Si elles sont illisibles, le « Tout refuser » de la page des préférences sert de
        // repli (OneTrust : identifiant ot-pc-refuse-all-handler, ajouté le 21/11 au soir).
        val preferencesFirst = a.platform == PLATFORM_UNKNOWN && !BrowserPolicy.isBrowser(s.packageName) &&
            s.packageName !in unusablePreferenceApps
        val preferencesInPlace = s.optionsOpened || a.platform != PLATFORM_UNKNOWN || preferencesFirst
        // Préférences déjà jugées inutilisables : sur cette plateforme (depuis le démarrage du service), ou
        // dans cette session après un retour à la première page (Consentmanager dans Firefox, 22/11).
        val preferencesUsable = !s.leftPreferences && s.platform !in unusablePreferences
        val deferDirect = workLeft && preferencesInPlace && preferencesUsable
        if (deferDirect && globalRefusal != null && !s.optionsOpened) s.deferredRefusalSeen = true
        // Préférences ouvertes par CYRAVA (BFM, 27/11 à 12 h 04) : leur « Tout refuser » fermait la fenêtre
        // avant les choix un par un, donc rien n'était vérifié, et le résultat affichait « refus partiel »
        // avec un motif trompeur. Ordre inversé : chaque choix encore accepté d'abord, puis la validation ;
        // ce « Tout refuser » est gardé en réserve (dernier recours, voir directFallback et la fin de decide).
        val itemsFirst = s.optionsOpened && !closingOnly && confirmRefusal == null && itemWorkLeft(s, a)
        if (itemsFirst && globalRefusal != null) s.prefsRefusalReserved = true
        val reserved = itemsFirst || s.prefsRefusalReserved
        val strongRefusal = confirmRefusal ?: globalRefusal.takeIf { !deferDirect && !reserved }
        // Lignes « Refuser | Accepter » (préférences Didomi en simples boutons) : chaque « Refuser »
        // est un choix, pas un bouton qui ferme la fenêtre. Traités plus bas (c2 ter), puis validés.
        val choiceRows = pairedChoiceRows(a) >= 2
        val weakRefusal = if (workLeft || reserved || a.offscreenRefusal || wantsGlobalSearch(s, a) || choiceRows) {
            null
        } else {
            // « Vous autorisez › Refuser » règle toutes les finalités : avant les « Refuser » un par un.
            // Un « Non » (paire « Non | Oui ») ne ferme jamais une fenêtre au premier écran (relecture du 27/11).
            pick(s, a, ButtonKind.REFUSE) { it.isMaster } ?: pick(s, a, ButtonKind.REFUSE) { it.phrase != "non" || s.optionsOpened }
        }
        (strongRefusal ?: weakRefusal)?.let { b ->
            if (s.method == null) s.method = RefusalMethod.DIRECT
            return click(s, a, b, closing = true, "refus : « ${b.phrase} »")
        }

        // b. Ouverture des options (premier niveau uniquement).
        if (!s.optionsOpened && !closingOnly) {
            pick(s, a, ButtonKind.OPTIONS)?.let { b ->
                s.optionsOpened = true
                s.method = RefusalMethod.OPTIONS
                s.newPage()
                return click(s, a, b, closing = false, "options : « ${b.phrase} »")
            }
        }

        // b bis. Un « Tout refuser » sûr existe plus bas dans la fenêtre : y aller directement,
        // avant tout autre clic (choix un par un, sections dépliées, liste des partenaires).
        if (!closingOnly && !deferDirect && !reserved && (a.offscreenRefusal || wantsGlobalSearch(s, a)) && s.offscreenScrolls < MAX_OFFSCREEN_SCROLLS &&
            a.buttons(ButtonKind.REFUSE).none { it.target != null && isGlobalRefusal(it) } &&
            a.buttons(ButtonKind.GLOBAL_REJECT).none { it.target != null }
        ) {
            s.offscreenScrolls++
            val target = a.scrollTarget
            return if (target != null) {
                scrollAct(s, a, Op.Scroll(target), "défilement vers « Tout refuser »")
            } else {
                s.swipedWithoutContainer = true
                scrollAct(s, a, Op.Swipe(swipeArea(a), a.root), "balayage vers « Tout refuser »")
            }
        }

        if (!closingOnly) {
            // c1. Boutons globaux (« Tout désactiver », « S'opposer à tout »…). Un « Tout refuser » (aussi
            // formule de refus de la fenêtre) gardé en réserve derrière les choix un par un attend (27/11).
            pick(s, a, ButtonKind.GLOBAL_REJECT) { !reserved || it.phrase !in rules.refuse }?.let { b ->
                s.method = s.method ?: RefusalMethod.OPTIONS
                return click(s, a, b, closing = true, "global : « ${b.phrase} »")
            }

            // c2. Sélectionner « Bloquer » dans chaque choix radio explicite, jamais « Autoriser ».
            // « Tous les partenaires › Bloquer » d'abord : il règle toute la liste d'un coup.
            (pick(s, a, ButtonKind.RADIO_REFUSE) { it.selected != true && it.isMaster }
                ?: pick(s, a, ButtonKind.RADIO_REFUSE) { it.selected != true }.takeIf { !masterRadioRefused(a) })?.let { b ->
                s.method = s.method ?: RefusalMethod.OPTIONS
                s.refusalControlsActed = true
                return click(s, a, b, closing = false, "choix radio : « ${b.phrase} »")
            }

            // c2 ter. Lignes « Refuser | Accepter » en simples boutons : « Vous autorisez › Refuser »
            // d'abord, puis chaque ligne. L'état n'étant pas publié, le résultat restera « non vérifiable ».
            if (choiceRows) {
                (pick(s, a, ButtonKind.REFUSE) { it.isMaster && it.key !in s.doneKeys }
                    ?: pick(s, a, ButtonKind.REFUSE) { it.key !in s.doneKeys && inChoiceRow(a, it) })?.let { b ->
                    s.method = s.method ?: RefusalMethod.OPTIONS
                    s.refusalControlsActed = true
                    s.unreadableChoices++
                    return click(s, a, b, closing = false, "choix : « ${b.phrase} »")
                }
            }

            // c2 bis. Décocher tous les interrupteurs cochés (consentement ET intérêt légitime).
            // Les finalités visibles d'abord, puis, en rafale, celles qui sont hors de l'écran mais déjà
            // présentes dans l'arbre avec leur état (Chrome : liste de 480 partenaires, 21/11 au soir).
            // Jamais une section repliée.
            val toggles = actionableToggles(s, a)
            if (toggles.isNotEmpty() && s.toggleClicks < MAX_TOGGLE_CLICKS) {
                val batch = toggles.take(minOf(MAX_TOGGLES_PER_STEP, MAX_TOGGLE_CLICKS - s.toggleClicks))
                s.method = s.method ?: RefusalMethod.OPTIONS
                batch.forEach {
                    // Second et dernier essai d'un interrupteur cliqué hors de l'écran et resté activé.
                    if (it.signature in s.offscreenTried && it.node.isVisibleToUser) s.offscreenRetried.add(it.signature)
                    if (it.offscreen) s.offscreenTried.add(it.signature)
                    s.toggleAttempts.merge(it.signature, 1, Int::plus)
                }
                s.toggleClicks += batch.size
                s.blockedToggles.removeAll(batch.map { it.signature }.toSet())
                val ops = batch.map {
                    Op.Click(
                        it.target!!, it.label.ifEmpty { "interrupteur" }, isToggle = true, toggleNode = it.node,
                        allowTapFallback = !it.offscreen, offscreen = it.offscreen,
                    )
                }
                // Un lot d'interrupteurs n'est pas une « action de navigation » : il est vérifié à
                // l'instantané suivant et plafonné à part (MAX_TOGGLE_CLICKS), ce qui permet de
                // traiter des centaines de fournisseurs dans la limite des 40 actions.
                return act(s, a, ops, PendingOp.Toggles, "${ops.size} interrupteur(s) décoché(s)", countsAsAction = false)
            }
            a.checkedToggles.filter { it.target == null }.forEach { s.blockedToggles.add(it.signature) }

            // Aucune section n'est dépliée (refonte du 21/11) : déplier les accordéons Didomi allongeait
            // la liste à l'infini, cassait le défilement et empêchait la validation. Les choix visibles
            // et les refus globaux suffisent ; une catégorie repliée reste réglée par son refus global.

            // c2 quinquies. Centre de préférences en onglets (OneTrust, 22/11 au soir) : chaque catégorie
            // n'affiche son interrupteur qu'une fois sélectionnée. Plus rien à décocher dans la catégorie
            // affichée : ouvrir la suivante. Un onglet n'enregistre rien ; la validation n'arrive qu'après.
            categoryStep(s, a)?.let { return it }

            // c2 quater. Plus rien à décocher dans toute la liste lue (y compris hors écran) et la
            // validation est tout en bas : la faire apparaître d'un coup plutôt que défiler ligne à ligne.
            // Le défilement normal reprend ensuite pour constater la fin de la liste avant d'enregistrer.
            // Aussi quand la page ne propose que des choix radio, tous déjà sur « Refuser » (Didomi dans
            // Chrome, 22/11 au soir) : sans conteneur défilant, le balayage ne bougeait rien et la session
            // finissait en « défilement impossible », le bouton « Enregistrer » restant sous l'écran.
            val reveal = a.confirmToReveal
            if (reveal != null && !s.confirmRevealed && canScroll && (s.toggleAttempts.isNotEmpty() || radiosAllRefused(a)) &&
                a.checkedToggles.none { (s.toggleAttempts[it.signature] ?: 0) == 0 }
            ) {
                s.confirmRevealed = true
                val label = TextNormalizer.canonical(reveal.subtreeTexts().firstOrNull { it.isNotBlank() }.orEmpty())
                return act(s, a, listOf(Op.ShowOnScreen(reveal, label)), PendingOp.Reveal, "aller au bouton de validation", countsAsAction = false)
            }

            // c3. Défilement continu « façon TikTok / Webtoon » (22/11) : écran après écran, en décochant
            // à chaque fois, jusqu'en bas de la liste. Plus de plafond fixe gênant pour une longue liste :
            // le défilement continue tant que de nouveaux choix apparaissent (voir [noteScrollNovelty]).
            if (canScroll && wantsScroll(s, a)) {
                noteScrollNovelty(s)
                val target = a.scrollTarget
                return if (target != null) {
                    scrollAct(s, a, Op.Scroll(target), "défilement")
                } else {
                    s.swipedWithoutContainer = true
                    scrollAct(s, a, Op.Swipe(swipeArea(a), a.root), "balayage")
                }
            }

            // c4. Onglets / pages fournisseurs et partenaires.
            if (canExplore && s.inSettings(a) && (!a.offscreenRefusal || completeRefusal)) {
                pick(s, a, ButtonKind.VENDOR_TAB) { it.label !in s.visitedTabs }?.let { b ->
                    s.visitedTabs.add(b.label)
                    s.newPage()
                    return click(s, a, b, closing = false, "onglet : « ${b.phrase} »")
                }
            }
        }

        // d. Validation. Une WebView peut masquer les cases ARIA non cochables :
        // sans contrôle reconnu, ne jamais enregistrer un choix que nous n'avons pas pu vérifier.
        // « Enregistrer » écrasé sous la barre d'Opera (22/11, 16 h 47) : mêmes conditions qu'un bouton visible.
        (pick(s, a, ButtonKind.CONFIRM) ?: pickHidden(s, a, ButtonKind.CONFIRM) ?: pickStuck(s, a, ButtonKind.CONFIRM))?.let { b ->
            // Audit 18/11 (C1) : au premier niveau, sans options ouvertes ni refus fait, une validation
            // n'enregistrerait que les choix du site. Avec des interrupteurs visibles, on applique les
            // contrôles des préférences ; sans rien de refusable, aucun clic.
            // Exception : « Tous les partenaires › Bloquer » visible et sélectionné (Le Dauphiné, 20/11) :
            // le refus est déjà en place et observé, la validation l'enregistre.
            val masterRefused = a.buttons(ButtonKind.RADIO_REFUSE).any {
                it.isMaster && it.selected == true && it.labelNode.isVisibleToUser
            }
            // Refus complet : si la validation ne peut pas être faite proprement, repli sur le refus
            // direct du site plutôt qu'un échec.
            val directFallback = if (completeRefusal || s.prefsRefusalReserved) globalRefusal else null
            fun fallbackOr(reason: String, handedOver: Boolean = false): Step {
                directFallback?.let { r ->
                    s.method = s.method ?: RefusalMethod.DIRECT
                    return click(s, a, r, closing = true, "refus (repli) : « ${r.phrase} »")
                }
                backToFirstScreen(s, a)?.let { return it }
                return finish(s, a, success = false, reason = reason, handedOver = handedOver)
            }
            if (s.method == null && !s.refusalControlsActed) {
                if (a.toggleStates.isEmpty() && !masterRefused) {
                    return fallbackOr("seul un bouton de validation est proposé : rien n'a été refusé, aucun clic")
                }
                s.method = RefusalMethod.OPTIONS
            }
            // Relecture sécurité du 21/11 : un interrupteur VISIBLE encore activé, jamais traité, interdit
            // d'enregistrer (le site recevrait un choix accepté que CYRAVA n'a pas refusé).
            val untouchedOn = a.checkedToggles.count { it.node.isVisibleToUser && it.signature !in s.toggleAttempts }
            if (untouchedOn > 0) {
                return fallbackOr("$untouchedOn interrupteur(s) visible(s) encore activé(s) et non traité(s) ; validation bloquée")
            }
            // Relecture sécurité du 21/11 : sous un choix maître, un bouton radio coché qui n'est pas un refus
            // reconnu (« Autoriser » suivi d'un long nom de partenaire) interdit d'enregistrer.
            if (masterRadioRefused(a) && unknownRadioChecked(a)) {
                return fallbackOr("un choix radio coché n'est pas un refus reconnu ; validation bloquée", handedOver = holdWhenUnverifiable)
            }
            if (s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty()) {
                return fallbackOr(
                    "choix radio non sûrs : ${s.radioUnverified.size} « Bloquer » non sélectionné(s), ${s.radioAcceptSelected.size} « Autoriser » encore sélectionné(s), ${s.radioUnknown.size} état(s) inconnu(s) ; validation bloquée",
                )
            }
            // Audit du 21/11 (n° 1) : liste pas lue jusqu'en bas, ou page des partenaires non ouverte faute
            // de temps. Valider enregistrerait des choix jamais vus, peut-être encore acceptés.
            val masterSettlesList = a.toggleStates.isEmpty() && masterRefused
            val unexplored = when {
                (a.scrollTarget != null || s.swipedWithoutContainer) && !s.scrollEnded && !masterSettlesList ->
                    "liste des choix pas lue jusqu'en bas (temps ou défilements épuisés)"
                !canExplore && s.inSettings(a) && pick(s, a, ButtonKind.VENDOR_TAB) { it.label !in s.visitedTabs } != null ->
                    "page des partenaires non ouverte faute de temps"
                unvisitedCategories(s, a).isNotEmpty() || s.skippedCategories.isNotEmpty() ->
                    "catégorie(s) de cookies non ouverte(s)"
                else -> null
            }
            if (unexplored != null) {
                s.explorationNote = unexplored
                // Audit du 22/11 (n° 1) : quel que soit le réglage « Ne pas valider dans le doute », JAMAIS
                // d'enregistrement quand une partie des choix n'a pas été vue (liste pas lue jusqu'en bas,
                // catégorie ou page des partenaires non ouverte) : le site enregistrerait ces choix tels
                // qu'ils sont, peut-être acceptés. Repli : le « Tout refuser » du site s'il est là (il règle
                // aussi la partie non vue ; résultat jamais « vérifié »), sinon aucun clic.
                globalRefusal?.let { r ->
                    s.method = s.method ?: RefusalMethod.DIRECT
                    return click(s, a, r, closing = true, "refus (choix pas tous vus) : « ${r.phrase} »")
                }
                return finish(
                    s, a, success = false,
                    reason = if (holdWhenUnverifiable) "$unexplored : validation laissée à l'utilisateur" else "$unexplored : validation bloquée par prudence",
                    handedOver = holdWhenUnverifiable,
                )
            }
            if (holdWhenUnverifiable) {
                val unconfirmed = s.toggleAttempts.keys.filter { it !in s.verifiedOff && it !in s.stillChecked }
                // Audit du 21/11 (n° 2) : un interrupteur VU activé et jamais traité (sorti de l'écran, grisé,
                // fin du temps) compte aussi, visible ou non.
                val doubtful = (s.unknownToggleSignatures + s.blockedToggles + s.stillChecked + unconfirmed + s.knownOnUntried()).toSet()
                if (doubtful.isNotEmpty()) {
                    return fallbackOr(
                        "${doubtful.size} interrupteur(s) impossibles à lire ou à désactiver : validation laissée à l'utilisateur",
                        handedOver = true,
                    )
                }
            }
            if (s.method == RefusalMethod.OPTIONS && s.unknownToggleSignatures.isNotEmpty() && !s.refusalControlsActed) {
                return fallbackOr(
                    "état de ${s.unknownToggleSignatures.size} interrupteur(s) non communiqué ; validation bloquée par prudence",
                )
            }
            if (s.method == RefusalMethod.OPTIONS && s.seenToggles.isEmpty() &&
                a.toggleStates.isEmpty() && !s.refusalControlsActed && !masterRefused
            ) {
                // Consentmanager dans Firefox (22/11, 15 h 11) : réglages en sous-pages, rien de lisible ici.
                // Sur cette plateforme, le refus du premier écran ne sera plus mis en attente (jusqu'au
                // redémarrage du service) : le « Tout refuser » du site sera utilisé directement.
                if (completeRefusal && s.platform != PLATFORM_UNKNOWN && s.platform != PLATFORM_GENERIC_TCF &&
                    a.categoryTabs.isEmpty()
                ) unusablePreferences.add(s.platform)
                return fallbackOr("aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence")
            }
            s.method = s.method ?: RefusalMethod.OPTIONS
            return click(s, a, b, closing = true, "validation : « ${b.phrase} »")
        }

        // d0. Validation GRISÉE alors qu'il ne reste rien à faire (Didomi dans Opera, 22/11 à 16 h 47 :
        // « Enregistrer » reste grisé tant que chaque finalité n'a pas de choix, et une partie de la liste
        // est cachée sous la barre d'Opera). Avant : plus rien ne bougeait pendant 12 s. Le « Tout refuser »
        // du site, s'il existe, termine (résultat jamais « vérifié ») ; sinon la fenêtre est laissée à
        // l'utilisateur. Jamais d'attente sans fin, jamais de clic sur le bouton grisé.
        val confirms = a.buttons(ButtonKind.CONFIRM)
        if (s.inSettings(a) && confirms.isNotEmpty() && confirms.all(::greyed)) {
            globalRefusal?.let { r ->
                // Refus du site, pas nos choix enregistrés : jamais « vérifié » (méthode directe).
                s.method = RefusalMethod.DIRECT
                return click(s, a, r, closing = true, "refus (validation grisée) : « ${r.phrase} »")
            }
            backToFirstScreen(s, a)?.let { return it }
            return finish(
                s, a, success = false,
                reason = "bouton de validation grisé par le site : un choix reste à faire à la main",
                handedOver = holdWhenUnverifiable,
            )
        }

        // d bis. « Autoriser la sélection » : seulement quand TOUT est vu et refusé (voir SelectionConfirm).
        if (s.method == RefusalMethod.OPTIONS && selectionFullyRefused(s, a, canExplore)) {
            SelectionConfirm.find(a.panelRoot!!)?.let { node ->
                val label = TextNormalizer.canonical(node.subtreeTexts().firstOrNull { it.isNotBlank() }.orEmpty())
                val key = "selection|$label"
                if (key !in s.doneKeys && (s.noEffect[key] ?: 0) < MAX_NO_EFFECT) {
                    deferClosingOnPartial(s)?.let { return it }
                    s.closedByRefusal = false
                    return act(
                        s, a,
                        listOf(Op.Click(ClickTarget.Node(node), label, isToggle = false, selectionConfirm = true, allowTapFallback = false)),
                        PendingOp.Button(key, closing = true),
                        "validation de la sélection, tout étant refusé : « $label »",
                    )
                }
            }
        }

        // Sous-page sans bouton de validation : revenir en arrière une fois.
        if (s.visitedTabs.isNotEmpty() && !s.wentBack) {
            pick(s, a, ButtonKind.BACK)?.let { b ->
                s.wentBack = true
                s.newPage()
                return click(s, a, b, closing = false, "retour : « ${b.phrase} »")
            }
        }

        // Dernier recours : options non encore ouvertes.
        if (!closingOnly) {
            pick(s, a, ButtonKind.OPTIONS)?.let { b ->
                s.optionsOpened = true
                s.method = RefusalMethod.OPTIONS
                s.newPage()
                return click(s, a, b, closing = false, "options : « ${b.phrase} »")
            }
        }
        // Bouton de validation non visible : défiler jusqu'en bas de la fenêtre avant de conclure
        // qu'il manque (Sourcepoint, 21/11). Jamais en fin de délai ou d'actions : des choix encore
        // acceptés pourraient apparaître sans pouvoir être traités avant l'enregistrement.
        if (!closingOnly && !s.scrollEnded && remaining > 3 && s.confirmSearchScrolls < MAX_CONFIRM_SEARCH_SCROLLS &&
            (s.inSettings(a) || a.offscreenConfirm)
        ) {
            s.confirmSearchScrolls++
            val target = a.searchScrollTarget
            if (target != null) {
                return scrollAct(s, a, Op.Scroll(target), "défilement vers le bouton de validation")
            }
            return scrollAct(s, a, Op.Swipe(swipeArea(a), a.root), "balayage vers le bouton de validation")
        }
        // Refus complet : plus rien à faire dans les préférences et aucune validation possible :
        // le refus direct du site, gardé en réserve, termine. Idem pour le « Tout refuser » des
        // préférences mis en réserve derrière les choix un par un (27/11).
        if (completeRefusal || s.prefsRefusalReserved) {
            globalRefusal?.let { r ->
                s.method = s.method ?: RefusalMethod.DIRECT
                return click(s, a, r, closing = true, "refus (repli) : « ${r.phrase} »")
            }
        }
        return null
    }

    /**
     * La liste défilante est-elle ENTIÈREMENT dans la capture, même si elle ne défile pas ? Il faut : capture
     * complète, liste dans une page web (WebView), des lignes situées sous l'écran déjà présentes avec un état
     * lu (preuve que la page expose tout, ce que Firefox ne fait pas), et l'état de TOUS les interrupteurs de
     * la liste connu. Sinon, le « défilement impossible » reste un échec prudent.
     */
    private fun listFullyExposed(s: Session, a: ScreenAnalysis, truncated: Boolean, requireCollapsedRows: Boolean = false): Boolean {
        if (truncated || s.captureTruncated || !a.scopeComplete || a.unknownStateToggles > 0) return false
        val list = a.scrollTarget ?: return false
        if (list.ancestors().none { it.className.contains("WebView", ignoreCase = true) }) return false
        val toggles = list.walk().filter { it !== list && it.isCheckable && (it.className.contains("Toggle") || it.className.contains("Switch") || it.className.contains("CheckBox")) }.toList()
        if (toggles.size < 2) return false
        val hidden = toggles.filter { !it.isVisibleToUser }
        val hiddenRows = hidden.size
        // Lignes sous l'écran « repliées » (hauteur nulle) : signe d'une liste rendue en entier, pas chargée
        // au fur et à mesure. Au moins 90 % d'entre elles (Galaxy Defense : la ligne juste sous l'écran garde sa taille).
        if (requireCollapsedRows && hidden.count { it.bounds.height <= 0 } * 10 < hiddenRows * 9) return false
        return hiddenRows > 0 && toggles.size == a.toggleStates.size
    }

    /** Refus complet : reste-t-il quelque chose à faire dans les préférences avant d'enregistrer ? */
    private fun preferenceWorkLeft(s: Session, a: ScreenAnalysis, canExplore: Boolean, canScroll: Boolean): Boolean {
        if (!s.optionsOpened && pick(s, a, ButtonKind.OPTIONS) != null) return true
        if (s.toggleClicks < MAX_TOGGLE_CLICKS &&
            actionableToggles(s, a).isNotEmpty()
        ) return true
        if (pick(s, a, ButtonKind.RADIO_REFUSE) { it.selected != true && (it.isMaster || !masterRadioRefused(a)) } != null) return true
        if (pairedChoiceRows(a) >= 2 && pick(s, a, ButtonKind.REFUSE) { inChoiceRow(a, it) } != null) return true
        if (canScroll && wantsScroll(s, a)) return true
        if (canExplore && s.inSettings(a) && pick(s, a, ButtonKind.VENDOR_TAB) { it.label !in s.visitedTabs } != null) return true
        if (unvisitedCategories(s, a).isNotEmpty()) return true
        return false
    }

    /**
     * Préférences ouvertes : reste-t-il un choix un par un à faire sur cet écran (interrupteur encore
     * activé, « Bloquer » non sélectionné, « Refuser » d'une ligne « Refuser | Accepter » ou « Non | Oui ») ?
     */
    private fun itemWorkLeft(s: Session, a: ScreenAnalysis): Boolean {
        if (s.toggleClicks < MAX_TOGGLE_CLICKS && actionableToggles(s, a).isNotEmpty()) return true
        if (pick(s, a, ButtonKind.RADIO_REFUSE) { it.selected != true && (it.isMaster || !masterRadioRefused(a)) } != null) return true
        if (pairedChoiceRows(a) >= 2 && pick(s, a, ButtonKind.REFUSE) { it.key !in s.doneKeys && inChoiceRow(a, it) } != null) return true
        return false
    }

    /** Onglets de catégories utilisables (garde-fou et filtre de navigation passés) pas encore ouverts. */
    private fun unvisitedCategories(s: Session, a: ScreenAnalysis): List<ButtonCandidate> =
        a.categoryTabs.filter { t ->
            t.blocked == null && t.labelNode.isEnabled && t.label !in s.visitedCategories &&
                (s.noEffect[t.key] ?: 0) < MAX_NO_EFFECT && NavigationFilter.blockReason(t, settingsOpened = true) == null
        }

    /**
     * Ouvre l'onglet de catégorie suivant (voir c2 quinquies). La catégorie affichée compte comme vue ;
     * si elle vient d'être ouverte et ne montre encore aucun réglage, on lui laisse un instant pour
     * s'afficher (un interrupteur pas encore dessiné ne doit pas être sauté).
     */
    private fun categoryStep(s: Session, a: ScreenAnalysis): Step? {
        if (a.categoryTabs.isEmpty() || s.categoryClicks >= MAX_CATEGORY_TABS) return null
        val shown = a.categoryTabs.filter { it.selected == true }
        val justOpened = shown.any { it.label == s.lastCategory }
        if (justOpened && a.toggleStates.isEmpty() && !s.categoryWaited) {
            s.categoryWaited = true
            return Step.Wait(limits.waitAfterClickMs)
        }
        shown.forEach { s.visitedCategories.add(it.label) }
        val next = unvisitedCategories(s, a).firstOrNull() ?: return null
        if (next.target == null || !next.labelNode.isVisibleToUser) {
            // Onglet sous l'écran : l'amener à l'écran (un défilement, pas un clic), une seule fois.
            if (!s.revealedCategories.add(next.label)) {
                s.visitedCategories.add(next.label)
                s.skippedCategories.add(next.label)
                return categoryStep(s, a)
            }
            return act(s, a, listOf(Op.ShowOnScreen(next.labelNode, next.label)), PendingOp.Reveal, "aller à la catégorie", countsAsAction = false)
        }
        s.visitedCategories.add(next.label)
        s.lastCategory = next.label
        s.categoryWaited = false
        s.categoryClicks++
        s.method = s.method ?: RefusalMethod.OPTIONS
        return click(s, a, next, closing = false, "catégorie : « ${next.label} »")
    }

    /**
     * Interrupteurs activés à décocher maintenant : visibles d'abord, puis ceux hors de l'écran ciblés
     * par leur nœud. Un essai chacun ; un interrupteur cliqué hors écran et relu encore activé a droit
     * à un second essai une fois visible (si Chrome a ignoré l'action hors écran).
     */
    private fun actionableToggles(s: Session, a: ScreenAnalysis): List<ToggleCandidate> =
        a.checkedToggles.filter { t ->
            if (t.target == null || !(t.node.isVisibleToUser || t.offscreen)) return@filter false
            val tries = s.toggleAttempts[t.signature] ?: 0
            tries < MAX_TOGGLE_ATTEMPTS ||
                (t.node.isVisibleToUser && t.signature in s.offscreenTried && t.signature !in s.offscreenRetried)
        }.sortedBy { if (it.node.isVisibleToUser) 0 else 1 }

    /** Un bouton radio visible et coché du panneau n'est pas un choix de refus reconnu. */
    private fun unknownRadioChecked(a: ScreenAnalysis): Boolean {
        val panel = a.panelRoot ?: return false
        val refusalRadios = a.buttons(ButtonKind.RADIO_REFUSE).mapTo(HashSet()) { it.labelNode }
        return panel.walk().any {
            it.className.contains("RadioButton", ignoreCase = true) && it.isChecked && it.isVisibleToUser && it !in refusalRadios
        }
    }

    /**
     * Page de choix radio seulement (aucun interrupteur), où chaque « Refuser » lu est déjà sélectionné,
     * aucun « Accepter » n'est sélectionné ni d'état inconnu, et aucun radio coché n'est inconnu.
     */
    private fun radiosAllRefused(a: ScreenAnalysis): Boolean {
        val refusals = a.buttons(ButtonKind.RADIO_REFUSE)
        return a.toggleStates.isEmpty() && refusals.isNotEmpty() && refusals.all { it.selected == true } &&
            a.buttons(ButtonKind.ACCEPT).none { it.isRadio && it.selected != false } && !unknownRadioChecked(a)
    }

    /** « Tous les partenaires › Bloquer » (ou équivalent) visible et sélectionné sur cet écran. */
    private fun masterRadioRefused(a: ScreenAnalysis): Boolean =
        a.buttons(ButtonKind.RADIO_REFUSE).any { it.isMaster && it.selected == true && it.labelNode.isVisibleToUser }

    /** Nombre de lignes « Refuser | Accepter » côte à côte (même hauteur à l'écran). */
    private fun pairedChoiceRows(a: ScreenAnalysis): Int = a.buttons(ButtonKind.REFUSE).count { inChoiceRow(a, it) }

    /** « Refuser » d'un seul mot avec un « Accepter » juste à sa droite, à la même hauteur. */
    private fun inChoiceRow(a: ScreenAnalysis, r: ButtonCandidate): Boolean {
        val b = r.labelNode.bounds
        if (b.isEmpty || isGlobalRefusal(r) || r.target == null) return false
        return a.buttons(ButtonKind.ACCEPT).any {
            val o = it.labelNode.bounds
            !o.isEmpty && kotlin.math.abs(o.centerY - b.centerY) <= 30 && o.left > b.left
        }
    }

    private fun wantsScroll(s: Session, a: ScreenAnalysis): Boolean {
        if (s.scrollEnded || !s.inSettings(a)) return false
        if (s.scrolls >= MAX_SCROLLS_PER_PAGE || s.totalScrolls >= MAX_TOTAL_SCROLLS) return false
        // La liste bouge mais ne livre plus aucun nouveau choix (bandeau animé, compteur, page du site
        // derrière) : inutile de défiler à l'infini. La fin de liste n'est PAS constatée pour autant :
        // la validation restera « non vérifiée » (liste pas lue jusqu'en bas).
        if (s.scrollsWithoutNews >= MAX_SCROLLS_WITHOUT_NEWS) return false
        // « Tous les partenaires › Bloquer » est sélectionné, sans interrupteur sur la page : la liste
        // entière est réglée, inutile de faire défiler 300 partenaires un par un (Le Dauphiné, 20/11).
        if (a.toggleStates.isEmpty() &&
            a.buttons(ButtonKind.RADIO_REFUSE).any { it.isMaster && it.selected == true && it.labelNode.isVisibleToUser } &&
            a.buttons(ButtonKind.CONFIRM).any { it.target != null }
        ) return false
        if (a.scrollTarget == null) {
            // Pas de conteneur exposé (fréquent dans les navigateurs) : balayer DANS le panneau, comme
            // un doigt, tant que la liste bouge. Un balayage sans effet marque la fin de la liste
            // (empreinte inchangée → scrollEnded). Avant le 21/11 au soir : un seul balayage d'essai,
            // d'où des listes jamais lues en entier.
            return a.toggleStates.isNotEmpty() || a.buttons(ButtonKind.RADIO_REFUSE).isNotEmpty()
        }
        return true
    }

    /**
     * Conditions pour « Autoriser la sélection » ([SelectionConfirm]) : panneau identifié, exploration
     * TERMINÉE (rien de replié, bas de liste atteint, aucun onglet fournisseurs non visité, temps et
     * actions encore disponibles), et chaque interrupteur vu pendant la session, d'état connu, refusé
     * (hors exceptions légitimes). Aucun choix radio douteux ni capture incomplète.
     */
    private fun selectionFullyRefused(s: Session, a: ScreenAnalysis, canExplore: Boolean): Boolean {
        val panel = a.panelRoot ?: return false
        if (!canExplore || s.budgetNote != null || s.captureTruncated || s.navigationBlocked) return false
        if (s.seenToggles.isEmpty() || a.toggleStates.isEmpty() || s.anyActiveLeft()) return false
        if (a.checkedToggles.isNotEmpty() || a.unknownStateToggles > 0 || s.unknownToggleSignatures.isNotEmpty()) return false
        if (s.blockedToggles.isNotEmpty() || s.stillChecked.isNotEmpty() || !s.toggleAttempts.keys.all { it in s.verifiedOff }) return false
        if (s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty()) return false
        if (a.expandables.isNotEmpty()) return false
        if (!s.scrollEnded && a.scrollTarget != null) return false
        if (a.buttons(ButtonKind.VENDOR_TAB).any { it.target != null && it.label !in s.visitedTabs }) return false
        // Un bouton radio sélectionné qui n'est pas un refus reconnu (« Oui », « Activé ») : doute.
        val refusalRadios = a.buttons(ButtonKind.RADIO_REFUSE).mapTo(HashSet()) { it.labelNode }
        if (panel.walk().any { it.className.contains("RadioButton", ignoreCase = true) && it.isChecked && it !in refusalRadios }) return false
        return true
    }

    /**
     * Bouton écrasé sous la barre du navigateur ([ButtonCandidate.hiddenTarget]) : seulement si aucun
     * bouton visible de la même catégorie ne convient. Mêmes filtres que [pick] (déjà traité, sans effet,
     * filtre de navigation).
     */
    private fun pickHidden(
        s: Session,
        a: ScreenAnalysis,
        kind: ButtonKind,
        extra: (ButtonCandidate) -> Boolean = { true },
    ): ButtonCandidate? {
        val settingsOpened = s.optionsOpened || s.visitedTabs.isNotEmpty()
        return a.buttons(kind).firstOrNull { b ->
            b.target == null && b.hiddenTarget != null && b.blocked == null && b.labelNode.isEnabled &&
                b.key !in s.doneKeys && (s.noEffect[b.key] ?: 0) < MAX_NO_EFFECT &&
                NavigationFilter.blockReason(b, settingsOpened) == null && extra(b)
        }
    }

    /**
     * Bouton du pied de la fenêtre resté SOUS l'écran malgré un défilement ou « faire apparaître »
     * ([ButtonCandidate.stuckTarget], voir [noteFooterPositions]). En dernier, après les boutons visibles
     * et écrasés ; mêmes filtres que [pick].
     */
    private fun pickStuck(
        s: Session,
        a: ScreenAnalysis,
        kind: ButtonKind,
        extra: (ButtonCandidate) -> Boolean = { true },
    ): ButtonCandidate? {
        val settingsOpened = s.optionsOpened || s.visitedTabs.isNotEmpty()
        return a.buttons(kind).firstOrNull { b ->
            b.target == null && b.hiddenTarget == null && b.stuckTarget != null && b.key in s.stuckFooter &&
                b.blocked == null && b.labelNode.isEnabled &&
                b.key !in s.doneKeys && (s.noEffect[b.key] ?: 0) < MAX_NO_EFFECT &&
                NavigationFilter.blockReason(b, settingsOpened) == null && extra(b)
        }
    }

    /**
     * Retient la position des boutons du pied de fenêtre situés sous l'écran. Si un défilement ET un
     * « faire apparaître » ont eu lieu sans que le bouton bouge d'un pixel, il est dans une fenêtre fixe : il devient cliquable par son nœud. S'il a bougé, il pourra être
     * amené à l'écran normalement : rien ne change.
     */
    private fun noteFooterPositions(s: Session, a: ScreenAnalysis) {
        val current = HashMap<String, Bounds>()
        for (b in a.buttons) {
            if (b.stuckTarget == null) continue
            val bounds = b.labelNode.bounds
            current[b.key] = bounds
            val before = s.footerBounds[b.key]
            // Relecture sécurité du 27/11 : DEUX essais de nature différente (« faire apparaître » ET défilement),
            // sans que le bouton bouge d'un pixel. Un seul défilement peut avoir agi sur une liste intérieure.
            if (before != null && before == bounds) {
                if ((s.footerMoveTried[b.key]?.size ?: 0) >= 2) s.stuckFooter.add(b.key)
            } else {
                s.footerMoveTried.remove(b.key)
                if (before != null) s.stuckFooter.remove(b.key)
            }
        }
        s.footerMoveTried.keys.retainAll(current.keys)
        s.footerBounds.clear()
        s.footerBounds.putAll(current)
    }

    /** Bouton grisé par le site (lui-même ou le bouton cliquable qui l'enveloppe). */
    private fun greyed(b: ButtonCandidate): Boolean =
        !b.labelNode.isEnabled || b.labelNode.ancestors().take(4).any { it.isClickable && !it.isEnabled }

    /**
     * Refus complet : les préférences ouvertes ne permettent pas de terminer (rien de lisible, validation
     * grisée) et le premier écran proposait un « Tout refuser » mis en attente. Revenir une fois à ce
     * premier écran ; le refus y sera cliqué sans plus attendre (Consentmanager dans Firefox, 22/11).
     */
    private fun backToFirstScreen(s: Session, a: ScreenAnalysis): Step? {
        if (!completeRefusal || !s.deferredRefusalSeen || !s.optionsOpened || s.leftPreferences) return null
        if (s.visitedTabs.isNotEmpty()) return null
        val back = pick(s, a, ButtonKind.BACK) ?: return null
        s.leftPreferences = true
        s.newPage()
        return click(s, a, back, closing = false, "retour au premier écran (préférences inutilisables) : « ${back.phrase} »")
    }

    /** Premier bouton utilisable de cette catégorie (visible en priorité), hors boutons déjà traités. */
    private fun pick(
        s: Session,
        a: ScreenAnalysis,
        kind: ButtonKind,
        extra: (ButtonCandidate) -> Boolean = { true },
    ): ButtonCandidate? {
        val settingsOpened = s.optionsOpened || s.visitedTabs.isNotEmpty()
        val usable = a.buttons(kind).filter { b ->
            b.blocked?.let { s.blockedLabels.add(it.reason) }
            b.target != null && b.key !in s.doneKeys &&
                (s.noEffect[b.key] ?: 0) < MAX_NO_EFFECT &&
                NavigationFilter.blockReason(b, settingsOpened) == null && extra(b)
        }
        // Ne jamais retomber sur un bouton invisible ou désactivé.
        return usable.firstOrNull { it.labelNode.isVisibleToUser && it.labelNode.isEnabled }
    }

    /**
     * Lecture partielle et décision de fermer la fenêtre : relire tout l'écran d'abord. Les notes
     * posées juste avant cette décision (méthode, exploration) sont remises comme au début de l'étape,
     * pour que la lecture complète reprenne la décision de zéro.
     */
    private fun deferClosingOnPartial(s: Session): Step? {
        if (!analyzingPartial) return null
        s.method = partialMethodBefore
        s.explorationNote = partialNoteBefore
        fullCaptureRequested = true
        return Step.Wait(0)
    }

    private fun click(s: Session, a: ScreenAnalysis, b: ButtonCandidate, closing: Boolean, description: String): Step {
        // Mode prudent : une fenêtre ne se ferme (validation, refus) que sur une lecture COMPLÈTE de
        // l'écran. Sur une lecture partielle, on relit tout d'abord ; la même décision sera reprise.
        if (closing) deferClosingOnPartial(s)?.let { return it }
        if (b.kind == ButtonKind.REFUSE || b.kind == ButtonKind.GLOBAL_REJECT) s.refusalControlsActed = true
        if (b.kind == ButtonKind.CONFIRM) s.confirmClicked = true
        if (closing) s.closedByRefusal = b.kind == ButtonKind.REFUSE || b.kind == ButtonKind.GLOBAL_REJECT
        // Un seul clic par élément (anti-boucle) : plus de second essai par appui au centre du libellé.
        // Une validation n'est jamais déclenchée par un appui aux coordonnées, même en secours.
        // Bouton écrasé sous la barre du navigateur : action d'accessibilité du nœud seulement.
        val squashed = b.target == null
        val op = Op.Click(
            b.target ?: b.hiddenTarget ?: b.stuckTarget!!, b.label, isToggle = false,
            allowTapFallback = b.kind != ButtonKind.CONFIRM && !squashed,
            offscreen = squashed,
        )
        return act(s, a, listOf(op), PendingOp.Button(b.key, closing), description)
    }

    private fun act(
        s: Session,
        a: ScreenAnalysis,
        ops: List<Op>,
        pending: PendingOp,
        description: String,
        countsAsAction: Boolean = true,
    ): Step {
        if (countsAsAction) s.actions++
        // Défilement ou « faire apparaître » : la position des boutons sous l'écran sera comparée ensuite.
        if (pending is PendingOp.Scroll || pending is PendingOp.Reveal) {
            for (k in s.footerBounds.keys) s.footerMoveTried.getOrPut(k) { HashSet() }.add(pending.javaClass.simpleName)
        }
        s.lastOp = pending
        s.lastFingerprint = a.fingerprint
        return Step.Act(ops, description)
    }

    /**
     * Compte les défilements qui n'ont fait apparaître aucun nouvel interrupteur ni choix radio. Une
     * longue liste en livre à chaque écran (le compteur revient à zéro) ; une zone qui bouge sans rien
     * de neuf s'arrête après [MAX_SCROLLS_WITHOUT_NEWS] défilements.
     */
    private fun noteScrollNovelty(s: Session) {
        val known = s.seenToggles.size + s.radioSeen.size
        if (known > s.choicesAtLastScroll) s.scrollsWithoutNews = 0 else s.scrollsWithoutNews++
        s.choicesAtLastScroll = known
    }

    /**
     * Défilement continu : un défilement ne compte pas dans les 40 actions (il ne clique sur rien).
     * Il reste plafonné par page ([MAX_SCROLLS_PER_PAGE]), par session ([MAX_TOTAL_SCROLLS]) et par le délai.
     */
    private fun scrollAct(s: Session, a: ScreenAnalysis, op: Op, description: String): Step {
        s.totalScrolls++
        return act(s, a, listOf(op), PendingOp.Scroll, description, countsAsAction = false)
    }

    /**
     * Page de choix un par un (plusieurs « Refuser » d'une seule finalité, sans refus global visible) :
     * un « Tout refuser » se trouve souvent plus bas. Firefox ne présente pas les éléments hors de
     * l'écran : on défile pour le chercher, jusqu'en bas de la fenêtre, avant de cliquer un par un.
     */
    private fun wantsGlobalSearch(s: Session, a: ScreenAnalysis): Boolean =
        !s.scrollEnded &&
            a.buttons(ButtonKind.REFUSE).count { it.target != null && !isGlobalRefusal(it) } >= 2 &&
            a.buttons(ButtonKind.REFUSE).none { (it.target != null || it.hiddenTarget != null) && isGlobalRefusal(it) } &&
            a.buttons(ButtonKind.GLOBAL_REJECT).none { it.target != null }

    /**
     * Zone du balayage : le panneau de cookies s'il est assez grand (le doigt doit faire défiler SA
     * liste, pas la page du site derrière), sinon toute la fenêtre.
     */
    private fun swipeArea(a: ScreenAnalysis): Bounds {
        val p = a.panelRoot?.bounds
        if (p == null || p.isEmpty || p.height < MIN_SWIPE_PANEL_HEIGHT || p.width < MIN_SWIPE_PANEL_WIDTH) return a.windowBounds
        val w = a.windowBounds
        val clipped = Bounds(maxOf(p.left, w.left), maxOf(p.top, w.top), minOf(p.right, w.right), minOf(p.bottom, w.bottom))
        return if (clipped.height < MIN_SWIPE_PANEL_HEIGHT || clipped.width < MIN_SWIPE_PANEL_WIDTH) a.windowBounds else clipped
    }

    /** Refus de toute la fenêtre : identifiant documenté ou formule d'au moins deux mots (« Tout refuser »). */
    private fun isGlobalRefusal(b: ButtonCandidate): Boolean =
        b.phrase.startsWith("id:") || TextNormalizer.wordCount(b.phrase) >= 2

    private fun failureReason(s: Session, a: ScreenAnalysis): String {
        s.budgetNote?.let { return it }
        // 22/11 : un bouton bloqué sur le PREMIER écran (« S'abonner ») était donné comme motif d'un échec
        // survenu bien plus loin, dans les préférences. Il n'est cité que si les préférences n'ont pas été ouvertes.
        if (s.blockedLabels.isNotEmpty() && !s.optionsOpened && s.visitedTabs.isEmpty()) return s.blockedLabels.first()
        if (s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty()) return "choix radio « Bloquer / Autoriser » non vérifié"
        if (s.blockedToggles.isNotEmpty()) return "interrupteurs impossibles à décocher sans risque"
        if (s.confirmClicked) return "bouton de validation cliqué, mais la fenêtre ne s'est pas fermée"
        if (s.visitedTabs.isNotEmpty() || s.optionsOpened) {
            return if (s.scrollEnded || s.confirmSearchScrolls > 0) {
                "bouton de validation introuvable, même après défilement jusqu'en bas de la fenêtre"
            } else {
                "bouton de validation introuvable"
            }
        }
        // Mur « accepter ou payer » : aucun refus gratuit n'existe, on l'explique au lieu d'un échec vague.
        if (ConsentOrPay.detectNearAccept(a.buttons(ButtonKind.ACCEPT).map { it.labelNode })) return ConsentOrPay.REASON
        return if (a.buttons(ButtonKind.ACCEPT).isNotEmpty()) {
            "aucune option de refus (seulement « ${a.buttons(ButtonKind.ACCEPT).first().phrase} »)"
        } else {
            "aucun bouton de refus reconnu"
        }
    }

    private fun finish(
        s: Session,
        a: ScreenAnalysis,
        success: Boolean,
        reason: String?,
        diagnostic: Boolean = !success,
        interrupted: Boolean = !diagnostic && !success,
        incompleteCapture: Boolean = false,
        handedOver: Boolean = false,
    ): Step {
        session = null
        val pause = if (success) limits.cooldownMs else limits.failureCooldownMs
        // Après une session terminée, le site peut réafficher une page avec une empreinte différente.
        // Le délai terminal s'applique donc à toute fenêtre de cette appli, pas seulement à la bannière initiale.
        // Navigateur (audit du 21/11, point 5) : jamais « toute l'appli », sinon le site suivant, ouvert
        // dans les 20 s, était ignoré. Le délai ne vise que la fenêtre (son contenu) qui vient d'être traitée.
        val anyWindow = success && !BrowserPolicy.isBrowser(s.packageName)
        // Cas particulier « préférences d'abord » : sur une appli inconnue, l'ouverture des options
        // a fait disparaître la fenêtre. Cette appli vient d'être mémorisée comme incompatible avec
        // ce parcours ; ne pas laisser le délai d'échec de 10 min bloquer le refus direct au prochain
        // affichage de la même fenêtre. La disparition courante reste terminée normalement.
        val retryWithDirectRefusal = interrupted && s.optionsOpened &&
            s.platform == PLATFORM_UNKNOWN && !BrowserPolicy.isBrowser(s.packageName) &&
            s.packageName in unusablePreferenceApps
        // Toutes les fenêtres vues pendant la session (bandeau, préférences…) : sinon la page affichée
        // à la fin, différente du bandeau, relançait aussitôt une nouvelle session.
        if (retryWithDirectRefusal) {
            cooldowns.remove(s.packageName)
        } else {
            cooldowns[s.packageName] = (clock() + pause) to if (anyWindow) setOf(ANY_WINDOW) else s.seenKeys.toSet()
        }
        if (!success && BrowserPolicy.isBrowser(s.packageName)) {
            shortCooldowns[s.packageName] = (clock() + limits.cooldownMs) to s.firstShape
        }
        // Un clic n'est jamais une preuve : seul un état OFF relu après la tentative compte.
        val togglesOff = s.verifiedOff.size
        val togglesUnconfirmed = s.toggleAttempts.keys - s.verifiedOff - s.stillChecked
        // Interrupteurs non vérifiés, NOMMÉS, pour le diagnostic (option « diagnostic détaillé », 26/11).
        // Un même interrupteur n'apparaît qu'une fois, avec la raison la plus parlante (resté activé
        // d'abord, jamais traité en dernier). Plafonné : une liste de 800 partenaires ne doit pas
        // gonfler le diagnostic. Libellés = texte d'écran : diagnostic uniquement, jamais l'historique.
        val controlIssues = ArrayList<ControlIssue>()
        run {
            val seen = HashSet<String>()
            fun issue(signatures: Collection<String>, kind: ControlIssueKind) {
                for (sig in signatures) {
                    if (controlIssues.size >= ControlIssue.MAX_ISSUES) return
                    if (!seen.add(sig)) continue
                    controlIssues.add(ControlIssue(s.toggleLabels[sig] ?: sig, kind, sig))
                }
            }
            issue(s.stillChecked, ControlIssueKind.STILL_ON)
            issue(s.blockedToggles, ControlIssueKind.BLOCKED)
            issue(s.unknownToggleSignatures, ControlIssueKind.UNKNOWN_STATE)
            issue(togglesUnconfirmed, ControlIssueKind.UNCONFIRMED)
            issue(s.knownOnUntried(), ControlIssueKind.UNTRIED)
        }
        val method = if (success) (s.method ?: RefusalMethod.DIRECT) else s.method
        // Un seul interrupteur d'état inconnu suffit à rendre le refus incomplet, même si d'autres
        // ont été désactivés.
        // Audit du 21/11 (n° 2) : après une VALIDATION (pas un refus du site), un interrupteur vu encore
        // activé ou une capture incomplète rendent aussi le refus incomplet ; avant, le résultat
        // s'affichait « refusé avec le bouton du site », voire « vérifié ».
        val validatedWithDoubt = !s.closedByRefusal && (s.anyActiveLeft() || s.captureTruncated)
        val incomplete = incompleteCapture || s.stillChecked.isNotEmpty() || s.blockedToggles.isNotEmpty() ||
            s.budgetNote != null || s.unknownToggleSignatures.isNotEmpty() || togglesUnconfirmed.isNotEmpty() || s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty() ||
            s.explorationNote != null || s.skippedNote != null || s.skippedCategories.isNotEmpty() ||
            s.navigationBlocked || validatedWithDoubt
        // Relecture indépendante des preuves accumulées : un état inconnu, une catégorie non
        // explorée ou un choix « Autoriser » sélectionné ne peut jamais devenir un refus vérifié.
        val verification = ConsentVerificationEngine.verifyAccessibility(
            ConsentVerificationEngine.AccessibilityEvidence(
                toggleStates = s.lastToggleStates,
                keptOn = s.keptOn,
                unknownToggleSignatures = s.unknownToggleSignatures,
                captureComplete = !s.captureTruncated && !incompleteCapture,
                explorationComplete = s.explorationNote == null && s.skippedNote == null &&
                    s.skippedCategories.isEmpty() && !s.navigationBlocked,
                radioRefusalVerified = s.radioSeen.isNotEmpty() &&
                    s.radioUnverified.isEmpty() && s.radioUnknown.isEmpty(),
                radioRefusalUnverified = s.radioUnverified.isNotEmpty() || s.radioUnknown.isNotEmpty(),
                acceptRadioSelected = s.radioAcceptSelected.isNotEmpty(),
            ),
        )
        // Même résultat métier qu'avant, désormais recalculé par le vérificateur indépendant.
        val togglesObservedOff = method != RefusalMethod.DIRECT &&
            s.seenToggles.isNotEmpty() && verification.allObservedTogglesOff
        // Les radios ne suffisent jamais si un interrupteur reste actif ou inconnu.
        val radioObservedSelected = method != RefusalMethod.DIRECT &&
            !s.anyActiveLeft() && verification.radioRefusalVerified
        val status = when {
            incompleteCapture -> OutcomeStatus.INCOMPLETE_CAPTURE
            handedOver -> OutcomeStatus.HANDED_OVER
            success && incomplete -> OutcomeStatus.PARTIAL
            // PARTIAL signifie qu'une partie du refus a réellement été effectuée.
            // Un contrôle inconnu bloque la validation, mais sans interrupteur désactivé,
            // tentative non vérifiée ou action explicite de refus, rien n'a été partiellement refusé.
            !success && !interrupted &&
                (togglesOff > 0 || togglesUnconfirmed.isNotEmpty() ||
                    s.stillChecked.isNotEmpty() || s.refusalControlsActed) &&
                (incompleteCapture || s.stillChecked.isNotEmpty() || s.blockedToggles.isNotEmpty() ||
                    s.budgetNote != null || s.unknownToggleSignatures.isNotEmpty() ||
                    togglesUnconfirmed.isNotEmpty() || s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty()) ->
                OutcomeStatus.PARTIAL
            // Opera, 27/11 à 12 h 00 : « vérifié » annoncé alors que la fenêtre n'avait pas pu être lue en entier.
            // Le vérificateur indépendant refusait ce « vérifié », mais son verdict n'était pas utilisé.
            // Relecture du 27/11 : un choix « Refuser » / « Non » cliqué sans état lisible n'est jamais vérifié.
            success && (togglesObservedOff || radioObservedSelected) &&
                (!verification.canClaimVerified || s.unreadableChoices > 0) -> OutcomeStatus.PARTIAL
            success && (togglesObservedOff || radioObservedSelected) -> OutcomeStatus.OPTIONS_SUCCESS
            // Clic de refus puis fermeture : la disparition de la fenêtre ne prouve pas l'enregistrement.
            success -> OutcomeStatus.UNVERIFIED
            interrupted -> OutcomeStatus.INTERRUPTED
            else -> OutcomeStatus.IMPOSSIBLE
        }
        // Un refus partiel mérite aussi un diagnostic (pour comprendre ce qui n'a pas pu être fait).
        val diagnosticRoot = if (diagnostic || status == OutcomeStatus.PARTIAL ||
            status == OutcomeStatus.INCOMPLETE_CAPTURE || status == OutcomeStatus.HANDED_OVER
        ) lastConsentRoot else null
        lastConsentRoot = null
        val partialReason = if (status == OutcomeStatus.PARTIAL && reason == null) {
            when {
                s.stillChecked.isNotEmpty() -> "${s.stillChecked.size} interrupteur(s) restés activés"
                (s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty()) -> "choix radio non vérifiés : ${s.radioUnverified.size} « Bloquer » non sélectionné(s), ${s.radioAcceptSelected.size} « Autoriser » sélectionné(s), ${s.radioUnknown.size} état(s) inconnu(s)"
                s.blockedToggles.isNotEmpty() -> "${s.blockedToggles.size} interrupteur(s) non modifiables sans risque"
                s.budgetNote != null -> s.budgetNote
                s.explorationNote != null -> s.explorationNote
                togglesUnconfirmed.isNotEmpty() -> "${togglesUnconfirmed.size} interrupteur(s) cliqué(s) dont le nouvel état n'a pas pu être relu"
                s.unknownToggleSignatures.isNotEmpty() -> "état de ${s.unknownToggleSignatures.size} interrupteur(s) non communiqué par l'appli, non vérifiés"
                s.captureTruncated -> "fenêtre trop longue pour être relue en entier : refus non vérifié"
                s.unreadableChoices > 0 -> "${s.unreadableChoices} choix « Refuser » / « Non » cliqué(s) dont l'appli ne publie pas l'état"
                else -> "${s.knownOnUntried().size} interrupteur(s) vu(s) activé(s) sans avoir pu être désactivé(s)"
            }
        } else if (status == OutcomeStatus.OPTIONS_SUCCESS && reason == null && s.seenToggles.isEmpty()) {
            "choix global « Bloquer » vu sélectionné ; aucun interrupteur dans la fenêtre"
        } else if (status == OutcomeStatus.UNVERIFIED && reason == null) {
            "bouton de refus cliqué et fenêtre fermée ; l'appli ne permet pas de vérifier l'enregistrement"
        } else {
            reason
        }
        return Step.Done(
            Outcome(
                packageName = s.packageName,
                success = success,
                method = method,
                togglesOff = togglesOff,
                actions = s.actions,
                reason = partialReason,
                togglesSeen = s.seenToggles.size,
                togglesUnknown = s.unknownToggleSignatures.size,
                diagnosticRoot = diagnosticRoot,
                status = status,
                platform = s.platform,
                confidence = s.confidence,
                structureSignature = s.structureSignature,
                togglesStillOn = s.stillChecked.size,
                togglesInitiallyOn = s.firstState.values.count { it == true },
                togglesAlreadyOff = s.firstState.values.count { it == false },
                togglesAttempted = s.toggleAttempts.size,
                togglesUnconfirmed = togglesUnconfirmed.size,
                consentDetected = true,
                controlsRecognized = s.controlsRecognized,
                actionAttempts = s.actionAttempts,
                actionExecutions = s.actionExecutions,
                blockedActions = s.blockedActions,
                stateVerified = when {
                    status == OutcomeStatus.OPTIONS_SUCCESS -> true
                    status == OutcomeStatus.PARTIAL || s.actionAttempts > 0 || s.toggleAttempts.isNotEmpty() || s.radioUnverified.isNotEmpty() || s.radioAcceptSelected.isNotEmpty() || s.radioUnknown.isNotEmpty() -> false
                    else -> null
                },
                captureTruncated = s.captureTruncated || incompleteCapture,
                controlIssues = controlIssues,
            ),
        )
    }

    private sealed interface PendingOp {
        data class Button(val key: String, val closing: Boolean) : PendingOp
        data object Toggles : PendingOp
        data object Expand : PendingOp
        data object Scroll : PendingOp
        data object Reveal : PendingOp
    }

    private class Session(
        val packageName: String,
        val startedAt: Long,
        val firstLightFingerprint: Int,
        val platform: String = PLATFORM_UNKNOWN,
        val confidence: Confidence = Confidence.PROBABLE,
        val structureSignature: Int = 0,
        /** Forme (boutons et indices) de la première fenêtre, pour la courte pause des navigateurs. */
        val firstShape: Int = 0,
    ) {
        /** Clés de délai de toutes les fenêtres vues pendant la session. */
        val seenKeys = HashSet<Int>().apply { add(firstLightFingerprint) }
        var toggleClicks = 0
        var actions = 0
        var method: RefusalMethod? = null
        var optionsOpened = false
        var closingClicks = 0
        var lastOp: PendingOp? = null
        var lastFingerprint = 0
        var waits = 0
        var goneChecks = 0
        var closingPending = false
        var budgetNote: String? = null

        /** Partie de la fenêtre volontairement laissée de côté (liste trop longue) : refus partiel. */
        var skippedNote: String? = null
        val doneKeys = HashSet<String>()
        val noEffect = HashMap<String, Int>()
        val visitedTabs = HashSet<String>()
        var wentBack = false
        val toggleAttempts = HashMap<String, Int>()
        /** Choix radio de refus observés puis relus sélectionnés. */
        val radioSeen = HashSet<String>()
        /** Défilements faits pour atteindre un « Tout refuser » hors de l'écran. */
        var offscreenScrolls = 0
        val radioUnverified = HashSet<String>()
        val radioAcceptSelected = HashSet<String>()
        val radioUnknown = HashSet<String>()
        /** Interrupteurs cliqués puis relus explicitement à OFF. */
        val verifiedOff = HashSet<String>()
        /** Premier état observé : true = ON, false = OFF, null = UNKNOWN. */
        val firstState = HashMap<String, Boolean?>()
        val stillChecked = HashSet<String>()
        val seenToggles = HashSet<String>()
        /** Dernier état lu de chaque interrupteur vu pendant la session (true, false, null = inconnu). */
        val lastToggleStates = HashMap<String, Boolean?>()
        /** Interrupteurs activés légitimement (toujours actif, « Nécessaires », état activé = refus). */
        val keptOn = HashSet<String>()

        /**
         * Libellé lisible de chaque interrupteur vu pendant la session (nom de la finalité ou du
         * fournisseur), pour le diagnostic nominatif. Texte d'écran : jamais dans l'historique.
         */
        val toggleLabels = HashMap<String, String>()

        /** Un interrupteur vu est resté activé (ou d'état inconnu) sans être une exception légitime. */
        fun anyActiveLeft(): Boolean = lastToggleStates.any { (sig, state) -> state != false && sig !in keptOn }

        /** Interrupteurs lus ACTIVÉS (dernier état connu), sans exception légitime, sur lesquels aucun clic n'a été tenté. */
        fun knownOnUntried(): Set<String> =
            lastToggleStates.filter { (sig, state) -> state == true && sig !in keptOn && sig !in toggleAttempts }.keys

        /** La dernière fermeture demandée était un refus du site (« Tout refuser »), pas une validation. */
        var closedByRefusal = false

        /** Exploration inachevée au moment de valider (liste pas lue jusqu'en bas, partenaires non ouverts). */
        var explorationNote: String? = null

        /** Défilements de toute la session (plafond anti-boucle, les défilements ne comptant pas comme actions). */
        var totalScrolls = 0

        /** Choix connus (interrupteurs + radios) au dernier défilement, et défilements sans nouveau choix. */
        var choicesAtLastScroll = -1
        var scrollsWithoutNews = 0
        val unknownToggleSignatures = HashSet<String>()
        val blockedToggles = HashSet<String>()
        val blockedLabels = LinkedHashSet<String>()
        var scrollEnded = false
        var scrolls = 0
        var navigationBlocked = false
        var controlsRecognized = 0
        var captureTruncated = false
        var actionAttempts = 0
        var actionExecutions = 0
        var blockedActions = 0
        var refusalControlsActed = false

        /** « Tout refuser » cliqué sur une fenêtre trop grande pour être lue en entier. */
        var truncatedDirectClick = false
        var swipedWithoutContainer = false
        /** Dernier changement observé à l'écran (le délai d'inactivité part de là). */
        var lastProgressAt = startedAt
        /** Défilements faits pour trouver le bouton de validation, et balayage d'essai sans conteneur. */
        var confirmSearchScrolls = 0
        var confirmClicked = false

        /** Interrupteurs cliqués hors de l'écran, et ceux qui ont déjà eu leur second essai. */
        val offscreenTried = HashSet<String>()
        val offscreenRetried = HashSet<String>()

        /** Bouton de validation déjà amené à l'écran d'un coup (une fois par session). */
        var confirmRevealed = false

        /** « Tout refuser » déjà amené à l'écran d'un coup après un défilement impossible (une fois). */
        var refusalRevealed = false

        /** Onglets de catégories (OneTrust) : ouverts ou affichés, amenés à l'écran, sautés, dernier ouvert. */
        val visitedCategories = HashSet<String>()
        val revealedCategories = HashSet<String>()
        val skippedCategories = HashSet<String>()
        var lastCategory: String? = null
        var categoryWaited = false
        var categoryClicks = 0

        /** Refus complet : un « Tout refuser » du premier écran a été mis en attente des préférences. */
        var deferredRefusalSeen = false

        /** Liste qui ne défile pas mais entièrement présente dans la capture (voir listFullyExposed). */
        var listFullyExposed = false

        /** Fin de liste tenue pour lue parce que la liste est entièrement dans la capture (pas mesurée au défilement). */
        var endFromExposure = false

        /** Interrupteurs connus avant d'amener la dernière ligne à l'écran (vérification de la fin de liste). */
        var exposureProbe: Set<String>? = null

        /** La vérification a montré une liste chargée au fur et à mesure : plus de raccourci pour cette session. */
        var exposureRejected = false

        /** Retour au premier écran fait, préférences abandonnées pour cette fenêtre. */
        var leftPreferences = false

        /** Choix « Refuser » / « Non » de lignes en simples boutons, cliqués : leur état n'est jamais lisible. */
        var unreadableChoices = 0

        /** « Tout refuser » des préférences gardé en réserve derrière les choix un par un (27/11). */
        var prefsRefusalReserved = false

        /** Boutons du pied de fenêtre sous l'écran : position lue, défilement tenté depuis, fenêtre fixe constatée. */
        val footerBounds = HashMap<String, Bounds>()
        val footerMoveTried = HashMap<String, MutableSet<String>>()
        val stuckFooter = HashSet<String>()

        /** Un écran de réglages : options ouvertes, ou interrupteurs / validation visibles. */
        fun inSettings(a: ScreenAnalysis): Boolean =
            optionsOpened || a.toggleStates.isNotEmpty() || a.buttons(ButtonKind.RADIO_REFUSE).isNotEmpty() || a.buttons(ButtonKind.CONFIRM).isNotEmpty()

        fun newPage() {
            scrollEnded = false
            scrolls = 0
            swipedWithoutContainer = false
            confirmSearchScrolls = 0
            scrollsWithoutNews = 0
            choicesAtLastScroll = -1
        }
    }

    private companion object {
        /** Jamais deux clics sur le même interrupteur ou le même bouton (anti-boucle, 21/11). */
        const val MAX_TOGGLE_ATTEMPTS = 1
        const val MAX_NO_EFFECT = 1
        /**
         * Plafonds de sécurité seulement (22/11) : une liste de 600 partenaires demande ~75 écrans. La
         * vraie limite est le délai de la session et l'arrêt quand plus rien de neuf n'apparaît.
         */
        const val MAX_SCROLLS_PER_PAGE = 250
        const val MAX_TOTAL_SCROLLS = 400
        /** Défilements de suite sans aucun nouveau choix avant d'arrêter de défiler. */
        const val MAX_SCROLLS_WITHOUT_NEWS = 15
        const val MAX_TOGGLES_PER_STEP = 80
        /** Sans aucun changement à l'écran pendant ce temps, la session s'arrête. */
        const val STALL_MS = 12_000L
        const val MIN_SWIPE_PANEL_HEIGHT = 300
        const val MIN_SWIPE_PANEL_WIDTH = 200

        const val MAX_TOGGLE_CLICKS = 600
        /** Avancement affiché à partir de ce nombre d'interrupteurs activés. */
        const val PROGRESS_MIN_TOGGLES = 15
        const val ANY_WINDOW = Int.MIN_VALUE
        /** Défilements au plus, par page, pour trouver le bouton de validation. */
        const val MAX_CONFIRM_SEARCH_SCROLLS = 12
        const val GONE_CHECKS = 3
        /** Fenêtre disparue alors qu'aucun refus ni validation n'a été cliqué : attente plus longue (~2 s). */
        const val GONE_CHECKS_NO_CLOSE = 8
        const val HARD_TIMEOUT_GRACE_MS = 5_000L
        /** Défilements au plus pour atteindre un « Tout refuser » situé plus bas. */
        const val MAX_OFFSCREEN_SCROLLS = 8
        /** Onglets de catégories ouverts au plus par fenêtre (OneTrust en compte 4 à 8). */
        const val MAX_CATEGORY_TABS = 15
    }
}
