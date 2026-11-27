package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Essais sur téléphone du 27/11 (CYRAVA 2.0, profil Strict), corrections de la pull request du 27/11 :
 * 1. préférences BFM (12 h 04) : le « Tout refuser » du site était cliqué AVANT les choix un par un ;
 * 2. Didomi dans Firefox (RTBF, 12 h 08) : « Enregistrer » et « Tout refuser » cachés sous la barre de Firefox ;
 * 4. AliExpress (Chrome) : seul le lien « paramètres de cookies » du texte était analysé ;
 * 5. France Télévisions (Opera) : choix en boutons « Non | Oui » inconnus ;
 * 6. Opera (12 h 00) : « vérifié » annoncé sur une fenêtre trop longue pour être lue en entier.
 * Les fichiers de diagnostic n'étaient pas joints à cette session : fenêtres reconstituées d'après l'analyse
 * des diagnostics faite le 27/11. Tout est simulé (aucun essai sur téléphone ici).
 */
class Essais27NovTest {
    private val rules = TestRules.rules
    private val chrome = "com.android.chrome"
    private val firefox = "org.mozilla.firefox"
    private val opera = "com.opera.browser"

    private fun page(vararg children: SnapshotNode, height: Int = 2300) =
        Tree.window(node("android.webkit.WebView", bounds = Bounds(0, 0, 1080, height), children = children.toList()))

    private fun engine(clock: FakeClock, complete: Boolean = false) =
        RefusalEngine(rules) { clock.now }.apply { completeRefusal = complete }

    // --- 1. Préférences : les choix un par un avant le « Tout refuser » du site -------------------

    /** Premier écran sans refus (BFM) ; préférences : interrupteurs, « Tout refuser » et « Enregistrer ». */
    private class Bfm(private val withSave: Boolean = true) : FakeApp("com.example.actu") {
        var prefs = false
        var closedBy: String? = null
        var accepted = false
        val on = mutableMapOf("Publicité personnalisée" to true, "Mesure d'audience" to true, "Réseaux sociaux" to true)
        override fun build(): SnapshotNode = when {
            closedBy != null -> Tree.window(text("Les titres du jour"))
            !prefs -> Tree.window(
                text("Nous et nos partenaires utilisons des cookies pour traiter vos données personnelles."),
                button("Tout accepter").onClick { accepted = true },
                button("Paramétrer").onClick { prefs = true },
            )
            else -> Tree.window(
                text("Vos choix de consentement : cookies et données personnelles"),
                *on.keys.map { k -> switch(k, on.getValue(k)).onClick { on[k] = !on.getValue(k) } }.toTypedArray(),
                button("Tout refuser").onClick { closedBy = "tout refuser" },
                *(if (withSave) arrayOf(button("Enregistrer").onClick { closedBy = "enregistrer" }) else emptyArray()),
                button("Tout accepter").onClick { accepted = true },
            )
        }
    }

    @Test
    fun `preferences BFM - chaque choix d'abord puis Enregistrer, jamais Tout refuser`() {
        val app = Bfm()
        val clock = FakeClock()
        val rr = runEngine(app, engine(clock), clock)
        val o = rr.outcome!!
        assertFalse(app.accepted)
        assertEquals(rr.acts.joinToString { it.description } + " / " + o.reason, "enregistrer", app.closedBy)
        assertFalse("le « Tout refuser » des préférences ne passe plus avant les choix", "tout refuser" in app.clickedLabels)
        assertTrue("tous les interrupteurs décochés", app.on.values.none { it })
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals(3, o.togglesOff)
    }

    @Test
    fun `preferences BFM sans Enregistrer - Tout refuser en dernier recours, apres les choix`() {
        val app = Bfm(withSave = false)
        val clock = FakeClock()
        val o = runEngine(app, engine(clock), clock).outcome!!
        assertFalse(app.accepted)
        assertEquals("tout refuser", app.closedBy)
        assertTrue("les interrupteurs sont décochés AVANT le refus du site", app.on.values.none { it })
        assertEquals("tout refuser", app.clickedLabels.last())
        assertTrue(o.status.isRefusal)
    }

