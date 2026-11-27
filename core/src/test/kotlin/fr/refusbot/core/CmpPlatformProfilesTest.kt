package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Profils de plateformes ajoutés à partir de la bibliothèque CMP (sélecteurs d'identifiants HTML
 * publiés par DuckDuckGo Autoconsent). Ils servent seulement à NOMMER la plateforme.
 */
class CmpPlatformProfilesTest {
    private val rules = TestRules.rules
    private val analyzer = ScreenAnalyzer(rules)

    private fun platformOf(viewId: String): String = analyzer.analyze(
        window(
            node(
                "android.view.View",
                viewId = viewId,
                children = listOf(text("Nous utilisons des cookies avec nos partenaires."), button("Tout refuser")),
            ),
        ),
    ).platform

    @Test
    fun `nouvelles plateformes reconnues par leur identifiant HTML`() {
        val cases = mapOf(
            "footer_tc_privacy_button" to "Commanders Act (TrustCommander)",
            "cmplz-cookiebanner-container" to "Complianz",
            "cky-consent" to "CookieYes",
            "ccc-notify" to "CIVIC Cookie Control",
            "coiOverlay" to "Cookie Information",
            "BorlabsCookieBox" to "Borlabs Cookie",
            "cookie-law-info-bar" to "WebToffee (CookieLawInfo)",
            "lanyard_root" to "Ketch",
            "fast-cmp-root" to "FastCMP",
            "appconsent-banner" to "AppConsent (SFBX)",
        )
        for ((id, expected) in cases) assertEquals(id, expected, platformOf(id))
    }

    @Test
    fun `les anciens profils gardent la priorite`() {
        assertEquals("Didomi", platformOf("didomi-notice"))
        assertEquals("OneTrust", platformOf("onetrust-banner-sdk"))
        assertEquals("Consentmanager", platformOf("ncmp__tool"))
    }

    @Test
    fun `un identifiant de plateforme seul ne cree aucun bouton de refus`() {
        val a = analyzer.analyze(
            window(node("android.view.View", viewId = "cmplz-cookiebanner-container", children = listOf(text("Bienvenue sur notre boutique")))),
        )
        assertTrue(a.buttons.none { it.kind == ButtonKind.REFUSE || it.kind == ButtonKind.GLOBAL_REJECT })
    }

    @Test
    fun `aucun repere de plateforme n'est partage entre deux profils`() {
        val all = rules.platforms.flatMap { p -> p.markers.map { it to p.name } }
        for ((m, name) in all) for ((m2, name2) in all) {
            if (name != name2) assertTrue("$m ($name) est inclus dans $m2 ($name2)", !m2.contains(m))
        }
    }

    @Test
    fun `chaque profil moteur est documente dans la bibliotheque CMP`() {
        // Le catalogue canonique est une ressource de l'application, pas un fichier docs/.
        // Gradle transmet son chemin absolu pour que le test fonctionne quel que soit le dossier courant.
        val configured = System.getProperty("refusbot.cmp.catalog")?.let(::File)
        val file = listOfNotNull(
            configured,
            File("../app/src/main/assets/cmp-catalog.json"),
            File("app/src/main/assets/cmp-catalog.json"),
        ).firstOrNull { it.isFile } ?: error("cmp-catalog.json introuvable dans app/src/main/assets/")
        val catalog = MiniJson.parse(file.readText()) as Map<*, *>
        val linked = (catalog["providers"] as List<*>).mapNotNull { (it as Map<*, *>)["engineProfile"] as? String }.toSet()
        for (p in rules.platforms) assertTrue("profil sans fiche : ${p.name}", p.name in linked)
    }
}
