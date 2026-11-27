package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scénarios inspirés des principales CMP, et garde-fous de sécurité supplémentaires. */
class CmpScenariosTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }

    private fun outcome(app: FakeApp): Outcome {
        val r = runEngine(app, engine, clock)
        assertNotNull("le moteur aurait dû terminer", r.outcome)
        return r.outcome!!
    }

    private fun gameScreen() = window(text("Accueil"), button("Jouer"))

    // --- Sécurité -----------------------------------------------------------------------------

    @Test
    fun `une etiquette englobant une case a cocher n'est jamais cliquee comme un bouton`() {
        // <label>Fournisseurs <input type=checkbox></label> : toucher l'étiquette basculerait la case.
        val root = window(
            text("Cookies et partenaires"), text("Vos données"),
            node(
                "android.view.View", clickable = true,
                children = listOf(text("Fournisseurs"), node("android.widget.CheckBox", checkable = true, checked = false, clickable = true)),
            ),
            button("Tout accepter"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        val tab = a.buttons(ButtonKind.VENDOR_TAB).single { it.label == "fournisseurs" }
        assertNull("une cible contenant un interrupteur est interdite", tab.target)
    }

    @Test
    fun `un bouton portant le libelle d'un interrupteur n'est jamais clique`() {
        // <label for="x">Partenaires</label> <input id="x" type=checkbox aria-label="Partenaires">
        val root = window(
            text("Cookies"), text("Vos données"),
            button("Partenaires"),
            node("android.widget.CheckBox", text = "Partenaires", checkable = true, checked = false, clickable = true),
            button("Enregistrer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertNull(a.buttons(ButtonKind.VENDOR_TAB).single().target)
        assertNotNull(a.buttons(ButtonKind.CONFIRM).single().target)
    }

    @Test
    fun `le journal ne contient jamais le texte de l'ecran`() {
        class Trap : FakeApp() {
            override fun build() = window(
                text("Nous et nos partenaires utilisons des cookies pour vos données."),
                node(
                    "android.widget.LinearLayout", clickable = true,
                    children = listOf(text("Refuser"), text("Accepter les cookies du Journal Secret 2026")),
                ),
            )
        }
        val o = outcome(Trap())
        assertFalse(o.success)
        val reason = o.reason.orEmpty()
        assertTrue(reason, reason.contains("garde-fou"))
        assertFalse("texte d'écran dans le motif : $reason", reason.contains("Secret"))
    }

    @Test
    fun `champ de saisie jamais lu ni exporte`() {
        val field = node("android.widget.EditText", text = "jean.dupont@example.com", editable = true)
        assertNull(field.text)
        assertTrue(field.ownTexts().isEmpty())
        val json = DiagnosticExport.toJson(
            window(field),
            DiagnosticExport.Meta("p", null, "t", "test", null, "1", 1, "15", "x"),
            null,
        )
        assertFalse(json.contains("dupont"))
        assertTrue(json.contains("\"editable\": true"))
    }

    @Test
    fun `cases dont l'etat n'est pas publie - jamais touchees, validation bloquee`() {
        // Cas observé sur émulateur : WebView exposant des CheckBox sans checkable ni checked.
        class Opaque : FakeApp() {
            var options = false
            var saved = false
            var clickedBox = false
            override fun build(): SnapshotNode = when {
                saved -> gameScreen()
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                    button("Tout accepter"), button("Gérer les options").onClick { options = true },
                )
                else -> window(
                    text("Intérêt légitime et consentement"), text("Fournisseurs"),
                    *(1..3).map { i ->
                        group(text("Finalité $i"), node("android.widget.CheckBox", text = "Intérêt légitime", clickable = true).onClick { clickedBox = true })
                    }.toTypedArray(),
                    button("Confirmer les choix").onClick { saved = true },
                )
            }
        }
        val app = Opaque()
        val o = outcome(app)
        assertFalse("un état inconnu ne permet pas de conclure à un refus complet", o.success)
        assertEquals(OutcomeStatus.IMPOSSIBLE, o.status)
        assertFalse("la validation ne doit pas être envoyée avec un état inconnu", app.saved)
        assertFalse("une case d'état inconnu ne doit jamais être touchée", app.clickedBox)
        assertEquals(0, o.togglesOff)
        assertEquals(3, o.togglesUnknown)
    }

    @Test
    fun `identifiant technique de CMP compte comme indice`() {
        val root = window(
            text("Cookies"),
            node("android.widget.Button", text = "Paramètres", clickable = true, viewId = "io.didomi.sdk:id/button_learn_more"),
            button("Tout accepter"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.hints.toString(), a.hints.any { it.startsWith("id:") })
        assertTrue(a.isConsent)
    }

    // --- CMP -----------------------------------------------------------------------------------

    @Test
    fun `OneTrust - Reject All au premier niveau, jamais Accept All Cookies`() {
        class OneTrust : FakeApp() {
            var r: String? = null
            override fun build() = if (r != null) gameScreen() else window(
                node(
                    "android.view.View", viewId = "onetrust-banner-sdk",
                    children = listOf(
                        text("We use cookies and similar technologies. Our partners process your personal data."),
                        button("Accept All Cookies").onClick { r = "accept" },
                        button("Reject All").onClick { r = "reject" },
                        button("Cookies Settings"),
                    ),
                ),
            )
        }
        val app = OneTrust()
        assertTrue(outcome(app).success)
        assertEquals("reject", app.r)
        assertEquals(listOf("reject all"), app.clickedLabels)
    }

    @Test
    fun `sections repliees - jamais depliees, aucune validation sans refus et jamais Allow All`() {
        class Accordion : FakeApp() {
            val open = BooleanArray(3)
            val li = Array(3) { BooleanArray(4) { true } }
            var confirmed = false
            var accepted = false
            var options = false
            override fun build(): SnapshotNode = when {
                confirmed || accepted -> gameScreen()
                !options -> window(
                    text("We use cookies and process your personal data with our partners."),
                    button("Accept All Cookies").onClick { accepted = true },
                    button("Cookies Settings").onClick { options = true },
                )
                else -> window(
                    text("Privacy Preference Center"),
                    text("Our vendors rely on legitimate interest."),
                    group(
                        *(0 until 3).flatMap { c ->
                            val header = node("android.widget.Button", text = "Purpose $c", clickable = true, expandable = !open[c])
                                .onExpand { open[c] = true }
                            val rows = if (open[c]) {
                                (0 until 4).map { v -> group(text("Vendor $c-$v"), switch("Legitimate Interest", li[c][v]).onClick { li[c][v] = !li[c][v] }) }
                            } else {
                                emptyList()
                            }
                            listOf(header) + rows
                        }.toTypedArray(),
                    ),
                    button("Allow All").onClick { accepted = true },
                    button("Confirm My Choices").onClick { confirmed = true },
                )
            }
        }
        val app = Accordion()
        val o = outcome(app)
        // Refonte du 21/11 : aucune section n'est dépliée. Sans refus global ni choix visible,
        // enregistrer ne ferait que valider les réglages du site : rien n'est validé.
        assertFalse(o.success)
        assertFalse(app.confirmed)
        assertFalse(app.accepted)
        assertTrue(app.open.none { it })
        assertTrue(app.expandedLabels.isEmpty())
        assertEquals(listOf("cookies settings"), app.clickedLabels)
    }

    @Test
    fun `Usercentrics - Refuser tout au premier niveau`() {
        class Uc : FakeApp() {
            var result: String? = null
            override fun build() = if (result != null) gameScreen() else window(
                node(
                    "android.view.View", viewId = "usercentrics-root",
                    children = listOf(
                        text("Paramètres de confidentialité"),
                        text("Nous utilisons des cookies et des technologies similaires. Vos données sont traitées par nos partenaires."),
                        button("Paramètres"),
                        button("Refuser tout").onClick { result = "refus" },
                        button("Accepter tout").onClick { result = "accept" },
                    ),
                ),
            )
        }
        val app = Uc()
        val o = outcome(app)
        assertTrue(o.success)
        assertEquals("refus", app.result)
        assertEquals(listOf("refuser tout"), app.clickedLabels)
    }

    @Test
    fun `Sirdata - boutons Refuser par finalite puis Enregistrer`() {
        class Sirdata : FakeApp() {
            var options = false
            val refused = BooleanArray(5)
            var saved = false
            var accepted = false
            override fun build(): SnapshotNode = when {
                saved || accepted -> gameScreen()
                !options -> window(
                    text("Nos partenaires et nous utilisons des cookies pour traiter vos données."),
                    button("Personnaliser").onClick { options = true },
                    button("Accepter").onClick { accepted = true },
                )
                else -> window(
                    text("Paramètres des cookies : finalités et partenaires"),
                    group(
                        *(0 until 5).map { i ->
                            group(
                                text("Finalité $i"),
                                button("Accepter").onClick { refused[i] = false },
                                button(if (refused[i]) "Refusé" else "Refuser").onClick { refused[i] = true },
                            )
                        }.toTypedArray(),
                    ),
                    button("Enregistrer").onClick { saved = true },
                )
            }
        }
        val app = Sirdata()
        val o = outcome(app)
        assertTrue("échec : ${o.reason}", o.success)
        assertTrue(app.saved)
        assertFalse(app.accepted)
        assertTrue(app.refused.all { it })
    }

    @Test
    fun `InMobi Choice - Plus d'options, onglet Partenaires, S'opposer a tout, Enregistrer et quitter`() {
        class InMobi : FakeApp() {
            var page = "notice"
            var objected = false
            var saved = false
            var accepted = false
            override fun build(): SnapshotNode = when {
                saved || accepted -> gameScreen()
                page == "notice" -> window(
                    text("Nous accordons de l'importance à votre vie privée"),
                    text("Nos partenaires et nous stockons et/ou accédons à des informations sur un appareil, telles que les cookies."),
                    button("PLUS D'OPTIONS").onClick { page = "purposes" },
                    button("J'ACCEPTE").onClick { accepted = true },
                )
                page == "purposes" -> window(
                    text("Nous accordons de l'importance à votre vie privée — finalités"),
                    text("Consentement et intérêt légitime"),
                    switch("Consentement", false),
                    button("PARTENAIRES").onClick { page = "partners" },
                    button("TOUT ACCEPTER").onClick { accepted = true },
                    button("ENREGISTRER ET QUITTER").onClick { saved = true },
                )
                else -> window(
                    text("Partenaires — intérêt légitime"),
                    switch("Intérêt légitime Criteo", !objected),
                    switch("Intérêt légitime Google", !objected),
                    button("S'OPPOSER À TOUT").onClick { objected = true },
                    button("TOUT ACCEPTER").onClick { accepted = true },
                    button("ENREGISTRER ET QUITTER").onClick { saved = true },
                )
            }
        }
        val app = InMobi()
        val o = outcome(app)
        assertTrue("échec : ${o.reason}", o.success)
        assertTrue(app.saved)
        assertTrue(app.objected)
        assertFalse(app.accepted)
        assertEquals("enregistrer et quitter", app.clickedLabels.last())
    }

    @Test
    fun `anglais - Reject all au premier niveau malgre Accept all`() {
        class En : FakeApp() {
            var r: String? = null
            override fun build() = if (r != null) gameScreen() else window(
                text("We value your privacy"),
                text("We and our partners use cookies to personalise ads. Legitimate interest applies."),
                button("Accept all").onClick { r = "accept" },
                button("Reject all").onClick { r = "reject" },
                button("More options"),
            )
        }
        val app = En()
        assertTrue(outcome(app).success)
        assertEquals("reject", app.r)
    }

    @Test
    fun `Didomi visible marker recognized without inventing refusal`() {
        val root = window(
            node("android.view.View", viewId = "didomi-notice", children = listOf(
                text("Nous utilisons des cookies et traitons vos données personnelles."),
                button("Tout accepter"),
            )),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.isConsent)
        assertEquals("Didomi", a.platform)
        assertTrue(a.buttons(ButtonKind.REFUSE).isEmpty())
    }

    @Test
    fun `Axeptio visible marker recognized and accept is never actionable`() {
        val root = window(
            node("android.view.View", viewId = "axeptio-widget", children = listOf(
                text("Vos données et vos préférences de confidentialité."),
                button("Accepter"),
            )),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.isConsent)
        assertEquals("Axeptio", a.platform)
        assertTrue(a.buttons.none { it.kind == ButtonKind.ACCEPT && it.target != null })
    }

    @Test
    fun `Web ARIA role without explicit state remains unknown`() {
        val root = window(
            node("android.view.View", viewId = "didomi-consent", children = listOf(
                text("Cookies et partenaires"),
                text("Intérêt légitime"),
                SnapshotNode(
                    className = "android.view.View",
                    text = "Intérêt légitime",
                    webRole = "switch",
                    isClickable = true,
                    bounds = Bounds(0, 160, 1000, 240),
                ),
                button("Enregistrer"),
            )),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertEquals(1, a.unknownStateToggles)
        // L'état inconnu est conservé explicitement (null), jamais converti en « désactivé ».
        assertEquals(listOf(null), a.toggleStates.values.toList())
    }

    @Test
    fun `Didomi - selectionner Bloquer dans le groupe radio sans jamais cliquer Autoriser`() {
        class DidomiRadio : FakeApp() {
            var blocked = false
            var saved = false
            var accepted = false
            var allowSelected = true
            override fun build(): SnapshotNode = when {
                saved -> gameScreen()
                else -> window(
                    text("Cookies et partenaires"),
                    text("Nous traitons vos données personnelles et votre consentement."),
                    text("Intérêt légitime"),
                    node("android.widget.RadioButton", text = "Autoriser", checkable = true, checked = !blocked, clickable = true)
                        .onClick { accepted = true },
                    node("android.widget.RadioButton", text = "Bloquer", checkable = true, checked = blocked, clickable = true)
                        .onClick { blocked = true; allowSelected = false },
                    button("Enregistrer").onClick { saved = true },
                )
            }
        }
        val app = DidomiRadio()
        val result = outcome(app)
        assertTrue("le choix Bloquer doit être sélectionné", app.blocked)
        assertFalse("Autoriser ne doit jamais être cliqué", app.accepted)
        assertFalse("Autoriser doit être désélectionné après le choix Bloquer", app.allowSelected)
        assertFalse("aucune action d'acceptation ne doit être envoyée", app.clickedLabels.any {
            TextNormalizer.canonical(it).contains("autoriser")
        })
        assertTrue("la sélection doit être enregistrée", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, result.status)
        assertEquals(true, result.stateVerified)
    }

    @Test
    fun `un bouton texte Bloquer hors RadioButton ne devient pas un choix radio`() {
        val root = window(
            text("Cookies et partenaires"),
            text("Nous traitons vos données personnelles."),
            button("Bloquer"),
            button("Autoriser"),
            button("Enregistrer"),
        )
        val analysis = ScreenAnalyzer(rules).analyze(root)
        assertTrue(analysis.buttons(ButtonKind.RADIO_REFUSE).isEmpty())
    }

    @Test
    fun `un bouton radio avec des mots en plus ne devient pas un choix de refus`() {
        // Régression : la correspondance exacte des choix radio ne doit pas toucher les boutons
        // ordinaires (« Refuser maintenant »), et un radio « Refuser maintenant » reste non reconnu.
        val root = window(
            text("Cookies et partenaires"),
            text("Nous traitons vos données personnelles."),
            node("android.widget.RadioButton", text = "Autoriser", checkable = true, checked = true, clickable = true),
            node("android.widget.RadioButton", text = "Refuser maintenant", checkable = true, checked = false, clickable = true),
            node("android.widget.RadioButton", text = "Bloquer ce partenaire et tout", checkable = true, checked = false, clickable = true),
            button("Enregistrer"),
        )
        val analysis = ScreenAnalyzer(rules).analyze(root)
        assertTrue(analysis.buttons(ButtonKind.RADIO_REFUSE).isEmpty())
        assertTrue(analysis.buttons.none { it.isRadio && it.kind != ButtonKind.ACCEPT })
    }

    @Test
    fun `Didomi - radio sans etat publiee bloque la validation`() {
        class OpaqueRadio : FakeApp() {
            var saved = false
            var accepted = false
            override fun build(): SnapshotNode = if (saved) gameScreen() else window(
                text("Cookies et partenaires"),
                text("Nous traitons vos données personnelles et votre consentement."),
                node("android.widget.RadioButton", text = "Autoriser", clickable = true)
                    .onClick { accepted = true },
                node("android.widget.RadioButton", text = "Bloquer", clickable = true),
                button("Enregistrer").onClick { saved = true },
            )
        }
        val app = OpaqueRadio()
        val result = outcome(app)
        assertFalse("l'état inconnu ne doit pas être validé", app.saved)
        assertFalse("Autoriser ne doit jamais être cliqué", app.accepted)
        assertFalse("un état radio non publié ne peut pas être vérifié", result.stateVerified == true)
    }
}
