package fr.refusbot.core

import java.io.File

/** Charge le vrai `app/src/main/assets/rules.json` (chemin fourni par Gradle, sinon chemins relatifs). */
object TestRules {
    val rules: Rules by lazy { Rules.fromJson(file().readText()) }

    fun file(): File {
        val candidates = listOfNotNull(
            System.getProperty("refusbot.rules"),
            "../app/src/main/assets/rules.json",
            "app/src/main/assets/rules.json",
        ).map(::File)
        return candidates.firstOrNull { it.isFile }
            ?: error("rules.json introuvable (essayé : ${candidates.joinToString { it.absolutePath }})")
    }
}

/** Petit DSL pour construire des arbres d'accessibilité simulés. */
object Tree {
    private var nextTop = 0

    /** Positions déterministes : à appeler avant chaque rendu. */
    fun reset() {
        nextTop = 0
    }

    fun node(
        cls: String = "android.view.View",
        text: String? = null,
        desc: String? = null,
        clickable: Boolean = false,
        checkable: Boolean = false,
        checked: Boolean = false,
        enabled: Boolean = true,
        scrollable: Boolean = false,
        visible: Boolean = true,
        password: Boolean = false,
        viewId: String? = null,
        bounds: Bounds? = null,
        editable: Boolean = false,
        expandable: Boolean = false,
        children: List<SnapshotNode> = emptyList(),
    ): SnapshotNode {
        val b = bounds ?: if (visible) Bounds(0, nextTop, 1000, (nextTop + 80).also { nextTop = it }) else Bounds(0, 5000, 1000, 5080)
        val n = SnapshotNode(
            className = cls, text = text, contentDescription = desc, viewId = viewId,
            isClickable = clickable, isCheckable = checkable, isChecked = checked, isEnabled = enabled,
            isScrollable = scrollable, isPassword = password, isVisibleToUser = visible, bounds = b,
            isEditable = editable, isExpandable = expandable,
        )
        children.forEach { n.addChild(it) }
        return n
    }

    fun text(s: String, visible: Boolean = true) = node("android.widget.TextView", text = s, visible = visible)
    fun button(s: String, visible: Boolean = true, enabled: Boolean = true) =
        node("android.widget.Button", text = s, clickable = true, visible = visible, enabled = enabled)

    fun switch(label: String?, checked: Boolean, enabled: Boolean = true, visible: Boolean = true) =
        node("android.widget.Switch", text = label, checkable = true, checked = checked, clickable = true, enabled = enabled, visible = visible)

    fun group(vararg children: SnapshotNode, cls: String = "android.widget.LinearLayout", scrollable: Boolean = false) =
        node(cls, scrollable = scrollable, children = children.toList())

    fun window(vararg children: SnapshotNode): SnapshotNode =
        node("android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 2400), children = children.toList()).assignKeys()
}

class FakeClock(var now: Long = 1_000_000L) {
    fun advance(ms: Long) {
        now += ms
    }
}

/**
 * Appli simulée : produit un arbre à chaque appel de [render] et réagit aux opérations.
 * Chaque nœud cliquable peut être associé à une action via [onClick].
 */
abstract class FakeApp(val packageName: String = "com.example.jeu") {
    private val handlers = HashMap<UiNode, () -> Unit>()
    private val expandHandlers = HashMap<UiNode, () -> Unit>()
    val expandedLabels = mutableListOf<String>()
    val clickedLabels = mutableListOf<String>()
    var opsCount = 0
        private set

    protected fun SnapshotNode.onClick(action: () -> Unit): SnapshotNode {
        handlers[this] = action
        return this
    }

    protected fun SnapshotNode.onExpand(action: () -> Unit): SnapshotNode {
        expandHandlers[this] = action
        return this
    }

    protected abstract fun build(): SnapshotNode
    open fun onScroll(container: UiNode) = Unit

    /** Balayage à l'écran (geste), utilisé quand aucun conteneur défilant n'est identifié. */
    open fun onSwipe() = Unit

    /** ACTION_SHOW_ON_SCREEN : faire apparaître ce nœud (bouton de validation en bas de liste). */
    open fun onShowOnScreen(node: UiNode) = Unit

    /** Vrai si l'exécuteur doit déclarer le défilement bloqué (le service Android le fait). */
    open fun scrollIsBlocked(container: UiNode): Boolean = false

    fun render(): SnapshotNode {
        handlers.clear()
        expandHandlers.clear()
        Tree.reset()
        return build()
    }

    fun perform(op: Op) {
        opsCount++
        when (op) {
            is Op.Click -> {
                val node = when (val t = op.target) {
                    is ClickTarget.Node -> t.node
                    is ClickTarget.Point -> t.node
                }
                // Garde-fou de dernier niveau, comme dans le service Android.
                check(SafetyGuard(TestRules.rules).checkNode(op.target.guardNode, op.selectionConfirm).isAllowed) {
                    "clic non autorisé sur ${op.target.guardNode}"
                }
                clickedLabels.add(TextNormalizer.canonical(node.text ?: node.contentDescription ?: op.label))
                val handler = generateSequence(node) { it.parent }.firstNotNullOfOrNull { handlers[it] }
                handler?.invoke()
            }
            is Op.Expand -> {
                expandedLabels.add(op.label)
                expandHandlers[op.node]?.invoke()
            }
            is Op.Scroll -> onScroll(op.container)
            is Op.Swipe -> onSwipe()
            is Op.ShowOnScreen -> onShowOnScreen(op.node)
        }
    }
}

data class RunResult(val outcome: Outcome?, val steps: Int, val acts: List<Step.Act>)

fun runEngine(app: FakeApp, engine: RefusalEngine, clock: FakeClock, maxSteps: Int = 1_000): RunResult {
    val acts = mutableListOf<Step.Act>()
    repeat(maxSteps) { i ->
        when (val step = engine.next(Screen(app.packageName, app.render()))) {
            Step.Idle -> return RunResult(null, i, acts)
            is Step.Wait -> clock.advance(step.delayMs)
            is Step.Act -> {
                acts.add(step)
                step.ops.forEach(app::perform)
                if (step.ops.any { it is Op.Scroll && app.scrollIsBlocked(it.container) }) engine.markNavigationBlocked()
                clock.advance(250)
            }
            is Step.Done -> return RunResult(step.outcome, i, acts)
        }
    }
    error("le moteur ne s'est pas arrêté après $maxSteps étapes")
}
