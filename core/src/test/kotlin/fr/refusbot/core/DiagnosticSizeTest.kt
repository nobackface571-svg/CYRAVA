package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Une page avec des centaines de partenaires (Paddock GP, 1 756 partenaires, 20/11/2026) dépassait
 * la limite de taille : la capture échouait (« espace de stockage indisponible »).
 */
class DiagnosticSizeTest {
    private val meta = DiagnosticExport.Meta(
        packageName = "org.mozilla.firefox", appLabel = "Firefox", capturedAt = "2026-11-20T10:22:00",
        trigger = "test", reason = null, appVersion = "test", rulesVersion = 2,
        androidVersion = "16 (API 36)", device = "samsung SM-S711B",
    )

    private fun bigPage(partners: Int) = window(
        text("Paddock GP demande votre consentement pour l'utilisation de vos données personnelles."),
        button("Refuser"),
        button("Accepter"),
        group(*(1..partners).map { i ->
            group(
                text("Partenaire publicitaire numéro $i avec une description assez longue", visible = i < 10),
                button("Bloquer", visible = i < 10),
                button("Autoriser", visible = i < 10),
            )
        }.toTypedArray()),
    )

    @Test
    fun `grande page - l'export tient dans la limite et garde le visible`() {
        val root = bigPage(1500)
        val max = 200_000
        val json = DiagnosticExport.toJsonFitting(root, meta, ScreenAnalyzer(TestRules.rules).analyze(root), maxBytes = max)
        assertTrue("taille ${json.length}", json.toByteArray(Charsets.UTF_8).size <= max)
        assertTrue("le mode réduit doit être indiqué", json.contains("exportMode"))
        assertTrue("le bouton visible « Refuser » doit rester", json.contains("Refuser"))
        assertTrue(MiniJson.parse(json) is Map<*, *>)
    }

    @Test
    fun `petite page - export complet inchange`() {
        val root = bigPage(3)
        val json = DiagnosticExport.toJsonFitting(root, meta, null)
        assertTrue(!json.contains("exportMode"))
        assertTrue(json == DiagnosticExport.toJson(root, meta, null))
    }

    @Test
    fun `limite tres basse - export tronque mais valide`() {
        val root = bigPage(1500)
        val json = DiagnosticExport.toJsonFitting(root, meta, null, maxBytes = 20_000)
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= 20_000)
        assertTrue(json.contains("tronqu"))
        assertTrue(MiniJson.parse(json) is Map<*, *>)
    }
}
