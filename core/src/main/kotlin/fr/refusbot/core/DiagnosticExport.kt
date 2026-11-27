package fr.refusbot.core

/**
 * Export de l'arbre d'accessibilité en JSON (mode diagnostic).
 * Les champs mot de passe ne sont jamais exportés (texte supprimé dès la capture).
 */
object DiagnosticExport {

    data class Meta(
        val packageName: String,
        val appLabel: String?,
        val capturedAt: String,
        val trigger: String,
        val reason: String?,
        val appVersion: String,
        val rulesVersion: Int,
        val androidVersion: String,
        val device: String,
        /** true/false for a measured capture; null when the caller cannot know. */
        val captureTruncated: Boolean? = null,
        /** Durées des étapes de la session, en ms (voir [SessionTiming.Summary.toMap]) ; null si non mesurées. */
        val timings: Map<String, Long>? = null,
    )

    /** Mode d'export : complet, puis de plus en plus réduit pour tenir dans [maxBytes]. */
    enum class Mode(val label: String) {
        FULL("complet"),
        COMPACT("compact : valeurs par défaut omises"),
        VISIBLE("visible : éléments hors écran omis"),
        TRUNCATED("tronqué : limite de taille atteinte"),
    }

    /**
     * Export qui tient dans [maxBytes] : une page avec des centaines de partenaires dépassait la
     * limite et la capture échouait. On essaie le format complet, puis compact, puis sans les
     * éléments hors écran, puis en limitant le nombre d'éléments. Le mode retenu est indiqué.
     *
     * [controlIssues] : interrupteurs non vérifiés, nommés, avec la raison (option « diagnostic
     * détaillé », 26/11). Paramètre ajouté APRÈS [maxBytes] pour ne pas casser les appels positionnels.
     */
    fun toJsonFitting(
        root: UiNode,
        meta: Meta,
        analysis: ScreenAnalysis?,
        maxBytes: Int = DiagnosticPayloadPolicy.MAX_BYTES,
        controlIssues: List<ControlIssue> = emptyList(),
    ): String {
        fun fits(json: String) = json.toByteArray(Charsets.UTF_8).size <= maxBytes
        toJson(root, meta, analysis, controlIssues = controlIssues).let { if (fits(it)) return it }
        toJson(root, meta, analysis, Mode.COMPACT, controlIssues = controlIssues).let { if (fits(it)) return it }
        toJson(root, meta, analysis, Mode.VISIBLE, controlIssues = controlIssues).let { if (fits(it)) return it }
        var budget = 3000
        while (budget >= 50) {
            val json = toJson(root, meta, analysis, Mode.TRUNCATED, nodeBudget = budget, controlIssues = controlIssues)
            if (fits(json)) return json
            budget /= 2
        }
        return toJson(root, meta, analysis, Mode.TRUNCATED, nodeBudget = 0, controlIssues = controlIssues)
    }

    fun toJson(root: UiNode, meta: Meta, analysis: ScreenAnalysis?, controlIssues: List<ControlIssue> = emptyList()): String =
        toJson(root, meta, analysis, Mode.FULL, controlIssues = controlIssues)

    fun toJson(
        root: UiNode,
        meta: Meta,
        analysis: ScreenAnalysis?,
        mode: Mode,
        nodeBudget: Int = Int.MAX_VALUE,
        controlIssues: List<ControlIssue> = emptyList(),
    ): String {
        val doc = LinkedHashMap<String, Any?>()
        doc["format"] = "refusbot-diagnostic-1"
        doc["package"] = meta.packageName
        doc["app"] = meta.appLabel
        doc["capturedAt"] = meta.capturedAt
        doc["trigger"] = meta.trigger
        doc["reason"] = meta.reason
        doc["refusbotVersion"] = meta.appVersion
        doc["rulesVersion"] = meta.rulesVersion
        doc["android"] = meta.androidVersion
        doc["device"] = meta.device
        doc["captureTruncated"] = meta.captureTruncated
        meta.timings?.let { doc["durees"] = it }
        if (analysis != null) doc["analysis"] = analysisToMap(analysis)
        // Interrupteurs non vérifiés, nommés, avec la raison (26/11). Comme le reste du diagnostic,
        // ces libellés sont du texte d'écran : ils restent dans ce fichier, jamais dans l'historique.
        if (controlIssues.isNotEmpty()) {
            doc["controlesNonVerifies"] = controlIssues.map { issue ->
                linkedMapOf(
                    "libelle" to issue.label,
                    "cause" to issue.kind.name,
                    "explication" to issue.kind.explication,
                    "signature" to issue.signature,
                )
            }
        }
        doc["nodeCount"] = root.walk().count()
        if (mode == Mode.FULL) {
            doc["root"] = nodeToMap(root)
            return MiniJson.write(doc, indent = 1)
        }
        doc["exportMode"] = mode.label
        val counter = intArrayOf(nodeBudget)
        doc["root"] = reducedNode(root, mode, counter)
        if (mode == Mode.TRUNCATED) doc["exportedNodes"] = nodeBudget - maxOf(counter[0], 0)
        return MiniJson.write(doc, indent = 0)
    }

