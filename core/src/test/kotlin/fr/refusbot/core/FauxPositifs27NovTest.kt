package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Faux positifs du 27/11 (Chrome, profil Strict) : CYRAVA a pris des PAGES QUI PARLENT DE COOKIES pour des
 * fenêtres de consentement, alors qu'aucune fenêtre n'était affichée.
 * - 16 h 02, README GitHub de CYRAVA : reconnu (profil Prudent : rien fait, mais en Strict il aurait agi) ;
 * - 17 h 35, journal LinuxFr présentant CYRAVA : clic sur le lien « Modifier mes préférences » du menu, et la
 *   case « licence CC BY-SA » du formulaire repérée comme interrupteur à décocher ;
 * - 17 h 48, fil XDA présentant CYRAVA : 14 s de défilements et un clic « More options », alors que l'analyse
 *   concluait « pas une fenêtre de consentement » (les boutons « gras / souligné » de l'éditeur de réponse
 *   passaient pour des interrupteurs).
 * Les trois captures sont les vrais diagnostics du téléphone, pseudonyme de l'auteur remplacé. Le reste est
 * simulé : aucun essai sur téléphone ici.
 */
class FauxPositifs27NovTest {
    private val rules = TestRules.rules
    private val chrome = "com.android.chrome"
    // --- Scénarios simulés ----------------------------------------------------------------------

    private val viewport = Bounds(0, 200, 1080, 2300)

    /** Page web : un conteneur qui remplit toute la zone de la page, plus des blocs à part (bandeau). */
    private fun page(content: List<SnapshotNode>, vararg overlays: SnapshotNode): SnapshotNode {
        val body = node("android.view.View", bounds = Bounds(20, 200, 1060, 2300), children = content)
        return Tree.window(node("android.webkit.WebView", bounds = viewport, children = listOf(body) + overlays))
    }

    /** Article qui présente une appli anti-cookies, avec menu, formulaire et éditeur (cas LinuxFr / XDA). */
    private fun article(licenceChecked: Boolean = true, boldOn: Boolean = true) = listOf(
        text("Une appli libre qui refuse toute seule les fenêtres de cookies"),
        text("Elle détecte les fenêtres de consentement (RGPD, IAB TCF) et décoche les partenaires et l'intérêt légitime."),
        text("Marre de cliquer « Tout refuser » ? Elle ne clique jamais sur « Accepter »."),
        node("android.view.View", desc = "Modifier mes préférences", clickable = true),
        node("android.widget.Button", text = "More options…", clickable = true),
        node("android.widget.CheckBox", text = "Je place ce document sous licence CC BY-SA", checkable = true,
            checked = licenceChecked, clickable = true),
        node("android.widget.ToggleButton", text = "Gras", checkable = true, checked = boldOn, clickable = true),
    )

    private class Static(private val tree: () -> SnapshotNode) : FakeApp("com.android.chrome") {
        override fun build() = tree()
    }

    private fun field() = node("android.widget.EditText", editable = true, clickable = true)
    private fun toggleButton(label: String, on: Boolean = false) =
        node("android.widget.ToggleButton", text = label, checkable = true, checked = on, clickable = true)

    /** README GitHub (16 h 02) : explications de l'appli, liens vers les réglages, onglets « Preview / Code ». */
    private fun githubReadme() = page(
        listOf(
            toggleButton("Preview", on = true), toggleButton("Code"), toggleButton("Blame"),
            text("CYRAVA automatically rejects cookie / tracker consent pop-ups in the apps you choose."),
            node("android.view.View", text = "Open accessibility settings", clickable = true),
            node("android.view.View", children = listOf(text("Read the notice"), text(" and accept."), text("then tap"), text("OK"))),
            text("During a long list, CYRAVA only re-reads the cookie pop-up. Privacy: no data collection."),
            node("android.view.View", text = "Settings", clickable = true),
        ),
    )

