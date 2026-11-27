package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.group
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cas relevés dans les diagnostics du 20/11/2026 (Firefox et Galaxy Defense), reproduits en
 * arbres simulés : le panneau choisi était un simple paragraphe contenant un lien « partenaires »,
 * la barre du navigateur était analysée, et deux formules de refus manquaient.
 */
class FieldReport20NovTest {
    private val rules = TestRules.rules
    private val analyzer = ScreenAnalyzer(rules)
    private val firefox = "org.mozilla.firefox"

    @Before
    fun setUp() = Tree.reset()

    private fun clickedLabel(step: Step): String? =
        ((step as? Step.Act)?.ops?.firstOrNull() as? Op.Click)?.label

    /** Ultra Premium Direct (Didomi) : « Continuer sans accepter » était ignoré. */
    private fun ultraPremium() = Tree.window(
        node("android.webkit.WebView", children = listOf(
            node(viewId = "didomi-popup", clickable = true, children = listOf(
                node("android.app.Dialog", text = "Bienvenue Gestion du consentement", clickable = true, children = listOf(
                    button("Continuer sans accepter"),
                    group(
                        text("Avec votre accord, nous et"),
                        button("nos 30 partenaires"),
                        text("utilisons des cookies pour traiter vos données personnelles, y compris sur la base de l'intérêt légitime"),
                        cls = "android.view.View",
                    ),
                    text("Vos données personnelles sont traitées pour les finalités suivantes : Publicités personnalisées"),
                    node("android.widget.Button", text = "Personnaliser mes choix: Configurer vos consentements",
                        clickable = true, viewId = "didomi-notice-learn-more-button"),
                    node("android.widget.Button", text = "Tout accepter: Accepter notre traitement des données et fermer",
                        clickable = true, viewId = "didomi-notice-agree-button"),
                )),
            )),
        )),
    )

    @Test
    fun `ultra premium - le refus direct est trouve et clique`() {
        val a = analyzer.analyze(ultraPremium(), webContentOnly = true)
        val refuse = a.buttons(ButtonKind.REFUSE).singleOrNull()
        assertNotNull("« Continuer sans accepter » doit être reconnu", refuse)
        assertNotNull(refuse!!.target)
        val engine = RefusalEngine(rules)
        assertEquals("continuer sans accepter", clickedLabel(engine.next(Screen(firefox, ultraPremium()))))
    }

    /** Galaxy Defense (Google UMP) : seul « Liste des partenaires. » était vu, pas « Gérer les options ». */
    private fun galaxyNotice() = Tree.window(
        node("android.webkit.WebView", children = listOf(
            node("android.app.AlertDialog", children = listOf(
                text("Galaxy Defense demande votre consentement pour utiliser vos données à caractère personnel dans les cas suivants :"),
                node(children = listOf(
                    text("Vos données à caractère personnel seront traitées, et les informations liées à votre appareil (cookies) pourront être consultées par 298 partenaires"),
                    button("Liste des partenaires."),
                )),
                text("Certains fournisseurs sont susceptibles de traiter vos données sur la base de l'intérêt légitime."),
                node("android.widget.Button", desc = "Autoriser", clickable = true),
                node("android.widget.Button", desc = "Gérer les options", clickable = true),
            )),
        )),
    )

    @Test
    fun `galaxy defense - gerer les options est propose, autoriser jamais`() {
        val a = analyzer.analyze(galaxyNotice())
        assertNotNull(a.buttons(ButtonKind.OPTIONS).singleOrNull()?.target)
        assertTrue(a.buttons(ButtonKind.ACCEPT).all { it.target == null })
        val engine = RefusalEngine(rules)
        assertEquals("gerer les options", clickedLabel(engine.next(Screen("com.cyberjoy.galaxydefense", galaxyNotice()))))
    }

