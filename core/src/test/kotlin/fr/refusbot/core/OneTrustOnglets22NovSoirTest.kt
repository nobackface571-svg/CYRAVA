package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Essais du 22/11 au soir (Opera 15:08, Chrome 15:13) : centre de préférences OneTrust « en onglets ».
 * La liste « Cookie Categories » n'affiche l'interrupteur que de la catégorie sélectionnée. Avant :
 * « aucun contrôle de préférence accessible reconnu ; validation bloquée par prudence ».
 */
class OneTrustOnglets22NovSoirTest {
    private val rules = TestRules.rules

    /** Fenêtre OneTrust simulée d'après le diagnostic Opera (Blizzard). */
    internal class OneTrust(
        private val lateRender: Boolean = false,
        private val hiddenLastTab: Boolean = false,
        /** Faux : l'onglet sous l'écran ne peut pas être amené à l'écran (audit du 22/11). */
        private val canReveal: Boolean = true,
    ) : FakeApp("com.opera.browser") {
        val categories = listOf(
            "Votre confidentialité", "Cookies essentiels", "Cookies de performance",
            "Cookies fonctionnels", "Cookies de réseaux sociaux",
        )
        /** État de l'interrupteur de chaque catégorie (null : pas d'interrupteur, « Toujours actif »). */
        val on = mutableMapOf<String, Boolean?>(
            "Cookies essentiels" to null, "Cookies de performance" to true,
            "Cookies fonctionnels" to true, "Cookies de réseaux sociaux" to true,
        )
        var selected = "Votre confidentialité"
        var rendersSinceSwitch = 0
        var confirmed = false
        var accepted = false
        var lastTabShown = !hiddenLastTab

        override fun onShowOnScreen(node: UiNode) { if (canReveal) lastTabShown = true }

        override fun build(): SnapshotNode {
            if (confirmed) return Tree.window(node("android.webkit.WebView", children = listOf(text("Accueil du site"))))
            rendersSinceSwitch++
            val pane = node(viewId = "ot-tab-desc", children = buildList {
                add(text(selected))
                add(text("Description de la catégorie et des cookies utilisés par nos partenaires"))
                val state = on[selected]
                val drawn = !lateRender || rendersSinceSwitch > 1
                if (selected == "Cookies essentiels") add(text("Toujours actif"))
                if (state != null && drawn) {
                    add(Tree.switch(selected, checked = state).onClick { on[selected] = false })
                }
            })
            val tabs = categories.map { c ->
                val visible = c != "Cookies de réseaux sociaux" || lastTabShown
                val top = 1000 + categories.indexOf(c) * 120
                SnapshotNode(
                    className = "android.view.View", text = c, isClickable = true, isSelected = c == selected,
                    isVisibleToUser = visible, bounds = if (visible) Bounds(0, top, 1050, top + 110) else Bounds(0, 2214, 1050, 2214),
                ).onClick { if (selected != c) { selected = c; rendersSinceSwitch = 0 } }
            }
            val menu = SnapshotNode(
                className = "android.view.View", supplementalDescription = "Cookie Categories",
                isScrollable = false, isVisibleToUser = true, bounds = Bounds(0, 400, 1080, 1700),
            )
            menu.addChild(tabs[0])
            menu.addChild(pane)
            tabs.drop(1).forEach(menu::addChild)
            return Tree.window(node("android.webkit.WebView", children = listOf(
                node(viewId = "onetrust-pc-sdk", children = listOf(
                    node("android.widget.TextView", text = "Centre de préférences de la confidentialité", viewId = "ot-pc-title"),
                    node(viewId = "ot-pc-content", children = listOf(menu)),
                    button("Confirmer la sélection").onClick { confirmed = true },
                    node("android.widget.Button", text = "Tout autoriser", viewId = "accept-recommended-btn-handler", clickable = true)
                        .onClick { accepted = true },
                )),
            )))
        }
    }

    private fun run(app: OneTrust): Outcome {
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
        return runEngine(app, engine, clock).outcome!!
    }

    @Test
    fun `chaque categorie est ouverte, decochee, puis la selection est confirmee`() {
        val app = OneTrust()
        val o = run(app)
        assertFalse("« Tout autoriser » ne doit jamais être cliqué", app.accepted)
        assertTrue("interrupteurs restés activés : ${app.on} / ${app.clickedLabels}", app.on.values.none { it == true })
        assertTrue("validation non cliquée : ${o.reason}", app.confirmed)
        assertEquals(o.reason.orEmpty(), OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    @Test
    fun `interrupteur dessine en retard apres le changement d'onglet - pas saute`() {
        val app = OneTrust(lateRender = true)
        val o = run(app)
        assertTrue("interrupteurs restés activés : ${app.on}", app.on.values.none { it == true })
        assertTrue(o.reason.orEmpty(), app.confirmed)
    }

    @Test
    fun `onglet sous l'ecran - amene a l'ecran puis ouvert`() {
        val app = OneTrust(hiddenLastTab = true)
        val o = run(app)
        assertTrue(app.lastTabShown)
        assertEquals(false, app.on["Cookies de réseaux sociaux"])
        assertTrue(o.reason.orEmpty(), app.confirmed)
    }

    @Test
    fun `les onglets sont reconnus sans compter comme boutons de la fenetre`() {
        val app = OneTrust()
        val a = ScreenAnalyzer(rules).analyze(app.render(), webContentOnly = true)
        assertEquals(app.categories.size, a.categoryTabs.size)
        assertTrue(a.categoryTabs.single { it.selected == true }.label.startsWith("votre"))
        assertTrue(a.buttons.none { it.key.startsWith("categorie|") })
    }
}
