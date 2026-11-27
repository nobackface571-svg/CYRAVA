package fr.refusbot.core.lab

import fr.refusbot.core.Bounds
import fr.refusbot.core.ClickTarget
import fr.refusbot.core.Op
import fr.refusbot.core.Outcome
import fr.refusbot.core.OutcomeStatus
import fr.refusbot.core.Policy
import fr.refusbot.core.RefusalEngine
import fr.refusbot.core.Rules
import fr.refusbot.core.SafetyGuard
import fr.refusbot.core.Screen
import fr.refusbot.core.SnapshotNode
import fr.refusbot.core.Step
import fr.refusbot.core.TextNormalizer
import fr.refusbot.core.UiNode

/**
 * LABORATOIRE (mode simulation) : fausses fenêtres de consentement sur lesquelles le vrai moteur
 * est exécuté. Aucune appli réelle n'est touchée : c'est une démonstration des décisions que
 * RefusBot prendrait, jamais un refus réel.
 */
abstract class SimulatedApp(val packageName: String = "fr.refusbot.laboratoire") {
    /** Permet de simuler une capture Android incomplète dans le laboratoire. */
    open val isTruncated: Boolean = false

    private val handlers = HashMap<UiNode, () -> Unit>()
    private val expandHandlers = HashMap<UiNode, () -> Unit>()
    private var nextTop = 0

    /** Vrai si la fausse appli a enregistré une acceptation (ne doit jamais arriver). */
    var accepted = false
        protected set

    /** État final lisible (« intérêt légitime actif : 0/27 »…). */
    abstract fun state(): String

    protected abstract fun build(): SnapshotNode

    open fun onScroll() = Unit

    fun render(): SnapshotNode {
        handlers.clear()
        expandHandlers.clear()
        nextTop = 0
        return build()
    }

    /** Exécute une opération et décrit ce qui s'est passé. */
    fun perform(op: Op, guard: SafetyGuard): String = when (op) {
        is Op.Click -> {
            val node = op.target.guardNode
            if (!guard.checkNode(node, op.selectionConfirm).isAllowed) {
                "BLOQUÉ par le garde-fou : « ${op.label} »"
            } else {
                val handler = generateSequence(node) { it.parent }.firstNotNullOfOrNull { handlers[it] }
                handler?.invoke()
                val how = if (op.target is ClickTarget.Point) "tap" else "clic"
                if (op.isToggle) "$how : décocher « ${op.label} »" else "$how : « ${op.label} »"
            }
        }
        is Op.Expand -> {
            when (val verdict = guard.checkNode(op.node)) {
                is SafetyGuard.Verdict.Blocked -> "BLOQUÉ par le garde-fou : ${verdict.reason}"
                SafetyGuard.Verdict.Allowed -> {
                    expandHandlers[op.node]?.invoke()
                    "déplier « ${op.label.take(40)} »"
                }
            }
        }
        is Op.Scroll -> {
            onScroll()
            "défilement"
        }
        is Op.Swipe -> {
            onScroll()
            "balayage"
        }
        is Op.ShowOnScreen -> "aller au bouton « ${op.label.take(40)} »"
    }

    // --- Construction d'arbres fictifs --------------------------------------------------------

    private fun node(
        cls: String,
        text: String? = null,
        clickable: Boolean = false,
        checkable: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        scrollable: Boolean = false,
        expandable: Boolean = false,
        viewId: String? = null,
        children: List<SnapshotNode> = emptyList(),
    ): SnapshotNode {
        val top = nextTop
        nextTop += 80
        val n = SnapshotNode(
            className = cls, text = text, viewId = viewId, isClickable = clickable, isCheckable = checkable,
            isChecked = checked, isEnabled = enabled, isScrollable = scrollable, bounds = Bounds(0, top, 1080, top + 80),
            isExpandable = expandable,
        )
        children.forEach { n.addChild(it) }
        return n
    }

    protected fun text(s: String) = node("android.widget.TextView", text = s)

    protected fun button(s: String, onClick: () -> Unit = {}) =
        node("android.widget.Button", text = s, clickable = true).also { handlers[it] = onClick }

    protected fun switch(label: String, checked: Boolean, enabled: Boolean = true, onToggle: () -> Unit = {}) =
        node("android.widget.Switch", text = label, clickable = true, checkable = true, checked = checked, enabled = enabled)
            .also { handlers[it] = onToggle }

