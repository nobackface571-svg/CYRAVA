package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Firefox affiche parfois une bulle d'aide (« Secouez votre appareil… ») dans une petite fenêtre au
 * premier plan (diagnostic du 20/11/2026). Elle ne doit ni masquer la page ni bloquer les clics.
 */
class WindowChoiceTest {
    private val page = Bounds(0, 263, 1080, 2214)
    private val tooltip = Bounds(120, 276, 960, 456)
    private val refuse = Bounds(42, 1470, 1038, 1590)

    @Test
    fun `petite bulle de la meme appli - on lit la page`() {
        assertTrue(TargetCheck.preferLargerWindow(tooltip.area, page.area))
    }

    @Test
    fun `vraie fenetre de dialogue - elle reste prioritaire`() {
        val dialog = Bounds(40, 500, 1040, 2000)
        assertFalse(TargetCheck.preferLargerWindow(dialog.area, page.area))
    }

    @Test
    fun `bulle de la meme appli qui ne recouvre pas la cible - clic autorise`() {
        assertTrue(TargetCheck.targetWindowUsable(1, "org.mozilla.firefox", refuse, 2, "org.mozilla.firefox", tooltip))
    }

    @Test
    fun `bulle de la meme appli qui recouvre la cible - abstention`() {
        val target = Bounds(200, 300, 400, 400)
        assertFalse(TargetCheck.targetWindowUsable(1, "org.mozilla.firefox", target, 2, "org.mozilla.firefox", tooltip))
    }

    @Test
    fun `autre appli au premier plan - abstention`() {
        assertFalse(TargetCheck.targetWindowUsable(1, "org.mozilla.firefox", refuse, 2, "com.autre", tooltip))
    }

    @Test
    fun `meme fenetre - toujours autorise`() {
        assertTrue(TargetCheck.targetWindowUsable(1, "org.mozilla.firefox", refuse, 1, "org.mozilla.firefox", page))
    }
}