    /** Journal LinuxFr (17 h 35) : article, lien « Modifier mes préférences » du menu, formulaire avec licence. */
    private fun linuxfrJournal() = page(
        listOf(
            node("android.view.View", desc = "Modifier mes préférences", clickable = true),
            text("Journal : une appli Android libre qui refuse toute seule les fenêtres de cookies"),
            text("Marre de cliquer « Tout refuser » et de décocher des listes de 200 « partenaires » ?"),
            text("Elle détecte les fenêtres de consentement (RGPD, IAB TCF) et décoche l'« intérêt légitime »."),
            field(), field(), field(),
            node("android.widget.CheckBox", text = "Je place ce document sous licence CC BY-SA", checkable = true,
                checked = true, clickable = true),
        ),
    )

    /** Fil XDA (17 h 48) : présentation, menus « More options… », éditeur de réponse (gras, souligné…). */
    private fun xdaThread() = page(
        listOf(
            text("Automatically reject cookie & tracking consent popups"),
            text("Detects cookie/consent banners and clicks \"Reject\" for you"),
            text("Unchecks every non-essential purpose and vendor. No ads, no trackers."),
            node("android.widget.Button", text = "More options…", clickable = true),
            toggleButton("Bold"), toggleButton("Italic"), toggleButton("Strike-through", on = true), toggleButton("Underline", on = true),
            field(),
        ),
    )

    private val pages = mapOf("GitHub" to ::githubReadme, "LinuxFr" to ::linuxfrJournal, "XDA" to ::xdaThread)

    @Test
    fun `pages du 27-11 - une page qui parle de cookies n'ouvre aucune session`() {
        for ((name, build) in pages) {
            val a = ScreenAnalyzer(rules).analyze(Static(build).render(), webContentOnly = true)
            assertTrue("$name : le panneau est la page entière", a.pageWide)
            assertFalse("$name : aucune session ne doit démarrer", a.canStartSession)
        }
    }

    @Test
    fun `pages du 27-11 - aucun clic ni defilement, en profil Strict comme en Prudent`() {
        for ((name, build) in pages) {
            val app = Static(build)
            val clock = FakeClock()
            val rr = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
            assertEquals("$name (Strict)", null, rr.outcome)
            assertEquals("$name (Strict) : ${app.clickedLabels}", 0, app.opsCount)
            val prudent = RefusalEngine(rules).next(Screen(chrome, app.render())) { Policy.PRUDENT }
            assertEquals("$name (Prudent)", Step.Idle, prudent)
        }
    }

    @Test
    fun `article sur les cookies - ni lien du menu, ni case du formulaire, ni bouton de l'editeur`() {
        val app = Static { page(article()) }
        val clock = FakeClock()
        val rr = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertEquals("aucune session", null, rr.outcome)
        assertTrue("aucun clic : ${app.clickedLabels}", app.clickedLabels.isEmpty())
    }

    @Test
    fun `article - un fragment de phrase n'est pas un bouton`() {
        // README GitHub (16 h 02) : « … and accept. » et « OK » au milieu des explications.
        val app = Static {
            page(
                article() + listOf(
                    node("android.view.View", children = listOf(
                        text("Open the app, read the notice"), text(" and accept."), text("then tap"), text("OK"),
                    )),
                ),
            )
        }
        val a = ScreenAnalyzer(rules).analyze(app.render(), webContentOnly = true)
        assertTrue(a.pageWide)
        assertFalse(a.pageWideChoice)
        assertFalse(a.canStartSession)
    }

    @Test
    fun `vrai bandeau par-dessus un article sur les cookies - refuse normalement`() {
        var refused = false
        var accepted = false
        val app = object : FakeApp("com.android.chrome") {
            override fun build(): SnapshotNode {
                val content = article()
                if (refused) return page(content)
                val banner = node(
                    "android.view.View", bounds = Bounds(0, 1700, 1080, 2300),
                    children = listOf(
                        node("android.widget.TextView", text = "Nous et nos partenaires utilisons des cookies pour mesurer l'audience.",
                            bounds = Bounds(40, 1720, 1040, 1900)),
                        node("android.widget.Button", text = "Tout refuser", clickable = true, bounds = Bounds(40, 1950, 520, 2050))
                            .onClick { refused = true },
                        node("android.widget.Button", text = "Tout accepter", clickable = true, bounds = Bounds(560, 1950, 1040, 2050))
                            .onClick { accepted = true },
                    ),
                )
                return page(content, banner)
            }
        }
        val first = ScreenAnalyzer(rules).analyze(app.render(), webContentOnly = true)
        assertFalse("le bandeau est un bloc à part", first.pageWide)
        assertTrue(first.canStartSession)
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
        assertFalse(accepted)
        assertTrue("refus cliqué : ${app.clickedLabels}", refused)
        assertEquals(listOf("tout refuser"), app.clickedLabels)
        assertEquals("refus non vérifiable, session arrêtée après le refus", OutcomeStatus.UNVERIFIED, o!!.status)
    }