    protected fun section(title: String, expanded: Boolean, onExpand: () -> Unit) =
        node("android.widget.Button", text = title, clickable = true, expandable = !expanded).also { expandHandlers[it] = onExpand }

    protected fun group(vararg c: SnapshotNode, scrollable: Boolean = false, viewId: String? = null) = node(
        if (scrollable) "androidx.recyclerview.widget.RecyclerView" else "android.widget.LinearLayout",
        scrollable = scrollable, viewId = viewId, children = c.toList(),
    )

    /** Conteneur cliquable (ex. un « bouton » composé de plusieurs textes). */
    protected fun clickableGroup(vararg c: SnapshotNode, onClick: () -> Unit) =
        node("android.widget.LinearLayout", clickable = true, children = c.toList()).also { handlers[it] = onClick }

    protected fun window(vararg c: SnapshotNode): SnapshotNode =
        node("android.widget.FrameLayout", children = c.toList()).assignKeys()

    protected fun gameScreen() = window(text("Écran du jeu"), button("Jouer"))
}

data class LabScenario(
    val id: String,
    val title: String,
    val description: String,
    /** Résultats acceptables pour ce scénario (vérifiés par les tests). */
    val expected: Set<OutcomeStatus>,
    val create: () -> SimulatedApp,
)

enum class LabTermination {
    /** Le moteur a produit un résultat terminal. */
    COMPLETED,
    /** Le plafond de temps simulé a été atteint avant un résultat terminal. */
    TIME_LIMIT,
    /** Le moteur n'a plus d'action à effectuer sans avoir produit de résultat. */
    IDLE,
    /** Le plafond de sécurité a été atteint : résultat incomplet, jamais assimilé à un succès. */
    ITERATION_LIMIT,
}

data class LabReport(
    val scenario: LabScenario,
    val steps: List<String>,
    val outcome: Outcome?,
    val finalState: String,
    val accepted: Boolean,
    val termination: LabTermination = LabTermination.COMPLETED,
) {
    val asExpected: Boolean
        get() = !accepted && termination == LabTermination.COMPLETED &&
            outcome != null && outcome.status in scenario.expected
}

object LabRunner {
    fun run(scenario: LabScenario, rules: Rules, policy: Policy = Policy.STRICT): LabReport {
        val app = scenario.create()
        var now = 0L
        val startedAt = now
        val maxSimulatedDurationMs = rules.limits.timeoutMs + 5_000L
        val engine = RefusalEngine(rules) { now }
        val guard = SafetyGuard(rules)
        val steps = ArrayList<String>()
        var outcome: Outcome? = null
        var termination = LabTermination.ITERATION_LIMIT
        loop@ for (i in 0 until 2_000) {
            if (now - startedAt > maxSimulatedDurationMs) {
                termination = LabTermination.TIME_LIMIT
                break@loop
            }
            when (val step = engine.next(Screen(app.packageName, app.render(), isTruncated = app.isTruncated)) { policy }) {
                Step.Idle -> {
                    termination = LabTermination.IDLE
                    break@loop
                }
                is Step.Wait -> now += step.delayMs
                is Step.Act -> {
                    val done = step.ops.map { app.perform(it, guard) }
                    steps += if (done.size <= 3) done.joinToString(" ; ") else "${step.description} (${done.size} opérations)"
                    now += 300
                }
                is Step.Done -> {
                    outcome = step.outcome
                    termination = LabTermination.COMPLETED
                    break@loop
                }
            }
        }
        return LabReport(scenario, steps, outcome, app.state(), app.accepted, termination)
    }

    /**
     * Exécute un scénario sans masquer les erreurs techniques.
     * L'annulation du travail doit toujours remonter à l'appelant.
     */
    fun runSafely(scenario: LabScenario, rules: Rules, policy: Policy = Policy.STRICT): LabExecutionResult =
        try {
            LabExecutionResult.Success(run(scenario, rules, policy))
        } catch (cancelled: java.util.concurrent.CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            LabExecutionResult.Failure(
                scenario = scenario,
                errorType = exception::class.simpleName ?: "Exception",
            )
        }
}

/**
 * Résultat explicite d'une exécution du laboratoire.
 * Une erreur de scénario reste visible au lieu de disparaître silencieusement.
 */