    /** Facebook dans Firefox : le menu « Plus d'options » du navigateur avait été cliqué. */
    private fun facebook() = Tree.window(
        group(
            node("android.widget.Button", desc = "Informations sur le site", clickable = true),
            node(viewId = "ADDRESSBAR_URL_BOX", text = "facebook.com/cookie/consent. Recherche ou adresse"),
            node("android.widget.Button", desc = "Plus d’options", clickable = true),
        ),
        node("android.webkit.WebView", text = "Facebook", bounds = Bounds(0, 265, 1080, 2214), children = listOf(
            // Firefox déclare ces textes « invisibles » alors qu'ils sont affichés dans la page.
            node(text = "Autoriser l'utilisation des cookies de Facebook sur ce navigateur ?", visible = false, bounds = Bounds(39, 396, 945, 536)),
            node(text = "Nous utilisons des cookies et des solutions similaires. Vos données personnelles et votre confidentialité.",
                visible = false, bounds = Bounds(39, 599, 1018, 644)),
            node("android.widget.Button", text = "Uniquement autoriser les cookies essentiels", clickable = true, bounds = Bounds(37, 1992, 1043, 2076)),
            node("android.widget.Button", text = "Autoriser les cookies essentiels et optionnels", clickable = true, bounds = Bounds(37, 2094, 1043, 2177)),
        )),
    )

    @Test
    fun `facebook dans firefox - jamais le menu du navigateur, refus essentiel clique`() {
        val a = analyzer.analyze(facebook(), webContentOnly = true)
        assertTrue("le menu du navigateur ne doit pas être un bouton", a.buttons(ButtonKind.OPTIONS).isEmpty())
        val refuse = a.buttons(ButtonKind.REFUSE).single()
        assertNotNull(refuse.target)
        val accept = a.buttons.filter { it.label == "autoriser les cookies essentiels et optionnels" }
        assertTrue(accept.all { it.target == null })
        val engine = RefusalEngine(rules)
        assertEquals("uniquement autoriser les cookies essentiels", clickedLabel(engine.next(Screen(firefox, facebook()))))
    }

    @Test
    fun `refus essentiel prolonge - jamais clique`() {
        val g = SafetyGuard(rules)
        assertTrue(g.checkText("Uniquement autoriser les cookies essentiels") is SafetyGuard.Verdict.Allowed)
        assertFalse(g.checkText("Uniquement autoriser les cookies essentiels et optionnels") is SafetyGuard.Verdict.Allowed)
        assertFalse(g.checkText("Autoriser les cookies essentiels et optionnels") is SafetyGuard.Verdict.Allowed)
    }

    /** TF1+ : après « Tout refuser », le site demande « Confirmer votre refus ». */
    @Test
    fun `tf1 - confirmer votre refus est un refus`() {
        val root = Tree.window(
            node("android.webkit.WebView", children = listOf(
                text("Vous avez fait le choix de refuser les cookies. Pour accéder au contenu, vous pouvez accepter les cookies ou vous abonner à Premium."),
                text("Nos partenaires utilisent vos données personnelles"),
                button("CONFIRMER VOTRE REFUS"),
                button("MODIFIER ET TOUT ACCEPTER"),
            )),
        )
        val a = analyzer.analyze(root, webContentOnly = true)
        assertNotNull(a.buttons(ButtonKind.REFUSE).singleOrNull { it.label == "confirmer votre refus" }?.target)
    }

    /** Hors navigateur, rien ne change : pas de WebView, pas de filtrage. */
    @Test
    fun `sans webview - analyse complete`() {
        val root = Tree.window(
            text("Nous et nos partenaires utilisons des cookies et vos données personnelles"),
            button("Tout refuser"),
            button("Tout accepter"),
        )
        assertEquals(1, analyzer.analyze(root, webContentOnly = true).buttons(ButtonKind.REFUSE).size)
    }

    @Test
    fun `navigateur reconnu par le moteur`() {
        assertTrue(BrowserPolicy.isBrowser(firefox))
        assertNull(clickedLabel(RefusalEngine(rules).next(Screen(firefox, Tree.window(text("Bonjour"))))))
    }

