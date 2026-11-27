package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Option « diagnostic nominatif » (26/11) : tout interrupteur dont l'état final n'est pas prouvé
 * est NOMMÉ dans le diagnostic, avec la raison (relu activé, état jamais publié, disparu…).
 * Ces libellés sont du texte d'écran : réservés au fichier de diagnostic, jamais dans l'historique.
 */
class ControlReportTest {
    private val clock = FakeClock()
    private val engine = RefusalEngine(TestRules.rules) { clock.now }

    /** Fenêtre d'options (même motif que ToggleCycleTest) : [onToggle] décrit l'effet réel du clic. */
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

    private fun issuesFor(o: Outcome, label: String) =
        o.controlIssues.filter { it.label == TextNormalizer.canonical(label) }

    @Test
    fun `tout est verifie - aucun controle signale`() {
        val app = Options(listOf(true, false))
        val o = runEngine(app, engine, clock).outcome!!
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        assertTrue(o.controlIssues.isEmpty())
    }

    @Test
    fun `reste active apres le clic - nomme, raison relu active`() {
        val app = Options(initial = listOf(true), onToggle = { _, _ -> })
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.accepted)
        assertEquals(1, o.togglesStillOn)
        val issues = issuesFor(o, "Finalité 1")
        assertEquals(o.controlIssues.toString(), 1, issues.size)
        assertEquals(ControlIssueKind.STILL_ON, issues[0].kind)
        assertTrue(issues[0].signature.isNotEmpty())
    }

    @Test
    fun `disparu apres le clic - nomme, raison non relu`() {
        // L'appli retire la ligne dès qu'on la touche : son nouvel état ne peut pas être relu.
        val app = Options(
            initial = listOf(true, true),
            onToggle = { a, i -> a.on[i] = false },
            row = { a, i -> if (i == 1 && !a.on[1]) null else switch("Finalité ${i + 1}", a.on[i]) },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.accepted)
        assertEquals(1, o.togglesUnconfirmed)
        val issues = issuesFor(o, "Finalité 2")
        assertEquals(o.controlIssues.toString(), 1, issues.size)
        assertEquals(ControlIssueKind.UNCONFIRMED, issues[0].kind)
        // L'autre interrupteur, désactivé ET relu, n'est pas signalé.
        assertTrue(issuesFor(o, "Finalité 1").isEmpty())
    }

    @Test
    fun `etat inconnu au depart - nomme, raison etat jamais publie`() {
        // Case de page web non cochable et sans description : état inconnu, jamais cliqué.
        val app = Options(
            initial = listOf(true, true),
            row = { a, i ->
                if (i == 0) node("android.widget.CheckBox", text = "Finalité 1", clickable = true)
                else switch("Finalité 2", a.on[1])
            },
        )
        val o = runEngine(app, engine, clock).outcome!!
        assertFalse(app.accepted)
        assertEquals(1, o.togglesUnknown)
        val issues = issuesFor(o, "Finalité 1")
        assertEquals(o.controlIssues.toString(), 1, issues.size)
        assertEquals(ControlIssueKind.UNKNOWN_STATE, issues[0].kind)
    }

    @Test
    fun `serialisation - controlesNonVerifies dans le json, absent quand tout est verifie`() {
        val root = window(
            text("Nous utilisons des cookies avec nos partenaires"),
            button("Tout accepter"),
        )
        val meta = DiagnosticExport.Meta(
            packageName = "com.example.jeu", appLabel = "Jeu", capturedAt = "2026-11-26T10:00:00",
            trigger = "test", reason = null, appVersion = "1.0.0", rulesVersion = 1,
            androidVersion = "14 (API 34)", device = "samsung SM-S921B",
        )
        val analysis = ScreenAnalyzer(TestRules.rules).analyze(root)
        val issues = listOf(
            ControlIssue("finalite 1", ControlIssueKind.STILL_ON, "ctx|finalite 1|Switch"),
            ControlIssue("finalite 2", ControlIssueKind.UNCONFIRMED, "ctx|finalite 2|Switch"),
        )
        val doc = MiniJson.parse(DiagnosticExport.toJson(root, meta, analysis, controlIssues = issues)) as Map<*, *>
        val list = doc["controlesNonVerifies"] as List<*>
        assertEquals(2, list.size)
        val first = list[0] as Map<*, *>
        assertEquals("finalite 1", first["libelle"])
        assertEquals("STILL_ON", first["cause"])
        assertEquals(ControlIssueKind.STILL_ON.explication, first["explication"])
        assertEquals("ctx|finalite 1|Switch", first["signature"])
        // Sans problème à signaler : la clé n'encombre pas le diagnostic.
        val clean = MiniJson.parse(DiagnosticExport.toJson(root, meta, analysis)) as Map<*, *>
        assertFalse(clean.containsKey("controlesNonVerifies"))
    }
}
