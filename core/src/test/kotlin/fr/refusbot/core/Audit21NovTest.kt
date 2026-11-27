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
 * Tests de régression de l'audit et du contre-audit du 21/11/2026 (docs/audit-2026-11-21.md).
 * Chaque test reproduit un constat : il échouait avec le code de la version 1.1.0 bêta 1.
 */
class Audit21NovTest {
    private val rules = TestRules.rules
    private val guard = SafetyGuard(rules)
    private val analyzer = ScreenAnalyzer(rules)

    private fun allowed(text: String) = guard.checkText(text).isAllowed

    // --- N° 3 : mots d'achat et de paiement ---------------------------------------------------

    @Test
    fun `validation suivie d'un mot d'achat - jamais reconnue ni autorisee`() {
        for (label in listOf("Confirmer la commande", "Valider ma commande", "Submit order", "Confirm and pay", "Save card", "Confirmer l'achat")) {
            assertNull(label, analyzer.match(TextNormalizer.canonical(label), rules.confirm))
        }
        // « order » seul n'est pas interdit (« in order to » figure dans les descriptions anglaises),
        // mais « Submit order » n'est déjà plus reconnu comme validation (test précédent).
        for (label in listOf("Confirmer la commande", "Confirm and pay", "Confirm payment", "Confirmer l'achat", "Buy now")) {
            assertFalse("garde-fou : $label", allowed(label))
        }
        // Les validations ordinaires restent reconnues, avec des mots neutres autour.
        assertNotNull(analyzer.match(TextNormalizer.canonical("Confirmer mes choix et fermer"), rules.confirm))
        assertNotNull(analyzer.match(TextNormalizer.canonical("Enregistrer"), rules.confirm))
        assertNotNull(analyzer.match(TextNormalizer.canonical("Save cookie settings"), rules.confirm))
        assertTrue(allowed("Enregistrer mes choix"))
    }

    @Test
    fun `fichier de regles importe - une validation avec un mot d'achat est refusee`() {
        val json = TestRules.file().readText().replaceFirst("\"confirm\": [", "\"confirm\": [\n      \"Valider la commande\",")
        assertTrue("la modification du fichier de test a échoué", json.contains("Valider la commande"))
        val (imported, report) = RulesValidator.parseAndValidate(json, rules)
        assertNull("fichier accepté : ${report.errors}", imported)
    }

    // --- N° 4 : alphabets non latins et textes vides --------------------------------------------

    @Test
    fun `texte dans un autre alphabet - toujours bloque`() {
        assertFalse(allowed("Принять все"))
        assertFalse(allowed("同意する"))
        // « Аccept all » avec un « А » cyrillique : devenait « ccept all ».
        assertFalse(allowed("Аccept all"))
        // Les langues prises en charge, accents compris, passent toujours.
        assertTrue(allowed("Tout refuser"))
        assertTrue(allowed("Não aceito"))
        assertTrue(allowed("Alle ablehnen"))
        assertTrue(allowed("Désactiver tout"))
    }

