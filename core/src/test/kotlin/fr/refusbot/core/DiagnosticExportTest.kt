package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {

    @Test
    fun `export json complet et sans mot de passe`() {
        val root = window(
            text("Nous utilisons des cookies avec nos partenaires"),
            node("android.widget.EditText", text = "motdepasse123", password = true),
            SnapshotNode(
                className = "android.widget.Switch",
                text = "Intérêt légitime",
                isCheckable = true,
                isChecked = true,
                isFocusable = true,
                isSelected = false,
                accessibilityActions = listOf(16, 32),
                availableExtraData = listOf("android.view.accessibility.extra.DATA_RENDERING_INFO_KEY"),
                supplementalDescription = "description complémentaire",
                extrasSummary = "chromeRole=switch; availableExtraData=[]; selected=false",
            ),
            button("Tout accepter"),
            button("Gérer les options"),
        )
        val analysis = ScreenAnalyzer(TestRules.rules).analyze(root)
        val meta = DiagnosticExport.Meta(
            packageName = "com.example.jeu", appLabel = "Jeu", capturedAt = "2026-11-16T10:00:00",
            trigger = "test", reason = "aucun bouton", appVersion = "1.0.0", rulesVersion = 1,
            androidVersion = "14 (API 34)", device = "samsung SM-S921B",
        )
        val json = DiagnosticExport.toJson(root, meta.copy(captureTruncated = true), analysis)
        assertFalse(json.contains("motdepasse123"))

        val doc = MiniJson.parse(json) as Map<*, *>
        assertEquals("refusbot-diagnostic-1", doc["format"])
        assertEquals("com.example.jeu", doc["package"])
        assertEquals(true, doc["captureTruncated"])
        assertEquals(6L, doc["nodeCount"])
        val rootMap = doc["root"] as Map<*, *>
        val children = rootMap["children"] as List<*>
        val sw = children[2] as Map<*, *>
        assertEquals("android.widget.Switch", sw["class"])
        assertEquals("Intérêt légitime", sw["text"])
        assertEquals(true, sw["checkable"])
        assertEquals(true, sw["checked"])
        assertEquals(true, sw["focusable"])
        assertEquals(false, sw["selected"])
        assertEquals(listOf(16L, 32L), sw["actions"])
        assertEquals(listOf("android.view.accessibility.extra.DATA_RENDERING_INFO_KEY"), sw["availableExtraData"])
        assertEquals("description complémentaire", sw["supplementalDescription"])
        assertEquals("chromeRole=switch; availableExtraData=[]; selected=false", sw["extras"])
        assertEquals(true, sw["enabled"])
        assertEquals(4, (sw["bounds"] as List<*>).size)
        val pwd = children[1] as Map<*, *>
        assertEquals(true, pwd["password"])
        assertFalse(pwd.containsKey("text"))
        val a = doc["analysis"] as Map<*, *>
        assertEquals(true, a["isConsent"])
        assertTrue((a["buttons"] as List<*>).isNotEmpty())
        assertEquals(0L, a["unknownStateToggles"])
    }

    @Test
    fun `sous arbre explicitement sensible exclut texte metadonnees et descendants`() {
        val sensitive = SnapshotNode(
            className = "android.view.View",
            text = "secret-parent",
            contentDescription = "secret-description",
            viewId = "private-field",
            isAccessibilityDataSensitive = true,
        )
        // addChild renvoie l'enfant : le parent sensible doit être gardé dans sa propre variable.
        sensitive.addChild(SnapshotNode(className = "android.widget.TextView", text = "secret-child"))
        val json = DiagnosticExport.toJson(
            window(sensitive),
            DiagnosticExport.Meta("p", null, "t", "test", null, "1", 1, "16", "device"),
            null,
        )
        assertFalse(json.contains("secret-parent"))
        assertFalse(json.contains("secret-description"))
        assertFalse(json.contains("private-field"))
        assertFalse(json.contains("secret-child"))
        assertTrue(json.contains("\"accessibilityDataSensitive\": true"))
    }

}
