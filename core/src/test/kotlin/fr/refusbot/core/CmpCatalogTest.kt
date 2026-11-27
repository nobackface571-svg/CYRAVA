package fr.refusbot.core
import org.junit.Assert.*
import org.junit.Test
class CmpCatalogTest {
    private val profiles = listOf(
        CmpCatalogProfile("didomi", "Didomi", listOf(CmpCatalogMarker("didomi", MarkerSource.VIEW_ID, true)), setOf("privacy preferences")),
        CmpCatalogProfile("onetrust", "OneTrust", listOf(CmpCatalogMarker("onetrust-banner-sdk", MarkerSource.VIEW_ID, true)), setOf("privacy preference center")),
    )
    @Test fun one_marker_is_only_probable() {
        val r = CmpCatalogMatcher.match(profiles, listOf("app:id/didomi-banner"), emptyList())
        assertEquals("didomi", r.profileId); assertEquals(CmpMatchConfidence.PROBABLE, r.confidence); assertFalse(r.mayAuthorizeAction)
    }
    @Test fun independent_marker_and_phrase_are_high_confidence() {
        val r = CmpCatalogMatcher.match(profiles, listOf("onetrust-banner-sdk"), listOf("Privacy Preference Center"))
        assertEquals("onetrust", r.profileId); assertEquals(CmpMatchConfidence.HIGH, r.confidence); assertFalse(r.mayAuthorizeAction)
    }
    @Test fun dom_symbols_are_not_android_view_ids() {
        val p = listOf(CmpCatalogProfile("web", "Web", listOf(CmpCatalogMarker("window.Didomi", MarkerSource.DOM_EXPORT, true))))
        assertEquals(CmpMatchConfidence.UNKNOWN, CmpCatalogMatcher.match(p, listOf("window.Didomi"), emptyList()).confidence)
    }
    @Test fun no_evidence_is_unknown() {
        assertEquals(CmpMatchConfidence.UNKNOWN, CmpCatalogMatcher.match(profiles, emptyList(), listOf("Accueil")).confidence)
    }
    @Test fun equal_profiles_are_ambiguous() {
        val p = profiles + profiles.first().copy(id="didomi-fork")
        assertEquals(CmpMatchConfidence.AMBIGUOUS, CmpCatalogMatcher.match(p, listOf("didomi"), listOf("privacy preferences")).confidence)
    }
}
