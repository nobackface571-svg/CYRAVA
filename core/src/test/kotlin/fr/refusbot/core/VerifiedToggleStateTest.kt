package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Régressions : un interrupteur cliqué n'est compté OFF qu'après relecture explicite. */
class VerifiedToggleStateTest {
    private val clock = FakeClock()
    private val engine = RefusalEngine(TestRules.rules) { clock.now }

    private class OptionsApp(
        private val disappearsAfterClick: Boolean,
    ) : FakeApp() {
        var optionsOpened = false
        var enabled = true
        var removed = false
        var saved = false

        override fun build() = when {
            saved -> window(text("Accueil"))
            !optionsOpened -> window(
                Tree.node(
                    cls = "android.view.View",
                    viewId = "didomi-notice",
                    children = listOf(
                        text("Nous utilisons des cookies avec nos partenaires."),
                        button("Tout accepter"),
                        button("Gérer les options").onClick { optionsOpened = true },
                    ),
                ),
            )
            else -> window(
                Tree.node(
                    cls = "android.view.View",
                    viewId = "didomi-notice",
                    children = buildList {
                        add(text("Cookies et partenaires"))
                        add(text("Intérêt légitime"))
                        if (!removed) {
                            add(switch("Personnalisation", enabled).onClick {
                                enabled = false
                                if (disappearsAfterClick) removed = true
                            })
                        }
                        add(button("Enregistrer").onClick { saved = true })
                    },
                ),
            )
        }
    }

    @Test
    fun `interrupteur relu OFF apres clic est compte`() {
        val app = OptionsApp(disappearsAfterClick = false)
        val outcome = runEngine(app, engine, clock).outcome!!

        assertFalse(app.enabled)
        assertTrue(app.saved)
        assertEquals(1, outcome.togglesOff)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, outcome.status)
        assertEquals(true, outcome.stateVerified)
    }

    @Test
    fun `interrupteur disparu apres clic reste non confirme`() {
        val app = OptionsApp(disappearsAfterClick = true)
        val outcome = runEngine(app, engine, clock).outcome!!

        assertFalse(app.enabled)
        assertTrue(app.saved)
        assertEquals(0, outcome.togglesOff)
        assertEquals(OutcomeStatus.PARTIAL, outcome.status)
        assertEquals(false, outcome.stateVerified)
        assertTrue(outcome.reason.orEmpty().contains("n'a pas pu être relu"))
    }
}
