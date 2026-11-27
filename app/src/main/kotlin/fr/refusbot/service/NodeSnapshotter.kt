package fr.refusbot.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import fr.refusbot.core.Bounds
import fr.refusbot.core.BrowserPolicy
import fr.refusbot.core.Rules
import fr.refusbot.core.Screen
import fr.refusbot.core.SnapshotNode
import fr.refusbot.core.SystemPackagePolicy
import fr.refusbot.core.TargetCheck
import fr.refusbot.core.UiNode

/**
 * Instantané de la fenêtre d'une appli surveillée.
 *
 * L'arbre [screen] est une copie en Kotlin pur ; les `AccessibilityNodeInfo` d'origine sont gardés
 * à côté (même index que [UiNode.key]) le temps d'exécuter les actions, puis relâchés par [close].
 */
class Capture(val screen: Screen, private val infos: List<AccessibilityNodeInfo>) : AutoCloseable {
    private var closed = false

    fun info(node: UiNode): AccessibilityNodeInfo? = if (closed) null else infos.getOrNull(node.key)

    override fun close() {
        if (closed) return
        closed = true
        // Avant Android 13, les nœuds doivent être recyclés explicitement.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            infos.forEach { it.recycleCompat() }
        }
    }
}

/**
 * État coché, d'après l'API Android explicite : getChecked() (Android 16+) ou isChecked().
 * La description d'état est conservée à part ; elle ne remplace jamais un état Android absent.
 */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.isCheckedCompat(): Boolean {
    if (!isCheckable) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && checked == AccessibilityNodeInfo.CHECKED_STATE_PARTIAL) {
        return false
    }
    val fromApi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
    } else {
        isChecked
    }
    // isChecked/getChecked est la source normative quand isCheckable est vrai.
    // Une stateDescription contradictoire ou obsolète ne doit jamais transformer un OFF en ON
    // (ni l'inverse) : elle est conservée séparément pour le diagnostic et l'analyse de cohérence.
    return fromApi
}

@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.isIndeterminateCompat(rules: Rules, stateDescription: String?): Boolean =
    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && checked == AccessibilityNodeInfo.CHECKED_STATE_PARTIAL) ||
        rules.isIndeterminateDescription(stateDescription)

/** API 34+ : une donnée explicitement sensible ne doit pas être lue, analysée ni actionnée. */
internal fun AccessibilityNodeInfo.isAccessibilityDataSensitiveCompat(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && isAccessibilityDataSensitive

@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        try {
            recycle()
        } catch (_: IllegalStateException) {
            // Déjà recyclé.
        }
    }
}

/** Copie indépendante d'un nœud (gardée d'une étape à l'autre, rafraîchie par [AccessibilityNodeInfo.refresh]). */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.copyCompat(): AccessibilityNodeInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AccessibilityNodeInfo(this) else AccessibilityNodeInfo.obtain(this)

@Suppress("DEPRECATION")
private fun AccessibilityWindowInfo.recycleCompat() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recycle()
}

class NodeSnapshotter(private val service: AccessibilityService, private val rules: Rules) {

