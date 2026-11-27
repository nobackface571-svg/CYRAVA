package fr.refusbot.core

import fr.refusbot.core.lab.LabRunner
import fr.refusbot.core.lab.LabExecutionResult
import fr.refusbot.core.lab.LabScenario
import fr.refusbot.core.lab.LabScenarios
import fr.refusbot.core.lab.LabReport
import fr.refusbot.core.lab.LabTermination
import fr.refusbot.core.lab.SimulatedApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.CancellationException

class LabAndValidatorTest {
    private val rules = TestRules.rules

    // --- Laboratoire (mode simulation) ----------------------------------------------------------

    @Test
    fun `chaque scenario du laboratoire donne le resultat attendu et n'accepte jamais`() {
        for (scenario in LabScenarios.all) {
            val report = LabRunner.run(scenario, rules)
            assertFalse("${scenario.id} : acceptation !", report.accepted)
            assertTrue(
                "${scenario.id} : statut ${report.outcome?.status}, état ${report.finalState}, étapes ${report.steps}",
                report.asExpected,
            )
        }
    }

    @Test
    fun `laboratoire en mode observation - aucune action`() {
        for (scenario in LabScenarios.all) {
            val report = LabRunner.run(scenario, rules, Policy.OBSERVATION)
            assertTrue("${scenario.id} : ${report.steps}", report.steps.isEmpty())
            assertFalse(report.accepted)
        }
    }

    @Test
    fun `laboratoire - une erreur de scenario reste visible comme echec technique`() {
        val broken = LabScenario(
            id = "scenario-en-erreur",
            title = "Scénario volontairement défaillant",
            description = "Vérifie que l'erreur n'est pas ignorée.",
            expected = emptySet(),
        ) {
            throw IllegalStateException("détail interne")
        }

        val result = LabRunner.runSafely(broken, rules)

        assertTrue(result is LabExecutionResult.Failure)
        result as LabExecutionResult.Failure
        assertEquals("scenario-en-erreur", result.scenario.id)
        assertEquals("IllegalStateException", result.errorType)
    }

    @Test
    fun `laboratoire - une annulation coroutine est propagee`() {
        val cancelled = LabScenario(
            id = "scenario-annule",
            title = "Scénario annulé",
            description = "Vérifie la propagation de l'annulation.",
            expected = emptySet(),
        ) {
            throw CancellationException("annulation attendue")
        }

        assertThrows(CancellationException::class.java) {
            LabRunner.runSafely(cancelled, rules)
        }
    }

    // --- Validation des règles importées --------------------------------------------------------

    @Test
    fun `les regles livrees sont valides`() {
        val report = RulesValidator.validate(rules)
        assertTrue(report.errors.toString(), report.isValid)
    }

    private fun json(refuse: String = "\"Tout refuser\"", confirm: String = "\"Enregistrer\"", forbidden: String = "\"accept\", \"agree\", \"autoris\", \"allow\", \"consentir\"", accept: String = "\"Tout accepter\"") = """
        {"version": 2, "minHints": 2,
         "hints": ["cookies", "partenaires"],
         "buttons": {"refuse": [$refuse], "options": ["Paramètres"], "globalReject": [], "confirm": [$confirm],
                     "vendorTabs": [], "back": [], "accept": [$accept]},
         "forbidden": {"substrings": [$forbidden], "words": []},
         "toggles": {"classes": ["switch"], "alwaysActive": []}}
    """.trimIndent()

    @Test
    fun `regles importees valides acceptees`() {
        val (parsed, report) = RulesValidator.parseAndValidate(json())
        assertTrue(report.errors.toString(), report.isValid)
        assertEquals(2, parsed!!.version)
    }

    @Test
    fun `regles importees - une acceptation deguisee en refus est refusee`() {
        val (parsed, report) = RulesValidator.parseAndValidate(json(refuse = "\"Tout refuser\", \"Tout accepter et fermer\""))
        assertNull(parsed)
        assertTrue(report.errors.toString(), report.errors.any { it.contains("ressemble à une acceptation") })
    }

    @Test
    fun `regles importees - une acceptation deguisee en validation est refusee`() {
        val (parsed, report) = RulesValidator.parseAndValidate(json(confirm = "\"Enregistrer\", \"Accepter mes choix\""))
        assertNull(parsed)
        assertTrue(report.errors.toString(), report.errors.any { it.contains("forme interdite") })
    }

    @Test
    fun `regles importees - le garde-fou ne peut pas etre affaibli`() {
        val (parsed, report) = RulesValidator.parseAndValidate(json(forbidden = "\"agree\""))
        assertNull(parsed)
        assertTrue(report.errors.toString(), report.errors.any { it.contains("« accept »") })
    }

    @Test
    fun `regles importees - fichier trop volumineux ou trop imbrique refuse`() {
        val huge = json() + " ".repeat(RulesValidator.MAX_FILE_CHARS)
        val (parsed, report) = RulesValidator.parseAndValidate(huge)
        assertNull(parsed)
        assertTrue(report.errors.toString(), report.errors.any { it.contains("trop volumineux") })

        val (parsed2, report2) = RulesValidator.parseAndValidate("[".repeat(10_000))
        assertNull(parsed2)
        assertTrue(report2.errors.toString(), report2.errors.any { it.contains("imbrication trop profonde") })
    }

    @Test
    fun `regles importees - fichier illisible`() {
        val (parsed, report) = RulesValidator.parseAndValidate("{ pas du json")
        assertNull(parsed)
        assertFalse(report.isValid)
    }

    @Test
    fun `laboratoire bloque expand si le sous arbre contient une forme interdite`() {
        var expanded = false
        val app = object : SimulatedApp() {
            override fun state() = if (expanded) "déplié" else "replié"
            override fun build(): SnapshotNode = window(
                section("Accepter tous les cookies", expanded = false) { expanded = true },
            )
        }
        val target = app.render().children.single()
        val result = app.perform(fr.refusbot.core.Op.Expand(target, "Accepter tous les cookies"), SafetyGuard(rules))
        assertFalse(expanded)
        assertTrue(result.contains("BLOQUÉ"))
    }

    @Test
    fun `laboratoire - limites d iterations et de temps jamais declarees conformes`() {
        val scenario = LabScenarios.all.first().copy(expected = setOf(OutcomeStatus.OPTIONS_SUCCESS))
        for (termination in listOf(LabTermination.ITERATION_LIMIT, LabTermination.TIME_LIMIT)) {
            val report = LabReport(
                scenario = scenario,
                steps = emptyList(),
                outcome = Outcome(
                    packageName = "com.example.demo",
                    success = true,
                    method = RefusalMethod.OPTIONS,
                    togglesOff = 1,
                    actions = 1,
                    reason = null,
                    status = OutcomeStatus.OPTIONS_SUCCESS,
                ),
                finalState = "état incomplet",
                accepted = false,
                termination = termination,
            )
            assertFalse("$termination ne doit jamais être conforme", report.asExpected)
        }
    }

}
