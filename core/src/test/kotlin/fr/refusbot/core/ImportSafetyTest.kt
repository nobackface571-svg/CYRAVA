package fr.refusbot.core

import fr.refusbot.core.lab.LabRunner
import fr.refusbot.core.lab.LabScenarios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un fichier de règles importé ne doit JAMAIS pouvoir introduire une acceptation.
 * Chaque test fabrique un fichier « piégé » à partir des règles livrées et vérifie qu'il est refusé,
 * ou que ses réglages dangereux sont remplacés par ceux de l'appli.
 */
class ImportSafetyTest {
    private val baseline = TestRules.rules
    private val bundledJson = TestRules.file().readText()

    /** Règles livrées, modifiées par [change] (carte JSON modifiable), puis importées. */
    @Suppress("UNCHECKED_CAST")
    private fun import(change: (MutableMap<String, Any?>) -> Unit): Pair<Rules?, RulesValidator.Report> {
        fun deepCopy(v: Any?): Any? = when (v) {
            is Map<*, *> -> LinkedHashMap<String, Any?>().also { m -> v.forEach { (k, x) -> m[k as String] = deepCopy(x) } }
            is List<*> -> ArrayList<Any?>().also { l -> v.forEach { l.add(deepCopy(it)) } }
            else -> v
        }
        val root = deepCopy(MiniJson.parse(bundledJson)) as MutableMap<String, Any?>
        change(root)
        return RulesValidator.parseAndValidate(MiniJson.write(root), baseline)
    }

    @Suppress("UNCHECKED_CAST")
    private fun MutableMap<String, Any?>.list(section: String, key: String): MutableList<Any?> =
        (this[section] as MutableMap<String, Any?>)[key] as MutableList<Any?>

    private fun assertRejected(result: Pair<Rules?, RulesValidator.Report>, expected: String) {
        assertNull("le fichier aurait dû être refusé", result.first)
        assertTrue(result.second.errors.toString(), result.second.errors.any { it.contains(expected) })
    }

    @Test
    fun `les regles livrees reimportees telles quelles sont acceptees et identiques`() {
        val (rules, report) = import { }
        assertTrue(report.errors.toString(), report.isValid)
        assertEquals(baseline, rules)
    }

    @Test
    fun `garde-fou allege - les formes interdites livrees sont conservees`() {
        // Le fichier retire « akzept » du garde-fou et ajoute « Alle akzeptieren » comme validation.
        assertRejected(
            import {
                it.list("forbidden", "substrings").remove("akzept")
                it.list("buttons", "confirm").add("Alle akzeptieren")
            },
            "forme interdite « akzept »",
        )
        // Sans la formule piégée, le fichier passe, mais le garde-fou reste complet.
        val (rules, report) = import { it.list("forbidden", "substrings").remove("akzept") }
        assertTrue(report.errors.toString(), report.isValid)
        assertTrue("akzept" in rules!!.forbiddenSubstrings)
        assertFalse(SafetyGuard(rules).checkText("Alle akzeptieren").isAllowed)
    }

    @Test
    fun `tout activer ne peut pas devenir un refus global`() {
        assertRejected(import { it.list("buttons", "globalReject").add("Tout activer") }, "ressemble à une acceptation")
        assertRejected(import { it.list("buttons", "globalReject").add("Alle aktivieren") }, "ressemble à une acceptation")
        assertRejected(import { it.list("buttons", "globalReject").add("Enable all") }, "ressemble à une acceptation")
    }

    @Test
    fun `un refus ajoute doit ressembler a un refus`() {
        assertRejected(import { it.list("buttons", "refuse").add("Fermer") }, "aucun mot-clé reconnu")
        assertRejected(import { it.list("buttons", "refuse").add("J'ai compris") }, "forme interdite")
        assertRejected(import { it.list("buttons", "refuse").add("OK") }, "forme interdite")
        assertRejected(import { it.list("buttons", "refuse").add("Continuer") }, "ressemble à une acceptation")
        // Une validation connue ne peut pas devenir un refus direct de premier niveau.
        assertRejected(import { it.list("buttons", "refuse").add("Enregistrer") }, "aucun mot-clé reconnu")
    }

