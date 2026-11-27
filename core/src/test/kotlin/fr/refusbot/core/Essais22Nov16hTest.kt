package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Troisième essai sur téléphone du 22/11 (16 h 42 – 16 h 50), échecs restants :
 * - Didomi dans Opera (16 h 47) : pied de fenêtre écrasé sous la barre d'Opera, « Enregistrer » grisé ;
 * - Didomi dans Firefox (16 h 44) et Sourcepoint dans Opera (16 h 46) : « fenêtre fermée sans validation » ;
 * - Consentmanager dans Firefox (15 h 11) : réglages en sous-pages, « aucun contrôle de préférence ».
 * Les fichiers de diagnostic n'étaient pas joints à cette session : fenêtres reconstituées d'après les
 * constats notés dans `docs/essais-reels.md` et le journal des versions. Tout est simulé.
 */
class Essais22Nov16hTest {
    private val rules = TestRules.rules
    private val opera = "com.opera.browser"
    private val firefox = "org.mozilla.firefox"

    private fun radio(label: String, checked: Boolean) =
        node("android.widget.RadioButton", desc = label, checkable = true, checked = checked, clickable = true)

    /** Hauteur nulle, sous la barre du bas d'Opera : présent dans la page, invisible et impossible à amener à l'écran. */
    private val squashed = Bounds(0, 2300, 1080, 2300)

    private fun page(vararg children: SnapshotNode) =
        Tree.window(node("android.webkit.WebView", bounds = Bounds(0, 0, 1080, 2300), children = children.toList()))

    private fun engine(clock: FakeClock) =
        RefusalEngine(rules) { clock.now }.apply { holdWhenUnverifiable = true; completeRefusal = true }

    // --- Didomi dans Opera : pied de fenêtre écrasé ---------------------------------------------