    @Test
    fun `bouton reconnu par son identifiant mais sans texte - jamais retenu`() {
        val root = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            node("android.widget.Button", clickable = true, viewId = "com.example:id/reject-all"),
            button("Tout accepter"),
        )
        val a = analyzer.analyze(root)
        assertTrue("la fenêtre doit être reconnue, sinon le test ne prouve rien", a.isConsent)
        assertTrue(a.buttons(ButtonKind.REFUSE).none { it.phrase.startsWith("id:") })
    }

    @Test
    fun `negations trompeuses - bloquees`() {
        assertFalse(allowed("Je n'accepte que les cookies publicitaires"))
        assertFalse(allowed("Ne pas ne pas accepter"))
        assertTrue(allowed("Je n'accepte pas"))
        assertTrue(allowed("Ne pas accepter"))
        assertTrue(allowed("Do not accept"))
    }

    // --- N° 2 : validation avec un choix connu comme accepté -------------------------------------

    /** Préférences avec un interrupteur ACTIVÉ mais grisé (non modifiable), sans badge « toujours actif ». */
    private class GreyedOn : FakeApp() {
        var options = false
        var saved = false
        var on = true
        override fun build(): SnapshotNode = when {
            saved -> window(text("Accueil"), button("Jouer"))
            !options -> window(
                text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                button("Tout accepter").onClick { error("acceptation") },
                button("Gérer les options").onClick { options = true },
            )
            else -> window(
                text("Intérêt légitime"), text("Fournisseurs"),
                group(text("Publicité ciblée"), switch("Publicité ciblée", true, enabled = false)),
                group(text("Mesure d'audience"), switch("Mesure d'audience", on).onClick { on = false }),
                button("Confirmer les choix").onClick { saved = true },
            )
        }
    }

    @Test
    fun `interrupteur active grise - ne pas valider dans le doute laisse la fenetre ouverte`() {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true }
        val app = GreyedOn()
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse("« Confirmer » cliqué alors qu'un choix est encore accepté", app.saved)
        assertFalse(app.on)
        assertEquals(OutcomeStatus.HANDED_OVER, o.status)
    }

    @Test
    fun `interrupteur active grise - sans le reglage, refus partiel et jamais un refus annonce`() {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }
        val app = GreyedOn()
        val r = runEngine(app, engine, clock)
        val o = r.outcome!!
        assertTrue(app.saved)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertNotEquals(OutcomeStatus.UNVERIFIED, o.status)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("activé"))
        // Une validation n'est jamais confiée à un appui aux coordonnées, même en secours.
        val confirm = r.acts.last().ops.single() as Op.Click
        assertFalse(confirm.allowTapFallback)
    }

    private fun radio(label: String, checked: Boolean) =
        node("android.widget.RadioButton", text = label, clickable = true, checkable = true, checked = checked)

    private fun truncatedPartners(other: String): Screen {
        Tree.reset()
        val root = window(node(viewId = "didomi-popup", children = listOf(
            text("Nos partenaires et fournisseurs utilisent vos données personnelles"),
            group(text("Tous les partenaires"), group(radio("Bloquer", true), radio("Autoriser", false)), cls = "android.view.View"),
            group(text(other), group(radio("Bloquer $other", false), radio("Autoriser $other", true)), cls = "android.view.View"),
            button("Enregistrer"),
            button("Tout accepter"),
        )))
        return Screen("com.example.jeu", root, isTruncated = true)
    }

    @Test
    fun `liste trop longue - jamais de validation avec un Autoriser selectionne`() {
        // Nom court (« Autoriser Google » reconnu comme acceptation) et nom long (plus de 2 mots ajoutés).
        for (other in listOf("Google", "Google Advertising Products")) {
            val step = RefusalEngine(rules).next(truncatedPartners(other))
            assertTrue("$other : $step", step is Step.Done)
            assertEquals(other, OutcomeStatus.HANDED_OVER, (step as Step.Done).outcome.status)
        }
    }

    // --- N° 1 : liste pas lue jusqu'en bas ----------------------------------------------------

    /** Liste sans fin (chaque défilement révèle de nouvelles lignes), « Confirmer » fixé en bas. */
    private class EndlessList : FakeApp() {
        var options = false
        var saved = false
        var offset = 0
        override fun onScroll(container: UiNode) {
            offset += 5
        }

        override fun build(): SnapshotNode = when {
            saved -> window(text("Accueil"), button("Jouer"))
            !options -> window(
                text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                button("Tout accepter").onClick { error("acceptation") },
                button("Gérer les options").onClick { options = true },
            )
            else -> window(
                text("Intérêt légitime"), text("Fournisseurs"),
                group(
                    *(offset until offset + 5).map { i -> group(text("Finalité $i"), switch("Finalité $i", false)) }.toTypedArray(),
                    cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true,
                ),
                button("Confirmer les choix").onClick { saved = true },
            )
        }
    }

    @Test
    fun `liste pas lue jusqu'en bas - jamais verifie`() {
        val clock = FakeClock()
        val o = runEngine(EndlessList(), RefusalEngine(rules) { clock.now }, clock).outcome!!
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    @Test
    fun `liste pas lue jusqu'en bas - ne pas valider dans le doute laisse la fenetre ouverte`() {
        val clock = FakeClock()
        val app = EndlessList()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true }, clock).outcome!!
        assertFalse(app.saved)
        assertEquals(OutcomeStatus.HANDED_OVER, o.status)
    }

    // --- N° 5 : navigateurs -------------------------------------------------------------------

    private class Site(val name: String) : FakeApp("com.android.chrome") {
        var refused = false
        override fun build(): SnapshotNode = window(node("android.webkit.WebView", children = if (refused) {
            listOf(text("Article de $name"), button("Lire la suite"))
        } else {
            listOf(
                text("$name et ses partenaires utilisent des cookies."),
                text("Vos données personnelles"),
                button("Tout accepter").onClick { error("acceptation") },
                button("Tout refuser").onClick { refused = true },
            )
        }))
    }

    @Test
    fun `navigateur - le site suivant n'est pas ignore pendant 20 secondes`() {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }
        val first = Site("Le Monde")
        val o = runEngine(first, engine, clock).outcome!!
        assertTrue(first.refused)
        assertTrue(o.success)

        clock.advance(2_000)
        val second = Site("Le Figaro")
        val step = engine.next(Screen(second.packageName, second.render()))
        assertTrue("le 2e site a été ignoré : $step", step is Step.Act)

        // La même fenêtre réaffichée tout de suite reste, elle, en attente (pas de nouveau clic).
        val again = Site("Le Monde")
        val clock2 = FakeClock()
        val engine2 = RefusalEngine(rules) { clock2.now }
        runEngine(Site("Le Monde"), engine2, clock2)
        clock2.advance(2_000)
        assertEquals(Step.Idle, engine2.next(Screen(again.packageName, again.render())))
    }

    /** Bandeau sans refus dont un texte change à chaque capture (« il y a N s »). */
    private class TickingAcceptOnly : FakeApp("com.android.chrome") {
        var tick = 0
        override fun build(): SnapshotNode = window(node("android.webkit.WebView", children = listOf(
            text("Nous et nos partenaires utilisons des cookies. Mis à jour il y a ${tick++} s"),
            text("Vos données personnelles"),
            button("Tout accepter").onClick { error("acceptation") },
        )))
    }

    @Test
    fun `navigateur - pas de nouvelle session a chaque capture quand le texte bouge`() {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }
        val app = TickingAcceptOnly()
        val first = engine.next(Screen(app.packageName, app.render()))
        assertTrue(first.toString(), first is Step.Done)
        clock.advance(500)
        assertEquals(Step.Idle, engine.next(Screen(app.packageName, app.render())))
    }

    @Test
    fun `regles changees - les delais d'attente sont repris`() {
        val clock = FakeClock()
        val old = RefusalEngine(rules) { clock.now }
        val app = TickingAcceptOnly()
        assertTrue(old.next(Screen(app.packageName, app.render())) is Step.Done)
        val fresh = RefusalEngine(rules) { clock.now }
        fresh.adoptCooldowns(old)
        app.tick = 0
        assertEquals(Step.Idle, fresh.next(Screen(app.packageName, app.render())))
    }

    /** Liste figée de 5 lignes déjà désactivées, « Confirmer » en bas. */
    private class StaticList : FakeApp() {
        var options = false
        override fun build(): SnapshotNode = if (!options) window(
            text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
            button("Tout accepter").onClick { error("acceptation") },
            button("Gérer les options").onClick { options = true },
        ) else window(
            text("Intérêt légitime"), text("Fournisseurs"),
            group(
                *(0 until 5).map { i -> group(text("Finalité $i"), switch("Finalité $i", false)) }.toTypedArray(),
                cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true,
            ),
            button("Confirmer les choix"),
        )
    }

    @Test
    fun `defilement continu - la fin de liste mesuree evite les attentes`() {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }
        val app = StaticList()
        var step: Step = engine.next(Screen(app.packageName, app.render()))
        var guard = 0
        while (guard++ < 20) {
            val act = step as? Step.Act
            if (act != null && act.ops.any { it is Op.Scroll }) break
            if (act != null) act.ops.forEach(app::perform) else if (step is Step.Wait) clock.advance(step.delayMs)
            clock.advance(250)
            step = engine.next(Screen(app.packageName, app.render()))
        }
        val scroll = step as Step.Act
        assertTrue(scroll.description, scroll.ops.single() is Op.Scroll)
        // Le service a mesuré : rien n'a bougé et Android ne propose plus de défiler.
        engine.recordExecution(ActionExecutionStats(requested = 1, executed = 1, scrollAtEnd = true))
        val next = engine.next(Screen(app.packageName, app.render()))
        assertTrue("attente inutile après une fin de liste mesurée : $next", next is Step.Act)
        assertTrue((next as Step.Act).description, next.description.startsWith("validation"))
    }

    // --- N° 6 : applis sensibles --------------------------------------------------------------

    @Test
    fun `banques et sante francaises exclues de Tout cocher`() {
        for (pkg in listOf("com.boursorama.android.clients", "fr.creditagricole.androidapp", "net.bnpparibas.mescomptes", "com.revolut.revolut", "fr.doctolib.www")) {
            assertTrue(pkg, SensitiveAppPolicy.isSensitivePackage(pkg))
        }
        assertFalse(SensitiveAppPolicy.isSensitivePackage("com.king.candycrushsaga"))
    }
}