    /** Le Télégramme (Didomi, liste des partenaires) : « Tous les partenaires › Bloquer » est le choix maître. */
    @Test
    fun `didomi - tous les partenaires bloquer est maitre, pas un onglet`() {
        fun radio(label: String) = node("android.widget.RadioButton", text = label, clickable = true, checkable = true,
            children = listOf(text(label.substringBefore(' '))))
        val root = Tree.window(
            node("android.webkit.WebView", children = listOf(
                node(viewId = "didomi-consent-popup", children = listOf(
                    text("Sélectionner les partenaires pour Le Télégramme"),
                    group(
                        node("android.widget.Button", text = "Tous les partenaires", clickable = true, viewId = "didomi-popup-vendors-all"),
                        group(radio("Bloquer"), radio("Autoriser")),
                        cls = "android.view.View",
                    ),
                    group(text("1plusX AG IAB TCF vendor"), group(radio("Bloquer 1plusX AG"), radio("Autoriser 1plusX AG")), cls = "android.view.View"),
                    group(text("33Across IAB TCF vendor"), group(radio("Bloquer 33Across"), radio("Autoriser 33Across")), cls = "android.view.View"),
                )),
            )),
        )
        val a = analyzer.analyze(root, webContentOnly = true)
        assertTrue(a.buttons(ButtonKind.VENDOR_TAB).isEmpty())
        val master = a.buttons(ButtonKind.RADIO_REFUSE).filter { it.isMaster }
        assertEquals(listOf("bloquer"), master.map { it.label })
        assertTrue(a.buttons(ButtonKind.RADIO_REFUSE).size >= 3)
    }

    /**
     * Galaxy Defense (Google UMP) : la liste des 298 partenaires dépasse la capture. CYRAVA revient
     * une fois en arrière pour valider la page des finalités, et annonce un refus partiel.
     */
    @Test
    fun `liste de partenaires trop longue - retour puis validation, refus partiel`() {
        val pkg = "com.cyberjoy.galaxydefense"
        fun purposes() = Tree.window(
            text("Galaxy Defense demande votre consentement pour utiliser vos données personnelles. Nos partenaires et l'intérêt légitime."),
            group(text("Publicités personnalisées"), Tree.switch("Consentement", checked = false)),
            group(text("Mesure d'audience"), Tree.switch("Consentement", checked = false)),
            button("Préférences concernant les fournisseurs"),
            button("Confirmer les choix"),
            button("Tout accepter"),
        )
        fun vendors() = Tree.window(
            button("Retour"),
            text("Préférences concernant les fournisseurs"),
            text("Nos partenaires : fournisseurs IAB TCF, consentement et intérêt légitime"),
            group(text("Quantcast"), Tree.switch("Intérêt légitime", checked = true)),
        )
        val engine = RefusalEngine(rules)
        val descriptions = ArrayList<String>()
        var onVendors = false
        var outcome: Outcome? = null
        for (i in 0 until 40) {
            Tree.reset()
            val screen = if (onVendors) Screen(pkg, vendors(), isTruncated = true) else Screen(pkg, purposes())
            when (val step = engine.next(screen)) {
                is Step.Act -> {
                    descriptions += step.description
                    if (step.description.startsWith("onglet")) onVendors = true
                    if (step.description.startsWith("retour")) onVendors = false
                    if (step.description.startsWith("validation")) {
                        // La fenêtre se ferme après la validation.
                        repeat(6) { engine.next(Screen(pkg, Tree.window(text("Jeu")))).let { if (it is Step.Done) outcome = it.outcome } }
                        break
                    }
                }
                is Step.Done -> { outcome = step.outcome; break }
                else -> Unit
            }
        }
        assertTrue(descriptions.toString(), descriptions.any { it.startsWith("onglet") })
        assertTrue(descriptions.toString(), descriptions.any { it.startsWith("retour : liste trop longue") })
        assertTrue(descriptions.toString(), descriptions.any { it.startsWith("validation") })
        assertTrue(descriptions.none { it.contains("accepter") })
        assertEquals(OutcomeStatus.PARTIAL, outcome?.status)
    }

    /** Firefox : la capture s'arrête dans l'article, pas dans la fenêtre Didomi, qui se traite normalement. */
    @Test
    fun `capture arretee hors du panneau - la fenetre est traitee`() {
        fun screen(): Screen {
            Tree.reset()
            val article = group(text("Article"), text("Suite"), cls = "android.view.View")
            article.childrenOmitted = true
            val root = Tree.window(
                node("android.webkit.WebView", children = listOf(
                    article,
                    node(viewId = "didomi-popup", children = listOf(
                        text("Nous et nos 244 partenaires utilisons des cookies et vos données personnelles pour de la publicité personnalisée"),
                        node("android.widget.Button", text = "Paramétrer", clickable = true, viewId = "didomi-notice-learn-more-button"),
                        button("Accepter"),
                    )),
                )),
            )
            return Screen(firefox, root, isTruncated = true)
        }
        val step = RefusalEngine(rules).next(screen())
        assertTrue(step.toString(), (step as? Step.Act)?.description?.startsWith("options") == true)
    }

