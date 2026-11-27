package fr.refusbot.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import fr.refusbot.R
import fr.refusbot.core.BrowserPolicy
import fr.refusbot.core.ControlIssue
import fr.refusbot.core.DiagnosticExport
import fr.refusbot.core.DiagnosticThrottle
import fr.refusbot.core.Outcome
import fr.refusbot.core.Op
import fr.refusbot.core.PartialReadPolicy
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.Policy
import fr.refusbot.core.RefusalEngine
import fr.refusbot.core.ScreenAnalysis
import fr.refusbot.core.SessionTiming
import fr.refusbot.core.SessionTiming.Phase
import fr.refusbot.core.StabilityGate
import fr.refusbot.core.Rules
import fr.refusbot.core.Step
import fr.refusbot.core.SystemPackagePolicy
import fr.refusbot.core.UiNode
import fr.refusbot.core.walk
import fr.refusbot.data.AppsRepository
import fr.refusbot.data.LogEntry
import fr.refusbot.data.Settings
import fr.refusbot.refusBot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Cœur Android de RefusBot : reçoit les événements des seules applis surveillées, capture la fenêtre
 * active, demande au [RefusalEngine] (Kotlin pur) quoi faire et exécute ses décisions.
 */
class RefusBotAccessibilityService : AccessibilityService() {

    /**
     * Aucune erreur ne doit faire planter le service : Android désactiverait l'accessibilité de
     * RefusBot après des plantages répétés.
     */
    private val crashGuard = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Erreur interne ignorée", e) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + crashGuard)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.Default.limitedParallelism(1)

    // Recréés à chaque changement de règles (importées / d'origine), toujours sur le fil de travail.
    @Volatile private lateinit var rules: Rules
    @Volatile private lateinit var engine: RefusalEngine
    @Volatile private lateinit var executor: ActionExecutor
    @Volatile private lateinit var snapshotter: NodeSnapshotter

    @Volatile private var settings = Settings(enabled = false)

    /** Captures manuelles en attente (le filtre de paquets reste ouvert pendant ce temps). */
    private var capturesInProgress = 0

    /** Analyses consécutives sans fenêtre de consentement (pour ralentir le rythme). */
    @Volatile private var idleStreak = 0

    /** Fenêtre de cookies de la session en cours, pour la lecture rapide (voir [rememberPanel]). */
    private var panelRef: AccessibilityNodeInfo? = null
    private var pendingJob: Job? = null
    private var sessionJob: Job? = null

    /** Captures manuelles en attente ou en cours : annulées avec le reste si le service est interrompu. */
    private val captureJobs = HashSet<Job>()
    private var wasEnabled = true

    /** Évite une rafale de diagnostics automatiques lors du défilement d'une même fenêtre. */
    private val diagnosticThrottle = DiagnosticThrottle()

    override fun onServiceConnected() {
        super.onServiceConnected()
        installRules(refusBot.rulesRepository.active.value.rules)
        instance = this
        _running.value = true
        Notifier.cancelServiceStopped(this)
        // Charge l'historique tout de suite : il sert à détecter un changement d'interface.
        scope.launch { refusBot.journal.awaitLoaded() }
        applyPackageFilter() // aucun paquet tant que les réglages ne sont pas lus
        scope.launch {
            refusBot.settings.settings.collect { s ->
                val pausedNow = wasEnabled && !s.enabled
                wasEnabled = s.enabled
                settings = s
                applyPackageFilter()
                if (pausedNow) stopSession("mise en pause par l'utilisateur")
            }
        }
        scope.launch {
            refusBot.rulesRepository.active.collect { active ->
                if (active.rules !== rules) {
                    // Audit du 21/11 : la session en cours est terminée et notée dans l'historique au lieu
                    // de disparaître sans trace quand le moteur est recréé.
                    pendingJob?.cancel()
                    sessionJob?.cancel()
                    withContext(worker) {
                        if (::engine.isInitialized) engine.abort("règles modifiées pendant le traitement")?.let { onOutcome(it) }
                        installRules(active.rules)
                    }
                }
            }
        }
    }

