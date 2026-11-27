package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réglage « Refus complet » (20/11) : préférences d'abord, refus direct seulement en repli. */
class CompleteRefusalTest {
    private val rules = TestRules.rules

    /** Fenêtre d'une plateforme reconnue (Didomi) : ses préférences s'ouvrent dans la même fenêtre. */
    private fun notice() = Tree.window(
        node(viewId = "didomi-notice", children = listOf(
            text("Nous et nos partenaires utilisons des cookies et vos données personnelles, y compris sur la base de l'intérêt légitime"),
            button("Continuer sans accepter"),
            button("Paramétrer"),
            button("Tout accepter"),
        )),
    )

    /** Fenêtre « Avant d'accéder à Google » vue dans Opera le 21/11 (plateforme inconnue). */
    private fun googleNotice() = Tree.window(
        node("android.app.AlertDialog", children = listOf(
            text("Avant d'accéder à Google"),
            text("Nous utilisons des cookies et des données, y compris des adresses IP, pour : afficher des annonces personnalisées"),
            node("android.widget.Button", text = "Tout accepter", clickable = true, viewId = "L2AGLb"),
            node("android.widget.Button", text = "Tout refuser", clickable = true, viewId = "W0wltc"),
            node(desc = "Plus d'options pour les paramètres de personnalisation", clickable = true, children = listOf(text("Plus d'options"))),
        )),
    )

    /** Fenêtre de cookies d'Opera (21/11), dans la page web : boutons décrits par leur seule description. */
    private fun operaPrompt() = Tree.window(
        node("android.webkit.WebView", children = listOf(node("android.app.AlertDialog", viewId = "pop", children = listOf(
            text("Paramètres de confidentialité et des cookies"),
            text("Nous utilisons des cookies et des technologies similaires pour fournir et améliorer nos services et vous proposer des publicités personnalisées"),
            node("android.widget.Button", desc = "Accepter tous les paramètres", clickable = true),
            node("android.widget.Button", desc = "Refuser tous les paramètres", clickable = true),
            button("Personnaliser les cookies"),
        )))),
    )

    private fun prefs(liChecked: Boolean, readable: Boolean = true) = Tree.window(
        text("Vos préférences : cookies, partenaires et intérêt légitime"),
        if (readable) group(text("Publicité personnalisée"), Tree.switch("Consentement", checked = false))
        else group(text("Publicité personnalisée")),
        if (readable) group(text("Mesure d'audience"), Tree.switch("Intérêt légitime", checked = liChecked))
        else group(text("Mesure d'audience")),
        button("Tout refuser"),
        button("Enregistrer"),
    )

    private fun label(st: Step) = (st as? Step.Act)?.description ?: st.toString()

    @Test
    fun `rapide - refus direct au premier niveau`() {
        Tree.reset()
        val e = RefusalEngine(rules)
        assertTrue(label(e.next(Screen("com.x", notice()))).startsWith("refus"))
    }

    @Test
    fun `complet - ouvre les preferences au lieu du refus direct`() {
        Tree.reset()
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        assertTrue(label(e.next(Screen("com.x", notice()))).startsWith("options"))
    }

    @Test
    fun `complet - plateforme inconnue, le refus direct passe avant les options (Google dans Opera)`() {
        Tree.reset()
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        assertEquals("refus : « tout refuser »", label(e.next(Screen("com.opera.browser", googleNotice()))))
    }

    @Test
    fun `fenetre d'Opera - Refuser tous les parametres est reconnu et clique`() {
        Tree.reset()
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        val st = label(e.next(Screen("com.opera.browser", operaPrompt())))
        assertEquals("refus : « refuser tous les parametres »", st)
    }

    @Test
    fun `complet - plateforme inconnue, un Refuser seul attend les preferences`() {
        Tree.reset()
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        val w = Tree.window(
            text("Nos partenaires et nous utilisons des cookies pour la publicité personnalisée"),
            button("Refuser"),
            button("Paramétrer"),
            button("Tout accepter"),
        )
        assertTrue(label(e.next(Screen("com.x", w))).startsWith("options"))
    }

    @Test
    fun `complet - TCF generique, les preferences passent avant Tout refuser`() {
        Tree.reset()
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        val w = Tree.window(
            text("Nous et nos partenaires stockons et/ou accédons à des informations sur un appareil, sur la base de votre consentement ou de l'intérêt légitime"),
            button("Tout refuser"),
            button("Paramétrer"),
            button("Tout accepter"),
        )
        assertTrue(label(e.next(Screen("com.x", w))).startsWith("options"))
    }

    /** Parcours complet : un seul clic, sur « Tout refuser », et un résultat « non vérifiable ». */
    @Test
    fun `complet - Google dans Opera, refus non verifiable et jamais Tout accepter`() {
        val app = object : FakeApp("com.opera.browser") {
            var open = true
            override fun build(): SnapshotNode = if (open) {
                Tree.window(node("android.webkit.WebView", children = listOf(
                    node("android.app.AlertDialog", children = listOf(
                        text("Avant d'accéder à Google"),
                        text("Nous utilisons des cookies et des données, y compris des adresses IP, pour : afficher des annonces personnalisées"),
                        node("android.widget.Button", text = "Tout accepter", clickable = true, viewId = "L2AGLb").onClick { error("acceptation") },
                        node("android.widget.Button", text = "Tout refuser", clickable = true, viewId = "W0wltc").onClick { open = false },
                        node(desc = "Plus d'options pour les paramètres de personnalisation", clickable = true,
                            children = listOf(text("Plus d'options"))).onClick { open = false },
                    )),
                )))
            } else {
                Tree.window(node("android.webkit.WebView", children = listOf(text("Google"), button("Rechercher"))))
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }.apply { completeRefusal = true }, clock)
        val o = r.outcome
        assertNotNull(o)
        assertEquals(listOf("tout refuser"), app.clickedLabels)
        assertEquals(RefusalMethod.DIRECT, o!!.method)
        assertEquals(OutcomeStatus.UNVERIFIED, o.status)
    }

    @Test
    fun `complet - decoche l'interet legitime puis enregistre, sans Tout refuser`() {
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        Tree.reset()
        assertTrue(label(e.next(Screen("com.x", notice()))).startsWith("options"))
        var liOn = true
        var closed = false
        val seen = ArrayList<String>()
        repeat(20) {
            if (closed) return@repeat
            Tree.reset()
            when (val st = e.next(Screen("com.x", prefs(liOn)))) {
                is Step.Act -> {
                    seen += st.description
                    if (st.description.contains("interrupteur")) liOn = false
                    if (st.description.startsWith("validation") || st.description.startsWith("refus")) closed = true
                }
                else -> Unit
            }
        }
        assertTrue(seen.toString(), seen.any { it.contains("interrupteur") })
        assertTrue(seen.toString(), seen.last().startsWith("validation"))
        assertTrue(seen.none { it.contains("accepter") })
    }

    @Test
    fun `complet - preferences illisibles, repli sur le refus du site`() {
        val e = RefusalEngine(rules).apply { completeRefusal = true }
        Tree.reset()
        e.next(Screen("com.x", notice()))
        var last = ""
        repeat(30) {
            Tree.reset()
            val st = e.next(Screen("com.x", prefs(liChecked = false, readable = false)))
            if (st is Step.Act && last.isEmpty()) last = st.description
        }
        // Rien à décocher ni à lire : le refus du site est utilisé tout de suite.
        assertEquals("refus : « tout refuser »", last)
    }
}
