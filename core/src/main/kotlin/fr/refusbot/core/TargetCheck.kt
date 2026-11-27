package fr.refusbot.core

/**
 * Dernière vérification avant d'agir : la cible à l'écran est-elle encore celle qui a été analysée ?
 *
 * L'analyse porte sur un instantané. Entre l'instantané et l'action, l'écran peut avoir changé
 * (un bouton « Refuser » remplacé par « Accepter », une autre fenêtre passée devant). Avant tout
 * clic ou geste, l'état RÉEL de la cible est relu et comparé ; au moindre écart, on s'abstient.
 */
object TargetCheck {

    fun toggleMayAct(isChecked: Boolean, isIndeterminate: Boolean = false): Boolean =
        isChecked && !isIndeterminate

    /**
     * La fenêtre de la cible peut-elle recevoir l'action ? Oui si elle est la fenêtre active, ou si la
     * fenêtre active est une autre fenêtre de LA MÊME appli (bulle d'aide de Firefox, par exemple)
     * qui ne recouvre pas le centre de la cible. Une autre appli au premier plan n'est jamais acceptée.
     */
    fun targetWindowUsable(
        targetWindowId: Int,
        targetPackage: String?,
        targetBounds: Bounds,
        activeWindowId: Int,
        activePackage: String?,
        activeBounds: Bounds,
    ): Boolean {
        if (activeWindowId == targetWindowId) return true
        if (targetPackage == null || activePackage != targetPackage) return false
        if (targetBounds.isEmpty) return false
        return !activeBounds.contains(targetBounds.centerX, targetBounds.centerY)
    }

    /**
     * Faut-il lire une autre fenêtre que la fenêtre active ? Seulement si la fenêtre active est
     * toute petite (moins de [SMALL_WINDOW_PERCENT] % de la plus grande fenêtre de la même appli) :
     * une bulle ou un menu. Une vraie fenêtre de consentement en dialogue reste prioritaire.
     */
    fun preferLargerWindow(activeArea: Long, largestSameAppArea: Long): Boolean =
        largestSameAppArea > 0 && activeArea * 100 < largestSameAppArea * SMALL_WINDOW_PERCENT

    const val SMALL_WINDOW_PERCENT = 20

    fun windowMismatch(
        expectedPackage: String,
        expectedBounds: Bounds,
        actualPackage: String?,
        actualBounds: Bounds,
        windowOnTop: Boolean,
    ): String? {
        if (actualPackage != expectedPackage) return "autre appli au premier plan"
        if (!windowOnTop) return "la fenêtre analysée n'est plus au premier plan"
        if (actualBounds != expectedBounds) return "cible déplacée depuis l'analyse"
        return null
    }

    /**
     * État réel de la cible, relu juste avant l'action.
     *
     * @param texts textes de la cible et de ses descendants (hors champs de saisie).
     * @param windowOnTop la fenêtre de la cible est toujours la fenêtre active.
     */
    data class Live(
        val packageName: String?,
        val texts: List<String>,
        val bounds: Bounds,
        val windowOnTop: Boolean,
        val isVisibleToUser: Boolean = true,
        val isEnabled: Boolean = true,
    )

    /**
     * Renvoie la raison de s'abstenir, ou null si la cible est inchangée.
     *
     * @param live null si la cible n'existe plus ou n'a pas pu être relue.
     * @param forGesture un appui par coordonnées exige en plus la même position ; toutes les actions
     *   exigent la même fenêtre au premier plan.
     */
    fun mismatch(
        expectedPackage: String,
        expected: UiNode,
        live: Live?,
        guard: SafetyGuard,
        forGesture: Boolean,
        selectionConfirm: Boolean = false,
        offscreenAllowed: Boolean = false,
    ): String? {
        if (live == null) return "cible disparue ou illisible"
        if (live.packageName != expectedPackage) return "autre appli au premier plan"
        if (!live.windowOnTop) return "la fenêtre analysée n'est plus au premier plan"
        // Interrupteur à décocher ou bouton à faire apparaître, hors de l'écran (21/11 au soir) : seule la
        // visibilité est dispensée, et jamais pour un geste. Même fenêtre au premier plan, même libellé,
        // garde-fou : tout le reste est exigé.
        val visibilityExempt = offscreenAllowed && !forGesture && !selectionConfirm
        if (!visibilityExempt && (!expected.isVisibleToUser || !live.isVisibleToUser)) return "cible non visible à l'écran"
        if (!expected.isEnabled || !live.isEnabled) return "cible désactivée"
        val before = expected.subtreeTexts().map(TextNormalizer::canonical).filter { it.isNotEmpty() }.sorted()
        val now = live.texts.map(TextNormalizer::canonical).filter { it.isNotEmpty() }.sorted()
        if (before != now) return "libellé de la cible modifié depuis l'analyse"
        for (t in live.texts) {
            val verdict = guard.checkText(t, selectionConfirm)
            if (verdict is SafetyGuard.Verdict.Blocked) return verdict.reason
        }
        if (forGesture) {
            if (expected.bounds.isEmpty || live.bounds.isEmpty || live.bounds != expected.bounds) {
                return "cible déplacée ou hors zone depuis l'analyse"
            }
        }
        return null
    }
}

/** Noms des fichiers de diagnostic : uniques même pour deux captures dans la même seconde. */
object DiagnosticNames {
    /**
     * @param stamp horodatage (« 20261116-101500 »).
     * @param exists indique si un nom est déjà pris.
     */
    fun next(packageName: String, stamp: String, exists: (String) -> Boolean): String {
        val safePkg = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val base = "diagnostic-$safePkg-$stamp"
        if (!exists("$base.json")) return "$base.json"
        var n = 2
        while (exists("$base-$n.json")) n++
        return "$base-$n.json"
    }
}
