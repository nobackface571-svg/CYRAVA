package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Essais sur téléphone du 22/11 au soir : préférences Didomi en boutons radio.
 * - Firefox : « un choix radio coché n'est pas un refus reconnu ; validation bloquée ».
 * - Chrome : tous les choix sur « Refuser », pied de fenêtre sous l'écran, « défilement impossible ».
 */
class DidomiRadios22NovSoirTest {
    private val rules = TestRules.rules

    private fun radio(label: String, checked: Boolean) =
        node("android.widget.RadioButton", desc = label, checkable = true, checked = checked, clickable = true)

    /** Firefox : chaque radio est seul dans son enveloppe (<label>), la paire est un niveau plus haut. */
    private fun wrapped(r: SnapshotNode) = node(children = listOf(r))

    @Test
    fun `Firefox - radios enveloppes un par un - les paires Refuser X Accepter X sont reconnues`() {
        Tree.reset()
        val w = Tree.window(node("android.webkit.WebView", children = listOf(
            node(viewId = "didomi-popup", children = listOf(
                text("Autorisez ou refusez la collecte de vos données pour les finalités suivantes"),
                node(text = "Publicités personnalisées", children = listOf(
                    wrapped(radio("Refuser Publicités personnalisées", true)),
                    wrapped(radio("Accepter Publicités personnalisées", false)),
                )),
                node(text = "Contenu personnalisé", children = listOf(
                    wrapped(radio("Refuser Contenu personnalisé", true)),
                    wrapped(radio("Accepter Contenu personnalisé", false)),
                )),
                button("Enregistrer vos préférences et fermer"),
            )),
        )))
        val a = ScreenAnalyzer(rules).analyze(w, webContentOnly = true)
        val refusals = a.buttons(ButtonKind.RADIO_REFUSE)
        assertEquals(refusals.toString(), 2, refusals.size)
        assertTrue(refusals.all { it.selected == true })
    }

    @Test
    fun `Firefox - un groupe de radios aplati (plus de deux) ne forme toujours pas de paire`() {
        Tree.reset()
        val w = Tree.window(node("android.webkit.WebView", children = listOf(
            node(viewId = "didomi-popup", children = listOf(
                text("Autorisez ou refusez la collecte de vos données pour les finalités suivantes"),
                node(children = listOf(
                    wrapped(radio("Refuser Publicités personnalisées", false)),
                    wrapped(radio("Accepter Publicités personnalisées", true)),
                    wrapped(radio("Accepter Contenu personnalisé", false)),
                )),
                button("Enregistrer vos préférences et fermer"),
            )),
        )))
        val a = ScreenAnalyzer(rules).analyze(w, webContentOnly = true)
        assertTrue(a.buttons(ButtonKind.RADIO_REFUSE).isEmpty())
    }

