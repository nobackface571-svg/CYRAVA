package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentOrPayTest {
    private fun c(vararg s: String) = s.map(TextNormalizer::canonical)

    @Test fun le_point_style_wall_is_detected() {
        assertTrue(ConsentOrPay.detect(c("Refuser et s'abonner >", "En savoir plus", "Tout accepter")))
    }

    @Test fun subscription_plus_accept_is_detected() {
        assertTrue(ConsentOrPay.detect(c("Sans accepter ni vous abonner…", "Abonnement 1 €/mois", "Accepter")))
    }

    @Test fun ordinary_banner_is_not_a_paywall() {
        assertFalse(ConsentOrPay.detect(c("Nous utilisons des cookies", "Tout refuser", "Tout accepter")))
        assertFalse(ConsentOrPay.detect(c("Continuer sans accepter", "Paramétrer", "Accepter")))
    }

    @Test
    fun `lien S'abonner loin de la fenetre de cookies - pas de mur payant`() {
        val root = Tree.window(
            Tree.group(Tree.text("Paddock GP"), Tree.button("S'abonner à la newsletter"), Tree.text("Abonnement premium")),
            Tree.group(Tree.group(Tree.group(Tree.group(
                Tree.text("Paddock GP demande votre consentement pour vos données personnelles."),
                Tree.button("Refuser"), Tree.button("Préférences"), Tree.button("Accepter"),
            )))),
        )
        val a = ScreenAnalyzer(TestRules.rules).analyze(root)
        assertFalse(ConsentOrPay.detectNearAccept(a.buttons(ButtonKind.ACCEPT).map { it.labelNode }))
    }

    @Test
    fun `refuser et s'abonner a cote d'accepter - mur payant`() {
        val root = Tree.window(
            Tree.text("Le site demande votre consentement pour vos données personnelles et cookies."),
            Tree.group(Tree.button("Refuser et s'abonner"), Tree.button("Tout accepter")),
        )
        val a = ScreenAnalyzer(TestRules.rules).analyze(root)
        assertTrue(ConsentOrPay.detectNearAccept(a.buttons(ButtonKind.ACCEPT).map { it.labelNode }))
    }
}

