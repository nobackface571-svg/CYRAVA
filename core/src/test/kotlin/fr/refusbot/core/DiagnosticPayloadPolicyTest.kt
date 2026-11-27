package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticPayloadPolicyTest {
    @Test fun small_diagnostic_is_allowed() {
        assertTrue(DiagnosticPayloadPolicy.isAllowed("{\"format\":\"refusbot-diagnostic-1\"}"))
    }

    @Test fun payload_at_limit_is_allowed() {
        val json = "x".repeat(DiagnosticPayloadPolicy.MAX_BYTES)
        assertTrue(DiagnosticPayloadPolicy.isAllowed(json))
    }

    @Test fun payload_over_limit_is_rejected() {
        val json = "x".repeat(DiagnosticPayloadPolicy.MAX_BYTES + 1)
        assertFalse(DiagnosticPayloadPolicy.isAllowed(json))
    }

    @Test fun utf8_size_not_character_count_is_used() {
        val json = "é".repeat(DiagnosticPayloadPolicy.MAX_BYTES / 2 + 1)
        assertFalse(DiagnosticPayloadPolicy.isAllowed(json))
    }
}
