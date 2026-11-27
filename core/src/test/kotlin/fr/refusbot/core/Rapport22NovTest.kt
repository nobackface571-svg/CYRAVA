package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Essais sur téléphone du 22/11 (Opera, Chrome, Firefox) : boutons de validation non trouvés. */
class Rapport22NovTest {
    private val rules = TestRules.rules

    /**
     * Commanders Act (TrustCommander) : la liste des finalités est un bloc à part, et « Enregistrer la
     * configuration des cookies » (identifiant save-consent) est dans le pied de la fenêtre, hors de ce
     * bloc. Avant : « bouton de validation introuvable, même après défilement ».
     */
    @Test
    fun `Commanders Act - validation du pied de fenetre reconnue et cliquee apres decochage`() {
        val app = object : FakeApp("com.opera.browser") {
            val on = booleanArrayOf(true, true)
            var saved = false
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node(viewId = "privacy-cat-modal", children = listOf(
                        node(children = listOf(
                            text("Paramétrage des cookies : nos partenaires et nous utilisons des cookies avec votre consentement"),
                            Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = on[0]).onClick { on[0] = false }),
                            Tree.group(text("Publicité ciblée"), Tree.switch("Publicité ciblée", checked = on[1]).onClick { on[1] = false }),
                        )),
                        node("android.widget.Button", text = "", desc = "Enregistrer la configuration des cookies", viewId = "save-consent", clickable = true)
                            .onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertTrue(app.on.none { it })
        assertTrue("validation non cliquée : ${app.clickedLabels}", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, r.outcome!!.status)
    }

    /** Didomi dans Opera : « Enregistrer vos préférences et fermer » sous la liste, hors du bloc des choix. */
    @Test
    fun `Didomi - le panneau est elargi jusqu'au bouton Enregistrer du pied`() {
        Tree.reset()
        val w = Tree.window(node("android.webkit.WebView", children = listOf(
            node(viewId = "didomi-popup", children = listOf(
                node(children = listOf(
                    text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                    Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = false)),
                )),
                node(children = listOf(button("Enregistrer vos préférences et fermer"))),
            )),
        )))
        val a = ScreenAnalyzer(rules).analyze(w, webContentOnly = true)
        assertTrue(a.buttons.toString(), a.buttons(ButtonKind.CONFIRM).any { it.target != null })
    }
}