    @Test
    fun `Firefox - Vous autorisez Refuser puis validation, jamais Accepter`() {
        val purposes = listOf("Stocker et/ou accéder à des informations sur un appareil", "Publicités personnalisées", "Contenu personnalisé")
        val app = object : FakeApp("org.mozilla.firefox") {
            var refused = false
            var saved = false
            var accepted = false
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article du jour"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", children = listOf(
                        node("android.widget.TextView", desc = "Préférences sur l'utilisation de vos données", viewId = "didomi-popup-preferences-title"),
                        text("Autorisez ou refusez la collecte de vos données personnelles pour les finalités suivantes"),
                        node(children = listOf(
                            node("android.widget.TextView", text = "VOUS AUTORISEZ", viewId = "didomi-popup-purposes-all"),
                            wrapped(radio("Refuser", refused).onClick { refused = true }),
                            wrapped(radio("Accepter", false).onClick { accepted = true }),
                        )),
                    ) + purposes.map { p ->
                        node(text = p, children = listOf(
                            wrapped(radio("Refuser $p", refused).onClick { refused = true }),
                            wrapped(radio("Accepter $p", false).onClick { accepted = true }),
                        ))
                    } + listOf(
                        node("android.widget.Button", desc = "Enregistrer vos préférences et fermer", viewId = "btn-toggle-save", clickable = true)
                            .onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        val r = runEngine(app, engine, clock)
        val o = r.outcome!!
        assertFalse("« Accepter » ne doit jamais être cliqué", app.accepted)
        assertTrue("validation non cliquée : ${o.reason} / ${app.clickedLabels}", app.saved)
        assertFalse(o.reason.orEmpty(), o.reason.orEmpty().contains("choix radio coché"))
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    /**
     * Chrome, 15:13 : tous les choix déjà sur « Refuser », aucune liste défilante, et les boutons du pied
     * (« Refuser tout… », « Enregistrer… ») sous l'écran. Avant : balayages sans effet puis « défilement
     * impossible ». Désormais : « Enregistrer » est amené à l'écran d'un coup, puis cliqué.
     */
    @Test
    fun `Chrome - choix radio tous refuses - le bouton Enregistrer est amene a l'ecran puis clique`() {
        val app = object : FakeApp("com.android.chrome") {
            var footerShown = false
            var saved = false
            var accepted = false
            var swipes = 0
            override fun onSwipe() { swipes++ }
            override fun onShowOnScreen(node: UiNode) { footerShown = true }
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", children = listOf(
                        node("android.widget.TextView", desc = "Préférences sur l'utilisation de vos données", viewId = "didomi-popup-preferences-title"),
                        text("Autorisez ou refusez la collecte de vos données personnelles pour les finalités suivantes"),
                        node(children = listOf(
                            node("android.widget.TextView", text = "VOUS AUTORISEZ", viewId = "didomi-popup-purposes-all"),
                            radio("Refuser", true),
                            radio("Accepter", false).onClick { accepted = true },
                        )),
                        node(text = "Publicités personnalisées", children = listOf(
                            radio("Refuser Publicités personnalisées", true),
                            radio("Accepter Publicités personnalisées", false).onClick { accepted = true },
                        )),
                        node("android.widget.Button", desc = "Refuser tout nos traitements de données et fermer",
                            viewId = "btn-toggle-disagree", clickable = true, visible = footerShown),
                        node("android.widget.Button", desc = "Enregistrer vos préférences et fermer",
                            viewId = "btn-toggle-save", clickable = true, visible = footerShown).onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        val r = runEngine(app, engine, clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        assertTrue("pied de fenêtre jamais amené à l'écran", app.footerShown)
        assertTrue("validation non cliquée : ${o.reason} / ${app.clickedLabels}", app.saved)
        assertNotEquals(OutcomeStatus.INCOMPLETE_CAPTURE, o.status)
        assertEquals("aucun balayage inutile", 0, app.swipes)
    }

    /** Défilement bloqué, « Tout refuser » sous l'écran : il est amené à l'écran (pas cliqué à l'aveugle), puis cliqué. */
    @Test
    fun `defilement impossible - le Tout refuser sous l'ecran est amene a l'ecran puis clique`() {
        val app = object : FakeApp("com.android.chrome") {
            val on = booleanArrayOf(true, true)
            var shown = false
            var refusedAll = false
            var accepted = false
            override fun scrollIsBlocked(container: UiNode) = true
            override fun onShowOnScreen(node: UiNode) { shown = true }
            override fun build(): SnapshotNode = if (refusedAll) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "didomi-popup", children = listOf(
                        text("Vos choix pour nos partenaires : consentement et intérêt légitime, finalités et cookies"),
                        Tree.group(
                            Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = on[0]).onClick { on[0] = false }),
                            Tree.group(text("Publicité ciblée"), Tree.switch("Publicité ciblée", checked = on[1]).onClick { on[1] = false }),
                            scrollable = true,
                        ),
                        node("android.widget.Button", desc = "Refuser tout nos traitements de données et fermer",
                            viewId = "btn-toggle-disagree", clickable = true, visible = shown).onClick { refusedAll = true },
                        button("Tout accepter").onClick { accepted = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        val r = runEngine(app, engine, clock)
        assertTrue("refus jamais amené à l'écran : ${r.outcome?.reason} ${r.acts.map { it.description }}", app.shown)
        assertTrue("refus non cliqué : ${r.outcome?.reason} / ${app.clickedLabels}", app.refusedAll)
        assertFalse(app.accepted)
        assertTrue(r.outcome!!.success)
    }
}
