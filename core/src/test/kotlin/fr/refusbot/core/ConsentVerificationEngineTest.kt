package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentVerificationEngineTest {
    @Test
    fun `unknown or active accessibility toggle never verifies`() {
        val active = ConsentVerificationEngine.verifyAccessibility(
            ConsentVerificationEngine.AccessibilityEvidence(mapOf("vendor-1" to true)),
        )
        assertFalse(active.canClaimVerified)
        assertEquals(1, active.activeToggleCount)

        val unknown = ConsentVerificationEngine.verifyAccessibility(
            ConsentVerificationEngine.AccessibilityEvidence(mapOf("vendor-1" to null)),
        )
        assertFalse(unknown.canClaimVerified)
        assertEquals(1, unknown.unresolvedToggleCount)
    }

    @Test
    fun `all observed optional toggles off verifies only with complete exploration`() {
        val evidence = ConsentVerificationEngine.AccessibilityEvidence(
            toggleStates = mapOf("vendor-1-consent" to false, "vendor-1-li" to false),
            captureComplete = true,
            explorationComplete = true,
        )
        assertTrue(ConsentVerificationEngine.verifyAccessibility(evidence).canClaimVerified)
        assertFalse(
            ConsentVerificationEngine.verifyAccessibility(evidence.copy(explorationComplete = false))
                .canClaimVerified,
        )
    }

    @Test
    fun `necessary always on toggle can be exempted only when classified`() {
        val result = ConsentVerificationEngine.verifyAccessibility(
            ConsentVerificationEngine.AccessibilityEvidence(
                toggleStates = mapOf("necessary" to true, "analytics" to false),
                keptOn = setOf("necessary"),
            ),
        )
        assertTrue(result.canClaimVerified)
    }
}
