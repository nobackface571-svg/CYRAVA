package fr.refusbot.core

/** Rectangle à l'écran, en pixels. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val area: Long get() = if (isEmpty) 0L else width.toLong() * height.toLong()

    fun contains(x: Int, y: Int): Boolean = !isEmpty && x >= left && x < right && y >= top && y < bottom

    companion object {
        val EMPTY = Bounds(0, 0, 0, 0)
    }
}

/**
 * Abstraction d'un nœud de l'arbre d'accessibilité, indépendante d'Android.
 *
 * Côté Android, l'arbre est capturé une fois par étape (instantané) puis relâché :
 * la logique de décision ne manipule jamais directement d'`AccessibilityNodeInfo`.
 */
interface UiNode {
    /** Identifiant unique dans l'instantané (ordre de parcours en profondeur). */
    val key: Int
    val className: String
    val text: String?
    val contentDescription: String?
    val viewId: String?
    val isClickable: Boolean
    /** Métadonnées neutres utiles au diagnostic des contrôles WebView. */
    val isFocusable: Boolean get() = false
    val isFocused: Boolean get() = false
    val isSelected: Boolean get() = false
    val accessibilityActions: List<Int> get() = emptyList()
    /** Clés d'extra-données annoncées par la source, sans lire le Bundle ni demander de payload. */
    val availableExtraData: List<String> get() = emptyList()
    val isCheckable: Boolean
    val isChecked: Boolean
    val isIndeterminate: Boolean
    val isEnabled: Boolean
    val isScrollable: Boolean
    val isPassword: Boolean
    /** Android 14+ : données explicitement marquées sensibles par l'application source. */
    val isAccessibilityDataSensitive: Boolean
    /** Vrai pour un nœud situé sous un champ ou un sous-arbre protégé. */
    val insideProtectedSubtree: Boolean get() = false
    /** Champ de saisie : son contenu (tapé par l'utilisateur) n'est jamais lu. */
    val isEditable: Boolean
    /** Section repliée qui accepte ACTION_EXPAND (accordéon de finalité, fiche fournisseur…). */
    val isExpandable: Boolean
    /** Description d'état fournie par l'appli (« coché », « activé »…), si elle existe (Android 11+). */
    val stateDescription: String?
    /** Description complémentaire Android 16 (diagnostic uniquement, jamais interprétée comme un état). */
    val supplementalDescription: String?
    /** Rôle exposé par une WebView (ex. « checkBox », « switch »), si disponible. */
    val webRole: String?
    /** Résumé des « extras » techniques d'un interrupteur (mode diagnostic uniquement). */
    val extrasSummary: String?
    val isVisibleToUser: Boolean
    val bounds: Bounds
    /** Une partie des enfants n'a pas été lue (limite de la capture atteinte à cet endroit). */
    val childrenOmitted: Boolean get() = false
    val parent: UiNode?
    val children: List<UiNode>
}