    @Test
    fun `page de reglages du compte qui parle de cookies - aucune case decochee, aucun clic`() {
        // Relecture sécurité du 27/11 : un bouton « Enregistrer » seul suffisait à démarrer sur la page entière.
        var saved = false
        val prefs = mutableMapOf("Recevoir la lettre d'information" to true, "Profil visible par les membres" to true)
        val app = object : FakeApp("com.android.chrome") {
            override fun build() = page(
                listOf(
                    text("Mon compte : vie privée, cookies et partenaires (RGPD)"),
                    *prefs.keys.map { k ->
                        node("android.widget.CheckBox", text = k, checkable = true, checked = prefs.getValue(k), clickable = true)
                            .onClick { prefs[k] = !prefs.getValue(k) }
                    }.toTypedArray(),
                    button("Enregistrer").onClick { saved = true },
                ),
            )
        }
        val a = ScreenAnalyzer(rules).analyze(app.render(), webContentOnly = true)
        assertTrue(a.pageWide)
        assertFalse(a.canStartSession)
        val clock = FakeClock()
        runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertTrue("aucun clic : ${app.clickedLabels}", app.clickedLabels.isEmpty())
        assertTrue(prefs.values.all { it })
        assertFalse(saved)
    }

    @Test
    fun `fenetre de consentement plein ecran - toujours traitee`() {
        // Mur de consentement qui occupe toute la page : un vrai refus visible, la session démarre.
        val app = Static {
            page(
                listOf(
                    text("Vos choix concernant les cookies"),
                    text("Nous et nos partenaires utilisons des cookies et traitons vos données personnelles."),
                    button("Continuer sans accepter"),
                    button("Tout accepter"),
                ),
            )
        }
        val a = ScreenAnalyzer(rules).analyze(app.render(), webContentOnly = true)
        assertTrue(a.pageWide)
        assertTrue(a.pageWideChoice)
        assertTrue(a.canStartSession)
    }

    @Test
    fun `apres un bandeau, une page entiere avec des interrupteurs ne prolonge pas la session`() {
        // Bandeau inconnu sans refus : « Personnaliser » est cliqué, mais le site ouvre un article (cas XDA :
        // boutons « gras » de l'éditeur pris pour des interrupteurs). CYRAVA s'arrête sans rien toucher d'autre.
        var opened = false
        var accepted = false
        val app = object : FakeApp("com.android.chrome") {
            override fun build(): SnapshotNode {
                if (opened) {
                    return page(
                        listOf(
                            text("Cette appli décoche les partenaires et refuse les cookies à votre place."),
                            text("Elle lit les fenêtres de consentement et l'intérêt légitime."),
                            node("android.widget.ToggleButton", text = "Gras", checkable = true, checked = true, clickable = true),
                            node("android.widget.ToggleButton", text = "Souligné", checkable = true, checked = true, clickable = true),
                        ),
                    )
                }
                val banner = node(
                    "android.view.View", bounds = Bounds(0, 1700, 1080, 2300),
                    children = listOf(
                        node("android.widget.TextView", text = "Nous et nos partenaires utilisons des cookies.",
                            bounds = Bounds(40, 1720, 1040, 1900)),
                        node("android.widget.Button", text = "Personnaliser", clickable = true, bounds = Bounds(40, 1950, 520, 2050))
                            .onClick { opened = true },
                        node("android.widget.Button", text = "Tout accepter", clickable = true, bounds = Bounds(560, 1950, 1040, 2050))
                            .onClick { accepted = true },
                    ),
                )
                return page(listOf(text("Les titres du jour")), banner)
            }
        }
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
        assertFalse(accepted)
        assertTrue("options ouvertes", opened)
        assertEquals("seul « Personnaliser » a été cliqué : ${app.clickedLabels}", listOf("personnaliser"), app.clickedLabels)
        assertTrue(o != null && !o.success)
    }
}
