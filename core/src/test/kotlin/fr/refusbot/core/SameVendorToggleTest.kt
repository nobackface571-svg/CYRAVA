package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Régression ciblée : un même fournisseur peut exposer plusieurs interrupteurs indépendants.
 * Le premier peut déjà être OFF alors que le second est encore ON.
 */
class SameVendorToggleTest {
    private val clock = FakeClock()
    private val engine = RefusalEngine(TestRules.rules) { clock.now }

    @Test
    fun `decocher uniquement la seconde case active chez un meme fournisseur`() {
        class OneVendor : FakeApp() {
            var consent = false
            var legitimateInterest = true
            var options = false
            var saved = false
            var accepted = false

            override fun build(): SnapshotNode = when {
                saved -> window(text("Accueil"), button("Jouer"))
                !options -> window(
                    text("Nous utilisons des cookies avec nos partenaires."),
                    text("Vos données personnelles et votre consentement."),
                    button("Accepter tout").onClick { accepted = true },
                    button("Gérer les options").onClick { options = true },
                )
                else -> window(
                    text("Préférences de confidentialité"),
                    text("Fournisseurs et finalités"),
                    group(
                        text("Fournisseur Exemple"),
                        switch("Consentement", consent).onClick { consent = !consent },
                        switch("Intérêt légitime", legitimateInterest).onClick {
                            legitimateInterest = !legitimateInterest
                        },
                    ),
                    button("Enregistrer mes choix").onClick { saved = true },
                    button("Tout accepter").onClick { accepted = true },
                )
            }
        }

        val app = OneVendor()
        val result = runEngine(app, engine, clock)

        assertTrue("le moteur doit terminer", result.outcome != null)
        assertTrue("les choix doivent être enregistrés", app.saved)
        assertFalse("RefusBot ne doit jamais activer la première case", app.consent)
        assertFalse("la seconde case active doit être décochée", app.legitimateInterest)
        assertFalse("aucun bouton d'acceptation ne doit être utilisé", app.accepted)
        assertEquals("une seule case devait être décochée", 1, result.outcome!!.togglesOff)
    }
}