    /** Chrome, Didomi : liste trop longue, mais « Tous les partenaires › Bloquer » visible. */
    @Test
    fun `liste trop longue avec choix maitre - maitre puis enregistrer`() {
        var masterChecked = false
        fun screen(): Screen {
            Tree.reset()
            fun radio(label: String, checked: Boolean) = node("android.widget.RadioButton", text = label, clickable = true,
                checkable = true, checked = checked)
            val vendors = group(
                group(text("1plusX AG IAB TCF vendor"), group(radio("Bloquer 1plusX AG", masterChecked), radio("Autoriser 1plusX AG", !masterChecked), cls = "android.widget.RadioGroup"), cls = "android.view.View"),
                group(text("33Across IAB TCF vendor"), group(radio("Bloquer 33Across", masterChecked), radio("Autoriser 33Across", !masterChecked), cls = "android.widget.RadioGroup"), cls = "android.view.View"),
                cls = "android.view.View",
            )
            vendors.childrenOmitted = true
            val root = Tree.window(
                node("android.webkit.WebView", children = listOf(
                    node("android.app.AlertDialog", viewId = "didomi-consent-popup", children = listOf(
                        text("Vous pouvez définir vos préférences de consentement pour chacun de nos partenaires"),
                        node("android.widget.TextView", text = "Tous les partenaires", viewId = "didomi-popup-vendors-all"),
                        group(radio("Bloquer", masterChecked), radio("Autoriser", !masterChecked), cls = "android.widget.RadioGroup"),
                        vendors,
                        node("android.widget.Button", text = "Enregistrer vos préférences et fermer", clickable = true, viewId = "btn-toggle-save"),
                    )),
                )),
            )
            return Screen("com.android.chrome", root, isTruncated = true)
        }
        val engine = RefusalEngine(rules)
        val first = engine.next(screen()) as Step.Act
        assertTrue(first.description, first.description.startsWith("choix maître"))
        assertEquals("bloquer", ((first.ops.single() as Op.Click).label))
        masterChecked = true
        var second: Step = engine.next(screen())
        var guard = 0
        while (second is Step.Wait && guard++ < 5) second = engine.next(screen())
        assertTrue(second.toString(), (second as Step.Act).description.startsWith("validation"))
    }

    /** Journal des Femmes (AppConsent) : « Refuser et payer 1 € » n'est jamais cliqué. */
    @Test
    fun `refuser et payer - mur payant, jamais clique`() {
        val root = Tree.window(
            node("android.webkit.WebView", children = listOf(
                node(viewId = "appconsent", children = listOf(
                    button("Refuser et payer 1€→"),
                    text("Faites un choix pour vos données"),
                    text("Nos 262 partenaires et nous-mêmes utilisons des cookies pour stocker et/ou accéder à des informations"),
                    button("TOUT ACCEPTER"),
                )),
            )),
        )
        val a = analyzer.analyze(root, webContentOnly = true)
        assertTrue(a.buttons.filter { it.label.contains("payer") }.all { it.target == null })
        assertTrue(ConsentOrPay.detect(root))
        val step = RefusalEngine(rules).next(Screen("com.android.chrome", root))
        assertTrue(step.toString(), step is Step.Done && step.outcome.reason == ConsentOrPay.REASON)
    }

    /** Contre-audit M2 : l'adresse du site visité ne doit jamais entrer dans une capture. */
    @Test
    fun `barres d'adresse des navigateurs reconnues`() {
        assertTrue(BrowserPolicy.isAddressBar(firefox, "ADDRESSBAR_URL_BOX"))
        assertTrue(BrowserPolicy.isAddressBar(firefox, "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"))
        assertTrue(BrowserPolicy.isAddressBar("com.android.chrome", "com.android.chrome:id/url_bar"))
        assertTrue(BrowserPolicy.isAddressBar("com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser:id/location_bar_edit_text"))
        assertFalse(BrowserPolicy.isAddressBar("com.cyberjoy.galaxydefense", "url_bar"))
        assertFalse(BrowserPolicy.isAddressBar(firefox, "didomi-notice-disagree-button"))
        assertFalse(BrowserPolicy.isAddressBar(firefox, null))
    }