sealed interface LabExecutionResult {
    data class Success(val report: LabReport) : LabExecutionResult
    data class Failure(val scenario: LabScenario, val errorType: String) : LabExecutionResult
}

/** Scénarios fictifs du laboratoire. */
object LabScenarios {

    val all: List<LabScenario> by lazy {
        listOf(
            LabScenario(
                "bandeau", "Bandeau avec « Tout refuser »",
                "Bandeau simple : « Tout accepter », « Paramètres », « Tout refuser ».",
                setOf(OutcomeStatus.UNVERIFIED),
            ) { Banner() },
            LabScenario(
                "ump", "Fixture CMP multi-pages (simulation pédagogique, pas Google UMP)",
                "Scénario synthétique inspiré d'une interface de consentement : « Gérer les options », consentement désactivé, intérêt légitime activé sur 27 lignes réparties sur 3 pages. Ce scénario n'utilise pas le SDK Google UMP et ne prouve rien sur son interface réelle.",
                setOf(OutcomeStatus.OPTIONS_SUCCESS),
            ) { Ump() },
            LabScenario(
                "accepter-seul", "Aucun refus possible",
                "Seuls « Tout accepter » et « OK » existent : RefusBot ne doit rien toucher.",
                setOf(OutcomeStatus.IMPOSSIBLE),
            ) { AcceptOnly() },
            LabScenario(
                "accordeons", "Catégories repliées (style OneTrust)",
                "Les fournisseurs à intérêt légitime sont cachés dans des sections repliées, sans refus global : CYRAVA ne déplie jamais une section, ne valide pas et ne clique sur aucune acceptation.",
                setOf(OutcomeStatus.IMPOSSIBLE),
            ) { Accordion() },
            LabScenario(
                "piege", "Piège : refus et acceptation dans le même bouton",
                "Un seul bouton contient « Refuser » et « Accepter les cookies » : le garde-fou doit bloquer.",
                setOf(OutcomeStatus.IMPOSSIBLE),
            ) { Trap() },
            LabScenario(
                "allemand", "Bandeau en allemand",
                "« Alle akzeptieren » / « Alle ablehnen ».",
                setOf(OutcomeStatus.UNVERIFIED),
            ) { German() },
            LabScenario(
                "aria-unknown", "Contrôle ARIA à état inconnu",
                "Un interrupteur non cochable avec « Off » reste inconnu ; la validation est bloquée.",
                setOf(OutcomeStatus.IMPOSSIBLE),
            ) { AriaUnknown() },
            LabScenario(
                "capture-tronquee", "Capture de consentement tronquée",
                "Sans refus direct visible, une capture incomplète n'autorise aucune action.",
                setOf(OutcomeStatus.INCOMPLETE_CAPTURE),
            ) { Truncated() },
        )
    }

    private class Banner : SimulatedApp() {
        var refused = false
        override fun state() = if (refused) "refus enregistré" else if (accepted) "ACCEPTÉ" else "fenêtre encore affichée"
        override fun build(): SnapshotNode = if (refused || accepted) gameScreen() else window(
            text("Nous utilisons des cookies et nos partenaires traitent vos données."),
            button("Tout accepter") { accepted = true },
            button("Paramètres"),
            button("Tout refuser") { refused = true },
        )
    }

    private class Ump : SimulatedApp() {
        var options = false
        var confirmed = false
        var offset = 0
        val consent = BooleanArray(27)
        val li = BooleanArray(27) { true }
        override fun state() = "intérêt légitime actif : ${li.count { it }}/27, consentement actif : ${consent.count { it }}/27" +
            (if (confirmed) ", choix confirmés" else "") + (if (accepted) ", ACCEPTÉ" else "")

        override fun onScroll() {
            if (options && offset + 9 < 27) offset += 9
        }

        override fun build(): SnapshotNode = when {
            confirmed || accepted -> gameScreen()
            !options -> window(
                text("Jeu demande votre consentement pour utiliser vos données personnelles"),
                text("Nos partenaires pourront stocker des informations sur votre appareil."),
                button("Autoriser") { accepted = true },
                button("Gérer les options") { options = true },
            )
            else -> window(
                text("Préférences concernant les fournisseurs"),
                text("Ces fournisseurs invoquent un intérêt légitime."),
                group(
                    *(offset until offset + 9).map { i ->
                        group(
                            text("Fournisseur ${i + 1}"),
                            switch("Consentement", consent[i]) { consent[i] = !consent[i] },
                            switch("Intérêt légitime", li[i]) { li[i] = !li[i] },
                        )
                    }.toTypedArray(),
                    scrollable = true,
                ),
                button("Tout accepter") { accepted = true },
                button("Confirmer les choix") { confirmed = true },
            )
        }
    }

