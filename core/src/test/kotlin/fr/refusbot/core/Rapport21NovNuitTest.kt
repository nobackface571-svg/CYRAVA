package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du 21/11 au soir (Chrome, page AlternativeTo de CYRAVA et sa fenêtre Google UMP) :
 * - liste de près de 480 partenaires : seulement 7 interrupteurs décochés avant la fin du temps ;
 * - fenêtre terminée à la main, puis la page derrière prise pour la même fenêtre (« bouton de
 *   validation introuvable ») et diagnostic montrant la page au lieu de la fenêtre ;
 * - le lien « Consent-O-Matic » pris pour un bouton « Consent » (acceptation).
 */
class Rapport21NovNuitTest {
    private val rules = TestRules.rules
    private val chrome = "com.android.chrome"

    /** Page AlternativeTo : texte sur les cookies, lien « Consent-O-Matic », boutons « J'aime » cochables. */
    private fun alternativeToPage(): SnapshotNode = Tree.window(node("android.webkit.WebView", children = listOf(
        text("CYRAVA Alternatives"),
        text("The only alternative to CYRAVA so far is"),
        node(desc = "Consent-O-Matic", clickable = true, children = listOf(text("Consent-O-Matic"))),
        node("android.widget.ToggleButton", clickable = true, checkable = true, checked = false),
        node("android.widget.ToggleButton", text = " 44 likes", clickable = true, checkable = true, checked = false),
        text(
            "Browser extension automatically responds to GDPR consent pop-ups, supports setting global " +
                "privacy preferences, consolidates data into five user-toggle categories",
        ),
        node(desc = "Cookie Manager", clickable = true, children = listOf(text("Cookie Manager"))),
        node(desc = "Privacy Tool", clickable = true, children = listOf(text("Privacy Tool"))),
        text("You think this is a good alternative!"),
        button("Remove vote"),
    )))

    @Test
    fun `lien Consent-O-Matic - pas un bouton d'acceptation`() {
        val analyzer = ScreenAnalyzer(rules)
        assertNull(analyzer.match("consent o matic", rules.accept, strictAccept = true))
        // Les acceptations ordinaires restent reconnues (détection seulement, jamais cliquées).
        assertNotNull(analyzer.match("accepter et continuer", rules.accept, strictAccept = true))
        assertNotNull(analyzer.match("accept all cookies", rules.accept, strictAccept = true))
        assertNotNull(analyzer.match("consent", rules.accept, strictAccept = true))
        // Bouton radio « Autoriser ce partenaire » : toujours reconnu, pour bloquer une validation.
        assertNotNull(analyzer.match("autoriser ce partenaire", rules.accept))
    }

    @Test
    fun `page AlternativeTo - aucune fenetre de cookies, aucune action`() {
        Tree.reset()
        val page = alternativeToPage()
        val e = RefusalEngine(rules)
        assertFalse(e.analyze(page, chrome).isConsent)
        assertEquals(Step.Idle, e.next(Screen(chrome, page)))
    }

    /**
     * Fenêtre Google UMP terminée à la main par l'utilisateur, la page AlternativeTo réapparaît derrière :
     * la session s'arrête (« fermée sans validation »), sans défiler ni chercher de bouton sur la page,
     * et le diagnostic garde la FENÊTRE (pas la page).
     */
    @Test
    fun `fenetre UMP fermee a la main - diagnostic de la fenetre, pas de la page`() {
        val clock = FakeClock()
        val e = RefusalEngine(rules) { clock.now }
        var closedByUser = false
        fun ump() = Tree.window(node("android.webkit.WebView", children = listOf(
            node(viewId = "fc-dialog-container", children = listOf(
                text("Préférences concernant les fournisseurs"),
                text("alternativeto.net demande votre consentement pour utiliser vos données personnelles : cookies et intérêt légitime"),
                node(scrollable = true, children = (1..12).map { i ->
                    Tree.group(text("Partenaire $i"), Tree.switch("Intérêt légitime", checked = true))
                }),
                button("Retour"),
                button("Confirmer les choix", visible = false),
            )),
        )))
        Tree.reset()
        val first = e.next(Screen(chrome, ump()))
        assertTrue(first.toString(), first is Step.Act)
        closedByUser = true
        var outcome: Outcome? = null
        repeat(20) {
            if (outcome != null) return@repeat
            Tree.reset()
            when (val st = e.next(Screen(chrome, if (closedByUser) alternativeToPage() else ump()))) {
                is Step.Done -> outcome = st.outcome
                is Step.Wait -> clock.advance(st.delayMs)
                is Step.Act -> {
                    assertTrue("aucun défilement ni clic sur la page : ${st.description}", false)
                }
                Step.Idle -> return@repeat
            }
        }
        val o = outcome!!
        assertEquals(OutcomeStatus.INTERRUPTED, o.status)
        assertTrue(o.reason ?: "", (o.reason ?: "").contains("fermée sans validation"))
        val diag = o.diagnosticRoot!!
        assertTrue("le diagnostic doit montrer la fenêtre UMP", diag.walk().any { it.viewId == "fc-dialog-container" })
    }

