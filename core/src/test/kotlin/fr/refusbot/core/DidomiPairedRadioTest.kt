package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Écran de réglages Didomi en boutons radio « Refuser X » / « Accepter X »
 * (diagnostic Chrome / Oodrive Sign du 18/11/2026).
 */
class DidomiPairedRadioTest {
    private val rules = TestRules.rules
    private val clock = FakeClock()
    private val engine = RefusalEngine(rules) { clock.now }

    private fun radio(label: String, checked: Boolean) =
        node("android.widget.RadioButton", desc = label, checkable = true, checked = checked, clickable = true)

    private fun pair(purpose: String, refused: Boolean) = node(
        "android.widget.RadioGroup",
        children = listOf(radio("Refuser $purpose", refused), radio("Accepter $purpose", !refused)),
    )

    @Test fun `une paire Refuser X Accepter X est reconnue comme choix de refus`() {
        val root = window(
            text("Politique de confidentialité"),
            text("Nous et nos partenaires traitons vos données personnelles et votre consentement."),
            pair("Mesurer la performance des publicités", refused = false),
            pair("Comprendre les publics", refused = false),
            button("Enregistrer vos préférences et fermer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        val radios = a.buttons(ButtonKind.RADIO_REFUSE)
        assertEquals(2, radios.size)
        assertTrue(radios.all { TextNormalizer.canonical(it.label).startsWith("refuser ") })
        assertTrue("« Accepter … » n'est jamais un choix de refus",
            radios.none { TextNormalizer.canonical(it.label).startsWith("accepter") })
    }

    @Test fun `meme libelle pour consentement et interet legitime donne deux choix distincts`() {
        val root = window(
            text("Politique de confidentialité"),
            text("Nous et nos partenaires traitons vos données personnelles et votre consentement."),
            text("Consentement"),
            pair("Comprendre les publics", refused = false),
            text("Intérêt légitime"),
            pair("Comprendre les publics", refused = false),
            button("Enregistrer vos préférences et fermer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertEquals(2, a.buttons(ButtonKind.RADIO_REFUSE).map { it.labelNode.key }.distinct().size)
    }

    @Test fun `suites differentes ou inversion ne forment pas une paire`() {
        val root = window(
            text("Politique de confidentialité"),
            text("Nous et nos partenaires traitons vos données personnelles et votre consentement."),
            node("android.widget.RadioGroup", children = listOf(
                radio("Refuser Mesurer la performance", false), radio("Accepter Comprendre les publics", true))),
            node("android.widget.RadioGroup", children = listOf(
                radio("Refuser de ne pas partager", false), radio("Accepter de ne pas partager", true))),
            button("Enregistrer vos préférences et fermer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.buttons(ButtonKind.RADIO_REFUSE).isEmpty())
    }

    @Test fun `un groupe de trois radios ne forme pas une paire`() {
        val root = window(
            text("Politique de confidentialité"),
            text("Nous et nos partenaires traitons vos données personnelles et votre consentement."),
            node("android.widget.RadioGroup", children = listOf(
                radio("Refuser Comprendre les publics", false),
                radio("Accepter Comprendre les publics", true),
                radio("Accepter Comprendre les publics et plus", false))),
            button("Enregistrer vos préférences et fermer"),
        )
        val a = ScreenAnalyzer(rules).analyze(root)
        assertTrue(a.buttons(ButtonKind.RADIO_REFUSE).isEmpty())
    }

    @Test fun `le moteur choisit Refuser dans chaque paire et ne clique jamais Accepter`() {
        class Oodrive : FakeApp() {
            val refused = BooleanArray(2)
            var accepted = false
            var saved = false
            private fun pairOf(i: Int, purpose: String) = node(
                "android.widget.RadioGroup",
                children = listOf(
                    radio("Refuser $purpose", refused[i]).onClick { refused[i] = true },
                    radio("Accepter $purpose", !refused[i]).onClick { accepted = true },
                ),
            )
            override fun build(): SnapshotNode = if (saved) window(text("Accueil"), button("Signer")) else window(
                text("Politique de confidentialité"),
                text("Nous et nos partenaires traitons vos données personnelles et votre consentement."),
                pairOf(0, "Mesurer la performance des publicités"),
                pairOf(1, "Comprendre les publics"),
                button("Enregistrer").onClick { saved = true },
            )
        }
        val app = Oodrive()
        val r = runEngine(app, engine, clock)
        assertNotNull("le moteur aurait dû terminer", r.outcome)
        assertFalse("« Accepter … » ne doit jamais être cliqué", app.accepted)
        assertFalse(app.clickedLabels.any { TextNormalizer.canonical(it).startsWith("accepter") })
        assertTrue("les deux choix « Refuser » doivent être sélectionnés", app.refused.all { it })
    }
}
