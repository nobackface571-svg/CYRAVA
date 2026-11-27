package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppConsent (SFBX) dans Opera, 22/11 à 14:19 : « bouton de validation introuvable, même après
 * défilement jusqu'en bas de la fenêtre ». Aucun diagnostic de cette fenêtre : structure SIMULÉE
 * (liste des finalités dans un bloc profond, validation dans le pied, loin de ce bloc).
 */
class AppConsent22NovSoirTest {
    private val rules = TestRules.rules

    private fun deep(levels: Int, inner: SnapshotNode): SnapshotNode =
        (1..levels).fold(inner) { acc, _ -> node(children = listOf(acc)) }

    private inner class AppConsent(private val confirmLabel: String, private val footerVisibleAtStart: Boolean) : FakeApp("com.opera.browser") {
        val on = booleanArrayOf(true, true)
        var footerShown = footerVisibleAtStart
        var saved = false
        var accepted = false
        override fun onShowOnScreen(node: UiNode) { footerShown = true }
        override fun build(): SnapshotNode = if (saved) {
            Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
        } else {
            Tree.window(node("android.webkit.WebView", children = listOf(
                node(viewId = "appconsent-container", children = listOf(
                    deep(4, node(children = listOf(
                        text("Vos préférences : nos partenaires et nous utilisons des cookies et traceurs avec votre consentement"),
                        button("Tout accepter").onClick { accepted = true },
                        Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = on[0]).onClick { on[0] = false }),
                        Tree.group(text("Publicité personnalisée"), Tree.switch("Publicité personnalisée", checked = on[1]).onClick { on[1] = false }),
                    ))),
                    node(children = listOf(
                        button(confirmLabel, visible = footerShown).onClick { saved = true },
                    )),
                )),
            )))
        }
    }

    private fun run(app: AppConsent): Outcome {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        return runEngine(app, engine, clock).outcome!!
    }

    @Test
    fun `validation du pied eloignee de la liste - reconnue et cliquee`() {
        val app = AppConsent("Valider mes choix", footerVisibleAtStart = true)
        val o = run(app)
        assertFalse(app.accepted)
        assertTrue(app.on.none { it })
        assertTrue("validation non cliquée : ${o.reason} / ${app.clickedLabels}", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    @Test
    fun `validation sous l'ecran et loin de la liste - amenee a l'ecran puis cliquee`() {
        val app = AppConsent("Valider mes préférences", footerVisibleAtStart = false)
        val o = run(app)
        assertFalse(app.accepted)
        assertTrue(app.on.none { it })
        assertTrue("pied jamais amené à l'écran : ${o.reason}", app.footerShown)
        assertTrue("validation non cliquée : ${o.reason} / ${app.clickedLabels}", app.saved)
        // Avant : « Valider mes préférences » était pris pour un bouton d'options (« préférences ») :
        // cliqué comme une simple ouverture des réglages, d'où « la fenêtre s'est fermée sans validation ».
        assertEquals(o.reason.orEmpty(), OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    @Test
    fun `nouvelles formules de validation reconnues`() {
        val a = ScreenAnalyzer(rules)
        for (label in listOf("Valider mes préférences", "Enregistrer ma sélection", "Confirmer mes préférences", "Valider et continuer")) {
            assertTrue(label, a.match(TextNormalizer.canonical(label), rules.confirm) != null)
        }
    }
}