/** Implémentation simple et immuable (après construction) utilisée pour les instantanés et les tests. */
class SnapshotNode(
    override val className: String = "android.view.View",
    text: String? = null,
    contentDescription: String? = null,
    viewId: String? = null,
    override val isClickable: Boolean = false,
    override val isFocusable: Boolean = false,
    override val isFocused: Boolean = false,
    override val isSelected: Boolean = false,
    override val accessibilityActions: List<Int> = emptyList(),
    override val availableExtraData: List<String> = emptyList(),
    override val isCheckable: Boolean = false,
    override val isChecked: Boolean = false,
    override val isIndeterminate: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isScrollable: Boolean = false,
    override val isPassword: Boolean = false,
    override val isAccessibilityDataSensitive: Boolean = false,
    override val isVisibleToUser: Boolean = true,
    override val bounds: Bounds = Bounds.EMPTY,
    override val isEditable: Boolean = false,
    override val isExpandable: Boolean = false,
    stateDescription: String? = null,
    supplementalDescription: String? = null,
    webRole: String? = null,
    extrasSummary: String? = null,
) : UiNode {
    // Ne jamais conserver le contenu d'un champ mot de passe ni d'un champ de saisie : ni son
    // texte, ni sa description (certaines applis y recopient la valeur saisie), ni les textes de
    // ses enfants (champs composites).
    private var rawText: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else text
    private var rawDescription: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else contentDescription
    private var rawViewId: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else viewId
    private var rawStateDescription: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else stateDescription
    private var rawSupplementalDescription: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else supplementalDescription
    private var rawWebRole: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else webRole
    private var rawExtrasSummary: String? = if (isPassword || isEditable || isAccessibilityDataSensitive) null else extrasSummary

    /** Vrai si un ancêtre protège tout le sous-arbre (saisie, mot de passe ou donnée sensible). */
    override var insideProtectedSubtree: Boolean = false
        private set

    override val text: String? get() = if (insideProtectedSubtree) null else rawText
    override val contentDescription: String? get() = if (insideProtectedSubtree) null else rawDescription
    override val viewId: String? get() = if (insideProtectedSubtree) null else rawViewId
    override val stateDescription: String? get() = if (insideProtectedSubtree) null else rawStateDescription
    override val supplementalDescription: String? get() = if (insideProtectedSubtree) null else rawSupplementalDescription
    override val webRole: String? get() = if (insideProtectedSubtree) null else rawWebRole
    override val extrasSummary: String? get() = if (insideProtectedSubtree) null else rawExtrasSummary

    override var key: Int = -1
        private set

    /** Posé par la capture quand elle a dû s'arrêter avant le dernier enfant de ce nœud. */
    override var childrenOmitted: Boolean = false
    override var parent: UiNode? = null
        private set
    private val mutableChildren = ArrayList<UiNode>(0)
    override val children: List<UiNode> get() = mutableChildren

    fun addChild(child: SnapshotNode): SnapshotNode {
        child.parent = this
        mutableChildren.add(child)
        if (isPassword || isEditable || isAccessibilityDataSensitive || insideProtectedSubtree) child.markInsideProtectedSubtree()
        return child
    }

    /** L'arbre se construit des feuilles vers la racine : le marquage descend dans tout le sous-arbre. */
    private fun markInsideProtectedSubtree() {
        if (insideProtectedSubtree) return
        insideProtectedSubtree = true
        rawText = null
        rawDescription = null
        rawViewId = null
        rawStateDescription = null
        rawSupplementalDescription = null
        rawWebRole = null
        rawExtrasSummary = null
        mutableChildren.forEach { (it as SnapshotNode).markInsideProtectedSubtree() }
    }

    /** Attribue les clés en parcours préfixe. À appeler une fois l'arbre construit. */
    fun assignKeys(): SnapshotNode {
        var next = 0
        fun visit(n: SnapshotNode) {
            n.key = next++
            n.mutableChildren.forEach { visit(it as SnapshotNode) }
        }
        visit(this)
        return this
    }

    override fun toString(): String =
        "SnapshotNode(#$key ${className.substringAfterLast('.')} text=$text desc=$contentDescription " +
            "checked=$isChecked clickable=$isClickable)"
}

/** Parcours en profondeur (préfixe) de tout le sous-arbre, nœud courant inclus. */
fun UiNode.walk(): Sequence<UiNode> = sequence {
    val stack = ArrayDeque<UiNode>()
    stack.addLast(this@walk)
    while (stack.isNotEmpty()) {
        val n = stack.removeLast()
        yield(n)
        for (i in n.children.indices.reversed()) stack.addLast(n.children[i])
    }
}

fun UiNode.ancestors(): Sequence<UiNode> = generateSequence(parent) { it.parent }

fun UiNode.isDescendantOf(other: UiNode): Boolean = ancestors().any { it === other }

/** Textes bruts (texte puis description) d'un nœud, hors champs mot de passe. */
fun UiNode.ownTexts(): List<String> {
    if (isPassword || isEditable || isAccessibilityDataSensitive || insideProtectedSubtree) return emptyList()
    val out = ArrayList<String>(2)
    text?.takeIf { it.isNotBlank() }?.let { out.add(it) }
    contentDescription?.takeIf { it.isNotBlank() && it != text }?.let { out.add(it) }
    return out
}

/** Tous les textes du sous-arbre (pour le garde-fou et le contexte d'une ligne). */
fun UiNode.subtreeTexts(): List<String> = walk().flatMap { it.ownTexts().asSequence() }.toList()
