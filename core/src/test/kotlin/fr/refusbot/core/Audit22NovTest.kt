package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Audit en lecture seule du 22/11 (après les pull requests 11 à 15), un test par constat.
 * N° 1 : jamais d'« Enregistrer » quand une partie des choix n'a pas été vue, MÊME si le réglage
 * « Ne pas valider dans le doute » est désactivé. N° 3 : jamais de troisième clic sur un interrupteur.
 */
class Audit22NovTest {
    private val rules = TestRules.rules

    private fun engine(clock: FakeClock, complete: Boolean = false) =
        RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = false; completeRefusal = complete }

    @Test
    fun `n1 - OneTrust, categorie impossible a ouvrir, reglage desactive - jamais de validation`() {
        val app = OneTrustOnglets22NovSoirTest.OneTrust(hiddenLastTab = true, canReveal = false)
        val clock = FakeClock()
        val o = runEngine(app, engine(clock), clock).outcome!!
        assertFalse("« Confirmer la sélection » cliqué alors qu'une catégorie n'a pas été vue : ${o.reason}", app.confirmed)
        assertFalse(app.accepted)
        assertFalse(o.success)
        assertTrue(o.reason.orEmpty(), o.reason.orEmpty().contains("non ouverte"))
    }

    /** Liste qui bouge (compteur animé) sans jamais livrer de nouveau choix : jamais lue jusqu'en bas. */
    private inner class EndlessList(private val withRefuseAll: Boolean) : FakeApp("com.android.chrome") {
        val on = booleanArrayOf(true, true)
        var tick = 0
        var saved = false
        var refusedAll = false
        override fun onScroll(container: UiNode) { tick++ }
        override fun build(): SnapshotNode = if (saved || refusedAll) {
            Tree.window(node("android.webkit.WebView", children = listOf(text("Article"))))
        } else {
            Tree.window(node("android.webkit.WebView", children = listOf(
                node(viewId = "didomi-popup", children = buildList {
                    add(text("Vos choix pour nos partenaires : consentement et intérêt légitime, cookies"))
                    add(node(scrollable = true, children = listOf(
                        text("Chargement des partenaires… $tick"),
                        Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = on[0]).onClick { on[0] = false }),
                        Tree.group(text("Publicité ciblée"), Tree.switch("Publicité ciblée", checked = on[1]).onClick { on[1] = false }),
                    )))
                    if (withRefuseAll) add(button("Tout refuser").onClick { refusedAll = true })
                    add(button("Enregistrer vos préférences et fermer").onClick { saved = true })
                }),
            )))
        }
    }

    @Test
    fun `n1 - liste jamais lue jusqu'en bas, reglage desactive - pas de validation, Tout refuser du site a la place`() {
        val app = EndlessList(withRefuseAll = true)
        val clock = FakeClock()
        val o = runEngine(app, engine(clock, complete = true), clock).outcome!!
        assertFalse("validation alors que la liste n'a pas été lue jusqu'en bas : ${o.reason}", app.saved)
        assertTrue("le « Tout refuser » du site devait servir de repli : ${o.reason}", app.refusedAll)
        assertNotEquals("jamais « vérifié »", OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    @Test
    fun `n1 - liste jamais lue jusqu'en bas, sans Tout refuser - aucun clic de validation`() {
        val app = EndlessList(withRefuseAll = false)
        val clock = FakeClock()
        val o = runEngine(app, engine(clock, complete = true), clock).outcome!!
        assertFalse("validation alors que la liste n'a pas été lue jusqu'en bas : ${o.reason}", app.saved)
        assertFalse(o.success)
    }

    /** Site qui ignore TOUS les clics : au plus deux clics par interrupteur (hors écran, puis visible), jamais trois. */
    @Test
    fun `n3 - jamais de troisieme clic sur un interrupteur`() {
        val n = 24
        val app = object : FakeApp("com.android.chrome") {
            var offset = 0
            val clicks = IntArray(n)
            var saved = false
            override fun onScroll(container: UiNode) { offset = minOf(offset + 8, n - 8) }
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
                                Tree.switch("Intérêt légitime", checked = true, visible = vis).onClick { clicks[i]++ },
                            )
                        }),
                        button("Confirmer les choix").onClick { saved = true },
                    )),
                )))
            }
        }
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true }, clock).outcome!!
        assertTrue("au plus deux clics par interrupteur : ${app.clicks.joinToString()}", app.clicks.all { it <= 2 })
        assertFalse("interrupteurs restés activés : jamais d'enregistrement (${o.reason})", app.saved)
        assertFalse(o.success)
    }
}
