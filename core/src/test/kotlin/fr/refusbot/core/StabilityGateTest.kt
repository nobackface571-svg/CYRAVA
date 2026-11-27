package fr.refusbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StabilityGateTest {
    @Test fun first_capture_is_never_enough() {
        assertFalse(StabilityGate().observe(12))
    }

    @Test fun same_count_twice_means_stable() {
        val g = StabilityGate()
        assertFalse(g.observe(3))
        assertFalse(g.observe(40))
        assertTrue(g.observe(40))
    }

    @Test fun never_waits_forever() {
        val g = StabilityGate(maxChecks = 3)
        assertFalse(g.observe(1))
        assertFalse(g.observe(2))
        assertTrue(g.observe(3))
    }

    /** 22/11 : captures lentes (page lourde) : on n'attend jamais plus de 1,5 s avant d'analyser. */
    @Test fun slow_captures_stop_waiting_after_time_budget() {
        val g = StabilityGate()
        assertFalse(g.observe(100, elapsedMs = 0))
        assertFalse(g.observe(200, elapsedMs = 1_200))
        assertTrue(g.observe(300, elapsedMs = 1_500))
    }
}
