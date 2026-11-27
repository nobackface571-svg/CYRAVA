package fr.refusbot.core

/**
 * Relecture indépendante des preuves d'accessibilité avant tout refus « vérifié ».
 *
 * La partie « vérification TCF » a été retirée le 27/11 : CYRAVA ne peut pas lire les données TCF
 * des autres applis (ni leur stockage privé, ni le JavaScript d'un navigateur), elle n'était donc
 * jamais utilisée par l'appli.
 */
object ConsentVerificationEngine {

    data class AccessibilityEvidence(
        val toggleStates: Map<String, Boolean?>,
        val keptOn: Set<String> = emptySet(),
        val unknownToggleSignatures: Set<String> = emptySet(),
        val captureComplete: Boolean = true,
        val explorationComplete: Boolean = true,
        val radioRefusalVerified: Boolean = false,
        val radioRefusalUnverified: Boolean = false,
        val acceptRadioSelected: Boolean = false,
    )

    data class AccessibilityResult(
        val allObservedTogglesOff: Boolean,
        val radioRefusalVerified: Boolean,
        val unresolvedToggleCount: Int,
        val activeToggleCount: Int,
        val canClaimVerified: Boolean,
        val reasons: List<String>,
    )

    /**
     * A strict re-evaluation of the evidence accumulated by RefusalEngine.
     * A control with an unknown state is never interpreted as OFF. Necessary/always-on
     * controls are exempt only when the existing analyser explicitly classified them as such.
     */
    fun verifyAccessibility(evidence: AccessibilityEvidence): AccessibilityResult {
        val reasons = mutableListOf<String>().apply {
            if (!evidence.captureComplete) add("capture incomplete")
            if (!evidence.explorationComplete) add("categories or pages not fully explored")
            if (evidence.radioRefusalUnverified) add("radio refusal not verified")
            if (evidence.acceptRadioSelected) add("an accept choice remains selected")
        }
        val unknown = evidence.toggleStates.count { (key, state) ->
            state == null || key in evidence.unknownToggleSignatures
        }
        val active = evidence.toggleStates.count { (key, state) ->
            state == true && key !in evidence.keptOn
        }
        val unresolved = unknown + evidence.unknownToggleSignatures.count { it !in evidence.toggleStates }
        if (unknown > 0) reasons += "toggle state unknown"
        if (active > 0) reasons += "active optional toggles remain"

        val allOff = evidence.toggleStates.isNotEmpty() &&
            active == 0 && unresolved == 0 &&
            evidence.toggleStates.all { (key, state) -> state == false || key in evidence.keptOn }
        val radioVerified = evidence.radioRefusalVerified &&
            !evidence.radioRefusalUnverified && !evidence.acceptRadioSelected
        val canVerify = evidence.captureComplete && evidence.explorationComplete &&
            unresolved == 0 && active == 0 &&
            !evidence.radioRefusalUnverified && !evidence.acceptRadioSelected &&
            (allOff || radioVerified)

        return AccessibilityResult(
            allObservedTogglesOff = allOff,
            radioRefusalVerified = radioVerified,
            unresolvedToggleCount = unresolved,
            activeToggleCount = active,
            canClaimVerified = canVerify,
            reasons = reasons,
        )
    }
}
