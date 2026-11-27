package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** Chronométrage des étapes (22/11) : durées exactes, relecture fidèle, aucune durée inventée. */
class SessionTimingTest {

    @Test
    fun `les durees de chaque etape sont additionnees et comptees`() {
        val clock = FakeClock()
        val t = SessionTiming { clock.now }
        t.measure(SessionTiming.Phase.CAPTURE) { clock.advance(300) }
        t.measure(SessionTiming.Phase.CAPTURE) { clock.advance(200) }
        t.measure(SessionTiming.Phase.TOGGLES) { clock.advance(1_000) }
        t.add(SessionTiming.Phase.WAITS, 250)
        clock.advance(250)
        t.add(SessionTiming.Phase.PAUSES, -5) // horloge déréglée : ignorée
        clock.advance(50)
        t.noteNodes(1200)
        t.noteNodes(800)

        val s = t.summary()
        assertEquals(1_800L, s.totalMs)
        assertEquals(500L, s.phaseMs[SessionTiming.Phase.CAPTURE])
        assertEquals(2, s.phaseCounts[SessionTiming.Phase.CAPTURE])
        assertEquals(1_000L, s.phaseMs[SessionTiming.Phase.TOGGLES])
        assertEquals(0, s.phaseCounts[SessionTiming.Phase.PAUSES])
        assertEquals(50L, s.otherMs)
        assertEquals(1200, s.maxNodes)
    }

    @Test
    fun `export puis relecture identiques, ancienne entree sans duree`() {
        val clock = FakeClock()
        val t = SessionTiming { clock.now }
        t.measure(SessionTiming.Phase.SCROLL) { clock.advance(430) }
        t.noteNodes(42)
        val s = t.summary()
        val m = s.toMap()
        assertEquals(430L, m["defilementMs"])
        assertEquals(1L, m["defilementNombre"])
        assertEquals(42L, m["elementsMax"])
        assertEquals(s, SessionTiming.Summary.fromMap(m))
        assertNull(SessionTiming.Summary.fromMap(emptyMap()))
    }

    @Test
    fun `le diagnostic contient les durees quand elles sont mesurees`() {
        Tree.reset()
        val root = Tree.window(Tree.text("Cookies"))
        val clock = FakeClock()
        val t = SessionTiming { clock.now }
        t.measure(SessionTiming.Phase.CAPTURE) { clock.advance(120) }
        val with = DiagnosticExport.toJson(root, DiagnosticExport.Meta("p", null, "t", "test", null, "1", 2, "16", "x", timings = t.summary().toMap()), null)
        assertTrue(with, with.contains("\"durees\"") && with.contains("\"lectureEcranMs\": 120"))
        val without = DiagnosticExport.toJson(root, DiagnosticExport.Meta("p", null, "t", "test", null, "1", 2, "16", "x"), null)
        assertFalse(without.contains("\"durees\""))
    }
}