    /** Lignes Didomi « Refuser | Accepter » (simples boutons) et « Tout refuser » dans les préférences. */
    @Test
    fun `preferences en lignes Refuser Accepter - chaque Refuser avant Enregistrer`() {
        val app = object : FakeApp("com.example.actu") {
            var prefs = false
            var closedBy: String? = null
            var accepted = false
            val refused = mutableSetOf<String>()
            fun row(name: String, y: Int) = node(text = null, bounds = Bounds(0, y, 1080, y + 90), children = listOf(
                node("android.widget.TextView", text = name, bounds = Bounds(0, y, 500, y + 90)),
                node("android.widget.Button", text = "Refuser", clickable = true, bounds = Bounds(520, y, 760, y + 90)).onClick { refused.add(name) },
                node("android.widget.Button", text = "Accepter", clickable = true, bounds = Bounds(780, y, 1060, y + 90)).onClick { accepted = true },
            ))
            override fun build(): SnapshotNode = when {
                closedBy != null -> Tree.window(text("Accueil"))
                !prefs -> Tree.window(
                    text("Nous et nos partenaires utilisons des cookies pour traiter vos données personnelles."),
                    button("Tout accepter").onClick { accepted = true },
                    button("Paramétrer").onClick { prefs = true },
                )
                else -> Tree.window(
                    node("android.widget.TextView", text = "Vos choix : cookies et données personnelles", bounds = Bounds(0, 100, 1080, 200)),
                    row("Publicité", 300), row("Mesure d'audience", 420), row("Contenus", 540),
                    node("android.widget.Button", text = "Tout refuser", clickable = true, bounds = Bounds(0, 1500, 1080, 1600)).onClick { closedBy = "tout refuser" },
                    node("android.widget.Button", text = "Enregistrer", clickable = true, bounds = Bounds(0, 1650, 1080, 1750)).onClick { closedBy = "enregistrer" },
                )
            }
        }
        val clock = FakeClock()
        runEngine(app, engine(clock), clock).outcome!!
        assertFalse(app.accepted)
        assertEquals(setOf("Publicité", "Mesure d'audience", "Contenus"), app.refused)
        assertEquals("enregistrer", app.closedBy)
    }

    // --- 2. Pied de fenêtre sous la barre de Firefox --------------------------------------------

