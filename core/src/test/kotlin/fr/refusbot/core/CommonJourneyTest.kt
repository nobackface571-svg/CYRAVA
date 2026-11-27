package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.switch
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parcours COMMUN (refonte du 21/11), le même pour toutes les plateformes :
 * premier écran → réglages → finalités (refus global d'abord) → partenaires (refus global, aucun
 * partenaire déplié) → validation. Chaque scénario échoue si un accordéon, un partenaire, une page
 * externe ou un bouton d'acceptation est touché.
 */
class CommonJourneyTest {
    private val rules = TestRules.rules

    /** Libellés et repères d'une plateforme : c'est tout ce qu'une plateforme fournit. */
    data class Cmp(
        val name: String,
        val marker: String?,
        val english: Boolean,
        val options: String,
        val purposesRefusal: String,
        val vendorLink: String,
        val confirm: String,
    )

    private val platforms = listOf(
        Cmp("Didomi", "didomi-popup", false, "Paramétrer", "Tout refuser", "Voir nos partenaires", "Enregistrer"),
        Cmp("Sourcepoint", "sp_message_container", true, "Manage options", "Reject all", "Vendors", "Save & Exit"),
        Cmp("Axeptio", "axeptio_overlay", false, "Personnaliser", "Tout refuser", "Voir les partenaires", "Enregistrer mes choix"),
        Cmp("OneTrust", "onetrust-pc-sdk", true, "Cookies Settings", "Reject All", "Vendor list", "Confirm My Choices"),
        Cmp("Quantcast", "qc-cmp2-container", true, "More options", "Reject all", "Partners", "Save & exit"),
        Cmp("Cookiebot", "CybotCookiebotDialog", false, "Personnaliser", "Refuser tout", "Voir les fournisseurs", "Valider"),
        Cmp("inconnue", null, false, "Préférences", "Tout désactiver", "Partenaires", "Enregistrer"),
    )

    /** Écran du jeu, une fois la fenêtre fermée. */
    private fun gameScreen() = window(text("Niveau 1"), button("Jouer"))

    private class Forbidden(what: String) : AssertionError(what)