    /**
     * Une finalité sans choix (sa ligne est elle aussi sous la barre) : « Enregistrer » reste grisé.
     * Avant : plus rien ne bougeait pendant 12 s (« refus partiel »). Désormais : « Refuser tout », caché
     * sous la barre, est actionné par son nœud (jamais par un appui aux coordonnées).
     */
    @Test
    fun `Opera - Enregistrer grise et pied ecrase - Refuser tout actionne par son noeud`() {
        val app = object : FakeApp(opera) {
            var refusedAll = false
            var accepted = false
            var pubRefused = false
            override fun build(): SnapshotNode = if (refusedAll) {
                page(text("Article du jour"))
            } else {
                page(
                    node(viewId = "didomi-popup", children = listOf(
                        node("android.widget.TextView", desc = "Préférences sur l'utilisation de vos données", viewId = "didomi-popup-preferences-title"),
                        text("Autorisez ou refusez la collecte de vos données personnelles pour les finalités suivantes"),
                        node(text = "Publicités personnalisées", children = listOf(
                            radio("Refuser Publicités personnalisées", pubRefused).onClick { pubRefused = true },
                            radio("Accepter Publicités personnalisées", false).onClick { accepted = true },
                        )),
                        // Dernière finalité, sous la barre : pas de choix, d'où « Enregistrer » grisé.
                        node(text = "Mesure d'audience", bounds = squashed, visible = false, children = listOf(
                            node("android.widget.RadioButton", desc = "Refuser Mesure d'audience", checkable = true, clickable = true, visible = false, bounds = squashed),
                            node("android.widget.RadioButton", desc = "Accepter Mesure d'audience", checkable = true, clickable = true, visible = false, bounds = squashed),
                        )),
                        node(bounds = squashed, visible = false, children = listOf(
                            node("android.widget.Button", desc = "Refuser tout nos traitements de données et fermer",
                                viewId = "btn-toggle-disagree", clickable = true, visible = false, bounds = squashed).onClick { refusedAll = true },
                            node("android.widget.Button", desc = "Enregistrer vos préférences et fermer",
                                viewId = "btn-toggle-save", clickable = true, enabled = false, visible = false, bounds = squashed),
                        )),
                    )),
                )
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse("« Accepter » ne doit jamais être cliqué", app.accepted)
        assertTrue("« Refuser tout » non actionné : ${o.reason} / ${r.acts.map { it.description }}", app.refusedAll)
        assertTrue(o.success)
        assertFalse(o.reason.orEmpty(), o.reason.orEmpty().contains("plus rien ne bougeait"))
        assertFalse("jamais « vérifié » : le refus vient du site", o.status.isVerified)
        // Le bouton caché n'est actionné que par son nœud, jamais par un appui aux coordonnées.
        val hiddenClick = r.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().single { it.label.contains("refuser tout") }
        assertTrue(hiddenClick.target is ClickTarget.Node)
        assertTrue(hiddenClick.offscreen)
        assertFalse(hiddenClick.allowTapFallback)
    }

    /** Tous les choix faits, « Enregistrer » actif mais écrasé sous la barre : actionné par son nœud, refus vérifié. */
    @Test
    fun `Opera - Enregistrer actif mais ecrase - validation par le noeud, choix verifies`() {
        val app = object : FakeApp(opera) {
            var saved = false
            var accepted = false
            var refused = false
            override fun build(): SnapshotNode = if (saved) {
                page(text("Article du jour"))
            } else {
                page(
                    node(viewId = "didomi-popup", children = listOf(
                        node("android.widget.TextView", desc = "Préférences sur l'utilisation de vos données", viewId = "didomi-popup-preferences-title"),
                        text("Autorisez ou refusez la collecte de vos données personnelles pour les finalités suivantes"),
                        node(text = "Publicités personnalisées", children = listOf(
                            radio("Refuser Publicités personnalisées", refused).onClick { refused = true },
                            radio("Accepter Publicités personnalisées", false).onClick { accepted = true },
                        )),
                        node(bounds = squashed, visible = false, children = listOf(
                            node("android.widget.Button", desc = "Enregistrer vos préférences et fermer",
                                viewId = "btn-toggle-save", clickable = true, enabled = refused, visible = false, bounds = squashed)
                                .onClick { saved = true },
                        )),
                    )),
                )
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        assertTrue("validation non cliquée : ${o.reason} / ${r.acts.map { it.description }}", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
        val save = r.acts.flatMap { it.ops }.filterIsInstance<Op.Click>().single { it.label.startsWith("enregistrer") }
        assertTrue(save.offscreen)
        assertFalse("jamais d'appui aux coordonnées pour une validation", save.allowTapFallback)
    }

    /** Garde-fous : une acceptation ou un « Refuser » d'un seul mot écrasés n'ont jamais de cible cachée ; hors navigateur non plus. */
    @Test
    fun `boutons ecrases - jamais d'acceptation, jamais un Refuser seul, jamais hors navigateur`() {
        Tree.reset()
        val w = page(
            node(viewId = "didomi-popup", children = listOf(
                text("Nous utilisons des cookies et des partenaires traitent vos données personnelles"),
                button("Paramétrer"),
                node("android.widget.Button", text = "Tout accepter", clickable = true, visible = false, bounds = squashed),
                node("android.widget.Button", text = "Refuser", clickable = true, visible = false, bounds = squashed),
                node("android.widget.Button", text = "Tout refuser", clickable = true, visible = false, bounds = squashed),
            )),
        )
        val web = ScreenAnalyzer(rules).analyze(w, webContentOnly = true)
        assertTrue(web.buttons(ButtonKind.ACCEPT).all { it.hiddenTarget == null && it.target == null })
        assertNull(web.buttons(ButtonKind.REFUSE).single { it.phrase == "refuser" }.hiddenTarget)
        assertTrue(web.buttons(ButtonKind.REFUSE).single { it.phrase == "tout refuser" }.hiddenTarget is ClickTarget.Node)
        val app = ScreenAnalyzer(rules).analyze(w, webContentOnly = false)
        assertTrue(app.buttons.all { it.hiddenTarget == null })
    }

    // --- Didomi dans Firefox : le titre sort de l'écran au défilement ----------------------------

    /**
     * Firefox ne présente que ce qui est à l'écran. Après le balayage, le titre et le texte d'introduction
     * ont disparu de l'arbre, et le conteneur « didomi-popup » dépasse en haut de l'écran : plus aucun
     * indice lisible. Avant : « la fenêtre s'est fermée sans validation ». Désormais : la session continue.
     */
    @Test
    fun `Firefox - titre sorti de l'ecran apres balayage - la session continue jusqu'a la validation`() {
        val app = object : FakeApp(firefox) {
            var scrolled = false
            var saved = false
            var accepted = false
            val refused = BooleanArray(4)
            val purposes = listOf("Publicités personnalisées", "Contenu personnalisé", "Développer et améliorer les services", "Utiliser des données de géolocalisation précises")
            override fun onSwipe() { scrolled = true }
            private fun row(i: Int) = node(text = purposes[i], children = listOf(
                node(children = listOf(radio("Refuser ${purposes[i]}", refused[i]).onClick { refused[i] = true })),
                node(children = listOf(radio("Accepter ${purposes[i]}", false).onClick { accepted = true })),
            ))
            override fun build(): SnapshotNode = when {
                saved -> page(text("Article du jour"))
                !scrolled -> page(
                    node(viewId = "didomi-popup", bounds = Bounds(0, 100, 1080, 2300), children = listOf(
                        node("android.widget.TextView", desc = "Préférences sur l'utilisation de vos données", viewId = "didomi-popup-preferences-title"),
                        text("Autorisez ou refusez la collecte de vos données personnelles pour les finalités suivantes"),
                        row(0), row(1),
                    )),
                )
                else -> page(
                    // Conteneur défilé : son haut est au-dessus de l'écran.
                    node(viewId = "didomi-popup", bounds = Bounds(0, -1400, 1080, 2300), children = listOf(
                        row(2), row(3),
                        node("android.widget.Button", desc = "Enregistrer vos préférences et fermer", viewId = "btn-toggle-save", clickable = true)
                            .onClick { saved = true },
                    )),
                )
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse("« Accepter » ne doit jamais être cliqué", app.accepted)
        assertNotEquals("fenêtre prise pour fermée : ${r.acts.map { it.description }}", OutcomeStatus.INTERRUPTED, o.status)
        assertTrue("choix du bas non refusés : ${app.refused.toList()}", app.refused.all { it })
        assertTrue("validation non cliquée : ${o.reason} / ${r.acts.map { it.description }}", app.saved)
    }

    /** Le mode « suite de session » n'ouvre jamais de session : une page avec un conteneur Didomi qui déborde ne suffit pas. */
    @Test
    fun `conteneur de plateforme qui deborde - aucune session sans fenetre lisible`() {
        val app = object : FakeApp(firefox) {
            override fun build(): SnapshotNode = page(
                node(viewId = "didomi-host", bounds = Bounds(0, -1400, 1080, 2300), children = listOf(
                    text("Développer et améliorer les services"),
                    button("Enregistrer"),
                )),
            )
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        assertNull(r.outcome)
        assertEquals(0, app.opsCount)
    }

    // --- Sourcepoint dans Opera : centre de préférences lent à s'afficher ------------------------

    /**
     * Le centre de préférences Sourcepoint s'ouvre dans un nouveau cadre : la page reste vide un instant.
     * Avant : 0,75 s d'attente puis « la fenêtre s'est fermée sans validation ». Désormais ~2 s.
     */
    @Test
    fun `Opera - Sourcepoint lent a s'afficher apres Personnaliser - la session attend`() {
        val app = object : FakeApp(opera) {
            var optionsAt = -1
            var renders = 0
            var off = false
            var saved = false
            var accepted = false
            override fun build(): SnapshotNode {
                renders++
                return when {
                    saved -> page(text("Article du jour"))
                    optionsAt < 0 -> page(
                        node(viewId = "sp_message_container_1", children = listOf(
                            text("Nous et nos partenaires utilisons des cookies pour des publicités personnalisées"),
                            button("Personnaliser").onClick { optionsAt = renders },
                            button("Tout accepter").onClick { accepted = true },
                        )),
                    )
                    renders - optionsAt <= 5 -> page(text("Chargement"))
                    else -> page(
                        node(viewId = "sp_message_container_2", children = listOf(
                            text("Vos choix de consentement pour nos partenaires et les finalités"),
                            Tree.group(text("Mesure d'audience"), Tree.switch("Mesure d'audience", checked = !off).onClick { off = true }),
                            button("Enregistrer et quitter").onClick { saved = true },
                            button("Tout accepter").onClick { accepted = true },
                        )),
                    )
                }
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        assertNotEquals("fermeture conclue trop tôt : ${r.acts.map { it.description }}", OutcomeStatus.INTERRUPTED, o.status)
        assertTrue("validation non cliquée : ${o.reason}", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    /** La fenêtre vraiment fermée sans aucun clic de refus reste « interrompue » (juste un peu plus tard). */
    @Test
    fun `fenetre vraiment fermee apres Personnaliser - toujours interrompue`() {
        val app = object : FakeApp(opera) {
            var gone = false
            override fun build(): SnapshotNode = if (gone) {
                page(text("Article du jour"))
            } else {
                page(
                    node(viewId = "sp_message_container_1", children = listOf(
                        text("Nous et nos partenaires utilisons des cookies pour des publicités personnalisées"),
                        button("Personnaliser").onClick { gone = true },
                        button("Tout accepter"),
                    )),
                )
            }
        }
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        assertEquals(OutcomeStatus.INTERRUPTED, r.outcome!!.status)
    }

    // --- Galaxy Defense (17 h 20) : liste qui ne défile pas, mais entièrement présente ---------------

    /**
     * Page « Confirmer nos fournisseurs » (Google UMP dans une WebView) : toutes les lignes sont dans la
     * capture, celles sous l'écran avec une hauteur nulle et leur état. La liste refuse de défiler.
     * Avant : « défilement impossible », 81 « intérêt légitime » laissés activés. Désormais : décochés
     * par leur nœud, relus désactivés, puis validation.
     */
    private inner class GalaxyDefense(private val exposeHiddenRows: Boolean) :
        FakeApp("com.cyberjoy.galaxydefense") {
        val vendors = (1..30).map { "Fournisseur numéro $it" }
        // 23/11 (relecture) : activés dès le départ, sinon le test ne prouve rien quand aucun défilement n'a lieu.
        val li = BooleanArray(30) { true }
        var scrollTried = false
        var saved = false
        var accepted = false
        override fun scrollIsBlocked(container: UiNode) = true.also { scrollTried = true; if (!liSet) { liSet = true; li.fill(true) } }
        private var liSet = false
        override fun build(): SnapshotNode {
            if (saved) return Tree.window(node("android.webkit.WebView", bounds = Bounds(0, 0, 1080, 2340), children = listOf(text("Galaxy Defense"))))
            val rows = vendors.indices.mapNotNull { i ->
                val shown = i < 4
                if (!shown && !exposeHiddenRows) return@mapNotNull null
                val b = if (shown) Bounds(120, 900 + i * 200, 960, 1080 + i * 200) else Bounds(120, 2340, 960, 2340)
                val tb = if (shown) Bounds(855, 1000 + i * 200, 960, 1060 + i * 200) else Bounds(855, 2340, 960, 2340)
                node(bounds = b, visible = shown, children = listOf(
                    node("android.widget.TextView", text = vendors[i], bounds = b, visible = shown),
                    node("android.widget.ToggleButton", desc = "Intérêt légitime, ${vendors[i]}", checkable = true,
                        checked = li[i], clickable = true, visible = shown, bounds = tb).onClick { li[i] = false },
                ))
            }
            return Tree.window(node("android.webkit.WebView", bounds = Bounds(0, 0, 1080, 2340), children = listOf(
                node("android.app.AlertDialog", bounds = Bounds(52, 231, 1026, 2110), children = listOf(
                    node("android.view.View", scrollable = true, bounds = Bounds(55, 333, 1026, 1745), children = listOf(
                        node("android.widget.TextView", text = "Confirmer nos fournisseurs", bounds = Bounds(118, 364, 963, 420)),
                        node("android.widget.TextView", text = "Les fournisseurs peuvent utiliser vos données. Intérêt légitime : certains fournisseurs ne demandent pas votre consentement.", bounds = Bounds(118, 448, 963, 640)),
                    ) + rows),
                    node("android.widget.Button", text = "Tout accepter", clickable = true, bounds = Bounds(100, 1900, 500, 1980)).onClick { accepted = true },
                    node("android.widget.Button", text = "Confirmer les choix", clickable = true, bounds = Bounds(520, 1900, 1000, 1980)).onClick { saved = true },
                )),
            )))
        }
    }

    @Test
    fun `Galaxy Defense - liste immobile mais entierement presente - tout decoche puis valide`() {
        val app = GalaxyDefense(exposeHiddenRows = true)
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        // 23/11 : la liste entière étant déjà dans la capture, elle est lue sans même essayer de défiler.
        assertFalse("défilement inutile : la liste entière est déjà dans la capture", app.scrollTried)
        assertTrue(r.acts.map { it.description }.toString(), r.acts.any { it.description.contains("interrupteur") })
        assertTrue("interrupteurs restés activés : ${app.li.count { it }} / ${o.reason} / ${r.acts.map { it.description }}", app.li.none { it })
        assertTrue("validation non cliquée : ${o.reason}", app.saved)
        assertEquals(OutcomeStatus.OPTIONS_SUCCESS, o.status)
    }

    /** Lignes sous l'écran absentes de la capture (comme Firefox) : on ne peut rien affirmer, échec prudent comme avant. */
    @Test
    fun `liste immobile et lignes sous l'ecran absentes - toujours defilement impossible`() {
        val app = GalaxyDefense(exposeHiddenRows = false)
        val clock = FakeClock()
        val r = runEngine(app, engine(clock), clock)
        assertFalse(app.saved)
        assertFalse(app.accepted)
        assertEquals(OutcomeStatus.INCOMPLETE_CAPTURE, r.outcome!!.status)
    }

    // --- Consentmanager dans Firefox : réglages en sous-pages ------------------------------------

    private inner class ConsentManagerSite : FakeApp(firefox) {
        var settings = false
        var refusedAll = false
        var accepted = false
        var saved = false
        override fun build(): SnapshotNode = when {
            refusedAll || saved -> page(text("Article du jour"))
            !settings -> page(
                node(viewId = "cmpbox", children = listOf(
                    text("Nous et nos partenaires utilisons des cookies et traitons vos données personnelles"),
                    button("Tout accepter").onClick { accepted = true },
                    button("Tout refuser").onClick { refusedAll = true },
                    button("Personnaliser").onClick { settings = true },
                )),
            )
            // Finalités en sous-pages : de simples liens, aucun interrupteur sur cette page.
            else -> page(
                node(viewId = "cmpbox", children = listOf(
                    text("Paramètres de confidentialité : finalités et partenaires"),
                    button("Mesure d'audience"),
                    button("Publicités personnalisées"),
                    button("Enregistrer").onClick { saved = true },
                    button("Retour").onClick { settings = false },
                )),
            )
        }
    }

    /**
     * Refus complet : « Tout refuser » du premier écran mis en attente, préférences sans réglage lisible.
     * Avant : « aucun contrôle de préférence accessible reconnu », refus impossible. Désormais : retour au
     * premier écran, puis « Tout refuser » (non vérifiable). Rien n'est enregistré à l'aveugle.
     */
    @Test
    fun `Consentmanager - preferences illisibles - retour au premier ecran puis Tout refuser`() {
        val app = ConsentManagerSite()
        val clock = FakeClock()
        val e = engine(clock)
        val r = runEngine(app, e, clock)
        val o = r.outcome!!
        assertFalse(app.accepted)
        assertFalse("« Enregistrer » cliqué sans aucun réglage vu", app.saved)
        assertTrue("refus non cliqué : ${o.reason} / ${r.acts.map { it.description }}", app.refusedAll)
        assertTrue(o.success)
        assertEquals(OutcomeStatus.UNVERIFIED, o.status)

        // Site suivant sur la même plateforme : le « Tout refuser » n'est plus mis en attente.
        clock.advance(60_000)
        val next = ConsentManagerSite()
        val r2 = runEngine(next, e, clock)
        assertTrue(next.refusedAll)
        assertFalse("préférences rouvertes inutilement", next.settings)
        assertEquals(1, r2.acts.size)
    }
}
