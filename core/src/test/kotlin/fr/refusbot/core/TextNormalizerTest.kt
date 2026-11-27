package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `minuscules, sans accents, espaces compactes`() {
        assertEquals("gerer les options", TextNormalizer.normalize("  Gérer   les\tOPTIONS \n"))
        assertEquals("interet legitime", TextNormalizer.normalize("Intérêt légitime"))
        assertEquals("preferences concernant les fournisseurs", TextNormalizer.normalize("Préférences concernant les fournisseurs"))
        assertEquals("ca coute", TextNormalizer.normalize("Ça COÛTE"))
    }

    @Test
    fun `espaces insecables, apostrophes typographiques et ligatures`() {
        assertEquals("s'opposer a tout", TextNormalizer.normalize("S’opposer à tout"))
        assertEquals("plus d'options", TextNormalizer.normalize("Plus d`options"))
        assertEquals("oeuvre", TextNormalizer.normalize("Œuvre"))
        assertEquals("tout refuser", TextNormalizer.normalize("Tout​ refuser"))
    }

    @Test
    fun `forme canonique ignore la ponctuation`() {
        assertEquals("continuer sans accepter", TextNormalizer.canonical("Continuer sans accepter →"))
        assertEquals("s opposer a tout", TextNormalizer.canonical("S'opposer à tout"))
        assertEquals("save exit", TextNormalizer.canonical("Save & Exit"))
        assertEquals("tout refuser", TextNormalizer.canonical("✕ TOUT REFUSER ✕"))
        assertEquals("", TextNormalizer.canonical(null))
        assertEquals("", TextNormalizer.canonical("  ...  "))
    }

    @Test
    fun `recherche de mots entiers`() {
        assertTrue(TextNormalizer.containsPhrase("nous utilisons des cookies", "cookies"))
        assertFalse(TextNormalizer.containsPhrase("nous utilisons des cookies", "cookie"))
        assertTrue(TextNormalizer.containsPhrase("votre interet legitime ici", "interet legitime"))
        assertFalse(TextNormalizer.containsPhrase("consentement", "consent"))
        assertEquals(3, TextNormalizer.wordCount("tout refuser maintenant"))
        assertEquals(0, TextNormalizer.wordCount(""))
    }
}
