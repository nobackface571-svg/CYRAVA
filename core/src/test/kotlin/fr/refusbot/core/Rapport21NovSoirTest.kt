package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Historique Opera / Firefox du 21/11 (soir) : vitesse et refus complet. */
class Rapport21NovSoirTest {
    private val rules = TestRules.rules

    /**
     * OneTrust dans Opera : préférences ouvertes (refus complet), mais aucun interrupteur lisible.
     * Avant : « refus impossible ». Maintenant : le « Tout refuser » de la page des préférences
     * (identifiant ot-pc-refuse-all-handler) sert de repli. Jamais « Confirmer mes choix ».
     */
    @Test
    fun `OneTrust - preferences illisibles, repli sur Tout refuser des preferences`() {
        val app = object : FakeApp("com.opera.browser") {
            var page = "banniere"
            override fun build(): SnapshotNode = when (page) {
                "banniere" -> Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "onetrust-banner-sdk", children = listOf(
                        text("Nous utilisons des cookies pour mesurer l'audience et afficher des publicités personnalisées"),
                        node("android.widget.Button", text = "Tout accepter", clickable = true, viewId = "onetrust-accept-btn-handler")
                            .onClick { error("acceptation") },
                        node("android.widget.Button", text = "Tout refuser", clickable = true, viewId = "onetrust-reject-all-handler")
                            .onClick { page = "ferme" },
                        node("android.widget.Button", text = "Paramètres des cookies", clickable = true, viewId = "onetrust-pc-btn-handler")
                            .onClick { page = "preferences" },
                    )),
                )))
                "preferences" -> Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "onetrust-pc-sdk", children = listOf(
                        text("Centre de préférences de la confidentialité"),
                        text("Cookies de performance, publicité ciblée et partenaires"),
                        node("android.widget.Button", text = "Autoriser tous", clickable = true, viewId = "accept-recommended-btn-handler")
                            .onClick { error("acceptation") },
                        node("android.widget.Button", text = "Tout refuser", clickable = true, viewId = "ot-pc-refuse-all-handler")
                            .onClick { page = "ferme" },
                        node("android.widget.Button", text = "Confirmer mes choix", clickable = true, viewId = "save-preference-btn-handler")
                            .onClick { error("validation sans rien avoir refusé") },
                    )),
                )))
                else -> Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }, clock)
        assertNotNull(r.outcome)
        assertEquals("ferme", app.page)
        assertEquals(listOf("parametres des cookies", "tout refuser"), app.clickedLabels)
    }

    /**
     * Défilement « à la Fylow » sans conteneur défilant (navigateurs) : balayer dans le panneau tant
     * que la liste bouge, décocher à chaque écran, puis enregistrer. Avant : un seul balayage d'essai.
     */
    @Test
    fun `liste sans conteneur defilant - balayages successifs jusqu'en bas`() {
        val app = object : FakeApp("com.opera.browser") {
            var offset = 0
            val on = BooleanArray(12) { true }
            var saved = false
            override fun onSwipe() {
                if (offset < 8) offset += 4
            }
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", bounds = Bounds(0, 200, 1080, 2200), children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                    ) + (offset until offset + 4).map { i ->
                        Tree.group(text("Finalité ${i + 1}"), Tree.switch("Finalité ${i + 1}", checked = on[i]).onClick { on[i] = false })
                    } + listOf(button("Enregistrer").onClick { saved = true })),
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertNotNull(r.outcome)
        assertTrue(app.on.joinToString(), app.on.none { it })
        assertTrue(app.saved)
        assertTrue(app.clickedLabels.none { it.contains("accepter") })
    }

    /** Défilement impossible : le « Tout refuser » visible sert de repli ; jamais « Enregistrer ». */
    @Test
    fun `defilement impossible - repli sur Tout refuser, jamais de validation`() {
        val app = object : FakeApp("com.opera.browser") {
            var closed = false
            override fun scrollIsBlocked(container: UiNode) = true
            override fun build(): SnapshotNode = if (closed) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                        node(scrollable = true, children = listOf(
                            Tree.group(text("Mesure"), Tree.switch("Mesure", checked = false)),
                            Tree.group(text("Publicité"), Tree.switch("Publicité", checked = false)),
                        )),
                        button("Tout refuser").onClick { closed = true },
                        button("Enregistrer").onClick { error("validation avec une liste pas lue") },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertNotNull(r.outcome)
        assertTrue(app.closed)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, r.outcome!!.status)
    }

    /** Le refus complet garde son parcours par les préférences sur Didomi (intérêt légitime). */
    @Test
    fun `Didomi - le refus complet ouvre toujours les preferences`() {
        Tree.reset()
        val w = Tree.window(node(viewId = "didomi-notice", children = listOf(
            text("Nous et nos partenaires utilisons des cookies et vos données personnelles, y compris sur la base de l'intérêt légitime"),
            button("Tout refuser"),
            button("Paramétrer"),
            button("Tout accepter"),
        )))
        val st = RefusalEngine(rules).apply { completeRefusal = true }.next(Screen("com.x", w))
        assertTrue(st.toString(), (st as Step.Act).description.startsWith("options"))
    }

    /** Relecture sécurité : interrupteurs décochés puis défilement bloqué → « Tout refuser », jamais « vérifié ». */
    @Test
    fun `defilement impossible apres decochage - jamais verifie`() {
        val app = object : FakeApp("com.opera.browser") {
            var closed = false
            val on = booleanArrayOf(true, true)
            override fun scrollIsBlocked(container: UiNode) = true
            override fun build(): SnapshotNode = if (closed) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                        node(scrollable = true, children = listOf(
                            Tree.group(text("Mesure"), Tree.switch("Mesure", checked = on[0]).onClick { on[0] = false }),
                            Tree.group(text("Publicité"), Tree.switch("Publicité", checked = on[1]).onClick { on[1] = false }),
                        )),
                        button("Tout refuser").onClick { closed = true },
                        button("Enregistrer").onClick { error("validation avec une liste pas lue") },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertTrue(app.closed)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, r.outcome!!.status)
    }

    /** Sans balayage abouti vers le bas (contenu qui change sans fin), aucun « vérifié » après la validation. */
    @Test
    fun `balayages sans fin - jamais verifie`() {
        val app = object : FakeApp("com.opera.browser") {
            var n = 0
            var on = true
            var saved = false
            override fun onSwipe() { n++ }
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", bounds = Bounds(0, 200, 1080, 2200), children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                        Tree.group(text("Partenaire ${n + 1}"), Tree.switch("Partenaire ${n + 1}", checked = false)),
                        Tree.group(text("Publicité"), Tree.switch("Publicité", checked = on).onClick { on = false }),
                        button("Enregistrer").onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock, maxSteps = 5_000)
        assertNotNull(r.outcome)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, r.outcome!!.status)
    }

    /** Plus rien ne bouge pendant 12 s (capture lente, clic sans effet) : arrêt, jamais « vérifié ». */
    @Test
    fun `ecran fige - arret apres 12 s`() {
        val clock = FakeClock()
        val e = RefusalEngine(rules) { clock.now }
        fun screen() = Tree.window(node(viewId = "didomi-popup", children = listOf(
            text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
            Tree.group(text("Publicité"), Tree.switch("Publicité", checked = true)),
            button("Enregistrer"),
        )))
        Tree.reset()
        val first = e.next(Screen("com.x", screen()))
        assertTrue(first is Step.Act)
        clock.advance(13_000)
        Tree.reset()
        val st = e.next(Screen("com.x", screen()))
        assertTrue(st.toString(), st is Step.Done)
        val o = (st as Step.Done).outcome
        assertTrue(o.reason ?: "", (o.reason ?: "").contains("plus rien ne bougeait"))
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }
}