    /**
     * Liste UMP de 300 partenaires dans Chrome : 8 lignes à l'écran, les autres dans l'arbre, hors écran,
     * avec leur état. Tout est décoché en rafale sans défiler 300 lignes, la validation est amenée à
     * l'écran d'un coup, puis enregistrée. Résultat vérifié, en peu d'étapes.
     */
    @Test
    fun `liste UMP de 300 partenaires - decochage en rafale hors ecran puis validation`() {
        val n = 300
        val app = object : FakeApp(chrome) {
            var page = "banniere"
            var offset = 0
            val on = BooleanArray(n) { true }
            var scrolls = 0
            override fun onScroll(container: UiNode) {
                scrolls++
                offset = minOf(offset + 8, n)
            }
            override fun onShowOnScreen(node: UiNode) {
                offset = n
            }
            override fun build(): SnapshotNode = when (page) {
                "banniere" -> Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "fc-consent-root", children = listOf(
                        text("alternativeto.net demande votre consentement pour utiliser vos données personnelles pour les cookies et la publicité"),
                        button("Autoriser").onClick { error("acceptation") },
                        button("Gérer les options").onClick { page = "liste" },
                    )),
                )))
                "liste" -> Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "fc-dialog-container", children = listOf(
                        text("Préférences concernant les fournisseurs"),
                        text("Consentement et intérêt légitime de nos partenaires pour les cookies"),
                        node(scrollable = true, children = (0 until n).map { i ->
                            val vis = i in offset until offset + 8
                            Tree.group(
                                text("Partenaire ${i + 1}", visible = vis),
                                Tree.switch("Intérêt légitime", checked = on[i], visible = vis).onClick { on[i] = false },
                            )
                        } + listOf(
                            button("Confirmer les choix", visible = offset >= n).onClick { page = "ferme" },
                        )),
                    )),
                )))
                else -> Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            }
        }
        val clock = FakeClock()
        // Réglages les plus exigeants : refus complet et « laisser la fenêtre ouverte si non vérifiable ».
        val engine = RefusalEngine(rules) { clock.now }.apply {
            completeRefusal = true
            holdWhenUnverifiable = true
        }
        val r = runEngine(app, engine, clock)
        val o = r.outcome!!
        assertEquals(r.acts.joinToString("\n") { it.description } + "\n" + o, "ferme", app.page)
        assertTrue("interrupteurs restés activés", app.on.none { it })
        assertEquals(o.reason, OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals(n, o.togglesOff)
        assertTrue("trop de défilements : ${app.scrolls}", app.scrolls <= 3)
        assertTrue("trop d'étapes : ${r.acts.size}", r.acts.size <= 12)
        assertTrue(app.clickedLabels.none { it.contains("autoriser") })
        // Les clics hors écran se font sur le nœud, jamais par un appui aux coordonnées.
        val offscreenClicks = r.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().filter { it.offscreen }
        assertTrue(offscreenClicks.isNotEmpty())
        assertTrue(offscreenClicks.all { it.target is ClickTarget.Node && !it.allowTapFallback && it.isToggle })
    }

    /**
     * Site qui ignore les clics hors de l'écran : chaque interrupteur reste activé, puis il est réessayé
     * une seule fois quand le défilement le rend visible. Tout finit décoché et vérifié.
     */
    @Test
    fun `clics hors ecran ignores - second essai une fois visible`() {
        val n = 24
        val app = object : FakeApp(chrome) {
            var offset = 0
            val on = BooleanArray(n) { true }
            val clicks = IntArray(n)
            var saved = false
            override fun onScroll(container: UiNode) {
                offset = minOf(offset + 8, n - 8)
            }
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "fc-dialog-container", children = listOf(
                        text("Préférences concernant les fournisseurs"),
                        text("Consentement et intérêt légitime de nos partenaires pour les cookies"),
                        node(scrollable = true, children = (0 until n).map { i ->
                            val vis = i in offset until offset + 8
                            Tree.group(
                                text("Partenaire ${i + 1}", visible = vis),
                                Tree.switch("Intérêt légitime", checked = on[i], visible = vis).onClick {
                                    clicks[i]++
                                    if (vis) on[i] = false
                                },
                            )
                        }),
                        button("Confirmer les choix").onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true }
        val r = runEngine(app, engine, clock)
        val o = r.outcome!!
        assertTrue(r.acts.joinToString("\n") { it.description } + "\n" + o, app.saved)
        assertTrue(app.on.joinToString(), app.on.none { it })
        assertTrue("au plus deux clics par interrupteur : ${app.clicks.joinToString()}", app.clicks.all { it in 1..2 })
        assertEquals(o.reason, OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    /** Hors de l'écran : la visibilité seule est dispensée, jamais pour un geste ni une validation. */
    @Test
    fun `verification de la cible hors ecran`() {
        Tree.reset()
        val sw = Tree.switch("Intérêt légitime", checked = true, visible = false)
        Tree.window(sw)
        val guard = SafetyGuard(rules)
        val live = TargetCheck.Live(chrome, listOf("Intérêt légitime"), sw.bounds, windowOnTop = true, isVisibleToUser = false)
        assertNull(TargetCheck.mismatch(chrome, sw, live, guard, forGesture = false, offscreenAllowed = true))
        assertNotNull(TargetCheck.mismatch(chrome, sw, live, guard, forGesture = false))
        assertNotNull(TargetCheck.mismatch(chrome, sw, live, guard, forGesture = true, offscreenAllowed = true))
        assertNotNull(TargetCheck.mismatch(chrome, sw, live, guard, forGesture = false, selectionConfirm = true, offscreenAllowed = true))
        // Une autre fenêtre au premier plan ou un libellé changé bloquent toujours.
        assertNotNull(TargetCheck.mismatch(chrome, sw, live.copy(windowOnTop = false), guard, forGesture = false, offscreenAllowed = true))
        assertNotNull(TargetCheck.mismatch(chrome, sw, live.copy(texts = listOf("Tout accepter")), guard, forGesture = false, offscreenAllowed = true))
    }

    /** Un interrupteur hors écran déjà désactivé, ou toujours actif, n'est jamais cliqué. */
    @Test
    fun `hors ecran - seuls les interrupteurs actives et modifiables sont cibles`() {
        Tree.reset()
        val w = Tree.window(node("android.webkit.WebView", children = listOf(
            node(viewId = "fc-dialog-container", children = listOf(
                text("Préférences concernant les fournisseurs"),
                text("Consentement et intérêt légitime de nos partenaires pour les cookies"),
                Tree.group(text("Partenaire A"), Tree.switch("Intérêt légitime", checked = true)),
                Tree.group(text("Partenaire B", visible = false), Tree.switch("Intérêt légitime", checked = false, visible = false)),
                Tree.group(text("Partenaire C", visible = false), Tree.switch("Intérêt légitime", checked = true, enabled = false, visible = false)),
                Tree.group(text("Partenaire D", visible = false), Tree.switch("Intérêt légitime", checked = true, visible = false)),
                button("Confirmer les choix"),
            )),
        )))
        val a = ScreenAnalyzer(rules).analyze(w, webContentOnly = true)
        val off = a.checkedToggles.filter { it.offscreen }
        assertEquals(1, off.size)
        assertTrue(off.single().signature.contains("partenaire d"))
        assertNotEquals(null, off.single().target)
    }

    /**
     * Page GitHub d'une pull request (21/11, 20:45) : le texte parle de cookies et de consentement, il y a
     * des boutons « Show options », « Notifications Customize » et un lien « Manage cookies ». Avant :
     * CYRAVA ouvrait le menu d'un commentaire puis la fenêtre des notifications. Maintenant : rien.
     */
    @Test
    fun `page GitHub - des boutons Options seuls n'ouvrent jamais de session`() {
        Tree.reset()
        val page = Tree.window(node("android.webkit.WebView", children = listOf(
            text("Corrections de l'audit du 21/11 + identifiant fr.cyrava #6"),
            text("Ajout du 21/11 dans la nuit — historique Chrome, fenêtre de consentement Google UMP"),
            node("android.widget.Button", text = "Show options", clickable = true),
            text("Faux positif : le lien Consent-O-Matic n'est plus pris pour un bouton Consent. Les cookies et la vie privée."),
            node("android.widget.Button", text = "Notifications Customize", clickable = true),
            node("android.widget.Button", text = "Unsubscribe", clickable = true),
            text("Privacy"),
            node("android.widget.Button", text = "Manage cookies", clickable = true),
            node("android.widget.Button", text = "Do not share my personal information", clickable = true),
        )))
        val e = RefusalEngine(rules)
        assertFalse(e.analyze(page, chrome).canStartSession)
        assertEquals(Step.Idle, e.next(Screen(chrome, page)))
        // Même chose avec une capture incomplète (longue page).
        assertEquals(Step.Idle, RefusalEngine(rules).next(Screen(chrome, page, isTruncated = true)))
    }

    /** Une vraie bannière avec seulement « Paramétrer » mais l'identifiant d'une plateforme connue reste traitée. */
    @Test
    fun `banniere avec identifiant de plateforme et seulement des options - toujours traitee`() {
        Tree.reset()
        val w = Tree.window(node(viewId = "didomi-notice", children = listOf(
            text("Nous et nos partenaires utilisons des cookies et vos données personnelles pour la publicité"),
            button("Paramétrer"),
        )))
        val st = RefusalEngine(rules).next(Screen("com.x", w))
        assertTrue(st.toString(), st is Step.Act && st.description.startsWith("options"))
    }
}