    private fun installRules(newRules: Rules) {
        rules = newRules
        // Les délais d'attente de l'ancien moteur sont repris : la fenêtre qui vient d'être traitée
        // (ou interrompue) n'est pas refaite aussitôt (relecture sécurité du 21/11).
        val previous = if (::engine.isInitialized) engine else null
        engine = RefusalEngine(newRules).also { fresh -> previous?.let(fresh::adoptCooldowns) }
        executor = ActionExecutor(this, newRules)
        snapshotter = NodeSnapshotter(this, newRules)
    }

    /**
     * Le système n'envoie d'événements QUE pour les applis surveillées : la liste est mise à jour
     * dynamiquement à chaque changement. Liste vide ou pause → paquet inexistant (aucun événement).
     */
    private fun applyPackageFilter() {
        val info = serviceInfo ?: return
        val s = settings
        // Pendant une capture manuelle, les applis surveillées restent « écoutées » même en pause :
        // sinon une WebView ne publie pas son contenu (capture vide). Aucun refus n'est fait en pause.
        val listen = (s.enabled || capturesInProgress > 0) && s.monitored.isNotEmpty()
        info.packageNames = if (listen) s.monitored.filterNot(SystemPackagePolicy::shouldIgnore).toTypedArray().ifEmpty { arrayOf(NO_PACKAGE) } else arrayOf(NO_PACKAGE)
        serviceInfo = info
        if (!s.enabled) pendingJob?.cancel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Événement ignoré", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null || !::engine.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        val s = settings
        // Double contrôle : on ignore tout paquet non surveillé, et rien n'est fait en pause.
        if (!s.enabled || pkg !in s.monitored || SystemPackagePolicy.shouldIgnore(pkg)) return
        // Nouvelle fenêtre (dialogue, activité) : on repasse au rythme normal.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) idleStreak = 0
        if (sessionJob?.isActive == true || pendingJob?.isActive == true) return
        // Regroupement : au plus une analyse toutes les ~300 ms, même si l'appli émet en continu ;
        // ralenti à 1 s quand l'écran ne contient manifestement pas de fenêtre de consentement.
        val wait = if (idleStreak < IDLE_STREAK_BEFORE_SLOWDOWN) rules.limits.debounceMs else SLOW_DEBOUNCE_MS
        pendingJob = scope.launch {
            delay(wait)
            startSession()
        }
    }

    private fun startSession() {
        if (sessionJob?.isActive == true) return
        sessionJob = scope.launch(worker) {
            try {
                runSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session interrompue", e)
                engine.reset()
            }
        }
    }

    /** Profil appliqué à une fenêtre : par appli, global, et suspension si l'interface est nouvelle (profil Prudent). */
    private fun policyFor(packageName: String, a: ScreenAnalysis): Policy {
        val s = settings
        val p = s.policyFor(packageName)
        if (p != Policy.PRUDENT) return p
        // Navigateur : chaque site a sa propre fenêtre ; une reconnaissance certaine suffit.
        if (BrowserPolicy.knownInBrowser(packageName, a.confidence)) return p
        return if (isNewInterface(s, packageName, a.structureSignature)) Policy.OBSERVATION else p
    }

    /**
     * Vrai si cette appli a déjà eu des refus, mais jamais sur une fenêtre de cette structure, et que
     * l'utilisateur ne l'a pas validée. La première fenêtre d'une appli n'est jamais « nouvelle ».
     */
    private fun isNewInterface(s: Settings, packageName: String, signature: Int): Boolean {
        if (signature == 0) return false
        val known = refusBot.journal.refusedSignatures(packageName)
        return known.isNotEmpty() && signature !in known && !s.isAcknowledged(packageName, signature)
    }