    /**
     * Le Dauphiné : liste des partenaires déjà tout sur « Bloquer », seul « Enregistrer » à cliquer.
     * La règle C1 (pas de validation sans refus) ne doit pas l'empêcher : le refus est observé.
     * Sans choix maître sélectionné, C1 continue de bloquer.
     */
    @Test
    fun `choix maitre deja sur bloquer - enregistrer est autorise, sinon non`() {
        fun screen(masterBlocked: Boolean): Screen {
            Tree.reset()
            fun radio(label: String, checked: Boolean) = node("android.widget.RadioButton", text = label, clickable = true, checkable = true, checked = checked)
            val root = Tree.window(
                node("android.webkit.WebView", children = listOf(
                    node("android.app.AlertDialog", viewId = "didomi-consent-popup", children = listOf(
                        text("Vous pouvez définir vos préférences de consentement pour chacun de nos partenaires"),
                        node("android.widget.TextView", text = "Tous les partenaires", viewId = "didomi-popup-vendors-all"),
                        group(radio("Bloquer", masterBlocked), radio("Autoriser", !masterBlocked), cls = "android.widget.RadioGroup"),
                        group(text("Zemanta Inc."), group(radio("Bloquer Zemanta Inc.", masterBlocked), radio("Autoriser Zemanta Inc.", !masterBlocked), cls = "android.widget.RadioGroup"), cls = "android.view.View"),
                        node("android.widget.Button", text = "Enregistrer", clickable = true),
                    )),
                )),
            )
            return Screen("com.android.chrome", root)
        }
        val ok = RefusalEngine(rules).next(screen(masterBlocked = true))
        assertTrue(ok.toString(), (ok as? Step.Act)?.description?.startsWith("validation") == true)
        val other = RefusalEngine(rules).next(screen(masterBlocked = false))
        assertFalse(other.toString(), (other as? Step.Act)?.description?.startsWith("validation") == true)
    }

    /** Site Didomi + abonnement (Chrome, 20/11) : « J'accepte tous les cookies » ou « Je m'abonne ». */
    @Test
    fun `accepter ou s'abonner - mur payant annonce, rien clique`() {
        val root = Tree.window(
            node("android.webkit.WebView", children = listOf(
                node("android.app.AlertDialog", children = listOf(
                    text("Choisissez votre mode de lecture. Continuez votre lecture gratuitement"),
                    text("Avec votre consentement, nous et nos 179 partenaires utilisons des cookies pour traiter vos données personnelles"),
                    group(
                        node("android.widget.Button", text = "J'accepte tous les cookies", clickable = true, viewId = "didomi-notice-agree-button"),
                        node("android.widget.Button", text = "Je m'abonne", clickable = true, viewId = "humanoid-subscribe-button"),
                        cls = "android.view.View",
                    ),
                )),
            )),
        )
        val step = RefusalEngine(rules).next(Screen("com.android.chrome", root))
        assertTrue(step.toString(), step is Step.Done && step.outcome.reason == ConsentOrPay.REASON)
    }

    /** Didomi (Marmiton, Le Dauphiné, 20/11) : refus formulé avec une négation. */
    @Test
    fun `refus avec negation autorise, acceptation jamais`() {
        val g = SafetyGuard(rules)
        fun ok(t: String) = g.checkText(t).isAllowed
        assertTrue(ok("Je n'accepte rien: Refuser notre traitement des données et fermer"))
        assertTrue(ok("Je n'accepte pas"))
        assertTrue(ok("Je n'autorise pas ces traitements"))
        assertFalse(ok("Je n'accepte pas les cookies tiers mais j'accepte les autres"))
        assertFalse(ok("Accepter et fermer"))
        assertFalse(ok("Ne pas refuser, accepter"))
        assertFalse(ok("Je n'accepte rien sauf l'abonnement premium"))
        assertFalse(ok("Je n'accepte pas, ok"))
        assertFalse(ok("Sans payer : j'accepte"))
    }

