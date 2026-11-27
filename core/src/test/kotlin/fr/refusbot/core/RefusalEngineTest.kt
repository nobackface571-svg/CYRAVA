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

class RefusalEngineTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }

    private val forbiddenClicks = listOf("accept", "agree", "autoris", "allow", "consentir", "ok")

    private fun assertNeverAccepted(app: FakeApp) {
        for (label in app.clickedLabels) {
            val canon = TextNormalizer.canonical(label)
            if (canon in rules.refusalFormulas) continue
            forbiddenClicks.forEach { assertFalse("clic interdit : « $label »", TextNormalizer.containsPhrase(canon, it) || canon.contains(it)) }
        }
    }

    // --- Écran de l'appli après fermeture de la fenêtre --------------------------------------
    private fun gameScreen() = window(text("Niveau 1"), button("Jouer"), button("Boutique"))

    // =========================================================================================
    // (1) Bandeau avec « Tout refuser »
    // =========================================================================================
    private class BannerApp : FakeApp() {
        var open = true
        var refused = false
        var accepted = false
        override fun build(): SnapshotNode = if (!open) {
            window(text("Niveau 1"), button("Jouer"))
        } else {
            window(
                group(
                    text("Nous utilisons des cookies"),
                    text("Nos partenaires et nous utilisons vos données pour personnaliser les publicités."),
                    button("Tout accepter").onClick { accepted = true; open = false },
                    button("Paramètres").onClick { },
                    button("Tout refuser").onClick { refused = true; open = false },
                ),
            )
        }
    }

    @Test
    fun `bandeau avec Tout refuser - refus direct en un clic`() {
        val app = BannerApp()
        val r = runEngine(app, engine, clock)
        val o = assertNotNullOutcome(r)
        assertTrue(o.success)
        assertEquals(RefusalMethod.DIRECT, o.method)
        assertEquals(listOf("tout refuser"), app.clickedLabels)
        assertEquals(0, o.togglesSeen)
        assertTrue(app.refused)
        assertFalse(app.accepted)
        assertEquals(1, o.actions)
        assertNull(o.diagnosticRoot)
        assertNeverAccepted(app)
    }

    @Test
    fun `apres succes la meme fenetre est ignoree pendant le delai de grace`() {
        val app = BannerApp()
        runEngine(app, engine, clock)
        val distinctBanner = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles"),
            button("Gérer les options"),
        )
        assertEquals("une transition de page après fermeture ne doit pas relancer la session", Step.Idle,
            engine.next(Screen(app.packageName, distinctBanner)))
        app.open = true
        val again = runEngine(app, engine, clock)
        assertNull(again.outcome)
        clock.advance(rules.limits.cooldownMs + 1)
        val third = runEngine(app, engine, clock)
        assertNotNull(third.outcome)
    }

    // =========================================================================================
    // (2) Style Google UMP : « Gérer les options » → plusieurs pages à défiler
    //     Consentement OFF + Intérêt légitime ON → « Confirmer les choix »
    // =========================================================================================
    private class UmpApp(purposeCount: Int = 10, vendorCount: Int = 12, private val pageSize: Int = 4) : FakeApp() {
        enum class Page { NOTICE, OPTIONS, VENDORS, CLOSED }

        inner class Item(val name: String, var consent: Boolean, var li: Boolean?)

        var page = Page.NOTICE
        var offset = 0
        var acceptedAll = false
        var confirmed = false
        val purposes = (1..purposeCount).map { Item("Finalité $it", consent = false, li = if (it == 1) null else true) }
        val vendors = (1..vendorCount).map { Item("Fournisseur publicitaire $it", consent = false, li = true) }
        var necessary = true

        val allLiOff get() = (purposes + vendors).all { it.li != true }
        val anyConsentOn get() = (purposes + vendors).any { it.consent }

        private fun rows(items: List<Item>): List<SnapshotNode> = items.map { item ->
            val children = mutableListOf(
                text(item.name),
                switch("Consentement", item.consent).onClick { item.consent = !item.consent },
            )
            item.li?.let { children.add(switch("Intérêt légitime", it).onClick { item.li = !item.li!! }) }
            group(*children.toTypedArray())
        }

        private fun footer() = group(
            button("Tout accepter").onClick {
                acceptedAll = true
                (purposes + vendors).forEach { it.consent = true; if (it.li != null) it.li = true }
                page = Page.CLOSED
            },
            button("Confirmer les choix").onClick { confirmed = true; page = Page.CLOSED },
        )

        override fun onScroll(container: UiNode) {
            val total = if (page == Page.OPTIONS) purposes.size + 2 else vendors.size
            if (offset + pageSize < total) offset += pageSize
        }

        override fun build(): SnapshotNode = when (page) {
            Page.CLOSED -> window(text("Niveau 1"), button("Jouer"), button("Paramètres"))
            Page.NOTICE -> window(
                group(
                    text("Example Game demande votre consentement pour utiliser vos données personnelles :"),
                    text("Publicités et contenu personnalisés, mesure de performance, données d'audience"),
                    text("Vos données personnelles seront traitées et des informations de votre appareil pourront être stockées par nos 150 partenaires."),
                    button("Autoriser").onClick {
                        acceptedAll = true
                        page = Page.CLOSED
                    },
                    button("Gérer les options").onClick { page = Page.OPTIONS; offset = 0 },
                ),
            )
            Page.OPTIONS -> {
                // Liste « native » : seules les lignes visibles existent dans l'arbre.
                val all = mutableListOf<SnapshotNode>()
                all.add(group(text("Cookies strictement nécessaires"), text("Toujours actif"), switch(null, necessary, enabled = false)))
                all.addAll(rows(purposes))
                all.add(button("Préférences concernant les fournisseurs").onClick { page = Page.VENDORS; offset = 0 })
                val visible = all.drop(offset).take(pageSize)
                window(
                    text("Gérer vos données"),
                    text("Vous pouvez choisir comment vos données personnelles sont utilisées. Nos partenaires peuvent invoquer un intérêt légitime."),
                    group(*visible.toTypedArray(), cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true),
                    footer(),
                )
            }
            Page.VENDORS -> {
                val visible = rows(vendors).drop(offset).take(pageSize)
                window(
                    text("Préférences concernant les fournisseurs"),
                    text("Ces fournisseurs invoquent un intérêt légitime ou demandent votre consentement."),
                    group(*visible.toTypedArray(), cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true),
                    footer(),
                )
            }
        }
    }

    @Test
    fun `style Google UMP - options, defilement, interet legitime decoche, confirmation`() {
        val app = UmpApp()
        val r = runEngine(app, engine, clock)
        val o = assertNotNullOutcome(r)

        assertTrue("échec : ${o.reason}", o.success)
        assertEquals(RefusalMethod.OPTIONS, o.method)
        assertTrue(app.confirmed)
        assertFalse(app.acceptedAll)
        assertTrue("tous les intérêts légitimes doivent être désactivés", app.allLiOff)
        assertFalse(app.anyConsentOn)
        assertTrue("l'interrupteur « toujours actif » ne doit pas bouger", app.necessary)
        assertEquals(9 + 12, o.togglesOff) // 9 finalités avec intérêt légitime + 12 fournisseurs
        assertTrue(o.togglesSeen >= 9 + 12)
        assertTrue("trop d'actions : ${o.actions}", o.actions <= rules.limits.maxActions)
        assertEquals("gerer les options", app.clickedLabels.first())
        assertEquals("confirmer les choix", app.clickedLabels.last())
        assertTrue("preferences concernant les fournisseurs" in app.clickedLabels)
        assertFalse("autoriser" in app.clickedLabels)
        assertFalse("tout accepter" in app.clickedLabels)
        assertFalse("parametres" in app.clickedLabels) // bouton de l'appli après fermeture : jamais touché
        assertNeverAccepted(app)
    }

    @Test
    fun `style UMP en WebView - lignes hors ecran jamais actionnees, revelees par le defilement`() {
        // En WebView, les lignes hors écran existent dans l'arbre (isVisibleToUser = false). Elles ne
        // sont jamais actionnées tant qu'elles ne sont pas à l'écran : le défilement les révèle par lots.
        class WebUmp : FakeApp() {
            val li = BooleanArray(30) { true }
            var shown = 5
            override fun onScroll(container: UiNode) {
                shown = minOf(30, shown + 5)
            }
            var confirmed = false
            var options = false
            override fun build(): SnapshotNode = if (confirmed) {
                window(text("Accueil"))
            } else if (!options) {
                window(
                    node(
                        "android.webkit.WebView", scrollable = true,
                        children = listOf(
                            text("Nous et nos partenaires stockons et/ou accédons à des informations sur un appareil (cookies)."),
                            text("Vos données personnelles"),
                            button("Tout accepter"),
                            button("Plus d'options").onClick { options = true },
                        ),
                    ),
                )
            } else {
                window(
                    node(
                        "android.webkit.WebView", scrollable = true,
                        children = listOf(text("Nos partenaires et l'intérêt légitime")) + li.indices.map { i ->
                            group(
                                text("Partenaire $i", visible = i < shown),
                                switch("Intérêt légitime", li[i], visible = i < shown).onClick { li[i] = !li[i] },
                            )
                        } + listOf(button("Tout accepter"), button("Enregistrer et quitter").onClick { confirmed = true }),
                    ),
                )
            }
        }
        val app = WebUmp()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue("échec : ${o.reason}", o.success)
        assertTrue(app.li.none { it })
        assertEquals(30, o.togglesOff)
        assertEquals("enregistrer et quitter", app.clickedLabels.last())
        assertNeverAccepted(app)
    }

    // =========================================================================================
    // (3) Aucun refus possible → aucune action
    // =========================================================================================
    @Test
    fun `aucun refus possible - aucune action et echec journalise`() {
        class AcceptOnly : FakeApp() {
            var accepted = false
            override fun build() = window(
                text("Nous utilisons des cookies et partageons vos données avec nos partenaires."),
                button("Tout accepter").onClick { accepted = true },
                button("OK").onClick { accepted = true },
            )
        }
        val app = AcceptOnly()
        val r = runEngine(app, engine, clock)
        val o = assertNotNullOutcome(r)
        assertFalse(o.success)
        assertTrue(r.acts.isEmpty())
        assertEquals(0, app.opsCount)
        assertFalse(app.accepted)
        assertEquals(0, o.actions)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("aucune option de refus"))
        assertNotNull("un échec doit fournir l'arbre pour le diagnostic", o.diagnosticRoot)

        // Pas de nouvelle tentative (ni notification) en rafale sur la même fenêtre.
        clock.advance(rules.limits.cooldownMs + 1)
        assertNull(runEngine(app, engine, clock).outcome)
        clock.advance(rules.limits.failureCooldownMs)
        assertNotNull(runEngine(app, engine, clock).outcome)
    }

    // =========================================================================================
    // Cas complémentaires
    // =========================================================================================
    @Test
    fun `ecran ordinaire - rien a faire`() {
        val root = window(text("Bienvenue"), button("Jouer"), button("Paramètres"), text("Politique de confidentialité"))
        assertEquals(Step.Idle, engine.next(Screen("com.example.jeu", root)))
    }

    @Test
    fun `un seul indice ne suffit pas`() {
        val root = window(text("Cookies"), button("Tout accepter"))
        assertEquals(Step.Idle, engine.next(Screen("com.example.jeu", root)))
    }

    @Test
    fun `Continuer sans accepter est clique, jamais Accepter et fermer`() {
        class Didomi : FakeApp() {
            var result: String? = null
            override fun build() = if (result != null) gameScreen() else window(
                text("Avec votre accord, nos partenaires et nous utilisons des cookies ou technologies similaires."),
                text("Vos données personnelles"),
                button("Continuer sans accepter →").onClick { result = "refus" },
                button("Accepter et fermer").onClick { result = "accept" },
                button("En savoir plus"),
            )
        }
        val app = Didomi()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.success)
        assertEquals("refus", app.result)
        assertEquals(listOf("continuer sans accepter"), app.clickedLabels)
    }

    @Test
    fun `refus dont le conteneur contient aussi Accepter - bloque par le garde-fou`() {
        class Trap : FakeApp() {
            var accepted = false
            override fun build() = window(
                text("Nous et nos partenaires utilisons des cookies pour vos données."),
                node(
                    "android.widget.LinearLayout", clickable = true,
                    children = listOf(text("Refuser"), text("Accepter les cookies")),
                ).onClick { accepted = true },
            )
        }
        val app = Trap()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(o.success)
        assertFalse(app.accepted)
        assertEquals(0, app.opsCount)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("garde-fou"))
    }

    @Test
    fun `mur payant Refuser et s'abonner - jamais clique`() {
        class PayWall : FakeApp() {
            override fun build() = window(
                text("Pour continuer, acceptez les cookies de nos partenaires ou abonnez-vous."),
                text("Vos données"),
                button("Accepter et continuer"),
                button("Refuser et s'abonner"),
            )
        }
        val app = PayWall()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(o.success)
        assertEquals(0, app.opsCount)
    }

    @Test
    fun `libelle avec mots en plus - Tout refuser et fermer`() {
        class App : FakeApp() {
            var closed = false
            override fun build() = if (closed) gameScreen() else window(
                text("Ce site utilise des cookies et des traceurs."),
                button("Tout accepter"),
                button("Tout refuser et fermer").onClick { closed = true },
            )
        }
        val app = App()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.success)
        assertEquals(listOf("tout refuser et fermer"), app.clickedLabels)
    }

    @Test
    fun `bouton qui ne reagit pas - un seul clic puis abandon sans boucler`() {
        class Stuck : FakeApp() {
            override fun build() = window(
                text("Nous utilisons des cookies avec nos partenaires."),
                button("Tout accepter"),
                button("Tout refuser"),
            )
        }
        val app = Stuck()
        val r = runEngine(app, engine, clock)
        val o = assertNotNullOutcome(r)
        assertFalse(o.success)
        // Anti-boucle (21/11) : jamais deux clics sur le même élément ; sans effet, on passe à la suite.
        assertEquals(listOf("tout refuser"), app.clickedLabels)
        assertTrue(r.acts.none { act -> act.ops.any { it is Op.Click && it.target is ClickTarget.Point } })
    }

    @Test
    fun `fenetre sans fin - limite de 40 actions`() {
        class Endless : FakeApp() {
            var n = 0
            override fun build() = window(
                text("Étape $n : cookies et partenaires"),
                text("Vos données"),
                switch("Intérêt légitime ${n}", true).onClick { n++ },
                button("Tout accepter"),
            )
        }
        val app = Endless()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(o.success)
        assertTrue(o.actions <= rules.limits.maxActions)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("40") || o.reason.orEmpty().contains("délai"))
    }

    @Test
    fun `delai depasse - abandon`() {
        class Slow(val clock: FakeClock) : FakeApp() {
            var n = 0
            override fun build() = window(
                text("Cookies et partenaires $n"),
                text("Vos données"),
                switch("Intérêt légitime $n", true).onClick { n++; clock.advance(5_000) },
                button("Tout accepter"),
            )
        }
        val app = Slow(clock)
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(o.success)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("délai"))
    }

    @Test
    fun `changement d'appli pendant la session - abandon sans diagnostic`() {
        val consent = window(text("Cookies"), text("Nos partenaires"), button("Gérer les options"), button("Tout accepter"))
        val step = engine.next(Screen("a.b", consent))
        assertTrue(step is Step.Act)
        val done = engine.next(Screen("c.d", window(text("Autre"))))
        assertTrue(done is Step.Done)
        assertNull((done as Step.Done).outcome.diagnosticRoot)
    }

    @Test
    fun `boutons globaux d'abord puis validation`() {
        class Sp : FakeApp() {
            var objected = false
            var saved = false
            var page = 0
            override fun build() = when {
                saved -> gameScreen()
                page == 0 -> window(
                    text("We and our partners use cookies and process your personal data."),
                    button("Accept all"),
                    button("Manage options").onClick { page = 1 },
                )
                else -> window(
                    text("Privacy settings"),
                    text("Legitimate interest"),
                    switch("Analytics", !objected),
                    switch("Ads", !objected),
                    button("Object to all").onClick { objected = true },
                    button("Accept all"),
                    button("Save & exit").onClick { saved = true },
                )
            }
        }
        val app = Sp()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.success)
        assertEquals(listOf("manage options", "object to all", "save exit"), app.clickedLabels)
    }

    @Test
    fun `etat Android explicite prime et une description contradictoire rend le controle inconnu`() {
        val analyzer = ScreenAnalyzer(rules.copy(checkedStates = listOf("active"), uncheckedStates = listOf("inactive")))
        val off = SnapshotNode(
            className = "android.widget.Switch",
            isCheckable = true,
            isChecked = false,
            stateDescription = "inactive",
        )
        val on = SnapshotNode(
            className = "android.widget.Switch",
            isCheckable = true,
            isChecked = true,
            stateDescription = "active",
        )
        val contradictory = SnapshotNode(
            className = "android.widget.Switch",
            isCheckable = true,
            isChecked = false,
            stateDescription = "active",
        )
        val ariaOnly = SnapshotNode(
            className = "android.view.View",
            isCheckable = false,
            isChecked = false,
            stateDescription = "active",
        )

        assertEquals(false, analyzer.toggleState(off))
        assertEquals(true, analyzer.toggleState(on))
        assertNull("les métadonnées contradictoires doivent rester inconnues", analyzer.toggleState(contradictory))
        assertNull("une description seule ne prouve pas l'état", analyzer.toggleState(ariaOnly))
    }

    @Test
    fun `interrupteur desactive ou toujours actif jamais touche`() {
        val root = window(
            text("Cookies"), text("Partenaires"),
            group(text("Cookies essentiels"), text("Toujours actif"), switch("Essentiels", true)),
            group(text("Mesure d'audience"), switch("Audience", true, enabled = false)),
            group(text("Publicité"), switch("Publicité", true)),
            button("Enregistrer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertEquals(listOf("publicite"), a.checkedToggles.map { it.label })
    }

    @Test
    fun `champ mot de passe jamais lu`() {
        val n = node("android.widget.EditText", text = "secret", password = true)
        assertNull(n.text)
        assertTrue(n.ownTexts().isEmpty())
    }

    @Test
    fun `scroll bloque declare par l'executeur - jamais traite comme fin de liste`() {
        class StuckScroll : FakeApp() {
            var options = false
            var saved = false
            override fun build() = if (!options) window(
                text("Nous utilisons des cookies avec nos partenaires."),
                text("Vos données personnelles"),
                button("Gérer les options").onClick { options = true },
                button("Tout accepter"),
            ) else window(
                text("Préférences de confidentialité"),
                text("Partenaires et intérêt légitime"),
                group(switch("Publicité", false), cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true),
                button("Enregistrer").onClick { saved = true },
            )
            // Le conteneur accepte la demande simulée mais son contenu ne bouge jamais, alors qu'Android
            // propose toujours de défiler : l'exécuteur le déclare bloqué.
            override fun onScroll(container: UiNode) = Unit
            override fun scrollIsBlocked(container: UiNode) = true
        }
        val app = StuckScroll()
        val result = runEngine(app, engine, clock)
        val outcome = assertNotNullOutcome(result)
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, outcome.status)
        assertFalse("la validation ne doit pas être cliquée après un scroll sans progression", app.saved)
        assertFalse(outcome.status.isRefusal)
    }

    @Test
    fun `fenetre disparue sans validation - interrompue avec capture pour le diagnostic`() {
        class Vanishing : FakeApp() {
            var options = false
            var gone = false
            override fun build() = when {
                gone -> window(text("Accueil du site"), button("Suivant"))
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."),
                    text("Vos données personnelles"),
                    button("Gérer les options").onClick { options = true },
                    button("Tout accepter"),
                )
                // La fenêtre disparaît dès le premier interrupteur touché, sans aucune validation.
                else -> window(
                    text("Préférences de confidentialité"),
                    text("Partenaires et intérêt légitime"),
                    switch("Publicité", true).onClick { gone = true },
                    button("Enregistrer"),
                )
            }
        }
        val app = Vanishing()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(o.success)
        assertEquals(OutcomeStatus.INTERRUPTED, o.status)
        assertNotNull("la dernière fenêtre doit être jointe pour comprendre la disparition", o.diagnosticRoot)
        assertNeverAccepted(app)
    }

    @Test
    fun `lien partenaires dans le texte - le vrai bouton Continuer sans accepter est trouve`() {
        // Cas SAFTI / Presse-citron (18/11) : un lien « nos 4 partenaires » au milieu du texte ne doit
        // pas faire prendre le paragraphe seul pour la fenêtre entière.
        class InlineLink : FakeApp() {
            var refused = false
            override fun build() = if (refused) gameScreen() else window(
                group(
                    text("Avec votre accord, nous et nos partenaires utilisons des cookies pour traiter vos données personnelles."),
                    button("nos 4 partenaires"),
                ),
                button("Continuer sans accepter").onClick { refused = true },
                button("Accepter & Fermer"),
            )
        }
        val app = InlineLink()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.reason.orEmpty(), o.success)
        assertTrue(app.refused)
        assertNeverAccepted(app)
    }

    @Test
    fun `Didomi - Refuser tout nos traitements de donnees et fermer`() {
        class App : FakeApp() {
            var refused = false
            override fun build() = if (refused) gameScreen() else window(
                text("Paramétrez vos consentements : cookies, partenaires et finalités."),
                node("android.widget.Button", desc = "Refuser tout nos traitements de données et fermer", clickable = true)
                    .onClick { refused = true },
                node("android.widget.Button", desc = "Accepter tout nos traitements de données et fermer", clickable = true),
            )
        }
        val app = App()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.reason.orEmpty(), o.success)
        assertTrue(app.refused)
        assertNeverAccepted(app)
    }

    @Test
    fun `compteur de partenaires jamais clique`() {
        // SAFTI 18/11 : les puces « 1 partenaire », « 2 partenaires » ouvraient une simple liste de noms.
        class App : FakeApp() {
            var saved = false
            override fun build() = if (saved) gameScreen() else window(
                text("Préférences : cookies, partenaires et finalités publicitaires."),
                button("2 partenaires"),
                button("nos 751 partenaires"),
                button("Refuser tout").onClick { saved = true },
                button("Accepter tout"),
            )
        }
        val app = App()
        assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse(app.clickedLabels.toString(), app.clickedLabels.any { it.contains("partenaires") })
        assertNeverAccepted(app)
    }

    @Test
    fun `categorie Necessaires jamais decochee`() {
        // Franklin / Cookiebot 18/11 : décocher « Nécessaires » déclenchait une alerte bloquante.
        class App : FakeApp() {
            override fun build() = window(
                text("Cookies et partenaires : vos préférences de consentement."),
                group(text("Nécessaires 17"), switch(null, true).onClick { error("clic interdit sur Nécessaires") }),
                group(text("Marketing 36"), switch(null, true)),
                button("Enregistrer"),
            )
        }
        val app = App()
        runEngine(app, engine, clock)
        assertNeverAccepted(app)
    }

    private class CookiebotLike(val marketingStuck: Boolean = false, val marketingGreyed: Boolean = false) : FakeApp() {
        var details = false
        var saved = false
        var acceptedAll = false
        var prefs = true
        var marketing = true
        override fun build() = when {
            saved || acceptedAll -> window(text("Boutique"), button("Panier"))
            !details -> window(
                text("Ce site utilise des cookies et des partenaires pour la publicité."),
                button("Personnaliser").onClick { details = true },
                button("Autoriser tous").onClick { acceptedAll = true },
            )
            else -> window(
                text("Préférences de consentement : cookies et partenaires."),
                group(text("Nécessaires 17"), switch(null, true)),
                group(text("Préférences 4"), switch(null, prefs).onClick { prefs = !prefs }),
                group(text("Marketing 36"), switch(null, marketing, enabled = !marketingGreyed).onClick { if (!marketingStuck) marketing = !marketing }),
                button("Autoriser la sélection").onClick { saved = true },
                button("Autoriser tous").onClick { acceptedAll = true },
            )
        }
    }

    @Test
    fun `Autoriser la selection clique seulement quand tout est refuse`() {
        val app = CookiebotLike()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertTrue(o.reason.orEmpty(), o.success)
        assertTrue(app.saved)
        assertFalse(app.acceptedAll)
        assertFalse(app.prefs)
        assertFalse(app.marketing)
        assertFalse(app.clickedLabels.toString(), app.clickedLabels.any { it == "autoriser tous" })
    }

    @Test
    fun `Autoriser la selection jamais clique si un choix reste active`() {
        val app = CookiebotLike(marketingStuck = true)
        runEngine(app, engine, clock)
        assertFalse("validation avec Marketing encore activé", app.saved)
        assertFalse(app.acceptedAll)
    }

    @Test
    fun `Autoriser la selection jamais clique si un choix active est grise`() {
        val app = CookiebotLike(marketingGreyed = true)
        val o = runEngine(app, engine, clock).outcome
        assertFalse("validation avec Marketing activé et grisé", app.saved)
        assertFalse(app.acceptedAll)
        assertTrue(o?.status != OutcomeStatus.OPTIONS_SUCCESS)
    }

    @Test
    fun `titre Necessaires reconnu seulement s'il est exact`() {
        assertTrue(isNecessaryHeading("necessaires"))
        assertTrue(isNecessaryHeading("necessaires 17"))
        assertTrue(isNecessaryHeading("cookies strictement necessaires"))
        assertFalse(isNecessaryHeading("necessaires a la mesure d audience et a la publicite"))
        assertFalse(isNecessaryHeading("essentiels et personnalisation"))
        assertFalse(isNecessaryHeading("essential ads"))
    }

    @Test
    fun `garde-fou - Autoriser la selection bloque sans l'autorisation du moteur`() {
        val guard = SafetyGuard(rules)
        assertFalse(guard.checkText("Autoriser la sélection").isAllowed)
        assertTrue(guard.checkText("Autoriser la sélection", selectionConfirm = true).isAllowed)
        // Jamais « Autoriser tous », même avec l'autorisation du moteur.
        assertFalse(guard.checkText("Autoriser tous", selectionConfirm = true).isAllowed)
        assertFalse(guard.checkText("Autoriser la sélection et tout le reste", selectionConfirm = true).isAllowed)
    }

    @Test
    fun `audit C1 - validation seule au premier niveau jamais cliquee`() {
        class App : FakeApp() {
            var validated = false
            override fun build() = if (validated) gameScreen() else window(
                text("Nous utilisons des cookies avec nos partenaires pour la publicité."),
                button("Tout accepter"),
                button("Valider").onClick { validated = true },
            )
        }
        val app = App()
        val o = assertNotNullOutcome(runEngine(app, engine, clock))
        assertFalse("validation cliquée sans rien refuser", app.validated)
        assertFalse(o.success)
        assertNeverAccepted(app)
    }

    @Test
    fun `audit I3 - obligatoire dans une description ne protege pas un interrupteur`() {
        val root = window(
            text("Cookies et partenaires"), text("Vos préférences"),
            group(text("Marketing"), text("Ces cookies publicitaires ne sont pas obligatoires pour naviguer."), switch(null, true)),
            group(text("Essentiels"), text("Toujours actif"), switch(null, true, enabled = false)),
            button("Enregistrer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertEquals(1, a.checkedToggles.size)
    }

    private fun assertNotNullOutcome(r: RunResult): Outcome {
        assertNotNull("le moteur aurait dû terminer une session", r.outcome)
        return r.outcome!!
    }
}
