package fr.refusbot.core

/**
 * Non-invasive bridge between the generic screen analyser and the optional CMP catalogue.
 * CMP identity is descriptive metadata only: an unknown or ambiguous provider never suppresses
 * generic consent recognition and never authorizes an action.
 */
enum class ConsentUiState { PRESENT, ABSENT, UNCERTAIN }

enum class ConsentActionAssessment {
    /** The screen is not a consent UI; no consent action may be considered. */
    NOT_APPLICABLE,
    /** Consent UI is recognized, but the generic analyser has insufficient confidence. */
    NEEDS_REVIEW,
    /** Consent UI and at least one safe refusal route are recognized; engine guards still apply. */
    REFUSAL_ROUTE_CANDIDATE,
}

data class CmpAwareScreenAnalysis(
    val screen: ScreenAnalysis,
    val cmp: CmpProfileMatch,
    val consentUi: ConsentUiState,
    val actionAssessment: ConsentActionAssessment,
    val visibleControlCount: Int,
    val knownStateToggleCount: Int,
    val unknownStateToggleCount: Int,
) {
    /** Provider recognition is descriptive only and is never a prerequisite for generic detection. */
    val providerIdentityRequiredForGenericRecognition: Boolean = false
}

class CmpAwareScreenAnalyzer(
    private val screenAnalyzer: ScreenAnalyzer,
    private val profiles: List<CmpCatalogProfile>,
) {
    fun analyze(root: UiNode): CmpAwareScreenAnalysis {
        val screen = screenAnalyzer.analyze(root)
        val safeNodes = root.walk().filter { node ->
            node.isVisibleToUser &&
                !node.bounds.isEmpty &&
                !node.isPassword &&
                !node.isEditable &&
                !node.isAccessibilityDataSensitive &&
                !node.insideProtectedSubtree
        }.toList()
        val viewIds = safeNodes.mapNotNull { it.viewId }
        val visibleTexts = safeNodes.flatMap { it.ownTexts() }
        val cmp = CmpCatalogMatcher.match(profiles, viewIds, visibleTexts)

        // Generic consent recognition is evaluated first and remains independent of provider identity.
        val consentUi = when {
            screen.isConsent -> ConsentUiState.PRESENT
            screen.hints.isNotEmpty() -> ConsentUiState.UNCERTAIN
            else -> ConsentUiState.ABSENT
        }
        val refusalRoute = screen.buttons.any { candidate ->
            candidate.kind == ButtonKind.REFUSE ||
                candidate.kind == ButtonKind.GLOBAL_REJECT ||
                candidate.kind == ButtonKind.OPTIONS
        }
        val assessment = when {
            consentUi == ConsentUiState.ABSENT -> ConsentActionAssessment.NOT_APPLICABLE
            consentUi == ConsentUiState.UNCERTAIN -> ConsentActionAssessment.NEEDS_REVIEW
            screen.confidence == Confidence.INSUFFISANT || !refusalRoute -> ConsentActionAssessment.NEEDS_REVIEW
            else -> ConsentActionAssessment.REFUSAL_ROUTE_CANDIDATE
        }
        return CmpAwareScreenAnalysis(
            screen = screen,
            cmp = cmp,
            consentUi = consentUi,
            actionAssessment = assessment,
            visibleControlCount = screen.buttons.size + screen.toggleStates.size + screen.expandables.size,
            knownStateToggleCount = screen.toggleStates.values.count { it != null },
            unknownStateToggleCount = screen.unknownStateToggles,
        )
    }
}
