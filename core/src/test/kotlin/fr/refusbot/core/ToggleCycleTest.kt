package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cycle complet d'un interrupteur : état initial → clic → NOUVELLE lecture → état final.
 * Un clic n'est jamais une preuve : seul un interrupteur relu désactivé compte comme désactivé.
 */
class ToggleCycleTest {
    private val clock = FakeClock()
    private val engine = RefusalEngine(TestRules.rules) { clock.now }

    /** Interrupteur d'état publié seulement par sa description (WebView). */
    private fun described(label: String, state: String) = SnapshotNode(
        className = "android.widget.ToggleButton", text = label, isClickable = true, isCheckable = false,
        isChecked = false, stateDescription = state,
    )

    /**
     * Fenêtre d'options avec des interrupteurs ; [render] construit les lignes selon l'état courant,
     * [onToggle] décrit ce que fait vraiment un clic sur l'interrupteur n°i.
     */
    private open inner class Options(
        val initial: List<Boolean>,
        val onToggle: (Options, Int) -> Unit = { app, i -> app.on[i] = false },
        val row: (Options, Int) -> SnapshotNode? = { app, i -> switch("Finalité ${i + 1}", app.on[i]) },
    ) : FakeApp() {
        val on = initial.toMutableList()
        var options = false
        var saved = false
        var accepted = false
        override fun build(): SnapshotNode = when {
            saved -> window(text("Accueil"), button("Jouer"))
            !options -> window(
                text("Nous utilisons des cookies avec nos partenaires."), text("Vos données"),
                button("Tout accepter").onClick { accepted = true },
                button("Gérer les options").onClick { options = true },
            )
            else -> window(
                text("Intérêt légitime"), text("Fournisseurs"),
                *initial.indices.mapNotNull { i ->
                    row(this, i)?.let { group(text("Ligne ${i + 1}"), it.onClick { onToggle(this, i) }) }
                }.toTypedArray(),
                button("Confirmer les choix").onClick { saved = true },
            )
        }
    }

    @Test
    fun `compte rendu complet - activés, déjà désactivés, désactivés et relus`() {
        val app = Options(listOf(true, true, false, true))
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.accepted)
        assertEquals(listOf(false, false, false, false), app.on)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals("détectés", 4, o.togglesSeen)
        assertEquals("activés au départ", 3, o.togglesInitiallyOn)
        assertEquals("déjà désactivés", 1, o.togglesAlreadyOff)
        assertEquals("cliqués", 3, o.togglesAttempted)
        assertEquals("désactivés ET relus", 3, o.togglesOff)
        assertEquals(0, o.togglesUnconfirmed)
        assertEquals(0, o.togglesStillOn)
        assertEquals(
            "4 interrupteur(s) détecté(s) · 3 activé(s) au départ · 1 déjà désactivé(s) · 3 désactivé(s) et vérifié(s) sur 3 cliqué(s)",
            ToggleReport.of(o).describe(),
        )
    }

    @Test
    fun `etat illisible apres le clic - jamais compte comme desactive`() {
        // Après le clic, l'appli ne publie plus qu'un état ambigu : rien ne prouve la désactivation.
        val app = Options(
            initial = listOf(true, true),
            onToggle = { a, i -> a.on[i] = false },
            row = { a, i -> if (i == 0 && !a.on[0]) described("Finalité 1", "variable") else switch("Finalité ${i + 1}", a.on[i]) },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertNotEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertEquals("seul l'interrupteur relu désactivé compte", 1, o.togglesOff)
        assertEquals(2, o.togglesAttempted)
        assertTrue(o.togglesUnknown >= 1)
    }

    @Test
    fun `interrupteur disparu apres le clic - non confirme, refus partiel`() {
        // L'appli retire la ligne dès qu'on la touche : son nouvel état ne peut pas être relu.
        val app = Options(
            initial = listOf(true, true),
            onToggle = { a, i -> a.on[i] = false },
            row = { a, i -> if (i == 1 && !a.on[1]) null else switch("Finalité ${i + 1}", a.on[i]) },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertEquals(1, o.togglesOff)
        assertEquals(1, o.togglesUnconfirmed)
        assertTrue(o.reason!!, o.reason.contains("n'a pas pu être relu"))
        assertTrue(ToggleReport.of(o).describe().contains("1 cliqué(s) sans relecture possible"))
    }

    @Test
    fun `clic sans effet - reste active, jamais compte comme desactive`() {
        val app = Options(initial = listOf(true), onToggle = { _, _ -> })
        val o = runEngine(app, engine, clock).outcome!!
        assertTrue(app.on[0])
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertEquals(0, o.togglesOff)
        assertEquals(1, o.togglesStillOn)
    }

    @Test
    fun `etat inconnu au depart - jamais clique`() {
        // Case de page web non cochable et sans description : état inconnu.
        val app = Options(
            initial = listOf(true, true),
            row = { a, i ->
                if (i == 0) node("android.widget.CheckBox", text = "Finalité 1", clickable = true)
                else switch("Finalité 2", a.on[1])
            },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertTrue("la case d'état inconnu n'a jamais été touchée", app.on[0])
        assertEquals(1, o.togglesAttempted)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
        assertEquals(1, o.togglesUnknown)
    }

    @Test
    fun `interrupteur reactive par l'appli apres le clic - jamais desactive`() {
        // L'appli remet l'interrupteur à ON : le moteur réessaie au plus deux fois puis s'arrête.
        val app = Options(initial = listOf(true), onToggle = { a, i -> a.on[i] = true })
        val o = runEngine(app, engine, clock).outcome!!
        assertEquals(0, o.togglesOff)
        assertEquals(OutcomeStatus.PARTIAL, o.status)
    }

    @Test
    fun `ne pas valider dans le doute - fenetre laissee ouverte, aucune validation`() {
        engine.holdWhenUnverifiable = true
        val app = Options(
            initial = listOf(true, true),
            row = { a, i ->
                if (i == 0) node("android.widget.CheckBox", text = "Finalité 1", clickable = true)
                else switch("Finalité 2", a.on[1])
            },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse("la validation n'a pas été cliquée", app.saved)
        assertFalse(app.accepted)
        assertTrue("la case d'état inconnu n'a pas été touchée", app.on[0])
        assertFalse("l'interrupteur lisible a quand même été désactivé", app.on[1])
        assertEquals(OutcomeStatus.HANDED_OVER, o.status)
        assertFalse(o.status.isRefusal)
        assertEquals(1, o.togglesOff)
        assertTrue(o.reason!!, o.reason.contains("laissée à l'utilisateur"))
    }

    @Test
    fun `ne pas valider dans le doute - aucun doute, validation normale`() {
        engine.holdWhenUnverifiable = true
        val app = Options(listOf(true, false))
        val o = runEngine(app, engine, clock).outcome!!
        assertTrue(app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }
}