    @Test
    fun `aucune nouvelle exception au garde-fou`() {
        // « Accepter sans personnaliser » contient une négation mais reste une acceptation.
        assertRejected(import { it.list("buttons", "refuse").add("Accepter sans personnaliser") }, "forme interdite « accept »")
        assertRejected(import { it.list("buttons", "refuse").add("No problem, accept") }, "forme interdite « accept »")
        // Même une vraie négation nouvelle est refusée : seules les exceptions livrées sont admises.
        assertRejected(import { it.list("buttons", "refuse").add("Refuser et ne pas consentir") }, "forme interdite")
    }

    @Test
    fun `sens inverse sans mot d'acceptation - refuse`() {
        // Un mot-clé de refus ne suffit pas : ces formules veulent dire le contraire d'un refus.
        for (phrase in listOf(
            "Do not reject", "No longer oppose", "Don't reject all", "Never decline", "Stop rejecting",
            "Ne pas refuser", "Ne plus s'opposer", "Annuler le refus", "Nicht ablehnen", "No rechazar",
            "Non rifiutare", "Niet weigeren", "Withdraw objection",
        )) {
            assertRejected(import { it.list("buttons", "refuse").add(phrase) }, "inverser le sens")
            assertRejected(import { it.list("buttons", "globalReject").add(phrase) }, "inverser le sens")
        }
        // Le mot-clé doit commencer un mot : « prerejected » ne contient « reject » qu'en apparence.
        assertRejected(import { it.list("buttons", "refuse").add("Prerejected defaults") }, "aucun mot-clé reconnu")
        assertRejected(
            import { it.list("buttons", "refuse").add("Reject one two three four five six seven eight") },
            "trop longue",
        )
    }

    @Test
    fun `une formule au sens inverse ne peut jamais etre cliquee`() {
        // Même si un tel fichier était accepté, « Do not reject » ne doit pas devenir un bouton de refus.
        val (rules, _) = import { it.list("buttons", "refuse").add("Do not reject") }
        assertNull(rules)
        val screen = fr.refusbot.core.Tree.window(
            fr.refusbot.core.Tree.text("Nous utilisons des cookies avec nos partenaires."),
            fr.refusbot.core.Tree.button("Do not reject"),
        )
        val analysis = ScreenAnalyzer(baseline).analyze(screen)
        assertTrue(analysis.buttons.none { it.kind == ButtonKind.REFUSE })
    }

