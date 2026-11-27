package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.node
import fr.refusbot.core.Tree.text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGuardTest {
    private val guard = SafetyGuard(TestRules.rules)

    private fun allowed(s: String) = guard.checkText(s).isAllowed

    @Test
    fun `les boutons d'acceptation sont bloques`() {
        assertFalse(allowed("Tout accepter"))
        assertFalse(allowed("Accepter et fermer"))
        assertFalse(allowed("Agree"))
        assertFalse(allowed("ACCEPTER"))
        assertFalse(allowed("J’accepte"))
        assertFalse(allowed("Accept all"))
        assertFalse(allowed("I agree"))
        assertFalse(allowed("Autoriser"))
        assertFalse(allowed("Allow all"))
        assertFalse(allowed("Consentir"))
        assertFalse(allowed("Consent"))
        assertFalse(allowed("OK"))
    }

    @Test
    fun `les formules de refus exactes sont autorisees`() {
        assertTrue(allowed("Continuer sans accepter"))
        assertTrue(allowed("Continuer sans accepter →"))
        assertTrue(allowed("Disagree"))
        assertTrue(allowed("Tout refuser"))
        assertTrue(allowed("Ne pas autoriser"))
        assertTrue(allowed("Do not consent"))
        assertTrue(allowed("Je n'accepte pas"))
    }

    @Test
    fun `les negations anglaises contractees et never sont reconnues`() {
        // Autrefois bloquées (faux négatifs sûrs) ; désormais reconnues comme des refus niés.
        assertTrue(allowed("Don't accept"))
        assertTrue(allowed("Never accept"))
        assertTrue(allowed("Don't allow"))
        assertTrue(allowed("Won't agree"))
        // « consent » est un mot interdit isolé (jamais niable) : reste bloqué même avec « n't ».
        assertFalse(allowed("Can't consent"))
        // Mais jamais au prix d'une ambiguïté : ces formes restent bloquées.
        assertFalse(allowed("Why don't you accept"))
        assertFalse(allowed("No thanks, accept all"))
        assertFalse(allowed("Don't accept but allow"))
        assertFalse(allowed("Never accept only"))
    }

    @Test
    fun `le t francais devant un verbe d acceptation n est pas une negation`() {
        // Contre-audit du 24/11 : « Je t'autorise » = « je t'autorise » (une acceptation !) —
        // le « t » pronominal français ne doit jamais être lu comme la contraction anglaise « n't ».
        assertFalse(allowed("Je t'autorise"))
        assertFalse(allowed("Je t'accepte"))
        assertFalse(allowed("On t'autorise"))
        assertFalse(allowed("Il t'accepte"))
        // Seul un auxiliaire anglais fermé légitime le « t » (voir le test des négations ci-dessus).
    }

    @Test
    fun `une formule de refus non exacte contenant accepter est bloquee`() {
        assertFalse(allowed("Continuer sans accepter et s'abonner"))
        assertFalse(allowed("Refuser et accepter les essentiels"))
        assertFalse(allowed("Disagree and accept"))
        assertFalse(allowed("Refuser et s'abonner"))
    }

    @Test
    fun `textes neutres autorises`() {
        assertTrue(allowed("Gérer les options"))
        assertTrue(allowed("Confirmer les choix"))
        assertTrue(allowed("Intérêt légitime"))
        assertTrue(allowed("Gérer le consentement"))
        assertTrue(allowed(""))
    }

    @Test
    fun `le garde-fou inspecte tout le sous-arbre de la cible`() {
        val container = node(
            "android.widget.LinearLayout", clickable = true,
            children = listOf(text("Tout refuser"), text("Tout accepter")),
        )
        assertFalse(guard.checkNode(container).isAllowed)
        val described = node("android.widget.Button", text = "Refuser", desc = "Accepter les cookies", clickable = true)
        assertFalse(guard.checkNode(described).isAllowed)
        assertTrue(guard.checkNode(button("Tout refuser")).isAllowed)
    }

    @Test
    fun `chaque formule d'acceptation des regles est bloquee et chaque refus autorise`() {
        val rules = TestRules.rules
        for (p in rules.accept) assertFalse("« $p » devrait être bloqué", allowed(p))
        for (p in rules.refuse + rules.globalReject) assertTrue("« $p » devrait être autorisé", allowed(p))
        for (p in rules.options + rules.confirm + rules.vendorTabs + rules.back) {
            assertTrue("« $p » (bouton neutre) ne doit pas déclencher le garde-fou", allowed(p))
        }
    }
}