    /**
     * Capture la fenêtre active si elle appartient à une appli de [allowed] ; sinon la fenêtre
     * applicative surveillée la plus haute. Les autres applis ne sont jamais lues.
     */
    fun capture(allowed: Set<String>): Capture? {
        val permitted = allowed.filterNot(SystemPackagePolicy::shouldIgnore).toSet()
        if (permitted.isEmpty()) return null
        service.rootInActiveWindow?.let { root ->
            val pkg = root.packageName?.toString()
            if (pkg != null && pkg in permitted) {
                // Bulle d'aide ou menu de la même appli au premier plan (Firefox : « Secouez votre
                // appareil… ») : lire la vraie page, sa plus grande fenêtre. Une vraie fenêtre de
                // consentement en dialogue est grande et reste prioritaire.
                val larger = largestAppWindowRoot(pkg, excludeWindowId = root.windowId)
                if (larger != null) {
                    if (TargetCheck.preferLargerWindow(areaOf(root), areaOf(larger))) {
                        root.recycleCompat()
                        return build(larger, pkg)
                    }
                    larger.recycleCompat()
                }
                return build(root, pkg)
            }
            root.recycleCompat()
        }
        val windows = try {
            service.windows
        } catch (_: RuntimeException) {
            emptyList()
        }
        try {
            for (w in windows.sortedByDescending { it.layer }) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val root = w.root ?: continue
                val pkg = root.packageName?.toString()
                if (pkg != null && pkg in permitted) return build(root, pkg)
                root.recycleCompat()
            }
        } finally {
            windows.forEach { it.recycleCompat() }
        }
        return null
    }

    /**
     * Lecture rapide (22/11 au soir) : relit SEULEMENT la fenêtre de cookies [panel] repérée à la
     * dernière lecture complète : la chaîne de ses ancêtres (sans leurs autres enfants), puis son
     * contenu entier. Sur une page de navigateur, la page derrière (des milliers d'éléments) n'est
     * plus relue à chaque écran d'une longue liste.
     *
     * Mode prudent : null (le service fait alors une lecture complète) si le panneau a disparu, n'est
     * plus visible, n'appartient plus à une appli surveillée, si sa fenêtre n'est plus la fenêtre
     * active, ou si un ancêtre est un champ protégé. Le moteur vérifie en plus qu'il retrouve
     * exactement ce panneau ([fr.refusbot.core.Screen.partialTopKey]), sinon il redemande tout.
     */
    fun capturePanel(panel: AccessibilityNodeInfo, allowed: Set<String>): Capture? {
        val permitted = allowed.filterNot(SystemPackagePolicy::shouldIgnore).toSet()
        val alive = try {
            panel.refresh()
        } catch (_: RuntimeException) {
            false
        }
        if (!alive || !panel.isVisibleToUser) return null
        val pkg = panel.packageName?.toString() ?: return null
        if (pkg !in permitted) return null
        val active = service.rootInActiveWindow ?: return null
        val sameWindow = active.windowId == panel.windowId && active.packageName?.toString() == pkg
        active.recycleCompat()
        if (!sameWindow) return null
        val chain = ArrayList<AccessibilityNodeInfo>()
        var p = panel.parent
        while (p != null) {
            chain.add(p)
            if (chain.size > MAX_DEPTH) {
                chain.forEach { it.recycleCompat() }
                return null
            }
            p = p.parent
        }
        chain.reverse()
        // Copie du panneau : la capture relâche ses nœuds à la fin de l'étape, le service garde le sien.
        val path = chain + panel.copyCompat()
        return build(path.first(), pkg, path)
    }

    /** Racine de la plus grande fenêtre applicative de [packageName] (hors [excludeWindowId]), ou null. */
    private fun largestAppWindowRoot(packageName: String, excludeWindowId: Int): AccessibilityNodeInfo? {
        val windows = try {
            service.windows
        } catch (_: RuntimeException) {
            emptyList()
        }
        var best: AccessibilityNodeInfo? = null
        var bestArea = -1L
        try {
            for (w in windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION || w.id == excludeWindowId) continue
                val root = w.root ?: continue
                if (root.packageName?.toString() != packageName) {
                    root.recycleCompat()
                    continue
                }
                val area = areaOf(root)
                if (area > bestArea) {
                    best?.recycleCompat()
                    best = root
                    bestArea = area
                } else {
                    root.recycleCompat()
                }
            }
        } finally {
            windows.forEach { it.recycleCompat() }
        }
        return best
    }

    private fun areaOf(node: AccessibilityNodeInfo): Long {
        val r = Rect()
        node.getBoundsInScreen(r)
        return Bounds(r.left, r.top, r.right, r.bottom).area
    }

    /**
     * @param path lecture partielle : la racine, les ancêtres du panneau, puis le panneau (dernier).
     * Seul l'enfant suivant du chemin est lu sous chaque ancêtre ; le panneau est lu en entier.
     * Vide : lecture complète.
     */
    @Suppress("DEPRECATION") // isChecked : remplacé par getChecked() en API 36, conservé pour minSdk 26.
    private fun build(rootInfo: AccessibilityNodeInfo, packageName: String, path: List<AccessibilityNodeInfo> = emptyList()): Capture? {
        val infos = ArrayList<AccessibilityNodeInfo>(256)
        val rect = Rect()
        var truncated = false
        // Un ancêtre du panneau protégé (saisie, mot de passe, donnée sensible) : lecture partielle refusée.
        var pathBroken = false

        fun convert(info: AccessibilityNodeInfo, depth: Int, parentInWeb: Boolean): SnapshotNode {
            infos.add(info)
            val accessibilityDataSensitive = info.isAccessibilityDataSensitiveCompat()
            if (accessibilityDataSensitive) {
                if (depth < path.size - 1) pathBroken = true
                // N'inspecter aucun autre attribut ni enfant d'un nœud explicitement sensible.
                return SnapshotNode(
                    className = "android.view.View",
                    isAccessibilityDataSensitive = true,
                    isVisibleToUser = false,
                    bounds = Bounds.EMPTY,
                )
            }
            val password = info.isPassword
            val editable = info.isEditable
            // Contre-audit M2 : la barre d'adresse d'un navigateur (adresse du site visité) n'entre
            // JAMAIS dans l'instantané, donc jamais dans un diagnostic. Le contenu des pages (sous une
            // WebView) n'est pas concerné.
            val addressBar = !parentInWeb && BrowserPolicy.isAddressBar(packageName, info.viewIdResourceName)
            val protected = password || editable || addressBar
            val inWeb = parentInWeb || info.className?.toString().orEmpty().contains("WebView")
            if (protected) rect.setEmpty() else info.getBoundsInScreen(rect)
            val stateDescription = if (!protected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.stateDescription?.toString()
            } else null
            // API 36 : information complémentaire d'accessibilité, conservée pour diagnostic seulement.
            val supplementalDescription = if (!protected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                info.supplementalDescription?.toString()
            } else null
            // Aucun Bundle extras n'est lu : get() / getCharSequence() peuvent désérialiser un
            // Parcelable fourni par l'application source. Les rôles ARIA non exposés comme contrôles
            // cochables restent inconnus et ne seront jamais actionnés.
            val node = SnapshotNode(
                className = info.className?.toString().orEmpty(),
                text = if (protected) null else info.text?.toString(),
                contentDescription = if (protected) null else info.contentDescription?.toString(),
                viewId = if (protected) null else info.viewIdResourceName,
                isClickable = info.isClickable,
                isFocusable = info.isFocusable,
                isFocused = info.isFocused,
                isSelected = info.isSelected,
                accessibilityActions = info.actionList.map { it.id }.distinct().sorted(),
                // Liste de noms de clés uniquement : aucun Bundle/extras n'est lu ni désérialisé.
                availableExtraData = if (protected) emptyList() else info.availableExtraData.toList(),
                isCheckable = info.isCheckable,
                isChecked = !protected && info.isCheckedCompat(),
                isEnabled = info.isEnabled,
                isScrollable = info.isScrollable,
                isPassword = password,
                isAccessibilityDataSensitive = accessibilityDataSensitive,
                isVisibleToUser = info.isVisibleToUser,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                isEditable = editable,
                isExpandable = info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_EXPAND },
                isIndeterminate = !protected && info.isIndeterminateCompat(rules, stateDescription),
                stateDescription = stateDescription,
                supplementalDescription = supplementalDescription,
                webRole = null,
                extrasSummary = null,
            )
            if (depth < path.size - 1) {
                // Ancêtre du panneau (lecture partielle) : seul l'enfant suivant du chemin est lu.
                if (protected || accessibilityDataSensitive) {
                    pathBroken = true
                } else {
                    node.addChild(convert(path[depth + 1], depth + 1, inWeb))
                }
                return node
            }
            if (!protected) {
                val childCount = info.childCount
                if (depth >= MAX_DEPTH && childCount > 0) {
                    truncated = true
                    node.childrenOmitted = true
                } else if (depth < MAX_DEPTH) {
                    for (i in 0 until childCount) {
                        if (infos.size >= MAX_NODES) {
                            truncated = true
                            node.childrenOmitted = true
                            break
                        }
                        // Longue liste (300 fournisseurs) : passé SOFT_NODES, on laisse tomber la fin
                        // de la liste pour garder de la place aux boutons placés APRÈS elle dans la page
                        // (« Confirmer les choix », « Enregistrer »), qui étaient perdus (20/11).
                        if (infos.size >= SOFT_NODES && childCount - i > FOOTER_SIBLINGS) {
                            truncated = true
                            node.childrenOmitted = true
                            break
                        }
                        val child = info.getChild(i) ?: continue
                        node.addChild(convert(child, depth + 1, inWeb))
                    }
                }
            }
            return node
        }

        val root = convert(rootInfo, 0, parentInWeb = false).assignKeys()
        if (pathBroken) {
            Capture(Screen(packageName, root), infos).close()
            return null
        }
        // Clés attribuées en parcours préfixe : le panneau vient juste après ses ancêtres.
        val partialTop = if (path.isEmpty()) -1 else path.size - 1
        return Capture(Screen(packageName, root, isTruncated = truncated, partialTopKey = partialTop), infos)
    }

    private companion object {
        const val MAX_NODES = 6000
        const val SOFT_NODES = 5000
        const val FOOTER_SIBLINGS = 3
        const val MAX_DEPTH = 80
    }
}
