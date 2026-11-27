package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Régressions autonomes du 23/11. Ces scénarios construisent leurs écrans de test en mémoire
 * et ne dépendent d'aucun export ni fichier de diagnostic supprimé.
 */
class RegressionsIndependantesTest {
    private val rules = TestRules.rules

    @Test
    fun `lecture rapide abandonnee des qu'elle est plus lente qu'une lecture complete`() {
        val p = PartialReadPolicy()
        p.recordPartial(11_000) // aucune lecture complète mesurée : on garde
        assertTrue(p.enabled)
        p.recordFull(5_800)
        p.recordPartial(900) // plus rapide : on garde
        assertTrue(p.enabled)
        p.recordPartial(11_300) // Galaxy Defense : 11 s contre 5,8 s
        assertFalse(p.enabled)
        p.recordFull(20_000)
        p.recordPartial(100) // une fois abandonnée, elle le reste pour la session
        assertFalse(p.enabled)
    }

    /**
     * 30 fournisseurs, tous activés. La page ne met dans la capture que 4 lignes visibles et 4 lignes
     * d'avance sous l'écran ; 4 de plus apparaissent à chaque défilement (ou quand la dernière ligne est
     * amenée à l'écran). [collapsedHidden] : lignes d'avance de hauteur nulle (comme Galaxy Defense) ou non.
     */
    private class LazyList(val collapsedHidden: Boolean) : FakeApp("com.example.webapp") {
        val li = BooleanArray(30) { true }
        var offset = 0
        var loaded = 8
        var saved = false

        override fun onScroll(container: UiNode) {
            if (offset + 4 < 30) offset += 4
            loaded = minOf(30, maxOf(loaded, offset + 8))
        }

        override fun onShowOnScreen(node: UiNode) {
            loaded = minOf(30, loaded + 4)
        }

        override fun build(): SnapshotNode {
            if (saved) return Tree.window(Tree.node("android.webkit.WebView", children = listOf(Tree.text("Accueil"))))
            val rows = (0 until loaded).map { i ->
                val shown = i in offset until offset + 4
                val b = when {
                    shown -> Bounds(100, 600 + (i - offset) * 200, 980, 780 + (i - offset) * 200)
                    collapsedHidden -> Bounds(100, 2300, 980, 2300)
                    else -> Bounds(100, 1400 + (i - offset) * 200, 980, 1580 + (i - offset) * 200)
                }
                Tree.node("android.view.View", bounds = b, visible = shown, children = listOf(
                    Tree.node("android.widget.TextView", text = "Fournisseur numéro $i", bounds = b, visible = shown),
                    Tree.node("android.widget.ToggleButton", desc = "Intérêt légitime, fournisseur $i", checkable = true,
                        checked = li[i], clickable = true, visible = shown, bounds = b).onClick { li[i] = false },
                ))
            }
            return Tree.window(Tree.node("android.webkit.WebView", bounds = Bounds(0, 0, 1080, 2340), children = listOf(
                Tree.node("android.app.AlertDialog", bounds = Bounds(40, 200, 1040, 2100), children = listOf(
                    Tree.node("android.view.View", scrollable = true, bounds = Bounds(40, 300, 1040, 1800), children = listOf(
                        Tree.node("android.widget.TextView", text = "Vos choix : nos fournisseurs, cookies et intérêt légitime", bounds = Bounds(100, 320, 980, 560)),
                    ) + rows),
                    Tree.node("android.widget.Button", text = "Tout accepter", clickable = true, bounds = Bounds(80, 1900, 500, 1980))
                        .onClick { error("acceptation") },
                    Tree.node("android.widget.Button", text = "Confirmer les choix", clickable = true, bounds = Bounds(520, 1900, 1000, 1980))
                        .onClick { saved = true },
                )),
            )))
        }
    }

    @Test
    fun `liste chargee au fur et a mesure - jamais validee ni verifiee avant d'avoir tout vu`() {
        for (collapsed in listOf(false, true)) {
            val app = LazyList(collapsedHidden = collapsed)
            val clock = FakeClock()
            val engine = RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }
            val r = runEngine(app, engine, clock)
            val o = r.outcome
            val stillOn = app.li.count { it }
            val steps = r.acts.map { it.description }
            if (app.saved) assertEquals("collapsed=$collapsed : validé avec $stillOn choix encore activés ; $steps", 0, stillOn)
            if (o?.status == OutcomeStatus.OPTIONS_SUCCESS) assertEquals("« vérifié » avec $stillOn choix activés", 0, stillOn)
            // La liste doit avoir été parcourue jusqu'au bout (30 lignes chargées).
            assertEquals("collapsed=$collapsed : $steps", 30, app.loaded)
        }
    }

    @Test
    fun `liste de categories - une rangee de boutons du panneau n'est pas prise pour des onglets`() {
        Tree.reset()
        val menu = SnapshotNode(
            className = "android.view.View", supplementalDescription = "Cookie Categories", isVisibleToUser = true,
            bounds = Bounds(0, 400, 1080, 1700),
        )
        val row = Tree.node("android.view.View", children = listOf(
            Tree.button("Tout refuser"), Tree.button("Paramètres"), Tree.button("Tout autoriser"),
        ))
        menu.addChild(row)
        val root = Tree.window(Tree.node(viewId = "onetrust-pc-sdk", children = listOf(
            Tree.text("Centre de préférences : cookies et partenaires"), menu, Tree.button("Confirmer la sélection"),
        )))
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.categoryTabs.map { it.label }.toString(), a.categoryTabs.isEmpty())
    }
}
