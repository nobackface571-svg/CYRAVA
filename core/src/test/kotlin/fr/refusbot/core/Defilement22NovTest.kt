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
 * Défilement continu « façon TikTok / Webtoon » (22/11) : une très longue liste se fait défiler écran
 * après écran, en décochant à chaque fois, jusqu'en bas, sans s'arrêter sur un plafond fixe.
 */
class Defilement22NovTest {
    private val rules = TestRules.rules

    /**
     * Liste native (RecyclerView) : seules les lignes à l'écran existent dans l'arbre, comme sur un vrai
     * téléphone. Chaque défilement avance d'un écran de [perScreen] lignes.
     */
    private class LongNativeList(val total: Int, val perScreen: Int = 8) : FakeApp("com.example.jeu") {
        val on = BooleanArray(total) { true }
        var offset = 0
        var saved = false
        var scrolls = 0

        override fun onScroll(container: UiNode) {
            scrolls++
            offset = minOf(offset + perScreen, total - perScreen)
        }

        override fun build(): SnapshotNode = if (saved) {
            Tree.window(text("Accueil"), button("Jouer"))
        } else {
            Tree.window(
                node(viewId = "didomi-popup", children = listOf(
                    text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                    Tree.group(
                        *(offset until offset + perScreen).map { i ->
                            Tree.group(text("Partenaire $i"), Tree.switch("Partenaire $i", checked = on[i]).onClick { on[i] = false })
                        }.toTypedArray(),
                        cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true,
                    ),
                    button("Enregistrer").onClick { saved = true },
                )),
            )
        }
    }

    /** Avant le 22/11 : 50 défilements par page au plus, la liste s'arrêtait vers la 400e ligne. */
    @Test
    fun `liste de 600 partenaires - defilement jusqu'en bas et tout decoche`() {
        val app = LongNativeList(total = 600)
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock, maxSteps = 5_000)
        val outcome = checkNotNull(r.outcome) { "Le moteur doit produire un résultat pour la liste de 600 partenaires" }
        val stillOn = app.on.indices.filter { app.on[it] }
        assertTrue("restés activés : ${stillOn.take(10)}…", stillOn.isEmpty())
        assertTrue(app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, outcome.status)
        assertEquals(600, outcome.togglesOff)
        assertTrue(app.clickedLabels.none { it.contains("accepter") })
    }

    /**
     * Garde-fou : une liste qui « bouge » sans jamais livrer de nouveau choix (bandeau animé, compteur)
     * ne fait pas défiler à l'infini. Le défilement s'arrête et le résultat n'est jamais « vérifié ».
     */
    @Test
    fun `defilement sans nouveau choix - arret rapide, jamais verifie`() {
        val app = object : FakeApp("com.example.jeu") {
            var n = 0
            var on = true
            var saved = false
            override fun onScroll(container: UiNode) { n++ }
            override fun build(): SnapshotNode = if (saved) {
                Tree.window(text("Accueil"))
            } else {
                Tree.window(node(viewId = "didomi-popup", children = listOf(
                    text("Vos choix pour nos partenaires : consentement et intérêt légitime"),
                    Tree.group(
                        Tree.group(text("Publicité"), Tree.switch("Publicité", checked = on).onClick { on = false }),
                        text("Mis à jour il y a $n s"),
                        cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true,
                    ),
                    button("Enregistrer").onClick { saved = true },
                )))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock, maxSteps = 5_000)
        val outcome = checkNotNull(r.outcome) { "Le moteur doit produire un résultat pour le test de défilement sans nouveau choix" }
        assertFalse(app.on)
        assertTrue("trop de défilements inutiles : ${app.n}", app.n <= 30)
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, outcome.status)
    }
}
