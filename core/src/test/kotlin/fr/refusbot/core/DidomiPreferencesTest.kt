package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fenêtres Didomi observées sur le téléphone le 20/11/2026 (ici.fr, Science & Vie) :
 * - page de préférences longue, « Tout refuser » tout en bas (identifiant btn-toggle-disagree) ;
 * - choix « Refuser / Accepter » par finalité et sections « + » dépliables ;
 * - liste des partenaires avec choix « Bloquer / Autoriser » et détails dépliables.
 * Le moteur doit aller au « Tout refuser » au lieu de cliquer partout.
 */
class DidomiPreferencesTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }

    private fun outcome(app: FakeApp): Outcome {
        val r = runEngine(app, engine, clock)
        assertNotNull("le moteur aurait dû terminer", r.outcome)
        return r.outcome!!
    }

    /** Page de préférences façon ici.fr : « Tout refuser » n'est visible qu'après défilement. */
    private class IciPreferences : FakeApp("com.android.chrome") {
        var scrolled = false
        var refusedAll = false
        var accepted = false
        var partnersOpened = false
        var expanded = 0
        var purposeRefusals = 0

        override fun onScroll(container: UiNode) {
            scrolled = true
        }

        override fun onSwipe() {
            scrolled = true
        }

        private fun purpose(name: String) = group(
            node("android.view.View", text = "+ $name", clickable = true, expandable = true).onExpand { expanded++ },
            button("Refuser").onClick { purposeRefusals++ },
            button("Accepter").onClick { accepted = true },
        )

        override fun build(): SnapshotNode = if (refusedAll) {
            window(text("Les conseils du jardinier"), button("Lire la suite"))
        } else {
            window(
                node(
                    "android.webkit.WebView", scrollable = true, viewId = "didomi-host",
                    children = listOf(
                        text("Bienvenue chez ICI Gestion du consentement"),
                        text("Nos partenaires et nous déposons des cookies et utilisons des informations non sensibles de votre appareil pour afficher des publicités et contenus personnalisés."),
                        text("Vous pouvez accepter ou refuser ces différentes opérations, en totalité ou finalité par finalité."),
                        purpose("Mesure d'audience et web analyse"),
                        purpose("Réseaux sociaux"),
                        purpose("Publicité et ciblage"),
                        button("Voir nos partenaires").onClick { partnersOpened = true },
                        node(
                            "android.widget.Button", text = "Tout refuser", clickable = true,
                            viewId = "btn-toggle-disagree", visible = scrolled,
                        ).onClick { refusedAll = true },
                        node(
                            "android.widget.Button", text = "Tout accepter", clickable = true,
                            viewId = "btn-toggle-agree", visible = scrolled,
                        ).onClick { accepted = true },
                        node(
                            "android.widget.Button", text = "Enregistrer", clickable = true,
                            viewId = "btn-toggle-save", visible = scrolled,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `preferences Didomi - defile jusqu'a Tout refuser au lieu de cliquer partout`() {
        val app = IciPreferences()
        val o = outcome(app)
        assertTrue("« Tout refuser » doit être cliqué : ${o.reason}", app.refusedAll)
        assertFalse("jamais d'acceptation", app.accepted)
        assertEquals("aucune section dépliée", 0, app.expanded)
        assertTrue("aucune section dépliée", app.expandedLabels.isEmpty())
        assertFalse("la liste des partenaires ne doit pas être ouverte", app.partnersOpened)
        assertEquals("aucun « Refuser » d'une seule finalité avant le refus global", 0, app.purposeRefusals)
        assertEquals("tout refuser", app.clickedLabels.last())
        assertTrue(o.success)
        // Un refus direct n'est jamais « vérifié » : la fermeture de la fenêtre ne prouve rien.
        assertEquals(OutcomeStatus.UNVERIFIED, o.status)
    }

    @Test
    fun `identifiant de refus ignore si le texte affiche est une acceptation`() {
        class Trap : FakeApp("com.android.chrome") {
            var accepted = false
            override fun build(): SnapshotNode = window(
                text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                node("android.widget.Button", text = "Tout accepter", clickable = true, viewId = "btn-toggle-disagree")
                    .onClick { accepted = true },
            )
        }
        val app = Trap()
        val a = ScreenAnalyzer(rules).analyze(app.render())
        assertTrue(a.buttons.none { it.kind == ButtonKind.REFUSE || it.kind == ButtonKind.GLOBAL_REJECT })
        runEngine(app, engine, clock)
        assertFalse("le texte « Tout accepter » l'emporte sur l'identifiant", app.accepted)
    }

    @Test
    fun `bouton reconnu par son identifiant Didomi meme avec un texte inhabituel`() {
        val root = window(
            text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
            node("android.widget.Button", text = "Non, merci", clickable = true, viewId = "didomi-notice-disagree-button"),
            button("Accepter et fermer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        val refuse = a.buttons(ButtonKind.REFUSE)
        assertTrue("refus par identifiant attendu", refuse.any { it.phrase == "id:didomi-notice-disagree-button" && it.target != null })
    }

    @Test
    fun `liste des partenaires - details jamais deplies sur une page Bloquer Autoriser`() {
        class Partners : FakeApp("com.android.chrome") {
            var blocked = false
            var accepted = false
            var saved = false
            override fun build(): SnapshotNode = if (saved) window(text("Accueil")) else window(
                text("Sélectionner les partenaires pour ICI"),
                text("Vous pouvez définir vos préférences de consentement pour chaque partenaire. Nos partenaires utilisent des cookies et vos données personnelles."),
                text("AcuityAds Inc."),
                node(
                    "android.view.View", text = "+ Traitements de données basés sur l'intérêt légitime",
                    clickable = true, expandable = true,
                ),
                group(
                    node("android.widget.RadioButton", text = "Bloquer", checkable = true, checked = blocked, clickable = true)
                        .onClick { blocked = true },
                    node("android.widget.RadioButton", text = "Autoriser", checkable = true, checked = !blocked, clickable = true)
                        .onClick { accepted = true },
                    cls = "android.widget.RadioGroup",
                ),
                button("Enregistrer").onClick { saved = true },
            )
        }
        val app = Partners()
        val o = outcome(app)
        assertTrue("aucun détail ne doit être déplié", app.expandedLabels.isEmpty())
        assertTrue("« Bloquer » sélectionné", app.blocked)
        assertFalse("« Autoriser » jamais cliqué", app.accepted)
        assertTrue("les préférences doivent être enregistrées : ${o.reason}", app.saved)
    }

    /**
     * Comme Ultra Premium Direct sous Firefox (20/11/2026) : Firefox ne présente pas les éléments
     * hors de l'écran. « Refuser tout » n'existe dans l'arbre qu'après défilement.
     */
    private class FirefoxDidomi(private val hasGlobal: Boolean) : FakeApp("org.mozilla.firefox") {
        var scrolled = false
        var refusedAll = false
        var masterRefused = false
        var accepted = false
        val purposeRefusals = mutableListOf<String>()
        val order = mutableListOf<String>()
        var saved = false

        override fun onSwipe() {
            scrolled = true
        }

        override fun onScroll(container: UiNode) {
            scrolled = true
        }

        private fun purpose(name: String) = group(
            text("+ $name"),
            group(
                button("Refuser").onClick { purposeRefusals.add(name); order.add(name) },
                button("Accepter").onClick { accepted = true },
            ),
        )

        override fun build(): SnapshotNode = if (refusedAll || saved) {
            window(text("Chiot"), text("Chaton"))
        } else {
            val bottom = if (!scrolled) emptyList() else if (hasGlobal) {
                listOf(button("Refuser tout").onClick { refusedAll = true }, button("Accepter tout").onClick { accepted = true })
            } else {
                listOf(button("Enregistrer").onClick { saved = true })
            }
            window(
                text("Bienvenue chez Ultra Premium Direct !"),
                text("Pour améliorer votre expérience, nous utilisons des cookies et collectons des données avec nos partenaires."),
                group(
                    text("VOUS AUTORISEZ"),
                    group(
                        button("Refuser").onClick { masterRefused = true; order.add("maître") },
                        button("Accepter").onClick { accepted = true },
                    ),
                ),
                purpose("Mesures d'audience"),
                purpose("Publicités personnalisées"),
                purpose("Réseaux sociaux"),
                *bottom.toTypedArray(),
            )
        }
    }

    @Test
    fun `Firefox - defile pour trouver Refuser tout avant de cliquer un par un`() {
        val app = FirefoxDidomi(hasGlobal = true)
        val o = outcome(app)
        assertTrue("« Refuser tout » doit être trouvé et cliqué : ${o.reason}", app.refusedAll)
        assertTrue("aucun « Refuser » un par un avant : ${app.purposeRefusals}", app.purposeRefusals.isEmpty())
        assertFalse(app.masterRefused)
        assertFalse("jamais d'acceptation", app.accepted)
    }

    @Test
    fun `sans refus global - le choix maitre Vous autorisez passe en premier`() {
        val app = FirefoxDidomi(hasGlobal = false)
        runEngine(app, engine, clock)
        assertTrue("« Vous autorisez › Refuser » doit être cliqué", app.masterRefused)
        assertEquals("le choix maître est le premier « Refuser » cliqué : ${app.order}", "maître", app.order.first())
        assertFalse("jamais d'acceptation", app.accepted)
    }

    @Test
    fun `Tous les partenaires - Bloquer maitre clique en premier`() {
        class Partners : FakeApp("com.android.chrome") {
            var all = false
            val vendors = mutableListOf<String>()
            var accepted = false
            private fun pair(onBlock: () -> Unit, blocked: Boolean) = group(
                node("android.widget.RadioButton", text = "Bloquer", checkable = true, checked = blocked, clickable = true)
                    .onClick(onBlock),
                node("android.widget.RadioButton", text = "Autoriser", checkable = true, checked = !blocked, clickable = true)
                    .onClick { accepted = true },
                cls = "android.widget.RadioGroup",
            )
            override fun build(): SnapshotNode = window(
                text("Sélectionner les partenaires pour ICI"),
                text("Vous pouvez définir vos préférences de consentement pour chaque partenaire. Nos partenaires utilisent des cookies et vos données personnelles."),
                group(text("Tous les partenaires"), pair({ all = true }, all)),
                group(text("AcuityAds Inc."), pair({ vendors.add("acuity") }, all || "acuity" in vendors)),
                group(text("Adform A/S"), pair({ vendors.add("adform") }, all || "adform" in vendors)),
            )
        }
        val app = Partners()
        val r = runEngine(app, engine, clock)
        assertTrue("« Tous les partenaires › Bloquer » doit être cliqué", app.all)
        assertTrue("les partenaires sont déjà bloqués par le choix maître : ${app.vendors}", app.vendors.isEmpty())
        assertFalse("jamais « Autoriser »", app.accepted)
        assertNotNull(r)
    }
}