    /** « pas » seul devant le verbe n'est pas une négation : seul « ne pas » compte. */
    @Test
    fun `pas seul ne vaut pas negation`() {
        val g = SafetyGuard(rules)
        fun ok(t: String) = g.checkText(t).isAllowed
        assertFalse(ok("Ne ratez pas : accepter et continuer"))
        assertFalse(ok("Pas de pub : accepter"))
        assertFalse(ok("Pourquoi pas accepter ?"))
        assertFalse(ok("Je ne veux pas accepter"))
        assertTrue(ok("Ne pas accepter"))
        assertTrue(ok("Ne pas autoriser"))
        assertTrue(ok("Je n'accepte pas"))
    }

    /** Même règle pour « jamais » : seul « ne jamais » compte. */
    @Test
    fun `jamais seul ne vaut pas negation`() {
        val g = SafetyGuard(rules)
        fun ok(t: String) = g.checkText(t).isAllowed
        assertFalse(ok("Ne ratez jamais : accepter et continuer"))
        assertFalse(ok("Jamais accepter ?"))
        assertFalse(ok("Jamais sans vous : accepter"))
        assertTrue(ok("Ne jamais autoriser"))
    }

    /** Une question avec négation est une invitation à accepter, jamais un refus. */
    @Test
    fun `question avec negation bloquee`() {
        val g = SafetyGuard(rules)
        fun ok(t: String) = g.checkText(t).isAllowed
        assertFalse(ok("Pourquoi ne pas accepter ?"))
        assertFalse(ok("Why not accept all?"))
        assertFalse(ok("Why not allow cookies"))
        assertFalse(ok("How about not refusing? Accept"))
        assertTrue(ok("Do not accept"))
        assertTrue(ok("Je n'accepte pas"))
    }

    /**
     * Préférences Didomi dans Firefox (Marmiton, 20/11) : lignes « Refuser | Accepter » en simples
     * boutons. CYRAVA refuse (« Vous autorisez › Refuser » d'abord) au lieu de déplier les sections.
     */
    @Test
    fun `lignes refuser accepter - choix refuser, jamais de section depliee`() {
        fun page(): Screen {
            Tree.reset()
            var y = 400
            fun row(title: String): SnapshotNode {
                val r = group(
                    node("android.view.View", text = "+ $title", clickable = true, expandable = true, bounds = Bounds(60, y, 900, y + 60)),
                    node("android.widget.Button", text = "Refuser", clickable = true, bounds = Bounds(90, y + 80, 290, y + 140)),
                    node("android.widget.Button", text = "Accepter", clickable = true, bounds = Bounds(300, y + 80, 500, y + 140)),
                    cls = "android.view.View",
                )
                y += 180
                return r
            }
            val root = Tree.window(node("android.webkit.WebView", bounds = Bounds(0, 200, 1080, 2214), children = listOf(
                node(viewId = "didomi-popup", children = listOf(
                    text("Nos partenaires et nous déposons des traceurs et utilisons vos données personnelles pour la publicité personnalisée"),
                    group(
                        node("android.widget.TextView", text = "VOUS AUTORISEZ", bounds = Bounds(60, 250, 300, 290)),
                        node("android.widget.Button", text = "Refuser", clickable = true, bounds = Bounds(560, 240, 750, 300)),
                        node("android.widget.Button", text = "Accepter", clickable = true, bounds = Bounds(770, 240, 980, 300)),
                        cls = "android.view.View",
                    ),
                    row("Mesure d'audience"), row("Réseaux sociaux"), row("Développer et améliorer les services"),
                    node("android.widget.Button", text = "Enregistrer", clickable = true, bounds = Bounds(720, 2000, 1000, 2080)),
                )),
            )))
            return Screen(firefox, root)
        }
        val engine = RefusalEngine(rules)
        val descriptions = ArrayList<String>()
        repeat(60) {
            when (val st = engine.next(page())) {
                is Step.Act -> descriptions += st.description
                is Step.Done -> return@repeat
                else -> Unit
            }
        }
        assertTrue(descriptions.toString(), descriptions.first { !it.startsWith("balayage") }.startsWith("choix"))
        assertTrue(descriptions.toString(), descriptions.none { it.contains("section") })
        assertTrue(descriptions.toString(), descriptions.any { it.startsWith("validation") })
        assertTrue(descriptions.none { it.contains("accepter") })
    }
}
