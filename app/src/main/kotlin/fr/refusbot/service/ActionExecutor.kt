package fr.refusbot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import fr.refusbot.core.ActionExecutionStats
import fr.refusbot.core.Bounds
import fr.refusbot.core.ClickTarget
import fr.refusbot.core.Limits
import fr.refusbot.core.Op
import fr.refusbot.core.Rules
import fr.refusbot.core.SafetyGuard
import fr.refusbot.core.TargetCheck
import fr.refusbot.core.UiNode
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Exécute les opérations décidées par le moteur.
 *
 * Le garde-fou est vérifié une DEUXIÈME fois ici, juste avant chaque clic : aucune action ne peut
 * atteindre l'écran sans passer par [SafetyGuard].
 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val rules: Rules,
) {
    private val guard = SafetyGuard(rules)
    private val limits: Limits = rules.limits

    /** Raisons des clics bloqués lors de la dernière exécution. */
    val blocked = mutableListOf<String>()

    suspend fun execute(ops: List<Op>, capture: Capture): ActionExecutionStats {
        blocked.clear()
        var scrollBlocked = false
        var scrollAtEnd = false
        var executed = 0
        ops.forEachIndexed { i, op ->
            val succeeded = when (op) {
                is Op.Click -> click(op, capture)
                is Op.Expand -> expand(op, capture)
                is Op.Scroll -> scroll(op, capture).let { outcome ->
                    if (outcome == ScrollOutcome.BLOCKED) scrollBlocked = true
                    if (outcome == ScrollOutcome.AT_END) scrollAtEnd = true
                    outcome != ScrollOutcome.BLOCKED
                }
                is Op.Swipe -> swipe(op.area, capture.screen.packageName, op.window, capture.info(op.window))
                    .also { if (!it) scrollBlocked = true }
                is Op.ShowOnScreen -> showOnScreen(op, capture)
            }
            if (succeeded) executed++
            if (i < ops.lastIndex) delay(limits.toggleDelayMs)
        }
        return ActionExecutionStats(
            requested = ops.size,
            executed = executed,
            blocked = blocked.size,
            navigationBlocked = scrollBlocked,
            scrollAtEnd = scrollAtEnd && !scrollBlocked,
        )
    }

    private suspend fun click(op: Op.Click, capture: Capture): Boolean {
        val verdict = guard.checkNode(op.target.guardNode, op.selectionConfirm)
        if (verdict is SafetyGuard.Verdict.Blocked) {
            Log.w(TAG, "Clic bloqué : ${verdict.reason}")
            blocked.add(verdict.reason)
            return false
        }
        val pkg = capture.screen.packageName
        return when (val t = op.target) {
            is ClickTarget.Node -> {
                val info = capture.info(t.node)
                // L'écran a pu changer depuis l'instantané : la cible RÉELLE est relue avant d'agir.
                // Hors écran : interrupteur présent dans l'arbre, ou bouton écrasé sous la barre du
                // navigateur (Opera, 22/11). Seule la visibilité est dispensée, jamais pour un geste.
                if (abstain(pkg, t.node, info, forGesture = false, op.selectionConfirm, offscreenAllowed = op.offscreen)) return false
                val toggleNode = op.toggleNode ?: t.node
                val toggleInfo = if (op.isToggle) capture.info(toggleNode) else null
                if (op.isToggle) {
                    if (toggleInfo == null) {
                        blocked.add("interrupteur absent de la capture")
                        return false
                    }
                    if (!toggleInfo.isStillChecked()) {
                        blocked.add("état de l'interrupteur non vérifiable avant action")
                        return false
                    }
                }
                if (info?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    true
                } else if (!op.allowTapFallback) {
                    // Validation : jamais d'appui aux coordonnées, même en secours (audit du 21/11).
                    // Interrupteur hors de l'écran : il sera réessayé une fois visible (voir le moteur).
                    blocked.add(
                        when {
                            op.offscreen && op.isToggle -> "interrupteur hors écran : action refusée, nouvel essai une fois visible"
                            op.offscreen -> "bouton hors de l'écran (sous la barre du navigateur) : action refusée, aucun appui aux coordonnées"
                            else -> "validation non cliquable : aucun appui aux coordonnées"
                        },
                    )
                    false
                } else if ((if (op.isToggle) toggleNode else t.node).isVisibleToUser &&
                    !(if (op.isToggle) toggleNode else t.node).bounds.isEmpty
                ) {
                    // Appui de secours : seulement si la cible est toujours au même endroit, dans la
                    // même fenêtre au premier plan. Sinon on s'abstient.
                    val fallbackNode = if (op.isToggle) toggleNode else t.node
                    val fallbackInfo = if (op.isToggle) toggleInfo else info
                    if (abstain(pkg, fallbackNode, fallbackInfo, forGesture = true, op.selectionConfirm)) return false
                    if (op.isToggle && (fallbackInfo == null || !fallbackInfo.isStillChecked())) return false
                    tap(fallbackNode.bounds.centerX.toFloat(), fallbackNode.bounds.centerY.toFloat())
                } else {
                    false
                }
            }
            is ClickTarget.Point -> {
                val info = capture.info(t.node)
                if (abstain(pkg, t.node, info, forGesture = true, op.selectionConfirm)) return false
                if (op.isToggle && (info == null || !info.isStillChecked())) {
                    blocked.add("état de l'interrupteur non vérifiable avant geste")
                    return false
                }
                tap(t.x.toFloat(), t.y.toFloat())
            }
        }
    }

    /**
     * Relit l'état réel de la cible et le compare à l'instantané analysé ([TargetCheck]). Vrai s'il
     * faut s'abstenir : cible disparue, libellé ou position modifiés, autre fenêtre au premier plan,
     * ou texte interdit apparu. Aucun geste n'est jamais envoyé vers une ancienne coordonnée.
     */
    private fun abstain(
        packageName: String,
        expected: UiNode,
        info: AccessibilityNodeInfo?,
        forGesture: Boolean,
        selectionConfirm: Boolean = false,
        offscreenAllowed: Boolean = false,
    ): Boolean {
        val reason = TargetCheck.mismatch(packageName, expected, live(info), guard, forGesture, selectionConfirm, offscreenAllowed) ?: return false
        Log.w(TAG, "Action abandonnée : $reason")
        blocked.add(reason)
        return true
    }

    private fun live(info: AccessibilityNodeInfo?): TargetCheck.Live? {
        if (info == null) return null
        return try {
            if (!info.refresh() || info.isAccessibilityDataSensitiveCompat()) return null
            val rect = Rect()
            info.getBoundsInScreen(rect)
            val texts = ArrayList<String>()
            var budget = MAX_LIVE_NODES
            var sensitiveFound = false
            fun collect(n: AccessibilityNodeInfo) {
                if (budget-- <= 0) return
                if (n.isAccessibilityDataSensitiveCompat()) {
                    sensitiveFound = true
                    return
                }
                // Comme dans l'instantané : rien n'est lu dans un champ de saisie ou de mot de passe.
                if (n.isPassword || n.isEditable) return
                val text = n.text?.toString()
                val description = n.contentDescription?.toString()
                text?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
                description?.takeIf { it.isNotBlank() && it != text }?.let { texts.add(it) }
                for (i in 0 until n.childCount) {
                    val child = n.getChild(i) ?: continue
                    try {
                        collect(child)
                    } finally {
                        // getChild() renvoie une copie : la libérer avant Android 13 évite
                        // d'accumuler des AccessibilityNodeInfo pendant les vérifications.
                        child.recycleCompat()
                    }
                }
            }
            collect(info)
            // Sous-arbre trop grand ou marqué sensible : on s'abstient.
            if (budget < 0 || sensitiveFound) return null
            val windowOnTop = targetWindowOnTop(info, rect)
            TargetCheck.Live(
                packageName = info.packageName?.toString(),
                texts = texts,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                windowOnTop = windowOnTop,
                isVisibleToUser = info.isVisibleToUser,
                isEnabled = info.isEnabled,
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cible illisible", e)
            null
        }
    }

    /**
     * Fait apparaître le bouton de validation (ACTION_SHOW_ON_SCREEN) : un défilement, pas un clic.
     * Garde-fou et relecture de la cible quand même (même appli, même fenêtre, même libellé).
     */
    private fun showOnScreen(op: Op.ShowOnScreen, capture: Capture): Boolean {
        when (val verdict = guard.checkNode(op.node)) {
            is SafetyGuard.Verdict.Blocked -> {
                blocked.add(verdict.reason)
                return false
            }
            SafetyGuard.Verdict.Allowed -> Unit
        }
        val info = capture.info(op.node)
        // Le bouton est hors de l'écran par définition : seule la visibilité est dispensée.
        if (abstain(capture.screen.packageName, op.node, info, forGesture = false, offscreenAllowed = true)) return false
        return try {
            info?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) == true
        } catch (_: RuntimeException) {
            false
        }
    }

    /** Déplier une section : action d'accessibilité dédiée, jamais de clic (le garde-fou s'applique quand même). */
    private fun expand(op: Op.Expand, capture: Capture): Boolean {
        when (val verdict = guard.checkNode(op.node)) {
            is SafetyGuard.Verdict.Blocked -> {
                blocked.add(verdict.reason)
                return false
            }
            SafetyGuard.Verdict.Allowed -> Unit
        }
        val info = capture.info(op.node)
        if (abstain(capture.screen.packageName, op.node, info, forGesture = false)) return false
        return info?.performAction(AccessibilityNodeInfo.ACTION_EXPAND) == true
    }

    /**
     * Résultat d'un défilement :
     * - MOVED : le contenu a bougé (mesuré, jamais supposé) ;
     * - AT_END : rien n'a bougé ET Android ne propose plus « défiler vers le bas » : fin de liste ;
     * - BLOCKED : rien n'a bougé alors qu'Android propose encore de défiler, ou défilement impossible
     *   (fenêtre changée, mesure illisible). Le moteur ne valide alors jamais.
     */
    private enum class ScrollOutcome { MOVED, AT_END, BLOCKED }

    private suspend fun scroll(op: Op.Scroll, capture: Capture): ScrollOutcome {
        val info = capture.info(op.container) ?: return ScrollOutcome.BLOCKED
        // Le retour true d'ACTION_SCROLL_FORWARD signifie seulement « accepté », pas « déplacé ».
        // On mesure le déplacement géométrique des descendants ; une mesure illisible n'est jamais
        // une preuve (ni de déplacement, ni de fin de liste).
        if (windowAbstain(capture.screen.packageName, op.container, info)) return ScrollOutcome.BLOCKED
        if (!info.refresh()) return ScrollOutcome.BLOCKED
        val initialGeometry = scrollGeometry(info) ?: return ScrollOutcome.BLOCKED
        val accepted = info.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        if (accepted) {
            delay(SCROLL_SETTLE_MS)
            val afterNative = if (info.refresh()) scrollGeometry(info) else null
            if (afterNative != null && afterNative != initialGeometry) return ScrollOutcome.MOVED
            // Pages web : le défilement est animé et l'arbre se met à jour en retard. Une seconde mesure
            // évite de prendre une liste qui bouge pour une liste bloquée (21/11 au soir).
            delay(SCROLL_LATE_MS)
            val late = if (info.refresh()) scrollGeometry(info) else null
            if (late != null && late != initialGeometry) return ScrollOutcome.MOVED
        }

        // Repli par geste, mesuré lui aussi (certaines WebView refusent l'action native).
        if (!swipe(op.container.bounds, capture.screen.packageName, op.container, info)) return ScrollOutcome.BLOCKED
        delay(SCROLL_SETTLE_MS)
        var afterSwipe = if (info.refresh()) scrollGeometry(info) else null
        if (afterSwipe == initialGeometry) {
            delay(SCROLL_LATE_MS)
            afterSwipe = if (info.refresh()) scrollGeometry(info) else null
        }
        if (afterSwipe == null) {
            blocked.add("défilement impossible à mesurer")
            return ScrollOutcome.BLOCKED
        }
        if (afterSwipe != initialGeometry) return ScrollOutcome.MOVED
        // Rien n'a bougé : fin de liste seulement si Android ne propose plus de défiler vers le bas.
        return if (!canScrollForward(info)) {
            ScrollOutcome.AT_END
        } else {
            blocked.add("défilement exécuté ou accepté, mais aucune progression mesurable")
            ScrollOutcome.BLOCKED
        }
    }

    private fun canScrollForward(info: AccessibilityNodeInfo): Boolean =
        info.refresh() && info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }

    /**
     * AccessibilityNodeInfo n'expose pas scrollY. On mesure donc le déplacement des descendants
     * visibles du conteneur à partir de leurs bornes écran, sans lire leurs textes ni états.
     * Une liste vide/inaccessible ne constitue jamais une preuve de progression.
     *
     * Audit du 22/11 : une longue liste (Chrome : 480 partenaires, tous présents dans l'arbre) a bien
     * plus de [MAX_SCROLL_NODES] descendants. Avant, la mesure était alors jugée « illisible » et
     * CHAQUE défilement « bloqué » : la liste n'était jamais parcourue. Les premiers nœuds suffisent à
     * voir si le contenu a bougé ; la fin de liste exige toujours, en plus, qu'Android ne propose plus
     * de défiler vers le bas (voir [scroll]).
     */
    private fun scrollGeometry(root: AccessibilityNodeInfo): List<String>? {
        val geometry = ArrayList<String>()
        // Parcours en largeur (22/11) : les lignes de la liste d'abord, puis leur contenu. Chaque nœud
        // relu est une question posée à l'appli : 200 nœuds, trois à quatre fois par défilement, coûtaient
        // plus d'une seconde. Une poignée de lignes suffit à voir si la liste a bougé.
        //
        // Essais du 22/11 à 16 h 48 (Galaxy Defense) : les 40 premières lignes d'une liste déjà défilée
        // sont SOUS le haut de l'écran, et la WebView leur donne toutes la même position écrasée (hauteur
        // nulle en haut). Mesurées seules, elles ne bougeaient plus : chaque défilement était déclaré
        // « bloqué » (« défilement impossible »), alors que la liste défilait. Seules les lignes VISIBLES
        // sont mesurées désormais, avec leur rang dans la liste (des lignes de même hauteur peuvent
        // retomber exactement aux mêmes positions après un défilement).
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var remaining = MAX_SCROLL_NODES
        var visits = MAX_SCROLL_VISITS
        return try {
            while (queue.isNotEmpty() && remaining > 0 && visits > 0) {
                val parent = queue.removeFirst()
                try {
                    for (index in 0 until parent.childCount) {
                        if (remaining <= 0 || visits-- <= 0) break
                        val child = parent.getChild(index) ?: continue
                        if (!child.refresh() || child.isAccessibilityDataSensitiveCompat() || !child.isVisibleToUser) {
                            child.recycleCompat()
                            continue
                        }
                        remaining--
                        val rect = Rect()
                        child.getBoundsInScreen(rect)
                        geometry.add("$index:${rect.left},${rect.top},${rect.right},${rect.bottom}")
                        queue.addLast(child)
                    }
                } finally {
                    if (parent !== root) parent.recycleCompat()
                }
            }
            if (geometry.isEmpty()) null else geometry
        } catch (_: RuntimeException) {
            null
        } finally {
            queue.forEach { if (it !== root) it.recycleCompat() }
        }
    }

    private suspend fun tap(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val path = Path().apply { moveTo(x, y) }
        return dispatch(GestureDescription.StrokeDescription(path, 0, TAP_MS))
    }

    /**
     * Balayage du bas vers le haut (le contenu défile vers le bas), « façon Webtoon » (22/11) : le doigt
     * glisse d'un peu plus d'un demi-écran puis RESTE POSÉ un instant avant de se lever. Sans cet arrêt,
     * Android lançait la liste sur sa lancée : elle pouvait sauter des lignes jamais lues. Chaque
     * balayage avance donc d'un écran à peu près fixe, en gardant un recouvrement avec le précédent.
     */
    private suspend fun swipe(
        area: Bounds,
        packageName: String,
        expectedWindow: UiNode,
        info: AccessibilityNodeInfo?,
    ): Boolean {
        if (windowAbstain(packageName, expectedWindow, info)) return false
        if (area.isEmpty || area.height < 50) return false
        val x = area.centerX.toFloat()
        val endY = area.top + area.height * 0.25f
        val path = Path().apply {
            moveTo(x, area.top + area.height * 0.78f)
            lineTo(x, endY)
        }
        val move = GestureDescription.StrokeDescription(path, 0, SWIPE_MS, true)
        if (!dispatch(move)) return false
        // Doigt immobile, puis levé : aucune vitesse au moment de lâcher, donc pas d'élan.
        val hold = try {
            move.continueStroke(Path().apply { moveTo(x, endY) }, 0, SWIPE_HOLD_MS, false)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Fin de balayage refusée", e)
            null
        }
        if (hold != null) dispatch(hold)
        // Le défilement a eu lieu ; son effet réel est mesuré ensuite (géométrie ou nouvelle capture).
        return true
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.isStillChecked(): Boolean {
        if (!refresh() || isAccessibilityDataSensitiveCompat() || !isCheckable) return false
        val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) stateDescription?.toString() else null
        // Ne jamais utiliser une description textuelle comme substitut à isChecked/getChecked
        // lors de la dernière vérification juste avant un clic.
        return !isIndeterminateCompat(rules, description) && isCheckedCompat()
    }

    /**
     * La fenêtre de [info] peut-elle recevoir l'action : fenêtre active, ou fenêtre de la même appli
     * qu'une petite fenêtre active qui ne recouvre pas la cible (voir TargetCheck.targetWindowUsable).
     */
    private fun targetWindowOnTop(info: AccessibilityNodeInfo, targetRect: Rect): Boolean {
        val active = service.rootInActiveWindow ?: return false
        return try {
            val a = Rect()
            active.getBoundsInScreen(a)
            TargetCheck.targetWindowUsable(
                targetWindowId = info.windowId,
                targetPackage = info.packageName?.toString(),
                targetBounds = Bounds(targetRect.left, targetRect.top, targetRect.right, targetRect.bottom),
                activeWindowId = active.windowId,
                activePackage = active.packageName?.toString(),
                activeBounds = Bounds(a.left, a.top, a.right, a.bottom),
            )
        } finally {
            active.recycleCompat()
        }
    }

    private fun windowAbstain(packageName: String, expected: UiNode, info: AccessibilityNodeInfo?): Boolean {
        if (info == null) return true
        return try {
            if (!info.refresh()) return true
            if (info.isAccessibilityDataSensitiveCompat()) {
                blocked.add("cible marquée comme sensible")
                return true
            }
            val active = service.rootInActiveWindow ?: return true
            val rect = Rect()
            info.getBoundsInScreen(rect)
            val activePackage = active.packageName?.toString()
            active.recycleCompat()
            val mismatch = TargetCheck.windowMismatch(
                expectedPackage = packageName,
                expectedBounds = expected.bounds,
                // Une bulle de la même appli au premier plan n'est pas « une autre appli ».
                actualPackage = activePackage,
                actualBounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                windowOnTop = targetWindowOnTop(info, rect),
            )
            if (mismatch != null) {
                Log.w(TAG, "Action abandonnée : $mismatch")
                blocked.add(mismatch)
            }
            mismatch != null
        } catch (_: RuntimeException) {
            true
        }
    }

    private suspend fun dispatch(stroke: GestureDescription.StrokeDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val accepted = try {
                service.dispatchGesture(gesture, callback, null)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Geste refusé", e)
                false
            }
            if (!accepted && cont.isActive) cont.resume(false)
        }

    private companion object {
        const val TAG = "RefusBot"
        const val TAP_MS = 60L
        const val SWIPE_MS = 280L
        /** Doigt immobile en fin de balayage, pour que la liste s'arrête net (pas d'élan). */
        const val SWIPE_HOLD_MS = 120L
        const val SCROLL_SETTLE_MS = 180L
        /** Seconde mesure, pour les listes web dont l'arbre se met à jour en retard. */
        const val SCROLL_LATE_MS = 250L
        const val MAX_LIVE_NODES = 200
        /** Nœuds relus pour mesurer un défilement (voir [scrollGeometry]). */
        const val MAX_SCROLL_NODES = 40
        /** Lignes lues au plus pour trouver les [MAX_SCROLL_NODES] lignes visibles (les autres ne sont pas mesurées). */
        const val MAX_SCROLL_VISITS = 400
    }
}
