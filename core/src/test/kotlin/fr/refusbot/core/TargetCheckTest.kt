package fr.refusbot.core

import fr.refusbot.core.Tree.button
import fr.refusbot.core.Tree.text
import fr.refusbot.core.Tree.window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Point 5 de la revue : aucune action sur une cible qui a changé depuis l'analyse. */
class TargetCheckTest {
    private val guard = SafetyGuard(TestRules.rules)
    private val refuse = button("Tout refuser")
    private val root = window(text("Cookies"), refuse)
    private val pkg = "com.example.jeu"

    private fun live(
        texts: List<String> = listOf("Tout refuser"),
        bounds: Bounds = refuse.bounds,
        onTop: Boolean = true,
        p: String? = pkg,
        visible: Boolean = true,
        enabled: Boolean = true,
    ) = TargetCheck.Live(p, texts, bounds, onTop, visible, enabled)

    @Test
    fun `cible inchangee - action autorisee`() {
        assertNotNull(root)
        assertNull(TargetCheck.mismatch(pkg, refuse, live(), guard, forGesture = false))
        assertNull(TargetCheck.mismatch(pkg, refuse, live(), guard, forGesture = true))
    }

    @Test
    fun `bouton remplace par Accepter juste avant l'action - abstention`() {
        for (gesture in listOf(false, true)) {
            assertNotNull(TargetCheck.mismatch(pkg, refuse, live(texts = listOf("Tout accepter")), guard, gesture))
            assertNotNull(TargetCheck.mismatch(pkg, refuse, live(texts = listOf("Accepter")), guard, gesture))
            // Même un libellé anodin différent suffit à s'abstenir.
            assertNotNull(TargetCheck.mismatch(pkg, refuse, live(texts = listOf("Continuer")), guard, gesture))
        }
    }

    @Test
    fun `cible disparue, autre appli ou autre fenetre - abstention`() {
        assertNotNull(TargetCheck.mismatch(pkg, refuse, null, guard, forGesture = true))
        assertNotNull(TargetCheck.mismatch(pkg, refuse, live(p = "com.autre"), guard, forGesture = false))
        for (gesture in listOf(false, true)) {
            assertNotNull(TargetCheck.mismatch(pkg, refuse, live(onTop = false), guard, forGesture = gesture))
        }
    }

    @Test
    fun `cible masquee ou desactivee juste avant action - abstention`() {
        assertNotNull(TargetCheck.mismatch(pkg, refuse, live(visible = false), guard, forGesture = false))
        assertNotNull(TargetCheck.mismatch(pkg, refuse, live(enabled = false), guard, forGesture = false))
    }

    @Test
    fun `cible deplacee - aucun appui aux anciennes coordonnees`() {
        val moved = Bounds(refuse.bounds.left, refuse.bounds.top + 300, refuse.bounds.right, refuse.bounds.bottom + 300)
        assertNotNull(TargetCheck.mismatch(pkg, refuse, live(bounds = moved), guard, forGesture = true))
        // Une action d'accessibilité vise le nœud lui-même : la position n'entre pas en compte.
        assertNull(TargetCheck.mismatch(pkg, refuse, live(bounds = moved), guard, forGesture = false))
    }

    @Test
    fun `texte interdit apparu dans la cible - bloque par le garde-fou`() {
        val reason = TargetCheck.mismatch(pkg, refuse, live(texts = listOf("Tout refuser", "Tout accepter")), guard, false)
        assertNotNull(reason)
    }

    @Test
    fun `une case inconnue ou mixte ne peut jamais recevoir de clic`() {
        assertTrue(TargetCheck.toggleMayAct(isChecked = true))
        assertFalse(TargetCheck.toggleMayAct(isChecked = false))
        assertFalse(TargetCheck.toggleMayAct(isChecked = true, isIndeterminate = true))

        val states = mutableListOf(true, true)
        val acted = mutableListOf<Int>()
        for (index in states.indices) {
            if (TargetCheck.toggleMayAct(states[index])) {
                acted.add(index)
                states[index] = false
                if (index == 0) states[1] = false
            }
        }
        assertEquals(listOf(0), acted)
        assertFalse(TargetCheck.toggleMayAct(isChecked = states[1]))
        val stateAfterFailedClick = false
        assertFalse(TargetCheck.toggleMayAct(isChecked = stateAfterFailedClick))
    }

    @Test
    fun `defilement sans conteneur exige la fenetre et la zone capturees`() {
        assertNull(TargetCheck.windowMismatch(pkg, refuse.bounds, pkg, refuse.bounds, windowOnTop = true))
        assertNotNull(TargetCheck.windowMismatch(pkg, refuse.bounds, pkg, refuse.bounds, windowOnTop = false))
        assertNotNull(TargetCheck.windowMismatch(pkg, refuse.bounds, "com.autre", refuse.bounds, windowOnTop = true))
        assertNotNull(
            TargetCheck.windowMismatch(
                pkg, refuse.bounds, pkg,
                Bounds(refuse.bounds.left, refuse.bounds.top + 10, refuse.bounds.right, refuse.bounds.bottom + 10),
                windowOnTop = true,
            ),
        )
    }

    @Test
    fun `deux diagnostics dans la meme seconde ont des noms differents`() {
        val taken = HashSet<String>()
        val a = DiagnosticNames.next("com.example.jeu", "20261116-101500", taken::contains).also { taken.add(it) }
        val b = DiagnosticNames.next("com.example.jeu", "20261116-101500", taken::contains).also { taken.add(it) }
        val c = DiagnosticNames.next("com.example.jeu", "20261116-101500", taken::contains)
        assertEquals("diagnostic-com.example.jeu-20261116-101500.json", a)
        assertNotEquals(a, b)
        assertNotEquals(b, c)
        assertTrue(DiagnosticNames.next("a/../b", "x", taken::contains).startsWith("diagnostic-a_.._b-"))
    }
}