    /**
     * Nouvelle fenêtre : attendre qu'elle ait fini de s'afficher (même nombre de nœuds deux fois de
     * suite, ~2 s au plus). Évite d'analyser une fenêtre de cookies à moitié chargée.
     */
    private suspend fun waitUntilStable() {
        val gate = StabilityGate()
        val start = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            val capture = snapshotter.capture(settings.monitored) ?: return
            val count = try {
                capture.screen.root.walk().count()
            } finally {
                capture.close()
            }
            if (gate.observe(count, System.currentTimeMillis() - start)) return
            delay(StabilityGate.CHECK_INTERVAL_MS)
        }
    }

    private suspend fun runSession() {
        val limits = rules.limits
        var missing = 0
        // Chronométrage des étapes (22/11) : durées seulement, enregistrées avec le résultat.
        val timing = SessionTiming()
        if (!engine.isActive) timing.measure(Phase.STABILITY) { waitUntilStable() }
        // Lecture rapide (22/11 au soir) : pendant une longue liste (décochage, défilement), seule la
        // fenêtre de cookies repérée à la dernière lecture complète est relue. Mode prudent : lecture
        // complète après tout autre clic, au moins une fois sur [PARTIAL_READS_IN_A_ROW] + 1, et dès que
        // le moteur la demande (panneau différent, fenêtre à fermer).
        var listWork = false
        var partialStreak = 0
        // Lecture rapide abandonnée pour la session si elle s'avère plus lente qu'une lecture complète (23/11).
        val partialPolicy = PartialReadPolicy()
        try {
            while (currentCoroutineContext().isActive) {
                val s = settings
                if (!s.enabled) {
                    engine.reset()
                    return
                }
                val fullWanted = engine.consumeFullCaptureRequest()
                val panel = panelRef
                val partial = if (partialPolicy.enabled && listWork && !fullWanted && engine.isActive && panel != null &&
                    partialStreak < PARTIAL_READS_IN_A_ROW
                ) {
                    val started = System.currentTimeMillis()
                    timing.measure(Phase.PARTIAL_CAPTURE) { snapshotter.capturePanel(panel, s.monitored) }
                        .also { if (it != null) partialPolicy.recordPartial(System.currentTimeMillis() - started) }
                } else {
                    null
                }
                partialStreak = if (partial != null) partialStreak + 1 else 0
                val capture = partial ?: run {
                    val started = System.currentTimeMillis()
                    timing.measure(Phase.CAPTURE) { snapshotter.capture(s.monitored) }
                        .also { if (it != null) partialPolicy.recordFull(System.currentTimeMillis() - started) }
                }
                if (capture == null) {
                    // Fenêtre momentanément indisponible, ou l'utilisateur a quitté l'appli.
                    if (!engine.isActive || ++missing > 3) {
                        engine.reset()
                        return
                    }
                    timing.measure(Phase.WAITS) { delay(limits.waitAfterClickMs) }
                    continue
                }
                missing = 0
                timing.noteNodes(capture.screen.root.walk().count())
                val pkg = capture.screen.packageName
                val step = try {
                    engine.holdWhenUnverifiable = s.holdIfUnverifiable
                    engine.completeRefusal = s.completeRefusal
                    timing.measure(Phase.ANALYSIS) { engine.next(capture.screen) { a -> policyFor(pkg, a) } }
                } catch (e: RuntimeException) {
                    capture.close()
                    throw e
                }
                if (!capture.screen.isPartial) rememberPanel(capture)
                if (step != Step.Idle) {
                    idleStreak = 0
                    // Compteurs uniquement (aucun texte d'écran) : utile pour le débogage via logcat.
                    engine.lastAnalysis?.let { a ->
                        Log.d(
                            TAG,
                            "analyse : indices=${a.hints.size} boutons=${a.buttons.size} " +
                                "interrupteurs=${a.toggleStates.size} cochés=${a.checkedToggles.size} " +
                                "sections=${a.expandables.size} défilable=${a.scrollTarget != null} " +
                                "plateforme=${a.platform} confiance=${a.confidence.label}",
                        )
                    }
                }
                when (step) {
                    Step.Idle -> {
                        capture.close()
                        idleStreak++
                        return
                    }
                    is Step.Wait -> {
                        capture.close()
                        timing.measure(Phase.WAITS) { delay(step.delayMs) }
                    }
                    is Step.Act -> {
                        try {
                            // Type d'étape seulement (« refus », « options »…) : le libellé du bouton est du texte d'écran,
                            // réservé aux fichiers de diagnostic (contre-audit N5).
                            Log.d(TAG, "${capture.screen.packageName} : ${step.description.substringBefore(" :").substringBefore(" «")}")
                            val phase = when {
                                step.ops.all { it is Op.Click && it.isToggle } -> Phase.TOGGLES
                                step.ops.all { it is Op.Scroll || it is Op.Swipe || it is Op.ShowOnScreen } -> Phase.SCROLL
                                else -> Phase.CLICKS
                            }
                            listWork = phase == Phase.TOGGLES || phase == Phase.SCROLL
                            val execution = timing.measure(phase) { executor.execute(step.ops, capture) }
                            engine.recordExecution(execution)
                            Log.d(TAG, "actions : demandées=${execution.requested}, acceptées par Android=${execution.executed}, bloquées=${execution.blocked}")
                            if (execution.navigationBlocked) engine.markNavigationBlocked()
                        } finally {
                            capture.close()
                        }
                        // Longue liste : avancement affiché, pour qu'on ne croie pas CYRAVA bloqué (21/11 au soir).
                        engine.progress()?.let { (done, total) -> Notifier.notifyProgress(this, done, total, s.notifyLevel) }
                        // Défilement continu : l'exécuteur a déjà attendu que la liste se pose et mesuré son
                        // déplacement ; on relit l'écran presque aussitôt pour décocher la suite.
                        val onlyScroll = step.ops.all { it is Op.Scroll || it is Op.Swipe || it is Op.ShowOnScreen }
                        timing.measure(Phase.PAUSES) {
                            delay(if (onlyScroll) SCROLL_STEP_DELAY_MS else Random.nextLong(limits.minDelayMs, limits.maxDelayMs + 1))
                        }
                    }
                    is Step.Done -> {
                        capture.close()
                        onOutcome(step.outcome, timing.summary())
                        return
                    }
                }
            }
        } finally {
            forgetPanel()
        }
    }

    /**
     * Fenêtre de cookies repérée par le moteur sur une lecture COMPLÈTE : gardée (copie) pour les
     * lectures rapides suivantes, seulement si elle a été lue en entier et n'est pas tout l'écran.
     */
    private fun rememberPanel(capture: Capture) {
        forgetPanel()
        val a = engine.lastAnalysis ?: return
        val panel = a.panelRoot ?: return
        if (!engine.isActive || panel === a.root || !a.scopeComplete || capture.screen.isTruncated) return
        panelRef = capture.info(panel)?.copyCompat()
    }

    private fun forgetPanel() {
        panelRef?.recycleCompat()
        panelRef = null
    }

    /** Interrompt immédiatement la session en cours (pause, arrêt d'urgence) et la journalise. */
    private fun stopSession(reason: String) {
        pendingJob?.cancel()
        sessionJob?.cancel()
        // Toujours la portée de l'application, jamais celle du service : onUnbind enchaîne
        // immédiatement sur shutdown(), qui annulerait un lancement fait sur la portée du service
        // avant même sa première exécution (contre-audit du 24/11). La portée de l'application
        // vit jusqu'à la fin du processus : la session interrompue est toujours journalisée.
        // Le fil de travail (worker) reste le même : le confinement du moteur est inchangé.
        refusBot.appScope.launch(worker) {
            if (!::engine.isInitialized) return@launch
            engine.abort(reason)?.let { onOutcome(it) }
        }
    }

    /** Arrêt d'urgence : plus aucune action, protection mise en pause, historique conservé. */
    fun emergencyStop() {
        stopSession("arrêt d'urgence demandé")
        refusBot.appScope.launch { refusBot.settings.setEnabled(false) }
    }

    /** [timing] : durées des étapes de la session (null si elle a été interrompue de l'extérieur). */
    private suspend fun onOutcome(o: Outcome, timing: SessionTiming.Summary? = null) {
        Notifier.cancelProgress(this)
        val appName = AppsRepository.label(this, o.packageName)
        val journal = refusBot.journal
        journal.awaitLoaded()
        // Navigateur : une fenêtre reconnue avec certitude n'est pas « à vérifier » (chaque site diffère).
        val changed = isNewInterface(settings, o.packageName, o.structureSignature) &&
            !BrowserPolicy.knownInBrowser(o.packageName, o.confidence)
        val reason = when {
            changed && o.status == OutcomeStatus.OBSERVED && settings.policyFor(o.packageName) == Policy.PRUDENT ->
                "fenêtre différente de celles déjà refusées dans cette appli : automatisation suspendue (profil Prudent), à vérifier dans l'onglet Compatibilité"
            else -> o.reason
        }
        val entry = LogEntry(
            time = System.currentTimeMillis(),
            packageName = o.packageName,
            appName = appName,
            status = o.status,
            togglesOff = o.togglesOff,
            reason = reason,
            platform = o.platform,
            confidence = o.confidence,
            actions = o.actions,
            signature = o.structureSignature,
            interfaceChanged = changed,
            consentDetected = o.consentDetected,
            controlsRecognized = o.controlsRecognized,
            actionAttempts = o.actionAttempts,
            actionExecutions = o.actionExecutions,
            blockedActions = o.blockedActions,
            stateVerified = o.stateVerified,
            captureTruncated = o.captureTruncated,
            timing = timing,
        )
        journal.add(entry)
        Log.i(TAG, "${entry.packageName} : ${entry.status.label}") // sans motif : il peut citer un libellé (N5)

        val s = settings
        val root = o.diagnosticRoot
        // Diagnostic automatique : seulement si l'option est activée, pour une fenêtre non résolue ou observée.
        val file = if (
            root != null &&
            s.autoDiagnostic &&
            !fr.refusbot.core.ConsentOrPay.isPaywall(o.reason) &&
            o.status in AUTO_DIAGNOSTIC_STATUSES &&
            diagnosticThrottle.tryAcquire(o.packageName, o.structureSignature, System.currentTimeMillis())
        ) {
            saveDiagnostic(
                root, o.packageName, appName,
                trigger = "automatique (${o.status.label})",
                reason = reason,
                captureTruncated = o.captureTruncated,
                timing = timing,
                controlIssues = o.controlIssues,
            )
        } else {
            null
        }
        Notifier.notifyOutcome(this, entry, s.notifyLevel, diagnosticSaved = file != null)
    }

    private suspend fun saveDiagnostic(
        root: UiNode,
        packageName: String,
        appName: String,
        trigger: String,
        reason: String?,
        captureTruncated: Boolean? = null,
        timing: SessionTiming.Summary? = null,
        controlIssues: List<ControlIssue> = emptyList(),
    ): File? {
        val meta = DiagnosticExport.Meta(
            packageName = packageName,
            appLabel = appName,
            capturedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            trigger = trigger,
            reason = reason,
            appVersion = appVersion(),
            rulesVersion = rules.version,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            captureTruncated = captureTruncated,
            timings = timing?.toMap(),
        )
        val json = DiagnosticExport.toJsonFitting(root, meta, engine.analyze(root, packageName), controlIssues = controlIssues)
        return try {
            refusBot.diagnostics.save(packageName, json)
        } catch (e: IOException) {
            Log.e(TAG, "Diagnostic non enregistré", e)
            null
        }
    }

    /** Capture manuelle (tuile « Diagnostic » ou bouton de l'appli), après [delayMs]. */
    fun requestCapture(delayMs: Long, trigger: String) {
        capturesInProgress++
        applyPackageFilter()
        lateinit var job: Job
        job = scope.launch(worker, start = CoroutineStart.LAZY) {
            try {
                delay(delayMs)
                captureNow(trigger)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    captureJobs.remove(job)
                    capturesInProgress--
                    applyPackageFilter()
                }
            }
        }
        captureJobs.add(job)
        job.start()
    }

    private suspend fun captureNow(trigger: String) {
        val capture = snapshotter.capture(settings.monitored)
        if (capture == null) {
            toast(getString(R.string.capture_not_monitored))
            return
        }
        val root = capture.screen.root
        val pkg = capture.screen.packageName
        val truncated = capture.screen.isTruncated
        capture.close()
        val appName = AppsRepository.label(this, pkg)
        if (saveDiagnostic(root, pkg, appName, trigger, reason = null, captureTruncated = truncated) != null) {
            toast(getString(R.string.capture_saved, appName))
        } else {
            toast(getString(R.string.capture_save_failed))
        }
    }

    /** Referme le volet des réglages rapides avant une capture (Android 12+). */
    fun collapseShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        }
    }

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(this@RefusBotAccessibilityService, message, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION") // getPackageInfo(String, Int) : suffisant pour lire sa propre version.
    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }

    /**
     * Interruption demandée par le système : plus aucune action ni capture tardive. L'analyse en
     * attente, la session en cours et les captures manuelles programmées sont toutes annulées.
     */
    override fun onInterrupt() {
        // Même traitement qu'une pause explicite : arrêt immédiat des opérations en attente,
        // puis journalisation de la session comme interrompue (jamais comme refus réussi).
        stopSession("service d'accessibilité interrompu par Android")
        captureJobs.toList().forEach { it.cancel() }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Arrêt du service (désactivé dans les réglages, ou arrêté par le système) : une session en
        // cours est interrompue ET journalisée (jamais perdue sans trace), puis on prévient, pour
        // que l'utilisateur sache que la protection n'est plus assurée.
        stopSession("service d'accessibilité arrêté par Android")
        val s = settings
        if (s.enabled && s.monitored.isNotEmpty()) Notifier.notifyServiceStopped(this, s.notifyLevel)
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (instance === this) instance = null
        _running.value = false
        scope.cancel()
    }

    companion object {
        private const val TAG = "RefusBot"
        private const val IDLE_STREAK_BEFORE_SLOWDOWN = 5
        private const val SLOW_DEBOUNCE_MS = 1_000L
        private const val SCROLL_STEP_DELAY_MS = 50L
        /** Lectures rapides (fenêtre de cookies seule) de suite au plus, avant une lecture complète. */
        private const val PARTIAL_READS_IN_A_ROW = 4
        private val AUTO_DIAGNOSTIC_STATUSES = setOf(
            OutcomeStatus.IMPOSSIBLE,
            OutcomeStatus.PARTIAL,
            OutcomeStatus.OBSERVED,
            OutcomeStatus.INCOMPLETE_CAPTURE,
            // Fenêtre laissée à l'utilisateur (« À terminer à la main ») : le moteur fournit sa capture,
            // mais elle n'était jamais enregistrée (audit du 22/11) ; utile pour les longues listes.
            OutcomeStatus.HANDED_OVER,
            // Seules les interruptions qui fournissent une capture (fenêtre disparue sans validation) :
            // une pause ou un changement d'appli n'en fournit jamais.
            OutcomeStatus.INTERRUPTED,
        )

        /** Paquet inexistant : filtre « aucune appli ». */
        const val NO_PACKAGE = "fr.cyrava.aucune.appli"

        private val _running = MutableStateFlow(false)

        /** Vrai quand le service est connecté (activé dans les réglages d'accessibilité et démarré). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        @Volatile
        var instance: RefusBotAccessibilityService? = null
            private set
    }
}
