package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveAppPolicyTest {
    @Test fun known_finance_and_authentication_apps_are_sensitive() {
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.n26"))
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.google.android.apps.walletnfcrel"))
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.example.authenticator"))
        assertTrue(SensitiveAppPolicy.isSensitivePackage("fr.impots.gouv"))
    }

    @Test fun known_anti_cheat_games_are_sensitive_for_bulk_selection() {
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.epicgames.fortnite"))
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.activision.callofduty.shooter"))
        assertTrue(SensitiveAppPolicy.isSensitivePackage("com.supercell.brawlstars"))
    }

    @Test fun ordinary_apps_are_not_marked_sensitive() {
        assertFalse(SensitiveAppPolicy.isSensitivePackage("com.example.news"))
        assertFalse(SensitiveAppPolicy.isSensitivePackage("com.chrome.beta"))
    }

    @Test fun bulk_selection_excludes_sensitive_apps_but_keeps_other_apps() {
        assertEquals(
            listOf("com.example.news", "com.chrome.beta"),
            SensitiveAppPolicy.safeForBulkSelection(
                listOf("com.example.news", "com.n26", "com.chrome.beta"),
            ),
        )
    }

    @Test fun package_matching_is_case_and_whitespace_tolerant() {
        assertTrue(SensitiveAppPolicy.isSensitivePackage(" COM.N26 "))
    }
}