    /** Fenêtre de consentement simulée, construite uniquement à partir des libellés de [cmp]. */
    private inner class CmpApp(val cmp: Cmp) : FakeApp() {
        var page = "notice"
        val purposes = booleanArrayOf(true, true)
        var masterBlocked = false
        var saved = false
        val forbidden = mutableListOf<String>()

        private fun no(what: String): () -> Unit = { forbidden += what; throw Forbidden(what) }

        private fun panel(vararg children: SnapshotNode) =
            if (cmp.marker != null) window(node(viewId = cmp.marker, children = children.toList())) else window(*children)

        /** Ligne d'accordéon : la toucher ou la déplier est interdit. */
        private fun accordion(label: String) =
            node("android.widget.Button", text = label, clickable = true, expandable = true)
                .onClick(no("accordéon cliqué : $label")).onExpand(no("accordéon déplié : $label"))

        private fun radio(label: String, checked: Boolean) =
            node("android.widget.RadioButton", text = label, clickable = true, checkable = true, checked = checked)

        private val accept get() = if (cmp.english) "Accept all" else "Tout accepter"
        private val block get() = if (cmp.english) "Reject" else "Bloquer"
        private val allow get() = if (cmp.english) "Accept" else "Autoriser"

        override fun build(): SnapshotNode = when {
            saved -> gameScreen()
            page == "notice" -> panel(
                text(if (cmp.english) "We and our partners use cookies and process your personal data." else "Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                button(accept).onClick(no("acceptation au premier écran")),
                button(cmp.options).onClick { page = "purposes" },
            )
            page == "purposes" -> panel(
                text(if (cmp.english) "Your privacy: purposes and legitimate interest" else "Vos choix : finalités et intérêt légitime"),
                // Un accordéon dont le titre ressemble à un lien partenaires : jamais touché.
                accordion("+ ${cmp.vendorLink}"),
                accordion(if (cmp.english) "+ Data categories" else "+ Catégories de données"),
                button(cmp.purposesRefusal).onClick { purposes.fill(false) },
                group(
                    text(if (cmp.english) "Personalised ads" else "Publicité personnalisée"),
                    switch(if (cmp.english) "Consent" else "Consentement", purposes[0]).onClick { purposes[0] = !purposes[0] },
                    accordion(if (cmp.english) "› Legitimate interest" else "› Intérêt légitime"),
                    button(if (cmp.english) "Learn more" else "En savoir plus").onClick(no("détails d'une finalité")),
                ),
                group(
                    text(if (cmp.english) "Audience measurement" else "Mesure d'audience"),
                    switch(if (cmp.english) "Consent" else "Consentement", purposes[1]).onClick { purposes[1] = !purposes[1] },
                    accordion(if (cmp.english) "+ Device storage" else "+ Stockage de l'appareil"),
                ),
                button(if (cmp.english) "Privacy policy" else "Politique de confidentialité").onClick(no("page externe")),
                button(cmp.vendorLink).onClick { page = "partners" },
                button(accept).onClick(no("acceptation dans les finalités")),
            )
            else -> panel(
                text(if (cmp.english) "Our vendors and partners" else "Nos partenaires et fournisseurs"),
                group(
                    text(if (cmp.english) "All vendors" else "Tous les partenaires"),
                    group(
                        radio(block, masterBlocked).onClick { masterBlocked = true },
                        radio(allow, !masterBlocked).onClick(no("« Autoriser » tous les partenaires")),
                    ),
                    cls = "android.view.View",
                ),
                *listOf("1plusX AG", "33Across", "Adform").map { vendor ->
                    group(
                        accordion(vendor),
                        group(
                            radio("$block $vendor", masterBlocked).onClick(no("partenaire un par un : $vendor")),
                            radio("$allow $vendor", !masterBlocked).onClick(no("« Autoriser » $vendor")),
                        ),
                        cls = "android.view.View",
                    )
                }.toTypedArray(),
                button(cmp.confirm).onClick { saved = true },
                button(accept).onClick(no("acceptation dans les partenaires")),
            )
        }
    }

    @Test
    fun `chaque plateforme suit le meme parcours, sans accordeon ni acceptation`() {
        for (cmp in platforms) {
            val app = CmpApp(cmp)
            val clock = FakeClock()
            val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
            val o = r.outcome
            val steps = r.acts.map { it.description }
            assertNotNull("${cmp.name} : aucun résultat", o)
            assertTrue("${cmp.name} : clics interdits ${app.forbidden}", app.forbidden.isEmpty())
            assertTrue("${cmp.name} : section dépliée ${app.expandedLabels}", app.expandedLabels.isEmpty())
            assertTrue("${cmp.name} : non validé, étapes $steps, motif ${o!!.reason}", app.saved)
            assertTrue("${cmp.name} : refus non enregistré", o.success)
            assertTrue("${cmp.name} : finalités encore acceptées", app.purposes.none { it })
            assertTrue("${cmp.name} : partenaires non refusés", app.masterBlocked)
            // Ordre du parcours : réglages, refus des finalités, partenaires, refus global, validation.
            // Depuis le 27/11 (BFM), un « Tout refuser » des préférences peut fermer la fenêtre sans rien
            // laisser vérifier : il passe APRÈS les choix un par un (ici, les deux interrupteurs). Un bouton
            // qui ne fait que tout désactiver (« Tout désactiver ») reste utilisé d'abord. En anglais, l'interrupteur
            // « Consent » est bloqué par le garde-fou (mot d'acceptation) : le refus global sert alors comme avant.
            val canon = TextNormalizer::canonical
            val switchLabel = canon(if (cmp.english) "Consent" else "Consentement")
            val purposesSteps = if (canon(cmp.purposesRefusal) in rules.refuse && !cmp.english) {
                listOf(switchLabel, switchLabel)
            } else {
                listOf(canon(cmp.purposesRefusal))
            }
            assertEquals(
                "${cmp.name} : $steps",
                listOf(canon(cmp.options)) + purposesSteps + listOf(canon(cmp.vendorLink), canon(if (cmp.english) "Reject" else "Bloquer"), canon(cmp.confirm)),
                app.clickedLabels,
            )
            // Jamais deux clics sur le même élément (les deux interrupteurs portent le même libellé).
            val buttons = app.clickedLabels.filter { it != switchLabel }
            assertEquals(buttons.distinct(), buttons)
        }
    }

    @Test
    fun `premier ecran - un refus direct suffit, puis fin`() {
        for (cmp in platforms) {
            val app = object : FakeApp() {
                var refused = false
                override fun build(): SnapshotNode = if (refused) gameScreen() else window(
                    text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                    button("Tout accepter").onClick { error("acceptation") },
                    button(cmp.options).onClick { error("les réglages ne sont pas nécessaires") },
                    button("Continuer sans accepter").onClick { refused = true },
                )
            }
            val clock = FakeClock()
            val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
            assertEquals(listOf("continuer sans accepter"), app.clickedLabels)
            assertEquals(OutcomeStatus.UNVERIFIED, o?.status)
        }
    }

    /** Réglages dont le bouton final est caché en bas de la fenêtre (Sourcepoint, 21/11). */
    private class HiddenSave(val container: Boolean, val revealable: Boolean = true) : FakeApp() {
        var options = false
        var disabled = false
        var scrolled = false
        var saved = false

        override fun build(): SnapshotNode = when {
            saved -> window(text("Niveau 1"), button("Jouer"))
            !options -> window(
                node(viewId = "sp_message_container", children = listOf(
                    text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                    button("Tout accepter").onClick { error("acceptation") },
                    button("Gérer les options").onClick { options = true },
                )),
            )
            else -> {
                val content = listOf(
                    text(if (disabled) "Finalités : tout est désactivé" else "Finalités et intérêt légitime de nos partenaires"),
                    button("Tout désactiver").onClick { disabled = true },
                    button("Tout accepter").onClick { error("acceptation") },
                    button("Enregistrer et quitter", visible = scrolled).onClick { saved = true },
                )
                window(node(viewId = "sp_message_container", children = listOf(
                    if (container) group(*content.toTypedArray(), scrollable = true) else group(*content.toTypedArray()),
                )))
            }
        }

        override fun onScroll(container: UiNode) {
            if (revealable) scrolled = true
        }

        override fun onSwipe() {
            if (revealable) scrolled = true
        }
    }

    @Test
    fun `bouton de validation hors ecran - le bot defile avant de conclure`() {
        val app = HiddenSave(container = true)
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        val steps = r.acts.map { it.description }
        assertTrue("non validé : $steps / ${r.outcome?.reason}", app.saved)
        val scroll = steps.indexOfFirst { it.startsWith("défilement vers le bouton de validation") }
        val save = steps.indexOfFirst { it.startsWith("validation") }
        assertTrue(steps.toString(), scroll in 0 until save)
        assertEquals(listOf("gerer les options", "tout desactiver", "enregistrer et quitter"), app.clickedLabels)
    }

    @Test
    fun `bouton de validation hors ecran sans zone defilante - balayage puis validation`() {
        val app = HiddenSave(container = false)
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        val steps = r.acts.map { it.description }
        assertTrue("non validé : $steps / ${r.outcome?.reason}", app.saved)
        assertTrue(steps.toString(), steps.any { it.startsWith("balayage vers le bouton de validation") })
    }

    @Test
    fun `bouton de validation vraiment absent - introuvable seulement apres defilement jusqu'en bas`() {
        val app = HiddenSave(container = true, revealable = false)
        val clock = FakeClock()
        val r = runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        val o = r.outcome
        assertNotNull(o)
        assertFalse(app.saved)
        assertTrue(r.acts.any { it.description.startsWith("défilement vers le bouton de validation") })
        assertTrue(o!!.reason.orEmpty(), o.reason.orEmpty().contains("même après défilement"))
    }

    @Test
    fun `liste noire - accordeons, details de finalite et pages externes`() {
        assertTrue(NavigationFilter.startsWithAccordionMark("+ Traitements de données basés sur le consentement"))
        assertTrue(NavigationFilter.startsWithAccordionMark("› Partenaires"))
        assertFalse(NavigationFilter.startsWithAccordionMark("Tout refuser"))
        for (label in listOf("Catégories de données", "Stockage de l'appareil", "À propos du stockage de l'appareil",
            "Voir les infos de l'utilisateur", "Intérêt légitime", "Politique de confidentialité")) {
            assertNotNull(label, NavigationFilter.blockReasonForLabel(TextNormalizer.canonical(label), settingsOpened = false))
        }
        // « En savoir plus » / « Détails » : autorisés pour ouvrir les réglages, interdits ensuite.
        assertEquals(null, NavigationFilter.blockReasonForLabel("en savoir plus", settingsOpened = false))
        assertNotNull(NavigationFilter.blockReasonForLabel("en savoir plus", settingsOpened = true))
        assertNotNull(NavigationFilter.blockReasonForLabel("details", settingsOpened = true))
        // Les actions de refus et de validation restent autorisées.
        for (label in listOf("tout refuser", "refuser", "bloquer", "voir nos partenaires", "enregistrer", "objecter")) {
            assertEquals(label, null, NavigationFilter.blockReasonForLabel(label, settingsOpened = true))
        }
    }

    // --- Relecture sécurité du 21/11 ----------------------------------------------------------

    private fun radio(label: String, checked: Boolean, visible: Boolean = true) =
        node("android.widget.RadioButton", text = label, clickable = true, checkable = true, checked = checked, visible = visible)

    /** Page des partenaires Didomi, choix maître déjà sur « Bloquer ». */
    private inner class Partners(val googleAllowed: Boolean) : FakeApp() {
        var options = false
        var saved = false
        override fun build(): SnapshotNode = when {
            saved -> gameScreen()
            !options -> window(node(viewId = "didomi-notice", children = listOf(
                text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                button("Tout accepter").onClick { error("acceptation") },
                button("Paramétrer").onClick { options = true },
            )))
            else -> window(node(viewId = "didomi-popup", children = listOf(
                text("Nos partenaires et fournisseurs"),
                group(text("Tous les partenaires"), group(radio("Bloquer", true), radio("Autoriser", false)), cls = "android.view.View"),
                group(text("Google"), group(radio("Bloquer Google", !googleAllowed), radio("Autoriser Google", googleAllowed)), cls = "android.view.View"),
                button("Enregistrer").onClick { saved = true },
                button("Tout accepter").onClick { error("acceptation") },
            )))
        }
    }

    @Test
    fun `choix maitre Bloquer mais un partenaire sur Autoriser - jamais verifie, rien enregistre`() {
        val app = Partners(googleAllowed = true)
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
        assertNotNull(o)
        assertFalse("« Enregistrer » cliqué malgré « Autoriser Google »", app.saved)
        assertTrue(o!!.status.toString(), o.status != OutcomeStatus.OPTIONS_SUCCESS)
    }

    @Test
    fun `choix maitre seul - verifie, avec un motif qui le dit`() {
        val app = Partners(googleAllowed = false)
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
        assertTrue(app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o?.status)
        assertTrue(o?.reason.orEmpty(), o?.reason.orEmpty().contains("choix global"))
    }

    @Test
    fun `Autoriser selectionne hors de l'ecran - jamais verifie, rien enregistre`() {
        val app = object : FakeApp() {
            var options = false
            var saved = false
            val blocked = BooleanArray(3)
            override fun build(): SnapshotNode = when {
                saved -> gameScreen()
                !options -> window(
                    text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                    button("Tout accepter").onClick { error("acceptation") },
                    button("Paramétrer").onClick { options = true },
                )
                else -> window(
                    text("Vos choix : finalités et intérêt légitime"),
                    *(0 until 3).map { i ->
                        group(text("Finalité $i"), group(radio("Bloquer", blocked[i]).onClick { blocked[i] = true }, radio("Autoriser", !blocked[i])), cls = "android.view.View")
                    }.toTypedArray(),
                    // Plus bas, hors de l'écran : « Autoriser » sélectionné, lisible mais invisible.
                    *(3 until 5).map { i ->
                        group(text("Finalité $i", visible = false), group(radio("Bloquer", false, visible = false), radio("Autoriser", true, visible = false)), cls = "android.view.View")
                    }.toTypedArray(),
                    button("Enregistrer").onClick { saved = true },
                )
            }
        }
        val clock = FakeClock()
        val o = runEngine(app, RefusalEngine(rules) { clock.now }, clock).outcome
        assertNotNull(o)
        assertFalse("enregistré avec des « Autoriser » sélectionnés hors écran", app.saved)
        assertTrue(o!!.status.toString(), o.status != OutcomeStatus.OPTIONS_SUCCESS)
    }

    /** Réglages ouverts, puis délai presque écoulé : ni défilement ni enregistrement de choix acceptés. */
    @Test
    fun `fin de delai - pas de defilement de recherche, pas d'enregistrement avec un interrupteur active`() {
        for (saveVisible in listOf(false, true)) {
            val app = object : FakeApp() {
                var options = false
                var saved = false
                override fun build(): SnapshotNode = when {
                    saved -> gameScreen()
                    !options -> window(
                        text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                        button("Tout accepter").onClick { error("acceptation") },
                        button("Paramétrer").onClick { options = true },
                    )
                    else -> window(group(
                        text("Vos choix : finalités et intérêt légitime"),
                        group(text("Publicité personnalisée"), switch("Consentement", true)),
                        button("Enregistrer", visible = saveVisible).onClick { saved = true },
                        scrollable = true,
                    ))
                }
            }
            val clock = FakeClock()
            val engine = RefusalEngine(rules) { clock.now }
            val first = engine.next(Screen(app.packageName, app.render()))
            (first as Step.Act).ops.forEach(app::perform)
            clock.advance(rules.limits.timeoutMs * 85 / 100)
            val r = runEngine(app, engine, clock)
            val steps = r.acts.map { it.description }
            assertFalse("saveVisible=$saveVisible : enregistré, étapes $steps", app.saved)
            assertTrue(steps.toString(), steps.none { it.startsWith("défilement vers le bouton") || it.startsWith("balayage vers le bouton") })
        }
    }

    @Test
    fun `retour - un bouton avec chevron Retour sur une sous-page sans validation est clique une fois`() {
        val app = object : FakeApp() {
            var page = "notice"
            var saved = false
            override fun build(): SnapshotNode = when {
                saved -> gameScreen()
                page == "notice" -> window(
                    text("Nous et nos partenaires utilisons des cookies et vos données personnelles."),
                    button("Tout accepter").onClick { error("acceptation") },
                    button("Paramétrer").onClick { page = "purposes" },
                )
                page == "purposes" -> window(
                    text("Vos choix : finalités et intérêt légitime"),
                    group(text("Publicité personnalisée"), switch("Consentement", false)),
                    button("Voir nos partenaires").onClick { page = "partners" },
                    button("Enregistrer").onClick { saved = true },
                )
                else -> window(
                    text("Nos partenaires : liste des fournisseurs et intérêt légitime"),
                    text("1plusX AG"), text("33Across"),
                    button("‹ Retour").onClick { page = "purposes" },
                )
            }
        }
        val clock = FakeClock()
        runEngine(app, RefusalEngine(rules) { clock.now }, clock)
        assertEquals(listOf("parametrer", "voir nos partenaires", "retour", "enregistrer"), app.clickedLabels)
        assertTrue(app.saved)
    }
}