    @Test
    fun `options, validation, fournisseurs et retour ajoutes sont controles`() {
        assertRejected(import { it.list("buttons", "options").add("Continuer") }, "ressemble à une acceptation")
        assertRejected(import { it.list("buttons", "options").add("Jouer") }, "aucun mot-clé reconnu")
        assertRejected(import { it.list("buttons", "confirm").add("Tout valider") }, "ambigu")
        assertRejected(import { it.list("buttons", "confirm").add("Ja") }, "ressemble à une acceptation")
        assertRejected(import { it.list("buttons", "confirm").add("Paramètres") }, "aucun mot-clé reconnu")
        assertRejected(import { it.list("buttons", "vendorTabs").add("Acheter") }, "forme interdite")
        assertRejected(import { it.list("buttons", "back").add("Suivant") }, "ressemble à une acceptation")
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `des formules legitimes peuvent etre ajoutees`() {
        val (rules, report) = import {
            it.list("buttons", "refuse").add("Refuser tous les cookies optionnels")
            it.list("buttons", "refuse").add("Nur notwendige Cookies")
            it.list("buttons", "globalReject").add("Désactiver toutes les finalités")
            it.list("buttons", "options").add("Paramétrer mes choix")
            it.list("buttons", "confirm").add("Sauvegarder mes réglages")
            it.list("buttons", "vendorTabs").add("Voir la liste des partenaires publicitaires")
            it.list("buttons", "back").add("Retour aux finalités")
            (it["hints"] as MutableList<Any?>).add("traceurs publicitaires")
        }
        assertTrue(report.errors.toString(), report.isValid)
        assertNotNull(rules)
        assertTrue("refuser tous les cookies optionnels" in rules!!.refuse)
    }

    @Test
    fun `la gestion des interrupteurs du fichier est ignoree`() {
        // Fichier piégé : plus aucune classe d'interrupteur, « publicité » déclarée intouchable,
        // « off » déclaré comme état coché, seuils de détection affaiblis.
        val (rules, report) = import {
            @Suppress("UNCHECKED_CAST")
            val toggles = it["toggles"] as MutableMap<String, Any?>
            toggles["classes"] = mutableListOf<Any?>()
            toggles["alwaysActive"] = mutableListOf<Any?>("publicité", "mesure d'audience")
            toggles["keepCheckedLabels"] = mutableListOf<Any?>()
            toggles["checkedStates"] = mutableListOf<Any?>("off", "désactivé")
            toggles["uncheckedStates"] = mutableListOf<Any?>("on", "activé")
            it["minHints"] = 2L
            it["maxExtraWords"] = 4L
        }
        assertTrue(report.errors.toString(), report.isValid)
        assertTrue(report.warnings.toString(), report.warnings.any { it.contains("ignorés") })
        rules!!
        assertEquals(baseline.toggleClasses, rules.toggleClasses)
        assertEquals(baseline.alwaysActive, rules.alwaysActive)
        assertEquals(baseline.keepCheckedLabels, rules.keepCheckedLabels)
        assertEquals(baseline.checkedStates, rules.checkedStates)
        assertEquals(baseline.uncheckedStates, rules.uncheckedStates)
        assertEquals(baseline.minHints, rules.minHints)
        assertEquals(baseline.maxExtraWords, rules.maxExtraWords)
        assertEquals(true, rules.checkedFromDescription("on"))
        assertEquals(false, rules.checkedFromDescription("off"))
    }

    @Test
    fun `sans durcissement, un fichier qui modifie les interrupteurs est refuse`() {
        val tampered = baseline.copy(alwaysActive = baseline.alwaysActive + "publicite")
        val report = RulesValidator.validate(tampered, baseline)
        assertTrue(report.errors.toString(), report.errors.any { it.contains("interrupteurs") })
    }

    @Test
    fun `limites deraisonnables refusees`() {
        @Suppress("UNCHECKED_CAST")
        fun limits(root: MutableMap<String, Any?>) = root["limits"] as MutableMap<String, Any?>
        assertRejected(import { limits(it)["maxActions"] = 5_000L }, "maxActions")
        assertRejected(import { limits(it)["cooldownMs"] = 0L }, "cooldownMs")
        assertRejected(import { limits(it)["failureCooldownMs"] = 1_000L }, "failureCooldownMs")
        assertRejected(import { limits(it)["maxWaits"] = 500L }, "maxWaits")
    }

    @Test
    fun `le laboratoire donne les memes resultats avec des regles importees valides`() {
        val (rules, report) = import {
            it.list("buttons", "refuse").add("Refuser les cookies facultatifs")
            it["version"] = 99L
        }
        assertTrue(report.errors.toString(), report.isValid)
        for (scenario in LabScenarios.all) {
            val result = LabRunner.run(scenario, rules!!)
            assertFalse("${scenario.id} : acceptation !", result.accepted)
            assertTrue("${scenario.id} : ${result.outcome?.status}", result.asExpected)
        }
    }

    @Test
    fun `les formules radio de refus sont validees sans jamais autoriser`() {
        val (rules, report) = import {
            it.list("buttons", "radioRefuse").add("Bloquer ce prestataire")
        }
        assertTrue(report.errors.toString(), report.isValid)
        assertTrue("bloquer ce prestataire" in rules!!.radioRefuse)

        assertRejected(
            import { it.list("buttons", "radioRefuse").add("Autoriser ce fournisseur") },
            "forme interdite",
        )
        assertRejected(
            import { it.list("buttons", "radioRefuse").add("Continuer") },
            "ressemble à une acceptation",
        )
    }
}
