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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de régression de la revue de sécurité : un refus n'est jamais annoncé sans preuve
 * observable, et un état d'interrupteur n'est jamais supposé.
 */
class SafetyRegressionTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }
    private val analyzer = ScreenAnalyzer(rules)

    private fun game() = window(text("Accueil"), button("Jouer"))

    /** Interrupteur de WebView : état publié seulement par la description, jamais par isChecked. */
    private fun webSwitch(label: String, state: String?, checkable: Boolean = true) = SnapshotNode(
        className = "android.widget.ToggleButton", text = label, isClickable = true, isCheckable = checkable,
        isChecked = checkable && state == "activé", bounds = Bounds(0, 500, 1000, 580), stateDescription = state,
    )

    @Test
    fun `capture tronquee sur un ecran ordinaire ne cree aucun refus partiel`() {
        val ordinary = window(text("Paramètres des cookies du jeu"), switch("Musique", true))
        assertEquals(Step.Idle, engine.next(Screen("com.example.game", ordinary, isTruncated = true)))
    }

    @Test
    fun `capture tronquee reconnue est journalisee une seule fois pendant le cooldown`() {
        // Sans refus direct visible (seulement des options), rien n'est tenté sur une capture incomplète.
        val consent = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Gérer les options"),
            button("Tout accepter"),
        )
        val first = engine.next(Screen("com.example.game", consent, isTruncated = true))
        assertTrue(first is Step.Done)
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, (first as Step.Done).outcome.status)
        assertFalse(first.outcome.status.isRefusal)
        assertEquals(Step.Idle, engine.next(Screen("com.example.game", consent, isTruncated = true)))
    }

    @Test
    fun `phrase qui n'est pas un bouton de refus - jamais reconnue`() {
        for (label in listOf(
            "Peut-on refuser", "Pourquoi refuser", "Refuser coûte 2 €", "Refuser sauf nécessaires",
            "Comment refuser", "Refuser plus tard", "Reject later", "Why reject", "Payer pour refuser",
        )) {
            val a = analyzer.analyze(window(text("Nous utilisons des cookies avec nos partenaires."), button(label)))
            assertTrue(label, a.buttons.none { it.kind == ButtonKind.REFUSE || it.kind == ButtonKind.GLOBAL_REJECT })
        }
    }

    @Test
    fun `variantes neutres d'un refus - toujours reconnues`() {
        for (label in listOf(
            "Tout refuser et fermer", "Refuser maintenant", "Refuser ?", "Reject all and close", "Alle ablehnen und schließen",
            "Rechazar todo y cerrar", "Rifiuta tutto e chiudi", "Refuser les cookies optionnels",
        )) {
            val a = analyzer.analyze(window(text("Nous utilisons des cookies avec nos partenaires."), button(label)))
            assertTrue(label, a.buttons.any { it.kind == ButtonKind.REFUSE && it.target != null })
        }
    }

    @Test
    fun `capture tronquee respecte le profil observation`() {
        val consent = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Tout refuser"),
        )
        val result = engine.next(Screen("com.example.game", consent, isTruncated = true)) { Policy.OBSERVATION }
        assertTrue(result is Step.Done)
        assertEquals(OutcomeStatus.OBSERVED, (result as Step.Done).outcome.status)
        assertEquals(0, result.outcome.actions)
    }

    @Test
    fun `bouton de refus invisible jamais actionnable`() {
        val hidden = SnapshotNode(
            className = "android.widget.Button",
            text = "Tout refuser",
            isClickable = true,
            isVisibleToUser = false,
            bounds = Bounds(10, 10, 300, 90),
        )
        val a = analyzer.analyze(window(
            text("Nous utilisons des cookies avec nos partenaires."),
            hidden,
        ))
        // Un bouton invisible est soit ignoré, soit sans cible : jamais actionnable.
        assertTrue("un bouton invisible ne doit pas recevoir de cible", a.buttons(ButtonKind.REFUSE).all { it.target == null })
    }

    @Test
    fun `interrupteur coche mais invisible jamais actionnable`() {
        val hidden = SnapshotNode(
            className = "android.widget.CheckBox",
            text = "Publicité personnalisée",
            isClickable = true,
            isCheckable = true,
            isChecked = true,
            isVisibleToUser = false,
            bounds = Bounds(10, 100, 300, 180),
        )
        val a = analyzer.analyze(window(
            text("Cookies et partenaires"),
            hidden,
        ))
        // Un interrupteur hors écran est soit ignoré, soit sans cible : jamais cliqué.
        assertTrue("un interrupteur hors écran ne doit jamais être cliqué", a.checkedToggles.all { it.target == null })
    }

    @Test
    fun `section repliee invisible jamais actionnable`() {
        val hidden = SnapshotNode(
            className = "android.widget.Button",
            text = "Fournisseurs",
            isClickable = true,
            isExpandable = true,
            isVisibleToUser = false,
            bounds = Bounds(0, 0, 0, 0),
        )
        val a = analyzer.analyze(window(
            text("Cookies et partenaires"),
            hidden,
        ))
        assertTrue(a.expandables.isEmpty())
    }

    @Test
    fun `marqueur CMP invisible et lien pied de page ne suffisent pas a detecter une banniere`() {
        val root = window(
            text("Accueil"),
            button("Paramètres des cookies"),
            node("android.view.View", viewId = "onetrust-consent-sdk", visible = false),
        )
        val analysis = analyzer.analyze(root)
        assertFalse("un identifiant CMP masqué ne doit pas compter comme preuve active", analysis.isConsent)
        assertTrue("le marqueur masqué ne doit pas être retenu comme indice", analysis.hints.none { it.startsWith("id:") })
    }

    @Test
    fun `boutons et interrupteurs hors du panneau CMP ne sont pas rattaches a la session`() {
        val consentPanel = group(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles sont traitées pour la publicité."),
            button("Gérer les options"),
            switch("Publicité personnalisée", true),
        )
        val article = group(text("Notre article explique les cookies et vos données personnelles."))
        val unrelatedFooter = group(button("Enregistrer"))
        val analysis = analyzer.analyze(window(consentPanel, article, unrelatedFooter))

        assertTrue("le panneau CMP visible doit être reconnu", analysis.isConsent)
        assertTrue("le bouton de l'article ne doit pas rejoindre les commandes CMP",
            analysis.buttons.none { it.label == "enregistrer" })
        assertEquals("seul l'interrupteur du panneau CMP doit être analysé", 1, analysis.toggleStates.size)
        assertEquals(TextNormalizer.canonical("Publicité personnalisée"), analysis.checkedToggles.single().label)
    }

    @Test
    fun `indices et bouton isoles dans des panneaux differents ne forment pas une CMP`() {
        Tree.reset() // les positions dépendent d'un compteur global : sans remise à zéro, le test dépend de l'ordre
        val article = group(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles sont importantes."),
        )
        val footer = group(node(
            "android.widget.Button",
            text = "Paramètres des cookies",
            clickable = true,
            bounds = Bounds(0, 2_100, 1_000, 2_180),
        ))
        val analysis = analyzer.analyze(window(article, footer))

        assertFalse("un texte éditorial et un lien de pied de page ne forment pas une bannière", analysis.isConsent)
        assertTrue("aucune commande isolée ne doit être actionnable", analysis.buttons.isEmpty())
        assertTrue("aucun interrupteur hors panneau ne doit être analysé", analysis.toggleStates.isEmpty())
    }

    @Test
    fun `le conteneur de defilement CMP le plus proche des interrupteurs est choisi`() {
        val innerList = group(
            switch("Publicité personnalisée", true),
            cls = "androidx.recyclerview.widget.RecyclerView",
            scrollable = true,
        )
        val outerWebView = group(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles sont traitées."),
            button("Gérer les options"),
            innerList,
            cls = "android.webkit.WebView",
            scrollable = true,
        )
        val analysis = analyzer.analyze(window(outerWebView))

        assertTrue(analysis.isConsent)
        assertEquals("la liste imbriquée doit être choisie plutôt que toute la WebView",
            "androidx.recyclerview.widget.RecyclerView", analysis.scrollTarget?.className)
    }

    @Test
    fun `abort journalise une interruption et bloque la fenetre sous jacente`() {
        val banner = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Tout accepter"),
            button("Gérer les options"),
        )
        val first = engine.next(Screen("com.example.app", banner))
        assertTrue(first is Step.Act)

        val aborted = engine.abort("service interrompu")
        assertNotNull(aborted)
        assertEquals(OutcomeStatus.INTERRUPTED, aborted!!.status)
        assertFalse(aborted.status.isRefusal)

        val underlyingPage = window(text("Accueil"), button("Paramètres"))
        assertEquals("la transition vers la page sous-jacente ne doit pas relancer une session",
            Step.Idle, engine.next(Screen("com.example.app", underlyingPage)))
    }

    // --- Point 1 : sens inversé à l'écran ---------------------------------------------------------

    @Test
    fun `libelle au sens inverse jamais reconnu comme un refus`() {
        for (label in listOf(
            "Ne pas refuser", "Do not reject", "Don't reject all", "Nicht ablehnen", "No rechazar",
            "Ne plus s'opposer", "Refuser jamais", "Never decline",
            "Refuser impossible", "Je refuse de refuser", "Refuser sauf nécessaires",
        )) {
            val a = analyzer.analyze(window(text("Nous utilisons des cookies avec nos partenaires."), button(label)))
            assertTrue(label, a.buttons.none { it.kind == ButtonKind.REFUSE || it.kind == ButtonKind.GLOBAL_REJECT })
        }
        // Un refus simple reste reconnu ; seule la construction « refuse de » inverse le sens.
        val direct = analyzer.analyze(window(text("Nous utilisons des cookies avec nos partenaires."), button("Je refuse")))
        assertTrue(direct.buttons.any { it.kind == ButtonKind.REFUSE })

        // Les mots neutres ajoutés restent tolérés.
        val ok = analyzer.analyze(window(text("Nous utilisons des cookies avec nos partenaires."), button("Tout refuser et fermer")))
        assertTrue(ok.buttons.any { it.kind == ButtonKind.REFUSE })
    }

    // --- Point 2 : description d'état ------------------------------------------------------------

    @Test
    fun `role ARIA WebView avec Off ambigu reste inconnu`() {
        // Chromium peut publier « Off » pour aria-checked=true comme pour false.
        // Un rôle Web sans état Android cochable ne doit donc jamais être assimilé à OFF.
        for (role in listOf("switch", "checkbox")) {
            val ariaToggle = SnapshotNode(
                className = "android.view.View",
                text = "Intérêt légitime",
                isClickable = true,
                isCheckable = false,
                isChecked = false,
                bounds = Bounds(0, 500, 1000, 580),
                stateDescription = "Off",
                webRole = role,
            )
            val analysis = analyzer.analyze(window(text("Cookies et partenaires"), ariaToggle))
            assertTrue("$role : aucun état actif déduit de « Off »", analysis.checkedToggles.isEmpty())
            assertEquals("$role : état inconnu", 1, analysis.unknownStateToggles)
            assertEquals("$role : état nullable conservé", listOf(null), analysis.toggleStates.values.toList())
            assertTrue("$role : signature inconnue conservée", analysis.unknownToggleSignatures.isNotEmpty())
        }
    }

    @Test
    fun `interrupteur active d'apres sa description - traite comme active`() {
        val a = analyzer.analyze(window(text("Cookies et partenaires"), webSwitch("Intérêt légitime", "activé")))
        assertEquals(1, a.checkedToggles.size)
        assertEquals(0, a.unknownStateToggles)
        assertEquals(listOf(true), a.toggleStates.values.toList())

        val off = analyzer.analyze(window(text("Cookies et partenaires"), webSwitch("Intérêt légitime", "désactivé")))
        assertTrue(off.checkedToggles.isEmpty())
        assertEquals(0, off.unknownStateToggles)

        // Description incompréhensible sur une vue non cochable : état inconnu, jamais « désactivé ».
        val odd = analyzer.analyze(window(text("Cookies et partenaires"), webSwitch("Intérêt légitime", "variable", checkable = false)))
        assertTrue(odd.checkedToggles.isEmpty())
        assertEquals(1, odd.unknownStateToggles)
    }

    @Test
    fun `role ARIA non cochable avec Off reste d etat inconnu`() {
        for (role in listOf("switch", "checkbox")) {
            val webNode = SnapshotNode(
                className = if (role == "switch") "android.widget.ToggleButton" else "android.widget.CheckBox",
                text = "Intérêt légitime",
                isClickable = true,
                isCheckable = false,
                isChecked = false,
                bounds = Bounds(0, 500, 1000, 580),
                stateDescription = "Off",
                webRole = role,
            )
            val a = analyzer.analyze(window(text("Cookies et partenaires"), webNode))
            assertTrue("un état Off ambigu ne doit pas être considéré désactivé", a.checkedToggles.isEmpty())
            assertEquals("l'état doit rester inconnu", 1, a.unknownStateToggles)
        }
    }

    @Test
    fun `ToggleButton Android non cochable sans role - Off reste inconnu`() {
        val toggle = SnapshotNode(
            className = "android.widget.ToggleButton",
            text = "Intérêt légitime",
            isClickable = true,
            isCheckable = false,
            isChecked = false,
            bounds = Bounds(0, 500, 1000, 580),
            stateDescription = "Off",
        )
        val analysis = analyzer.analyze(window(text("Cookies et partenaires"), toggle))
        assertTrue(analysis.checkedToggles.isEmpty())
        assertEquals(1, analysis.unknownStateToggles)
        assertTrue(analysis.unknownToggleSignatures.isNotEmpty())
    }

    @Test
    fun `ligne cliquable - operation conserve le vrai noeud du switch`() {
        class RowApp : FakeApp() {
            var options = false
            var saved = false
            var enabled = true
            val toggles = mutableListOf<SnapshotNode>()
            val rows = mutableListOf<SnapshotNode>()

            override fun build(): SnapshotNode = when {
                saved -> game()
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                    button("Tout accepter"), button("Gérer les options").onClick { options = true },
                )
                else -> {
                    val toggle = SnapshotNode(
                        className = "android.widget.CheckBox", text = "Intérêt légitime",
                        isCheckable = true, isChecked = enabled, bounds = Bounds(100, 500, 150, 550),
                    )
                    val row = SnapshotNode(
                        className = "android.widget.LinearLayout", isClickable = true,
                        bounds = Bounds(0, 480, 600, 570),
                    )
                    row.addChild(toggle)
                    row.onClick { enabled = false }
                    toggles.add(toggle)
                    rows.add(row)
                    window(
                        text("Intérêt légitime"), text("Fournisseurs"), row,
                        button("Confirmer les choix").onClick { saved = true },
                    )
                }
            }
        }
        val app = RowApp()
        val run = runEngine(app, engine, clock)
        val toggleOp = run.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().first { it.isToggle }
        val pair = app.toggles.zip(app.rows).first { it.first === toggleOp.toggleNode }
        assertSame(pair.second, (toggleOp.target as ClickTarget.Node).node)
        assertFalse(app.enabled)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, run.outcome?.status)
        assertEquals(1, run.outcome?.togglesOff)
    }

    private open inner class DescribedToggleApp(val clickWorks: Boolean) : FakeApp() {
        var options = false
        var saved = false
        var on = true
        override fun build(): SnapshotNode = when {
            saved -> game()
            !options -> window(
                text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                button("Tout accepter"), button("Gérer les options").onClick { options = true },
            )
            else -> window(
                text("Intérêt légitime"), text("Fournisseurs"),
                group(text("Finalité 1"), webSwitch("Intérêt légitime", if (on) "activé" else "désactivé").onClick { if (clickWorks) on = false }),
                button("Confirmer les choix").onClick { saved = true },
            )
        }
    }

    @Test
    fun `succes seulement apres avoir observe l'etat desactive`() {
        val app = DescribedToggleApp(clickWorks = true)
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse("l'interrupteur doit avoir été désactivé avant la validation", app.on)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals(1, o.togglesOff)
    }

    @Test
    fun `interrupteur reste active d'apres sa description - jamais de succes complet`() {
        val app = DescribedToggleApp(clickWorks = false)
        val o = runEngine(app, engine, clock).outcome!!
        assertTrue(app.on)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertNotEquals(OutcomeStatus.UNVERIFIED, o.status)
    }

    // --- Point 3 : la fermeture n'est pas une preuve ---------------------------------------------

    @Test
    fun `fenetre fermee sans enregistrer le refus - jamais de refus direct reussi`() {
        class ClosesWithoutSaving : FakeApp() {
            var closed = false
            var refusalRecorded = false
            override fun build(): SnapshotNode = if (closed) {
                game()
            } else {
                window(
                    text("Nous utilisons des cookies avec nos partenaires."),
                    button("Tout accepter"),
                    // Le bouton ferme la fenêtre mais l'appli n'enregistre rien.
                    button("Tout refuser").onClick { closed = true },
                )
            }
        }
        val app = ClosesWithoutSaving()
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.refusalRecorded)
        assertNotEquals(OutcomeStatus.DIRECT_SUCCESS, o.status)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals(OutcomeStatus.UNVERIFIED, o.status)
        assertFalse(o.status.isVerified)
        assertTrue(o.reason!!, o.reason.contains("vérifier"))
    }

    @Test
    fun `le statut direct reussi n'est plus jamais produit`() {
        for (scenario in fr.refusbot.core.lab.LabScenarios.all) {
            val report = fr.refusbot.core.lab.LabRunner.run(scenario, rules)
            assertNotEquals(scenario.id, OutcomeStatus.DIRECT_SUCCESS, report.outcome?.status)
        }
    }

    // --- Point 4 : un interrupteur inconnu suffit à rendre le refus partiel ----------------------

    @Test
    fun `un interrupteur desactive et un autre d'etat inconnu - refus partiel`() {
        class Mixed : FakeApp() {
            var options = false
            var saved = false
            var known = true
            override fun build(): SnapshotNode = when {
                saved -> game()
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                    button("Tout accepter"), button("Gérer les options").onClick { options = true },
                )
                else -> window(
                    text("Intérêt légitime"), text("Fournisseurs"),
                    group(text("Publicité"), switch("Consentement", known).onClick { known = false }),
                    // Case de WebView : ni cochable, ni cochée, sans description → état inconnu.
                    group(text("Mesure"), node("android.widget.CheckBox", text = "Intérêt légitime", clickable = true)),
                    button("Confirmer les choix").onClick { saved = true },
                )
            }
        }
        val app = Mixed()
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.known)
        assertEquals(1, o.togglesOff)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertTrue(o.reason!!, o.reason.contains("non communiqué"))
    }

    @Test
    fun `les interrupteurs inconnus ont des signatures distinctes par ligne`() {
        val root = window(
            text("Cookies et partenaires"),
            group(text("Fournisseur A"), node("android.widget.CheckBox", text = "Intérêt légitime", clickable = true)),
            group(text("Fournisseur B"), node("android.widget.CheckBox", text = "Intérêt légitime", clickable = true)),
        )
        val a = analyzer.analyze(root)
        assertEquals(2, a.unknownStateToggles)
        assertEquals(2, a.unknownToggleSignatures.size)
    }

    @Test
    fun `case inconnue avec ou sans description - aucun clic et validation bloquee`() {
        for (description in listOf("Partially checked", null)) {
            class MixedApp : FakeApp() {
                var options = false
                var saved = false
                override fun build(): SnapshotNode = when {
                    saved -> game()
                    !options -> window(
                        text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                        button("Tout accepter"), button("Gérer les options").onClick { options = true },
                    )
                    else -> window(
                        text("Intérêt légitime"), text("Fournisseurs"),
                        SnapshotNode(
                            className = "android.widget.CheckBox", text = "Intérêt légitime",
                            isCheckable = true, isIndeterminate = true, stateDescription = description,
                        ),
                        button("Confirmer les choix").onClick { saved = true },
                    )
                }
            }
            val app = MixedApp()
            val clock = FakeClock()
            val outcome = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome!!
            assertEquals(OutcomeStatus.IMPOSSIBLE, outcome.status)
            assertFalse("la validation ne doit pas être envoyée avec un état inconnu", app.saved)
            assertFalse(app.clickedLabels.contains("intérêt légitime"))
        }
    }

    // --- Point 6 : champs de saisie --------------------------------------------------------------

    @Test
    fun `description et enfants d'un champ de saisie absents du diagnostic`() {
        val inner = SnapshotNode(
            className = "android.widget.TextView", text = "valeur-enfant-4242",
            viewId = "id-enfant-4242",
            contentDescription = "desc-enfant-4242", stateDescription = "state-enfant-4242",
            webRole = "role-enfant-4242", extrasSummary = "extra-enfant-4242",
            supplementalDescription = "supp-enfant-4242",
        )
        val fieldContent = SnapshotNode().addChild(inner)
        val field = SnapshotNode(
            className = "android.widget.EditText", text = "valeur-saisie-4242",
            viewId = "id-champ-4242",
            contentDescription = "valeur-recopiee-4242", isEditable = true,
            stateDescription = "state-champ-4242", webRole = "role-champ-4242",
            supplementalDescription = "supp-champ-4242",
            extrasSummary = "extra-champ-4242",
        )
        field.addChild(fieldContent)
        val root = window(text("Cookies"), field)
        assertNull(field.contentDescription)
        assertNull(field.viewId)
        assertNull(inner.text)
        assertNull(inner.contentDescription)
        assertNull(inner.viewId)
        assertNull(field.stateDescription)
        assertNull(field.webRole)
        assertNull(field.extrasSummary)
        assertNull(field.supplementalDescription)
        assertNull(inner.stateDescription)
        assertNull(inner.webRole)
        assertNull(inner.extrasSummary)
        assertNull(inner.supplementalDescription)
        assertTrue(field.subtreeTexts().isEmpty())
        val json = DiagnosticExport.toJson(root, DiagnosticExport.Meta("p", null, "t", "test", null, "1", 1, "15", "x"), analyzer.analyze(root))
        assertFalse(json, json.contains("4242"))
        assertTrue(json.contains("\"editable\": true"))
    }

    @Test
    fun `donnees accessibilite sensibles - texte et sous arbre jamais lus ni exportes`() {
        val sensitive = SnapshotNode(
            className = "android.view.View",
            text = "private marker 123",
            isAccessibilityDataSensitive = true,
        )
        val child = SnapshotNode(className = "android.widget.TextView", text = "secret child 456")
        sensitive.addChild(child)

        assertNull(sensitive.text)
        assertNull(child.text)
        assertTrue(sensitive.ownTexts().isEmpty())
        assertFalse(SafetyGuard(rules).checkNode(sensitive).isAllowed)
        assertFalse("un descendant protégé ne peut pas devenir une cible", SafetyGuard(rules).checkNode(child).isAllowed)

        val json = DiagnosticExport.toJson(
            window(sensitive),
            DiagnosticExport.Meta("p", null, "t", "test", null, "1", 1, "14", "x"),
            null,
        )
        assertFalse(json.contains("private marker"))
        assertFalse(json.contains("secret child"))
        assertTrue(json.contains("accessibilityDataSensitive"))
    }

    @Test
    fun `preferences ARIA non exposees sans role ni checkable - validation bloquee`() {
        class AriaOnly : FakeApp() {
            var options = false
            var saved = false
            override fun build(): SnapshotNode = if (!options) {
                window(
                    text("Nous utilisons des cookies avec nos partenaires."),
                    text("Vos données personnelles"),
                    button("Tout accepter"),
                    button("Gérer les options").onClick { options = true },
                )
            } else {
                window(
                    text("Intérêt légitime et consentement"),
                    node("android.view.View", text = "Intérêt légitime", clickable = true, checkable = false),
                    button("Confirmer les choix").onClick { saved = true },
                )
            }
        }

        val app = AriaOnly()
        val result = runEngine(app, engine, clock).outcome!!
        assertFalse("une validation ne doit pas être envoyée sans contrôle reconnu", app.saved)
        assertEquals(OutcomeStatus.IMPOSSIBLE, result.status)
        assertTrue(result.reason.orEmpty(), result.reason.orEmpty().contains("aucun contrôle"))
    }

    @Test
    fun `ToggleButton non cochable sans role - validation bloquee`() {
        class AriaOnly : FakeApp() {
            var options = false
            var saved = false
            override fun build(): SnapshotNode = if (!options) {
                window(
                    text("Nous utilisons des cookies avec nos partenaires."),
                    text("Vos données personnelles"),
                    button("Tout accepter"),
                    button("Gérer les options").onClick { options = true },
                )
            } else {
                window(
                    text("Préférences de cookies et intérêt légitime"),
                    SnapshotNode(
                        className = "android.widget.ToggleButton",
                        text = "Intérêt légitime",
                        isClickable = true,
                        isCheckable = false,
                        isChecked = false,
                        stateDescription = "Off",
                    ),
                    button("Confirmer les choix").onClick { saved = true },
                )
            }
        }

        val app = AriaOnly()
        val result = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome!!
        assertFalse("la validation ne doit pas être envoyée avec un état inconnu", app.saved)
        assertEquals(OutcomeStatus.IMPOSSIBLE, result.status)
        assertEquals(1, result.togglesUnknown)
        assertTrue(result.reason.orEmpty(), result.reason.orEmpty().contains("validation bloquée"))
    }

    @Test
    fun `profil observation - distingue observation enregistree et banniere detectee`() {
        val root = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Tout accepter"),
            button("Gérer les options"),
        )
        val result = engine.next(Screen("com.example.game", root)) { Policy.OBSERVATION } as Step.Done
        assertEquals(OutcomeStatus.OBSERVED, result.outcome.status)
        assertTrue(result.outcome.consentDetected)
        assertTrue(result.outcome.controlsRecognized > 0)
        assertEquals(0, result.outcome.actionAttempts)
        assertEquals(0, result.outcome.actionExecutions)
        assertNull(result.outcome.stateVerified)
    }

}
