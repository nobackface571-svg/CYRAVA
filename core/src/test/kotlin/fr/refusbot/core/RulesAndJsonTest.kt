package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RulesAndJsonTest {

    @Test
    fun `rules json est valide et complet`() {
        val r = TestRules.rules
        assertEquals(40, r.limits.maxActions)
        assertEquals(120_000L, r.limits.timeoutMs)
        assertTrue(r.limits.minDelayMs in 50..400 && r.limits.maxDelayMs in 80..400)
        assertEquals(2, r.minHints)
        listOf(r.hints, r.refuse, r.options, r.globalReject, r.confirm, r.vendorTabs, r.back, r.accept, r.forbiddenSubstrings)
            .forEach { assertTrue(it.isNotEmpty()) }
        // Formes canoniques
        assertTrue("tout refuser" in r.refuse)
        assertTrue("continuer sans accepter" in r.refuse)
        assertTrue("s opposer a tout" in r.globalReject)
        assertTrue("confirmer les choix" in r.confirm)
        assertTrue("save exit" in r.confirm)
        assertTrue("interet legitime" in r.hints)
    }

    @Test
    fun `aucune formule n'est a la fois acceptation et refus ou validation`() {
        val r = TestRules.rules
        val accept = r.accept.toSet()
        for (p in r.refuse + r.globalReject + r.options + r.confirm + r.vendorTabs + r.back) {
            assertFalse("« $p » est aussi dans accept", p in accept)
        }
    }

    @Test
    fun `etat coche lu dans la description d'etat (WebView recentes)`() {
        val r = TestRules.rules
        assertEquals(true, r.checkedFromDescription("Coché"))
        assertEquals(true, r.checkedFromDescription("checked"))
        assertEquals(true, r.checkedFromDescription("Activé"))
        assertEquals(true, r.checkedFromDescription("On"))
        assertEquals(false, r.checkedFromDescription("Non coché"))
        assertEquals(false, r.checkedFromDescription("not checked"))
        assertEquals(false, r.checkedFromDescription("Désactivé"))
        assertNull(r.checkedFromDescription("Partially checked"))
        assertNull(r.checkedFromDescription("Mixed"))
        assertEquals(false, r.checkedFromDescription("inactif"))
        assertNull(r.checkedFromDescription(null))
        assertNull(r.checkedFromDescription("Intérêt légitime"))
    }

    @Test
    fun `mini json lit et ecrit`() {
        val parsed = MiniJson.parse("""{"a": [1, 2.5, "x\"é", true, null], "b": {}, "c": -3}""") as Map<*, *>
        val a = parsed["a"] as List<*>
        assertEquals(1L, a[0])
        assertEquals(2.5, a[1])
        assertEquals("x\"é", a[2])
        assertEquals(true, a[3])
        assertNull(a[4])
        assertEquals(-3L, parsed["c"])
        val written = MiniJson.write(parsed, indent = 0)
        assertEquals(parsed, MiniJson.parse(written))
        assertEquals("\"a\\nb\\u0001\"", MiniJson.quote("a\nb\u0001"))
    }

    @Test
    fun `mini json rejette un texte invalide`() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"abc", "{} x")) {
            try {
                MiniJson.parse(bad)
                fail("aurait dû échouer : $bad")
            } catch (_: MiniJson.ParseException) {
            }
        }
    }

    @Test
    fun `mini json rejette les nombres non conformes a la spec JSON`() {
        // Formes invalides en JSON strict, autrefois acceptées ou mal rejetées.
        for (bad in listOf("01", "-01", "1.", ".5", "1e", "1e+", "--5", "+5", "0x10", "1..2")) {
            try {
                MiniJson.parse(bad)
                fail("aurait dû échouer : $bad")
            } catch (_: MiniJson.ParseException) {
            }
        }
        // Formes valides : inchangées.
        assertEquals(0L, MiniJson.parse("0"))
        assertEquals(-0L, MiniJson.parse("-0"))
        assertEquals(0.5, MiniJson.parse("0.5"))
        assertEquals(12.5e2, MiniJson.parse("12.5e2"))
        assertEquals(1L, MiniJson.parse("1"))
        assertEquals(-12L, MiniJson.parse("-12"))
        assertEquals(1.0e-3, MiniJson.parse("1.0e-3"))
    }

    @Test
    fun `mini json combine les paires de substitution unicode`() {
        // « 😀 » = U+1F600 = surrogate high D83D + low DE00 : un seul emoji, pas deux caractères.
        assertEquals("\uD83D\uDE00", MiniJson.parse("\"\\uD83D\\uDE00\""))
        // High surrogate non suivi d'un low surrogate : conservé tel quel, pas de plantage.
        assertEquals("\uD83D" + "A", MiniJson.parse("\"\\uD83DA\""))
        assertEquals("\uD83D" + "A", MiniJson.parse("\"\\uD83D\\u0041\""))
    }

    @Test
    fun `mini json borne aussi la profondeur a l ecriture`() {
        var deep: Any? = null
        repeat(MiniJson.MAX_DEPTH + 5) { deep = listOf(deep) }
        try {
            MiniJson.write(deep)
            fail("aurait dû échouer")
        } catch (e: MiniJson.ParseException) {
            assertTrue(e.message!!.contains("imbrication trop profonde"))
        }
        // À la profondeur exacte, l'écriture reste possible.
        var ok: Any? = null
        repeat(MiniJson.MAX_DEPTH) { ok = listOf(ok) }
        assertTrue(MiniJson.write(ok).isNotEmpty())
    }

    @Test
    fun `mini json refuse une imbrication trop profonde sans faire deborder la pile`() {
        // Fichier piégé : 100 000 crochets ouvrants. Doit échouer proprement (pas de StackOverflowError).
        for (bad in listOf("[".repeat(100_000), "{\"a\":".repeat(100_000))) {
            try {
                MiniJson.parse(bad)
                fail("aurait dû échouer")
            } catch (e: MiniJson.ParseException) {
                assertTrue(e.message!!.contains("imbrication trop profonde"))
            }
        }
        // Une imbrication raisonnable reste acceptée.
        val ok = "[".repeat(MiniJson.MAX_DEPTH) + "]".repeat(MiniJson.MAX_DEPTH)
        assertTrue(MiniJson.parse(ok) is List<*>)
    }

    @Test
    fun `regles sans garde-fou refusees`() {
        try {
            Rules.fromJson("""{"buttons": {"refuse": ["Refuser"], "confirm": ["Valider"]}, "forbidden": {"substrings": []}}""")
            fail("un rules.json sans garde-fou doit être refusé")
        } catch (_: IllegalArgumentException) {
        }
    }
    @Test
    fun `import ne peut pas elargir les indices ni les profils de reconnaissance`() {
        val baseline = TestRules.rules
        val imported = baseline.copy(
            hints = baseline.hints + "indice arbitraire",
            cmpMarkers = baseline.cmpMarkers + "cmp-inconnu",
            platforms = baseline.platforms + PlatformProfile(
                name = "profil importe",
                markers = listOf("site-piege"),
                phrases = listOf("gestion des cookies"),
            ),
        )

        val hardened = RulesValidator.harden(imported, baseline)

        assertEquals(baseline.hints, hardened.hints)
        assertEquals(baseline.cmpMarkers, hardened.cmpMarkers)
        assertEquals(baseline.platforms, hardened.platforms)
    }

}
