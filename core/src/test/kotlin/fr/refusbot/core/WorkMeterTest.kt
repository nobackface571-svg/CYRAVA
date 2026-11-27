package fr.refusbot.core

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkMeterTest {
    @Test fun separe_lectures_sans_fenetre_et_fenetres_traitees() {
        var now = 1_000L
        val m = WorkMeter { now }
        m.record(40, acted = false)
        m.record(60, acted = false)
        m.record(3_500, acted = true)
        m.record(-5, acted = false) // horloge déréglée : jamais de durée négative
        val s = m.snapshot()
        assertEquals(1_000L, s.since)
        assertEquals(3, s.idleReads)
        assertEquals(100L, s.idleMs)
        assertEquals(1, s.windows)
        assertEquals(3_500L, s.windowMs)
        assertEquals(3_600L, s.totalMs)
        now = 9_000L
        m.reset()
        assertEquals(WorkMeter.Snapshot(9_000L, 0, 0, 0, 0), m.snapshot())
    }
}
