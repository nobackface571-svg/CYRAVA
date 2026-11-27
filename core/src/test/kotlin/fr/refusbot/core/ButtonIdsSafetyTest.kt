package fr.refusbot.core

import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Identifiants de boutons importés (Autoconsent, Consent-O-Matic) par
 * scripts/cmp-library/import_rules.py : aucun ne doit pouvoir mener à une acceptation.
 */
class ButtonIdsSafetyTest {
    private val rules = TestRules.rules
    private val acceptLike = Regex("accept|(?<!dis)agree|allow|approv|consent-?yes|gotit|got-it|okay|enable|opt-?in", RegexOption.IGNORE_CASE)
    private val toggleLike = Regex("checkbox|chk|toggle|switch|slide|radio", RegexOption.IGNORE_CASE)
    private val toggleExceptions = setOf("btn-toggle-disagree", "btn-toggle-save") // identifiants Didomi vérifiés à la main

    @Test
    fun `aucune categorie d'acceptation et identifiants presents`() {
        assertTrue(ButtonKind.ACCEPT !in rules.buttonIds.keys)
        assertTrue(ButtonKind.RADIO_REFUSE !in rules.buttonIds.keys)
        assertTrue("au moins 40 refus attendus", (rules.buttonIds[ButtonKind.REFUSE]?.size ?: 0) >= 40)
        assertTrue("btn-toggle-disagree" in rules.buttonIds[ButtonKind.REFUSE].orEmpty())
        assertTrue("tarteaucitronalldenied2" in rules.buttonIds[ButtonKind.REFUSE].orEmpty())
    }

    @Test
    fun `aucun identifiant n'evoque une acceptation ou un interrupteur`() {
        for ((kind, ids) in rules.buttonIds) for (id in ids) {
            assertTrue("$kind : « $id » évoque une acceptation", !acceptLike.containsMatchIn(id))
            if (id !in toggleExceptions) assertTrue("$kind : « $id » évoque un interrupteur", !toggleLike.containsMatchIn(id))
            assertEquals("$kind : « $id » doit être en minuscules", id.lowercase(), id)
        }
    }

    @Test
    fun `un identifiant n'appartient qu'a une seule categorie`() {
        val all = rules.buttonIds.flatMap { (kind, ids) -> ids.map { it to kind } }
        val dup = all.groupBy({ it.first }, { it.second }).filterValues { it.toSet().size > 1 }
        assertTrue("identifiants ambigus : $dup", dup.isEmpty())
    }

    @Test
    fun `tarteaucitron - le bouton d'acceptation voisin n'est jamais reconnu comme refus`() {
        // « tarteaucitronPersonalize2 » est le « Tout accepter » du bandeau tarteaucitron.
        val ids = rules.buttonIds.values.flatten().toSet()
        assertTrue("tarteaucitronpersonalize2" !in ids)
    }

    @Test
    fun `identifiant de refus hors fenetre de consentement - aucun clic`() {
        class Shop : FakeApp("com.android.chrome") {
            override fun build(): SnapshotNode = window(
                text("Bienvenue sur notre boutique"),
                text("Découvrez nos nouveautés de la semaine."),
                node("android.widget.Button", text = "Voir les offres", clickable = true, viewId = "reject-all"),
            )
        }
        val app = Shop()
        val analysis = ScreenAnalyzer(rules).analyze(app.render())
        assertTrue("une page ordinaire n'est pas une fenêtre de consentement", !analysis.isConsent)
        val clock = FakeClock()
        runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertTrue("aucun clic sur une page ordinaire : ${app.clickedLabels}", app.clickedLabels.isEmpty())
    }

    @Test
    fun `identifiant d'un fichier de regles importe ignore`() {
        // Un fichier importé ne peut ni ajouter ni retirer d'identifiants : ceux de l'appli s'appliquent.
        val imported = rules.copy(buttonIds = mapOf(ButtonKind.REFUSE to listOf("accept-all-button")))
        val hardened = RulesValidator.harden(imported, rules)
        assertEquals(rules.buttonIds, hardened.buttonIds)
    }
}