    /**
     * RTBF dans Firefox : les interrupteurs sont à l'écran, mais « Enregistrer » et « Tout refuser » sont
     * sous la barre de Firefox (taille normale, hors de l'écran) et ne bougent pas quand on défile.
     * Avant : 24 s de défilements pour rien. Désormais : après un essai sans effet, « Enregistrer » est
     * actionné par son nœud (jamais par un appui aux coordonnées).
     */
    @Test
    fun `Firefox - Enregistrer sous la barre, fenetre fixe - actionne par son noeud apres un essai`() {
        val below = Bounds(0, 2350, 1080, 2440)
        val app = object : FakeApp(firefox) {
            var prefs = false
            var saved = false
            var accepted = false
            val on = mutableMapOf("Publicité" to true, "Statistiques" to true)
            override fun build(): SnapshotNode = when {
                saved -> page(text("RTBF Info"))
                !prefs -> page(node(viewId = "cookie-banner", children = listOf(
                    text("Nous utilisons des cookies pour traiter vos données personnelles avec nos partenaires."),
                    button("Tout accepter").onClick { accepted = true },
                    button("Paramétrer").onClick { prefs = true },
                )))
                else -> page(node(viewId = "cookie-prefs", children = listOf(
                    text("Vos préférences : cookies et données personnelles"),
                    *on.keys.map { k -> switch(k, on.getValue(k)).onClick { on[k] = !on.getValue(k) } }.toTypedArray(),
                    node("android.widget.Button", text = "Enregistrer", clickable = true, visible = false, bounds = below).onClick { saved = true },
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        assertTrue("« Enregistrer » doit être actionné", app.saved)
        val save = r.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().single { it.label == "enregistrer" }
        assertTrue("action d'accessibilité du nœud seulement", save.offscreen)
        assertFalse("jamais d'appui aux coordonnées", save.allowTapFallback)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertTrue("au moins un essai pour l'amener à l'écran avant", r.acts.indexOfFirst { a -> a.ops.any { it is Op.ShowOnScreen || it is Op.Swipe || it is Op.Scroll } } in 0 until r.acts.indexOfFirst { a -> a.ops.any { it is Op.Click && it.label == "enregistrer" } })
    }

    /** Un bouton sous l'écran qui BOUGE quand on défile n'est jamais actionné par son nœud : il est amené à l'écran. */
    @Test
    fun `bouton sous l'ecran qui bouge au defilement - jamais pris pour un pied fixe`() {
        val app = object : FakeApp(firefox) {
            var offset = 0
            var saved = false
            val on = mutableMapOf("Publicité" to true, "Statistiques" to true)
            var prefs = false
            override fun build(): SnapshotNode {
                if (saved) return page(text("Accueil"))
                if (!prefs) return page(node(children = listOf(
                    text("Nous utilisons des cookies pour traiter vos données personnelles avec nos partenaires."),
                    button("Tout accepter"),
                    button("Paramétrer").onClick { prefs = true },
                )))
                val y = 2350 - offset
                val visible = y + 90 <= 2300
                return page(node(viewId = "cookie-prefs", children = listOf(
                    text("Vos préférences : cookies et données personnelles"),
                    *on.keys.map { k -> switch(k, on.getValue(k)).onClick { on[k] = !on.getValue(k) } }.toTypedArray(),
                    node("android.widget.Button", text = "Enregistrer", clickable = true, visible = visible, bounds = Bounds(0, y, 1080, y + 90)).onClick { saved = true },
                )))
            }
            override fun onSwipe() { offset = minOf(offset + 200, 800) }
            override fun onShowOnScreen(node: UiNode) { offset = 800 }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        assertTrue(r.acts.joinToString { it.description } + " / " + r.outcome?.reason, app.saved)
        val save = r.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().single { it.label == "enregistrer" }
        assertFalse("bouton amené à l'écran puis cliqué normalement", save.offscreen)
    }

    // --- 4. AliExpress : les vrais boutons à côté du lien du texte -----------------------------

    @Test
    fun `AliExpress - Refuser les cookies est vu malgre le lien parametres de cookies du texte`() {
        val app = object : FakeApp(chrome) {
            var refused = false
            var accepted = false
            override fun build(): SnapshotNode = if (refused) {
                page(text("AliExpress"))
            } else {
                page(node(children = listOf(
                    node(children = listOf(
                        text("Nous utilisons des cookies et traitons vos données personnelles. Vous pouvez modifier vos choix dans les"),
                        node("android.view.View", text = "paramètres de cookies", clickable = true),
                    )),
                    button("Refuser les cookies").onClick { refused = true },
                    button("Personnaliser"),
                    button("Accepter les cookies").onClick { accepted = true },
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        assertFalse(app.accepted)
        assertTrue("« Refuser les cookies » doit être cliqué", app.refused)
        assertEquals("refuser les cookies", app.clickedLabels.first())
        assertTrue(r.outcome!!.status.isRefusal)
    }

    // --- 5. France Télévisions : choix « Non | Oui » --------------------------------------------

    @Test
    fun `France Televisions - chaque Non clique, jamais Oui, puis Enregistrer`() {
        val app = object : FakeApp(opera) {
            var prefs = false
            var saved = false
            var accepted = false
            val refused = mutableSetOf<String>()
            fun row(name: String, y: Int) = node(bounds = Bounds(0, y, 1080, y + 90), children = listOf(
                node("android.widget.TextView", text = name, bounds = Bounds(0, y, 600, y + 90)),
                node("android.widget.Button", text = "Non", clickable = true, bounds = Bounds(620, y, 820, y + 90)).onClick { refused.add(name) },
                node("android.widget.Button", text = "Oui", clickable = true, bounds = Bounds(840, y, 1060, y + 90)).onClick { accepted = true },
            ))
            override fun build(): SnapshotNode = when {
                saved -> page(text("france.tv"))
                !prefs -> page(node(children = listOf(
                    text("Nous et nos partenaires utilisons des cookies pour traiter vos données personnelles."),
                    button("Tout accepter").onClick { accepted = true },
                    button("Paramétrer").onClick { prefs = true },
                )))
                else -> page(node(bounds = Bounds(0, 0, 1080, 2000), children = listOf(
                    node("android.widget.TextView", text = "Gestion des cookies et de vos données personnelles", bounds = Bounds(0, 100, 1080, 200)),
                    row("Mesure d'audience", 300), row("Publicité ciblée", 420), row("Réseaux sociaux", 540),
                    node("android.widget.Button", text = "Enregistrer", clickable = true, bounds = Bounds(0, 1700, 1080, 1800)).onClick { saved = true },
                )))
            }
        }
        val clock = FakeClock()
        val o = runEngine(app, engine(clock), clock).outcome!!
        assertFalse("« Oui » ne doit jamais être cliqué", app.accepted)
        assertEquals(setOf("Mesure d'audience", "Publicité ciblée", "Réseaux sociaux"), app.refused)
        assertTrue(app.saved)
        assertNotEquals("plus de « fenêtre fermée sans validation »", OutcomeStatus.INTERRUPTED, o.status)
        assertFalse("l'état des « Non » n'est pas publié : jamais « vérifié »", o.status.isVerified)
    }

    @Test
    fun `un Non seul, sans Oui a cote, n'est pas un refus`() {
        val a = ScreenAnalyzer(rules).analyze(
            page(node(children = listOf(
                text("Nous utilisons des cookies pour traiter vos données personnelles."),
                node("android.widget.Button", text = "Non", clickable = true, bounds = Bounds(0, 300, 300, 390)),
                node("android.widget.Button", text = "Tout accepter", clickable = true, bounds = Bounds(0, 600, 1080, 690)),
            ))),
            webContentOnly = true,
        )
        assertTrue(a.buttons(ButtonKind.REFUSE).isEmpty())
    }

    // --- 6. Jamais « vérifié » si une capture de la session était tronquée ----------------------

    /**
     * Première capture tronquée : « Tout refuser » cliqué, mais la fenêtre reste ; CYRAVA ouvre ensuite
     * les préférences (lues en entier). Les interrupteurs sont décochés et relus, puis le « Refuser tout » des
     * préférences ferme la fenêtre. Avant : « vérifié », alors qu'une partie de la fenêtre n'avait pas été lue.
     */
    @Test
    fun `capture tronquee dans la session - jamais verifie meme si les interrupteurs relus sont desactives`() {
        val app = object : FakeApp("com.example.jeu") {
            var stage = 0
            var accepted = false
            val on = mutableMapOf("Publicité" to true, "Statistiques" to true)
            override fun build(): SnapshotNode = when (stage) {
                0 -> Tree.window(
                    text("Nous utilisons des cookies pour traiter vos données personnelles."),
                    button("Tout refuser").onClick { stage = 1 },
                    button("Tout accepter").onClick { accepted = true },
                )
                1 -> Tree.window(
                    text("Encore une étape : cookies et données personnelles"),
                    button("Paramétrer").onClick { stage = 2 },
                    button("Tout accepter").onClick { accepted = true },
                )
                2 -> Tree.window(
                    text("Vos préférences : cookies et données personnelles"),
                    *on.keys.map { k -> switch(k, on.getValue(k)).onClick { on[k] = !on.getValue(k) } }.toTypedArray(),
                    button("Refuser tout").onClick { stage = 3 },
                )
                else -> Tree.window(text("Jouer"))
            }
        }
        val clock = FakeClock()
        val engine = engine(clock)
        var outcome: Outcome? = null
        repeat(200) {
            if (outcome != null) return@repeat
            when (val step = engine.next(Screen(app.packageName, app.render(), isTruncated = app.stage == 0))) {
                Step.Idle -> error("inactif")
                is Step.Wait -> clock.advance(step.delayMs)
                is Step.Act -> { step.ops.forEach(app::perform); clock.advance(250) }
                is Step.Done -> outcome = step.outcome
            }
        }
        val o = outcome!!
        assertFalse(app.accepted)
        assertFalse("une capture tronquée interdit « vérifié »", o.status.isVerified)
        assertTrue(o.captureTruncated)
        assertTrue("les interrupteurs ont bien été décochés et relus", app.on.values.none { it } && o.togglesOff == 2)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
    }

    // --- Relecture sécurité du 27/11 -------------------------------------------------------------

    private fun yesNoScreen(vararg rows: SnapshotNode) = page(node(bounds = Bounds(0, 0, 1080, 2000), children = listOf(
        node("android.widget.TextView", text = "Gestion des cookies et de vos données personnelles", bounds = Bounds(0, 100, 1080, 200)),
        *rows,
        node("android.widget.Button", text = "Enregistrer", clickable = true, bounds = Bounds(0, 1700, 1080, 1800)),
    )))

    @Test
    fun `selecteur Non Oui en un seul element - aucune cible`() {
        val a = ScreenAnalyzer(rules).analyze(yesNoScreen(
            node(bounds = Bounds(0, 300, 1080, 390), children = listOf(
                node("android.widget.TextView", text = "Publicité", bounds = Bounds(0, 300, 600, 390)),
                node("android.view.View", clickable = true, bounds = Bounds(620, 300, 1060, 390), children = listOf(
                    node("android.widget.TextView", text = "Non", bounds = Bounds(620, 300, 820, 390)),
                    node("android.widget.TextView", text = "Oui", bounds = Bounds(840, 300, 1060, 390)),
                )),
            )),
        ), webContentOnly = true)
        assertTrue("aucun « Non » cliquable dans un élément qui contient aussi « Oui »", a.buttons(ButtonKind.REFUSE).none { it.target != null })
    }

    @Test
    fun `question qui porte sur un refus - la paire Non Oui est ignoree`() {
        fun row(q: String, y: Int) = node(bounds = Bounds(0, y, 1080, y + 90), children = listOf(
            node("android.widget.TextView", text = q, bounds = Bounds(0, y, 600, y + 90)),
            node("android.widget.Button", text = "Non", clickable = true, bounds = Bounds(620, y, 820, y + 90)),
            node("android.widget.Button", text = "Oui", clickable = true, bounds = Bounds(840, y, 1060, y + 90)),
        ))
        val a = ScreenAnalyzer(rules).analyze(
            yesNoScreen(row("Désactiver la mesure d'audience ?", 300), row("Refuser la publicité ciblée ?", 420)),
            webContentOnly = true,
        )
        assertTrue(a.buttons(ButtonKind.REFUSE).isEmpty())
    }

    @Test
    fun `premier ecran - un Non seul ne ferme jamais la fenetre`() {
        val app = object : FakeApp(opera) {
            var clicked = false
            override fun build(): SnapshotNode = page(node(bounds = Bounds(0, 1500, 1080, 2000), children = listOf(
                node("android.widget.TextView", text = "Nous utilisons des cookies pour traiter vos données personnelles. Acceptez-vous ?", bounds = Bounds(0, 1500, 1080, 1700)),
                node(bounds = Bounds(0, 1750, 1080, 1840), children = listOf(
                    node("android.widget.Button", text = "Non", clickable = true, bounds = Bounds(0, 1750, 500, 1840)).onClick { clicked = true },
                    node("android.widget.Button", text = "Oui", clickable = true, bounds = Bounds(560, 1750, 1060, 1840)).onClick { clicked = true },
                )),
            )))
        }
        val clock = FakeClock()
        runEngine(app, engine(clock), clock)
        assertFalse(app.clicked)
    }

    @Test
    fun `interrupteurs verifies et lignes Non cliquees - jamais verifie`() {
        val app = object : FakeApp(opera) {
            var prefs = false
            var saved = false
            var pub = true
            fun row(name: String, y: Int) = node(bounds = Bounds(0, y, 1080, y + 90), children = listOf(
                node("android.widget.TextView", text = name, bounds = Bounds(0, y, 600, y + 90)),
                node("android.widget.Button", text = "Non", clickable = true, bounds = Bounds(620, y, 820, y + 90)),
                node("android.widget.Button", text = "Oui", clickable = true, bounds = Bounds(840, y, 1060, y + 90)),
            ))
            override fun build(): SnapshotNode = when {
                saved -> page(text("france.tv"))
                !prefs -> page(node(children = listOf(
                    text("Nous et nos partenaires utilisons des cookies pour traiter vos données personnelles."),
                    button("Tout accepter"),
                    button("Paramétrer").onClick { prefs = true },
                )))
                else -> page(node(bounds = Bounds(0, 0, 1080, 2000), children = listOf(
                    node("android.widget.TextView", text = "Gestion des cookies et de vos données personnelles", bounds = Bounds(0, 100, 1080, 200)),
                    node("android.widget.Switch", text = "Publicité", checkable = true, checked = pub, clickable = true, bounds = Bounds(0, 220, 1080, 290)).onClick { pub = !pub },
                    row("Mesure d'audience", 300), row("Réseaux sociaux", 420),
                    node("android.widget.Button", text = "Enregistrer", clickable = true, bounds = Bounds(0, 1700, 1080, 1800)).onClick { saved = true },
                )))
            }
        }
        val clock = FakeClock()
        val o = runEngine(app, engine(clock), clock).outcome!!
        assertTrue(app.saved)
        assertFalse(o.status.isVerified)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
    }

    @Test
    fun `bouton au-dessus de l'ecran - jamais pris pour un pied fixe`() {
        val a = ScreenAnalyzer(rules).analyze(page(node(children = listOf(
            text("Vos préférences : cookies et données personnelles"),
            switch("Publicité", true),
            node("android.widget.Button", text = "Enregistrer", clickable = true, visible = false, bounds = Bounds(0, -400, 1080, -310)),
        ))), webContentOnly = true)
        assertTrue(a.buttons(ButtonKind.CONFIRM).none { it.stuckTarget != null })
    }
}
