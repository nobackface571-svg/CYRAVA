package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Une capture tronquée (fenêtre trop grande pour être lue en entier) n'autorise qu'un refus DIRECT
 * visible ; jamais d'options, d'interrupteurs ni de validation, et jamais un refus « vérifié ».
 */
class TruncatedSnapshotTest {
    @Test
    fun `navigation bloquee est non resolue et non un refus`() {
        val engine = RefusalEngine(TestRules.rules) { 1_000L }
        val root = window(text("Nous utilisons des cookies avec nos partenaires."), text("Vos données personnelles"), button("Gérer les options"), button("Tout accepter"))
        val first = engine.next(Screen("fr.refusbot.demo", root))
        assertTrue(first is Step.Act)
        engine.markNavigationBlocked()
        val changedRoot = window(text("Nous utilisons des cookies avec nos partenaires."), text("Vos données personnelles et options."), button("Gérer les options"), button("Tout accepter"))
        val done = engine.next(Screen("fr.refusbot.demo", changedRoot))
        assertTrue(done is Step.Done)
        val outcome = (done as Step.Done).outcome
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, outcome.status)
        assertFalse(outcome.status.isRefusal)
    }

    @Test
    fun `capture tronquee est non resolue sans action ni faux refus partiel`() {
        val engine = RefusalEngine(TestRules.rules) { 1_000L }
        // Pas de refus direct : seules les options permettraient de refuser, interdites ici.
        val root = window(
            text("Nous utilisons des cookies avec nos partenaires."),
            text("Vos données personnelles et votre consentement."),
            button("Gérer les options"),
            button("Tout accepter"),
        )

        val step = engine.next(Screen("fr.refusbot.demo", root, isTruncated = true))

        assertTrue("le moteur doit terminer immédiatement", step is Step.Done)
        val outcome = (step as Step.Done).outcome
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, outcome.status)
        assertFalse("une capture incomplète ne constitue pas un refus", outcome.status.isRefusal)
        assertFalse("une capture incomplète ne peut pas être un succès", outcome.success)
        assertEquals("aucune action ne doit être exécutée", 0, outcome.actions)
        assertTrue(outcome.reason.orEmpty().contains("capture incomplète"))
        assertFalse("aucune session ne doit rester active", engine.isActive)
    }

    private class BigBanner : FakeApp() {
        var refused = false
        var accepted = false
        var refuseWorks = true
        override fun build(): SnapshotNode = if (refused) {
            window(text("Accueil"), button("Jouer"))
        } else {
            window(
                text("Nous utilisons des cookies avec nos partenaires."),
                text("Vos données personnelles et votre consentement."),
                button("Tout accepter").onClick { accepted = true },
                button("Tout refuser").onClick { if (refuseWorks) refused = true },
            )
        }
    }

    /** La fenêtre de consentement est trop grande (tronquée) ; l'écran du jeu ensuite aussi. */
    private fun runTruncated(app: BigBanner): Pair<Outcome, List<Step.Act>> {
        var now = 1_000L
        val engine = RefusalEngine(TestRules.rules) { now }
        val acts = ArrayList<Step.Act>()
        repeat(50) {
            when (val step = engine.next(Screen(app.packageName, app.render(), isTruncated = true))) {
                Step.Idle -> error("le moteur ne doit pas rester inactif")
                is Step.Wait -> now += step.delayMs
                is Step.Act -> {
                    acts.add(step)
                    step.ops.forEach(app::perform)
                    now += 300
                }
                is Step.Done -> return step.outcome to acts
            }
        }
        error("le moteur ne s'est pas arrêté")
    }

    @Test
    fun `grande fenetre - le refus direct visible est clique, resultat non verifiable`() {
        val app = BigBanner()
        val (outcome, acts) = runTruncated(app)
        assertTrue(app.refused)
        assertFalse("jamais d'acceptation", app.accepted)
        assertEquals(1, acts.size)
        assertTrue(acts.single().ops.all { it is Op.Click && !it.isToggle })
        // Fenêtre lue en partie : le refus ne peut pas être annoncé comme vérifié.
        assertEquals(OutcomeStatus.UNVERIFIED, outcome.status)
        assertFalse(outcome.status.isVerified)
    }

    @Test
    fun `grande fenetre - refus sans effet, aucune autre tentative`() {
        val app = BigBanner().apply { refuseWorks = false }
        val (outcome, acts) = runTruncated(app)
        assertFalse(app.accepted)
        assertEquals("un seul clic, jamais de nouvelle tentative sur une fenêtre lue en partie", 1, acts.size)
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, outcome.status)
        assertFalse(outcome.status.isRefusal)
    }

    @Test
    fun `grande fenetre - profil prudent sans certitude, aucun clic`() {
        val engine = RefusalEngine(TestRules.rules) { 1_000L }
        // Peu d'indices : reconnaissance « probable », donc aucune action en profil Prudent.
        val root = window(text("Nous utilisons des cookies avec nos partenaires."), button("Tout refuser"), button("Tout accepter"))
        val step = engine.next(Screen("com.example.jeu", root, isTruncated = true)) { Policy.PRUDENT }
        assertTrue(step is Step.Done)
        assertEquals(OutcomeStatus.OBSERVED, (step as Step.Done).outcome.status)
        assertEquals(0, step.outcome.actions)
    }
}