    /** Nœud réduit : valeurs par défaut omises ; en mode VISIBLE ou TRUNCATED, branches hors écran omises. */
    private fun reducedNode(n: UiNode, mode: Mode, budget: IntArray): Map<String, Any?> {
        budget[0]--
        val full = nodeToMap(n, withChildren = false).toMutableMap()
        // Valeurs par défaut retirées (le lecteur les suppose) : gain de place important.
        for (key in listOf("clickable", "focusable", "focused", "selected", "checkable", "checked")) {
            if (full[key] == false) full.remove(key)
        }
        if (full["enabled"] == true) full.remove("enabled")
        if (full["visible"] == true) full.remove("visible")
        full.remove("availableExtraData")
        full.remove("actions")
        if (n.isAccessibilityDataSensitive) return full
        val kids = n.children.filter { c ->
            mode == Mode.COMPACT || c.walk().any { it.isVisibleToUser && !it.bounds.isEmpty }
        }
        val out = ArrayList<Map<String, Any?>>()
        for (c in kids) {
            if (budget[0] <= 0) {
                full["childrenOmitted"] = kids.size - out.size
                break
            }
            out.add(reducedNode(c, mode, budget))
        }
        if (out.isNotEmpty()) full["children"] = out
        return full
    }

    fun analysisToMap(a: ScreenAnalysis): Map<String, Any?> = linkedMapOf(
        "isConsent" to a.isConsent,
        "hints" to a.hints.toList(),
        "buttons" to a.buttons.map { b ->
            linkedMapOf(
                "kind" to b.kind.name,
                "label" to b.label,
                "phrase" to b.phrase,
                "target" to when (b.target) {
                    is ClickTarget.Node -> "node"
                    is ClickTarget.Point -> "tap"
                    null -> null
                },
                "blocked" to b.blocked?.detail,
            )
        },
        "checkedToggles" to a.checkedToggles.map { t ->
            linkedMapOf("label" to t.label, "signature" to t.signature, "blocked" to t.blocked?.detail)
        },
        "expandables" to a.expandables.map { it.label },
        "toggleCount" to a.toggleStates.size,
        "recognizedControlCount" to (a.buttons.size + a.toggleStates.size + a.expandables.size),
        "unknownStateToggles" to a.unknownStateToggles,
        "unknownToggleSignatures" to a.unknownToggleSignatures.toList(),
        "scrollContainer" to a.scrollTarget?.className,
    )

    fun nodeToMap(n: UiNode, withChildren: Boolean = true): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["class"] = n.className
        if (n.isAccessibilityDataSensitive) {
            // Aucun texte, état, identifiant, position ou descendant d'un nœud sensible n'est exporté.
            m["accessibilityDataSensitive"] = true
            return m
        }
        if (!n.isPassword) {
            // Le contenu des champs de saisie n'est jamais exporté.
            if (!n.isEditable) n.text?.let { m["text"] = it }
            n.contentDescription?.let { m["contentDescription"] = it }
        } else {
            m["password"] = true
        }
        n.viewId?.let { m["viewId"] = it }
        n.webRole?.let { m["webRole"] = it }
        n.stateDescription?.let { m["stateDescription"] = it }
        n.supplementalDescription?.let { m["supplementalDescription"] = it }
        n.extrasSummary?.let { m["extras"] = it }
        m["clickable"] = n.isClickable
        m["focusable"] = n.isFocusable
        m["focused"] = n.isFocused
        m["selected"] = n.isSelected
        if (n.accessibilityActions.isNotEmpty()) m["actions"] = n.accessibilityActions
        if (n.availableExtraData.isNotEmpty()) m["availableExtraData"] = n.availableExtraData
        m["checkable"] = n.isCheckable
        m["checked"] = n.isChecked
        m["enabled"] = n.isEnabled
        if (n.isScrollable) m["scrollable"] = true
        if (n.isEditable) m["editable"] = true
        if (n.isExpandable) m["expandable"] = true
        m["visible"] = n.isVisibleToUser
        m["bounds"] = listOf(n.bounds.left, n.bounds.top, n.bounds.right, n.bounds.bottom)
        // La capture s'est arrêtée avant le dernier enfant de ce nœud (limite atteinte ici).
        if (n.childrenOmitted) m["captureStopped"] = true
        if (withChildren && n.children.isNotEmpty()) m["children"] = n.children.map { nodeToMap(it) }
        return m
    }
}
