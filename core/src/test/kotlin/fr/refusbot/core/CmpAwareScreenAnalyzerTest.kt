package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CmpAwareScreenAnalyzerTest {
    @Test fun wrapper_adds_provider_metadata_without_granting_action_permission() {
        val root = SnapshotNode(bounds = Bounds(0, 0, 400, 800))
        root.addChild(SnapshotNode(text = "Cookies et partenaires", bounds = Bounds(0, 0, 300, 40)))
        root.addChild(SnapshotNode(text = "Vos données personnelles", bounds = Bounds(0, 50, 300, 90)))
        root.addChild(SnapshotNode(className = "android.widget.Button", text = "Tout accepter", isClickable = true, bounds = Bounds(0, 100, 200, 150)))
        root.addChild(SnapshotNode(viewId = "io.didomi.sdk:id/didomi-host", bounds = Bounds(0, 160, 300, 700)))
        root.assignKeys()
        val profiles = listOf(
            CmpCatalogProfile("didomi", "Didomi", listOf(CmpCatalogMarker("didomi", MarkerSource.VIEW_ID, true)))
        )
        val result = CmpAwareScreenAnalyzer(ScreenAnalyzer(TestRules.rules), profiles).analyze(root)
        assertEquals("didomi", result.cmp.profileId)
        assertFalse(result.cmp.mayAuthorizeAction)
        assertEquals(ConsentUiState.PRESENT, result.consentUi)
        assertEquals(ConsentActionAssessment.NEEDS_REVIEW, result.actionAssessment)
    }

    @Test fun unknown_provider_does_not_disable_generic_consent_recognition() {
        val root = SnapshotNode(bounds = Bounds(0, 0, 400, 800))
        root.addChild(SnapshotNode(text = "Nous utilisons des cookies et des partenaires", bounds = Bounds(0, 0, 300, 40)))
        root.addChild(SnapshotNode(text = "Vos données personnelles", bounds = Bounds(0, 50, 300, 90)))
        root.addChild(SnapshotNode(className = "android.widget.Button", text = "Tout refuser", isClickable = true, bounds = Bounds(0, 100, 200, 150)))
        root.assignKeys()
        val result = CmpAwareScreenAnalyzer(ScreenAnalyzer(TestRules.rules), emptyList()).analyze(root)
        assertEquals(CmpMatchConfidence.UNKNOWN, result.cmp.confidence)
        assertEquals(ConsentUiState.PRESENT, result.consentUi)
        assertEquals(ConsentActionAssessment.REFUSAL_ROUTE_CANDIDATE, result.actionAssessment)
        assertFalse(result.cmp.mayAuthorizeAction)
    }

    @Test fun wrapper_does_not_read_or_match_sensitive_subtrees() {
        val root = SnapshotNode(bounds = Bounds(0, 0, 400, 800))
        root.addChild(SnapshotNode(text = "Cookies", bounds = Bounds(0, 0, 300, 40)))
        root.addChild(SnapshotNode(text = "Vos données", bounds = Bounds(0, 50, 300, 90)))
        root.addChild(SnapshotNode(className = "android.widget.Button", text = "Tout accepter", isClickable = true, bounds = Bounds(0, 100, 200, 150)))
        root.addChild(SnapshotNode(viewId = "secret-didomi", text = "secret", isEditable = true, bounds = Bounds(0, 160, 300, 200)))
        root.assignKeys()
        val profiles = listOf(
            CmpCatalogProfile("didomi", "Didomi", listOf(CmpCatalogMarker("didomi", MarkerSource.VIEW_ID, true)))
        )
        val result = CmpAwareScreenAnalyzer(ScreenAnalyzer(TestRules.rules), profiles).analyze(root)
        assertEquals(CmpMatchConfidence.UNKNOWN, result.cmp.confidence)
    }
}