    private class AcceptOnly : SimulatedApp() {
        override fun state() = if (accepted) "ACCEPTÉ" else "rien n'a été touché"
        override fun build(): SnapshotNode = if (accepted) gameScreen() else window(
            text("Nous utilisons des cookies et partageons vos données avec nos partenaires."),
            button("Tout accepter") { accepted = true },
            button("OK") { accepted = true },
        )
    }

    private class Accordion : SimulatedApp() {
        var options = false
        var saved = false
        val open = BooleanArray(3)
        val li = Array(3) { BooleanArray(4) { true } }
        override fun state() = "intérêt légitime actif : ${li.sumOf { r -> r.count { it } }}/12" +
            (if (saved) ", choix enregistrés" else "") + (if (accepted) ", ACCEPTÉ" else "")

        override fun build(): SnapshotNode = when {
            saved || accepted -> gameScreen()
            !options -> window(
                text("We use cookies and process your personal data with our partners."),
                button("Accept All Cookies") { accepted = true },
                button("Cookies Settings") { options = true },
            )
            else -> window(
                text("Privacy Preference Center"),
                text("Our vendors rely on legitimate interest."),
                group(
                    *(0 until 3).flatMap { c ->
                        val rows = if (open[c]) {
                            (0 until 4).map { v ->
                                group(text("Vendor ${c + 1}-${v + 1}"), switch("Legitimate Interest", li[c][v]) { li[c][v] = !li[c][v] })
                            }
                        } else {
                            emptyList()
                        }
                        listOf(section("Purpose ${c + 1}", open[c]) { open[c] = true }) + rows
                    }.toTypedArray(),
                ),
                button("Allow All") { accepted = true },
                button("Confirm My Choices") { saved = true },
            )
        }
    }

    private class Trap : SimulatedApp() {
        override fun state() = if (accepted) "ACCEPTÉ" else "rien n'a été touché"
        override fun build(): SnapshotNode = if (accepted) gameScreen() else window(
            text("Nous et nos partenaires utilisons des cookies pour vos données."),
            // Un seul élément cliquable contient « Refuser » ET « Accepter les cookies » : le toucher accepterait.
            clickableGroup(text("Refuser"), text("Accepter les cookies")) { accepted = true },
        )
    }

    private class German : SimulatedApp() {
        var refused = false
        override fun state() = if (refused) "refus enregistré" else if (accepted) "ACCEPTÉ" else "fenêtre encore affichée"
        override fun build(): SnapshotNode = if (refused || accepted) gameScreen() else window(
            text("Wir verwenden Cookies. Unsere Partner verarbeiten Ihre Daten."),
            button("Alle akzeptieren") { accepted = true },
            button("Alle ablehnen") { refused = true },
        )
    }

    private class AriaUnknown : SimulatedApp() {
        var options = false
        var confirmed = false
        override fun state() = if (confirmed) "validation envoyée malgré l'état inconnu" else "validation bloquée ; état ARIA inconnu"
        override fun build(): SnapshotNode = if (!options) {
            window(
                text("Nous utilisons des cookies avec nos partenaires."),
                text("Vos données personnelles"),
                button("Tout accepter") { accepted = true },
                button("Gérer les options") { options = true },
            )
        } else {
            window(
                text("Préférences de cookies et intérêt légitime"),
                SnapshotNode(
                    className = "android.widget.ToggleButton",
                    text = "Intérêt légitime",
                    isClickable = true,
                    isCheckable = false,
                    isChecked = false,
                    stateDescription = "Off",
                ),
                button("Confirmer les choix") { confirmed = true },
            )
        }
    }

    private class Truncated : SimulatedApp() {
        override val isTruncated = true
        override fun state() = "capture incomplète ; aucune action d'options effectuée"
        override fun build(): SnapshotNode = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Gérer les options"),
            button("Tout accepter") { accepted = true },
        )
    }

    /** Libellé canonique (utilitaire pour les tests du laboratoire). */
    fun canonical(s: String) = TextNormalizer.canonical(s)
}
