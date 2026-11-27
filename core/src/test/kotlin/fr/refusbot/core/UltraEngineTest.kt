package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fonctions « Ultra » : niveaux de confiance, plateformes, profils STRICT / PRUDENT / OBSERVATION,
 * statuts honnêtes, arrêt d'urgence, longues listes, langues, empreinte structurelle.
 * Les numéros « TEST n » renvoient au plan de tests du cahier des charges.
 */
class UltraEngineTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }
    private val analyzer = ScreenAnalyzer(rules)

    private fun run(app: FakeApp, policy: Policy = Policy.STRICT): RunResult {
        val acts = mutableListOf<Step.Act>()
        repeat(2_000) { i ->
            when (val step = engine.next(Screen(app.packageName, app.render())) { policy }) {
                Step.Idle -> return RunResult(null, i, acts)
                is Step.Wait -> clock.advance(step.delayMs)
                is Step.Act -> {
                    acts.add(step)
                    step.ops.forEach(app::perform)
                    clock.advance(250)
                }
                is Step.Done -> return RunResult(step.outcome, i, acts)
            }
        }
        error("le moteur ne s'est pas arrêté")
    }

    private fun gameScreen() = window(text("Accueil"), button("Jouer"))

    /** Bandeau simple : « Tout refuser » + « Tout accepter ». */
    private class Banner(val markerId: String? = null) : FakeApp() {
        var result: String? = null
        override fun build(): SnapshotNode = if (result != null) {
            window(text("Accueil"), button("Jouer"))
        } else {
            window(
                node(
                    "android.view.View", viewId = markerId,
                    children = listOf(
                        text("Nous utilisons des cookies avec nos partenaires."),
                        button("Tout accepter").onClick { result = "accept" },
                        button("Tout refuser").onClick { result = "refus" },
                    ),
                ),
            )
        }
    }

    // --- Confiance et plateformes -----------------------------------------------------------

    @Test
    fun `niveaux de confiance`() {
        val certain = analyzer.analyze(Banner(markerId = "didomi-notice").render())
        assertEquals("Didomi", certain.platform)
        assertEquals(Confidence.CERTAIN, certain.confidence)

        val probable = analyzer.analyze(window(text("Cookies et partenaires"), button("Tout refuser")))
        assertEquals(Confidence.PROBABLE, probable.confidence)

        val insuffisant = analyzer.analyze(window(text("Cookies"), button("Jouer")))
        assertEquals(Confidence.INSUFFISANT, insuffisant.confidence)
    }

    @Test
    fun `plateformes reconnues par identifiant ou formulation`() {
        fun platformOf(root: SnapshotNode) = analyzer.analyze(root).platform
        assertEquals(
            "OneTrust",
            platformOf(window(node("android.view.View", viewId = "onetrust-banner-sdk", children = listOf(text("We use cookies with our partners"), button("Reject All"))))),
        )
        assertEquals(
            "Google UMP (Funding Choices)",
            platformOf(window(text("Préférences concernant les fournisseurs"), text("Intérêt légitime"), button("Confirmer les choix"))),
        )
        assertEquals(
            PLATFORM_GENERIC_TCF,
            platformOf(window(text("Nos fournisseurs invoquent un intérêt légitime"), button("Enregistrer"))),
        )
        assertEquals(PLATFORM_UNKNOWN, platformOf(window(text("Cookies et vie privée"), button("Tout refuser"))))
    }

    // --- Profils -----------------------------------------------------------------------------

    @Test
    fun `mode observation - aucune action, fenetre journalisee comme observee`() {
        val app = Banner()
        val r = run(app, Policy.OBSERVATION)
        val o = r.outcome!!
        assertEquals(OutcomeStatus.OBSERVED, o.status)
        assertTrue(r.acts.isEmpty())
        assertEquals(0, app.opsCount)
        assertNull(app.result)
        // Pas de nouvelle observation en rafale.
        assertNull(run(app, Policy.OBSERVATION).outcome)
    }

    @Test
    fun `mode prudent - n'agit que si la reconnaissance est certaine`() {
        val generic = Banner()
        val o1 = run(generic, Policy.PRUDENT).outcome!!
        assertEquals(OutcomeStatus.OBSERVED, o1.status)
        assertEquals(Confidence.PROBABLE, o1.confidence)
        assertEquals(0, generic.opsCount)

        val didomi = Banner(markerId = "didomi-popup")
        val o2 = run(didomi, Policy.PRUDENT).outcome!!
        assertEquals(OutcomeStatus.UNVERIFIED, o2.status)
        assertEquals("refus", didomi.result)
        assertEquals("Didomi", o2.platform)
    }

    // --- Statuts --------------------------------------------------------------------------------

    @Test
    fun `statuts - direct, impossible, interrompu`() {
        assertEquals(OutcomeStatus.UNVERIFIED, run(Banner()).outcome!!.status)

        class AcceptOnly : FakeApp("com.autre.appli") {
            override fun build() = window(
                text("Nous utilisons des cookies avec nos partenaires."),
                text("Vos données personnelles sont utilisées pour des finalités publicitaires."),
                button("Tout accepter"),
            )
        }
        assertEquals(OutcomeStatus.IMPOSSIBLE, run(AcceptOnly()).outcome!!.status)

        val consent = window(text("Cookies"), text("Nos partenaires"), button("Gérer les options"), button("Tout accepter"))
        assertTrue(engine.next(Screen("a.b", consent)) is Step.Act)
        val done = engine.next(Screen("c.d", window(text("Autre appli")))) as Step.Done
        assertEquals(OutcomeStatus.INTERRUPTED, done.outcome.status) // TEST 11 : changement de fenêtre
    }

    @Test
    fun `validation bloquee - interrupteur dont l'etat n'est pas publie`() {
        class Opaque : FakeApp() {
            var options = false
            var saved = false
            override fun build(): SnapshotNode = when {
                saved -> gameScreen()
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                    button("Tout accepter"), button("Gérer les options").onClick { options = true },
                )
                else -> window(
                    text("Intérêt légitime"), text("Fournisseurs"),
                    group(text("Finalité 1"), node("android.widget.CheckBox", text = "Intérêt légitime", clickable = true)),
                    button("Confirmer les choix").onClick { saved = true },
                )
            }
        }
        val app = Opaque()
        val o = run(app).outcome!!
        assertEquals(OutcomeStatus.IMPOSSIBLE, o.status)
        assertFalse("la validation ne doit pas être envoyée avec un état inconnu", app.saved)
        assertTrue(o.reason!!, o.reason.contains("non communiqué"))
    }

    @Test
    fun `TEST 12 - arret d'urgence pendant le traitement`() {
        val app = UmpLike(vendors = 40)
        // Quelques étapes, puis arrêt d'urgence.
        repeat(3) {
            when (val step = engine.next(Screen(app.packageName, app.render()))) {
                is Step.Act -> step.ops.forEach(app::perform)
                else -> Unit
            }
            clock.advance(300)
        }
        val o = engine.abort("arrêt d'urgence demandé")
        assertNotNull(o)
        assertEquals(OutcomeStatus.INTERRUPTED, o!!.status)
        assertFalse(engine.isActive)
        // Plus aucune action sur cette fenêtre ensuite.
        val before = app.opsCount
        assertNull(run(app).outcome)
        assertEquals(before, app.opsCount)
        assertFalse(app.confirmed)
    }

    // --- Sécurité -------------------------------------------------------------------------------

    @Test
    fun `TEST 10 - bouton de confirmation ambigu Accepter mes choix - abstention`() {
        class Ambiguous : FakeApp() {
            var options = false
            var clicked = false
            override fun build(): SnapshotNode = if (!options) {
                window(text("Cookies et partenaires"), text("Vos données"), button("Personnaliser").onClick { options = true }, button("Tout accepter"))
            } else {
                window(
                    text("Vos choix concernant les cookies et nos partenaires"),
                    group(text("Mesure d'audience"), switch("Mesure", false)),
                    button("Accepter mes choix").onClick { clicked = true },
                )
            }
        }
        val app = Ambiguous()
        val o = run(app).outcome!!
        assertFalse("« Accepter mes choix » ne doit jamais être touché", app.clicked)
        assertEquals(OutcomeStatus.IMPOSSIBLE, o.status)
    }

    // --- Longues listes -------------------------------------------------------------------------

    /** Liste « native » de fournisseurs : seules [pageSize] lignes existent dans l'arbre à la fois. */
    private class UmpLike(vendors: Int, private val pageSize: Int = 10) : FakeApp() {
        var options = false
        var confirmed = false
        var accepted = false
        var offset = 0
        val li = BooleanArray(vendors) { true }

        override fun onScroll(container: UiNode) {
            if (offset + pageSize < li.size) offset += pageSize
        }

        override fun build(): SnapshotNode = when {
            confirmed || accepted -> window(text("Accueil"), button("Jouer"))
            !options -> window(
                text("Example demande votre consentement pour utiliser vos données personnelles"),
                text("Nos partenaires"),
                button("Autoriser").onClick { accepted = true },
                button("Gérer les options").onClick { options = true },
            )
            else -> window(
                text("Préférences concernant les fournisseurs"),
                text("Ces fournisseurs invoquent un intérêt légitime."),
                group(
                    *(offset until minOf(offset + pageSize, li.size)).map { i ->
                        group(text("Fournisseur $i"), switch("Intérêt légitime", li[i]).onClick { li[i] = !li[i] })
                    }.toTypedArray(),
                    cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true,
                ),
                button("Tout accepter").onClick { accepted = true },
                button("Confirmer les choix").onClick { confirmed = true },
            )
        }
    }

    @Test
    fun `TEST 13 - 300 fournisseurs - tous traites sans repetition, fin de liste, 40 actions maximum`() {
        val app = UmpLike(vendors = 300)
        val o = run(app).outcome!!
        assertTrue("échec : ${o.reason}", o.status.isRefusal)
        assertTrue(app.confirmed)
        assertFalse(app.accepted)
        assertEquals("intérêts légitimes restés actifs", 0, app.li.count { it })
        assertEquals(300, o.togglesOff)
        assertTrue("actions : ${o.actions}", o.actions <= rules.limits.maxActions)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        // Aucune répétition : chaque interrupteur n'a été touché qu'une fois.
        assertEquals(300, app.clickedLabels.count { it == "interet legitime" })
    }

    // --- Empreinte structurelle ---------------------------------------------------------------

    @Test
    fun `TEST 14 - empreinte structurelle stable entre langues, modifiee par une nouvelle structure`() {
        val fr = analyzer.analyze(window(node("android.view.View", viewId = "didomi-notice", children = listOf(text("Cookies et partenaires"), button("Tout refuser"), button("Tout accepter")))))
        val en = analyzer.analyze(window(node("android.view.View", viewId = "didomi-notice", children = listOf(text("Cookies and partners"), button("Reject all"), button("Accept all")))))
        assertEquals(fr.structureSignature, en.structureSignature)
        val updated = analyzer.analyze(window(node("android.view.View", viewId = "didomi-notice", children = listOf(text("Cookies et partenaires"), button("Tout accepter"), button("Paramètres")))))
        assertNotEquals(fr.structureSignature, updated.structureSignature)
    }

    // --- Langues --------------------------------------------------------------------------------

    @Test
    fun `refus direct en allemand, espagnol, italien, portugais et neerlandais`() {
        val cases = listOf(
            listOf("Wir verwenden Cookies. Unsere Partner verarbeiten Ihre Daten.", "Alle akzeptieren", "Alle ablehnen"),
            listOf("Usamos cookies. Nuestros socios tratan tus datos.", "Aceptar todo", "Rechazar todo"),
            listOf("Utilizziamo cookie. I nostri fornitori trattano i tuoi dati.", "Accetta tutto", "Rifiuta tutto"),
            listOf("Usamos cookies. Os nossos parceiros tratam os seus dados.", "Aceitar tudo", "Rejeitar tudo"),
            listOf("Wij gebruiken cookies. Onze partners verwerken uw gegevens.", "Alles accepteren", "Alles weigeren"),
        )
        for ((i, c) in cases.withIndex()) {
            class Lang : FakeApp("com.langue.$i") {
                var result: String? = null
                override fun build(): SnapshotNode = if (result != null) {
                    window(text("Home"), button("Play"))
                } else {
                    window(text(c[0]), button(c[1]).onClick { result = "accept" }, button(c[2]).onClick { result = "refus" })
                }
            }
            val app = Lang()
            val o = run(app).outcome
            assertNotNull("fenêtre non reconnue : ${c[0]}", o)
            assertEquals("langue ${c[2]}", "refus", app.result)
        }
    }

    @Test
    fun `garde-fou multilingue`() {
        val guard = SafetyGuard(rules)
        listOf("Alle akzeptieren", "Zustimmen", "Aceptar todo", "Accetta tutto", "Aceitar tudo", "Concordo", "Alles accepteren", "Akkoord")
            .forEach { assertFalse("« $it » devrait être bloqué", guard.checkText(it).isAllowed) }
        listOf("Alle ablehnen", "No aceptar", "Continua senza accettare", "Não aceito", "Niet akkoord", "Weiter ohne Zustimmung")
            .forEach { assertTrue("« $it » devrait être autorisé", guard.checkText(it).isAllowed) }
    }
}
