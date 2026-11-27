package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Option « préférences d'abord » (26/11) : sur une plateforme INCONNUE, hors navigateur, le
 * « Tout refuser » global (dont l'effet n'est pas vérifiable) attend que les préférences — seul
 * parcours vérifiable, les interrupteurs y sont relus — aient été tentées. Si leur ouverture fait
 * perdre la fenêtre, l'appli est mémorisée et le refus direct redevient immédiat POUR ELLE.
 * Navigateurs : comportement inchangé (le refus direct reste immédiat).
 */
class PreferencesDAbordTest {
    private val rules = TestRules.rules

    /**
     * Fenêtre d'une plateforme inconnue (même texte que le cas « Refuser seul » de
     * CompleteRefusalTest), mais avec un refus GLOBAL « Tout refuser ».
     * [onOptions] décrit ce que fait vraiment l'appli quand « Paramétrer » est cliqué.
     */
    private class NoticeApp(
        packageName: String = "com.example.jeu",
        val onOptions: (NoticeApp) -> Unit = {},
    ) : FakeApp(packageName) {
        var open = true
        var accepted = false
        override fun build(): SnapshotNode = if (open) {
            window(
                text("Nos partenaires et nous utilisons des cookies pour la publicité personnalisée"),
                button("Tout refuser").onClick { open = false },
                button("Paramétrer").onClick { onOptions(this) },
                button("Tout accepter").onClick { accepted = true },
            )
        } else {
            window(text("Accueil"), button("Jouer"))
        }
    }

    private fun label(st: Step) = (st as? Step.Act)?.description ?: st.toString()

    @Test
    fun `hors navigateur - les preferences passent avant le Tout refuser global`() {
        val app = NoticeApp()
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }
        assertTrue(label(engine.next(Screen(app.packageName, app.render()))).startsWith("options"))
    }

    @Test
    fun `navigateur - le refus direct reste immediat (comportement inchange)`() {
        val app = NoticeApp(packageName = "com.opera.browser")
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }
        assertEquals("refus : « tout refuser »", label(engine.next(Screen(app.packageName, app.render()))))
    }

    @Test
    fun `fenetre perdue en ouvrant les preferences - appli memorisee, refus direct ensuite`() {
        // L'ouverture des « préférences » fait disparaître la fenêtre sans validation (appli qui
        // renvoie vers un réglage système, ouvre une page externe…).
        val app = NoticeApp(onOptions = { it.open = false })
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }

        val first = runEngine(app, engine, clock)
        assertNotNull(first.outcome)
        assertEquals(OutcomeStatus.INTERRUPTED, first.outcome!!.status)
        assertFalse(app.accepted)
        // « Paramétrer » a été tenté, jamais « Tout refuser » ni « Tout accepter ».
        assertEquals(listOf(TextNormalizer.canonical("Paramétrer")), app.clickedLabels)

        // La mémoire survit à la recréation du moteur (changement de règles, adoptCooldowns).
        // On vérifie ce transfert AVANT le refus direct réussi : celui-ci doit ensuite conserver
        // son délai anti-répétition, y compris après une nouvelle recréation du moteur.
        app.open = true
        val fresh = RefusalEngine(rules) { clock.now }.apply {
            completeRefusal = true
            adoptCooldowns(engine)
        }
        val second = runEngine(app, fresh, clock)
        val o = second.outcome
        assertNotNull(o)
        assertEquals(RefusalMethod.DIRECT, o!!.method)
        assertEquals(OutcomeStatus.UNVERIFIED, o.status)
        assertFalse(app.accepted)
        assertEquals(
            listOf(TextNormalizer.canonical("Paramétrer"), TextNormalizer.canonical("Tout refuser")),
            app.clickedLabels,
        )

        // Après ce refus réussi, le délai anti-répétition doit lui aussi survivre au redémarrage du moteur.
        val afterSuccess = RefusalEngine(rules) { clock.now }.apply {
            completeRefusal = true
            adoptCooldowns(fresh)
        }
        assertEquals("Idle", label(afterSuccess.next(Screen(app.packageName, app.render()))))
    }

    @Test
    fun `navigateur - fenetre perdue en ouvrant les options, jamais memorise`() {
        // Chez un navigateur, perdre la fenêtre en ouvrant les options est connu (Opera, 21/11) :
        // le refus direct y était déjà immédiat et le reste, sans mémoire particulière.
        val app = NoticeApp(packageName = "com.opera.browser", onOptions = { it.open = false })
        val clock = FakeClock()
        val engine = RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }
        val r = runEngine(app, engine, clock)
        assertNotNull(r.outcome)
        // Le refus direct a été utilisé tout de suite : « Paramétrer » n'a jamais été cliqué.
        assertEquals(listOf(TextNormalizer.canonical("Tout refuser")), app.clickedLabels)
        assertFalse(app.accepted)
    }
}
